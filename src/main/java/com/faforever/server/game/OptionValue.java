package com.faforever.server.game;

import java.util.Locale;

public sealed interface OptionValue {

    Object value();

    sealed interface Victory extends OptionValue {
        String KEY = "victory";

        enum Known implements Victory {
            DEMORALIZATION, DOMINATION, ERADICATION, SANDBOX;

            public String value() {
                return name().toLowerCase(Locale.ROOT);
            }
        }

        static Victory from(Object value) {
            return switch (value) {
                case String strValue -> findValueOrUnknown(strValue, Known.class);
                default -> new Unknown(value);
            };
        }
    }

    sealed interface Unranked extends OptionValue {
        String KEY = "unranked";

        enum Known implements Unranked {
            TRUE, FALSE;

            public String value() {
                return name().toLowerCase(Locale.ROOT);
            }
        }
    }

    sealed interface TeamLock extends OptionValue {
        String KEY = "teamlock";

        enum Known implements TeamLock {
            LOCKED, UNLOCKED;

            public String value() {
                return name().toLowerCase(Locale.ROOT);
            }
        }
    }

    sealed interface GameSpeed extends OptionValue {
        String KEY = "gamespeed";

        enum Known implements GameSpeed {
            NORMAL, FAST, ADJUSTABLE;

            public String value() {
                return name().toLowerCase(Locale.ROOT);
            }
        }
    }

    sealed interface PrebuiltUnits extends OptionValue {
        enum Known implements PrebuiltUnits {
            OFF, ON;

            public String value() {
                return name().toLowerCase(Locale.ROOT);
            }
        }
    }

    sealed interface Team extends OptionValue {
        record Known(Integer value) implements Team {}
    }

    record Unknown(Object value) implements Team, PrebuiltUnits, GameSpeed, TeamLock, Victory, Unranked {}

    @SuppressWarnings("unchecked")
    static <T, E extends Enum<E>> T findValueOrUnknown(Object value, Class<E> enumClass) {
        if (!(value instanceof String strValue)) {
            return (T) new Unknown(value);
        }

        try {
            return (T) Enum.valueOf(enumClass, strValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            return (T) new Unknown(value);
        }
    }
}
