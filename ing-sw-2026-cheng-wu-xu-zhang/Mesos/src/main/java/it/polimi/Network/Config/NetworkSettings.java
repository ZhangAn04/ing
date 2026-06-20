package it.polimi.Network.Config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;

/**
 * A utility class responsible for loading network configuration parameters.
 * <p>
 * It attempts to parse the {@code network_config.json} file bundled in the application resources
 * to retrieve the default server host and port. If the file is missing or malformed, it provides
 * safe fallback defaults to ensure the server and client can still boot.
 * </p>
 */
public class NetworkSettings {

    /** Creates a network settings reader. */
    public NetworkSettings() {
    }
    /** The name of the configuration file located in the resources folder. */
    private static final String CONFIG_FILE = "network_config.json";
    
    /** The Jackson object mapper used to parse the JSON file. */
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /** The root node of the parsed JSON configuration. */
    private static JsonNode configNode;

    static {
        try (InputStream is = NetworkSettings.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                configNode = mapper.readTree(is);
            }
        } catch (Exception e) {
            System.err.println("Could not load network_config.json, using hardcoded defaults.");
        }
    }

    /**
     * Retrieves the default host address from the JSON configuration.
     *
     * @return The configured host as a String, or {@code "127.0.0.1"} if not defined.
     */
    public static String getHostFromJSON() {
        return getString("defaultHost", "127.0.0.1");
    }

    /**
     * Retrieves the default port number from the JSON configuration.
     *
     * @return The configured port as an integer, or {@code 1234} if not defined.
     */
    public static int getPortFromJSON() {
        return getInt("defaultPort", 1234);
    }

    /**
     * Retrieves the default transport protocol from the JSON configuration.
     *
     * @return {@code "socket"} or {@code "rmi"}. Falls back to {@code "socket"}.
     */
    public static String getProtocolFromJSON() {
        String protocol = getString("defaultProtocol", "socket");
        return "rmi".equalsIgnoreCase(protocol) ? "rmi" : "socket";
    }

    /**
     * Retrieves the default RMI registry port from the JSON configuration.
     *
     * @return The configured RMI port, or {@code 1099} if not defined.
     */
    public static int getRmiPortFromJSON() {
        return getInt("defaultRmiPort", 1099);
    }

    /**
     * Retrieves the default RMI binding name from the JSON configuration.
     *
     * @return The configured binding name, or {@code "MesosGameService"} if not defined.
     */
    public static String getRmiBindingNameFromJSON() {
        return getString("defaultRmiBindingName", "MesosGameService");
    }

    /**
     * Retrieves a string value from the JSON configuration by key.
     * Returns a fallback value if the key is not found or configuration is unavailable.
     *
     * @param key the configuration key to look up
     * @param fallback the default value to return if key not found
     * @return the configured value or fallback
     */
    private static String getString(String key, String fallback) {
        if (configNode != null && configNode.has(key)) {
            return configNode.get(key).asText();
        }
        return fallback;
    }

    /**
     * Retrieves an integer value from the JSON configuration by key.
     * Returns a fallback value if the key is not found, configuration is unavailable,
     * or the value cannot be parsed as an integer.
     *
     * @param key the configuration key to look up
     * @param fallback the default value to return if key not found or parsing fails
     * @return the configured value or fallback
     */
    private static int getInt(String key, int fallback) {
        if (configNode != null && configNode.has(key)) {
            return configNode.get(key).asInt();
        }
        return fallback;
    }
}
