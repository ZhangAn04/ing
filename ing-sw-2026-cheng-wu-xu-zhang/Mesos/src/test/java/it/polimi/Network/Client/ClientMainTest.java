package it.polimi.Network.Client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for argument parsing helpers in {@link ClientMain}.
 */
class ClientMainTest {

    @Test
    void resolveProtocolSupportsExplicitAndLegacyModes() throws Exception {
        assertEquals("socket", invokeString("resolveProtocol", new String[] { "socket" }));
        assertEquals("rmi", invokeString("resolveProtocol", new String[] { "RMI" }));
        assertEquals("socket", invokeString("resolveProtocol", new String[] { "127.0.0.1", "9999" }));
        assertEquals("socket", invokeString("resolveProtocol", new String[0]));
    }

    @Test
    void resolveSocketHostAndPortHandleProtocolAndLegacyArgs() throws Exception {
        assertEquals("legacyHost", invokeString("resolveSocketHost", new String[] { "legacyHost", "7777" }));
        assertEquals("socketHost", invokeString("resolveSocketHost", new String[] { "socket", "socketHost", "7000" }));
        assertEquals("127.0.0.1", invokeString("resolveSocketHost", new String[0]));

        assertEquals(7777, invokeInt("resolveSocketPort", new String[] { "legacyHost", "7777" }));
        assertEquals(7000, invokeInt("resolveSocketPort", new String[] { "socket", "socketHost", "7000" }));
        assertEquals(9999, invokeInt("resolveSocketPort", new String[0]));
    }

    @Test
    void resolveRmiHostAndPortHandleOptionalHost() throws Exception {
        assertEquals("127.0.0.1", invokeString("resolveRmiHost", new String[] { "rmi", "1099" }));
        assertEquals("myrmi", invokeString("resolveRmiHost", new String[] { "rmi", "myrmi", "2099" }));
        assertEquals("127.0.0.1", invokeString("resolveRmiHost", new String[0]));

        assertEquals(1099, invokeInt("resolveRmiPort", new String[] { "rmi", "1099" }));
        assertEquals(2099, invokeInt("resolveRmiPort", new String[] { "rmi", "myrmi", "2099" }));
        assertEquals(1099, invokeInt("resolveRmiPort", new String[0]));
    }

    @Test
    void parseAndValidationHelpersWork() throws Exception {
        assertEquals(5000, invokeInt2("parsePortOrDefault", "5000", 9999));
        assertEquals(9999, invokeInt2("parsePortOrDefault", "bad", 9999));

        assertTrue(invokeBool("isInteger", "42"));
        assertFalse(invokeBool("isInteger", "abc"));

        assertTrue(invokeBool("isProtocolToken", "socket"));
        assertTrue(invokeBool("isProtocolToken", "RMI"));
        assertFalse(invokeBool("isProtocolToken", "http"));
    }

    /** Reflectively invokes a private static method that returns a string. */
    private String invokeString(String methodName, String[] args) throws Exception {
        Method m = ClientMain.class.getDeclaredMethod(methodName, String[].class);
        m.setAccessible(true);
        return (String) m.invoke(null, (Object) args);
    }

    /** Reflectively invokes a private static method that returns an int. */
    private int invokeInt(String methodName, String[] args) throws Exception {
        Method m = ClientMain.class.getDeclaredMethod(methodName, String[].class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, (Object) args);
    }

    /** Reflectively invokes a private static method with (String, int) signature. */
    private int invokeInt2(String methodName, String value, int fallback) throws Exception {
        Method m = ClientMain.class.getDeclaredMethod(methodName, String.class, int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, value, fallback);
    }

    /** Reflectively invokes a private static method that returns a boolean. */
    private boolean invokeBool(String methodName, String value) throws Exception {
        Method m = ClientMain.class.getDeclaredMethod(methodName, String.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, value);
    }
}
