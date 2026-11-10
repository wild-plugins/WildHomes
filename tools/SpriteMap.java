import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class SpriteMap {

    private static final String ITEMS = "assets/minecraft/items/";
    private static final String MODELS = "assets/minecraft/models/";
    private static final List<String> PRIORITY = List.of("layer0", "all", "side", "texture", "front", "top", "end", "cross", "plant", "pattern", "wool", "particle");

    private static ZipFile jar;

    public static void main(String[] args) throws Exception {
        jar = new ZipFile(args[0]);
        List<String> lines = new ArrayList<>();
        for (ZipEntry entry : Collections.list(jar.entries())) {
            String name = entry.getName();
            if (!name.startsWith(ITEMS) || !name.endsWith(".json")) continue;
            String id = name.substring(ITEMS.length(), name.length() - 5);
            String model = findModel(read(name));
            if (model == null) continue;
            String texture = texture(model);
            if (texture == null) continue;
            texture = strip(texture);
            String atlas;
            if (texture.startsWith("block/")) atlas = "blocks";
            else if (texture.startsWith("item/")) atlas = "items";
            else continue;
            lines.add(id + " " + atlas + " " + texture);
        }
        Collections.sort(lines);
        Files.write(Path.of(args[1]), lines, StandardCharsets.UTF_8);
        System.out.println(lines.size() + " sprites written to " + args[1]);
    }

    private static JsonObject read(String path) throws Exception {
        ZipEntry entry = jar.getEntry(path);
        if (entry == null) return null;
        try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static String findModel(JsonElement node) {
        if (node == null) return null;
        if (node.isJsonObject()) {
            JsonObject obj = node.getAsJsonObject();
            if (obj.has("type") && strip(obj.get("type").getAsString()).equals("model") && obj.get("model").isJsonPrimitive()) {
                return obj.get("model").getAsString();
            }
            if (obj.has("type") && strip(obj.get("type").getAsString()).equals("special") && obj.has("base")) {
                return obj.get("base").getAsString();
            }
            for (String key : List.of("model", "fallback", "on_false", "on_true", "cases", "entries", "models")) {
                if (!obj.has(key)) continue;
                String found = findModel(obj.get(key));
                if (found != null) return found;
            }
        } else if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) {
                String found = findModel(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String texture(String model) throws Exception {
        Map<String, String> textures = new HashMap<>();
        String current = model;
        int depth = 0;
        while (current != null && depth++ < 20) {
            JsonObject json = read(MODELS + strip(current) + ".json");
            if (json == null) break;
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("textures").entrySet()) {
                    if (e.getValue().isJsonPrimitive()) textures.putIfAbsent(e.getKey(), e.getValue().getAsString());
                }
            }
            current = json.has("parent") ? json.get("parent").getAsString() : null;
        }
        for (String key : PRIORITY) {
            String resolved = resolve(textures, key);
            if (resolved != null) return resolved;
        }
        for (String key : textures.keySet()) {
            String resolved = resolve(textures, key);
            if (resolved != null) return resolved;
        }
        return null;
    }

    private static String resolve(Map<String, String> textures, String key) {
        String value = textures.get(key);
        int guard = 0;
        while (value != null && value.startsWith("#") && guard++ < 10) value = textures.get(value.substring(1));
        return value == null || value.startsWith("#") ? null : value;
    }

    private static String strip(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }
}
