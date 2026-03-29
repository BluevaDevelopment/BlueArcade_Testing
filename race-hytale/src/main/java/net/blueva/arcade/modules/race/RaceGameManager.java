package net.blueva.arcade.modules.race;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.visuals.VisualEffectsAPI;
import net.blueva.arcade.modules.race.state.RaceStateRegistry;
import net.blueva.arcade.modules.race.support.RaceLoadoutService;
import net.blueva.arcade.modules.race.support.RaceMessagingService;
import net.blueva.arcade.modules.race.support.RaceProgressService;
import net.blueva.arcade.modules.race.support.RaceStatsService;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RaceGameManager {

    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final RaceStatsService statsService;
    private final RaceLoadoutService loadoutService;
    private final RaceMessagingService messagingService;
    private final RaceProgressService progressService;
    private final RaceStateRegistry stateRegistry;

    public RaceGameManager(ModuleInfo moduleInfo,
                           ModuleConfigAPI moduleConfig,
                           CoreConfigAPI coreConfig,
                           RaceStatsService statsService) {
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.statsService = statsService;
        this.stateRegistry = new RaceStateRegistry();
        this.loadoutService = new RaceLoadoutService(moduleConfig);
        this.progressService = new RaceProgressService();
        this.messagingService = new RaceMessagingService(moduleInfo, moduleConfig, coreConfig, progressService);
    }

    public void handleStart(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        stateRegistry.registerArena(context);
        capturePlayerPositions(context);
        scheduleSpawnCapture(context);
        startMovementTracking(context);
        applyCountdownMovementSpeed(context);
        messagingService.sendDescription(context);
    }

    public void handleCountdownTick(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                    int secondsLeft) {
        messagingService.sendCountdownTick(context, secondsLeft);
        handleMovementTick(context);
        if (secondsLeft == 1) {
            teleportPlayersToSpawn(context);
        }
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        messagingService.sendCountdownFinished(context);
    }

    public void handleGameStart(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        startGameTimer(context);
        restoreMovementSpeed(context);

        for (Player player : context.getPlayers()) {
            loadoutService.giveStartingItems(player);
            loadoutService.applyStartingEffects(player);
            context.getScoreboardAPI().showModuleScoreboard(player);
        }
    }

    private void startGameTimer(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        int arenaId = context.getArenaId();

        Integer gameTime = context.getDataAccess().getGameData("basic.time", Integer.class);
        if (gameTime == null || gameTime == 0) {
            gameTime = 60;
        }

        final int[] timeLeft = {gameTime};
        final int[] tickCount = {0};

        String taskId = "arena_" + arenaId + "_race_timer";

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (stateRegistry.isEnded(arenaId)) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            tickCount[0]++;

            if (tickCount[0] % 2 == 0) {
                timeLeft[0]--;
            }

            List<Player> alivePlayers = context.getAlivePlayers();
            List<Player> allPlayers = context.getPlayers();
            List<Player> spectators = context.getSpectators();
            int registeredPlayers = context.getArenaAPI().getCurrentPlayers();

            // End game if no players, too many spectators, or time runs out
            if (registeredPlayers < 2 || alivePlayers.isEmpty() || spectators.size() >= 3 || timeLeft[0] <= 0) {
                String reason = String.format(
                        "end-condition met (registered=%d, players=%d, alive=%d, spectators=%d, timeLeft=%d)",
                        registeredPlayers,
                        allPlayers.size(),
                        alivePlayers.size(),
                        spectators.size(),
                        timeLeft[0]
                );
                endGameOnce(context, reason);
                return;
            }

            for (Player player : allPlayers) {
                if (player == null) {
                    continue;
                }

                Map<String, String> customPlaceholders = getCustomPlaceholders(player);
                customPlaceholders.put("time", String.valueOf(timeLeft[0]));
                customPlaceholders.put("round", String.valueOf(context.getCurrentRound()));
                customPlaceholders.put("round_max", String.valueOf(context.getMaxRounds()));

                List<Player> topPlayers = progressService.getTopPlayersByDistance(context);
                customPlaceholders.put("distance_1", topPlayers.size() >= 1 ? progressService.formatDistance(context, topPlayers.get(0)) : "-");
                customPlaceholders.put("distance_2", topPlayers.size() >= 2 ? progressService.formatDistance(context, topPlayers.get(1)) : "-");
                customPlaceholders.put("distance_3", topPlayers.size() >= 3 ? progressService.formatDistance(context, topPlayers.get(2)) : "-");
                customPlaceholders.put("distance_4", topPlayers.size() >= 4 ? progressService.formatDistance(context, topPlayers.get(3)) : "-");
                customPlaceholders.put("distance_5", topPlayers.size() >= 5 ? progressService.formatDistance(context, topPlayers.get(4)) : "-");

                context.getScoreboardAPI().update(player, customPlaceholders);
            }
        }, 0L, 10L);
    }

    public void handleGameTick(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        handleMovementTick(context);
    }

    public void handlePlayerMove(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                 Player player,
                                 Location from,
                                 Location to) {
        if (to == null || !hasBlockChanged(from, to)) {
            return;
        }

        switch (context.getPhase()) {
            case PLAYING -> processActiveMovement(context, player, to);
            default -> {
                if (!context.isInsideBounds(to)) {
                    context.respawnPlayer(player);
                    messagingService.playRespawnSound(context, player);
                    handlePlayerDeath(context, player, false);
                    handlePlayerRespawn(player);
                }
            }
        }
    }

    private void processActiveMovement(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                       Player player,
                                       Location to) {
        // Skip death handling for spectators
        if (context.getSpectators().contains(player)) {
            return;
        }

        // Check bounds
        if (!context.isInsideBounds(to)) {
            playDeathEffect(player);
            context.respawnPlayer(player);
            messagingService.playRespawnSound(context, player);
            handlePlayerDeath(context, player, false);
            handlePlayerRespawn(player);
            return;
        }

        // Check finish line
        if (messagingService.isInsideFinishLine(context, to)) {
            context.finishPlayer(player);

            int position = context.getSpectators().indexOf(player) + 1;

            handlePlayerFinish(player);
            System.out.println("[BlueArcade-race] Finish line crossed by "
                    + player.getDisplayName()
                    + " with position " + position
                    + " (spectators=" + context.getSpectators().size() + ")");
            messagingService.broadcastFinish(context, player, position);
            messagingService.sendFinishTitles(context, player, position);
        }

        // Death blocks disabled in Hytale due to threading constraints
        // Block state reads require world thread, but movement polling runs on scheduler thread
    }

    public void handlePlayerFinish(Player player) {
        Integer arenaId = stateRegistry.getArenaId(player);
        if (arenaId == null) {
            return;
        }

        statsService.recordFinishLineCross(player);

        if (stateRegistry.markWinner(arenaId, player.getUuid())) {
            statsService.recordWin(player);
        }
    }

    public void handlePlayerRespawn(Player player) {
        loadoutService.applyRespawnEffects(player);
    }

    public void handlePlayerDeath(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                  Player player,
                                  boolean deathBlock) {
        messagingService.broadcastDeath(context, player, deathBlock);
    }

    private void endGameOnce(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                             String reason) {
        int arenaId = context.getArenaId();

        if (stateRegistry.markEnded(arenaId)) {
            String moduleId = moduleInfo != null ? moduleInfo.getId() : "race";
            System.out.println("[BlueArcade-" + moduleId + "] Ending game in arena " + arenaId + " because " + reason);
            context.getSchedulerAPI().cancelArenaTasks(arenaId);
            context.endGame();
        }
    }

    private void playDeathEffect(Player player) {
        VisualEffectsAPI visualEffectsAPI = ModuleAPI.getVisualEffectsAPI();
        if (visualEffectsAPI == null || player == null) {
            return;
        }
        GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context = getGameContext(player);
        if (context == null) {
            return;
        }
        context.getSchedulerAPI().runAtEntity(player, () -> {
            Location location = resolvePlayerLocation(player);
            if (location != null) {
                visualEffectsAPI.playDeathEffect(player, location);
            }
        });
    }

    public void handleEnd(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);
        statsService.recordGamePlayed(context.getPlayers());
        stateRegistry.clearArena(arenaId);
    }

    public void handleDisable() {
        stateRegistry.cancelAllSchedulers(moduleInfo.getId());
        stateRegistry.clearAll();
    }

    public GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> getGameContext(Player player) {
        Integer arenaId = stateRegistry.getArenaId(player);
        if (arenaId == null) {
            return null;
        }
        return stateRegistry.getContext(arenaId);
    }

    public Map<String, String> getCustomPlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>();

        GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context = getGameContext(player);
        if (context != null) {
            int position = progressService.calculateLivePosition(context, player);
            placeholders.put("race_position", String.valueOf(position));

            List<Player> topPlayers = progressService.getTopPlayersByDistance(context);
            placeholders.put("place_1", topPlayers.size() >= 1 ? topPlayers.get(0).getDisplayName() : "-");
            placeholders.put("place_2", topPlayers.size() >= 2 ? topPlayers.get(1).getDisplayName() : "-");
            placeholders.put("place_3", topPlayers.size() >= 3 ? topPlayers.get(2).getDisplayName() : "-");
            placeholders.put("place_4", topPlayers.size() >= 4 ? topPlayers.get(3).getDisplayName() : "-");
            placeholders.put("place_5", topPlayers.size() >= 5 ? topPlayers.get(4).getDisplayName() : "-");
        }

        return placeholders;
    }

    private void startMovementTracking(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        int arenaId = context.getArenaId();
        String taskId = "arena_" + arenaId + "_race_movement";
        Location worldLocation = context.getArenaAPI().getRandomSpawn();
        if (worldLocation == null) {
            worldLocation = context.getArenaAPI().getBoundsMin();
        }
        if (worldLocation != null) {
            context.getSchedulerAPI().runTimer(taskId, () -> handleMovementTick(context), 0L, 5L);
        } else {
            context.getSchedulerAPI().runTimer(taskId, () -> handleMovementTick(context), 0L, 5L);
        }
    }

    private void handleMovementTick(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (player == null) {
                continue;
            }
            handleMovementTickForPlayer(context, player);
        }
    }

    private void handleMovementTickForPlayer(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                             Player player) {
        Location current = resolvePlayerLocation(player);
        if (current == null) {
            return;
        }
        Location previous = stateRegistry.getLastPosition(player);
        if (previous != null) {
            handlePlayerMove(context, player, previous, current);
        }
        stateRegistry.updateLastPosition(player, current);
    }

    private void capturePlayerPositions(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (player == null) {
                continue;
            }
            context.getSchedulerAPI().runAtEntity(player, () -> {
                Location location = resolvePlayerLocation(player);
                if (location != null) {
                    stateRegistry.updateLastPosition(player, location);
                    stateRegistry.updateSpawnPosition(player, location);
                }
            });
        }
    }

    private void scheduleSpawnCapture(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        int arenaId = context.getArenaId();
        String taskId = "arena_" + arenaId + "_race_spawn_capture";
        context.getSchedulerAPI().runLater(taskId, () -> refreshSpawnAnchors(context), 1L);
    }

    private void refreshSpawnAnchors(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (player == null) {
                continue;
            }
            context.getSchedulerAPI().runAtEntity(player, () -> {
                Location current = resolvePlayerLocation(player);
                if (current != null) {
                    stateRegistry.updateSpawnPosition(player, current);
                    stateRegistry.updateLastPosition(player, current);
                }
            });
        }
    }

    private void teleportPlayersToSpawn(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (player == null) {
                continue;
            }
            Location spawn = stateRegistry.getSpawnPosition(player);
            if (spawn == null) {
                continue;
            }
            teleportPlayer(context, player, spawn);
        }
    }

    private void applyCountdownMovementSpeed(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        for (Player player : context.getPlayers()) {
            updateMovementSpeedMultiplier(context, player, 0.1f);
        }
    }

    private void restoreMovementSpeed(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        for (Player player : context.getPlayers()) {
            updateMovementSpeedMultiplier(context, player, 1.0f);
        }
    }

    private void updateMovementSpeedMultiplier(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                               Player player,
                                               float multiplier) {
        if (player == null || player.getReference() == null) {
            return;
        }
        context.getSchedulerAPI().runAtEntity(player, () -> {
            if (player.getReference() == null) {
                return;
            }
            Store<EntityStore> store = player.getReference().getStore();
            MovementManager movementManager = store.getComponent(player.getReference(), MovementManager.getComponentType());
            if (movementManager == null) {
                return;
            }
            MovementSettings settings = movementManager.getSettings();
            MovementSettings defaults = movementManager.getDefaultSettings();
            if (settings == null || defaults == null) {
                return;
            }
            settings.baseSpeed = defaults.baseSpeed * multiplier;
            settings.horizontalFlySpeed = defaults.horizontalFlySpeed * multiplier;
            settings.verticalFlySpeed = defaults.verticalFlySpeed * multiplier;
            settings.acceleration = defaults.acceleration * multiplier;
            settings.forwardWalkSpeedMultiplier = defaults.forwardWalkSpeedMultiplier * multiplier;
            settings.backwardWalkSpeedMultiplier = defaults.backwardWalkSpeedMultiplier * multiplier;
            settings.strafeWalkSpeedMultiplier = defaults.strafeWalkSpeedMultiplier * multiplier;
            settings.forwardRunSpeedMultiplier = defaults.forwardRunSpeedMultiplier * multiplier;
            settings.backwardRunSpeedMultiplier = defaults.backwardRunSpeedMultiplier * multiplier;
            settings.strafeRunSpeedMultiplier = defaults.strafeRunSpeedMultiplier * multiplier;
            settings.forwardSprintSpeedMultiplier = defaults.forwardSprintSpeedMultiplier * multiplier;
            settings.airSpeedMultiplier = defaults.airSpeedMultiplier * multiplier;
            settings.maxSpeedMultiplier = defaults.maxSpeedMultiplier * multiplier;
            if (player.getPlayerRef() == null) {
                return;
            }
            PacketHandler packetHandler = player.getPlayerRef().getPacketHandler();
            if (packetHandler == null) {
                return;
            }
            movementManager.update(packetHandler);
        });
    }

    private Location resolvePlayerLocation(Player player) {
        if (player == null || player.getWorld() == null || player.getTransformComponent() == null) {
            return null;
        }
        Vector3d position = player.getTransformComponent().getPosition();
        Vector3f rotation = player.getTransformComponent().getRotation();
        return new Location(player.getWorld().getName(), position.x, position.y, position.z, rotation.x, rotation.y, rotation.z);
    }

    private boolean hasBlockChanged(Location from, Location to) {
        Vector3d fromPos = from.getPosition();
        Vector3d toPos = to.getPosition();
        return (int) Math.floor(fromPos.x) != (int) Math.floor(toPos.x)
                || (int) Math.floor(fromPos.y) != (int) Math.floor(toPos.y)
                || (int) Math.floor(fromPos.z) != (int) Math.floor(toPos.z);
    }

    private void teleportPlayer(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                Player player,
                                Location location) {
        if (player == null || location == null) {
            return;
        }
        context.getSchedulerAPI().runAtEntity(player, () -> {
            Vector3d position = location.getPosition();
            Vector3f rotation = location.getRotation();
            player.getTransformComponent().teleportPosition(position);
            if (rotation != null) {
                player.getTransformComponent().teleportRotation(rotation);
            }
        });
    }
}
