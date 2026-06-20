package it.polimi.Game.Persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * JDBC-based repository that stores match results in a MySQL database.
 */
public final class MySqlGameResultRepository implements GameResultRepository {

    private static final String DEFAULT_TABLE = "mesos_game_scores";
    private static final String DEFAULT_REPORT_TABLE_SUFFIX = "_reports";
    private static final int[] CONNECT_RETRY_BACKOFF_MS = new int[]{300, 800, 1500};

    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final String tableName;
    private final String reportTableName;
    private final boolean autoCreateTable;

    /**
     * Creates a MySQL-backed game-result repository.
     *
     * @param jdbcUrl JDBC URL to connect to MySQL.
     * @param user database username.
     * @param password database password.
     * @param tableName table name to store scores into.
     * @param autoCreateTable if true, attempts to create the table if missing.
     */
    public MySqlGameResultRepository(String jdbcUrl, String user, String password, String tableName, boolean autoCreateTable) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.tableName = sanitizeTableName(tableName, DEFAULT_TABLE);
        this.reportTableName = sanitizeTableName(this.tableName + DEFAULT_REPORT_TABLE_SUFFIX, DEFAULT_TABLE + DEFAULT_REPORT_TABLE_SUFFIX);
        this.autoCreateTable = autoCreateTable;
    }

    /**
     * Persists the final match result and player scores into the MySQL database.
     * Retries database connection up to maxAttempts if the initial attempt fails.
     *
     * @param result The final match game result to save.
     */
    @Override
    public void saveGameResult(GameResult result) {
        if (result == null || result.playerScores() == null || result.playerScores().isEmpty()) {
            return;
        }

        int maxAttempts = 1 + CONNECT_RETRY_BACKOFF_MS.length;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
                if (autoCreateTable) {
                    ensureTableExists(connection);
                    ensureReportTableExists(connection);
                }
                insertScores(connection, result);
                insertFinalReport(connection, result);
                System.out.println("[DB] Saved match scores matchId=" + result.matchId()
                        + " players=" + result.playerScores().size()
                        + " table=" + tableName);
                return;
            } catch (SQLException e) {
                boolean connectionIssue = isConnectionException(e);
                System.err.println("[DB] Could not persist game results"
                        + " attempt=" + attempt + "/" + maxAttempts
                        + " connectionIssue=" + connectionIssue
                        + " message=" + e.getMessage()
                        + " sqlState=" + e.getSQLState()
                        + " errorCode=" + e.getErrorCode());

                if (!connectionIssue || attempt == maxAttempts) {
                    e.printStackTrace();
                    return;
                }

                int backoffIndex = attempt - 1;
                if (backoffIndex >= 0 && backoffIndex < CONNECT_RETRY_BACKOFF_MS.length) {
                    if (!sleepBackoff(CONNECT_RETRY_BACKOFF_MS[backoffIndex])) {
                        System.err.println("[DB] Retry interrupted; aborting persistence.");
                        return;
                    }
                }
            } catch (Exception e) {
                System.err.println("[DB] Unexpected error while persisting game results: " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }
    }

    /**
     * Pauses before a connection retry while preserving interruption status.
     *
     * @param backoffMs delay in milliseconds
     * @return {@code true} when the delay completed, or {@code false} when interrupted
     */
    private static boolean sleepBackoff(int backoffMs) {
        try {
            Thread.sleep(backoffMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Determines whether an SQL exception represents a transient connection failure.
     *
     * @param e exception to inspect
     * @return {@code true} when retrying the connection may succeed
     */
    private static boolean isConnectionException(SQLException e) {
        if (e == null) {
            return false;
        }

        String sqlState = e.getSQLState();
        if (sqlState != null && sqlState.startsWith("08")) {
            return true;
        }

        String message = e.getMessage();
        if (message == null) {
            return false;
        }

        String lower = message.toLowerCase();
        return lower.contains("communications link failure")
                || lower.contains("driver has not received any packets")
                || lower.contains("connection timed out")
                || lower.contains("connect timed out")
                || lower.contains("connection refused")
                || lower.contains("connection reset")
                || lower.contains("broken pipe");
    }

    /**
     * Inserts all player scores for one match as a JDBC batch.
     *
     * @param connection active database connection
     * @param result result containing the scores
     * @throws SQLException if the batch cannot be executed
     */
    private void insertScores(Connection connection, GameResult result) throws SQLException {
        String sql = "INSERT INTO " + tableName + " (match_id, ended_at, player_nickname, prestige_points, food, endgame_bonus, player_rank, is_winner)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (PlayerScore score : result.playerScores()) {
                ps.setString(1, result.matchId());
                ps.setTimestamp(2, Timestamp.from(result.endedAt()));
                ps.setString(3, score.nickname());
                ps.setInt(4, score.prestigePoints());
                ps.setInt(5, score.food());
                ps.setInt(6, score.endgameBonus());
                ps.setInt(7, score.rank());
                ps.setBoolean(8, score.winner());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Stores the optional textual final report for a match.
     *
     * @param connection active database connection
     * @param result result containing the report
     * @throws SQLException if the report cannot be inserted
     */
    private void insertFinalReport(Connection connection, GameResult result) throws SQLException {
        if (result.finalReport() == null) {
            return;
        }

        String sql = "INSERT INTO " + reportTableName + " (match_id, ended_at, final_report) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, result.matchId());
            ps.setTimestamp(2, Timestamp.from(result.endedAt()));
            ps.setString(3, result.finalReport());
            ps.executeUpdate();
        }
    }

    /**
     * Creates the score table when it does not already exist.
     *
     * @param connection active database connection
     * @throws SQLException if the table cannot be created
     */
    private void ensureTableExists(Connection connection) throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "match_id CHAR(36) NOT NULL,"
                + "ended_at TIMESTAMP NOT NULL,"
                + "player_nickname VARCHAR(64) NOT NULL,"
                + "prestige_points INT NOT NULL,"
                + "food INT NOT NULL,"
                + "endgame_bonus INT NOT NULL,"
                + "player_rank INT NOT NULL,"
                + "is_winner BOOLEAN NOT NULL,"
                + "PRIMARY KEY (match_id, player_nickname),"
                + "INDEX idx_ended_at (ended_at)"
                + ")";

        try (Statement st = connection.createStatement()) {
            st.executeUpdate(ddl);
        }
    }

    /**
     * Creates the final-report table when it does not already exist.
     *
     * @param connection active database connection
     * @throws SQLException if the table cannot be created
     */
    private void ensureReportTableExists(Connection connection) throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS " + reportTableName + " ("
                + "match_id CHAR(36) NOT NULL,"
                + "ended_at TIMESTAMP NOT NULL,"
                + "final_report TEXT NOT NULL,"
                + "PRIMARY KEY (match_id),"
                + "INDEX idx_ended_at (ended_at)"
                + ")";

        try (Statement st = connection.createStatement()) {
            st.executeUpdate(ddl);
        }
    }

    /**
     * Ensures that the MySQL database exists. Creates it if it does not.
     * Connects to the MySQL server without specifying a database, then creates the database if missing.
     *
     * @param host MySQL host
     * @param port MySQL port
     * @param user MySQL user
     * @param password MySQL password
     * @param dbName Database name to create
     * @param connectTimeoutMs Connection timeout in ms
     * @param socketTimeoutMs Socket timeout in ms
     */
    public static void ensureDatabaseExists(String host, int port, String user, String password, String dbName, int connectTimeoutMs, int socketTimeoutMs) {
        // Build JDBC URL without database name for initial connection
        String adminUrl = "jdbc:mysql://" + host + ":" + port
                + "?useSSL=false"
                + "&useUnicode=true"
                + "&characterEncoding=utf8"
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC"
                + "&connectTimeout=" + Math.max(0, connectTimeoutMs)
                + "&socketTimeout=" + Math.max(0, socketTimeoutMs);

        try (Connection connection = DriverManager.getConnection(adminUrl, user, password)) {
            // Check if database exists using INFORMATION_SCHEMA
            String checkDbSql = "SELECT 1 FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?";
            try (PreparedStatement ps = connection.prepareStatement(checkDbSql)) {
                ps.setString(1, dbName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        // Database doesn't exist, create it
                        String createDbSql = "CREATE DATABASE IF NOT EXISTS " + sanitizeDatabaseName(dbName);
                        try (Statement st = connection.createStatement()) {
                            st.executeUpdate(createDbSql);
                            System.out.println("[DB] Created database: " + dbName);
                        }
                    }
                }
            }

            ensureDatabasePrivileges(connection, dbName, user);
            System.out.println("[DB] Database ready: " + dbName);
        } catch (SQLException e) {
            System.err.println("[DB] Error ensuring database exists: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Grants the connected account access to the game database when permitted.
     *
     * @param connection active server connection
     * @param dbName database receiving the grant
     * @param fallbackUser configured user used when MySQL cannot resolve the current account
     */
    private static void ensureDatabasePrivileges(Connection connection, String dbName, String fallbackUser) {
        String currentUser = fallbackUser;
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT CURRENT_USER()")) {
            if (rs.next()) {
                String value = rs.getString(1);
                if (value != null && !value.isBlank()) {
                    currentUser = value.trim();
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Could not resolve CURRENT_USER(); using configured user for grants: " + e.getMessage());
        }

        String[] accountParts = currentUser.split("@", 2);
        String accountName = accountParts.length > 0 ? accountParts[0].trim() : fallbackUser;
        String accountHost = accountParts.length > 1 ? accountParts[1].trim() : "%";

        if (accountName.isEmpty()) {
            accountName = fallbackUser;
        }
        if (accountHost.isEmpty()) {
            accountHost = "%";
        }

        String grantSql = "GRANT ALL PRIVILEGES ON " + sanitizeDatabaseName(dbName)
                + ".* TO '" + escapeSqlLiteral(accountName) + "'@'" + escapeSqlLiteral(accountHost) + "'";

        try (Statement st = connection.createStatement()) {
            st.executeUpdate(grantSql);
            System.out.println("[DB] Granted privileges on database " + dbName + " to " + accountName + "@" + accountHost);
        } catch (SQLException e) {
            System.err.println("[DB] Could not grant privileges on database " + dbName + ": " + e.getMessage());
        }
    }

    /**
     * Validates and quotes a database identifier.
     *
     * @param raw identifier to validate
     * @return the validated identifier enclosed in backticks
     * @throws IllegalArgumentException if the identifier is empty or contains unsupported characters
     */
    private static String sanitizeDatabaseName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Database name cannot be null or empty");
        }

        String trimmed = raw.trim();
        // Allow alphanumeric and underscore only
        if (!trimmed.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid database name: " + raw);
        }

        return "`" + trimmed + "`";
    }

    /**
     * Escapes backslashes and single quotes for a SQL string literal.
     *
     * @param raw value to escape
     * @return the escaped value, or an empty string for {@code null}
     */
    private static String escapeSqlLiteral(String raw) {
        if (raw == null) {
            return "";
        }

        return raw.replace("\\", "\\\\").replace("'", "''");
    }

    /**
     * Validates a table identifier and substitutes a fallback when invalid.
     *
     * @param raw identifier to validate
     * @param fallback identifier used for null, blank, or invalid input
     * @return the validated identifier or fallback
     */
    private static String sanitizeTableName(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }

        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }

        if (!trimmed.matches("[A-Za-z0-9_]+")) {
            return fallback;
        }

        return trimmed;
    }
}
