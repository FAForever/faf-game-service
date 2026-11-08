package com.faforever.server.game;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.message.client.GameMessage;
import com.faforever.server.message.client.dto.GameType;
import com.faforever.server.message.service.InboundGameMessage;
import com.faforever.server.message.service.OutboundGameMessage;
import com.faforever.server.player.FriendOrFoeRepository;
import com.faforever.server.player.Player;
import com.faforever.server.player.PlayerStateTracker;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Slf4j
@ApplicationScoped
public class GameService {

    private final PlayerStateTracker playerStateTracker;
    private final GameStateTracker gameStateTracker;

    private final FriendOrFoeRepository friendOrFoeRepository;

    GameService(PlayerStateTracker playerStateTracker, GameStateTracker gameStateTracker,
                FriendOrFoeRepository friendOrFoeRepository) {
        this.playerStateTracker = playerStateTracker;
        this.gameStateTracker = gameStateTracker;
        this.friendOrFoeRepository = friendOrFoeRepository;
    }

    @Incoming("game-inbound")
    public void handleRequest(InboundGameMessage<GameMessage.Client> request) {
        long sessionId = request.sessionId();
        switch (request.message()) {
            case GameMessage.HostGameRequest hostGameRequest -> hostGame(sessionId, hostGameRequest);
            case GameMessage.JoinGameRequest joinGameRequest -> joinGame(sessionId, joinGameRequest);
            case GameMessage.RestoreGameSessionRequest restoreGameSessionRequest ->
                    restoreGameSession(sessionId, restoreGameSessionRequest);
        }
    }

    private void hostGame(long sessionId, GameMessage.HostGameRequest hostMessage) {
        Player player = playerStateTracker.getPlayerForSession(sessionId);
        if (player == null) {
            throw new IllegalStateException("session not associated with a player");
        }

        String title = hostMessage.title();
        if (title.isBlank()) {
            throw new IllegalStateException("Title must not be empty");
        }

        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(title)) {
            throw new IllegalStateException("Title must contain only ascii characters");
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
                    friendOrFoeRepository.loadFriendsAndFoes(player.getId(), FriendOrFoeEntity.Status.FOE));
            case PRIVATE -> new Visibility.Private(
                    friendOrFoeRepository.loadFriendsAndFoes(player.getId(), FriendOrFoeEntity.Status.FRIEND));
        };

        gameStateTracker.hostGame(sessionId, title, mapName, enforceRatingRange,
                                  new GameDetails(visibility, hostMessage.password(), "global", GameType.CUSTOM,
                                                  hostMessage.featuredMod() == null ? "faf" : featuredMod,
                                                  new GamePlayer(player.getId(), player.getUsername()),
                                                  new RatingRange(ratingMin, ratingMax)));
    }

    private void joinGame(long sessionId, GameMessage.JoinGameRequest joinMessage) {
        Player player = playerStateTracker.getPlayerForSession(sessionId);
        if (player == null) {
            throw new IllegalStateException("session not associated with a player");
        }

        int gameId = joinMessage.gameId();
        Game game = gameStateTracker.getGame(gameId);
        if (game == null) {
            messageBroker.send(OutboundGameMessage.forSession(sessionId,
                                                              new GameMessage.GameJoinFailed("host_left_game",
                                                                                             gameId)));
            return;
        }

        if (game.getState() != Game.State.LOBBY) {
            messageBroker.send(OutboundGameMessage.forSession(sessionId,
                                                              new GameMessage.GameJoinFailed("game_not_ready",
                                                                                             gameId)));
            return;
        }

        if (!game.getPassword().map(password -> password.equals(joinMessage.password())).orElse(true)) {
            messageBroker.send(
                    OutboundGameMessage.forSession(sessionId, new GameMessage.GameJoinFailed("bad_password", gameId)));
            return;
        }

        if (!Set.of(GameType.CUSTOM, GameType.COOP).contains(game.getGameType())) {
            throw new IllegalStateException("Game cannot be joined");
        }

        if (!gameStateTracker.isGameVisibleToPlayer(game, player)) {
            throw new IllegalStateException("You cannot join this game");
        }

        messageBroker.send(OutboundGameMessage.forSession(sessionId, GameMessage.GameLaunchResponse.from(game)));
    }

    private void restoreGameSession(long sessionId, GameMessage.RestoreGameSessionRequest restoreMessage) {
        Player player = playerStateTracker.getPlayerForSession(sessionId);
        if (player == null) {
            throw new IllegalStateException("session not associated with a player");
        }

        int gameId = restoreMessage.gameId();
        Game game = gameStateTracker.getGame(gameId);
        if (game == null) {
            throw new IllegalStateException("The game you were connected to no longer exists");
        }

        if (!Set.of(Game.State.LOBBY, Game.State.LIVE).contains(game.getState())) {
            throw new IllegalStateException("The game you were connected to is no longer available");
        }

    }
}
