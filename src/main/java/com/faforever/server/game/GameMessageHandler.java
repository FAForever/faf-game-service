package com.faforever.server.game;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.game.dto.GameDetails;
import com.faforever.server.game.dto.GamePlayer;
import com.faforever.server.game.dto.RatingRange;
import com.faforever.server.game.dto.Visibility;
import com.faforever.server.message.client.GameMessage;
import com.faforever.server.message.client.dto.GameType;
import com.faforever.server.message.service.InboundGameMessage;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.nio.charset.StandardCharsets;

@Slf4j
@ApplicationScoped
public class GameMessageHandler {

    private final GameTracker gameTracker;

    private final GameMessageHandlerRepository gameMessageHandlerRepository;

    GameMessageHandler(GameTracker gameTracker,
                       GameMessageHandlerRepository gameMessageHandlerRepository) {
        this.gameTracker = gameTracker;
        this.gameMessageHandlerRepository = gameMessageHandlerRepository;
    }

    @Incoming("game-inbound")
    public void handleRequest(InboundGameMessage<GameMessage.Client> request) {
        long sessionId = request.sessionId();
        int playerId = request.playerId();
        switch (request.message()) {
            case GameMessage.HostGameRequest hostGameRequest -> hostGame(sessionId, playerId, hostGameRequest);
            case GameMessage.JoinGameRequest joinGameRequest -> joinGame(sessionId, playerId, joinGameRequest);
            case GameMessage.RestoreGameSessionRequest restoreGameSessionRequest ->
                    restoreGameSession(sessionId, playerId, restoreGameSessionRequest);
        }
    }

    private void hostGame(long sessionId, int playerId, GameMessage.HostGameRequest hostMessage) {
        String title = hostMessage.title();
        if (title.isBlank()) {
            throw new IllegalStateException("Title must not be empty");
        }

        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(title)) {
            throw new IllegalStateException("Title must contain only ascii characters");
        }

        GamePlayer host = gameMessageHandlerRepository.loadPlayer(playerId);
        if (host == null) {
            throw new IllegalStateException("Unknown player id");
        }

        String featuredMod = hostMessage.featuredMod();
        if (featuredMod == null) {
            featuredMod = "faf";
        }

        String mapName = hostMessage.mapName();
        if (mapName == null) {
            mapName = "scmp_007";
        }

        Integer ratingMax = hostMessage.ratingMax();
        Integer ratingMin = hostMessage.ratingMin();
        boolean enforceRatingRange = hostMessage.enforceRatingRange();

        Visibility visibility = switch (hostMessage.visibility()) {
            case PUBLIC -> new Visibility.Public(
                    gameMessageHandlerRepository.loadFriendsAndFoes(playerId, FriendOrFoeEntity.Status.FOE));
            case PRIVATE -> new Visibility.Private(
                    gameMessageHandlerRepository.loadFriendsAndFoes(playerId, FriendOrFoeEntity.Status.FRIEND));
        };


        gameTracker.hostGame(sessionId, title, mapName,
                             new GameDetails(visibility, hostMessage.password(), "global", GameType.CUSTOM,
                                             hostMessage.featuredMod() == null ? "faf" : featuredMod, host,
                                             new RatingRange(enforceRatingRange, ratingMin, ratingMax)));
    }

    private void joinGame(long sessionId, int playerId, GameMessage.JoinGameRequest joinMessage) {
        GamePlayer player = gameMessageHandlerRepository.loadPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("Unknown player id");
        }

        gameTracker.joinGame(sessionId, player, joinMessage.gameId(), joinMessage.password());
    }

    private void restoreGameSession(long sessionId, int playerId,
                                    GameMessage.RestoreGameSessionRequest restoreMessage) {
        gameTracker.restoreConnectionToGame(sessionId, playerId, restoreMessage.gameId());
    }
}
