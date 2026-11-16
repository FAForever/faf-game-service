package com.faforever.server.game;

import com.faforever.server.game.dto.GameDetails;
import com.faforever.server.game.dto.GamePlayer;
import com.faforever.server.game.dto.LaunchDetails;
import com.faforever.server.message.client.GPGMessage;
import com.faforever.server.message.client.GameMessage;
import com.faforever.server.message.client.dto.GameInfo;
import com.faforever.server.message.service.InboundGPGMessage;
import com.faforever.server.message.service.OutboundGameMessage;
import com.faforever.server.player.Player;
import com.faforever.server.player.PlayerTracker;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
@RunOnVirtualThread
public class GameTracker {

    private final PlayerTracker playerTracker;

    private final Emitter<OutboundGameMessage<GameMessage.Server>> outboundEmitter;

    private final GameTrackerRepository gameTrackerRepository;

    private final Instance<GameHandler> gameHandlerInstance;

    private final Map<Long, GameHandler> sessionGameHandlerMap = new ConcurrentHashMap<>();
    private final Map<Integer, GameHandler> gameIdMap = new ConcurrentHashMap<>();
    private final AtomicInteger gameCounter = new AtomicInteger();
    @Getter(AccessLevel.PACKAGE)
    private final Map<Integer, GameInfoWithFilter> dirtyGames = new ConcurrentHashMap<>();

    GameTracker(PlayerTracker playerTracker,
                @Channel("lobby-outbound") Emitter<OutboundGameMessage<GameMessage.Server>> outboundEmitter,
                GameTrackerRepository gameTrackerRepository, Instance<GameHandler> gameHandlerInstance) {
        this.playerTracker = playerTracker;
        this.outboundEmitter = outboundEmitter;
        this.gameTrackerRepository = gameTrackerRepository;
        this.gameHandlerInstance = gameHandlerInstance;
    }

    @PostConstruct
    void initialize() {
        gameCounter.set(gameTrackerRepository.findMaxGameId());
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
        Map<Integer, GameInfoWithFilter> frozenDirtyGames = Map.copyOf(dirtyGames);
        if (frozenDirtyGames.isEmpty()) {
            return;
        }

        playerTracker.getPlayers().forEach(player -> {
            Set<GameInfo> visibleGames = frozenDirtyGames.values().stream()
                                                         .filter(game -> game.filter().test(player))
                                                         .map(GameInfoWithFilter::gameInfo)
                                                         .collect(Collectors.toSet());
            GameMessage.GameInfoList filteredGameInfoMessage = new GameMessage.GameInfoList(visibleGames);
            if (filteredGameInfoMessage.games().isEmpty()) {
                return;
            }

            outboundEmitter.send(OutboundGameMessage.forPlayer(player.getId(), filteredGameInfoMessage));
        });

        frozenDirtyGames.forEach(dirtyGames::remove);
    }

    public void sendGamesToSession(long sessionId) {
        Player player = playerTracker.getPlayerForSession(sessionId);
        if (player == null) {
            Log.warn("No player associated with session");
            return;
        }
        Set<GameHandler> gameHandlers = Set.copyOf(gameIdMap.values());

        GameMessage.GameInfoList filteredGameInfoMessage = gameHandlers.stream()
                                                                       .filter(gameHandler -> gameHandler.isVisibleToPlayer(
                                                                               player))
                                                                       .map(GameHandler::createGameInfo)
                                                                       .filter(Objects::nonNull)
                                                                       .collect(Collectors.collectingAndThen(
                                                                               Collectors.toSet(),
                                                                               GameMessage.GameInfoList::new));
        if (filteredGameInfoMessage.games().isEmpty()) {
            return;
        }
        outboundEmitter.send(OutboundGameMessage.forSession(sessionId, filteredGameInfoMessage));
    }

    void hostGame(long sessionId, String title, String mapName, GameDetails gameDetails) {
        GameHandler gameHandler = gameHandlerInstance.get();
        sessionGameHandlerMap.put(sessionId, gameHandler);
        gameIdMap.put(gameHandler.getGameId(), gameHandler);
        Player player = playerTracker.getPlayerForSession(sessionId);
        if (player == null) {
            throw new IllegalStateException("Player not active");
        }

        gameHandler.initializeDetails(title, mapName, gameDetails);

        gameHandler.launch(
                new LaunchDetails(sessionId, new GamePlayer(player.getId(), player.getUsername()), Map.of(), null, null,
                                  null, null));
    }

    void joinGame(long sessionId, int gameId, @Nullable String password) {
        Player player = playerTracker.getPlayerForSession(sessionId);
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

    void stageUpdate(GameInfo gameInfo, Predicate<Player> filter) {
        dirtyGames.put(gameInfo.uid(), new GameInfoWithFilter(gameInfo, filter));
    }

    void removeGame(GameHandler gameHandler) {
        if (!gameIdMap.remove(gameHandler.getGameId(), gameHandler)) {
            LOG.warn("Game handler not associated with game id in game tracker");
            return;
        }

        gameHandlerInstance.destroy(gameHandler);
    }

    private record GameInfoWithFilter(
            GameInfo gameInfo,
            Predicate<Player> filter
    ) {}
}
