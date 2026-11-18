package io.github.aubergineux.riposte.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DialectTest {

    @Test
    void sqliteUpsertUsesOnConflict() {
        String sql = Database.Dialect.SQLITE.upsert("t", new String[]{"a", "b", "c"}, new String[]{"a"});
        assertEquals("INSERT INTO t (a, b, c) VALUES (?, ?, ?) ON CONFLICT(a) DO UPDATE SET b = excluded.b, c = excluded.c", sql);
    }

    @Test
    void mysqlUpsertUsesDuplicateKey() {
        String sql = Database.Dialect.MYSQL.upsert("t", new String[]{"a", "b", "c"}, new String[]{"a", "b"});
        assertEquals("INSERT INTO t (a, b, c) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE c = VALUES(c)", sql);
    }

    @Test
    void layoutEncodingRoundTrips() {
        int[] layout = new int[41];
        for (int i = 0; i < 41; i++) {
            layout[i] = (i * 7) % 41;
        }
        assertArrayEquals(layout, DataStore.parseLayout(DataStore.encodeLayout(layout)));
        assertNull(DataStore.parseLayout("1,2,x"));
    }
}
