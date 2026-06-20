package it.polimi.Game.Persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class responsible for loading MySQL persistence configuration.
 * <p>
 * It tries to read a {@code db_config.json} file from the current working directory first,
 * then falls back to the bundled resource with the same name.
 * </p>
 */
public final class DbSettings {

    private static final String CONFIG_FILE = "db_config.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode configNode;

    static {
        configNode = loadConfigNode();
    }

    /** Prevents instantiation of this configuration utility. */
    private DbSettings() {
    }

    /**
     * Checks if score persistence is enabled in the configuration file.
     *
     * @return true if enabled, false otherwise.
     */
    public static boolean isEnabledFromJSON() {
        return getBoolean("enabled", true);
    }

    /**
     * Retrieves the database host from the configuration file.
     *
     * @return The database host address.
     */
    public static String getHostFromJSON() {
        return getString("host", "127.0.0.1");
    }

    /**
     * Retrieves the database port from the configuration file.
     *
     * @return The database port number.
     */
    public static int getPortFromJSON() {
        return getInt("port", 3306);
    }

    /**
     * Retrieves the database name from the configuration file.
     *
     * @return The name of the database.
     */
    public static String getDbNameFromJSON() {
        return getString("db", "mesos");
    }

    /**
     * Retrieves the database username from the configuration file.
     *
     * @return The database user name.
     */
    public static String getUserFromJSON() {
        return getString("user", "mesos");
    }

    /**
     * Retrieves the database admin username from the configuration file.
     *
     * @return The administrator username.
     */
    public static String getAdminUserFromJSON() {
        return getString("adminUser", "root");
    }

    /**
     * Retrieves the database password from the configuration file.
     *
     * @return The database password.
     */
    public static String getPasswordFromJSON() {
        return getString("password", "Woshinibaba88!");
    }

    /**
     * Retrieves the database admin password from the configuration file.
     *
     * @return The administrator password.
     */
    public static String getAdminPasswordFromJSON() {
        return getString("adminPassword", "");
    }

    /**
     * Retrieves the name of the database table for scores from the configuration file.
     *
     * @return The database table name.
     */
    public static String getTableFromJSON() {
        return getString("table", "mesos_game_scores");
    }

    /**
     * Checks if automatic table creation is enabled in the configuration file.
     *
     * @return true if automatic creation is enabled, false otherwise.
     */
    public static boolean getAutoCreateFromJSON() {
        return getBoolean("autoCreate", true);
    }

    /**
     * Retrieves the connection timeout in milliseconds from the configuration file.
     *
     * @return The connect timeout in milliseconds.
     */
    public static int getConnectTimeoutMsFromJSON() {
        return getInt("connectTimeoutMs", 10000);
    }

    /**
     * Retrieves the socket read timeout in milliseconds from the configuration file.
     *
     * @return The socket timeout in milliseconds.
     */
    public static int getSocketTimeoutMsFromJSON() {
        return getInt("socketTimeoutMs", 30000);
    }

    /**
     * Loads the database configuration from the working directory or classpath.
     *
     * @return the configuration node, or {@code null} when no valid file is available
     */
    private static JsonNode loadConfigNode() {
        JsonNode fromFile = tryLoadFromWorkingDirectory();
        if (fromFile != null) {
            return fromFile;
        }

        return tryLoadFromResources();
    }

    /**
     * Attempts to load the configuration from the current working directory.
     *
     * @return the parsed node, or {@code null} if loading fails
     */
    private static JsonNode tryLoadFromWorkingDirectory() {
        Path filePath = Paths.get(CONFIG_FILE);
        if (!Files.isRegularFile(filePath)) {
            return null;
        }

        try (InputStream is = Files.newInputStream(filePath)) {
            return MAPPER.readTree(is);
        } catch (Exception e) {
            System.err.println("[DB] Could not parse " + CONFIG_FILE + " from working directory; falling back to bundled resource.");
            return null;
        }
    }

    /**
     * Attempts to load the bundled configuration resource.
     *
     * @return the parsed node, or {@code null} if loading fails
     */
    private static JsonNode tryLoadFromResources() {
        try (InputStream is = DbSettings.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                return MAPPER.readTree(is);
            }
        } catch (Exception e) {
            System.err.println("[DB] Could not load bundled " + CONFIG_FILE + "; using fallback defaults.");
        }
        return null;
    }

    /**
     * Reads a string setting from the loaded configuration.
     *
     * @param key JSON property name
     * @param fallback value returned when the property is unavailable
     * @return the configured or fallback value
     */
    private static String getString(String key, String fallback) {
        if (configNode != null && configNode.has(key)) {
            JsonNode value = configNode.get(key);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return fallback;
    }

    /**
     * Reads an integer setting from the loaded configuration.
     *
     * @param key JSON property name
     * @param fallback value returned when the property is unavailable
     * @return the configured or fallback value
     */
    private static int getInt(String key, int fallback) {
        if (configNode != null && configNode.has(key)) {
            return configNode.get(key).asInt();
        }
        return fallback;
    }

    /**
     * Reads a boolean setting from the loaded configuration.
     *
     * @param key JSON property name
     * @param fallback value returned when the property is unavailable
     * @return the configured or fallback value
     */
    private static boolean getBoolean(String key, boolean fallback) {
        if (configNode != null && configNode.has(key)) {
            return configNode.get(key).asBoolean();
        }
        return fallback;
    }
}
