package io.github.aubergineux.riposte.command;

import io.github.aubergineux.riposte.RipostePlugin;
import io.papermc.paper.command.brigadier.Commands;

/** Registers every Riposte command with Paper's Brigadier command system. */
public final class CommandRegistrar {

    private final RipostePlugin plugin;

    public CommandRegistrar(RipostePlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        new PlayerCommands(plugin).register(commands);
        new AdminCommands(plugin).register(commands);
    }
}
