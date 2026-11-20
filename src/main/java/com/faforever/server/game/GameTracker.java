package com.faforever.server.game;

import com.faforever.server.game.dto.GameDetails;
import com.faforever.server.game.dto.GamePlayer;
import com.faforever.server.game.dto.LaunchDetails;
import com.faforever.server.message.client.GPGMessage;
import com.faforever.server.message.client.GameMessage;
import com.faforever.server.message.service.InboundGPGMessage;
import com.faforever.server.message.service.OutboundGameMessage;
import com.faforever.server.player.GameInfoWithFilter;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
@RunOnVirtualThread
public class GameTracker {

    private final Emitter<OutboundGameMessage<GameMessage.Server>> outboundEmitter;

    private final GameTrackerRepository gameTrackerRepository;

    private final Instance<GameHandler> gameHandlerInstance;

    private final Map<Integer, GameHandler> playerGameHandlerMap = new ConcurrentHashMap<>();
    private final Map<Integer, GameHandler> gameIdMap = new ConcurrentHashMap<>();
    private final AtomicInteger gameCounter = new AtomicInteger();

    GameTracker(@Channel("lobby-outbound") Emitter<OutboundGameMessage<GameMessage.Server>> outboundEmitter,
                GameTrackerRepository gameTrackerRepository, Instance<GameHandler> gameHandlerInstance) {
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
        GameHandler gameHandler = playerGameHandlerMap.get(message.playerId());
        if (gameHandler == null) {
            LOG.warn("Received message from player not in a game: {}", message);
            return;
        }

        gameHandler.accept(message);
    }

    public Collection<GameInfoWithFilter> getActiveGames() {
        return gameIdMap.values()
                        .stream()
                        .map(GameHandler::createGameInfo)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
    }

    void hostGame(long sessionId, String title, String mapName, GameDetails gameDetails) {
        GameHandler existingHostGame = playerGameHandlerMap.get(gameDetails.host().id());
        if (existingHostGame != null) {
            throw new IllegalStateException("Host already in game with id %d".formatted(existingHostGame.getGameId()));
        }

        GameHandler gameHandler = createNewGame(title, mapName, gameDetails);

        LaunchDetails launchDetails = new LaunchDetails(sessionId, gameDetails.host(), Map.of(), null, null, null,
                                                        null);
        launch(gameHandler, launchDetails);
    }

    private void launch(GameHandler gameHandler, LaunchDetails launchDetails) {
        playerGameHandlerMap.put(launchDetails.player().id(), gameHandler);
        gameHandler.launch(launchDetails);
    }

    private GameHandler createNewGame(String title, String mapName, GameDetails gameDetails) {
        int gameId = nextGameId();
        GameHandler gameHandler = gameHandlerInstance.get();
        gameIdMap.put(gameId, gameHandler);
        gameHandler.initializeDetails(gameId, title, mapName, gameDetails);
        return gameHandler;
    }

    void joinGame(long sessionId, GamePlayer player, int gameId, @Nullable String password) {
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
            outboundEmitter.send(
                    OutboundGameMessage.forSession(sessionId, new GameMessage.GameJoinFailed("not_eligible", gameId)));
            return;
        }

        launch(gameHandler, new LaunchDetails(sessionId, player, Map.of(), null, null, null, null));
    }

    int nextGameId() {
        return gameCounter.incrementAndGet();
    }

    void removeConnectionToGame(int playerId, GameHandler gameHandler) {
        playerGameHandlerMap.remove(playerId, gameHandler);
    }

    void removeGame(GameHandler gameHandler) {
        if (!gameIdMap.remove(gameHandler.getGameId(), gameHandler)) {
            LOG.warn("Game handler not associated with game id in game tracker");
            return;
        }

        gameHandlerInstance.destroy(gameHandler);
    }

    void restoreConnectionToGame(long sessionId, int playerId, int gameId) {
        GameHandler gameHandler = gameIdMap.get(gameId);
        if (gameHandler == null) {
            LOG.warn("Trying to restore connection to game that no longer exists");
            return;
        }

        if (gameHandler != playerGameHandlerMap.get(playerId)) {
            LOG.warn("Trying to restore connection to game that the player is not part of");
            return;
        }

        if (gameHandler.updatePlayerSession(playerId, sessionId)) {
            playerGameHandlerMap.put(playerId, gameHandler);
        }
    }

}
