package de.justgino.plugins.wildhomes.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
            registrar.register(commands.admin(), "Manage the homes of other players", plugin.settings().adminAliases);
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

    private static final List<String> ADMIN_NODES = List.of(
            "wildhomes.admin.view", "wildhomes.admin.teleport", "wildhomes.admin.set", "wildhomes.admin.rename",
            "wildhomes.admin.icon", "wildhomes.admin.delete", "wildhomes.admin.reload");

    private static boolean anyAdmin(CommandSender sender) {
        for (String node : ADMIN_NODES) if (sender.hasPermission(node)) return true;
        return false;
    }

    private static Predicate<CommandSourceStack> can(String node) {
        return source -> source.getSender().hasPermission(node);
    }

    private LiteralCommandNode<CommandSourceStack> admin() {
        return Commands.literal("home-admin")
                .requires(source -> anyAdmin(source.getSender()))
                .executes(ctx -> {
                    plugin.lang().sendLines(ctx.getSource().getSender(), "admin.help", "version", plugin.getPluginMeta().getVersion());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload").requires(can("wildhomes.admin.reload")).executes(ctx -> {
                    plugin.reload();
                    plugin.lang().send(ctx.getSource().getSender(), "messages.reloaded");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("open").requires(can("wildhomes.admin.view"))
                        .then(playerArgument().executes(ctx -> {
                            Player viewer = player(ctx);
                            if (viewer != null) withHomes(viewer, StringArgumentType.getString(ctx, "player"),
                                    homes -> plugin.tasks().entity(viewer, () -> plugin.dialogs().openMain(viewer, homes, false)));
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.literal("tp").requires(can("wildhomes.admin.teleport"))
                        .then(playerArgument().then(homeArgument().executes(ctx -> {
                            Player viewer = player(ctx);
                            if (viewer == null) return Command.SINGLE_SUCCESS;
                            String name = StringArgumentType.getString(ctx, "home");
                            withHomes(viewer, StringArgumentType.getString(ctx, "player"), homes -> plugin.tasks().entity(viewer, () -> {
                                Home home = homes.byName(name);
                                if (home == null) plugin.lang().send(viewer, "messages.home-not-found", "home", name);
                                else plugin.teleports().teleport(viewer, home);
                            }));
                            return Command.SINGLE_SUCCESS;
                        }))))
                .then(Commands.literal("delete").requires(can("wildhomes.admin.delete"))
                        .then(playerArgument().then(homeArgument().executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String name = StringArgumentType.getString(ctx, "home");
                            withHomes(sender, StringArgumentType.getString(ctx, "player"), homes -> {
                                Runnable work = () -> {
                                    Home home = homes.byName(name);
                                    if (home == null) plugin.lang().send(sender, "messages.home-not-found", "home", name);
                                    else plugin.homes().delete(sender, homes, home);
                                };
                                if (sender instanceof Player p) plugin.tasks().entity(p, work);
                                else work.run();
                            });
                            return Command.SINGLE_SUCCESS;
                        }))))
                .build();
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return Commands.argument("player", StringArgumentType.word()).suggests((ctx, builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(online.getName());
            }
            return builder.buildFuture();
        });
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> homeArgument() {
        return Commands.argument("home", StringArgumentType.greedyString()).suggests((ctx, builder) -> {
            String target = StringArgumentType.getString(ctx, "player");
            String typed = builder.getRemainingLowerCase();
            return lookup(target).thenApply(homes -> {
                if (homes != null) {
                    for (Home home : homes.all()) {
                        if (home.name().toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(home.name());
                    }
                }
                return builder.build();
            }).exceptionally(error -> builder.build());
        });
    }

    private CompletableFuture<PlayerHomes> lookup(String name) {
        Player online = Bukkit.getPlayerExact(name);
        CompletableFuture<Map.Entry<UUID, String>> found = online != null
                ? CompletableFuture.completedFuture(Map.entry(online.getUniqueId(), online.getName()))
                : plugin.tasks().supply(() -> plugin.database().findPlayer(name));
        return found.thenCompose(entry -> entry == null ? CompletableFuture.completedFuture(null)
                : plugin.homes().loadDetached(entry.getKey(), entry.getValue()));
    }

    private void withHomes(CommandSender sender, String name, Consumer<PlayerHomes> action) {
        lookup(name).whenComplete((homes, error) -> {
            if (error != null) plugin.lang().send(sender, "messages.error");
            else if (homes == null) plugin.lang().send(sender, "messages.player-not-found", "player", name);
            else action.accept(homes);
        });
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
