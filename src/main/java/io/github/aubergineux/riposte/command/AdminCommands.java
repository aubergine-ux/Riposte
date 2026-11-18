package io.github.aubergineux.riposte.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.arena.Arena;
import io.github.aubergineux.riposte.arena.ArenaGenerator;
import io.github.aubergineux.riposte.arena.ArenaManager;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.match.InventorySnapshot;
import io.github.aubergineux.riposte.menu.menus.InventoryPreviewMenu;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.ranked.RankedManager;
import io.github.aubergineux.riposte.util.BlockPos;
import io.github.aubergineux.riposte.util.Items;
import io.github.aubergineux.riposte.util.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/** /riposte, /arena and /kit. */
final class AdminCommands {

    private static final String ADMIN = "riposte.admin";

    private final RipostePlugin plugin;

    AdminCommands(RipostePlugin plugin) {
        this.plugin = plugin;
    }

    void register(Commands commands) {
        commands.register(riposte(), "Riposte main command", List.of("rp", "practice"));
        commands.register(arena(), "Arena setup");
        commands.register(kit(), "Kit setup");
    }

    private static boolean admin(CommandSourceStack s) {
        return s.getSender().hasPermission(ADMIN);
    }

    private static Player player(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player p) {
            return p;
        }
        return ctx.getSource().getSender() instanceof Player p ? p : null;
    }

    private Command<CommandSourceStack> playerOnly(Consumer<Player> action) {
        return ctx -> {
            Player p = player(ctx);
            if (p == null) {
                plugin.messages().send(ctx.getSource().getSender(), "errors.players-only");
            } else {
                action.accept(p);
            }
            return Command.SINGLE_SUCCESS;
        };
    }

    private void send(CommandSender to, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... r) {
        plugin.messages().send(to, key, r);
    }

    // ------------------------------------------------------------------ /riposte

    private LiteralCommandNode<CommandSourceStack> riposte() {
        return Commands.literal("riposte")
                .executes(ctx -> {
                    send(ctx.getSource().getSender(), ctx.getSource().getSender().hasPermission(ADMIN) ? "help.admin" : "help.player",
                            Text.p("version", plugin.getPluginMeta().getVersion()));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("help").executes(ctx -> {
                    send(ctx.getSource().getSender(), ctx.getSource().getSender().hasPermission(ADMIN) ? "help.admin" : "help.player",
                            Text.p("version", plugin.getPluginMeta().getVersion()));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("inventory").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    Player p = player(ctx);
                    if (p == null) {
                        return Command.SINGLE_SUCCESS;
                    }
                    InventorySnapshot snapshot;
                    try {
                        snapshot = plugin.matches().snapshot(UUID.fromString(StringArgumentType.getString(ctx, "id")));
                    } catch (IllegalArgumentException e) {
                        snapshot = null;
                    }
                    if (snapshot == null) {
                        send(p, "summary.expired");
                    } else {
                        new InventoryPreviewMenu(plugin, p, snapshot).open();
                    }
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("reload").requires(AdminCommands::admin).executes(ctx -> {
                    plugin.reload();
                    send(ctx.getSource().getSender(), "admin.reloaded");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("setup").requires(AdminCommands::admin).executes(playerOnly(this::setup)))
                .then(Commands.literal("setlobby").requires(AdminCommands::admin).executes(playerOnly(p -> {
                    plugin.lobby().setLobby(p.getLocation());
                    send(p, "admin.lobby-set");
                })))
                .then(Commands.literal("lobby").executes(playerOnly(p -> {
                    if (PlayerCommands.inLobby(plugin, p) || p.hasPermission(ADMIN)) {
                        plugin.lobby().toLobby(p);
                    }
                })))
                .then(Commands.literal("build").requires(AdminCommands::admin).executes(playerOnly(p -> {
                    PlayerProfile profile = plugin.profiles().get(p);
                    if (profile == null) {
                        return;
                    }
                    profile.buildMode(!profile.buildMode());
                    if (profile.buildMode()) {
                        p.getInventory().clear();
                        p.setGameMode(org.bukkit.GameMode.CREATIVE);
                    } else {
                        plugin.lobby().toLobby(p);
                    }
                    send(p, profile.buildMode() ? "admin.build-on" : "admin.build-off");
                })))
                .then(Commands.literal("status").requires(AdminCommands::admin).executes(ctx -> {
                    send(ctx.getSource().getSender(), "admin.status", Text.p("matches", plugin.matches().count()),
                            Text.p("fighting", plugin.matches().playersInMatches()), Text.p("queued", plugin.queues().totalQueued()),
                            Text.p("arenas", plugin.arenas().all().size()), Text.p("copies", plugin.arenas().totalCopies()),
                            Text.p("jobs", plugin.arenas().pendingJobs()), Text.p("kits", plugin.kits().all().size()),
                            Text.p("storage", plugin.dataStore().database().dialect().name()),
                            Text.p("season", plugin.ranked().season().name()),
                            Text.p("mspt", String.format(Locale.ROOT, "%.2f", plugin.getServer().getAverageTickTime())));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("season").requires(AdminCommands::admin)
                        .executes(ctx -> {
                            send(ctx.getSource().getSender(), "admin.season-info", Text.p("season", plugin.ranked().season().name()),
                                    Text.p("id", plugin.ranked().season().id()));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("new").then(Args.choice("reset", "soft", "hard")
                                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    boolean soft = StringArgumentType.getString(ctx, "reset").equalsIgnoreCase("soft");
                                    String name = StringArgumentType.getString(ctx, "name");
                                    plugin.ranked().startSeason(name, soft, () -> send(sender, "admin.season-started", Text.p("season", name)));
                                    return Command.SINGLE_SUCCESS;
                                })))))
                .then(Commands.literal("hologram").requires(AdminCommands::admin)
                        .then(Commands.literal("create").then(Commands.argument("id", StringArgumentType.word())
                                .then(Args.word("board", this::boards).executes(ctx -> {
                                    Player p = player(ctx);
                                    if (p == null) {
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String id = StringArgumentType.getString(ctx, "id");
                                    String board = StringArgumentType.getString(ctx, "board").toLowerCase(Locale.ROOT);
                                    if (!boards().contains(board)) {
                                        send(p, "errors.unknown-kit", Text.p("kit", board));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Location at = p.getLocation().add(0, 2.2, 0);
                                    send(p, plugin.holograms().create(id, at, board) ? "admin.hologram-created" : "admin.hologram-exists",
                                            Text.p("id", id));
                                    return Command.SINGLE_SUCCESS;
                                }))))
                        .then(Commands.literal("delete").then(Args.word("id", () -> plugin.holograms().all().keySet()).executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            send(ctx.getSource().getSender(), plugin.holograms().delete(id) ? "admin.hologram-deleted" : "admin.hologram-unknown",
                                    Text.p("id", id));
                            return Command.SINGLE_SUCCESS;
                        })))
                        .then(Commands.literal("list").executes(ctx -> {
                            send(ctx.getSource().getSender(), "admin.hologram-list", Text.p("list",
                                    plugin.holograms().all().isEmpty() ? "-" : String.join(", ", plugin.holograms().all().keySet())));
                            return Command.SINGLE_SUCCESS;
                        })))
                .build();
    }

    private List<String> boards() {
        List<String> out = new ArrayList<>();
        out.add(RankedManager.GLOBAL);
        plugin.kits().ranked().forEach(k -> out.add(k.id()));
        return out;
    }

    /** One command that makes a fresh server playable: lobby, three generated arenas, copies pasted. */
    private void setup(Player player) {
        if (!plugin.lobby().hasLobby()) {
            plugin.lobby().setLobby(player.getLocation());
            send(player, "setup.lobby");
        }
        int created = 0;
        for (ArenaGenerator.Style style : ArenaGenerator.Style.values()) {
            String name = style.name().toLowerCase(Locale.ROOT);
            if (plugin.arenas().get(name) != null) {
                continue;
            }
            generate(player, name, style, plugin.getConfig().getInt("setup.copies." + name, style == ArenaGenerator.Style.CLASSIC ? 4 : 2));
            created++;
        }
        send(player, created == 0 ? "setup.nothing" : "setup.done", Text.p("count", created));
    }

    private void generate(CommandSender sender, String name, ArenaGenerator.Style style, int copies) {
        ArenaManager arenas = plugin.arenas();
        Arena arena = arenas.get(name);
        if (arena == null) {
            arena = arenas.create(name);
        }
        ArenaGenerator.generate(arena, style, arenas.templateWorld(), plugin.settings().arenaSpacing);
        arena.displayName(plugin.messages().raw("setup.names." + style.name().toLowerCase(Locale.ROOT)));
        arena.copyCount(copies);
        arena.enabled(true);
        arenas.save();
        String error = arenas.rebuild(arena);
        if (error != null) {
            send(sender, error);
            return;
        }
        send(sender, "arena.generated", Text.p("arena", name), Text.p("style", Text.capitalize(style.name())), Text.p("copies", copies));
    }

    // ------------------------------------------------------------------ /arena

    private Arena arenaArg(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "arena");
        Arena arena = plugin.arenas().get(name);
        if (arena == null) {
            send(ctx.getSource().getSender(), "arena.unknown", Text.p("arena", name));
        }
        return arena;
    }

    private interface ArenaAction {
        void run(CommandContext<CommandSourceStack> ctx, Arena arena);
    }

    private Command<CommandSourceStack> withArena(ArenaAction action) {
        return ctx -> {
            Arena arena = arenaArg(ctx);
            if (arena != null) {
                action.run(ctx, arena);
            }
            return Command.SINGLE_SUCCESS;
        };
    }

    private LiteralCommandNode<CommandSourceStack> arena() {
        ArenaManager arenas = plugin.arenas();
        return Commands.literal("arena").requires(AdminCommands::admin)
                .executes(ctx -> {
                    send(ctx.getSource().getSender(), "help.arena");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("wand").executes(playerOnly(p -> {
                    ItemStack wand = Items.of(Material.BLAZE_ROD)
                            .name(plugin.messages().formatItem(plugin.messages().raw("arena.wand-name")))
                            .lore(List.of(plugin.messages().formatItem(plugin.messages().raw("arena.wand-lore"))))
                            .tag(plugin.arenaListener().wandKey(), "wand")
                            .glow(true)
                            .build();
                    p.getInventory().addItem(wand);
                    send(p, "arena.wand-given");
                })))
                .then(Commands.literal("list").executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();
                    send(s, "arena.list-header", Text.p("count", arenas.all().size()));
                    for (Arena a : arenas.all()) {
                        send(s, "arena.list-line", Text.p("arena", a.name()), Text.mm("display", a.displayName()),
                                Text.p("tags", String.join(", ", a.tags())), Text.p("free", a.available()), Text.p("copies", a.copies().size()),
                                Text.mm("status", plugin.messages().raw(a.enabled() ? "arena.status-enabled" : "arena.status-disabled")));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    if (arenas.get(name) != null) {
                        send(ctx.getSource().getSender(), "arena.exists", Text.p("arena", name));
                        return Command.SINGLE_SUCCESS;
                    }
                    Arena arena = arenas.create(name);
                    Player p = player(ctx);
                    if (p != null) {
                        ArenaManager.Selection sel = arenas.selection(p.getUniqueId());
                        if (sel != null && sel.pos1() != null && sel.pos2() != null) {
                            arena.region(sel.world(), sel.pos1(), sel.pos2());
                            arenas.save();
                        }
                    }
                    send(ctx.getSource().getSender(), "arena.created", Text.p("arena", arena.name()));
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("generate").then(Commands.argument("name", StringArgumentType.word())
                        .then(Args.choice("style", "classic", "sumo", "bridge")
                                .executes(ctx -> generateCmd(ctx, 2))
                                .then(Commands.argument("copies", IntegerArgumentType.integer(1, 64)).executes(ctx ->
                                        generateCmd(ctx, IntegerArgumentType.getInteger(ctx, "copies")))))))
                .then(Commands.literal("delete").then(Args.arena(plugin, "arena").executes(withArena((ctx, arena) -> {
                    for (var copy : arena.copies()) {
                        if (copy.state() == io.github.aubergineux.riposte.arena.ArenaCopy.State.IN_USE) {
                            send(ctx.getSource().getSender(), "arena.in-use");
                            return;
                        }
                    }
                    arenas.delete(arena);
                    send(ctx.getSource().getSender(), "arena.deleted", Text.p("arena", arena.name()));
                }))))
                .then(Commands.literal("setregion").then(Args.arena(plugin, "arena").executes(withArena((ctx, arena) -> {
                    Player p = player(ctx);
                    ArenaManager.Selection sel = p == null ? null : arenas.selection(p.getUniqueId());
                    if (sel == null || sel.pos1() == null || sel.pos2() == null) {
                        send(ctx.getSource().getSender(), "arena.no-selection");
                        return;
                    }
                    arena.region(sel.world(), sel.pos1(), sel.pos2());
                    arenas.save();
                    send(p, "arena.region-set", Text.p("arena", arena.name()),
                            Text.p("size", arena.sizeX() + "x" + arena.sizeY() + "x" + arena.sizeZ()));
                }))))
                .then(Commands.literal("setspawn").then(Args.arena(plugin, "arena").then(Args.choice("team", "1", "2")
                        .executes(withArena((ctx, arena) -> {
                            Player p = player(ctx);
                            if (p == null) {
                                return;
                            }
                            boolean first = StringArgumentType.getString(ctx, "team").equals("1");
                            Location loc = p.getLocation();
                            if (arena.hasRegion() && !loc.getWorld().getName().equals(arena.templateWorld())) {
                                send(p, "arena.wrong-world");
                                return;
                            }
                            if (first) {
                                arena.spawn1(loc);
                            } else {
                                arena.spawn2(loc);
                            }
                            arenas.save();
                            send(p, arena.hasRegion() && !arena.contains(loc) ? "arena.spawn-outside" : "arena.spawn-set",
                                    Text.p("arena", arena.name()), Text.p("team", first ? 1 : 2));
                        })))))
                .then(Commands.literal("setgoal").then(Args.arena(plugin, "arena").then(Args.choice("team", "1", "2")
                        .executes(withArena((ctx, arena) -> {
                            Player p = player(ctx);
                            if (p == null) {
                                return;
                            }
                            boolean first = StringArgumentType.getString(ctx, "team").equals("1");
                            BlockPos pos = BlockPos.of(p.getLocation());
                            if (first) {
                                arena.goal1(pos);
                            } else {
                                arena.goal2(pos);
                            }
                            arenas.save();
                            send(p, "arena.goal-set", Text.p("arena", arena.name()), Text.p("team", first ? 1 : 2));
                        })))))
                .then(Commands.literal("tags").then(Args.arena(plugin, "arena")
                        .then(Commands.argument("tags", StringArgumentType.greedyString()).executes(withArena((ctx, arena) -> {
                            arena.tags().clear();
                            for (String tag : StringArgumentType.getString(ctx, "tags").split("[ ,]+")) {
                                if (!tag.isBlank()) {
                                    arena.tags().add(tag.toLowerCase(Locale.ROOT));
                                }
                            }
                            arenas.save();
                            send(ctx.getSource().getSender(), "arena.tags-set", Text.p("arena", arena.name()),
                                    Text.p("tags", String.join(", ", arena.tags())));
                        })))))
                .then(Commands.literal("displayname").then(Args.arena(plugin, "arena")
                        .then(Commands.argument("name", StringArgumentType.greedyString()).executes(withArena((ctx, arena) -> {
                            arena.displayName(StringArgumentType.getString(ctx, "name"));
                            arenas.save();
                            send(ctx.getSource().getSender(), "arena.updated", Text.p("arena", arena.name()));
                        })))))
                .then(Commands.literal("icon").then(Args.arena(plugin, "arena").executes(withArena((ctx, arena) -> {
                    Player p = player(ctx);
                    if (p == null || p.getInventory().getItemInMainHand().isEmpty()) {
                        send(ctx.getSource().getSender(), "errors.hold-item");
                        return;
                    }
                    arena.icon(p.getInventory().getItemInMainHand().getType());
                    arenas.save();
                    send(p, "arena.updated", Text.p("arena", arena.name()));
                }))))
                .then(Commands.literal("copies").then(Args.arena(plugin, "arena")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(withArena((ctx, arena) -> {
                            arena.copyCount(IntegerArgumentType.getInteger(ctx, "count"));
                            arenas.save();
                            String error = arenas.rebuild(arena);
                            send(ctx.getSource().getSender(), error == null ? "arena.pasting" : error, Text.p("arena", arena.name()),
                                    Text.p("copies", arena.copyCount()));
                        })))))
                .then(Commands.literal("paste").then(Args.arena(plugin, "arena").executes(withArena((ctx, arena) -> {
                    String error = arenas.rebuild(arena);
                    send(ctx.getSource().getSender(), error == null ? "arena.pasting" : error, Text.p("arena", arena.name()),
                            Text.p("copies", arena.copyCount()));
                }))))
                .then(Commands.literal("enable").then(Args.arena(plugin, "arena").executes(withArena((ctx, arena) -> {
                    if (!arena.isComplete()) {
                        send(ctx.getSource().getSender(), "arena.incomplete");
                        return;
                    }
                    arena.enabled(true);
                    arenas.save();
                    if (arena.copies().isEmpty()) {
                        arenas.rebuild(arena);
                    }
                    send(ctx.getSource().getSender(), "arena.enabled", Text.p("arena", arena.name()));
                }))))
                .then(Commands.literal("disable").then(Args.arena(plugin, "arena").executes(withArena((ctx, arena) -> {
                    arena.enabled(false);
                    arenas.save();
                    send(ctx.getSource().getSender(), "arena.disabled", Text.p("arena", arena.name()));
                }))))
                .then(Commands.literal("info").then(Args.arena(plugin, "arena").executes(withArena((ctx, arena) -> {
                    send(ctx.getSource().getSender(), "arena.info", Text.p("arena", arena.name()), Text.mm("display", arena.displayName()),
                            Text.p("world", arena.templateWorld() == null ? "-" : arena.templateWorld()),
                            Text.p("min", arena.min() == null ? "-" : arena.min().serialize()),
                            Text.p("max", arena.max() == null ? "-" : arena.max().serialize()),
                            Text.p("spawn1", arena.spawn1() == null ? "-" : "set"), Text.p("spawn2", arena.spawn2() == null ? "-" : "set"),
                            Text.p("goals", arena.goal1() != null && arena.goal2() != null ? "set" : "-"),
                            Text.p("tags", String.join(", ", arena.tags())), Text.p("copies", arena.copies().size()),
                            Text.p("free", arena.available()),
                            Text.mm("status", plugin.messages().raw(arena.enabled() ? "arena.status-enabled" : "arena.status-disabled")));
                }))))
                .then(Commands.literal("tp").then(Args.arena(plugin, "arena").executes(withArena((ctx, arena) -> {
                    Player p = player(ctx);
                    if (p != null && arena.spawn1() != null) {
                        PlayerProfile profile = plugin.profiles().get(p);
                        if (profile != null && !profile.buildMode()) {
                            profile.buildMode(true);
                            send(p, "admin.build-on");
                        }
                        p.teleport(arena.spawn1());
                    }
                }))))
                .build();
    }

    private int generateCmd(CommandContext<CommandSourceStack> ctx, int copies) {
        String name = StringArgumentType.getString(ctx, "name");
        ArenaGenerator.Style style = ArenaGenerator.Style.parse(StringArgumentType.getString(ctx, "style"));
        if (style == null) {
            send(ctx.getSource().getSender(), "help.arena");
            return Command.SINGLE_SUCCESS;
        }
        generate(ctx.getSource().getSender(), name, style, copies);
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------ /kit

    private Kit kitArg(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "kit");
        Kit kit = plugin.kits().get(id);
        if (kit == null) {
            send(ctx.getSource().getSender(), "errors.unknown-kit", Text.p("kit", id));
        }
        return kit;
    }

    private interface KitAction {
        void run(CommandContext<CommandSourceStack> ctx, Kit kit);
    }

    private Command<CommandSourceStack> withKit(KitAction action) {
        return ctx -> {
            Kit kit = kitArg(ctx);
            if (kit != null) {
                action.run(ctx, kit);
            }
            return Command.SINGLE_SUCCESS;
        };
    }

    private LiteralCommandNode<CommandSourceStack> kit() {
        var kits = plugin.kits();
        return Commands.literal("kit").requires(AdminCommands::admin)
                .executes(ctx -> {
                    send(ctx.getSource().getSender(), "help.kit");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("list").executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();
                    send(s, "kit.list-header", Text.p("count", kits.all().size()));
                    for (Kit kit : kits.sorted()) {
                        send(s, "kit.list-line", Text.p("id", kit.id()), Text.mm("kit", kit.displayName()),
                                Text.p("mode", Text.capitalize(kit.rules().winCondition.name())), Text.p("tag", kit.rules().arenaTag),
                                Text.mm("status", plugin.messages().raw(kit.enabled() ? "arena.status-enabled" : "arena.status-disabled")));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("create").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    if (kits.get(id) != null) {
                        send(ctx.getSource().getSender(), "kit.exists", Text.p("kit", id));
                        return Command.SINGLE_SUCCESS;
                    }
                    Kit kit = kits.create(id);
                    Player p = player(ctx);
                    if (p != null) {
                        kit.contents(p.getInventory().getContents());
                        kits.save();
                    }
                    send(ctx.getSource().getSender(), "kit.created", Text.p("kit", kit.id()));
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("delete").then(Args.kit(plugin, "kit").executes(withKit((ctx, kit) -> {
                    kits.delete(kit.id());
                    send(ctx.getSource().getSender(), "kit.deleted", Text.p("kit", kit.id()));
                }))))
                .then(Commands.literal("setinv").then(Args.kit(plugin, "kit").executes(withKit((ctx, kit) -> {
                    Player p = player(ctx);
                    if (p == null) {
                        return;
                    }
                    kit.contents(p.getInventory().getContents());
                    kits.save();
                    send(p, "kit.inventory-set", Text.mm("kit", kit.displayName()));
                }))))
                .then(Commands.literal("give").then(Args.kit(plugin, "kit").executes(withKit((ctx, kit) -> {
                    Player p = player(ctx);
                    if (p == null) {
                        return;
                    }
                    PlayerProfile profile = plugin.profiles().get(p);
                    if (profile != null && !profile.buildMode()) {
                        profile.buildMode(true);
                        send(p, "admin.build-on");
                    }
                    kit.apply(p, null);
                    send(p, "kit.given", Text.mm("kit", kit.displayName()));
                }))))
                .then(Commands.literal("seticon").then(Args.kit(plugin, "kit").executes(withKit((ctx, kit) -> {
                    Player p = player(ctx);
                    if (p == null || p.getInventory().getItemInMainHand().isEmpty()) {
                        send(ctx.getSource().getSender(), "errors.hold-item");
                        return;
                    }
                    kit.icon(p.getInventory().getItemInMainHand().getType());
                    kits.save();
                    send(p, "kit.updated", Text.mm("kit", kit.displayName()));
                }))))
                .then(Commands.literal("enable").then(Args.kit(plugin, "kit").executes(withKit((ctx, kit) -> {
                    kit.enabled(true);
                    kits.save();
                    send(ctx.getSource().getSender(), "kit.updated", Text.mm("kit", kit.displayName()));
                }))))
                .then(Commands.literal("disable").then(Args.kit(plugin, "kit").executes(withKit((ctx, kit) -> {
                    kit.enabled(false);
                    kits.save();
                    send(ctx.getSource().getSender(), "kit.updated", Text.mm("kit", kit.displayName()));
                }))))
                .then(Commands.literal("restore").then(Args.word("kit", () -> Arrays.asList("nodebuff", "sword", "axe", "crystal", "uhc",
                        "builduhc", "sumo", "boxing", "bridge", "mace")).executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "kit");
                    send(ctx.getSource().getSender(), kits.restoreDefault(id) ? "kit.restored" : "errors.unknown-kit", Text.p("kit", id));
                    return Command.SINGLE_SUCCESS;
                })))
                .build();
    }
}
