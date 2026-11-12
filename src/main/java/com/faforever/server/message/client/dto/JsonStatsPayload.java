package com.faforever.server.message.client.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The JSON document inside the JsonStats GPG message: lua/sim/score.lua's ArmyScore table at game over, encoded by
 * lua/system/dkson.lua.
 *
 * @param stats one entry per non-civilian army in army index order; a civilian army between two players leaves a
 *              null
 * @see <a href="https://github.com/Draiget/faf-re/blob/master/docs/GPGNET_MESSAGES.md#4-jsonstats-payload">
 *         faf-re GPGNET_MESSAGES.md, JsonStats payload</a>
 */
public record JsonStatsPayload(
        List<@Nullable ArmyStats> stats
) {

    /**
     * @param defeated   present once the army is defeated: game seconds + 15, later -1
     * @param units      per unit category: land, air, naval, cdr, experimental, structures during the game;
     *                   transportation, sacu, engineer, tech1, tech2, tech3 added at game over
     * @param blueprints per unit blueprint id, only for units the server's achievements count
     */
    public record ArmyStats(
            String name,
            BrainType type,
            Faction faction,
            @JsonProperty("Defeated") @Nullable Double defeated,
            General general,
            Map<String, KillsBuiltLost> units,
            @Nullable Map<String, BlueprintStats> blueprints,
            Resources resources
    ) {}

    public sealed interface BrainType {
        enum Known implements BrainType {
            HUMAN, AI
        }

        record Unknown(String value) implements BrainType {}

        @JsonCreator
        static BrainType fromValue(String value) {
            for (Known known : Known.values()) {
                if (known.name().equalsIgnoreCase(value)) {
                    return known;
                }
            }
            return new Unknown(value);
        }
    }

    // Below, only KillsBuiltLost's fields are required by the schema; everything else may be absent

    public record General(
            @Nullable Integer score,
            @JsonProperty("lastupdatetick") @Nullable Integer lastUpdateTick,
            @JsonProperty("currentunits") @Nullable Integer currentUnits,
            @JsonProperty("currentcap") @Nullable Integer currentCap,
            @Nullable CountMassEnergy kills,
            @Nullable CountMassEnergy built,
            @Nullable CountMassEnergy lost
    ) {}

    public record CountMassEnergy(
            @Nullable Integer count,
            @Nullable Double mass,
            @Nullable Double energy
    ) {}

    public record KillsBuiltLost(
            int kills,
            int built,
            int lost
    ) {}

    /**
     * @param lowestHealth ACUs only: the lowest health the ACU had
     */
    public record BlueprintStats(
            int kills,
            int built,
            int lost,
            @JsonProperty("lowest_health") @Nullable Double lowestHealth
    ) {}

    public record Resources(
            @JsonProperty("massin") @Nullable ResourceIn massIn,
            @JsonProperty("energyin") @Nullable ResourceIn energyIn,
            @JsonProperty("massout") @Nullable ResourceOut massOut,
            @JsonProperty("energyout") @Nullable ResourceOut energyOut,
            @Nullable Storage storage
    ) {}

    public record ResourceIn(
            @Nullable Double total,
            @Nullable Double rate,
            @Nullable Double reclaimed,
            @JsonProperty("reclaimRate") @Nullable Double reclaimRate
    ) {}

    public record ResourceOut(
            @Nullable Double total,
            @Nullable Double rate,
            @Nullable Double excess
    ) {}

    public record Storage(
            @JsonProperty("storedMass") @Nullable Double storedMass,
            @JsonProperty("storedEnergy") @Nullable Double storedEnergy,
            @JsonProperty("maxMass") @Nullable Double maxMass,
            @JsonProperty("maxEnergy") @Nullable Double maxEnergy
    ) {}
}
