package it.polimi.Network.Config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link NetworkSettings} configuration loading.
 */
class NetworkSettingsTest {

    /**
     * Verifies the utility class constructor can be invoked.
     */
    @Test
    void constructorIsInvokable() {
        new NetworkSettings();
    }

    /**
     * Verifies that the host is loaded from the JSON configuration.
     */
    @Test
    void hostIsLoadedFromConfig() {
        assertEquals("127.0.0.1", NetworkSettings.getHostFromJSON());
    }

    /**
     * Verifies that the port is loaded from the JSON configuration.
     */
    @Test
    void portIsLoadedFromConfig() {
        assertEquals(9999, NetworkSettings.getPortFromJSON());
    }

    /**
     * Verifies protocol and RMI-specific settings are loaded from configuration.
     */
    @Test
    void protocolAndRmiSettingsAreLoadedFromConfig() {
        assertEquals("socket", NetworkSettings.getProtocolFromJSON());
        assertEquals(1099, NetworkSettings.getRmiPortFromJSON());
        assertEquals("MesosGameService", NetworkSettings.getRmiBindingNameFromJSON());
    }

    /**
     * Verifies fallback defaults and protocol normalization when config keys are missing/invalid.
     */
    @Test
    void fallbackDefaultsAndProtocolNormalizationWork() throws Exception {
        Field configNodeField = NetworkSettings.class.getDeclaredField("configNode");
        configNodeField.setAccessible(true);
        JsonNode original = (JsonNode) configNodeField.get(null);
        ObjectMapper mapper = new ObjectMapper();

        try {
            configNodeField.set(null, mapper.createObjectNode());

            assertEquals("127.0.0.1", NetworkSettings.getHostFromJSON());
            assertEquals(1234, NetworkSettings.getPortFromJSON());
            assertEquals("socket", NetworkSettings.getProtocolFromJSON());
            assertEquals(1099, NetworkSettings.getRmiPortFromJSON());
            assertEquals("MesosGameService", NetworkSettings.getRmiBindingNameFromJSON());

            ObjectNode invalidProtocol = mapper.createObjectNode();
            invalidProtocol.put("defaultProtocol", "http");
            configNodeField.set(null, invalidProtocol);

            assertEquals("socket", NetworkSettings.getProtocolFromJSON());
        } finally {
            configNodeField.set(null, original);
        }
    }

    /**
     * Verifies that the transport protocol is loaded from the JSON configuration.
     */
    @Test
    void protocolIsLoadedFromConfig() {
        assertEquals("socket", NetworkSettings.getProtocolFromJSON());
    }

    /**
     * Verifies that the RMI port is loaded from the JSON configuration.
     */
    @Test
    void rmiPortIsLoadedFromConfig() {
        assertEquals(1099, NetworkSettings.getRmiPortFromJSON());
    }

    /**
     * Verifies that the RMI binding name is loaded from the JSON configuration.
     */
    @Test
    void rmiBindingNameIsLoadedFromConfig() {
        assertEquals("MesosGameService", NetworkSettings.getRmiBindingNameFromJSON());
    }

    /**
     * Verifies that hardcoded defaults are used when config node is unavailable.
     */
    @Test
    void fallbackValuesAreUsedWhenConfigNodeIsNull() throws Exception {
        Field field = NetworkSettings.class.getDeclaredField("configNode");
        field.setAccessible(true);

        Object previous = field.get(null);
        try {
            field.set(null, null);

            assertEquals("127.0.0.1", NetworkSettings.getHostFromJSON());
            assertEquals(1234, NetworkSettings.getPortFromJSON());
            assertEquals("socket", NetworkSettings.getProtocolFromJSON());
            assertEquals(1099, NetworkSettings.getRmiPortFromJSON());
            assertEquals("MesosGameService", NetworkSettings.getRmiBindingNameFromJSON());
        } finally {
            field.set(null, previous);
        }
    }

    /**
     * Verifies that invalid protocol values are normalized to socket.
     */
    @Test
    void invalidProtocolFallsBackToSocket() throws Exception {
        Field field = NetworkSettings.class.getDeclaredField("configNode");
        field.setAccessible(true);

        Object previous = field.get(null);
        try {
            ObjectNode fakeConfig = new ObjectMapper().createObjectNode();
            fakeConfig.put("defaultProtocol", "not-valid");
            field.set(null, fakeConfig);

            assertEquals("socket", NetworkSettings.getProtocolFromJSON());
        } finally {
            field.set(null, previous);
        }
    }

    /**
     * Verifies fallback paths when config node exists but requested keys are missing.
     */
    @Test
    void missingKeysFallbackWhenConfigNodeIsPresent() throws Exception {
        Field field = NetworkSettings.class.getDeclaredField("configNode");
        field.setAccessible(true);

        Object previous = field.get(null);
        try {
            ObjectNode emptyConfig = new ObjectMapper().createObjectNode();
            field.set(null, emptyConfig);

            assertEquals("127.0.0.1", NetworkSettings.getHostFromJSON());
            assertEquals(1234, NetworkSettings.getPortFromJSON());
        } finally {
            field.set(null, previous);
        }
    }
}
