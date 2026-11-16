package com.faforever.server.game;

import com.faforever.server.game.dto.FAFMap;
import com.faforever.server.game.dto.SimMod;
import com.faforever.server.game.dto.SlotHolder;
import com.faforever.server.message.client.GPGMessage;
import com.faforever.server.message.client.GameOption;
import com.faforever.server.message.client.dto.OptionValue;
import com.faforever.server.message.client.dto.SlotOption;
import jakarta.enterprise.context.Dependent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Dependent
@RequiredArgsConstructor
class GameSettings {

    private final GameOptionsRepository gameOptionsRepository;

    private final Map<Integer, SlotHolder> slotHolderMap = new ConcurrentHashMap<>();
    private final Map<SlotHolder.Occupied, SlotProperties> slotHolderProperties = new ConcurrentHashMap<>();
    private final Map<String, OptionValue> otherOptions = new ConcurrentHashMap<>();

    @Getter
    private String gameTitle = "";
    @Getter
    private FAFMap map = new FAFMap.Vault(558, "scmp_007", true);
    private GameOption.AIReplacement.Value aiReplacement = GameOption.TrueFalse.FALSE;
    private GameOption.RestrictedCategories.Value restrictionsEnabled = GameOption.TrueFalse.FALSE;
    private GameOption.CheatsEnabled.Value cheatsEnabled = GameOption.TrueFalse.FALSE;
    private GameOption.Share.Value share = GameOption.Share.Known.SHARE_UNTIL_DEATH;
    private GameOption.Unranked.Value unranked = GameOption.TrueFalse.FALSE;
    private GameOption.TeamLock.Value teamLock = GameOption.TeamLock.Known.LOCKED;
    private GameOption.FogOfWar.Value fogOfWar = GameOption.FogOfWar.Known.EXPLORED;
    private GameOption.GameSpeed.Value gameSpeed = GameOption.GameSpeed.Known.NORMAL;
    private GameOption.PrebuiltUnits.Value prebuildUnits = GameOption.TrueFalse.FALSE;
    private GameOption.Victory.Value victory = GameOption.Victory.Known.DEMORALIZATION;
    private int noRush = 0;
    private Set<SimMod> simMods = Set.of();

    private boolean launched = false;

    void handleGameSetup(GPGMessage.Client.GameSetup gameSetup) {
        if (launched) {
            LOG.warn("Received game setup message after game launch, ignoring: {}", gameSetup);
            return;
        }

        switch (gameSetup) {
            case GPGMessage.AIOption aiOption -> handleAIOption(aiOption);
            case GPGMessage.PlayerOption playerOption -> handlePlayerOption(playerOption);
            case GameOption gameOption -> handleGameOption(gameOption);
            case GPGMessage.ClearSlot(int slot) -> clearSlot(slot);
            case GPGMessage.GameMods.Uids(Collection<String> uids) -> setSimMods(uids);
            case GPGMessage.GameMods.Activated(int count) -> {
                if (count == 0) {
                clearSimMods();
                }
            }
        }
    }

    private void handleGameOption(GameOption gameOption) {
        switch (gameOption) {
            case GameOption.Unranked(GameOption.Unranked.Value value) -> unranked = value;
            case GameOption.Share(GameOption.Share.Value value) -> share = value;
            case GameOption.CheatsEnabled(GameOption.CheatsEnabled.Value value) -> cheatsEnabled = value;
            case GameOption.RestrictedCategories(GameOption.RestrictedCategories.Value value) ->
                    restrictionsEnabled = value;
            case GameOption.AIReplacement(GameOption.AIReplacement.Value value) -> aiReplacement = value;
            case GameOption.ScenarioFile(Path value) -> setMapName(value.getName(1).toString());
            case GameOption.FogOfWar(GameOption.FogOfWar.Value value) -> fogOfWar = value;
            case GameOption.GameSpeed(GameOption.GameSpeed.Value value) -> gameSpeed = value;
            case GameOption.NoRushOption(int value) -> noRush = value;
            case GameOption.PrebuiltUnits(GameOption.PrebuiltUnits.Value value) -> prebuildUnits = value;
            case GameOption.TeamLock(GameOption.TeamLock.Value value) -> teamLock = value;
            case GameOption.Victory(GameOption.Victory.Value value) -> victory = value;
            case GameOption.Slots(int value) -> {
                Set<Integer> existingSlots = Set.copyOf(slotHolderMap.keySet());
                IntStream.range(1, value + 1)
                         .forEach(slot -> slotHolderMap.putIfAbsent(slot, SlotHolder.Empty.INSTANCE));
                existingSlots.stream().filter(slot -> slot > value).forEach(this::clearSlot);
            }
            case GameOption.Title(String value) -> gameTitle = value;
            case GameOption.Other(String key, OptionValue value) -> otherOptions.put(key, value);
        }
    }

    private void handlePlayerOption(GPGMessage.PlayerOption playerOption) {
        SlotHolder.Player slotHolder = new SlotHolder.Player(playerOption.playerId());
        handleSlotOption(playerOption.option(), slotHolder);
    }

    private void handleAIOption(GPGMessage.AIOption aiOption) {
        SlotHolder.AI slotHolder = new SlotHolder.AI(aiOption.aiName());
        handleSlotOption(aiOption.option(), slotHolder);
    }

    private void handleSlotOption(SlotOption option, SlotHolder.Occupied slotHolder) {
        if (option instanceof SlotOption.Slot(int value) && value > 0) {
            SlotHolder previousSlotHolder = slotHolderMap.put(value, slotHolder);
            if (previousSlotHolder instanceof SlotHolder.AI aiHolder) {
                slotHolderProperties.remove(aiHolder);
            }
        }

        slotHolderProperties.computeIfAbsent(slotHolder, _ -> new SlotProperties()).handleSlotOption(option);
    }

    private void clearSlot(int slot) {
        SlotHolder previousSlotHolder = slotHolderMap.put(slot, SlotHolder.Empty.INSTANCE);
        if (previousSlotHolder instanceof SlotHolder.AI aiHolder) {
            slotHolderProperties.remove(aiHolder);
        }
    }

    private void setSimMods(Collection<String> simMods) {
        this.simMods = simMods.stream().map(gameOptionsRepository::findSimModOrUnknown).collect(Collectors.toSet());
    }

    private void clearSimMods() {
        simMods = Set.of();
    }

    void removePlayer(int playerId) {
        if (launched) {
            LOG.warn("Cannot remove player after launch");
            return;
        }
        SlotHolder.Player slotHolder = new SlotHolder.Player(playerId);
        SlotProperties removedSlotProperties = slotHolderProperties.remove(slotHolder);
        slotHolderMap.remove(removedSlotProperties.getSlot(), slotHolder);
    }

    Map<Integer, Set<Integer>> getTeamPlayerMap() {
        Map<Integer, Set<Integer>> map = new HashMap<>();
        slotHolderProperties.forEach((key, value) -> {
            if (key instanceof SlotHolder.Player(int playerId)) {
                map.computeIfAbsent(value.getTeamIndex(), _ -> new HashSet<>()).add(playerId);
            }
        });
        return map;
    }

    void setMapName(String mapName) {
        if (launched) {
            LOG.warn("Cannot change map after launch");
            return;
        }

        if (mapName.startsWith(FAFMap.Neroxis.PREFIX)) {
            map = new FAFMap.Neroxis(mapName);
        } else {
            map = gameOptionsRepository.findMapOrUnknown(mapName);
        }
    }

    void setGameTitle(String gameTitle) {
        if (launched) {
            LOG.warn("Cannot change title after launch");
            return;
        }

        this.gameTitle = gameTitle;
    }

    int getNumSlots() {
        return slotHolderMap.size();
    }

    public Map<String, String> getSimMods() {
        return simMods.stream().collect(Collectors.toMap(SimMod::uid, SimMod::name));
    }

    void markLaunched() {
        launched = true;
    }
}
