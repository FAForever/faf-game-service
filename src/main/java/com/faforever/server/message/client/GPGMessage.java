package com.faforever.server.message.client;

import com.faforever.server.jackson.CommaSeparatedIntDeserializer;
import com.faforever.server.jackson.EmbeddedJsonDeserializer;
import com.faforever.server.jackson.GPGClientMessageDeserializer;
import com.faforever.server.message.client.dto.JsonStatsPayload;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.Collection;
import java.util.List;

public sealed interface GPGMessage {

    record Wrapped<T extends GPGMessage>(
            @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command", include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
            @JsonProperty("args")
            @JsonDeserialize(using = GPGClientMessageDeserializer.class)
            T content
    ) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes({@JsonSubTypes.Type(value = HostGame.class, name = HostGame.COMMAND),

    })
    sealed interface Server extends GPGMessage {}

    @JsonSubTypes({
            @JsonSubTypes.Type(value = AIOption.class, name = AIOption.COMMAND),
            @JsonSubTypes.Type(value = Bottleneck.class, name = Bottleneck.COMMAND),
            @JsonSubTypes.Type(value = BottleneckCleared.class, name = BottleneckCleared.COMMAND),
            @JsonSubTypes.Type(value = Chat.class, name = Chat.COMMAND),
            @JsonSubTypes.Type(value = ClearSlot.class, name = ClearSlot.COMMAND),
            @JsonSubTypes.Type(value = Desync.class, name = Desync.COMMAND),
            @JsonSubTypes.Type(value = Disconnected.class, name = Disconnected.COMMAND),
            @JsonSubTypes.Type(value = EstablishedPeer.class, name = EstablishedPeer.COMMAND),
            @JsonSubTypes.Type(value = DisconnectedPeer.class, name = DisconnectedPeer.COMMAND),
            @JsonSubTypes.Type(value = EnforceRating.class, name = EnforceRating.COMMAND),
            @JsonSubTypes.Type(value = GameEnded.class, name = GameEnded.COMMAND),
            @JsonSubTypes.Type(value = GameFull.class, name = GameFull.COMMAND),
            @JsonSubTypes.Type(value = GameMods.class, name = GameMods.COMMAND),
            @JsonSubTypes.Type(value = GameOption.class, name = GameOption.COMMAND),
            @JsonSubTypes.Type(value = GameResult.class, name = GameResult.COMMAND),
            @JsonSubTypes.Type(value = GameState.class, name = GameState.COMMAND),
            @JsonSubTypes.Type(value = IceMsg.class, name = IceMsg.COMMAND),
            @JsonSubTypes.Type(value = JsonStats.class, name = JsonStats.COMMAND),
            @JsonSubTypes.Type(value = XMLStats.class, name = XMLStats.COMMAND),
            @JsonSubTypes.Type(value = ProcessNatPacket.class, name = ProcessNatPacket.COMMAND),
            @JsonSubTypes.Type(value = LaunchStatus.class, name = LaunchStatus.COMMAND),
            @JsonSubTypes.Type(value = OperationComplete.class, name = OperationComplete.COMMAND),
            @JsonSubTypes.Type(value = PlayerOption.class, name = PlayerOption.COMMAND),
            @JsonSubTypes.Type(value = Rehost.class, name = Rehost.COMMAND),
            @JsonSubTypes.Type(value = Beat.class, name = Beat.COMMAND),
            @JsonSubTypes.Type(value = TeamkillHappened.class, name = TeamkillHappened.COMMAND),
            @JsonSubTypes.Type(value = TeamkillReport.class, name = TeamkillReport.COMMAND),
    })
    sealed interface Client extends GPGMessage {

        String command();

        sealed interface HostOnly extends GPGMessage.Client {}

    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record AIOption(
            String aiName,
            String key,
            OptionValue value
    ) implements Client.HostOnly {

        public static final String COMMAND = "AIOption";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Bottleneck(
            Kind kind,
            long beatNumber,
            @JsonDeserialize(using = CommaSeparatedIntDeserializer.class) Collection<Integer> ids,
            double milliseconds
    ) implements Client {
        public static final String COMMAND = "Bottleneck";

        public String command() {
            return COMMAND;
        }

        public sealed interface Kind {

            enum Known implements Kind {
                NOTHING, READINESS, DATA, ACK
            }

            record Unknown(String value) implements Kind {}

            @JsonCreator
            static Kind fromValue(String value) {
                for (Kind.Known known : Kind.Known.values()) {
                    if (known.name().equalsIgnoreCase(value)) {
                        return known;
                    }
                }
                return new Kind.Unknown(value);
            }

        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record BottleneckCleared(
    ) implements Client {
        public static final String COMMAND = "BottleneckCleared";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Chat(
            String message
    ) implements Client {
        public static final String COMMAND = "Chat";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record ClearSlot(
            int slot
    ) implements Client.HostOnly {
        public static final String COMMAND = "ClearSlot";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Desync(
            int beat,
            int army,
            String hash1,
            String hash2
    ) implements Client {
        public static final String COMMAND = "Desync";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Disconnected(
            String uid
    ) implements Client {
        public static final String COMMAND = "Disconnected";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record EstablishedPeer(
            String uid
    ) implements Client {
        public static final String COMMAND = "EstablishedPeer";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record DisconnectedPeer(
            String uid
    ) implements Client {
        public static final String COMMAND = "DisconnectedPeer";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record EnforceRating() implements Client {
        public static final String COMMAND = "EnforceRating";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record GameEnded() implements Client {
        public static final String COMMAND = "GameEnded";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record GameFull() implements Client {
        public static final String COMMAND = "GameFull";

        public String command() {
            return COMMAND;
        }
    }

    sealed interface GameMods extends Client.HostOnly {
        String COMMAND = "GameMods";

        default String command() {
            return COMMAND;
        }

        @JsonCreator
        static GameMods from(TypeArg typeArg) {
            return switch (typeArg.arg()) {
                case String uids when "uids".equalsIgnoreCase(typeArg.type()) -> new Uids(List.of(uids.split(" ")));
                case Integer count when "activated".equalsIgnoreCase(typeArg.type()) -> new Activated(count);
                default -> throw new IllegalArgumentException("Unknown GameMods type");
            };
        }

        record Activated(int count) implements GameMods {}

        record Uids(Collection<String> uids) implements GameMods {}

        @JsonFormat(shape = JsonFormat.Shape.ARRAY)
        record TypeArg(
                String type,
                Object arg
        ) {}
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record GameOption(
            String key,
            OptionValue value
    ) implements Client.HostOnly {
        public static final String COMMAND = "GameOption";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record GameResult(
            int army,
            String result
    ) implements Client {
        public static final String COMMAND = "GameResult";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record GameState(
            State state
    ) implements Client {
        public static final String COMMAND = "GameState";

        public String command() {
            return COMMAND;
        }

        public sealed interface State {
            enum Known implements State {
                IDLE, LOBBY, LAUNCHING
            }

            record Unknown(String value) implements State {}

            @JsonCreator
            static State fromValue(String value) {
                for (Known known : Known.values()) {
                    if (known.name().equalsIgnoreCase(value)) {
                        return known;
                    }
                }
                return new Unknown(value);
            }
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record IceMsg(
            List<Object> args
    ) implements Client {
        public static final String COMMAND = "IceMsg";

        public String command() {
            return COMMAND;
        }
    }

    /**
     * End-of-game army statistics, sent once by every client that is not watching a replay. The single arg is a JSON
     * document: lua/sim/score.lua's ArmyScore table at game over, encoded by lua/system/dkson.lua.
     *
     * @see <a href="https://github.com/Draiget/faf-re/blob/master/docs/GPGNET_MESSAGES.md#4-jsonstats-payload">
     *         faf-re GPGNET_MESSAGES.md, JsonStats payload</a>
     */
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record JsonStats(
            @JsonDeserialize(using = EmbeddedJsonDeserializer.class) JsonStatsPayload payload
    ) implements Client {
        public static final String COMMAND = "JsonStats";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record XMLStats(
            String xml
    ) implements Client {
        public static final String COMMAND = "Stats";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record ProcessNatPacket(
            String fromIpPort,
            byte[] data
    ) implements Client {
        public static final String COMMAND = "ProcessNatPacket";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record LaunchStatus(
            Status status
    ) implements Client {
        public static final String COMMAND = "LaunchStatus";

        public String command() {
            return COMMAND;
        }

        public sealed interface Status {
            enum Known implements LaunchStatus.Status {
                CONNECTING, MISSING_LOCAL_PEERS, REJOINING, READY, EJECTED, REJECTED
            }

            record Unknown(String value) implements LaunchStatus.Status {}

            @JsonCreator
            static Status fromValue(String value) {
                for (Status.Known known : Status.Known.values()) {
                    if (known.name().replace("_", " ").equalsIgnoreCase(value)) {
                        return known;
                    }
                }
                return new Status.Unknown(value);
            }
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record OperationComplete(
            boolean primary,
            boolean secondary,
            String time
    ) implements Client {
        public static final String COMMAND = "OperationComplete";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record PlayerOption(
            int playerId,
            String key,
            OptionValue value
    ) implements Client.HostOnly {
        public static final String COMMAND = "PlayerOption";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Rehost(
    ) implements Client {
        public static final String COMMAND = "Rehost";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Beat(
            int tick,
            int gameSpeed
    ) implements Client {
        public static final String COMMAND = "BEAT";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record TeamkillHappened(
            int time,
            String victimId,
            String victimName,
            String killerId,
            String killerName
    ) implements Client {
        public static final String COMMAND = "TeamkillHappened";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record TeamkillReport(
            int time,
            String victimId,
            String victimName,
            String killerId,
            String killerName
    ) implements Client {
        public static final String COMMAND = "TeamkillReport";

        public String command() {
            return COMMAND;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Unknown(
            String command,
            List<Object> args
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Invalid(
            String command,
            List<Object> args
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record HostGame(
            List<Object> args
    ) implements Server {

        public static final String COMMAND = "HostGame";

    }

    sealed interface OptionValue {
        record Str(String value) implements OptionValue {}

        record Int(int value) implements OptionValue {}

        record Unknown(Object value) implements OptionValue {}

        @JsonCreator
        static OptionValue fromValue(Object value) {
            return switch (value) {
                case String strValue -> new OptionValue.Str(strValue);
                case Integer intValue -> new OptionValue.Int(intValue);
                default -> new Unknown(value);
            };
        }
    }
}
