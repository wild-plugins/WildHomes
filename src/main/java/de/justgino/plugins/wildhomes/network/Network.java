package de.justgino.plugins.wildhomes.network;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import de.justgino.plugins.wildhomes.WildHomes;
import de.justgino.plugins.wildhomes.config.Settings;
import de.justgino.plugins.wildhomes.home.Home;
import de.justgino.plugins.wildhomes.storage.Database;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Network implements Listener, PluginMessageListener {

    private static final String CHANNEL = "BungeeCord";

    private record Arrival(Database.Pending pending, boolean placed) {
    }

    private final WildHomes plugin;
    private final Map<UUID, Arrival> arrivals = new ConcurrentHashMap<>();
    private final AtomicBoolean checked = new AtomicBoolean();
    private boolean enabled;
    private Settings.Method method = Settings.Method.PROXY;

    public Network(WildHomes plugin) {
        this.plugin = plugin;
    }

    public void start() {
        enabled = plugin.settings().crossServer;
        method = plugin.settings().crossServerMethod;
        if (enabled && !plugin.database().isMariaDb()) {
            plugin.getLogger().warning("cross-server needs type: mariadb in database.yml - cross-server stays off.");
            enabled = false;
        }
        if (!enabled) return;
        if (method == Settings.Method.TRANSFER) {
            String address = plugin.settings().serverAddress;
            if (address.isEmpty()) {
                plugin.getLogger().warning("cross-server.method is TRANSFER but cross-server.server-address is empty - players cannot be sent to this server.");
            } else {
                plugin.tasks().async(() -> {
                    try {
                        plugin.database().saveServer(plugin.settings().serverName, address);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Could not save the server address: " + e.getMessage());
                    }
                });
            }
            if (!Bukkit.getServer().isAcceptingTransfers()) {
                plugin.getLogger().warning("accepts-transfers is false in server.properties - players sent here by TRANSFER will be kicked.");
            }
        } else {
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        }
        plugin.getLogger().info("Cross-server is on (" + method + "), this server is '" + plugin.settings().serverName + "'.");
    }

    public void stop() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean isRemote(Home home) {
        return enabled && !home.server().equalsIgnoreCase(plugin.settings().serverName);
    }

    public void send(Player player, Home home) {
        UUID uuid = player.getUniqueId();
        long created = System.currentTimeMillis();
        Database.Pending pending = new Database.Pending(uuid, home.server(), home.name(), home.world(),
                home.x(), home.y(), home.z(), home.yaw(), home.pitch(), created);
        boolean transfer = method == Settings.Method.TRANSFER;
        plugin.tasks().supply(() -> {
            String address = transfer ? plugin.database().serverAddress(home.server()) : "";
            if (address == null) return null;
            plugin.database().savePending(pending);
            return address;
        }).whenComplete((address, error) -> plugin.tasks().entity(player, () -> {
            if (error != null) {
                plugin.lang().send(player, "messages.error");
                return;
            }
            if (address == null) {
                plugin.lang().send(player, "teleport.switch-failed", "server", home.server());
                return;
            }
            plugin.lang().send(player, "teleport.switching", "server", home.server());
            if (transfer) {
                String host = address;
                int port = 25565;
                int colon = address.lastIndexOf(':');
                if (colon > 0 && address.indexOf(']') < colon) {
                    host = address.substring(0, colon);
                    try {
                        port = Integer.parseInt(address.substring(colon + 1).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                player.transfer(host.replace("[", "").replace("]", ""), port);
            } else {
                player.sendPluginMessage(plugin, CHANNEL, message("Connect", home.server()));
            }
            plugin.tasks().entityLater(player, () -> plugin.tasks().supply(() -> plugin.database().deletePending(uuid, created))
                    .thenAccept(stillThere -> {
                        if (stillThere) plugin.tasks().entity(player, () -> plugin.lang().send(player, "teleport.switch-failed", "server", home.server()));
                    }), 20L * plugin.settings().connectTimeout);
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        arrivals.remove(event.getUniqueId());
        if (!enabled || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        try {
            Database.Pending pending = plugin.tasks().supply(() -> plugin.database().loadPending(event.getUniqueId())).join();
            if (pending == null || !pending.server().equalsIgnoreCase(plugin.settings().serverName)) return;
            long window = Math.max(60_000L, plugin.settings().connectTimeout * 1000L + 30_000L);
            if (Math.abs(System.currentTimeMillis() - pending.created()) > window) return;
            arrivals.put(event.getUniqueId(), new Arrival(pending, false));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the pending teleport of " + event.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        UUID uuid = event.getConnection().getProfile().getId();
        if (uuid == null) return;
        Arrival arrival = arrivals.get(uuid);
        if (arrival == null) return;
        Database.Pending p = arrival.pending();
        World world = Bukkit.getWorld(p.world());
        if (world == null) return;
        event.setSpawnLocation(new Location(world, p.x(), p.y(), p.z(), p.yaw(), p.pitch()));
        arrivals.put(uuid, new Arrival(p, true));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (enabled && method == Settings.Method.PROXY && checked.compareAndSet(false, true)) {
            plugin.tasks().entityLater(player, () -> player.sendPluginMessage(plugin, CHANNEL, message("GetServer", null)), 20L);
        }
        Arrival arrival = arrivals.remove(player.getUniqueId());
        if (arrival == null) return;
        Database.Pending p = arrival.pending();
        plugin.tasks().supply(() -> plugin.database().deletePending(p.player(), p.created())).thenAccept(taken -> {
            if (!taken) return;
            plugin.tasks().entity(player, () -> {
                if (arrival.placed()) plugin.teleports().arrived(player, p.home());
                else plugin.lang().send(player, "teleport.world-missing");
            });
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        arrivals.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        arrivals.remove(event.getPlayerUniqueId());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] data) {
        if (!CHANNEL.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (!"GetServer".equals(in.readUTF())) return;
            String actual = in.readUTF();
            if (!actual.equalsIgnoreCase(plugin.settings().serverName)) {
                plugin.getLogger().severe("config.yml -> server-name is '" + plugin.settings().serverName
                        + "' but the proxy calls this server '" + actual + "'. Teleports to this server will fail until both match.");
            }
        } catch (IOException ignored) {
        }
    }

    private static byte[] message(String sub, String argument) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(sub);
            if (argument != null) out.writeUTF(argument);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }
}
