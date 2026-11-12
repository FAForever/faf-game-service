package com.faforever.server.game;

import com.faforever.server.message.client.GPGMessage;
import com.faforever.server.message.client.GameMessage;
import com.faforever.server.message.client.dto.GameInfo;
import com.faforever.server.message.service.InboundGPGMessage;
import com.faforever.server.message.service.OutboundGameMessage;
import com.faforever.server.player.Player;
import com.faforever.server.player.PlayerStateTracker;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.AccessLevel;
import lombok.Getter;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@ApplicationScoped
@RunOnVirtualThread
public class GameStateTracker {

    private final PlayerStateTracker playerStateTracker;

    private final Emitter<OutboundGameMessage<GameMessage.Server>> outboundEmitter;

    private final GameRepository gameRepository;

    private final Instance<GameHandler> gameHandlerInstance;

    private final Map<Long, GameHandler> sessionGameHandlerMap = new ConcurrentHashMap<>();
    private final Map<Integer, GameHandler> gameIdMap = new ConcurrentHashMap<>();
    private final AtomicInteger gameCounter = new AtomicInteger();
    @Getter(AccessLevel.PACKAGE)
    private final Set<GameHandler> dirtyGames = ConcurrentHashMap.newKeySet();

    GameStateTracker(PlayerStateTracker playerStateTracker,
                     @Channel("lobby-outbound") Emitter<OutboundGameMessage<GameMessage.Server>> outboundEmitter,
                     GameRepository gameRepository, Instance<GameHandler> gameHandlerInstance) {
        this.playerStateTracker = playerStateTracker;
        this.outboundEmitter = outboundEmitter;
        this.gameRepository = gameRepository;
        this.gameHandlerInstance = gameHandlerInstance;
    }

    @PostConstruct
    void initialize() {
        gameCounter.set(gameRepository.findMaxGameId());
    }

    @Incoming("gpg-inbound")
    void handleGPGMessage(InboundGPGMessage<GPGMessage.Client> message) {
        GameHandler gameHandler = sessionGameHandlerMap.get(message.sessionId());
        if (gameHandler == null) {
            throw new IllegalStateException("No game found for session");
        }

        gameHandler.accept(message);
    }

    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void updateDirtyGames() {
        Set<GameHandler> frozenDirtyGames = Set.copyOf(dirtyGames);
        if (frozenDirtyGames.isEmpty()) {
            return;
        }

        playerStateTracker.getPlayerIds().forEach(playerId -> {
            Player player = playerStateTracker.getPlayer(playerId);
            if (player == null) {
                return;
            }

            GameMessage.GameInfoList filteredGameInfoMessage = createFilteredGameInfoMessage(frozenDirtyGames, player);
            if (filteredGameInfoMessage.games().isEmpty()) {
                return;
            }
            outboundEmitter.send(OutboundGameMessage.forPlayer(playerId, filteredGameInfoMessage));
        });

        frozenDirtyGames.forEach(dirtyGames::remove);
    }

    public void sendGamesToPlayer(long sessionId, Player player) {
        Set<GameHandler> gameHandlers = Set.copyOf(gameIdMap.values());

        GameMessage.GameInfoList filteredGameInfoMessage = createFilteredGameInfoMessage(gameHandlers, player);
        if (filteredGameInfoMessage.games().isEmpty()) {
            return;
        }
        outboundEmitter.send(OutboundGameMessage.forSession(sessionId, filteredGameInfoMessage));
    }

    private GameMessage.GameInfoList createFilteredGameInfoMessage(Collection<GameHandler> gameHandlers,
                                                                   Player player) {
        Set<GameInfo> visibleGames = gameHandlers.stream()
                                                 .filter(game -> game.isVisibleToPlayer(player))
                                                 .map(GameHandler::createGameInfo)
                                                 .filter(Objects::nonNull)
                                                 .collect(Collectors.toSet());
        return new GameMessage.GameInfoList(visibleGames);
    }

    void hostGame(long sessionId, String title, String mapName, GameDetails gameDetails) {
        GameHandler gameHandler = gameHandlerInstance.get();
        sessionGameHandlerMap.put(sessionId, gameHandler);
        gameIdMap.put(gameHandler.getGameId(), gameHandler);
        Player player = playerStateTracker.getPlayerForSession(sessionId);
        if (player == null) {
            throw new IllegalStateException("Player not active");
        }

        gameHandler.initializeDetails(title, mapName, gameDetails);

        gameHandler.launch(
                new LaunchDetails(sessionId, new GamePlayer(player.getId(), player.getUsername()), Map.of(), null, null,
                                  null, null));
    }

    void joinGame(long sessionId, int gameId, @Nullable String password) {
        Player player = playerStateTracker.getPlayerForSession(sessionId);
        if (player == null) {
            throw new IllegalStateException("Player not active");
        }

        GameHandler gameHandler = gameIdMap.get(gameId);
        if (gameHandler == null) {
            outboundEmitter.send(OutboundGameMessage.forSession(sessionId,
                                                              new GameMessage.GameJoinFailed("host_left_game",
                                                                                             gameId)));
            return;
        }

        if (!gameHandler.isJoinable()) {
            outboundEmitter.send(OutboundGameMessage.forSession(sessionId,
                                                                new GameMessage.GameJoinFailed("game_not_ready",
                                                                                               gameId)));
            return;
        }

        if (!gameHandler.passwordMatches(password)) {
            outboundEmitter.send(
                    OutboundGameMessage.forSession(sessionId, new GameMessage.GameJoinFailed("bad_password", gameId)));
            return;
        }


        if (!gameHandler.isVisibleToPlayer(player)) {
            outboundEmitter.send(OutboundGameMessage.forSession(sessionId,
                                                                new GameMessage.GameJoinFailed("not_eligible",
                                                                                               gameId)));
            return;
        }

        gameHandler.launch(
                new LaunchDetails(sessionId, new GamePlayer(player.getId(), player.getUsername()), Map.of(), null, null,
                                  null, null));
    }

    int nextGameId() {
        return gameCounter.incrementAndGet();
    }

    void markDirty(GameHandler gameHandler) {
        dirtyGames.add(gameHandler);
    }
}
