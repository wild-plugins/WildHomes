package de.justgino.plugins.wildhomes.dialog;

import de.justgino.plugins.wildhomes.WildHomes;
import de.justgino.plugins.wildhomes.config.Lang;
import de.justgino.plugins.wildhomes.config.Settings;
import de.justgino.plugins.wildhomes.home.Home;
import de.justgino.plugins.wildhomes.home.PlayerHomes;
import de.justgino.plugins.wildhomes.util.Versions;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("UnstableApiUsage")
public final class Dialogs implements Listener {

    private static final ClickCallback.Options CLICK = ClickCallback.Options.builder()
            .lifetime(Duration.ofHours(1)).uses(ClickCallback.UNLIMITED_USES).build();
    private static final Key ICON_PICK = Key.key("wildhomes", "icon");
    private static final Pattern ITEM = Pattern.compile("item:\"([a-z0-9_./-]+)\"");
    private static final Map<String, String> ADMIN = Map.of(
            "wildhomes.use", "wildhomes.admin.teleport",
            "wildhomes.sethome", "wildhomes.admin.set",
            "wildhomes.rename", "wildhomes.admin.rename",
            "wildhomes.icon", "wildhomes.admin.icon",
            "wildhomes.delhome", "wildhomes.admin.delete");

    private record IconSession(PlayerHomes homes, int slot) {
    }

    private final WildHomes plugin;
    private final Map<UUID, IconSession> iconSessions = new ConcurrentHashMap<>();

    public Dialogs(WildHomes plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        if (!ICON_PICK.equals(event.getIdentifier())) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        Player player = connection.getPlayer();
        Matcher matcher = ITEM.matcher(event.getTag() == null ? "" : event.getTag().string());
        if (!matcher.find()) return;
        String key = matcher.group(1);
        plugin.tasks().entity(player, () -> {
            IconSession session = iconSessions.get(player.getUniqueId());
            Material material = Material.matchMaterial(key);
            if (session == null || material == null || !plugin.icons().choices(player).contains(material)) return;
            PlayerHomes homes = plugin.homes().current(session.homes());
            if (!may(player, homes, "wildhomes.icon")) return;
            changeIcon(player, homes, session.slot(), material);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        iconSessions.remove(event.getPlayer().getUniqueId());
    }

    private Settings settings() {
        return plugin.settings();
    }

    private Lang lang() {
        return plugin.lang();
    }

    private boolean own(Player viewer, PlayerHomes homes) {
        return viewer.getUniqueId().equals(homes.owner());
    }

    private boolean may(Player viewer, PlayerHomes homes, String permission) {
        return own(viewer, homes) ? viewer.hasPermission(permission) : viewer.hasPermission(ADMIN.getOrDefault(permission, "wildhomes.admin"));
    }

    public void openMain(Player viewer, PlayerHomes given, boolean expanded) {
        PlayerHomes homes = plugin.homes().current(given);
        int limit = plugin.homes().limitFor(viewer, homes);
        boolean admin = !own(viewer, homes);
        List<Integer> visible = new ArrayList<>();
        for (int slot = 1; slot <= settings().maxSlots; slot++) {
            if (homes.bySlot(slot) != null || slot <= limit || settings().showLocked) visible.add(slot);
        }
        boolean more = visible.size() > settings().previewSlots;
        List<Integer> shown = expanded || !more ? visible : visible.subList(0, settings().previewSlots);

        List<ActionButton> buttons = new ArrayList<>();
        for (int slot : shown) {
            Home home = homes.bySlot(slot);
            if (home != null) {
                buttons.add(homeButton(viewer, homes, home, expanded));
            } else if (slot <= limit) {
                int target = slot;
                buttons.add(button(lang().get("dialogs.main.empty"), lang().lines("dialogs.main.empty-tooltip"), settings().mainWidth, p -> {
                    if (!may(p, homes, "wildhomes.sethome")) {
                        lang().send(p, "messages.no-permission");
                        return;
                    }
                    plugin.homes().setHome(p, homes, null, target);
                    openMain(p, homes, expanded);
                }));
            } else {
                buttons.add(button(lang().get("dialogs.main.locked"), lang().lines("dialogs.main.locked-tooltip"), settings().mainWidth,
                        p -> lang().send(p, "messages.slot-locked")));
            }
        }
        ActionButton exit = null;
        if (more && !expanded) {
            buttons.add(button(lang().get("dialogs.main.more"), null, settings().mainWidth, p -> openMain(p, homes, true)));
        } else if (more) {
            exit = button(lang().get("dialogs.main.less"), null, settings().mainWidth, p -> openMain(p, homes, false));
        }
        Component title = admin ? lang().get("dialogs.main.title-other", "player", homes.name()) : lang().get("dialogs.main.title");
        show(viewer, title, List.of(item(settings().mainIcon)), List.of(), buttons, exit, settings().mainColumns);
    }

    private ActionButton homeButton(Player viewer, PlayerHomes homes, Home home, boolean expanded) {
        Object[] ph = placeholders(home, viewer);
        List<String> tooltip = new ArrayList<>(lang().rawLines("dialogs.main.home-tooltip"));
        if (plugin.network().enabled()) {
            String server = lang().raw("dialogs.main.home-tooltip-server");
            if (!server.isEmpty()) tooltip.add(Math.min(3, tooltip.size()), server);
        }
        return button(lang().get("dialogs.main.home", ph), lang().join(tooltip, ph), settings().mainWidth, p -> {
            if (settings().clickAction == Settings.ClickAction.TELEPORT && own(p, homes)) {
                teleport(p, homes, home.slot());
            } else {
                openHome(p, homes, home.slot());
            }
        });
    }

    private Object[] placeholders(Home home, Player viewer) {
        return new Object[]{
                "icon", plugin.icons().sprite(plugin.homes().icon(home), viewer),
                "home", home.name(),
                "world", home.world(),
                "server", home.server(),
                "x", (long) Math.floor(home.x()),
                "y", (long) Math.floor(home.y()),
                "z", (long) Math.floor(home.z())
        };
    }

    private Material shownIcon(Home home, Player viewer) {
        Material icon = plugin.homes().icon(home);
        return plugin.icons().supported(icon, Versions.client(viewer)) ? icon : settings().defaultIcon;
    }

    private void teleport(Player player, PlayerHomes homes, int slot) {
        Home home = homes.bySlot(slot);
        if (home == null) return;
        if (!may(player, homes, "wildhomes.use")) {
            lang().send(player, "messages.no-permission");
            return;
        }
        if (!plugin.homes().usable(player, homes, home)) {
            lang().send(player, "messages.over-limit");
            return;
        }
        plugin.teleports().teleport(player, home);
    }

    public void openHome(Player viewer, PlayerHomes given, int slot) {
        PlayerHomes homes = plugin.homes().current(given);
        Home home = homes.bySlot(slot);
        if (home == null) {
            openMain(viewer, homes, false);
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        int width = settings().homeWidth;
        if (may(viewer, homes, "wildhomes.use")) {
            buttons.add(button(lang().get("dialogs.home.teleport", "home", home.name()), null, width, p -> teleport(p, homes, slot)));
        }
        if (may(viewer, homes, "wildhomes.icon")) {
            buttons.add(button(lang().get("dialogs.home.icon", "home", home.name()), null, width, p -> openIcons(p, homes, slot, "")));
        }
        if (may(viewer, homes, "wildhomes.rename")) {
            buttons.add(button(lang().get("dialogs.home.rename", "home", home.name()), null, width, p -> openRename(p, homes, slot, null)));
        }
        if (may(viewer, homes, "wildhomes.delhome")) {
            buttons.add(button(lang().get("dialogs.home.delete", "home", home.name()), null, width, p -> {
                if (settings().confirmDelete) {
                    openDelete(p, homes, slot);
                } else {
                    delete(p, homes, slot);
                }
            }));
        }
        ActionButton back = button(lang().get("dialogs.home.back"), null, width, p -> openMain(p, homes, false));
        show(viewer, lang().get("dialogs.home.title", "home", home.name()), List.of(item(shownIcon(home, viewer))),
                List.of(), buttons, back, settings().homeColumns);
    }

    public void openRename(Player viewer, PlayerHomes given, int slot, @Nullable Component error) {
        PlayerHomes homes = plugin.homes().current(given);
        Home home = homes.bySlot(slot);
        if (home == null) {
            openMain(viewer, homes, false);
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        if (error != null) body.add(DialogBody.plainMessage(error, settings().renameInputWidth));
        DialogInput input = DialogInput.text("name", lang().get("dialogs.rename.label"))
                .width(settings().renameInputWidth)
                .maxLength(settings().nameMaxLength)
                .initial(home.name().length() > settings().nameMaxLength ? home.name().substring(0, settings().nameMaxLength) : home.name())
                .build();
        ActionButton save = submit(lang().get("dialogs.rename.save"), settings().renameWidth, (p, view) -> {
            Home current = homes.bySlot(slot);
            if (current == null) return;
            String name = view.getText("name") == null ? "" : view.getText("name").trim();
            String problem = plugin.homes().nameError(homes, name, slot);
            if (problem != null) {
                openRename(p, homes, slot, lang().get(problem, "home", name, "max", settings().nameMaxLength));
                return;
            }
            Home renamed = plugin.homes().rename(homes, current, name);
            lang().send(p, "messages.home-renamed", "home", renamed.name());
            openHome(p, homes, slot);
        });
        ActionButton cancel = button(lang().get("dialogs.rename.cancel"), null, settings().renameWidth, p -> openHome(p, homes, slot));
        show(viewer, lang().get("dialogs.rename.title", "home", home.name()), body, List.of(input), List.of(save), cancel, 1);
    }

    public void openIcons(Player viewer, PlayerHomes given, int slot, String query) {
        PlayerHomes homes = plugin.homes().current(given);
        Home home = homes.bySlot(slot);
        if (home == null) {
            openMain(viewer, homes, false);
            return;
        }
        int width = settings().iconWidth;
        List<DialogBody> body = new ArrayList<>();
        body.add(item(shownIcon(home, viewer)));
        List<Material> found = plugin.icons().search(viewer, query);
        if (found.isEmpty()) body.add(DialogBody.plainMessage(lang().get("dialogs.icons.no-results", "search", query)));
        DialogInput search = DialogInput.text("search", lang().get("dialogs.icons.search-label"))
                .width(Math.min(1024, width * 2)).maxLength(32).initial(query).build();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(submit(lang().get("dialogs.icons.search"), width, (p, view) ->
                openIcons(p, homes, slot, view.getText("search") == null ? "" : view.getText("search"))));
        buttons.add(button(lang().get("dialogs.icons.default"), null, width, p -> changeIcon(p, homes, slot, null)));
        buttons.add(button(lang().get("dialogs.icons.back"), null, width, p -> openHome(p, homes, slot)));
        for (Material material : found) {
            buttons.add(action(lang().get("dialogs.icons.item", "icon", plugin.icons().sprite(material, viewer), "item", plugin.icons().name(material)),
                    null, width, DialogAction.customClick(ICON_PICK, BinaryTagHolder.binaryTagHolder("{item:\"" + material.getKey().getKey() + "\"}"))));
        }
        iconSessions.put(viewer.getUniqueId(), new IconSession(homes, slot));
        show(viewer, lang().get("dialogs.icons.title", "home", home.name()), body, List.of(search), buttons, null, settings().iconColumns);
    }

    private void changeIcon(Player player, PlayerHomes homes, int slot, @Nullable Material material) {
        Home home = homes.bySlot(slot);
        if (home == null) return;
        Home changed = plugin.homes().setIcon(homes, home, material);
        lang().send(player, "messages.icon-changed", "home", changed.name());
        openHome(player, homes, slot);
    }

    public void openDelete(Player viewer, PlayerHomes given, int slot) {
        PlayerHomes homes = plugin.homes().current(given);
        Home home = homes.bySlot(slot);
        if (home == null) {
            openMain(viewer, homes, false);
            return;
        }
        int width = settings().deleteWidth;
        List<ActionButton> buttons = List.of(
                button(lang().get("dialogs.delete.cancel", "home", home.name()), null, width, p -> openHome(p, homes, slot)),
                button(lang().get("dialogs.delete.confirm", "home", home.name()), null, width, p -> delete(p, homes, slot)));
        show(viewer, lang().get("dialogs.delete.title", "home", home.name()), List.of(), List.of(), buttons, null, 2);
    }

    private void delete(Player player, PlayerHomes homes, int slot) {
        Home home = homes.bySlot(slot);
        if (home != null) plugin.homes().delete(player, homes, home);
        openMain(player, homes, false);
    }

    private DialogBody item(Material material) {
        return DialogBody.item(ItemStack.of(material)).showTooltip(false).showDecorations(false).build();
    }

    private ActionButton button(Component label, @Nullable Component tooltip, int width, Consumer<Player> onClick) {
        return action(label, tooltip, width, DialogAction.customClick((view, audience) -> {
            if (audience instanceof Player player) plugin.tasks().entity(player, () -> onClick.accept(player));
        }, CLICK));
    }

    private ActionButton submit(Component label, int width, BiConsumer<Player, DialogResponseView> onClick) {
        return action(label, null, width, DialogAction.customClick((view, audience) -> {
            if (audience instanceof Player player) plugin.tasks().entity(player, () -> onClick.accept(player, view));
        }, CLICK));
    }

    private static ActionButton action(Component label, @Nullable Component tooltip, int width, DialogAction action) {
        ActionButton.Builder builder = ActionButton.builder(label).width(width).action(action);
        if (tooltip != null && !tooltip.equals(Component.empty())) builder.tooltip(tooltip);
        return builder.build();
    }

    private void show(Player player, Component title, List<DialogBody> body, List<DialogInput> inputs,
                      List<ActionButton> buttons, @Nullable ActionButton exit, int columns) {
        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .inputs(inputs)
                .build();
        DialogType type;
        if (buttons.isEmpty()) {
            type = exit == null ? DialogType.notice() : DialogType.notice(exit);
        } else {
            var multi = DialogType.multiAction(buttons).columns(Math.max(1, columns));
            if (exit != null) multi.exitAction(exit);
            type = multi.build();
        }
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(type)));
    }
}
