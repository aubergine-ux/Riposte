package io.github.aubergineux.riposte.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.aubergineux.riposte.config.Settings;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connection handling for SQLite (one connection, one writer thread) and MySQL/MariaDB (pooled).
 * Every call returns a future and runs off the main thread.
 */
public final class Database {

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    private final Logger logger;
    private final Dialect dialect;
    private final String prefix;
    private final ExecutorService executor;
    private final HikariDataSource pool;
    private final Connection sqlite;

    public Database(Settings settings, File dataFolder, Logger logger) throws SQLException {
        this.logger = logger;
        this.prefix = settings.tablePrefix.replaceAll("[^A-Za-z0-9_]", "");
        AtomicInteger threads = new AtomicInteger();
        if (settings.storageType.equals("mysql") || settings.storageType.equals("mariadb")) {
            dialect = Dialect.MYSQL;
            HikariConfig config = new HikariConfig();
            config.setPoolName("Riposte");
            config.setJdbcUrl("jdbc:mysql://" + settings.mysqlHost + ":" + settings.mysqlPort + "/" + settings.mysqlDatabase + "?" + settings.mysqlParams);
            config.setUsername(settings.mysqlUser);
            config.setPassword(settings.mysqlPassword);
            config.setMaximumPoolSize(settings.mysqlPoolSize);
            config.setMinimumIdle(Math.min(2, settings.mysqlPoolSize));
            config.setConnectionTimeout(10_000);
            config.setMaxLifetime(1_800_000);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            pool = new HikariDataSource(config);
            sqlite = null;
            executor = Executors.newFixedThreadPool(settings.mysqlPoolSize, r -> thread(r, threads));
        } else {
            dialect = Dialect.SQLITE;
            pool = null;
            File file = new File(dataFolder, settings.sqliteFile);
            file.getParentFile().mkdirs();
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite driver not found", e);
            }
            sqlite = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement st = sqlite.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
            }
            // SQLite allows one writer, so a single thread keeps every statement ordered and lock free.
            executor = Executors.newSingleThreadExecutor(r -> thread(r, threads));
        }
    }

    private static Thread thread(Runnable r, AtomicInteger counter) {
        Thread t = new Thread(r, "Riposte-DB-" + counter.incrementAndGet());
        t.setDaemon(true);
        return t;
    }

    public Dialect dialect() {
        return dialect;
    }

    public String table(String name) {
        return prefix + name;
    }

    public <T> CompletableFuture<T> async(SqlFunction<T> function) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sync(function);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Database error", e);
                throw new IllegalStateException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> run(SqlFunction<?> function) {
        return async(function).thenApply(x -> null);
    }

    /** Runs on the calling thread. Only call this from an async context (e.g. async pre-login). */
    public <T> T sync(SqlFunction<T> function) throws SQLException {
        if (pool != null) {
            try (Connection c = pool.getConnection()) {
                return function.apply(c);
            }
        }
        synchronized (sqlite) {
            return function.apply(sqlite);
        }
    }

    /** Blocks until queued writes finish. Used on shutdown so nothing is lost. */
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Timed out waiting for database writes to finish");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (sqlite != null) {
                sqlite.close();
            }
            if (pool != null) {
                pool.close();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error closing database", e);
        }
    }

    public enum Dialect {
        SQLITE, MYSQL;

        public String autoIncrementKey() {
            return this == SQLITE ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "BIGINT PRIMARY KEY AUTO_INCREMENT";
        }

        /** INSERT that updates the given columns when the primary key already exists. */
        public String upsert(String table, String[] columns, String[] keys) {
            StringBuilder sb = new StringBuilder("INSERT INTO ").append(table).append(" (")
                    .append(String.join(", ", columns)).append(") VALUES (")
                    .append("?, ".repeat(columns.length - 1)).append("?)");
            java.util.Set<String> keySet = java.util.Set.of(keys);
            StringBuilder updates = new StringBuilder();
            for (String col : columns) {
                if (keySet.contains(col)) {
                    continue;
                }
                if (!updates.isEmpty()) {
                    updates.append(", ");
                }
                updates.append(col).append(" = ").append(this == SQLITE ? "excluded." + col : "VALUES(" + col + ")");
            }
            if (this == SQLITE) {
                sb.append(" ON CONFLICT(").append(String.join(", ", keys)).append(") DO UPDATE SET ").append(updates);
            } else {
                sb.append(" ON DUPLICATE KEY UPDATE ").append(updates);
            }
            return sb.toString();
        }
    }
}
