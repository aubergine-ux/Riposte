package io.github.aubergineux.riposte.menu.menus;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.menu.Menu;
import io.github.aubergineux.riposte.party.Party;
import io.github.aubergineux.riposte.util.Items;
import io.github.aubergineux.riposte.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Party leader menu: party fights, challenging other parties, and member management. */
public final class PartyMenu extends Menu {

    private final RipostePlugin plugin;

    public PartyMenu(RipostePlugin plugin, Player viewer) {
        super(viewer, 4, plugin.menus().title("party"));
        this.plugin = plugin;
    }

    @Override
    protected void draw() {
        var m = plugin.menus();
        Party party = plugin.parties().partyOf(viewer);
        if (party == null) {
            set(13, m.item("party.none"));
            fill(m.filler());
            return;
        }
        boolean leader = party.isLeader(viewer.getUniqueId());
        if (leader) {
            set(m.slot("party.split.slot", 10), m.item("party.split"), click ->
                    new KitSelectMenu(plugin, viewer, KitSelectMenu.Purpose.PARTY, kit -> plugin.parties().startFight(viewer, kit, false)).open());
            set(m.slot("party.ffa.slot", 12), m.item("party.ffa"), click ->
                    new KitSelectMenu(plugin, viewer, KitSelectMenu.Purpose.PARTY, kit -> plugin.parties().startFight(viewer, kit, true)).open());
            set(m.slot("party.duel.slot", 14), m.item("party.duel"), click -> new PartyListMenu().open());
        }
        set(m.slot("party.info.slot", 16), m.item("party.info", Text.p("leader", party.leaderName()), Text.p("size", party.size()),
                Text.p("max", plugin.settings().partyMaxSize)), click -> {
            viewer.closeInventory();
            plugin.parties().info(viewer);
        });
        int slot = 19;
        for (Player member : party.onlineMembers()) {
            if (slot > 25) {
                break;
            }
            boolean isLeader = party.isLeader(member.getUniqueId());
            var item = Items.of(Material.PLAYER_HEAD).skull(member)
                    .name(m.messages().formatItem(m.string(isLeader ? "party.member.leader-name" : "party.member.name", "<player>"),
                            Text.p("player", member.getName())))
                    .lore(m.lore(m.lines(leader && !isLeader ? "party.member.lore-kick" : "party.member.lore")))
                    .hideAll().build();
            set(slot++, item, leader && !isLeader ? click -> {
                plugin.parties().kick(viewer, member.getName());
                redraw();
            } : null);
        }
        set(m.slot("party.leave.slot", 31), m.item(leader ? "party.disband" : "party.leave"), click -> {
            viewer.closeInventory();
            if (leader) {
                plugin.parties().disband(viewer);
            } else {
                plugin.parties().leave(viewer);
            }
        });
        fill(m.filler());
    }

    /** Other parties in the lobby that can be challenged. */
    private final class PartyListMenu extends Menu {

        PartyListMenu() {
            super(PartyMenu.this.viewer, 6, plugin.menus().title("party-list"));
        }

        @Override
        protected void draw() {
            var m = plugin.menus();
            Party own = plugin.parties().partyOf(viewer);
            int[] slots = innerSlots(6);
            int i = 0;
            java.util.Set<Party> seen = new java.util.HashSet<>();
            for (Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
                Party party = plugin.parties().partyOf(other);
                if (party == null || party == own || !seen.add(party) || i >= slots.length) {
                    continue;
                }
                Player leader = org.bukkit.Bukkit.getPlayer(party.leader());
                if (leader == null) {
                    continue;
                }
                var item = Items.of(Material.PLAYER_HEAD).skull(leader)
                        .name(m.messages().formatItem(m.string("party-list.party.name", "<leader>"), Text.p("leader", leader.getName())))
                        .lore(m.lore(m.lines("party-list.party.lore"), Text.p("size", party.size())))
                        .hideAll().build();
                set(slots[i++], item, click -> new KitSelectMenu(plugin, viewer, KitSelectMenu.Purpose.DUEL,
                        kit -> plugin.duels().send(viewer, leader, kit, null)).open());
            }
            if (i == 0) {
                set(22, m.item("party-list.empty"));
            }
            border(m.border());
        }

        @Override
        public void tick() {
            redraw();
        }
    }
}
