package de.justgino.plugins.wildhomes.teleport;

import de.justgino.plugins.wildhomes.WildHomes;
import de.justgino.plugins.wildhomes.config.Settings;
import de.justgino.plugins.wildhomes.home.Home;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportService {

    private static final class Warmup {
        private final Home home;
        private final Location start;
        private ScheduledTask task;
        private int remaining;

        private Warmup(Home home, Location start, int remaining) {
            this.home = home;
            this.start = start;
            this.remaining = remaining;
        }
    }

    private final WildHomes plugin;
    private final Map<UUID, Warmup> warmups = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public TeleportService(WildHomes plugin) {
        this.plugin = plugin;
    }

    private Settings settings() {
        return plugin.settings();
    }

    public void teleport(Player player, Home home) {
        if (plugin.network().isRemote(home) && !player.hasPermission("wildhomes.crossserver")) {
            plugin.lang().send(player, "teleport.no-cross-server", "server", home.server());
            return;
        }
        if (!plugin.network().isRemote(home) && home.location() == null) {
            plugin.lang().send(player, "teleport.world-missing");
            return;
        }
        long left = cooldownLeft(player);
        if (left > 0) {
            plugin.lang().send(player, "teleport.cooldown", "seconds", left);
            return;
        }
        cancel(player.getUniqueId());
        int seconds = player.hasPermission("wildhomes.bypass.warmup") ? 0 : settings().warmup;
        if (seconds <= 0) {
            finish(player, home);
            return;
        }
        Warmup warmup = new Warmup(home, player.getLocation(), seconds);
        warmups.put(player.getUniqueId(), warmup);
        warmup.task = plugin.tasks().entityTimer(player, task -> tick(player, warmup), 1L, 20L);
    }

    private void tick(Player player, Warmup warmup) {
        if (warmups.get(player.getUniqueId()) != warmup) {
            warmup.task.cancel();
            return;
        }
        if (warmup.remaining <= 0) {
            warmup.task.cancel();
            warmups.remove(player.getUniqueId());
            finish(player, warmup.home);
            return;
        }
        countdown(player, warmup.home, warmup.remaining);
        sound(player, settings().soundCountdown);
        warmup.remaining--;
    }

    private void countdown(Player player, Home home, int seconds) {
        switch (settings().countdownDisplay) {
            case ACTIONBAR -> player.sendActionBar(plugin.lang().get(player, "teleport.countdown", "home", home.name(), "seconds", seconds));
            case CHAT -> plugin.lang().send(player, "teleport.countdown", "home", home.name(), "seconds", seconds);
            case TITLE -> player.showTitle(Title.title(
                    plugin.lang().get(player, "teleport.countdown-title", "home", home.name(), "seconds", seconds),
                    plugin.lang().get(player, "teleport.countdown-subtitle", "home", home.name(), "seconds", seconds),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(200))));
            case NONE -> {
            }
        }
    }

    private void finish(Player player, Home home) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        if (plugin.network().isRemote(home)) {
            plugin.network().send(player, home);
            return;
        }
        Location target = home.location();
        if (target == null) {
            plugin.lang().send(player, "teleport.world-missing");
            return;
        }
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(ok -> {
            if (ok) plugin.tasks().entity(player, () -> arrived(player, home.name()));
        });
    }

    public void arrived(Player player, String home) {
        if (settings().countdownDisplay == Settings.Display.TITLE) player.clearTitle();
        plugin.lang().send(player, "teleport.success", "home", home);
        sound(player, settings().soundSuccess);
    }

    public void onMove(Player player, Location to) {
        if (!settings().cancelOnMove) return;
        Warmup warmup = warmups.get(player.getUniqueId());
        if (warmup == null) return;
        Location from = warmup.start;
        if (from.getWorld() != to.getWorld() || from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            interrupt(player, "teleport.cancelled-move");
        }
    }

    public void onDamage(Player player) {
        if (settings().cancelOnDamage && warmups.containsKey(player.getUniqueId())) interrupt(player, "teleport.cancelled-damage");
    }

    private void interrupt(Player player, String message) {
        if (!cancel(player.getUniqueId())) return;
        if (settings().countdownDisplay == Settings.Display.TITLE) player.clearTitle();
        plugin.lang().send(player, message);
        sound(player, settings().soundCancel);
    }

    public boolean cancel(UUID player) {
        Warmup warmup = warmups.remove(player);
        if (warmup == null) return false;
        if (warmup.task != null) warmup.task.cancel();
        return true;
    }

    public void quit(UUID player) {
        cancel(player);
    }

    public void cancelAll() {
        for (UUID player : warmups.keySet()) cancel(player);
    }

    private long cooldownLeft(Player player) {
        if (settings().cooldown <= 0 || player.hasPermission("wildhomes.bypass.cooldown")) return 0;
        Long last = cooldowns.get(player.getUniqueId());
        if (last == null) return 0;
        long left = settings().cooldown * 1000L - (System.currentTimeMillis() - last);
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    public void sound(Player player, String name) {
        if (name == null || name.isBlank()) return;
        try {
            player.playSound(Sound.sound(Key.key(name.trim().toLowerCase()), Sound.Source.MASTER, settings().soundVolume, 1f));
        } catch (Exception e) {
            plugin.getLogger().warning("Unknown sound in config.yml: " + name);
        }
    }
}
