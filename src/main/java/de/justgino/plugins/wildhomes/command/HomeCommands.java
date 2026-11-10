package de.justgino.plugins.wildhomes.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.justgino.plugins.wildhomes.WildHomes;
import de.justgino.plugins.wildhomes.home.Home;
import de.justgino.plugins.wildhomes.home.PlayerHomes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class HomeCommands {

    private final WildHomes plugin;

    private HomeCommands(WildHomes plugin) {
        this.plugin = plugin;
    }

    public static void register(WildHomes plugin) {
        HomeCommands commands = new HomeCommands(plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(commands.home(), "Open your homes or teleport to one", plugin.settings().homeAliases);
            registrar.register(commands.sethome(), "Set a home at your position", plugin.settings().sethomeAliases);
            registrar.register(commands.delhome(), "Delete a home", plugin.settings().delhomeAliases);
            registrar.register(commands.admin(), "WildHomes admin commands", plugin.settings().adminAliases);
        });
    }

    private LiteralCommandNode<CommandSourceStack> home() {
        return Commands.literal("home")
                .requires(source -> source.getSender().hasPermission("wildhomes.use"))
                .executes(ctx -> {
                    Player player = player(ctx);
                    PlayerHomes homes = homes(player);
                    if (homes != null) plugin.dialogs().openMain(player, homes, false);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests(this::ownHomes)
                        .executes(ctx -> {
                            Player player = player(ctx);
                            PlayerHomes homes = homes(player);
                            if (homes == null) return Command.SINGLE_SUCCESS;
                            String name = StringArgumentType.getString(ctx, "name");
                            Home home = homes.byName(name);
                            if (home == null) {
                                plugin.lang().send(player, "messages.home-not-found", "home", name);
                            } else if (!plugin.homes().usable(player, homes, home)) {
                                plugin.lang().send(player, "messages.over-limit");
                            } else {
                                plugin.teleports().teleport(player, home);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> sethome() {
        return Commands.literal("sethome")
                .requires(source -> source.getSender().hasPermission("wildhomes.sethome"))
                .executes(ctx -> {
                    Player player = player(ctx);
                    PlayerHomes homes = homes(player);
                    if (homes != null) plugin.homes().setHome(player, homes, null, 0);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests(this::ownHomes)
                        .executes(ctx -> {
                            Player player = player(ctx);
                            PlayerHomes homes = homes(player);
                            if (homes != null) plugin.homes().setHome(player, homes, StringArgumentType.getString(ctx, "name"), 0);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> delhome() {
        return Commands.literal("delhome")
                .requires(source -> source.getSender().hasPermission("wildhomes.delhome"))
                .executes(ctx -> {
                    plugin.lang().send(ctx.getSource().getSender(), "messages.usage-delhome");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests(this::ownHomes)
                        .executes(ctx -> {
                            Player player = player(ctx);
                            PlayerHomes homes = homes(player);
                            if (homes == null) return Command.SINGLE_SUCCESS;
                            String name = StringArgumentType.getString(ctx, "name");
                            Home home = homes.byName(name);
                            if (home == null) {
                                plugin.lang().send(player, "messages.home-not-found", "home", name);
                            } else {
                                plugin.homes().delete(player, homes, home);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> admin() {
        return Commands.literal("wildhomes")
                .requires(source -> source.getSender().hasPermission("wildhomes.admin"))
                .executes(ctx -> {
                    plugin.lang().sendLines(ctx.getSource().getSender(), "admin.help", "version", plugin.getPluginMeta().getVersion());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload").executes(ctx -> {
                    plugin.reload();
                    plugin.lang().send(ctx.getSource().getSender(), "messages.reloaded");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("open")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String typed = builder.getRemainingLowerCase();
                                    for (Player online : Bukkit.getOnlinePlayers()) {
                                        if (online.getName().toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(online.getName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Player viewer = player(ctx);
                                    if (viewer != null) open(viewer, StringArgumentType.getString(ctx, "player"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    private void open(Player viewer, String name) {
        Player online = Bukkit.getPlayerExact(name);
        CompletableFuture<Map.Entry<java.util.UUID, String>> lookup = online != null
                ? CompletableFuture.completedFuture(Map.entry(online.getUniqueId(), online.getName()))
                : plugin.tasks().supply(() -> plugin.database().findPlayer(name));
        lookup.thenCompose(found -> found == null ? CompletableFuture.completedFuture(null)
                        : plugin.homes().loadDetached(found.getKey(), found.getValue()))
                .whenComplete((homes, error) -> plugin.tasks().entity(viewer, () -> {
                    if (error != null) plugin.lang().send(viewer, "messages.error");
                    else if (homes == null) plugin.lang().send(viewer, "messages.player-not-found", "player", name);
                    else plugin.dialogs().openMain(viewer, homes, false);
                }));
    }

    private @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) return player;
        plugin.lang().send(sender, "messages.players-only");
        return null;
    }

    private @Nullable PlayerHomes homes(@Nullable Player player) {
        if (player == null) return null;
        PlayerHomes homes = plugin.homes().get(player.getUniqueId());
        if (homes == null) plugin.lang().send(player, "messages.loading");
        return homes;
    }

    private CompletableFuture<Suggestions> ownHomes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getSender() instanceof Player player) {
            PlayerHomes homes = plugin.homes().get(player.getUniqueId());
            if (homes != null) {
                String typed = builder.getRemainingLowerCase();
                for (Home home : homes.all()) {
                    if (home.name().toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(home.name());
                }
            }
        }
        return builder.buildFuture();
    }
}
