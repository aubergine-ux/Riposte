package io.github.aubergineux.riposte.ranked;

import org.bukkit.Material;

/**
 * A named ELO band, e.g. "Gold III". {@code color} is a MiniMessage tag such as {@code <gold>} or {@code <#ffd166>}.
 */
public record Division(String name, int minElo, String color, Material icon) {

    /** The division name in its color, ready for MiniMessage. Open tags are closed so the color never leaks. */
    public String formatted() {
        return color + name + "<reset>";
    }
}
