package de.justgino.plugins.wildhomes.dialog;

import de.justgino.plugins.wildhomes.WildHomes;
import de.justgino.plugins.wildhomes.util.Versions;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class Icons {

    private static final Key ITEMS = Key.key("minecraft", "items");
    private static final Key BLOCKS = Key.key("minecraft", "blocks");

    private record Sprite(Component component, int[] since) {
    }

    private final WildHomes plugin;
    private final Map<String, Sprite> sprites = new HashMap<>();
    private final Map<String, String[]> names = new HashMap<>();
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
                    if (parts.length != 4) continue;
                    Key atlas = parts[2].equals("blocks") ? BLOCKS : ITEMS;
                    sprites.put(parts[0], new Sprite(sprite(atlas, parts[3]), Versions.parse(parts[1])));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the item sprites: " + e.getMessage());
        }
        try (InputStream in = plugin.getResource("item-names.txt")) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\t");
                    if (parts.length < 2) continue;
                    names.put(parts[0], Arrays.copyOfRange(parts, 1, parts.length));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the item names: " + e.getMessage());
        }
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        List<Material> items = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.name().startsWith("LEGACY_") || material.isAir() || !material.isItem()) continue;
            if (!sprites.containsKey(material.getKey().getKey())) continue;
            if (world != null && !enabled(world, material)) continue;
            items.add(material);
        }
        items.sort(Comparator.comparing(m -> m.getKey().getKey()));
        everything = List.copyOf(items);
        reload();
    }

    @SuppressWarnings("UnstableApiUsage")
    private static boolean enabled(World world, Material material) {
        try {
            return world.isEnabled(material.asItemType());
        } catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    public void reload() {
        List<Material> configured = plugin.settings().iconList;
        List<Material> allowed = new ArrayList<>(configured.isEmpty() ? everything : configured);
        allowed.retainAll(everything);
        allowed.removeIf(this::excluded);
        choices = List.copyOf(allowed);
    }

    private boolean excluded(Material material) {
        String key = material.getKey().getKey();
        for (Pattern pattern : plugin.settings().iconExclude) {
            if (pattern.matcher(key).matches()) return true;
        }
        return false;
    }

    public boolean supported(Material material, int[] client) {
        Sprite sprite = sprites.get(material.getKey().getKey());
        return sprite != null && Versions.atMost(sprite.since(), client);
    }

    public List<Material> choices(Player viewer) {
        int[] client = Versions.client(viewer);
        List<Material> result = new ArrayList<>();
        for (Material material : choices) if (supported(material, client)) result.add(material);
        return result;
    }

    public List<Material> search(Player viewer, String query) {
        String q = normalize(query);
        List<Material> result = new ArrayList<>();
        for (Material material : choices(viewer)) {
            if (q.isEmpty() || matches(material, q)) result.add(material);
        }
        return result;
    }

    private boolean matches(Material material, String query) {
        String key = material.getKey().getKey();
        if (normalize(key).contains(query)) return true;
        String[] names = this.names.get(key);
        if (names == null) return false;
        for (String name : names) {
            if (normalize(name).contains(query)) return true;
        }
        return false;
    }

    public Component sprite(Material material, Player viewer) {
        int[] client = Versions.client(viewer);
        Material shown = supported(material, client) ? material : plugin.settings().defaultIcon;
        Sprite sprite = sprites.get(shown.getKey().getKey());
        return sprite != null && Versions.atMost(sprite.since(), client) ? sprite.component() : Component.empty();
    }

    public Component name(Material material) {
        return Component.translatable(material.translationKey());
    }

    private static Component sprite(Key atlas, String path) {
        return Component.object(ObjectContents.sprite(atlas, Key.key("minecraft", path)));
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "").replace("-", "").trim();
    }
}
