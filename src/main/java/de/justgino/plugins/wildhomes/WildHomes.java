package de.justgino.plugins.wildhomes;

import de.justgino.plugins.wildhomes.command.HomeCommands;
import de.justgino.plugins.wildhomes.config.Lang;
import de.justgino.plugins.wildhomes.config.Settings;
import de.justgino.plugins.wildhomes.dialog.Dialogs;
import de.justgino.plugins.wildhomes.dialog.Icons;
import de.justgino.plugins.wildhomes.home.HomeService;
import de.justgino.plugins.wildhomes.hook.PlaceholderHook;
import de.justgino.plugins.wildhomes.listener.PlayerListener;
import de.justgino.plugins.wildhomes.network.Network;
import de.justgino.plugins.wildhomes.storage.Database;
import de.justgino.plugins.wildhomes.teleport.TeleportService;
import de.justgino.plugins.wildhomes.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class WildHomes extends JavaPlugin {

    private Tasks tasks;
    private Settings settings;
    private Lang lang;
    private Icons icons;
    private Database database;
    private HomeService homes;
    private Network network;
    private TeleportService teleports;
    private Dialogs dialogs;
    private boolean placeholderApi;

    @Override
    public void onEnable() {
        tasks = new Tasks(this);
        saveDefaults();
        settings = Settings.load(this);
        lang = new Lang(this);
        lang.load(settings.language);
        icons = new Icons(this);
        database = new Database(this);
        try {
            database.connect();
        } catch (Exception e) {
            getLogger().severe("Could not connect to the database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        homes = new HomeService(this);
        network = new Network(this);
        teleports = new TeleportService(this);
        dialogs = new Dialogs(this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(network, this);
        getServer().getPluginManager().registerEvents(dialogs, this);
        network.start();
        HomeCommands.register(this);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderHook(this).register();
            placeholderApi = true;
        }
        for (Player player : Bukkit.getOnlinePlayers()) homes.loadAsync(player.getUniqueId(), player.getName());
    }

    @Override
    public void onDisable() {
        if (teleports != null) teleports.cancelAll();
        if (network != null) network.stop();
        if (tasks != null) tasks.shutdown();
        if (database != null) database.close();
    }

    public void reload() {
        settings = Settings.load(this);
        lang.load(settings.language);
        icons.reload();
    }

    private void saveDefaults() {
        for (String name : new String[]{"config.yml", "database.yml", "lang/english.yml", "lang/german.yml"}) {
            if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
        }
    }

    public Tasks tasks() {
        return tasks;
    }

    public Settings settings() {
        return settings;
    }

    public Lang lang() {
        return lang;
    }

    public Icons icons() {
        return icons;
    }

    public Database database() {
        return database;
    }

    public HomeService homes() {
        return homes;
    }

    public Network network() {
        return network;
    }

    public TeleportService teleports() {
        return teleports;
    }

    public Dialogs dialogs() {
        return dialogs;
    }

    public boolean placeholderApi() {
        return placeholderApi;
    }
}
