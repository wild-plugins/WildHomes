package de.justgino.plugins.wildhomes.config;

import de.justgino.plugins.wildhomes.WildHomes;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class Settings {

    public enum Display { ACTIONBAR, TITLE, CHAT, NONE }

    public enum ClickAction { MANAGE, TELEPORT }

    public final String language;
    public final Map<String, String> autoLanguages;
    public final String serverName;
    public final boolean crossServer;
    public final int connectTimeout;

    public final int defaultAmount;
    public final int maxSlots;
    public final Material defaultIcon;
    public final int nameMaxLength;
    public final Pattern namePattern;
    public final boolean overwriteExisting;
    public final boolean useOverLimit;
    public final Set<String> disabledWorlds;

    public final int warmup;
    public final int cooldown;
    public final boolean cancelOnMove;
    public final boolean cancelOnDamage;
    public final Display countdownDisplay;
    public final float soundVolume;
    public final String soundCountdown;
    public final String soundSuccess;
    public final String soundCancel;

    public final int mainColumns;
    public final int mainWidth;
    public final Material mainIcon;
    public final int previewSlots;
    public final boolean showLocked;
    public final ClickAction clickAction;
    public final int homeColumns;
    public final int homeWidth;
    public final int renameWidth;
    public final int renameInputWidth;
    public final int iconColumns;
    public final int iconWidth;
    public final List<Material> iconList;
    public final List<Pattern> iconExclude;
    public final int deleteWidth;
    public final boolean confirmDelete;

    public final List<String> homeAliases;
    public final List<String> sethomeAliases;
    public final List<String> delhomeAliases;
    public final List<String> adminAliases;

    private Settings(WildHomes plugin, YamlConfiguration c) {
        language = c.getString("language", "auto").trim().toLowerCase(Locale.ROOT);
        Map<String, String> auto = new HashMap<>();
        ConfigurationSection autoSection = c.getConfigurationSection("auto-languages");
        if (autoSection != null) {
            for (String key : autoSection.getKeys(false)) auto.put(key.toLowerCase(Locale.ROOT), String.valueOf(autoSection.get(key)).trim().toLowerCase(Locale.ROOT));
        }
        autoLanguages = Map.copyOf(auto);
        serverName = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "database.yml")).getString("server-name", "survival").trim();
        crossServer = c.getBoolean("cross-server.enabled");
        connectTimeout = Math.max(3, c.getInt("cross-server.connect-timeout", 15));

        defaultAmount = Math.max(0, c.getInt("homes.default-amount", 1));
        maxSlots = clamp(c.getInt("homes.max-slots", 28), 1, 200);
        defaultIcon = material(plugin, c.getString("homes.default-icon"), Material.WHITE_BED);
        nameMaxLength = clamp(c.getInt("homes.name-max-length", 16), 1, 64);
        namePattern = pattern(plugin, c.getString("homes.name-pattern", "[\\p{L}\\p{N}_ -]+"));
        overwriteExisting = c.getBoolean("homes.overwrite-existing", true);
        useOverLimit = c.getBoolean("homes.use-over-limit", true);
        Set<String> worlds = new HashSet<>();
        for (String world : c.getStringList("homes.disabled-worlds")) worlds.add(world.toLowerCase(Locale.ROOT));
        disabledWorlds = Set.copyOf(worlds);

        warmup = Math.max(0, c.getInt("teleport.warmup", 3));
        cooldown = Math.max(0, c.getInt("teleport.cooldown", 0));
        cancelOnMove = c.getBoolean("teleport.cancel-on-move", true);
        cancelOnDamage = c.getBoolean("teleport.cancel-on-damage", true);
        countdownDisplay = enumValue(Display.class, c.getString("teleport.countdown-display"), Display.ACTIONBAR);
        soundVolume = (float) Math.max(0, c.getDouble("teleport.sounds.volume", 1.0));
        soundCountdown = c.getString("teleport.sounds.countdown", "");
        soundSuccess = c.getString("teleport.sounds.success", "");
        soundCancel = c.getString("teleport.sounds.cancel", "");

        mainColumns = clamp(c.getInt("dialogs.main.columns", 4), 1, 8);
        mainWidth = width(c.getInt("dialogs.main.button-width", 110));
        mainIcon = material(plugin, c.getString("dialogs.main.icon"), Material.WHITE_BED);
        previewSlots = Math.max(1, c.getInt("dialogs.main.preview-slots", 7));
        showLocked = c.getBoolean("dialogs.main.show-locked", true);
        clickAction = enumValue(ClickAction.class, c.getString("dialogs.main.click-action"), ClickAction.MANAGE);
        homeColumns = clamp(c.getInt("dialogs.home.columns", 2), 1, 8);
        homeWidth = width(c.getInt("dialogs.home.button-width", 150));
        renameWidth = width(c.getInt("dialogs.rename.button-width", 200));
        renameInputWidth = width(c.getInt("dialogs.rename.input-width", 200));
        iconColumns = clamp(c.getInt("dialogs.icons.columns", 4), 1, 8);
        iconWidth = width(c.getInt("dialogs.icons.button-width", 115));
        List<Material> icons = new ArrayList<>();
        for (String key : c.getStringList("dialogs.icons.list")) {
            Material m = material(plugin, key, null);
            if (m != null && m.isItem()) icons.add(m);
        }
        iconList = List.copyOf(icons);
        List<Pattern> exclude = new ArrayList<>();
        for (String glob : c.getStringList("dialogs.icons.exclude")) {
            String clean = glob.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (clean.isEmpty()) continue;
            String[] parts = clean.split("\\*", -1);
            StringBuilder regex = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) regex.append(".*");
                if (!parts[i].isEmpty()) regex.append(Pattern.quote(parts[i]));
            }
            exclude.add(Pattern.compile(regex.toString()));
        }
        iconExclude = List.copyOf(exclude);
        deleteWidth = width(c.getInt("dialogs.delete.button-width", 150));
        confirmDelete = c.getBoolean("dialogs.delete.confirm", true);

        homeAliases = c.getStringList("commands.home-aliases");
        sethomeAliases = c.getStringList("commands.sethome-aliases");
        delhomeAliases = c.getStringList("commands.delhome-aliases");
        adminAliases = c.getStringList("commands.admin-aliases");
    }

    public static Settings load(WildHomes plugin) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in != null) config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
        }
        return new Settings(plugin, config);
    }

    public boolean worldDisabled(String world) {
        return disabledWorlds.contains(world.toLowerCase(Locale.ROOT));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int width(int value) {
        return clamp(value, 1, 1024);
    }

    private static Material material(WildHomes plugin, String key, Material fallback) {
        if (key == null || key.isBlank()) return fallback;
        Material m = Material.matchMaterial(key.trim());
        if (m == null || m.isAir() || !m.isItem()) {
            plugin.getLogger().warning("Unknown item in config.yml: " + key);
            return fallback;
        }
        return m;
    }

    private static Pattern pattern(WildHomes plugin, String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            plugin.getLogger().warning("homes.name-pattern is not a valid regular expression, every name is allowed.");
            return Pattern.compile(".+");
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
