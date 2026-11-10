package de.justgino.plugins.wildhomes.hook;

import de.justgino.plugins.wildhomes.WildHomes;
import de.justgino.plugins.wildhomes.home.Home;
import de.justgino.plugins.wildhomes.home.PlayerHomes;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;

public final class PlaceholderHook extends PlaceholderExpansion {

    private final WildHomes plugin;

    public PlaceholderHook(WildHomes plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "wildhomes";
    }

    @Override
    public @NotNull String getAuthor() {
        return "JustGino";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (offline == null) return "";
        PlayerHomes homes = plugin.homes().get(offline.getUniqueId());
        if (homes == null) return "";
        Player player = offline.getPlayer();
        int limit = player != null ? plugin.homes().limit(player) : plugin.settings().defaultAmount;
        switch (params) {
            case "amount" -> {
                return String.valueOf(homes.size());
            }
            case "limit" -> {
                return String.valueOf(limit);
            }
            case "free" -> {
                return String.valueOf(Math.max(0, limit - homes.countWithin(limit)));
            }
            case "names" -> {
                return homes.all().stream().map(Home::name).collect(Collectors.joining(", "));
            }
            default -> {
            }
        }
        if (!params.startsWith("home_")) return null;
        String[] parts = params.substring(5).split("_", 2);
        int slot;
        try {
            slot = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return null;
        }
        Home home = homes.bySlot(slot);
        if (home == null) return "";
        if (parts.length == 1) return home.name();
        return switch (parts[1]) {
            case "world" -> home.world();
            case "server" -> home.server();
            case "x" -> String.valueOf((long) Math.floor(home.x()));
            case "y" -> String.valueOf((long) Math.floor(home.y()));
            case "z" -> String.valueOf((long) Math.floor(home.z()));
            default -> null;
        };
    }
}
