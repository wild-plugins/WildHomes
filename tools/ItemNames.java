import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

public final class ItemNames {

    public static void main(String[] args) throws Exception {
        Path sprites = Path.of(args[0]);
        JsonObject english;
        try (ZipFile jar = new ZipFile(args[1]);
             Reader r = new InputStreamReader(jar.getInputStream(jar.getEntry("assets/minecraft/lang/en_us.json")), StandardCharsets.UTF_8)) {
            english = JsonParser.parseReader(r).getAsJsonObject();
        }
        JsonObject german;
        try (Reader r = Files.newBufferedReader(Path.of(args[2]), StandardCharsets.UTF_8)) {
            german = JsonParser.parseReader(r).getAsJsonObject();
        }
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(sprites, StandardCharsets.UTF_8)) {
            String key = line.split(" ")[0];
            String en = name(english, key);
            String de = name(german, key);
            if (en == null && de == null) continue;
            lines.add(key + "\t" + (en == null ? "" : en) + "\t" + (de == null ? "" : de));
        }
        Files.write(Path.of(args[3]), lines, StandardCharsets.UTF_8);
        System.out.println(lines.size() + " names written to " + args[3]);
    }

    private static String name(JsonObject lang, String key) {
        for (String prefix : new String[]{"item.minecraft.", "block.minecraft."}) {
            if (lang.has(prefix + key)) return lang.get(prefix + key).getAsString();
        }
        return null;
    }
}
