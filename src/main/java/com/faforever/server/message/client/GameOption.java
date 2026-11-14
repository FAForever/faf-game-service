package com.faforever.server.message.client;

import com.faforever.server.message.client.dto.KeyValue;
import com.faforever.server.message.client.dto.OptionValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public sealed interface GameOption extends GPGMessage.Client.HostOnly {

    @JsonCreator
    static GameOption of(KeyValue keyValue) {
        final OptionValue value = keyValue.value();
        return switch (keyValue.key().toLowerCase(Locale.ROOT)) {
            case "victory" -> Victory.of(value.strValue());
            case "unranked" -> Unranked.of(value.strValue());
            case "teamlock" -> TeamLock.of(value.strValue());
            case "gamespeed" -> GameSpeed.of(value.strValue());
            case "prebuiltunits" -> PrebuiltUnits.of(value.strValue());
            case "cheatsenabled" -> CheatsEnabled.of(value.strValue());
            case "fogofwar" -> FogOfWar.of(value.strValue());
            case "norushoption" -> NoRushOption.of(value);
            case "share" -> Share.of(value.strValue());
            case "scenariofile" -> new ScenarioFile(Path.of(value.strValue()));
            default -> new Other(keyValue.key(), value);
        };
    }

    record ScenarioFile(Path value) implements GameOption {}

    record Victory(Value value) implements GameOption {

        sealed interface Value {}

        enum Known implements Value {
            DEMORALIZATION, DOMINATION, ERADICATION, SANDBOX
        }

        static Victory of(String value) {
            return new Victory(findValueOrUnknown(value, Known.class));
        }
    }

    record Unranked(Value value) implements GameOption {

        sealed interface Value {}

        enum Known implements Value {
            TRUE, FALSE
        }

        static Unranked of(String value) {
            Boolean enabled = parseToggle(value);
            return new Unranked(enabled == null ? new Unknown(value) : enabled ? Known.TRUE : Known.FALSE);
        }
    }

    record CheatsEnabled(Value value) implements GameOption {

        sealed interface Value {}

        enum Known implements Value {
            TRUE, FALSE
        }

        static CheatsEnabled of(String value) {
            Boolean enabled = parseToggle(value);
            return new CheatsEnabled(enabled == null ? new Unknown(value) : enabled ? Known.TRUE : Known.FALSE);
        }
    }

    record FogOfWar(Value value) implements GameOption {

        sealed interface Value {}

        enum Known implements Value {
            NONE, EXPLORED
        }

        static FogOfWar of(String value) {
            return new FogOfWar(findValueOrUnknown(value, FogOfWar.Known.class));
        }
    }

    record TeamLock(Value value) implements GameOption {

        sealed interface Value {}

        enum Known implements Value {
            LOCKED, UNLOCKED
        }

        static TeamLock of(String value) {
            return new TeamLock(findValueOrUnknown(value, TeamLock.Known.class));
        }
    }

    record GameSpeed(Value value) implements GameOption {

        sealed interface Value {}

        enum Known implements GameSpeed.Value {
            NORMAL, FAST, ADJUSTABLE
        }

        static GameSpeed of(String value) {
            return new GameSpeed(findValueOrUnknown(value, GameSpeed.Known.class));
        }
    }

    record PrebuiltUnits(Value value) implements GameOption {

        sealed interface Value {}

        enum Known implements Value {
            OFF, ON
        }

        static PrebuiltUnits of(String value) {
            return new PrebuiltUnits(findValueOrUnknown(value, PrebuiltUnits.Known.class));
        }
    }

    /**
     * @param value the no-rush time; 0 when off
     */
    record NoRushOption(int value) implements GameOption {

        static NoRushOption of(OptionValue value) {
            // The lobby sends "Off" when disabled; the server treats it the same as 0
            if (value instanceof OptionValue.Str(String strValue) && strValue.equalsIgnoreCase("off")) {
                return new NoRushOption(0);
            }
            return new NoRushOption(value.intValue());
        }
    }

    record Share(Value value) implements GameOption {

        sealed interface Value {}

        enum Known implements Value {
            FULL_SHARE, SHARE_UNTIL_DEATH, PARTIAL_SHARE, TRANSFER_TO_KILLER, DEFECTORS, CIVILIAN_DESERT
        }

        static Share of(String value) {
            return new Share(switch (value.toLowerCase(Locale.ROOT)) {
                case "fullshare" -> Known.FULL_SHARE;
                case "shareuntildeath" -> Known.SHARE_UNTIL_DEATH;
                case "partialshare" -> Known.PARTIAL_SHARE;
                case "transfertokiller" -> Known.TRANSFER_TO_KILLER;
                case "defectors" -> Known.DEFECTORS;
                case "civiliandesert" -> Known.CIVILIAN_DESERT;
                default -> new Unknown(value);
            });
        }
    }

    record Unknown(String value)
            implements PrebuiltUnits.Value, GameSpeed.Value, TeamLock.Value, Victory.Value, Unranked.Value, Share.Value,
            FogOfWar.Value, CheatsEnabled.Value {}

    record Other(
            String key,
            OptionValue value
    ) implements GameOption {}

    /**
     * On/off option values. The server treats false, "false", "off", "no" and 0 as disabled (case-insensitive); the
     * matching enabled values are accepted too.
     *
     * @return whether the option is enabled, or null if the value is neither
     */
    private static @Nullable Boolean parseToggle(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "1" -> true;
            case "false", "off", "no", "0" -> false;
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Enum<E>> T findValueOrUnknown(String value, Class<E> enumClass) {
        try {
            return (T) Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            return (T) new Unknown(value);
        }
    }
}
