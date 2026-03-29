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

public class RaceArenaState {

    private final GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context;
    private final Map<UUID, Location> lastKnownPositions;
    private final Map<UUID, Location> spawnPositions;
    private boolean ended;
    private UUID winner;

    public RaceArenaState(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        this.context = context;
        this.lastKnownPositions = new ConcurrentHashMap<>();
        this.spawnPositions = new ConcurrentHashMap<>();
    }

    public GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> getContext() {
        return context;
    }

    public Map<UUID, Location> getLastKnownPositions() {
        return lastKnownPositions;
    }

    public Map<UUID, Location> getSpawnPositions() {
        return spawnPositions;
    }

    public boolean isEnded() {
        return ended;
    }

    public void markEnded() {
        this.ended = true;
    }

    public UUID getWinner() {
        return winner;
    }

    public void setWinner(UUID winner) {
        this.winner = winner;
    }
}
