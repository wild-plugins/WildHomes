package de.justgino.plugins.wildhomes.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.justgino.plugins.wildhomes.WildHomes;
import de.justgino.plugins.wildhomes.home.Home;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class Database {

    public record Pending(UUID player, String server, String home, String world,
                          double x, double y, double z, float yaw, float pitch, long created) {
    }

    private final WildHomes plugin;
    private HikariDataSource source;
    private boolean mariadb;
    private String homes;
    private String players;
    private String pending;
    private String servers;

    public Database(WildHomes plugin) {
        this.plugin = plugin;
    }

    public boolean isMariaDb() {
        return mariadb;
    }

    public void connect() throws SQLException {
        File file = new File(plugin.getDataFolder(), "database.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String type = cfg.getString("type", "sqlite").trim().toLowerCase(Locale.ROOT);
        mariadb = type.equals("mariadb") || type.equals("mysql");
        String prefix = cfg.getString("table-prefix", "wildhomes_").replaceAll("[^A-Za-z0-9_]", "");
        homes = prefix + "homes";
        players = prefix + "players";
        pending = prefix + "pending";
        servers = prefix + "servers";

        try {
            Configurator.setLevel("de.justgino.plugins.wildhomes.libs.hikari", Level.WARN);
        } catch (Throwable ignored) {
        }
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("WildHomes");
        hikari.setConnectionTimeout(Math.max(1_000L, cfg.getLong("pool.connection-timeout", 10_000L)));
        if (mariadb) {
            ConfigurationSection db = cfg.getConfigurationSection("mariadb");
            if (db == null) db = cfg.createSection("mariadb");
            Map<String, String> params = new LinkedHashMap<>();
            params.put("sslMode", "disable");
            params.put("allowPublicKeyRetrieval", "true");
            params.put("connectTimeout", "5000");
            params.put("socketTimeout", "30000");
            ConfigurationSection extra = db.getConfigurationSection("properties");
            if (extra != null) for (String key : extra.getKeys(false)) params.put(key, String.valueOf(extra.get(key)));
            String query = params.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
            hikari.setDriverClassName("org.mariadb.jdbc.Driver");
            hikari.setJdbcUrl("jdbc:mariadb://" + db.getString("host", "localhost") + ":" + db.getInt("port", 3306)
                    + "/" + db.getString("database", "wildhomes") + "?" + query);
            hikari.setUsername(db.getString("username", "root"));
            hikari.setPassword(db.getString("password", ""));
            int size = Math.max(1, cfg.getInt("pool.maximum-size", db.getInt("pool-size", 4)));
            hikari.setMaximumPoolSize(size);
            hikari.setMinimumIdle(Math.max(0, Math.min(size, cfg.getInt("pool.minimum-idle", 1))));
            long idle = cfg.getLong("pool.idle-timeout", 60_000L);
            hikari.setIdleTimeout(idle <= 0 ? 0 : Math.max(10_000L, idle));
            hikari.setMaxLifetime(Math.max(30_000L, cfg.getLong("pool.max-lifetime", 600_000L)));
            long keepalive = cfg.getLong("pool.keepalive-time", 0L);
            hikari.setKeepaliveTime(keepalive <= 0 ? 0 : Math.max(30_000L, Math.min(keepalive, hikari.getMaxLifetime() - 1_000L)));
            long leak = cfg.getLong("pool.leak-detection", 0L);
            hikari.setLeakDetectionThreshold(leak <= 0 ? 0 : Math.max(2_000L, leak));
        } else {
            File db = new File(plugin.getDataFolder(), cfg.getString("sqlite.file", "homes.db"));
            String journal = cfg.getString("sqlite.journal-mode", "WAL").replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
            long busy = Math.max(0L, cfg.getLong("sqlite.busy-timeout", 5_000L));
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setJdbcUrl("jdbc:sqlite:" + db.getAbsolutePath() + "?journal_mode=" + journal + "&synchronous=NORMAL&busy_timeout=" + busy);
            hikari.setMaximumPoolSize(1);
        }
        source = new HikariDataSource(hikari);
        createTables();
        plugin.getLogger().info("Connected to " + (mariadb ? "MariaDB" : "SQLite") + ".");
    }

    private void createTables() throws SQLException {
        String options = mariadb ? " DEFAULT CHARSET=utf8mb4" : "";
        try (Connection c = source.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + homes + " ("
                    + "owner VARCHAR(36) NOT NULL, slot INT NOT NULL, name VARCHAR(64) NOT NULL, "
                    + "server VARCHAR(64) NOT NULL, world VARCHAR(128) NOT NULL, "
                    + "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, yaw FLOAT NOT NULL, pitch FLOAT NOT NULL, "
                    + "icon VARCHAR(128), created BIGINT NOT NULL, PRIMARY KEY (owner, slot))" + options);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + players + " ("
                    + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, name_lower VARCHAR(32) NOT NULL)" + options);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + pending + " ("
                    + "player VARCHAR(36) NOT NULL PRIMARY KEY, server VARCHAR(64) NOT NULL, home VARCHAR(64) NOT NULL, "
                    + "world VARCHAR(128) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, "
                    + "yaw FLOAT NOT NULL, pitch FLOAT NOT NULL, created BIGINT NOT NULL)" + options);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + servers + " ("
                    + "name VARCHAR(64) NOT NULL PRIMARY KEY, address VARCHAR(255) NOT NULL, updated BIGINT NOT NULL)" + options);
        }
    }

    public void saveServer(String name, String address) throws SQLException {
        try (Connection c = source.getConnection();
             PreparedStatement ps = c.prepareStatement("REPLACE INTO " + servers + " (name, address, updated) VALUES (?, ?, ?)")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            ps.setString(2, address);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public @Nullable String serverAddress(String name) throws SQLException {
        try (Connection c = source.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT address FROM " + servers + " WHERE name = ?")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("address") : null;
            }
        }
    }

    public void close() {
        if (source != null) source.close();
    }

    public List<Home> loadHomes(UUID owner) throws SQLException {
        List<Home> list = new ArrayList<>();
        try (Connection c = source.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM " + homes + " WHERE owner = ?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Home(owner, rs.getInt("slot"), rs.getString("name"), rs.getString("server"),
                            rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("yaw"), rs.getFloat("pitch"), rs.getString("icon"), rs.getLong("created")));
                }
            }
        }
        return list;
    }

    public void saveHome(Home home) throws SQLException {
        try (Connection c = source.getConnection();
             PreparedStatement ps = c.prepareStatement("REPLACE INTO " + homes
                     + " (owner, slot, name, server, world, x, y, z, yaw, pitch, icon, created) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, home.owner().toString());
            ps.setInt(2, home.slot());
            ps.setString(3, home.name());
            ps.setString(4, home.server());
            ps.setString(5, home.world());
            ps.setDouble(6, home.x());
            ps.setDouble(7, home.y());
            ps.setDouble(8, home.z());
            ps.setFloat(9, home.yaw());
            ps.setFloat(10, home.pitch());
            ps.setString(11, home.icon());
            ps.setLong(12, home.created());
            ps.executeUpdate();
        }
    }

    public void deleteHome(UUID owner, int slot) throws SQLException {
        try (Connection c = source.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + homes + " WHERE owner = ? AND slot = ?")) {
            ps.setString(1, owner.toString());
            ps.setInt(2, slot);
            ps.executeUpdate();
        }
    }

    public void savePlayer(UUID uuid, String name) throws SQLException {
        try (Connection c = source.getConnection();
             PreparedStatement ps = c.prepareStatement("REPLACE INTO " + players + " (uuid, name, name_lower) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, name.toLowerCase(Locale.ROOT));
            ps.executeUpdate();
        }
    }

    public @Nullable Map.Entry<UUID, String> findPlayer(String name) throws SQLException {
        try (Connection c = source.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT uuid, name FROM " + players + " WHERE name_lower = ?")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return Map.entry(UUID.fromString(rs.getString("uuid")), rs.getString("name"));
            }
        }
    }

    public void savePending(Pending p) throws SQLException {
        try (Connection c = source.getConnection();
             PreparedStatement ps = c.prepareStatement("REPLACE INTO " + pending
                     + " (player, server, home, world, x, y, z, yaw, pitch, created) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, p.player().toString());
            ps.setString(2, p.server());
            ps.setString(3, p.home());
            ps.setString(4, p.world());
            ps.setDouble(5, p.x());
            ps.setDouble(6, p.y());
            ps.setDouble(7, p.z());
            ps.setFloat(8, p.yaw());
            ps.setFloat(9, p.pitch());
            ps.setLong(10, p.created());
            ps.executeUpdate();
        }
    }

    public @Nullable Pending loadPending(UUID player) throws SQLException {
        try (Connection c = source.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM " + pending + " WHERE player = ?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Pending(player, rs.getString("server"), rs.getString("home"), rs.getString("world"),
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"), rs.getLong("created"));
            }
        }
    }

    public boolean deletePending(UUID player, long created) throws SQLException {
        try (Connection c = source.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + pending + " WHERE player = ? AND created = ?")) {
            ps.setString(1, player.toString());
            ps.setLong(2, created);
            return ps.executeUpdate() > 0;
        }
    }
}
