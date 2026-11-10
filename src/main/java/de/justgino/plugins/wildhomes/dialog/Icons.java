package de.justgino.plugins.wildhomes.dialog;

import de.justgino.plugins.wildhomes.WildHomes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Material;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Icons {

    private static final Key ITEMS = Key.key("minecraft", "items");
    private static final Key BLOCKS = Key.key("minecraft", "blocks");

    private final WildHomes plugin;
    private final Map<String, Component> sprites = new HashMap<>();
    private final List<Material> everything;
    private List<Material> choices;

    public Icons(WildHomes plugin) {
        this.plugin = plugin;
        try (InputStream in = plugin.getResource("sprites.txt")) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split(" ");
                    if (parts.length != 3) continue;
                    Key atlas = parts[1].equals("blocks") ? BLOCKS : ITEMS;
                    sprites.put(parts[0], sprite(atlas, parts[2]));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the item sprites: " + e.getMessage());
        }
        List<Material> items = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.name().startsWith("LEGACY_") || material.isAir() || !material.isItem()) continue;
            items.add(material);
        }
        items.sort(Comparator.comparing(m -> m.getKey().getKey()));
        everything = List.copyOf(items);
        reload();
    }

    public void reload() {
        List<Material> configured = plugin.settings().iconList;
        choices = configured.isEmpty() ? everything : configured;
    }

    public List<Material> choices() {
        return choices;
    }

    public List<Material> search(String query) {
        String q = normalize(query);
        if (q.isEmpty()) return choices;
        List<Material> result = new ArrayList<>();
        for (Material material : choices) {
            if (normalize(material.getKey().getKey()).contains(q)) result.add(material);
        }
        return result;
    }

    public Component sprite(Material material) {
        String key = material.getKey().getKey();
        Component known = sprites.get(key);
        if (known != null) return known;
        return material.isBlock() ? sprite(BLOCKS, "block/" + key) : sprite(ITEMS, "item/" + key);
    }

    public Component name(Material material) {
        return Component.translatable(material.translationKey());
    }

    private static Component sprite(Key atlas, String path) {
        return Component.object(ObjectContents.sprite(atlas, Key.key("minecraft", path)));
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "").trim();
    }
}
