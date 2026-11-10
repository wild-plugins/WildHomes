import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.yaml.snakeyaml.Yaml;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DialogShots {

    static final int W = 640;
    static final int H = 360;
    static final int S = 3;
    static final int HEADER = 33;
    static final int FOOTER = 33;
    static final int WHITE = 0xFFFFFF;

    record Glyph(BufferedImage img, int x, int y, int cellH, int height, int ascent, int advance) {
    }

    record Seg(String text, int color, String sprite) {
    }

    record Button(List<Seg> label, int width, List<List<Seg>> tooltip, boolean hover) {
    }

    record Home(int slot, String name, String icon, int x, int y, int z) {
    }

    static ZipFile jar;
    static final Map<Integer, Glyph> FONT = new HashMap<>();
    static final Map<String, String> SPRITES = new HashMap<>();
    static final Map<String, BufferedImage> TEXTURES = new HashMap<>();
    static final List<java.util.regex.Pattern> EXCLUDE = new ArrayList<>();
    static Map<String, Object> lang;
    static Map<String, String> names = new HashMap<>();
    static BufferedImage background;
    static int tooltipX = -1, tooltipY = -1;
    static List<List<Seg>> tooltipLines;

    public static void main(String[] args) throws Exception {
        jar = new ZipFile(args[0]);
        Path root = Path.of(args[1]);
        Path out = Path.of(args[2]);
        String language = args[3];
        Path names = args.length > 4 ? Path.of(args[4]) : null;
        Files.createDirectories(out);
        loadFont();
        for (String line : Files.readAllLines(root.resolve("src/main/resources/sprites.txt"))) {
            String[] p = line.split(" ");
            if (p.length == 4 && p[1].equals("26.1")) SPRITES.put(p[0], p[3]);
        }
        try (Reader r = Files.newBufferedReader(root.resolve("src/main/resources/lang/" + language + ".yml"))) {
            lang = new Yaml().load(r);
        }
        try (Reader r = Files.newBufferedReader(root.resolve("src/main/resources/config.yml"))) {
            Map<String, Object> config = new Yaml().load(r);
            Object dialogs = config.get("dialogs");
            Object icons = dialogs instanceof Map<?, ?> d ? d.get("icons") : null;
            Object exclude = icons instanceof Map<?, ?> i ? i.get("exclude") : null;
            if (exclude instanceof List<?> globs) {
                for (Object glob : globs) {
                    String[] parts = String.valueOf(glob).split("\\*", -1);
                    StringBuilder regex = new StringBuilder();
                    for (int p = 0; p < parts.length; p++) {
                        if (p > 0) regex.append(".*");
                        if (!parts[p].isEmpty()) regex.append(java.util.regex.Pattern.quote(parts[p]));
                    }
                    EXCLUDE.add(java.util.regex.Pattern.compile(regex.toString()));
                }
            }
        }
        loadNames(names);
        background = makeBackground();

        boolean de = language.equals("german");
        List<Home> homes = List.of(
                new Home(1, de ? "Basis" : "Base", "white_bed", 128, 64, -342),
                new Home(2, "Farm", "wheat", 410, 71, -205),
                new Home(3, de ? "Netherportal" : "Nether Portal", "obsidian", -96, 70, 512),
                new Home(4, de ? "Mine" : "Mine", "diamond_pickaxe", 1024, -42, 77));

        render(out.resolve("1-homes.png"), main(homes, 5, false, 1));
        render(out.resolve("2-homes-all.png"), main(homes, 5, true, 6));
        Home farm = homes.get(1);
        render(out.resolve("3-home.png"), detail(farm));
        render(out.resolve("4-rename.png"), rename(farm));
        render(out.resolve("5-icons.png"), icons(farm, "", 3 + 4));
        render(out.resolve("6-icons-search.png"), icons(farm, de ? "Diamant" : "diamond", 3 + 1));
        render(out.resolve("7-delete.png"), delete(farm));
        System.out.println("rendered " + language + " to " + out);
    }

    static String str(String path) {
        Object node = lang;
        for (String part : path.split("\\.")) {
            if (!(node instanceof Map<?, ?> m)) return "";
            node = m.get(part);
        }
        return node instanceof String s ? s : "";
    }

    @SuppressWarnings("unchecked")
    static List<String> lines(String path) {
        Object node = lang;
        for (String part : path.split("\\.")) {
            if (!(node instanceof Map<?, ?> m)) return List.of();
            node = m.get(part);
        }
        if (node instanceof List<?> l) return (List<String>) l;
        if (node instanceof String s && !s.isEmpty()) return List.of(s);
        return List.of();
    }

    static String fill(String text, Map<String, String> ph) {
        for (Map.Entry<String, String> e : ph.entrySet()) text = text.replace("{" + e.getKey() + "}", e.getValue());
        return text;
    }

    static Map<String, String> ph(Home h) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("icon", "<sprite:" + h.icon() + ">");
        m.put("home", h.name());
        m.put("world", "world");
        m.put("server", "survival");
        m.put("x", String.valueOf(h.x()));
        m.put("y", String.valueOf(h.y()));
        m.put("z", String.valueOf(h.z()));
        return m;
    }

    static List<List<Seg>> tip(List<String> raw, Map<String, String> ph) {
        List<List<Seg>> out = new ArrayList<>();
        for (String line : raw) out.add(mini(fill(line, ph), WHITE));
        return out;
    }

    static Consumer<BufferedImage> main(List<Home> homes, int limit, boolean expanded, int hoverIndex) {
        int maxSlots = 28, preview = 7, width = 110, columns = 4;
        List<Button> buttons = new ArrayList<>();
        int shown = expanded ? maxSlots : preview;
        for (int slot = 1; slot <= shown; slot++) {
            int i = buttons.size();
            Home home = null;
            for (Home h : homes) if (h.slot() == slot) home = h;
            if (home != null) {
                buttons.add(new Button(mini(fill(str("dialogs.main.home"), ph(home)), WHITE), width, tip(lines("dialogs.main.home-tooltip"), ph(home)), i == hoverIndex));
            } else if (slot <= limit) {
                buttons.add(new Button(mini(str("dialogs.main.empty"), WHITE), width, tip(lines("dialogs.main.empty-tooltip"), Map.of()), i == hoverIndex));
            } else {
                buttons.add(new Button(mini(str("dialogs.main.locked"), WHITE), width, tip(lines("dialogs.main.locked-tooltip"), Map.of()), i == hoverIndex));
            }
        }
        Button exit = null;
        if (!expanded) buttons.add(new Button(mini(str("dialogs.main.more"), WHITE), width, List.of(), false));
        else exit = new Button(mini(str("dialogs.main.less"), WHITE), width, List.of(), false);
        Button footer = exit;
        return canvas -> screen(canvas, mini(str("dialogs.main.title"), WHITE), List.of(itemBody("white_bed")), buttons, columns, footer);
    }

    static Consumer<BufferedImage> detail(Home home) {
        Map<String, String> ph = ph(home);
        List<Button> buttons = List.of(
                new Button(mini(fill(str("dialogs.home.teleport"), ph), WHITE), 150, List.of(), true),
                new Button(mini(fill(str("dialogs.home.icon"), ph), WHITE), 150, List.of(), false),
                new Button(mini(fill(str("dialogs.home.rename"), ph), WHITE), 150, List.of(), false),
                new Button(mini(fill(str("dialogs.home.delete"), ph), WHITE), 150, List.of(), false));
        Button back = new Button(mini(str("dialogs.home.back"), WHITE), 150, List.of(), false);
        return canvas -> screen(canvas, mini(fill(str("dialogs.home.title"), ph), WHITE), List.of(itemBody(home.icon())), buttons, 2, back);
    }

    static Consumer<BufferedImage> rename(Home home) {
        Map<String, String> ph = ph(home);
        List<Button> buttons = List.of(new Button(mini(str("dialogs.rename.save"), WHITE), 200, List.of(), true));
        Button cancel = new Button(mini(str("dialogs.rename.cancel"), WHITE), 200, List.of(), false);
        return canvas -> screen(canvas, mini(fill(str("dialogs.rename.title"), ph), WHITE),
                List.of(inputBody(str("dialogs.rename.label"), home.name(), 200)), buttons, 1, cancel);
    }

    static Consumer<BufferedImage> icons(Home home, String query, int hover) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(new Button(mini(str("dialogs.icons.search"), WHITE), 115, List.of(), false));
        buttons.add(new Button(mini(str("dialogs.icons.default"), WHITE), 115, List.of(), false));
        buttons.add(new Button(mini(str("dialogs.icons.back"), WHITE), 115, List.of(), false));
        List<String> keys = new ArrayList<>(SPRITES.keySet());
        keys.removeIf(k -> !names.containsKey(k) || EXCLUDE.stream().anyMatch(p -> p.matcher(k).matches()));
        keys.sort(String::compareTo);
        String q = query.toLowerCase().replace("_", "").replace(" ", "");
        for (String key : keys) {
            if (!q.isEmpty() && !key.replace("_", "").contains(q) && !names.get(key).toLowerCase().replace(" ", "").contains(q)) continue;
            Map<String, String> ph = Map.of("icon", "<sprite:" + key + ">", "item", names.get(key));
            buttons.add(new Button(mini(fill(str("dialogs.icons.item"), ph), WHITE), 115, List.of(), buttons.size() == hover));
        }
        return canvas -> screen(canvas, mini(fill(str("dialogs.icons.title"), ph(home)), WHITE),
                List.of(itemBody(home.icon()), inputBody(str("dialogs.icons.search-label"), query, 230)), buttons, 4, null);
    }

    static Consumer<BufferedImage> delete(Home home) {
        Map<String, String> ph = ph(home);
        List<Button> buttons = List.of(
                new Button(mini(fill(str("dialogs.delete.cancel"), ph), WHITE), 150, List.of(), false),
                new Button(mini(fill(str("dialogs.delete.confirm"), ph), WHITE), 150, List.of(), true));
        return canvas -> screen(canvas, mini(fill(str("dialogs.delete.title"), ph), WHITE), List.of(), buttons, 2, null);
    }

    interface Body {
        int height();

        void draw(BufferedImage canvas, int y);
    }

    static Body itemBody(String key) {
        return new Body() {
            public int height() {
                return 16;
            }

            public void draw(BufferedImage canvas, int y) {
                BufferedImage icon = key.equals("white_bed") ? bed() : texture(SPRITES.getOrDefault(key, "item/" + key));
                int x = W / 2 - 8;
                blitScaled(canvas, icon, x * S, y * S, 16 * S, 16 * S);
            }
        };
    }

    static Body inputBody(String label, String value, int width) {
        return new Body() {
            public int height() {
                return 9 + 4 + 20;
            }

            public void draw(BufferedImage canvas, int y) {
                int x = W / 2 - width / 2;
                drawSegs(canvas, mini(label, 0xE0E0E0), x, y);
                int by = y + 13;
                fill(canvas, x, by, width, 20, 0xFFA0A0A0);
                fill(canvas, x + 1, by + 1, width - 2, 18, 0xFF000000);
                drawSegs(canvas, mini(value, 0xE0E0E0), x + 4, by + 6);
            }
        };
    }

    static void screen(BufferedImage canvas, List<Seg> title, List<Body> bodies, List<Button> buttons, int columns, Button exit) {
        tooltipLines = null;
        int titleW = width(title);
        int groupW = titleW + 10 + 20;
        int tx = W / 2 - groupW / 2;
        drawSegs(canvas, title, tx, (HEADER - 8) / 2);
        blitScaled(canvas, gui("dialog/warning_button"), (tx + titleW + 10) * S, 6 * S, 20 * S, 20 * S);

        int bw = buttons.isEmpty() ? 0 : buttons.getFirst().width();
        int rows = (buttons.size() + columns - 1) / columns;
        int gridH = rows == 0 ? 0 : rows * 20 + (rows - 1) * 2;
        int contentH = 0;
        for (Body b : bodies) contentH += b.height() + 10;
        contentH += gridH;
        int top = HEADER, bottom = H - FOOTER;
        int y = contentH <= bottom - top ? top + (bottom - top - contentH) / 2 : top + 4;
        for (Body b : bodies) {
            b.draw(canvas, y);
            y += b.height() + 10;
        }
        for (int i = 0; i < buttons.size(); i++) {
            int row = i / columns, col = i % columns;
            int inRow = Math.min(columns, buttons.size() - row * columns);
            int rowW = inRow * bw + (inRow - 1) * 2;
            int x = W / 2 - rowW / 2 + col * (bw + 2);
            int by = y + row * 22;
            if (by + 20 > bottom + (exit == null ? FOOTER : 0)) break;
            button(canvas, buttons.get(i), x, by);
        }
        if (contentH > bottom - top) {
            int sx = W - 12;
            int trackTop = top, trackH = H - top - 2;
            nine(canvas, gui("widget/scroller_background"), sx, trackTop, 6, trackH, 1);
            int handle = Math.max(32, trackH * (bottom - top) / contentH);
            nine(canvas, gui("widget/scroller"), sx, trackTop, 6, handle, 1);
        }
        if (exit != null) button(canvas, exit, W / 2 - exit.width() / 2, H - FOOTER + 6);
        if (tooltipLines != null && !tooltipLines.isEmpty()) tooltip(canvas, tooltipLines, tooltipX, tooltipY);
    }

    static int clipX0 = Integer.MIN_VALUE, clipX1 = Integer.MAX_VALUE;

    static void button(BufferedImage canvas, Button b, int x, int y) {
        nine(canvas, tint(gui(b.hover() ? "widget/button_highlighted" : "widget/button"), 0.64), x, y, b.width(), 20, 3);
        int tw = width(b.label());
        int tx = tw > b.width() - 8 ? x + 4 : x + b.width() / 2 - tw / 2;
        clipX0 = x + 2;
        clipX1 = x + b.width() - 2;
        drawSegs(canvas, b.label(), tx, y + 6);
        clipX0 = Integer.MIN_VALUE;
        clipX1 = Integer.MAX_VALUE;
        if (b.hover() && !b.tooltip().isEmpty()) {
            tooltipLines = b.tooltip();
            tooltipX = x + b.width() / 2 + 12;
            tooltipY = y + 14;
        }
    }

    static void tooltip(BufferedImage canvas, List<List<Seg>> lines, int x, int y) {
        int w = 0;
        for (List<Seg> l : lines) w = Math.max(w, width(l));
        int h = lines.size() * 10 - 2 + (lines.size() > 1 ? 2 : 0);
        if (x + w + 12 > W) x = W - w - 12;
        if (y + h + 6 > H) y = H - h - 6;
        nine(canvas, gui("tooltip/background"), x - 14, y - 14, w + 28, h + 28, 9);
        nineStretch(canvas, gui("tooltip/frame"), x - 14, y - 14, w + 28, h + 28, 10);
        int ly = y;
        for (List<Seg> l : lines) {
            drawSegs(canvas, l, x, ly);
            ly += ly == y && lines.size() > 1 ? 12 : 10;
        }
    }

    static void render(Path file, Consumer<BufferedImage> scene) throws Exception {
        BufferedImage canvas = new BufferedImage(W * S, H * S, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = canvas.createGraphics();
        g.drawImage(background, 0, 0, null);
        g.dispose();
        scene.accept(canvas);
        BufferedImage rgb = new BufferedImage(W * S, H * S, BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(canvas, 0, 0, null);
        ImageIO.write(rgb, "png", file.toFile());
    }

    static BufferedImage makeBackground() {
        int w = W * S, h = H * S;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0x6F96D8), 0, h * 0.7f, new java.awt.Color(0xA9C3EC)));
        g.fillRect(0, 0, w, h);
        java.util.Random rnd = new java.util.Random(7);
        g.setColor(new java.awt.Color(0x4F6E9E));
        g.fillRect((int) (w * 0.44), 0, (int) (w * 0.035), h);
        int[] greens = {0x2E4A1C, 0x3B5E22, 0x24401A, 0x46702A, 0x1E3315};
        for (int i = 0; i < 70; i++) {
            g.setColor(new java.awt.Color(greens[rnd.nextInt(greens.length)]));
            int r = 120 + rnd.nextInt(260);
            int cx = rnd.nextInt(w), cy = (int) (h * 0.62) + rnd.nextInt((int) (h * 0.5));
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
        }
        g.setColor(new java.awt.Color(0x283D1A));
        g.fillRect((int) (w * 0.56), 0, w, h);
        for (int i = 0; i < 25; i++) {
            g.setColor(new java.awt.Color(greens[rnd.nextInt(greens.length)]));
            int r = 80 + rnd.nextInt(200);
            g.fillOval((int) (w * 0.5) + rnd.nextInt(w / 2) - r, rnd.nextInt(h) - r, r * 2, r * 2);
        }
        g.dispose();
        for (int pass = 0; pass < 3; pass++) out = boxBlur(out, 18);
        return out;
    }

    static BufferedImage boxBlur(BufferedImage src, int r) {
        int w = src.getWidth(), h = src.getHeight();
        int[] in = src.getRGB(0, 0, w, h, null, 0, w);
        int[] tmp = new int[in.length];
        int[] out = new int[in.length];
        blurPass(in, tmp, w, h, r, true);
        blurPass(tmp, out, w, h, r, false);
        BufferedImage res = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        res.setRGB(0, 0, w, h, out, 0, w);
        return res;
    }

    static void blurPass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int outer = horizontal ? h : w, inner = horizontal ? w : h;
        for (int o = 0; o < outer; o++) {
            long sr = 0, sg = 0, sb = 0;
            int n = 0;
            for (int i = -r; i <= r; i++) {
                int idx = clampIdx(i, inner);
                int p = in[horizontal ? o * w + idx : idx * w + o];
                sr += (p >> 16) & 255;
                sg += (p >> 8) & 255;
                sb += p & 255;
                n++;
            }
            for (int i = 0; i < inner; i++) {
                out[horizontal ? o * w + i : i * w + o] = 0xFF000000 | (int) (sr / n) << 16 | (int) (sg / n) << 8 | (int) (sb / n);
                int addIdx = clampIdx(i + r + 1, inner), remIdx = clampIdx(i - r, inner);
                int pa = in[horizontal ? o * w + addIdx : addIdx * w + o];
                int pr = in[horizontal ? o * w + remIdx : remIdx * w + o];
                sr += ((pa >> 16) & 255) - ((pr >> 16) & 255);
                sg += ((pa >> 8) & 255) - ((pr >> 8) & 255);
                sb += (pa & 255) - (pr & 255);
            }
        }
    }

    static int clampIdx(int i, int n) {
        return Math.max(0, Math.min(n - 1, i));
    }

    static BufferedImage bed() {
        BufferedImage tex = texture("entity/bed/white");
        int size = 16 * S;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        double[][] faces = {
                {0, 9, 0, 16, 9, 0, 16, 9, 16, 0, 9, 16, 6, 6, 22, 22, 1.0},
                {0, 9, 16, 16, 9, 16, 16, 9, 32, 0, 9, 32, 6, 28, 22, 44, 1.0},
                {0, 9, 0, 0, 9, 16, 0, 3, 16, 0, 3, 0, 0, 6, 6, 22, 0.8},
                {0, 9, 16, 0, 9, 32, 0, 3, 32, 0, 3, 16, 0, 28, 6, 44, 0.8},
                {16, 9, 0, 16, 9, 16, 16, 3, 16, 16, 3, 0, 22, 6, 28, 22, 0.6},
                {16, 9, 16, 16, 9, 32, 16, 3, 32, 16, 3, 16, 22, 28, 28, 44, 0.6},
                {0, 9, 32, 16, 9, 32, 16, 3, 32, 0, 3, 32, 6, 22, 22, 28, 0.7},
                {0, 9, 0, 16, 9, 0, 16, 3, 0, 0, 3, 0, 6, 0, 22, 6, 0.7},
        };
        double yaw = Math.toRadians(225 - 90), pitch = Math.toRadians(30);
        double sc = size / 21.0;
        double[] zbuf = new double[size * size];
        java.util.Arrays.fill(zbuf, Double.NEGATIVE_INFINITY);
        for (double[] f : faces) {
            double[][] p = new double[4][];
            for (int k = 0; k < 4; k++) p[k] = project(f[k * 3] - 8, f[k * 3 + 1] - 6, f[k * 3 + 2] - 16, yaw, pitch, sc, size);
            rasterQuad(out, zbuf, p, tex, f[12], f[13], f[14], f[15], f[16]);
        }
        return out;
    }

    static double[] project(double x, double y, double z, double yaw, double pitch, double sc, int size) {
        double cx = x * Math.cos(yaw) - z * Math.sin(yaw);
        double cz = x * Math.sin(yaw) + z * Math.cos(yaw);
        double cy = y * Math.cos(pitch) - cz * Math.sin(pitch);
        double depth = y * Math.sin(pitch) + cz * Math.cos(pitch);
        return new double[]{size / 2.0 + cx * sc, size / 2.0 - cy * sc + size * 0.08, depth};
    }

    static void rasterQuad(BufferedImage out, double[] zbuf, double[][] p, BufferedImage tex, double u0, double v0, double u1, double v1, double shade) {
        double[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        rasterTri(out, zbuf, p[0], p[1], p[2], uv[0], uv[1], uv[2], tex, shade);
        rasterTri(out, zbuf, p[0], p[2], p[3], uv[0], uv[2], uv[3], tex, shade);
    }

    static void rasterTri(BufferedImage out, double[] zbuf, double[] a, double[] b, double[] c, double[] ta, double[] tb, double[] tc, BufferedImage tex, double shade) {
        int size = out.getWidth();
        int minX = (int) Math.max(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int maxX = (int) Math.min(size - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int minY = (int) Math.max(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int maxY = (int) Math.min(size - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        double area = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1]);
        if (Math.abs(area) < 1e-9) return;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5, py = y + 0.5;
                double w0 = ((b[0] - px) * (c[1] - py) - (c[0] - px) * (b[1] - py)) / area;
                double w1 = ((c[0] - px) * (a[1] - py) - (a[0] - px) * (c[1] - py)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                double depth = w0 * a[2] + w1 * b[2] + w2 * c[2];
                if (depth < zbuf[y * size + x]) continue;
                double u = w0 * ta[0] + w1 * tb[0] + w2 * tc[0];
                double v = w0 * ta[1] + w1 * tb[1] + w2 * tc[1];
                int tx = Math.max(0, Math.min(tex.getWidth() - 1, (int) Math.floor(u * tex.getWidth() / 64.0)));
                int ty = Math.max(0, Math.min(tex.getHeight() - 1, (int) Math.floor(v * tex.getHeight() / 64.0)));
                int col = tex.getRGB(tx, ty);
                if ((col >>> 24) == 0) continue;
                int r = (int) (((col >> 16) & 255) * shade), g = (int) (((col >> 8) & 255) * shade), bl = (int) ((col & 255) * shade);
                out.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | bl);
                zbuf[y * size + x] = depth;
            }
        }
    }

    static BufferedImage texture(String path) {
        return TEXTURES.computeIfAbsent(path, p -> {
            try {
                BufferedImage img = read("assets/minecraft/textures/" + p + ".png");
                if (img != null && img.getHeight() > img.getWidth()) img = img.getSubimage(0, 0, img.getWidth(), img.getWidth());
                return img == null ? new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB) : img;
            } catch (Exception e) {
                return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            }
        });
    }

    static BufferedImage gui(String sprite) {
        return texture("gui/sprites/" + sprite);
    }

    static BufferedImage read(String path) throws Exception {
        ZipEntry e = jar.getEntry(path);
        if (e == null) return null;
        try (InputStream in = jar.getInputStream(e)) {
            return ImageIO.read(in);
        }
    }

    static void nine(BufferedImage canvas, BufferedImage sprite, int x, int y, int w, int h, int border) {
        int sw = sprite.getWidth(), sh = sprite.getHeight();
        int[] xs = {0, border, sw - border, sw};
        int[] ys = {0, border, sh - border, sh};
        int[] dx = {x, x + border, x + w - border, x + w};
        int[] dy = {y, y + border, y + h - border, y + h};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int sx0 = xs[i], sx1 = xs[i + 1], sy0 = ys[j], sy1 = ys[j + 1];
                int dx0 = dx[i], dx1 = dx[i + 1], dy0 = dy[j], dy1 = dy[j + 1];
                if (dx1 <= dx0 || dy1 <= dy0 || sx1 <= sx0 || sy1 <= sy0) continue;
                for (int py = dy0; py < dy1; py++) {
                    int sy = sy0 + (py - dy0) % (sy1 - sy0);
                    for (int px = dx0; px < dx1; px++) {
                        int sx = sx0 + (px - dx0) % (sx1 - sx0);
                        plot(canvas, px, py, sprite.getRGB(sx, sy));
                    }
                }
            }
        }
    }

    static final Map<BufferedImage, BufferedImage> TINTED = new HashMap<>();

    static BufferedImage tint(BufferedImage src, double factor) {
        return TINTED.computeIfAbsent(src, s -> {
            BufferedImage out = new BufferedImage(s.getWidth(), s.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < s.getHeight(); y++) {
                for (int x = 0; x < s.getWidth(); x++) {
                    int c = s.getRGB(x, y), rgb = c & 0xFFFFFF;
                    if (rgb != 0xFFFFFF && rgb != 0) {
                        int r = (int) (((c >> 16) & 255) * factor), g = (int) (((c >> 8) & 255) * factor), b = (int) ((c & 255) * factor);
                        c = (c & 0xFF000000) | r << 16 | g << 8 | b;
                    }
                    out.setRGB(x, y, c);
                }
            }
            return out;
        });
    }

    static void nineStretch(BufferedImage canvas, BufferedImage sprite, int x, int y, int w, int h, int border) {
        int sw = sprite.getWidth(), sh = sprite.getHeight();
        for (int py = 0; py < h; py++) {
            int sy = py < border ? py : py >= h - border ? sh - (h - py) : border + (py - border) * (sh - 2 * border) / Math.max(1, h - 2 * border);
            for (int px = 0; px < w; px++) {
                int sx = px < border ? px : px >= w - border ? sw - (w - px) : border + (px - border) * (sw - 2 * border) / Math.max(1, w - 2 * border);
                plot(canvas, x + px, y + py, sprite.getRGB(sx, sy));
            }
        }
    }

    static void plot(BufferedImage canvas, int gx, int gy, int argb) {
        if (gx < clipX0 || gx >= clipX1) return;
        int a = argb >>> 24;
        if (a == 0) return;
        for (int dy = 0; dy < S; dy++) {
            for (int dx = 0; dx < S; dx++) {
                int px = gx * S + dx, py = gy * S + dy;
                if (px < 0 || py < 0 || px >= canvas.getWidth() || py >= canvas.getHeight()) continue;
                canvas.setRGB(px, py, a == 255 ? argb : blend(canvas.getRGB(px, py), argb));
            }
        }
    }

    static int blend(int dst, int src) {
        double a = (src >>> 24) / 255.0;
        int r = (int) (((src >> 16) & 255) * a + ((dst >> 16) & 255) * (1 - a));
        int g = (int) (((src >> 8) & 255) * a + ((dst >> 8) & 255) * (1 - a));
        int b = (int) ((src & 255) * a + (dst & 255) * (1 - a));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static void fill(BufferedImage canvas, int x, int y, int w, int h, int argb) {
        for (int py = y; py < y + h; py++) for (int px = x; px < x + w; px++) plot(canvas, px, py, argb);
    }

    static void blitScaled(BufferedImage canvas, BufferedImage img, int x, int y, int w, int h) {
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int col = img.getRGB(px * img.getWidth() / w, py * img.getHeight() / h);
                int a = col >>> 24;
                if (a == 0) continue;
                int cx = x + px, cy = y + py;
                if (cx < 0 || cy < 0 || cx >= canvas.getWidth() || cy >= canvas.getHeight()) continue;
                canvas.setRGB(cx, cy, a == 255 ? col : blend(canvas.getRGB(cx, cy), col));
            }
        }
    }

    static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("black", 0x000000), Map.entry("dark_blue", 0x0000AA), Map.entry("dark_green", 0x00AA00),
            Map.entry("dark_aqua", 0x00AAAA), Map.entry("dark_red", 0xAA0000), Map.entry("dark_purple", 0xAA00AA),
            Map.entry("gold", 0xFFAA00), Map.entry("gray", 0xAAAAAA), Map.entry("dark_gray", 0x555555),
            Map.entry("blue", 0x5555FF), Map.entry("green", 0x55FF55), Map.entry("aqua", 0x55FFFF),
            Map.entry("red", 0xFF5555), Map.entry("light_purple", 0xFF55FF), Map.entry("yellow", 0xFFFF55),
            Map.entry("white", 0xFFFFFF));

    static List<Seg> mini(String text, int base) {
        List<Seg> out = new ArrayList<>();
        java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>();
        int color = base;
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            int close = text.indexOf('>', i);
            if (ch == '<' && close > i) {
                String tag = text.substring(i + 1, close);
                String low = tag.toLowerCase();
                Integer named = COLORS.get(low);
                boolean handled = true;
                if (low.startsWith("sprite:")) {
                    if (!cur.isEmpty()) out.add(new Seg(cur.toString(), color, null));
                    cur.setLength(0);
                    out.add(new Seg("", color, tag.substring(7)));
                } else if (named != null || (low.startsWith("#") && low.length() == 7)) {
                    if (!cur.isEmpty()) out.add(new Seg(cur.toString(), color, null));
                    cur.setLength(0);
                    stack.push(color);
                    color = named != null ? named : Integer.parseInt(low.substring(1), 16);
                } else if (low.startsWith("/")) {
                    if (!cur.isEmpty()) out.add(new Seg(cur.toString(), color, null));
                    cur.setLength(0);
                    color = stack.isEmpty() ? base : stack.pop();
                } else {
                    handled = false;
                }
                if (handled) {
                    i = close + 1;
                    continue;
                }
            }
            cur.append(ch);
            i++;
        }
        if (!cur.isEmpty()) out.add(new Seg(cur.toString(), color, null));
        return out;
    }

    static int width(List<Seg> segs) {
        int w = 0;
        for (Seg s : segs) {
            if (s.sprite() != null) w += 9;
            else for (int cp : s.text().codePoints().toArray()) w += advance(cp);
        }
        return w;
    }

    static void drawSegs(BufferedImage canvas, List<Seg> segs, int x, int y) {
        int cx = x;
        for (Seg s : segs) {
            if (s.sprite() != null) {
                String path = SPRITES.getOrDefault(s.sprite(), "item/" + s.sprite());
                if (cx >= clipX0 && cx + 8 <= clipX1) blitScaled(canvas, texture(path), cx * S, y * S, 8 * S, 8 * S);
                cx += 9;
                continue;
            }
            int shadow = ((s.color() >> 16 & 255) / 4 << 16) | ((s.color() >> 8 & 255) / 4 << 8) | ((s.color() & 255) / 4);
            int sx = cx + 1;
            for (int cp : s.text().codePoints().toArray()) sx += glyph(canvas, cp, sx, y + 7 + 1, shadow);
            for (int cp : s.text().codePoints().toArray()) cx += glyph(canvas, cp, cx, y + 7, s.color());
        }
    }

    static void loadFont() throws Exception {
        JsonObject root;
        try (Reader r = new InputStreamReader(jar.getInputStream(jar.getEntry("assets/minecraft/font/include/default.json")), StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(r).getAsJsonObject();
        }
        for (JsonElement raw : root.getAsJsonArray("providers")) {
            JsonObject p = raw.getAsJsonObject();
            if (!p.has("file")) continue;
            String file = p.get("file").getAsString().replace("minecraft:", "");
            BufferedImage img = read("assets/minecraft/textures/" + file);
            JsonArray rows = p.getAsJsonArray("chars");
            int height = p.has("height") ? p.get("height").getAsInt() : 8;
            int ascent = p.get("ascent").getAsInt();
            String first = rows.get(0).getAsString();
            int cellW = img.getWidth() / first.codePointCount(0, first.length());
            int cellH = img.getHeight() / rows.size();
            for (int r = 0; r < rows.size(); r++) {
                int[] cps = rows.get(r).getAsString().codePoints().toArray();
                for (int c = 0; c < cps.length; c++) {
                    int cp = cps[c];
                    if (cp == 0 || FONT.containsKey(cp)) continue;
                    int gx = c * cellW, gy = r * cellH, w = 0;
                    for (int xx = cellW - 1; xx >= 0 && w == 0; xx--) {
                        for (int yy = 0; yy < cellH; yy++) {
                            if ((img.getRGB(gx + xx, gy + yy) >>> 24) != 0) {
                                w = xx + 1;
                                break;
                            }
                        }
                    }
                    double factor = (double) height / cellH;
                    FONT.put(cp, new Glyph(img, gx, gy, cellH, height, ascent, (int) Math.round(w * factor) + 1));
                }
            }
        }
    }

    static int advance(int cp) {
        if (cp == ' ') return 4;
        Glyph g = FONT.get(cp);
        return g == null ? 6 : g.advance();
    }

    static int glyph(BufferedImage canvas, int cp, int x, int baseline, int rgb) {
        if (cp == ' ') return 4;
        Glyph g = FONT.get(cp);
        if (g == null) return 6;
        double factor = (double) g.height() / g.cellH();
        int top = baseline - g.ascent();
        for (int gy = 0; gy < g.height(); gy++) {
            for (int gx = 0; gx < g.advance() - 1; gx++) {
                int sx = g.x() + (int) (gx / factor), sy = g.y() + (int) (gy / factor);
                if ((g.img().getRGB(sx, sy) >>> 24) == 0) continue;
                plot(canvas, x + gx, top + gy, 0xFF000000 | rgb);
            }
        }
        return g.advance();
    }

    static void loadNames(Path override) throws Exception {
        JsonObject json;
        if (override != null) {
            try (Reader r = Files.newBufferedReader(override)) {
                json = JsonParser.parseReader(r).getAsJsonObject();
            }
        } else {
            try (Reader r = new InputStreamReader(jar.getInputStream(jar.getEntry("assets/minecraft/lang/en_us.json")), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(r).getAsJsonObject();
            }
        }
        for (String key : SPRITES.keySet()) {
            for (String prefix : new String[]{"item.minecraft.", "block.minecraft."}) {
                if (json.has(prefix + key)) {
                    names.put(key, json.get(prefix + key).getAsString());
                    break;
                }
            }
        }
    }
}
