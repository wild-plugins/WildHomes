package de.justgino.plugins.wildhomes.listener;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import de.justgino.plugins.wildhomes.WildHomes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {

    private final WildHomes plugin;

    public PlayerListener(WildHomes plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        try {
            plugin.homes().loadBlocking(event.getUniqueId(), event.getName());
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load the homes of " + event.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.homes().get(player.getUniqueId()) == null) plugin.homes().loadAsync(player.getUniqueId(), player.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.teleports().quit(event.getPlayer().getUniqueId());
        plugin.homes().unload(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        if (Bukkit.getPlayer(event.getPlayerUniqueId()) == null) plugin.homes().unload(event.getPlayerUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock()) plugin.teleports().onMove(event.getPlayer(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) plugin.teleports().onDamage(player);
    }
}
