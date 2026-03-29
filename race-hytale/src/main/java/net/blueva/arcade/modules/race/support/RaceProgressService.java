package net.blueva.arcade.modules.race.support;

import net.blueva.arcade.api.game.GameContext;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Holder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RaceProgressService {

    public int calculateLivePosition(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                     Player player) {
        List<Player> alivePlayers = context.getAlivePlayers();
        List<Player> spectators = context.getSpectators();

        if (spectators.contains(player)) {
            return spectators.indexOf(player) + 1;
        }

        Map<Player, Double> distances = new HashMap<>();
        for (Player p : alivePlayers) {
            double distance = getDistanceToFinish(context, p);
            distances.put(p, distance);
        }

        List<Map.Entry<Player, Double>> sorted = new ArrayList<>(distances.entrySet());
        sorted.sort(Map.Entry.comparingByValue());

        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getKey().equals(player)) {
                return spectators.size() + i + 1;
            }
        }

        return spectators.size() + alivePlayers.size();
    }

    public List<Player> getTopPlayersByDistance(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        List<Player> alivePlayers = context.getAlivePlayers();
        List<Player> spectators = context.getSpectators();

        List<Player> topPlayers = new ArrayList<>(spectators);

        Map<Player, Double> distances = new HashMap<>();
        for (Player p : alivePlayers) {
            double distance = getDistanceToFinish(context, p);
            distances.put(p, distance);
        }

        List<Map.Entry<Player, Double>> sorted = new ArrayList<>(distances.entrySet());
        sorted.sort(Map.Entry.comparingByValue());

        for (Map.Entry<Player, Double> entry : sorted) {
            topPlayers.add(entry.getKey());
        }

        return topPlayers;
    }

    public String formatDistance(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                 Player player) {
        if (context.getSpectators().contains(player)) {
            return "0";
        }

        double distance = getDistanceToFinish(context, player);
        if (distance == Double.MAX_VALUE) {
            return "?";
        }

        return String.format("%.0f", distance);
    }

    private double getDistanceToFinish(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                       Player player) {
        try {
            Location finishMin = context.getDataAccess().getGameLocation("game.finish_line.bounds.min");
            Location finishMax = context.getDataAccess().getGameLocation("game.finish_line.bounds.max");

            if (finishMin == null || finishMax == null) {
                return Double.MAX_VALUE;
            }

            Vector3d minPos = finishMin.getPosition();
            Vector3d maxPos = finishMax.getPosition();

            double centerX = (minPos.x + maxPos.x) / 2;
            double centerY = (minPos.y + maxPos.y) / 2;
            double centerZ = (minPos.z + maxPos.z) / 2;

            World world = player.getWorld();
            if (world == null || !world.isInThread() || player.getTransformComponent() == null) {
                return Double.MAX_VALUE;
            }
            Vector3d playerPos = player.getTransformComponent().getPosition();

            double dx = playerPos.x - centerX;
            double dy = playerPos.y - centerY;
            double dz = playerPos.z - centerZ;

            return Math.sqrt(dx * dx + dy * dy + dz * dz);

        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
    }
}
