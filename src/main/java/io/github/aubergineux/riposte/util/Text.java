package io.github.aubergineux.riposte.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public final class Text {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component parse(String input, TagResolver... resolvers) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MM.deserialize(input, resolvers);
    }

    /** Parses text meant for item names and lore, where vanilla would otherwise italicize it. */
    public static Component item(String input, TagResolver... resolvers) {
        return parse(input, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> itemLines(List<String> lines, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(item(line, resolvers));
        }
        return out;
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static TagResolver p(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static TagResolver c(String key, Component value) {
        return Placeholder.component(key, value);
    }

    /** A placeholder whose value is itself MiniMessage (used for colors and division names from config). */
    public static TagResolver mm(String key, String value) {
        return Placeholder.parsed(key, value);
    }

    public static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long minutes = seconds / 60;
        seconds %= 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    public static String formatDecimal(double value) {
        if (Math.abs(value - Math.round(value)) < 0.05) {
            return String.valueOf(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    public static String ratio(int a, int b) {
        if (b == 0) {
            return formatDecimal(a);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", (double) a / b);
    }

    public static String percent(int part, int total) {
        if (total == 0) {
            return "0%";
        }
        return Math.round(part * 100.0 / total) + "%";
    }

    /** Health in hearts, e.g. 17.0 hp becomes "8.5". */
    public static String hearts(double health) {
        return formatDecimal(Math.ceil(health) / 2.0);
    }

    private static final char[] SPARKS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};

    public static String sparkline(List<Integer> values) {
        if (values.isEmpty()) {
            return "";
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        StringBuilder sb = new StringBuilder(values.size());
        for (int v : values) {
            int idx = max == min ? SPARKS.length / 2 : (int) Math.round((v - min) * (SPARKS.length - 1.0) / (max - min));
            sb.append(SPARKS[idx]);
        }
        return sb.toString();
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        String lower = s.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder(lower.length());
        boolean up = true;
        for (char ch : lower.toCharArray()) {
            sb.append(up ? Character.toUpperCase(ch) : ch);
            up = ch == ' ';
        }
        return sb.toString();
    }
}
