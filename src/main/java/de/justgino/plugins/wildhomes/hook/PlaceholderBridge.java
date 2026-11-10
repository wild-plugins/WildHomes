package de.justgino.plugins.wildhomes.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

public final class PlaceholderBridge {

    private PlaceholderBridge() {
    }

    public static String apply(Player player, String text) {
        return text.indexOf('%') < 0 ? text : PlaceholderAPI.setPlaceholders(player, text);
    }
}
