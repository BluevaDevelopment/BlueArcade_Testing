package net.blueva.arcade.modules.race.support;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.module.ModuleInfo;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Holder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class RaceMessagingService {

    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final RaceProgressService progressService;

    public RaceMessagingService(ModuleInfo moduleInfo,
                                ModuleConfigAPI moduleConfig,
                                CoreConfigAPI coreConfig,
                                RaceProgressService progressService) {
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.progressService = progressService;
    }

    public void sendDescription(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        List<String> description = moduleConfig.getStringListFrom("language.yml", "description");

        for (Player player : context.getPlayers()) {
            for (String line : description) {
                context.getMessagesAPI().sendRaw(player, line);
            }
        }
    }

    public void sendCountdownTick(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                  int secondsLeft) {
        for (Player player : context.getPlayers()) {
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.countdown"));

            String title = coreConfig.getLanguage("titles.starting_game.title")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            String subtitle = coreConfig.getLanguage("titles.starting_game.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 5);
        }
    }

    public void sendCountdownFinished(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        for (Player player : context.getPlayers()) {
            String title = coreConfig.getLanguage("titles.game_started.title")
                    .replace("{game_display_name}", moduleInfo.getName());

            String subtitle = coreConfig.getLanguage("titles.game_started.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName());

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 20);

            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.start"));
        }
    }

    public void broadcastDeath(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                               Player player,
                               boolean deathBlock) {
        // Don't broadcast death messages for spectators
        if (context.getSpectators().contains(player)) {
            return;
        }

        String path = deathBlock ? "messages.deaths.death_block" : "messages.deaths.void";
        String message = getRandomMessage(path);
        if (message == null) {
            return;
        }

        message = message.replace("{player}", player.getDisplayName());
        for (Player target : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(target, message);
        }
    }

    public void broadcastFinish(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                Player player,
                                int position) {
        String message = getRandomMessage("messages.finish.crossed");
        if (message == null) {
            return;
        }

        message = message
                .replace("{player}", player.getDisplayName())
                .replace("{position}", String.valueOf(position));

        for (Player target : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(target, message);
        }
    }

    public void sendFinishTitles(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                 Player player,
                                 int position) {
        String title = moduleConfig.getStringFrom("language.yml", "titles.finished.title");
        String subtitle = moduleConfig.getStringFrom("language.yml", "titles.finished.subtitle")
                .replace("{position}", String.valueOf(position));

        context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 80, 20);
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.classified"));
    }

    public void playRespawnSound(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                 Player player) {
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.respawn"));
    }

    public String getDeathBlock(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        try {
            String deathBlockName = context.getDataAccess().getGameData("basic.death_block", String.class);
            if (deathBlockName != null) {
                return deathBlockName;
            }
        } catch (Exception ignored) {
            // Fallback to default
        }
        return "hytale:barrier";
    }

    public boolean isInsideFinishLine(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                      Location location) {
        try {
            Location finishMin = context.getDataAccess().getGameLocation("game.finish_line.bounds.min");
            Location finishMax = context.getDataAccess().getGameLocation("game.finish_line.bounds.max");

            if (finishMin == null || finishMax == null) {
                return false;
            }

            Vector3d minPos = finishMin.getPosition();
            Vector3d maxPos = finishMax.getPosition();
            Vector3d current = location.getPosition();

            return current.x >= Math.min(minPos.x, maxPos.x) &&
                    current.x <= Math.max(minPos.x, maxPos.x) &&
                    current.y >= Math.min(minPos.y, maxPos.y) &&
                    current.y <= Math.max(minPos.y, maxPos.y) &&
                    current.z >= Math.min(minPos.z, maxPos.z) &&
                    current.z <= Math.max(minPos.z, maxPos.z);

        } catch (Exception e) {
            return false;
        }
    }

    public void sendSpectatorDescriptions(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        List<Player> spectators = context.getSpectators();
        for (int i = 0; i < spectators.size(); i++) {
            Player spectator = spectators.get(i);
            Map<String, String> placeholders = Map.of("race_position", String.valueOf(i + 1));
            context.getScoreboardAPI().update(spectator, placeholders);
        }
    }

    private String getRandomMessage(String path) {
        List<String> messages = moduleConfig.getStringListFrom("language.yml", path);
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        int index = ThreadLocalRandom.current().nextInt(messages.size());
        return messages.get(index);
    }
}
