package com.faforever.server.message.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

public sealed interface GPGMessage {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = HostGame.class, name = "HostGame"),

    })
    sealed interface Server extends GPGMessage {}

    sealed interface Client extends GPGMessage {

        String command();

        sealed interface HostOnly extends GPGMessage.Client {}

        @JsonCreator
        static GPGMessage.Client of(String command, String target, List<Object> args) {
            if (!"game".equals(target)) {
                throw new IllegalArgumentException("GPG Game message with wrong target");
            }
            return switch (command) {
                case AIOption.COMMAND -> new AIOption(args);
                case Bottleneck.COMMAND -> new Bottleneck(args);
                case BottleneckCleared.COMMAND -> new BottleneckCleared(args);
                case Chat.COMMAND -> new Chat(args);
                case ClearSlot.COMMAND -> new ClearSlot(args);
                case Desync.COMMAND -> new Desync(args);
                case Disconnected.COMMAND -> new Disconnected(args);
                case EnforceRating.COMMAND -> new EnforceRating(args);
                case GameEnded.COMMAND -> new GameEnded(args);
                case GameFull.COMMAND -> new GameFull(args);
                case GameMods.COMMAND -> new GameMods(args);
                case GameOption.COMMAND -> new GameOption(args);
                case GameResult.COMMAND -> new GameResult(args);
                case GameState.COMMAND -> new GameState(args);
                case IceMsg.COMMAND -> new IceMsg(args);
                case JsonStats.COMMAND -> new JsonStats(args);
                case LaunchStatus.COMMAND -> new LaunchStatus(args);
                case OperationComplete.COMMAND -> new OperationComplete(args);
                case PlayerOption.COMMAND -> new PlayerOption(args);
                case Rehost.COMMAND -> new Rehost(args);
                case TeamkillHappened.COMMAND -> new TeamkillHappened(args);
                case TeamkillReport.COMMAND -> new TeamkillReport(args);
                default -> new UnknownMessage(command, args);
            };
        }

    }
    
    record AIOption(
            List<Object> args
    ) implements Client.HostOnly {

        private static final String COMMAND = "AIOption";

        public String command() {
            return COMMAND;
        }

        public String aiName() {
            return (String) args.getFirst();
        }

        public String optionKey() {
            return (String) args.get(1);
        }

        public Object optionValue() {
            return args.get(2);
        }
    }

    record Bottleneck(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "Bottleneck";

        public String command() {
            return COMMAND;
        }
    }

    record BottleneckCleared(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "BottleneckCleared";

        public String command() {
            return COMMAND;
        }
    }

    record Chat(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "Chat";

        public String command() {
            return COMMAND;
        }
    }

    record ClearSlot(
            List<Object> args
    ) implements Client.HostOnly {
        private static final String COMMAND = "ClearSlot";

        public String command() {
            return COMMAND;
        }
    }

    record Desync(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "Desync";

        public String command() {
            return COMMAND;
        }
    }

    record Disconnected(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "Disconnected";

        public String command() {
            return COMMAND;
        }
    }

    record EnforceRating(
            List<Object> args
    ) implements Client.HostOnly {
        private static final String COMMAND = "EnforceRating";

        public String command() {
            return COMMAND;
        }
    }

    record GameEnded(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "GameEnded";

        public String command() {
            return COMMAND;
        }
    }

    record GameFull(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "GameFull";

        public String command() {
            return COMMAND;
        }
    }

    record GameMods(
            List<Object> args
    ) implements Client.HostOnly {
        private static final String COMMAND = "GameMods";

        public String command() {
            return COMMAND;
        }
    }

    record GameOption(
            List<Object> args
    ) implements Client.HostOnly {
        private static final String COMMAND = "GameOption";

        public String command() {
            return COMMAND;
        }
    }

    record GameResult(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "GameResult";

        public String command() {
            return COMMAND;
        }
    }

    record GameState(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "GameState";

        public String command() {
            return COMMAND;
        }

        public String state() {
            return (String) args.getFirst();
        }
    }

    record IceMsg(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "IceMsg";

        public String command() {
            return COMMAND;
        }
    }

    record JsonStats(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "JsonStats";

        public String command() {
            return COMMAND;
        }
    }

    record LaunchStatus(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "LaunchStatus";

        public String command() {
            return COMMAND;
        }
    }

    record OperationComplete(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "OperationComplete";

        public String command() {
            return COMMAND;
        }
    }

    record PlayerOption(
            List<Object> args
    ) implements Client.HostOnly {
        private static final String COMMAND = "PlayerOption";

        public String command() {
            return COMMAND;
        }
    }

    record Rehost(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "Rehost";

        public String command() {
            return COMMAND;
        }
    }

    record TeamkillHappened(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "TeamkillHappened";

        public String command() {
            return COMMAND;
        }
    }

    record TeamkillReport(
            List<Object> args
    ) implements Client {
        private static final String COMMAND = "TeamkillReport";

        public String command() {
            return COMMAND;
        }
    }

    record UnknownMessage(
            String command,
            List<Object> args
    ) implements Client {}
    
    record HostGame(
            List<Object> args
    ) implements Server {}

}
