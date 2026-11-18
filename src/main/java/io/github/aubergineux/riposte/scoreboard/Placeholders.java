package io.github.aubergineux.riposte.scoreboard;

import io.github.aubergineux.riposte.util.Text;

import java.util.HashMap;
import java.util.Map;

/**
 * Plain string placeholder substitution. Sidebar lines are filled in as text first and only then
 * parsed, so identical lines (very common across players) hit the parse cache instead of MiniMessage.
 */
final class Placeholders {

    private final Map<String, String> values = new HashMap<>();

    /** A literal value; any tags in it are escaped. */
    void p(String key, Object value) {
        values.put(key, Text.MM.escapeTags(String.valueOf(value)));
    }

    /** A value that is itself MiniMessage, such as a kit name or division color. */
    void mm(String key, String value) {
        values.put(key, value);
    }

    String apply(String template) {
        if (template.indexOf('<') < 0) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length() + 32);
        int i = 0;
        while (i < template.length()) {
            char ch = template.charAt(i);
            if (ch == '<') {
                int end = template.indexOf('>', i + 1);
                if (end > i) {
                    String value = values.get(template.substring(i + 1, end));
                    if (value != null) {
                        out.append(value);
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }
}
