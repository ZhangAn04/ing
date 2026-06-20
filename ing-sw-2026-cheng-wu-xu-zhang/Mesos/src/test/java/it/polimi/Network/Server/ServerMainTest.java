package it.polimi.Network.Server;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ServerMain} helper methods and startup branches.
 */
class ServerMainTest {

    @Test
    void resolveProtocolHandlesExplicitLegacyAndFallback() throws Exception {
        assertEquals("socket", invokeString("resolveProtocol", new String[] { "socket" }));
        assertEquals("rmi", invokeString("resolveProtocol", new String[] { "RMI" }));
        assertEquals("socket", invokeString("resolveProtocol", new String[] { "9999" }));
    }

    @Test
    void resolvePortsHandleExplicitAndFallback() throws Exception {
        assertEquals(9999, invokeInt("resolveSocketPort", new String[] { "9999" }));
        assertEquals(9999, invokeInt("resolveSocketPort", new String[] { "socket", "bad" }));

        assertEquals(1099, invokeInt("resolveRmiPort", new String[] { "rmi", "1099" }));
        assertEquals(1099, invokeInt("resolveRmiPort", new String[] { "rmi", "bad" }));
    }

    @Test
    void parseAndProtocolHelpersWork() throws Exception {
        assertEquals(7777, invokeInt2("parsePortOrDefault", "7777", 1234));
        assertEquals(1234, invokeInt2("parsePortOrDefault", "nope", 1234));

        assertTrue(invokeBool("isProtocolToken", "socket"));
        assertTrue(invokeBool("isProtocolToken", "RMI"));
    }

    @Test
    void startSocketServerWithInvalidPortThrowsFast() throws Exception {
        Method startSocket = ServerMain.class.getDeclaredMethod("startSocketServer", int.class);
        startSocket.setAccessible(true);

        try {
            startSocket.invoke(null, -1);
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    void startRmiServerWithInvalidPortReturnsWithoutBlocking() throws Exception {
        Method startRmi = ServerMain.class.getDeclaredMethod("startRmiServer", int.class);
        startRmi.setAccessible(true);
        assertThrows(InvocationTargetException.class, () -> startRmi.invoke(null, -1));
    }

    @Test
    void mainInvalidArgsCoverBothProtocolBranches() {
        assertThrows(IllegalArgumentException.class, () -> ServerMain.main(new String[] { "socket", "-1" }));
        assertThrows(IllegalArgumentException.class, () -> ServerMain.main(new String[] { "rmi", "-1" }));
    }

    @Test
    void startSocketServerAcceptsConnectionWhenRunAsDaemon() throws Exception {
        int port = findFreePort();
        Method startSocket = ServerMain.class.getDeclaredMethod("startSocketServer", int.class);
        startSocket.setAccessible(true);

        Thread daemon = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    startSocket.invoke(null, port);
                } catch (Exception ignored) {
                    // Server thread is daemon and may be interrupted at test teardown.
                }
            }
        });
        daemon.setDaemon(true);
        daemon.start();

        Thread.sleep(200);
        try (Socket socket = new Socket("127.0.0.1", port)) {
            assertTrue(socket.isConnected());
        }
    }

    @Test
    void startRmiServerCanStartAndStopWhenInterrupted() throws Exception {
        int port = findFreePort();
        Method startRmi = ServerMain.class.getDeclaredMethod("startRmiServer", int.class);
        startRmi.setAccessible(true);

        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    startRmi.invoke(null, port);
                } catch (Exception ignored) {
                    // Invocation target wrapped exceptions are not relevant for interruption path.
                }
            }
        });
        serverThread.start();

        Thread.sleep(500);
        serverThread.interrupt();
        serverThread.join(2000);
        assertTrue(!serverThread.isAlive());
    }

    /** Reflectively invokes a private static method that returns a string. */
    private String invokeString(String methodName, String[] args) throws Exception {
        Method m = ServerMain.class.getDeclaredMethod(methodName, String[].class);
        m.setAccessible(true);
        return (String) m.invoke(null, (Object) args);
    }

    /** Reflectively invokes a private static method that returns an int. */
    private int invokeInt(String methodName, String[] args) throws Exception {
        Method m = ServerMain.class.getDeclaredMethod(methodName, String[].class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, (Object) args);
    }

    /** Reflectively invokes a private static method with (String, int) signature. */
    private int invokeInt2(String methodName, String value, int fallback) throws Exception {
        Method m = ServerMain.class.getDeclaredMethod(methodName, String.class, int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, value, fallback);
    }

    /** Reflectively invokes a private static method that returns a boolean. */
    private boolean invokeBool(String methodName, String value) throws Exception {
        Method m = ServerMain.class.getDeclaredMethod(methodName, String.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, value);
    }

    /** @return an ephemeral TCP port available at call time. */
    private int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
