package com.faforever.server.message.client;

import com.faforever.server.jackson.CommaSeparatedIntDeserializer;
import com.faforever.server.jackson.EmbeddedJsonDeserializer;
import com.faforever.server.jackson.GPGMessageDeserializer;
import com.faforever.server.message.client.dto.JsonStatsPayload;
import com.faforever.server.message.client.dto.KeyValue;
import com.faforever.server.message.client.dto.OptionValue;
import com.faforever.server.message.client.dto.SlotOption;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.Collection;
import java.util.List;

public sealed interface GPGMessage {

    record Wrapped<T extends GPGMessage>(
            @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "command",
                          include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
            @JsonProperty("args")
            @JsonDeserialize(using = GPGMessageDeserializer.class)
            T content
    ) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "command")
    @JsonSubTypes({@JsonSubTypes.Type(value = HostGame.class),

    })
    sealed interface Server extends GPGMessage {}

    @JsonSubTypes({@JsonSubTypes.Type(value = AIOption.class), @JsonSubTypes.Type(value = Bottleneck.class),
                   @JsonSubTypes.Type(value = BottleneckCleared.class), @JsonSubTypes.Type(value = Chat.class),
                   @JsonSubTypes.Type(value = ClearSlot.class), @JsonSubTypes.Type(value = Desync.class),
                   @JsonSubTypes.Type(value = Disconnected.class), @JsonSubTypes.Type(value = EstablishedPeer.class),
                   @JsonSubTypes.Type(value = DisconnectedPeer.class), @JsonSubTypes.Type(value = EnforceRating.class),
                   @JsonSubTypes.Type(value = GameEnded.class), @JsonSubTypes.Type(value = GameFull.class),
                   @JsonSubTypes.Type(value = GameMods.class), @JsonSubTypes.Type(value = GameOption.class),
                   @JsonSubTypes.Type(value = GameResult.class), @JsonSubTypes.Type(value = GameState.class),
                   @JsonSubTypes.Type(value = IceMsg.class), @JsonSubTypes.Type(value = JsonStats.class),
                   @JsonSubTypes.Type(value = XMLStats.class), @JsonSubTypes.Type(value = ProcessNatPacket.class),
                   @JsonSubTypes.Type(value = LaunchStatus.class), @JsonSubTypes.Type(value = OperationComplete.class),
                   @JsonSubTypes.Type(value = PlayerOption.class), @JsonSubTypes.Type(value = Rehost.class),
                   @JsonSubTypes.Type(value = Beat.class), @JsonSubTypes.Type(value = TeamkillHappened.class),
                   @JsonSubTypes.Type(value = TeamkillReport.class),})
    sealed interface Client extends GPGMessage {

        sealed interface HostOnly extends GPGMessage.Client
                permits AIOption, ClearSlot, GameMods, PlayerOption, GameOption {}

    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record AIOption(
            String aiName,
            SlotOption option
    ) implements Client.HostOnly {

        @JsonCreator
        public static AIOption of(Args args) {
            return new AIOption(args.aiName(), SlotOption.of(args.key(), args.value()));
        }

        @JsonFormat(shape = JsonFormat.Shape.ARRAY)
        public record Args(String aiName, String key, OptionValue value) {}
    }

    record PlayerOption(
            int playerId,
            SlotOption option
    ) implements Client.HostOnly {

        @JsonCreator
        public static PlayerOption of(Args args) {
            return new PlayerOption(args.playerId(), SlotOption.of(args.key(), args.value()));
        }

        @JsonFormat(shape = JsonFormat.Shape.ARRAY)
        public record Args(int playerId, String key, OptionValue value) {}
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Bottleneck(
            Kind kind,
            long beatNumber,
            @JsonDeserialize(using = CommaSeparatedIntDeserializer.class)
            Collection<Integer> ids,
            double milliseconds
    ) implements Client {
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
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Chat(
            String message
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record ClearSlot(
            int slot
    ) implements Client.HostOnly {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Desync(
            int beat,
            int army,
            String hash1,
            String hash2
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Disconnected(
            String uid
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record EstablishedPeer(
            String uid
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record DisconnectedPeer(
            String uid
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record EnforceRating() implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record GameEnded() implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record GameFull() implements Client {}

    sealed interface GameMods extends Client.HostOnly {
        @JsonCreator
        static GameMods from(KeyValue keyValue) {
            final String key = keyValue.key();
            return switch (keyValue.value()) {
                case OptionValue.Str(String strValue) when key.equalsIgnoreCase("uids") ->
                        new Uids(List.of(strValue.split(" ")));
                case OptionValue.Int(int intValue) when key.equalsIgnoreCase("activated") -> new Activated(intValue);
                default -> throw new IllegalArgumentException("Unknown GameMods type");
            };
        }

        record Activated(int count) implements GameMods {}

        record Uids(Collection<String> uids) implements GameMods {}

    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record GameResult(
            int army,
            String result
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record GameState(
            State state
    ) implements Client {
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
            int receiverId,
            String iceMessage
    ) implements Client {}

    /**
     * End-of-game army statistics, sent once by every client that is not watching a replay. The single arg is a JSON
     * document: lua/sim/score.lua's ArmyScore table at game over, encoded by lua/system/dkson.lua.
     */
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record JsonStats(
            @JsonDeserialize(using = EmbeddedJsonDeserializer.class)
            JsonStatsPayload payload
    ) implements Client {}

    @JsonTypeName("Stats")
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record XMLStats(
            String xml
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record ProcessNatPacket(
            String fromIpPort,
            byte[] data
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record LaunchStatus(
            Status status
    ) implements Client {
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
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Rehost(
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Beat(
            int tick,
            int gameSpeed
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record TeamkillHappened(
            int time,
            String victimId,
            String victimName,
            String killerId,
            String killerName
    ) implements Client {}

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record TeamkillReport(
            int time,
            String victimId,
            String victimName,
            String killerId,
            String killerName
    ) implements Client {}

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
    ) implements Server {}

}
