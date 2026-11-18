package io.github.aubergineux.riposte.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.arena.Arena;
import io.github.aubergineux.riposte.kit.Kit;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Shared argument builders with suggestions. */
final class Args {

    private Args() {
    }

    static SuggestionProvider<CommandSourceStack> suggest(Supplier<Collection<String>> values) {
        return (ctx, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            for (String value : values.get()) {
                if (value.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(value);
                }
            }
            return builder.buildFuture();
        };
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> word(String name, Supplier<Collection<String>> values) {
        return Commands.argument(name, StringArgumentType.word()).suggests(suggest(values));
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> player(String name) {
        return word(name, () -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> kit(RipostePlugin plugin, String name) {
        return word(name, () -> plugin.kits().all().stream().map(Kit::id).toList());
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> arena(RipostePlugin plugin, String name) {
        return word(name, () -> plugin.arenas().all().stream().map(Arena::name).toList());
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> choice(String name, String... values) {
        return word(name, () -> List.of(values));
    }
}
