package net.blueva.arcade.modules.race.state;

import net.blueva.arcade.api.game.GameContext;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Holder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RaceStateRegistry {

    private final Map<Integer, RaceArenaState> arenas = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerArenas = new ConcurrentHashMap<>();

    public void registerArena(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        int arenaId = context.getArenaId();
        arenas.put(arenaId, new RaceArenaState(context));
        context.getPlayers().forEach(player -> playerArenas.put(player.getUuid(), arenaId));
    }

    public Integer getArenaId(Player player) {
        return playerArenas.get(player.getUuid());
    }

    public GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> getContext(int arenaId) {
        RaceArenaState state = arenas.get(arenaId);
        return state != null ? state.getContext() : null;
    }

    public boolean isEnded(int arenaId) {
        RaceArenaState state = arenas.get(arenaId);
        return state != null && state.isEnded();
    }

    public boolean markEnded(int arenaId) {
        RaceArenaState state = arenas.get(arenaId);
        if (state == null || state.isEnded()) {
            return false;
        }

        state.markEnded();
        return true;
    }

    public boolean markWinner(int arenaId, UUID winner) {
        RaceArenaState state = arenas.get(arenaId);
        if (state == null || state.getWinner() != null) {
            return false;
        }

        state.setWinner(winner);
        return true;
    }

    public void clearArena(int arenaId) {
        RaceArenaState state = arenas.remove(arenaId);
        if (state != null) {
            state.getContext().getPlayers().forEach(player -> playerArenas.remove(player.getUuid()));
        }
    }

    public void clearAll() {
        arenas.clear();
        playerArenas.clear();
    }

    public void cancelAllSchedulers(String moduleId) {
        for (RaceArenaState state : arenas.values()) {
            state.getContext().getSchedulerAPI().cancelModuleTasks(moduleId);
        }
    }

    public Location getLastPosition(Player player) {
        Integer arenaId = getArenaId(player);
        if (arenaId == null) {
            return null;
        }
        RaceArenaState state = arenas.get(arenaId);
        if (state == null) {
            return null;
        }
        return state.getLastKnownPositions().get(player.getUuid());
    }

    public void updateLastPosition(Player player, Location location) {
        if (player == null || location == null) {
            return;
        }
        Integer arenaId = getArenaId(player);
        if (arenaId == null) {
            return;
        }
        RaceArenaState state = arenas.get(arenaId);
        if (state == null) {
            return;
        }
        state.getLastKnownPositions().put(player.getUuid(), location);
    }

    public Location getSpawnPosition(Player player) {
        Integer arenaId = getArenaId(player);
        if (arenaId == null) {
            return null;
        }
        RaceArenaState state = arenas.get(arenaId);
        if (state == null) {
            return null;
        }
        return state.getSpawnPositions().get(player.getUuid());
    }

    public void updateSpawnPosition(Player player, Location location) {
        if (player == null || location == null) {
            return;
        }
        Integer arenaId = getArenaId(player);
        if (arenaId == null) {
            return;
        }
        RaceArenaState state = arenas.get(arenaId);
        if (state == null) {
            return;
        }
        state.getSpawnPositions().put(player.getUuid(), location);
    }
}
