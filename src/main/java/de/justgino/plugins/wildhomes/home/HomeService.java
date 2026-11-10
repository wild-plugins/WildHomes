package de.justgino.plugins.wildhomes.home;

import de.justgino.plugins.wildhomes.WildHomes;
import de.justgino.plugins.wildhomes.config.Settings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class HomeService {

    private static final String AMOUNT = "wildhomes.amount.";

    private final WildHomes plugin;
    private final Map<UUID, PlayerHomes> online = new ConcurrentHashMap<>();

    public HomeService(WildHomes plugin) {
        this.plugin = plugin;
    }

    private Settings settings() {
        return plugin.settings();
    }

    public @Nullable PlayerHomes get(UUID owner) {
        return online.get(owner);
    }

    public void loadBlocking(UUID owner, String name) {
        loadAsync(owner, name).join();
    }

    public CompletableFuture<PlayerHomes> loadAsync(UUID owner, String name) {
        return plugin.tasks().supply(() -> {
            try {
                plugin.database().savePlayer(owner, name);
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not save the name of " + name + ": " + e.getMessage());
            }
            PlayerHomes homes = new PlayerHomes(owner, name, plugin.database().loadHomes(owner));
            online.put(owner, homes);
            return homes;
        });
    }

    public PlayerHomes current(PlayerHomes homes) {
        PlayerHomes live = online.get(homes.owner());
        return live != null ? live : homes;
    }

    public CompletableFuture<PlayerHomes> loadDetached(UUID owner, String name) {
        PlayerHomes cached = online.get(owner);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return plugin.tasks().supply(() -> new PlayerHomes(owner, name, plugin.database().loadHomes(owner)));
    }

    public void unload(UUID owner) {
        online.remove(owner);
    }

    public int limit(Player player) {
        int max = settings().maxSlots;
        if (player.hasPermission(AMOUNT + "unlimited")) return max;
        int best = -1;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission();
            if (!perm.startsWith(AMOUNT)) continue;
            try {
                best = Math.max(best, Integer.parseInt(perm.substring(AMOUNT.length())));
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.min(max, best < 0 ? settings().defaultAmount : best);
    }

    public int limitFor(Player viewer, PlayerHomes homes) {
        return viewer.getUniqueId().equals(homes.owner()) ? limit(viewer) : settings().maxSlots;
    }

    public boolean usable(Player viewer, PlayerHomes homes, Home home) {
        return settings().useOverLimit || home.slot() <= limitFor(viewer, homes);
    }

    public @Nullable String nameError(PlayerHomes homes, String name, int ignoreSlot) {
        if (name.isEmpty() || !settings().namePattern.matcher(name).matches()) return "messages.name-invalid";
        if (name.length() > settings().nameMaxLength) return "messages.name-too-long";
        Home existing = homes.byName(name);
        if (existing != null && existing.slot() != ignoreSlot) return "messages.home-exists";
        return null;
    }

    public String defaultName(PlayerHomes homes, int slot) {
        String base = plugin.lang().raw("default-name").trim();
        if (base.isEmpty()) base = "Home {slot}";
        if (!base.contains("{slot}")) base = base + " {slot}";
        for (int n = slot; n < slot + 1000; n++) {
            String number = String.valueOf(n);
            String name = base.replace("{slot}", number);
            if (name.length() > settings().nameMaxLength) {
                String head = base.replace("{slot}", "").trim();
                int room = Math.max(0, settings().nameMaxLength - number.length() - 1);
                name = (head.length() > room ? head.substring(0, room) : head).trim() + " " + number;
                name = name.trim();
                if (name.length() > settings().nameMaxLength) name = number;
            }
            if (homes.byName(name) == null) return name;
        }
        return String.valueOf(slot);
    }

    public @Nullable Home setHome(Player actor, PlayerHomes homes, @Nullable String requested, int slot) {
        Location location = actor.getLocation();
        if (settings().worldDisabled(location.getWorld().getName()) && !actor.hasPermission("wildhomes.bypass.world")) {
            plugin.lang().send(actor, "messages.world-disabled");
            return null;
        }
        String name = requested == null ? null : requested.trim();
        if (name != null && !name.isEmpty()) {
            Home existing = homes.byName(name);
            if (existing != null) {
                if (!settings().overwriteExisting) {
                    plugin.lang().send(actor, "messages.home-exists", "home", existing.name());
                    return null;
                }
                Home moved = existing.movedTo(settings().serverName, location);
                store(homes, moved);
                plugin.lang().send(actor, "messages.home-moved", "home", moved.name());
                return moved;
            }
        }
        int limit = limitFor(actor, homes);
        if (slot <= 0) slot = homes.firstFree(limit);
        if (slot <= 0 || slot > limit || homes.bySlot(slot) != null) {
            plugin.lang().send(actor, "messages.limit-reached", "max", limit);
            return null;
        }
        if (name == null || name.isEmpty()) name = defaultName(homes, slot);
        String error = nameError(homes, name, -1);
        if (error != null) {
            plugin.lang().send(actor, error, "home", name, "max", settings().nameMaxLength);
            return null;
        }
        Home home = Home.at(homes.owner(), slot, name, settings().serverName, location);
        store(homes, home);
        plugin.lang().send(actor, "messages.home-set", "home", home.name());
        return home;
    }

    public void delete(Player actor, PlayerHomes homes, Home home) {
        homes.remove(home.slot());
        plugin.tasks().async(() -> {
            try {
                plugin.database().deleteHome(home.owner(), home.slot());
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        plugin.lang().send(actor, "messages.home-deleted", "home", home.name());
    }

    public Home rename(PlayerHomes homes, Home home, String name) {
        Home renamed = home.withName(name);
        store(homes, renamed);
        return renamed;
    }

    public Home setIcon(PlayerHomes homes, Home home, @Nullable Material icon) {
        Home changed = home.withIcon(icon == null ? null : icon.getKey().toString());
        store(homes, changed);
        return changed;
    }

    public Material icon(Home home) {
        if (home.icon() != null) {
            Material m = Material.matchMaterial(home.icon());
            if (m != null && m.isItem() && !m.isAir()) return m;
        }
        return settings().defaultIcon;
    }

    private void store(PlayerHomes homes, Home home) {
        homes.put(home);
        plugin.tasks().async(() -> {
            try {
                plugin.database().saveHome(home);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
