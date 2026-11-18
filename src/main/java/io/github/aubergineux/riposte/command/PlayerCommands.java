package io.github.aubergineux.riposte.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.event.EventManager;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.lobby.HotbarAction;
import io.github.aubergineux.riposte.match.Match;
import io.github.aubergineux.riposte.menu.menus.ArenaSelectMenu;
import io.github.aubergineux.riposte.menu.menus.KitSelectMenu;
import io.github.aubergineux.riposte.menu.menus.LeaderboardMenu;
import io.github.aubergineux.riposte.menu.menus.PartyMenu;
import io.github.aubergineux.riposte.menu.menus.QueueMenu;
import io.github.aubergineux.riposte.menu.menus.SettingsMenu;
import io.github.aubergineux.riposte.menu.menus.SpectateMenu;
import io.github.aubergineux.riposte.menu.menus.StatsMenu;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.PlayerState;
import io.github.aubergineux.riposte.ranked.RankedManager;
import io.github.aubergineux.riposte.util.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Commands every player uses: queue, duel, spectate, party, event, stats, leaderboard, settings. */
final class PlayerCommands {

    private final RipostePlugin plugin;

    PlayerCommands(RipostePlugin plugin) {
        this.plugin = plugin;
    }

    void register(Commands commands) {
        commands.register(queue(), "Open the queue menu or join a queue", List.of("q"));
        commands.register(simpleQueue("unranked", false), "Join an unranked queue");
        commands.register(simpleQueue("ranked", true), "Join a ranked queue");
        commands.register(duel(), "Challenge a player to a duel");
        commands.register(Commands.literal("rematch").requires(this::isPlayer).executes(player(p -> plugin.duels().rematch(p))).build(),
                "Rematch your last opponent");
        commands.register(spectate(), "Watch a live match", List.of("spec"));
        commands.register(Commands.literal("leave").requires(this::isPlayer).executes(player(this::leave)).build(),
                "Leave the queue, spectating or an event");
        commands.register(party(), "Party commands", List.of("p"));
        commands.register(event(), "Hosted events");
        commands.register(stats(), "View player stats");
        commands.register(leaderboard(), "View leaderboards", List.of("lb", "top"));
        commands.register(Commands.literal("settings").requires(this::isPlayer).executes(player(p -> new SettingsMenu(plugin, p).open())).build(),
                "Your personal settings");
        commands.register(kitEditor(), "Rearrange your kit hotbar", List.of("editkit", "layout"));
    }

    // ------------------------------------------------------------------ helpers

    private boolean isPlayer(CommandSourceStack source) {
        return source.getExecutor() instanceof Player || source.getSender() instanceof Player;
    }

    private static Player playerOf(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player p) {
            return p;
        }
        return (Player) ctx.getSource().getSender();
    }

    private static Command<CommandSourceStack> player(Consumer<Player> action) {
        return ctx -> {
            action.accept(playerOf(ctx));
            return Command.SINGLE_SUCCESS;
        };
    }

    private Kit kitArg(CommandContext<CommandSourceStack> ctx, String name) {
        String id = StringArgumentType.getString(ctx, name);
        Kit kit = plugin.kits().get(id);
        if (kit == null || !kit.enabled()) {
            plugin.messages().send(ctx.getSource().getSender(), "errors.unknown-kit", Text.p("kit", id));
            return null;
        }
        return kit;
    }

    private Player onlineArg(CommandContext<CommandSourceStack> ctx, String name) {
        String target = StringArgumentType.getString(ctx, name);
        Player player = Bukkit.getPlayerExact(target);
        if (player == null) {
            plugin.messages().send(ctx.getSource().getSender(), "errors.player-offline", Text.p("player", target));
        }
        return player;
    }

    private List<String> enabledKits() {
        List<String> out = new ArrayList<>();
        for (Kit kit : plugin.kits().enabled()) {
            out.add(kit.id());
        }
        return out;
    }

    // ------------------------------------------------------------------ queue

    private LiteralCommandNode<CommandSourceStack> queue() {
        return Commands.literal("queue").requires(this::isPlayer)
                .executes(player(p -> new QueueMenu(plugin, p, false).open()))
                .then(Commands.literal("leave").executes(player(p -> plugin.queues().leave(p, true))))
                .then(Commands.literal("unranked")
                        .executes(player(p -> new QueueMenu(plugin, p, false).open()))
                        .then(Args.word("kit", this::enabledKits).executes(ctx -> joinQueue(ctx, false))))
                .then(Commands.literal("ranked")
                        .executes(player(p -> new QueueMenu(plugin, p, true).open()))
                        .then(Args.word("kit", this::enabledKits).executes(ctx -> joinQueue(ctx, true))))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> simpleQueue(String name, boolean ranked) {
        return Commands.literal(name).requires(this::isPlayer)
                .executes(player(p -> new QueueMenu(plugin, p, ranked).open()))
                .then(Args.word("kit", this::enabledKits).executes(ctx -> joinQueue(ctx, ranked)))
                .build();
    }

    private int joinQueue(CommandContext<CommandSourceStack> ctx, boolean ranked) {
        Kit kit = kitArg(ctx, "kit");
        if (kit != null) {
            plugin.queues().join(playerOf(ctx), kit, ranked);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void leave(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) {
            return;
        }
        switch (profile.state()) {
            case QUEUE -> plugin.queues().leave(player, true);
            case SPECTATING -> plugin.spectators().stopSpectating(player, true);
            case EVENT -> plugin.events().leave(player, true);
            default -> plugin.messages().send(player, "errors.nothing-to-leave");
        }
    }

    // ------------------------------------------------------------------ duel

    private LiteralCommandNode<CommandSourceStack> duel() {
        return Commands.literal("duel").requires(this::isPlayer)
                .then(Commands.literal("accept").then(Args.player("player").executes(ctx -> {
                    plugin.duels().accept(playerOf(ctx), StringArgumentType.getString(ctx, "player"));
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("deny").then(Args.player("player").executes(ctx -> {
                    plugin.duels().deny(playerOf(ctx), StringArgumentType.getString(ctx, "player"));
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Args.player("target")
                        .executes(ctx -> {
                            Player sender = playerOf(ctx);
                            Player target = onlineArg(ctx, "target");
                            if (target != null) {
                                openDuelMenus(sender, target);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Args.word("kit", this::enabledKits)
                                .executes(ctx -> {
                                    Player target = onlineArg(ctx, "target");
                                    Kit kit = kitArg(ctx, "kit");
                                    if (target != null && kit != null) {
                                        plugin.duels().send(playerOf(ctx), target, kit, null);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Args.arena(plugin, "arena").executes(ctx -> {
                                    Player target = onlineArg(ctx, "target");
                                    Kit kit = kitArg(ctx, "kit");
                                    if (target != null && kit != null) {
                                        plugin.duels().send(playerOf(ctx), target, kit, StringArgumentType.getString(ctx, "arena"));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }))))
                .build();
    }

    private void openDuelMenus(Player sender, Player target) {
        new KitSelectMenu(plugin, sender, KitSelectMenu.Purpose.DUEL, kit -> {
            if (plugin.getConfig().getBoolean("duels.choose-arena", true) && plugin.arenas().arenasFor(kit).size() > 1) {
                new ArenaSelectMenu(plugin, sender, kit, arena -> plugin.duels().send(sender, target, kit, arena)).open();
            } else {
                plugin.duels().send(sender, target, kit, null);
            }
        }).open();
    }

    // ------------------------------------------------------------------ spectate

    private LiteralCommandNode<CommandSourceStack> spectate() {
        return Commands.literal("spectate").requires(s -> isPlayer(s) && s.getSender().hasPermission("riposte.spectate"))
                .executes(player(p -> new SpectateMenu(plugin, p).open()))
                .then(Args.player("player").executes(ctx -> {
                    Player viewer = playerOf(ctx);
                    Player target = onlineArg(ctx, "player");
                    if (target == null) {
                        return Command.SINGLE_SUCCESS;
                    }
                    Match match = plugin.matches().matchOf(target);
                    if (match == null) {
                        plugin.messages().send(viewer, "spectate.not-in-match", Text.p("player", target.getName()));
                        return Command.SINGLE_SUCCESS;
                    }
                    String error = plugin.spectators().spectate(viewer, match);
                    if (error != null) {
                        plugin.messages().send(viewer, error);
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    // ------------------------------------------------------------------ party

    private LiteralCommandNode<CommandSourceStack> party() {
        var parties = plugin.parties();
        return Commands.literal("party").requires(s -> isPlayer(s) && s.getSender().hasPermission("riposte.party"))
                .executes(player(p -> {
                    if (parties.partyOf(p) == null) {
                        plugin.messages().send(p, "party.help");
                    } else {
                        new PartyMenu(plugin, p).open();
                    }
                }))
                .then(Commands.literal("create").executes(player(parties::create)))
                .then(Commands.literal("invite").then(Args.player("player").executes(ctx -> {
                    Player target = onlineArg(ctx, "player");
                    if (target != null) {
                        parties.invite(playerOf(ctx), target);
                    }
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("accept").then(Args.player("leader").executes(ctx -> {
                    parties.accept(playerOf(ctx), StringArgumentType.getString(ctx, "leader"));
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("leave").executes(player(parties::leave)))
                .then(Commands.literal("disband").executes(player(parties::disband)))
                .then(Commands.literal("info").executes(player(parties::info)))
                .then(Commands.literal("menu").executes(player(p -> new PartyMenu(plugin, p).open())))
                .then(Commands.literal("kick").then(Args.player("player").executes(ctx -> {
                    parties.kick(playerOf(ctx), StringArgumentType.getString(ctx, "player"));
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("chat").then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                    parties.chat(playerOf(ctx), StringArgumentType.getString(ctx, "message"));
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("ffa").executes(player(p -> new KitSelectMenu(plugin, p, KitSelectMenu.Purpose.PARTY,
                        kit -> parties.startFight(p, kit, true)).open())))
                .then(Commands.literal("split").executes(player(p -> new KitSelectMenu(plugin, p, KitSelectMenu.Purpose.PARTY,
                        kit -> parties.startFight(p, kit, false)).open())))
                .build();
    }

    // ------------------------------------------------------------------ events

    private LiteralCommandNode<CommandSourceStack> event() {
        var events = plugin.events();
        return Commands.literal("event").requires(this::isPlayer)
                .executes(player(p -> plugin.messages().send(p, "events.help")))
                .then(Commands.literal("join").executes(player(events::join)))
                .then(Commands.literal("leave").executes(player(p -> events.leave(p, true))))
                .then(Commands.literal("host").requires(s -> s.getSender().hasPermission("riposte.event.host"))
                        .then(Args.choice("type", "tournament", "sumo")
                                .executes(ctx -> host(ctx, null))
                                .then(Args.word("kit", this::enabledKits).executes(ctx -> host(ctx, kitArg(ctx, "kit"))))))
                .then(Commands.literal("start").requires(s -> s.getSender().hasPermission("riposte.event.host"))
                        .executes(player(events::forceStart)))
                .then(Commands.literal("cancel").requires(s -> s.getSender().hasPermission("riposte.event.host"))
                        .executes(player(events::cancel)))
                .build();
    }

    private int host(CommandContext<CommandSourceStack> ctx, Kit kit) {
        Player player = playerOf(ctx);
        EventManager.Type type = EventManager.Type.parse(StringArgumentType.getString(ctx, "type"));
        if (type == null) {
            plugin.messages().send(player, "events.help");
            return Command.SINGLE_SUCCESS;
        }
        if (kit == null && type == EventManager.Type.TOURNAMENT) {
            new KitSelectMenu(plugin, player, KitSelectMenu.Purpose.EVENT, k -> plugin.events().host(player, type, k)).open();
            return Command.SINGLE_SUCCESS;
        }
        plugin.events().host(player, type, kit);
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------ stats and boards

    private LiteralCommandNode<CommandSourceStack> stats() {
        return Commands.literal("stats").requires(this::isPlayer)
                .executes(player(p -> StatsMenu.open(plugin, p, p.getName())))
                .then(Args.player("player").executes(ctx -> {
                    StatsMenu.open(plugin, playerOf(ctx), StringArgumentType.getString(ctx, "player"));
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> leaderboard() {
        return Commands.literal("leaderboard").requires(this::isPlayer)
                .executes(player(p -> new LeaderboardMenu(plugin, p).open()))
                .then(Args.word("board", () -> {
                    List<String> boards = new ArrayList<>();
                    boards.add(RankedManager.GLOBAL);
                    plugin.kits().ranked().forEach(k -> boards.add(k.id()));
                    return boards;
                }).executes(ctx -> {
                    String board = StringArgumentType.getString(ctx, "board").toLowerCase(java.util.Locale.ROOT);
                    if (!board.equals(RankedManager.GLOBAL) && plugin.kits().get(board) == null) {
                        plugin.messages().send(ctx.getSource().getSender(), "errors.unknown-kit", Text.p("kit", board));
                        return Command.SINGLE_SUCCESS;
                    }
                    LeaderboardMenu.printChat(plugin, playerOf(ctx), board);
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> kitEditor() {
        return Commands.literal("kiteditor").requires(this::isPlayer)
                .executes(player(p -> plugin.lobbyListener().run(p, HotbarAction.KIT_EDITOR)))
                .then(Args.word("kit", this::enabledKits).executes(ctx -> {
                    Kit kit = kitArg(ctx, "kit");
                    if (kit != null) {
                        plugin.kitEditor().open(playerOf(ctx), kit);
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    static boolean inLobby(RipostePlugin plugin, Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        return profile != null && profile.state() == PlayerState.LOBBY;
    }
}
