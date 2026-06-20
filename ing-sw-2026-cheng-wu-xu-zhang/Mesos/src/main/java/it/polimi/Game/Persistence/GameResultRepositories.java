package it.polimi.Game.Persistence;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Factory for creating {@link GameResultRepository} instances from JSON defaults (db_config.json)
 * and optional environment/system properties overrides.
 */
public final class GameResultRepositories {

    private static final AtomicBoolean DISABLED_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean ENABLED_LOGGED = new AtomicBoolean(false);

    /** Prevents instantiation of this repository factory. */
    private GameResultRepositories() {
    }

    /**
     * Creates a repository based on environment variables / JVM system properties.
     * <p>
        * Enabled by default (can be disabled via MESOS_DB_ENABLED=false / -Dmesos.db.enabled=false).
     * </p>
     *
     * Supported keys (env var / system property):
     * <ul>
     *   <li>MESOS_DB_ENABLED / mesos.db.enabled</li>
     *   <li>MESOS_DB_URL / mesos.db.url</li>
     *   <li>MESOS_DB_HOST / mesos.db.host</li>
     *   <li>MESOS_DB_PORT / mesos.db.port</li>
     *   <li>MESOS_DB_NAME / mesos.db.name</li>
     *   <li>MESOS_DB_USER / mesos.db.user</li>
     *   <li>MESOS_DB_PASSWORD / mesos.db.password</li>
    *   <li>MESOS_DB_ADMIN_USER / mesos.db.adminUser</li>
    *   <li>MESOS_DB_ADMIN_PASSWORD / mesos.db.adminPassword</li>
     *   <li>MESOS_DB_TABLE / mesos.db.table</li>
     *   <li>MESOS_DB_AUTOCREATE / mesos.db.autocreate</li>
     * </ul>
     *
     * @return the configured MySQL repository, or the no-op repository when persistence is disabled
     */
    public static GameResultRepository fromEnvironment() {
        boolean enabled = getBoolean("MESOS_DB_ENABLED", "mesos.db.enabled", DbSettings.isEnabledFromJSON());
        if (!enabled) {
            if (DISABLED_LOGGED.compareAndSet(false, true)) {
                System.out.println("[DB] MySQL score persistence DISABLED (set MESOS_DB_ENABLED=true or -Dmesos.db.enabled=true to enable, or edit db_config.json)");
            }
            return NoOpGameResultRepository.INSTANCE;
        }

        String host = getString("MESOS_DB_HOST", "mesos.db.host", DbSettings.getHostFromJSON());
        int port = getInt("MESOS_DB_PORT", "mesos.db.port", DbSettings.getPortFromJSON());
        String dbName = getString("MESOS_DB_NAME", "mesos.db.name", DbSettings.getDbNameFromJSON());
        int connectTimeoutMs = DbSettings.getConnectTimeoutMsFromJSON();
        int socketTimeoutMs = DbSettings.getSocketTimeoutMsFromJSON();

        String jdbcUrl = getString("MESOS_DB_URL", "mesos.db.url", null);
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            jdbcUrl = buildJdbcUrl(host, port, dbName, connectTimeoutMs, socketTimeoutMs);
        }

        String user = getString("MESOS_DB_USER", "mesos.db.user", DbSettings.getUserFromJSON());
        String password = getString("MESOS_DB_PASSWORD", "mesos.db.password", DbSettings.getPasswordFromJSON());
        String adminUser = getString("MESOS_DB_ADMIN_USER", "mesos.db.adminUser", DbSettings.getAdminUserFromJSON());
        String adminPassword = getString("MESOS_DB_ADMIN_PASSWORD", "mesos.db.adminPassword", DbSettings.getAdminPasswordFromJSON());
        String table = getString("MESOS_DB_TABLE", "mesos.db.table", DbSettings.getTableFromJSON());
        boolean autoCreate = getBoolean("MESOS_DB_AUTOCREATE", "mesos.db.autocreate", DbSettings.getAutoCreateFromJSON());

        // Ensure database exists before creating repository
        String bootstrapUser = adminUser.isBlank() ? user : adminUser;
        String bootstrapPassword = adminPassword.isBlank() ? password : adminPassword;
        MySqlGameResultRepository.ensureDatabaseExists(host, port, bootstrapUser, bootstrapPassword, dbName, connectTimeoutMs, socketTimeoutMs);

        if (ENABLED_LOGGED.compareAndSet(false, true)) {
            System.out.println("[DB] MySQL score persistence ENABLED" +
            " url=" + redactJdbcUrl(jdbcUrl) +
            " user=" + user +
            " adminUserSet=" + (!adminUser.isBlank()) +
            " table=" + table +
            " autoCreate=" + autoCreate +
            " passwordSet=" + (!password.isEmpty()));
        }

        return new MySqlGameResultRepository(jdbcUrl, user, password, table, autoCreate);
    }

    /**
     * Builds a MySQL JDBC URL from the configured connection settings.
     *
     * @param host database host
     * @param port database port
     * @param dbName database name
     * @param connectTimeoutMs connection timeout in milliseconds
     * @param socketTimeoutMs socket timeout in milliseconds
     * @return the configured JDBC URL
     */
    private static String buildJdbcUrl(String host, int port, String dbName, int connectTimeoutMs, int socketTimeoutMs) {
        // Keep defaults conservative and resilient.
        return "jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?useSSL=false"
                + "&useUnicode=true"
                + "&characterEncoding=utf8"
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC"
                + "&connectTimeout=" + Math.max(0, connectTimeoutMs)
                + "&socketTimeout=" + Math.max(0, socketTimeoutMs);
    }

    /**
     * Resolves a string from an environment variable, then a system property, then a fallback.
     *
     * @param envKey environment variable name
     * @param propKey system property name
     * @param fallback value used when neither override exists
     * @return the resolved value
     */
    private static String getString(String envKey, String propKey, String fallback) {
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null) {
            return fromEnv;
        }

        String fromProp = System.getProperty(propKey);
        if (fromProp != null) {
            return fromProp;
        }

        return fallback;
    }

    /**
     * Resolves an integer override, returning the fallback for missing or malformed values.
     *
     * @param envKey environment variable name
     * @param propKey system property name
     * @param fallback fallback value
     * @return the resolved integer
     */
    private static int getInt(String envKey, String propKey, int fallback) {
        String raw = getString(envKey, propKey, null);
        if (raw == null) {
            return fallback;
        }

        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Resolves a boolean override using common affirmative values.
     *
     * @param envKey environment variable name
     * @param propKey system property name
     * @param fallback fallback value
     * @return the resolved boolean
     */
    private static boolean getBoolean(String envKey, String propKey, boolean fallback) {
        String raw = getString(envKey, propKey, null);
        if (raw == null) {
            return fallback;
        }

        String normalized = raw.trim().toLowerCase();
        return normalized.equals("true")
                || normalized.equals("1")
                || normalized.equals("yes")
                || normalized.equals("y")
                || normalized.equals("on");
    }

    /**
     * Redacts credentials embedded in a JDBC URL before logging it.
     *
     * @param jdbcUrl URL to redact
     * @return the redacted URL, or {@code null} when the input is null
     */
    private static String redactJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }

        // Avoid leaking credentials if they were (incorrectly) embedded in the URL.
        String redacted = jdbcUrl;
        redacted = redacted.replaceAll("(?i)(password=)[^&]*", "$1***");
        redacted = redacted.replaceAll("(?i)(user=)[^&]*", "$1***");
        return redacted;
    }
}
