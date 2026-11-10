package de.justgino.plugins.wildhomes.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class Versions {

    private Versions() {
    }

    public static int[] parse(String version) {
        String clean = version == null ? "" : version.trim();
        int dash = clean.indexOf('-');
        if (dash > 0) clean = clean.substring(0, dash);
        String[] parts = clean.split("\\.");
        int[] out = new int[2];
        for (int i = 0; i < 2 && i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static boolean atMost(int[] version, int[] limit) {
        if (version[0] != limit[0]) return version[0] < limit[0];
        return version[1] <= limit[1];
    }

    public static int[] server() {
        return parse(Bukkit.getMinecraftVersion());
    }

    public static int[] client(Player player) {
        int[] server = server();
        Plugin via = Bukkit.getPluginManager().getPlugin("ViaVersion");
        if (via == null || !via.isEnabled()) return server;
        try {
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
            Object api = viaClass.getMethod("getAPI").invoke(null);
            Object protocol = api.getClass().getMethod("getPlayerProtocolVersion", java.util.UUID.class).invoke(api, player.getUniqueId());
            String name = String.valueOf(protocol.getClass().getMethod("getName").invoke(protocol));
            int[] client = parse(name);
            if (client[0] == 0) return server;
            return atMost(client, server) ? client : server;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return server;
        }
    }
}
