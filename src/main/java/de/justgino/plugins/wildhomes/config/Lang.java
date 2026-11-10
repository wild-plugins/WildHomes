package de.justgino.plugins.wildhomes.config;

import de.justgino.plugins.wildhomes.WildHomes;
import de.justgino.plugins.wildhomes.hook.PlaceholderBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class Lang {

    public static final String AUTO = "auto";
    private static final String FALLBACK = "english";
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final WildHomes plugin;
    private Map<String, YamlConfiguration> files = Map.of();
    private String setting = AUTO;
    private Map<String, String> auto = Map.of();

    public Lang(WildHomes plugin) {
        this.plugin = plugin;
    }

    public void load(Settings settings) {
        Map<String, YamlConfiguration> loaded = new TreeMap<>();
        File[] list = new File(plugin.getDataFolder(), "lang").listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (list != null) {
            for (File file : list) {
                String name = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                config.setDefaults(bundled(name));
                loaded.put(name, config);
            }
        }
        if (!loaded.containsKey(FALLBACK)) {
            YamlConfiguration english = new YamlConfiguration();
            english.setDefaults(bundled(FALLBACK));
            loaded.put(FALLBACK, english);
        }
        files = loaded;
        auto = settings.autoLanguages;
        setting = settings.language;
        if (!setting.equals(AUTO) && !files.containsKey(setting)) {
            plugin.getLogger().warning("lang/" + setting + ".yml does not exist, using auto.");
            setting = AUTO;
        }
        for (Map.Entry<String, String> entry : auto.entrySet()) {
            if (!files.containsKey(entry.getValue())) plugin.getLogger().warning("auto-languages: lang/" + entry.getValue() + ".yml does not exist.");
        }
    }

    private YamlConfiguration bundled(String name) {
        InputStream in = plugin.getResource("lang/" + name + ".yml");
        if (in == null) in = plugin.getResource("lang/" + FALLBACK + ".yml");
        if (in == null) return new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            return new YamlConfiguration();
        }
    }

    public List<String> languages() {
        List<String> names = new ArrayList<>();
        names.add(AUTO);
        names.addAll(files.keySet());
        return names;
    }

    public boolean exists(String language) {
        return language.equals(AUTO) || files.containsKey(language);
    }

    public String languageOf(@Nullable CommandSender sender) {
        if (!setting.equals(AUTO)) return setting;
        if (sender instanceof Player player) {
            Locale locale = player.locale();
            String mapped = auto.get(locale.toString().toLowerCase(Locale.ROOT));
            if (mapped == null) mapped = auto.get(locale.getLanguage().toLowerCase(Locale.ROOT));
            if (mapped != null && files.containsKey(mapped)) return mapped;
        }
        return FALLBACK;
    }

    private YamlConfiguration file(@Nullable CommandSender sender) {
        YamlConfiguration file = files.get(languageOf(sender));
        return file != null ? file : files.get(FALLBACK);
    }

    public String raw(@Nullable CommandSender sender, String path) {
        return file(sender).getString(path, "");
    }

    public List<String> rawLines(@Nullable CommandSender sender, String path) {
        YamlConfiguration file = file(sender);
        if (file.isList(path)) return file.getStringList(path);
        String single = file.getString(path, "");
        return single.isEmpty() ? List.of() : List.of(single);
    }

    public Component get(@Nullable CommandSender sender, String path, Object... placeholders) {
        return parse(raw(sender, path), placeholders);
    }

    public Component lines(@Nullable CommandSender sender, String path, Object... placeholders) {
        return join(rawLines(sender, path), placeholders);
    }

    public Component join(List<String> lines, Object... placeholders) {
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out = out.append(Component.newline());
            out = out.append(parse(lines.get(i), placeholders));
        }
        return out;
    }

    public void send(CommandSender to, String path, Object... placeholders) {
        String text = raw(to, path);
        if (text.isEmpty()) return;
        text = raw(to, "prefix") + text;
        if (to instanceof Player player && plugin.placeholderApi()) text = PlaceholderBridge.apply(player, text);
        to.sendMessage(parse(text, placeholders));
    }

    public void sendLines(CommandSender to, String path, Object... placeholders) {
        for (String line : rawLines(to, path)) to.sendMessage(parse(line, placeholders));
    }

    public Component parse(String raw, Object... placeholders) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        String text = raw;
        TagResolver.Builder tags = TagResolver.builder();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String key = String.valueOf(placeholders[i]);
            Object value = placeholders[i + 1];
            String token = "{" + key + "}";
            if (!text.contains(token)) continue;
            if (value instanceof Component component) {
                String tag = "wh_" + key.replaceAll("[^a-z0-9_]", "");
                tags.resolver(Placeholder.component(tag, component));
                text = text.replace(token, "<" + tag + ">");
            } else {
                text = text.replace(token, MINI.escapeTags(String.valueOf(value)));
            }
        }
        return MINI.deserialize(text, tags.build());
    }
}
