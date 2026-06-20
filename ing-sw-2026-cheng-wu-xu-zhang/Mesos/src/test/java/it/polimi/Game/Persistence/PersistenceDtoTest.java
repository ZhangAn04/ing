package it.polimi.Game.Persistence;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Persistence data transfer objects, repositories, and configuration settings.
 */
class PersistenceDtoTest {

    @Test
    void testPlayerScoreDto() {
        PlayerScore score = new PlayerScore("Alice", 15, 5, 10, 1, true);

        assertEquals("Alice", score.nickname());
        assertEquals(15, score.prestigePoints());
        assertEquals(5, score.food());
        assertEquals(10, score.endgameBonus());
        assertEquals(1, score.rank());
        assertTrue(score.winner());
    }

    @Test
    void testGameResultDto() {
        Instant now = Instant.now();
        PlayerScore score = new PlayerScore("Bob", 10, 2, 5, 2, false);
        GameResult result = new GameResult("match-123", now, "Final Report Summary", Collections.singletonList(score));

        assertEquals("match-123", result.matchId());
        assertEquals(now, result.endedAt());
        assertEquals("Final Report Summary", result.finalReport());
        assertEquals(1, result.playerScores().size());
        assertEquals(score, result.playerScores().get(0));
    }

    @Test
    void testDbSettingsGetters() {
        // Access all the getters in DbSettings to ensure coverage of settings retrieval logic.
        assertNotNull(DbSettings.getHostFromJSON());
        assertTrue(DbSettings.getPortFromJSON() > 0);
        assertNotNull(DbSettings.getDbNameFromJSON());
        assertNotNull(DbSettings.getUserFromJSON());
        assertNotNull(DbSettings.getPasswordFromJSON());
        assertNotNull(DbSettings.getTableFromJSON());
        assertNotNull(DbSettings.isEnabledFromJSON());
        assertNotNull(DbSettings.getAutoCreateFromJSON());
        assertTrue(DbSettings.getConnectTimeoutMsFromJSON() > 0);
        assertTrue(DbSettings.getSocketTimeoutMsFromJSON() > 0);

        // Exercise the private constructor using reflection to cover it as well.
        try {
            java.lang.reflect.Constructor<DbSettings> constructor = DbSettings.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            assertNotNull(constructor.newInstance());
        } catch (Exception ignored) {
        }
    }

    @Test
    void testNoOpRepository() throws Exception {
        NoOpGameResultRepository repo = NoOpGameResultRepository.INSTANCE;
        repo.saveGameResult(null); // No-op, should return immediately

        // Call private constructor via reflection to get 100% method coverage
        java.lang.reflect.Constructor<NoOpGameResultRepository> constructor = 
            NoOpGameResultRepository.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        NoOpGameResultRepository instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    void testMySqlGameResultRepositoryEarlyExits() {
        MySqlGameResultRepository repository = new MySqlGameResultRepository(
            "jdbc:mysql://localhost:3306/mesos", "user", "pass", "table", true
        );

        // Early exits should not trigger database connection attempts
        repository.saveGameResult(null);
        repository.saveGameResult(new GameResult("id", Instant.now(), "report", null));
        repository.saveGameResult(new GameResult("id", Instant.now(), "report", Collections.emptyList()));
    }

    /**
     * Verifies transient-connection classification, retry interruption, and SQL identifier sanitization.
     */
    @Test
    void testMySqlRepositoryPrivateValidationHelpers() throws Exception {
        Method connectionCheck = MySqlGameResultRepository.class
                .getDeclaredMethod("isConnectionException", SQLException.class);
        connectionCheck.setAccessible(true);
        assertFalse((boolean) connectionCheck.invoke(null, new Object[]{null}));
        assertTrue((boolean) connectionCheck.invoke(null, new SQLException("failure", "08001")));
        assertTrue((boolean) connectionCheck.invoke(null,
                new SQLException("Communications link failure", "HY000")));
        assertFalse((boolean) connectionCheck.invoke(null, new SQLException((String) null)));
        assertFalse((boolean) connectionCheck.invoke(null, new SQLException("syntax error", "42000")));

        Method sleep = MySqlGameResultRepository.class.getDeclaredMethod("sleepBackoff", int.class);
        sleep.setAccessible(true);
        assertTrue((boolean) sleep.invoke(null, 0));
        Thread.currentThread().interrupt();
        assertFalse((boolean) sleep.invoke(null, 1));
        assertTrue(Thread.interrupted());

        Method sanitizeDatabase = MySqlGameResultRepository.class
                .getDeclaredMethod("sanitizeDatabaseName", String.class);
        sanitizeDatabase.setAccessible(true);
        assertEquals("`valid_name`", sanitizeDatabase.invoke(null, " valid_name "));
        assertThrows(Exception.class, () -> sanitizeDatabase.invoke(null, "invalid-name"));

        Method escapeLiteral = MySqlGameResultRepository.class
                .getDeclaredMethod("escapeSqlLiteral", String.class);
        escapeLiteral.setAccessible(true);
        assertEquals("", escapeLiteral.invoke(null, new Object[]{null}));
        assertEquals("a''b\\\\c", escapeLiteral.invoke(null, "a'b\\c"));
    }

    /**
     * Verifies score, report, and schema SQL operations without requiring a live database.
     */
    @Test
    void testMySqlRepositoryExecutesJdbcOperations() throws Exception {
        List<String> calls = new ArrayList<>();
        PreparedStatement preparedStatement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    if (method.getName().equals("executeBatch")) return new int[]{1};
                    if (method.getName().equals("executeUpdate")) return 1;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
        Statement statement = (Statement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    if (method.getName().equals("executeUpdate")) return 1;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    if (method.getName().equals("prepareStatement")) return preparedStatement;
                    if (method.getName().equals("createStatement")) return statement;
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });

        MySqlGameResultRepository repository = new MySqlGameResultRepository(
                "jdbc:mysql://localhost/test", "user", "pass", "scores", true);
        GameResult result = new GameResult("match", Instant.now(), "Final report",
                Collections.singletonList(new PlayerScore("Alice", 12, 3, 4, 1, true)));

        invokePrivate(repository, "insertScores",
                new Class<?>[]{Connection.class, GameResult.class}, connection, result);
        invokePrivate(repository, "insertFinalReport",
                new Class<?>[]{Connection.class, GameResult.class}, connection, result);
        invokePrivate(repository, "insertFinalReport",
                new Class<?>[]{Connection.class, GameResult.class}, connection,
                new GameResult("match", Instant.now(), null, result.playerScores()));
        invokePrivate(repository, "ensureTableExists",
                new Class<?>[]{Connection.class}, connection);
        invokePrivate(repository, "ensureReportTableExists",
                new Class<?>[]{Connection.class}, connection);

        assertTrue(calls.contains("executeBatch"));
        assertTrue(calls.contains("executeUpdate"));
        assertTrue(calls.contains("addBatch"));
    }

    /**
     * Invokes a private repository method for isolated infrastructure testing.
     *
     * @param target object receiving the invocation
     * @param name method name
     * @param parameterTypes declared parameter types
     * @param arguments invocation arguments
     * @return the method result
     */
    private static Object invokePrivate(Object target, String name, Class<?>[] parameterTypes,
                                        Object... arguments) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    @Test
    void testGameResultRepositoriesFactory() {
        // Exercise private constructor using reflection.
        try {
            java.lang.reflect.Constructor<GameResultRepositories> constructor = GameResultRepositories.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            assertNotNull(constructor.newInstance());
        } catch (Exception ignored) {
        }

        // Test with DB disabled.
        System.setProperty("mesos.db.enabled", "false");
        try {
            GameResultRepository repo = GameResultRepositories.fromEnvironment();
            assertSame(NoOpGameResultRepository.INSTANCE, repo);
        } finally {
            System.clearProperty("mesos.db.enabled");
        }

        // Test with DB enabled and defaults.
        System.setProperty("mesos.db.enabled", "true");
        System.setProperty("mesos.db.host", "127.0.0.1");
        System.setProperty("mesos.db.port", "3306");
        System.setProperty("mesos.db.name", "testdb");
        System.setProperty("mesos.db.user", "testuser");
        System.setProperty("mesos.db.password", "testpass");
        System.setProperty("mesos.db.table", "testtable");
        System.setProperty("mesos.db.autocreate", "true");
        try {
            GameResultRepository repo = GameResultRepositories.fromEnvironment();
            assertTrue(repo instanceof MySqlGameResultRepository);
        } finally {
            System.clearProperty("mesos.db.enabled");
            System.clearProperty("mesos.db.host");
            System.clearProperty("mesos.db.port");
            System.clearProperty("mesos.db.name");
            System.clearProperty("mesos.db.user");
            System.clearProperty("mesos.db.password");
            System.clearProperty("mesos.db.table");
            System.clearProperty("mesos.db.autocreate");
        }

        // Test with JDBC URL provided directly.
        System.setProperty("mesos.db.enabled", "true");
        System.setProperty("mesos.db.url", "jdbc:mysql://127.0.0.1:3306/customdb?user=foo&password=bar");
        try {
            GameResultRepository repo = GameResultRepositories.fromEnvironment();
            assertTrue(repo instanceof MySqlGameResultRepository);
        } finally {
            System.clearProperty("mesos.db.enabled");
            System.clearProperty("mesos.db.url");
        }
    }
}
