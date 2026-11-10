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

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Lang {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final WildHomes plugin;
    private YamlConfiguration file = new YamlConfiguration();

    public Lang(WildHomes plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        File dir = new File(plugin.getDataFolder(), "lang");
        File target = new File(dir, language + ".yml");
        if (!target.exists()) {
            plugin.getLogger().warning("lang/" + language + ".yml does not exist, using english.");
            target = new File(dir, "english.yml");
            language = "english";
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(target);
        InputStream in = plugin.getResource("lang/" + language + ".yml");
        if (in == null) in = plugin.getResource("lang/english.yml");
        if (in != null) {
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(reader));
            } catch (Exception ignored) {
            }
        }
        file = loaded;
    }

    public String raw(String path) {
        return file.getString(path, "");
    }

    public List<String> rawLines(String path) {
        if (file.isList(path)) return file.getStringList(path);
        String single = file.getString(path, "");
        return single.isEmpty() ? List.of() : List.of(single);
    }

    public Component get(String path, Object... placeholders) {
        return parse(raw(path), placeholders);
    }

    public Component lines(String path, Object... placeholders) {
        return join(rawLines(path), placeholders);
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
        String text = raw(path);
        if (text.isEmpty()) return;
        text = raw("prefix") + text;
        if (to instanceof Player player && plugin.placeholderApi()) text = PlaceholderBridge.apply(player, text);
        to.sendMessage(parse(text, placeholders));
    }

    public void sendLines(CommandSender to, String path, Object... placeholders) {
        for (String line : rawLines(path)) to.sendMessage(parse(line, placeholders));
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
