package it.polimi.Network.Client;

import it.polimi.Network.Common.SerializedUpdate;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders server updates and prompts for the console-based client UI.
 */
final class ConsoleTuiRenderer {
    private static final String PROMPT = "> ";
    private static final String CLEAR_CURRENT_LINE = "\r  \r";
    private static final Pattern IMG_ID_PATTERN = Pattern.compile("\\{\\s*img\\s*=\\s*(\\d+)\\s*\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_METADATA_PATTERN = Pattern.compile("\\{\\s*img\\s*[:=]\\s*[^}]*}", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTING_ROW_PATTERN = Pattern.compile(
            "^(?:Acting|Now acting):\\s*(.+?)\\s*\\(tile\\s+[^)]*\\)\\s*remaining\\s+upper=(\\d+)\\s+lower=(\\d+).*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUND_PATTERN = Pattern.compile("\\bround=(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ERA_PATTERN = Pattern.compile("\\bera=([^,\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INITIALIZED_PATTERN = Pattern.compile("\\binitialized=([^,\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_TILE_PATTERN = Pattern.compile(
            "^(.+?)\\s+color=\\S+.*\\stile=([^\\s]+)(?:\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIRST_COLOR_PLAYER_PATTERN = Pattern.compile(
            "All players ready\\.\\s+(.+?)\\s+chooses color first\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_COLOR_PLAYER_PATTERN = Pattern.compile(
            "Next color choice:\\s*([^\\r\\n.]+)", Pattern.CASE_INSENSITIVE);

    private final PrintStream out;
    private final Object lock = new Object();
    private volatile boolean forceNextStatusRender = false;
    private volatile String pendingCommand;
    private boolean promptVisible;
    private String localNickname;
    private String lastStatusContent;
    private final Map<String, List<Integer>> takenCardsByPlayer = new HashMap<>();
    private final List<String> localTakenNotes = new ArrayList<>();

    ConsoleTuiRenderer() {
        this(System.out);
    }

    ConsoleTuiRenderer(PrintStream out) {
        this.out = out == null ? System.out : out;
    }

    /**
     * Prints a plain line in a thread-safe way.
     *
     * @param message line to print.
     */
    void printLine(String message) {
        synchronized (lock) {
            out.println(stripImageMetadata(message));
        }
    }

    /**
     * Prints the interactive command prompt.
     */
    void renderPrompt() {
        synchronized (lock) {
            printPromptLocked();
        }
    }

    /**
     * Marks a command as sent so the next matching response can restore the prompt.
     *
     * @param command command entered by the local player
     */
    void markCommandSent(String command) {
        pendingCommand = commandName(command);
        synchronized (lock) {
            promptVisible = false;
        }
    }

    /**
     * Prints the local TUI command reference.
     */
    void renderHelp() {
        synchronized (lock) {
            out.println();
            out.println("--- Mesos TUI Help ---");
            out.println("help");
            out.println("  Shows this command reference.");
            out.println("ready");
            out.println("  Marks you ready in the lobby.");
            out.println("unready");
            out.println("  Removes your ready state and returns you to the lobby before the match starts.");
            out.println("color <name>");
            out.println("  Chooses your player color during color selection. Example: color blue.");
            out.println("status");
            out.println("  Requests the current game state, including players, turn, rows, and offer tiles.");
            out.println("stats");
            out.println("  Shows your prestige points and food.");
            out.println("stats <player>");
            out.println("  Shows another player's prestige points and food.");
            out.println("hand");
            out.println("  Requests your character and building cards from the server.");
            out.println("hand <player>");
            out.println("  Requests another player's character and building cards from the server.");
            out.println("player");
            out.println("  Shows the nickname of the logged-in player.");
            out.println("totem <letter>");
            out.println("  Places your totem on an offer tile. Example: totem A.");
            out.println("pick <upper|lower> <t|b> <index>");
            out.println("  Picks a card while resolving actions. Use t for tribe, b for building. Example: pick upper t 0.");
            out.println("quit");
            out.println("  Leaves the client.");
            out.println();
            out.print(PROMPT);
            out.flush();
            promptVisible = true;
        }
    }

    /**
     * Renders a structured update coming from the server.
     *
     * @param update update payload.
     */
    void renderServerUpdate(SerializedUpdate update) {
        if (update == null) {
            return;
        }

        String type = normalizeType(update.getType());
        String content = update.getContent() == null ? "" : update.getContent();
        String visibleContent = stripImageMetadata(content);
        String prefix = "[" + type + "]";

        synchronized (lock) {
            boolean showPrompt = false;

            switch (type) {
                case "LOGIN_REQUEST":
                    prepareUpdateLineLocked();
                    out.println(prefix + " " + visibleContent);
                    break;
                case "LOGIN_RESULT":
                    prepareUpdateLineLocked();
                    if (update.isSuccess()) {
                        out.println(prefix + " Login Successful: " + visibleContent);
                    } else {
                        out.println(prefix + " Login Failed: " + visibleContent);
                    }
                    break;
                case "NOTIFICATION":
                    prepareUpdateLineLocked();
                    updateHandFromNotification(content);
                    String notification = appendNotificationHint(visibleContent);
                    out.println(prefix);
                    out.println(notification);
                    consumePendingResponse("NOTIFICATION");
                    showPrompt = true;
                    break;
                case "STATUS_UPDATE":
                    if (isCompleteStatusSnapshot(content)) {
                        lastStatusContent = content;
                        updateTakenCardsFromStatus(content);
                        String summary = buildStatusSummary(visibleContent);
                        if (forceNextStatusRender || !isLobbyStatusSnapshot(content)) {
                            prepareUpdateLineLocked();
                            out.println(summary);
                            showPrompt = true;
                        }
                        consumePendingResponse("STATUS_UPDATE");
                    }
                    forceNextStatusRender = false;
                    break;
                case "ERROR":
                    prepareUpdateLineLocked();
                    out.println("[ERROR] " + visibleContent);
                    consumePendingResponse("ERROR");
                    showPrompt = true;
                    break;
                default:
                    prepareUpdateLineLocked();
                    out.println(prefix + " Received: " + visibleContent);
                    consumePendingResponse(type);
                    showPrompt = true;
                    break;
            }

            if (showPrompt && !promptVisible) {
                printPromptLocked();
            }
        }
    }

    /**
     * Renders a raw (non-JSON) server line.
     *
     * @param rawLine non-parsed line received from server.
     */
    void renderRawServerLine(String rawLine) {
        synchronized (lock) {
            prepareUpdateLineLocked();
            out.println("[SERVER RAW]: " + stripImageMetadata(rawLine));
            printPromptLocked();
        }
    }

    /**
     * Renders a local client-side error after an action send attempt.
     *
     * @param message error content.
     */
    void renderClientError(String message) {
        synchronized (lock) {
            prepareUpdateLineLocked();
            out.println("[ERROR] " + stripImageMetadata(message == null ? "Unknown error." : message));
            printPromptLocked();
        }
    }

    /**
     * Marks the next STATUS_UPDATE as manually requested so lobby snapshots are rendered.
     */
    void markManualStatusRequest() {
        forceNextStatusRender = true;
    }

    /**
     * Sets the local nickname so the TUI can show per-player hand data.
     *
     * @param nickname local player nickname
     */
    void setLocalNickname(String nickname) {
        this.localNickname = nickname;
    }

    /**
     * Renders the local player's hand from the last known server data.
     */
    void renderHand() {
        synchronized (lock) {
            out.println();
            out.println("--- Your Hand ---");

            List<Integer> ids = null;
            if (localNickname != null && !localNickname.isBlank()) {
                ids = takenCardsByPlayer.get(localNickname.trim().toLowerCase());
            }

            if (ids != null && !ids.isEmpty()) {
                out.println("Cards in hand: " + ids.size());
                if (!localTakenNotes.isEmpty()) {
                    out.println("Recent takes:");
                    for (String note : localTakenNotes) {
                        out.println("  " + note);
                    }
                }
            } else if (!localTakenNotes.isEmpty()) {
                out.println("Cards:");
                for (String note : localTakenNotes) {
                    out.println("  " + note);
                }
            } else {
                out.println("No cards in hand yet.");
                if (lastStatusContent == null || lastStatusContent.isBlank()) {
                    out.println("Tip: use 'status' to request a fresh snapshot.");
                }
            }

            out.print(PROMPT);
            out.flush();
            promptVisible = true;
        }
    }

    /** Displays the nickname associated with the current terminal session. */
    void renderPlayerIdentity() {
        synchronized (lock) {
            out.println();
            if (localNickname == null || localNickname.isBlank()) {
                out.println("No player is currently logged in.");
            } else {
                out.println("Logged in player: " + localNickname);
            }
            printPromptLocked();
        }
    }

    /**
     * Renders a connection-lost notification.
     */
    void renderConnectionLost() {
        synchronized (lock) {
            out.println();
            out.println("Connection to server lost.");
        }
    }

    /**
     * Normalizes a server update type string for consistent display.
     * Converts to uppercase and handles null or empty strings by returning "UNKNOWN".
     *
     * @param type the update type string to normalize
     * @return the uppercase normalized type, or "UNKNOWN" if type is null or empty
     */
    private String normalizeType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "UNKNOWN";
        }
        return type.trim().toUpperCase();
    }

    /** Prints the input prompt while the renderer lock is held. */
    private void printPromptLocked() {
        out.print(PROMPT);
        out.flush();
        promptVisible = true;
    }

    /** Clears the current terminal line while the renderer lock is held. */
    private void clearCurrentLineLocked() {
        out.print(CLEAR_CURRENT_LINE);
        out.flush();
    }

    /** Prepares the current terminal line for one visible server update. */
    private void prepareUpdateLineLocked() {
        if (promptVisible) {
            clearCurrentLineLocked();
        }
        promptVisible = false;
        out.println();
    }

    /**
     * Extracts the command verb from a complete command line.
     *
     * @param command complete command line
     * @return lowercase command verb, or an empty string for blank input
     */
    private String commandName(String command) {
        if (command == null || command.isBlank()) return "";
        return command.trim().toLowerCase().split("\\s+", 2)[0];
    }

    /**
     * Matches an incoming update with the command currently awaiting a response.
     *
     * @param updateType normalized server update type
     * @return {@code true} when the update completes the pending command
     */
    private boolean consumePendingResponse(String updateType) {
        String command = pendingCommand;
        if (command == null) return false;
        boolean matches = "ERROR".equals(updateType)
                || ("status".equals(command) && "STATUS_UPDATE".equals(updateType))
                || (!"status".equals(command) && "NOTIFICATION".equals(updateType));
        if (matches) pendingCommand = null;
        return matches;
    }

    /**
     * Stores card-taking messages for the local hand summary.
     *
     * @param content notification payload
     */
    private void updateHandFromNotification(String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, "Taken:", 0, 6)) {
                localTakenNotes.add(stripImageMetadata(trimmed));
            }
        }
    }

    /**
     * Rebuilds per-player card identifiers from a complete status snapshot.
     *
     * @param content status snapshot
     */
    private void updateTakenCardsFromStatus(String content) {
        takenCardsByPlayer.clear();
        if (content == null || content.isBlank()) {
            return;
        }

        boolean inSection = false;
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (!inSection) {
                if ("Taken cards:".equalsIgnoreCase(trimmed)) {
                    inSection = true;
                }
                continue;
            }

            if (trimmed.isEmpty()) {
                break;
            }

            int idx = trimmed.toLowerCase().indexOf(" cards=");
            if (idx <= 0) {
                continue;
            }

            String nick = trimmed.substring(0, idx).trim().toLowerCase();
            List<Integer> ids = extractAllAssetIds(trimmed);
            takenCardsByPlayer.put(nick, ids);
        }
    }

    /**
     * Extracts every numeric image asset identifier from text.
     *
     * @param text text containing image metadata
     * @return asset identifiers in encounter order
     */
    private List<Integer> extractAllAssetIds(String text) {
        List<Integer> ids = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return ids;
        }

        Matcher matcher = IMG_ID_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                ids.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // ignore invalid tags
            }
        }
        return ids;
    }

    /**
     * Formats asset identifiers as zero-padded comma-separated values.
     *
     * @param ids identifiers to format
     * @return formatted identifier list
     */
    private String joinAssetIds(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%03d", ids.get(i)));
        }
        return sb.toString();
    }

    /**
     * Removes GUI-only image identifiers from text displayed by the terminal client.
     *
     * @param text source text
     * @return text without {@code img} metadata blocks
     */
    private String stripImageMetadata(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return IMG_METADATA_PATTERN.matcher(text).replaceAll("").replaceAll("[ \\t]+(?=\\R|$)", "");
    }

    /**
     * Appends a color-selection hint when a notification targets the local player.
     *
     * @param message notification body
     * @return notification body with any applicable local hint
     */
    private String appendNotificationHint(String message) {
        if (message == null || message.isBlank() || localNickname == null) {
            return message == null ? "" : message;
        }

        Matcher nextColor = NEXT_COLOR_PLAYER_PATTERN.matcher(message);
        if (nextColor.find() && isLocalPlayer(nextColor.group(1))) {
            return message + "\nHint: use 'color <name>' to choose an available color.";
        }

        Matcher firstColor = FIRST_COLOR_PLAYER_PATTERN.matcher(message);
        if (firstColor.find() && isLocalPlayer(firstColor.group(1))) {
            return message + "\nHint: use 'color <name>' to choose an available color.";
        }

        return message;
    }

    /**
     * Distinguishes controller snapshots from compact model change payloads such as
     * {@code Game{round=1,...}}, which do not contain enough data for the TUI.
     */
    private boolean isCompleteStatusSnapshot(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        for (String line : content.split("\\R")) {
            if (line.trim().startsWith("Status:")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether a complete status snapshot represents the pre-game lobby.
     *
     * @param content status snapshot
     * @return {@code true} when the game has not been initialized
     */
    private boolean isLobbyStatusSnapshot(String content) {
        if (content == null) {
            return false;
        }
        Matcher matcher = INITIALIZED_PATTERN.matcher(content);
        return matcher.find() && !Boolean.parseBoolean(matcher.group(1));
    }

    /**
     * Converts a controller status snapshot into a concise terminal representation.
     *
     * @param content complete controller status snapshot
     * @return formatted terminal status
     */
    private String buildStatusSummary(String content) {
        if (content == null || content.isBlank()) {
            return "[STATUS_UPDATE] (empty)";
        }

        String round = "?";
        String era = "?";
        String phase = null;
        String offerTiles = null;
        String availableTiles = null;
        String acting = null;
        String remainingUpper = null;
        String remainingLower = null;
        String toPlace = null;

        boolean initialized = true;
        String requiredPlayers = null;
        String connectedPlayers = null;
        String readyPlayers = null;
        String readyCount = null;
        String colorTurn = null;
        String availableColors = null;
        String chosenColors = null;

        List<String> upperTribe = new ArrayList<>();
        List<String> upperBuildings = new ArrayList<>();
        List<String> lowerTribe = new ArrayList<>();
        List<String> lowerBuildings = new ArrayList<>();
        List<String> placedTotems = new ArrayList<>();

        String currentSection = null;

        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                currentSection = null;
                continue;
            }

            if (line.startsWith("Status:")) {
                Matcher roundMatch = ROUND_PATTERN.matcher(line);
                if (roundMatch.find()) {
                    round = roundMatch.group(1);
                }
                Matcher eraMatch = ERA_PATTERN.matcher(line);
                if (eraMatch.find()) {
                    era = eraMatch.group(1);
                }
                Matcher initMatch = INITIALIZED_PATTERN.matcher(line);
                if (initMatch.find()) {
                    initialized = Boolean.parseBoolean(initMatch.group(1));
                }
                continue;
            }

            if (line.startsWith("Phase:")) {
                phase = line.substring(line.indexOf(':') + 1).trim();
                continue;
            }

            Matcher actingMatch = ACTING_ROW_PATTERN.matcher(line);
            if (actingMatch.matches()) {
                acting = actingMatch.group(1).trim();
                remainingUpper = actingMatch.group(2);
                remainingLower = actingMatch.group(3);
                continue;
            }

            String lower = line.toLowerCase();

            Matcher playerTileMatch = PLAYER_TILE_PATTERN.matcher(line);
            if (playerTileMatch.matches() && !"-".equals(playerTileMatch.group(2))) {
                placedTotems.add(playerTileMatch.group(1).trim() + "=" + playerTileMatch.group(2));
                continue;
            }

            if (lower.startsWith("to place:")) {
                toPlace = line.substring(line.indexOf(':') + 1).trim();
                continue;
            }
            if (lower.startsWith("available tiles:")) {
                availableTiles = line.substring(line.indexOf(':') + 1).trim();
                continue;
            }
            if (lower.startsWith("offer tiles:")) {
                offerTiles = line.substring(line.indexOf(':') + 1).trim();
                continue;
            }

            if (lower.startsWith("required players:")) {
                requiredPlayers = line.substring(line.indexOf(':') + 1).trim();
                continue;
            }
            if (lower.startsWith("connected players:")) {
                connectedPlayers = line.substring(line.indexOf(':') + 1).trim();
                continue;
            }
            if (lower.startsWith("ready players:")) {
                readyPlayers = line.substring(line.indexOf(':') + 1).trim();
                continue;
            }
            if (lower.startsWith("ready count:")) {
                readyCount = line.substring(line.indexOf(':') + 1).trim();
                continue;
            }
            if (lower.startsWith("color selection turn:")) {
                colorTurn = line.substring(line.indexOf(':') + 1).trim();
                continue;
            }
            if (lower.startsWith("available colors:")) {
                availableColors = line.substring(line.indexOf(':') + 1).trim();
                continue;
            }
            if (lower.startsWith("chosen colors:")) {
                chosenColors = line.substring(line.indexOf(':') + 1).trim();
                continue;
            }

            if ("Upper tribe row:".equalsIgnoreCase(line)) {
                currentSection = "upperTribe";
                continue;
            }
            if ("Upper buildings:".equalsIgnoreCase(line)) {
                currentSection = "upperBuildings";
                continue;
            }
            if ("Lower tribe row:".equalsIgnoreCase(line)) {
                currentSection = "lowerTribe";
                continue;
            }
            if ("Lower buildings:".equalsIgnoreCase(line)) {
                currentSection = "lowerBuildings";
                continue;
            }

            if (currentSection != null) {
                if ("upperTribe".equals(currentSection)) {
                    upperTribe.add(line);
                } else if ("upperBuildings".equals(currentSection)) {
                    upperBuildings.add(line);
                } else if ("lowerTribe".equals(currentSection)) {
                    lowerTribe.add(line);
                } else if ("lowerBuildings".equals(currentSection)) {
                    lowerBuildings.add(line);
                }
            }
        }

        if (!initialized) {
            StringBuilder lobby = new StringBuilder("[STATUS_UPDATE] Lobby");
            if (requiredPlayers != null) lobby.append("\nRequired players: ").append(requiredPlayers);
            if (connectedPlayers != null) lobby.append("\nConnected players: ").append(connectedPlayers);
            if (readyPlayers != null) lobby.append("\nReady players: ").append(readyPlayers);
            if (readyCount != null) lobby.append("\nReady count: ").append(readyCount);
            if (colorTurn != null && !colorTurn.isBlank() && !"(none)".equalsIgnoreCase(colorTurn)) {
                lobby.append("\nColor selection: ").append(colorTurn);
                if (availableColors != null) {
                    lobby.append("\nAvailable colors: ").append(availableColors);
                }
                if (chosenColors != null) {
                    lobby.append("\nChosen colors: ").append(chosenColors);
                }
                lobby.append("\nHint: use 'color <name>' when it is your turn.");
            } else {
                lobby.append("\nHint: use 'ready' when you are ready.");
            }
            return lobby.toString();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[STATUS_UPDATE]");
        sb.append(" Round ").append(round).append(" | Era ").append(era);
        if (phase != null && !phase.isBlank()) {
            sb.append(" | Phase: ").append(phase);
        }

        String tiles = availableTiles != null ? availableTiles : offerTiles;
        if (tiles != null) {
            sb.append("\nOffer tiles: ").append(tiles);
        }

        if (toPlace != null && !toPlace.isBlank()) {
            sb.append("\nTotem placement: ").append(toPlace);
        }
        if (!placedTotems.isEmpty()) {
            sb.append("\nPlaced totems: ").append(String.join(", ", placedTotems));
        }
        if (acting != null && !acting.isBlank()) {
            sb.append("\nActing: ").append(acting);
            if (remainingUpper != null && remainingLower != null) {
                sb.append(" (remaining upper=").append(remainingUpper)
                        .append(", lower=").append(remainingLower).append(")");
            }
        }

        if (!upperTribe.isEmpty()) {
            sb.append("\n\nUpper tribe row:");
            for (String line : upperTribe) {
                sb.append("\n  ").append(line);
            }
        }
        if (!upperBuildings.isEmpty()) {
            sb.append("\n\nUpper buildings:");
            for (String line : upperBuildings) {
                sb.append("\n  ").append(line);
            }
        }
        if (!lowerTribe.isEmpty()) {
            sb.append("\n\nLower tribe row:");
            for (String line : lowerTribe) {
                sb.append("\n  ").append(line);
            }
        }
        if (!lowerBuildings.isEmpty()) {
            sb.append("\n\nLower buildings:");
            for (String line : lowerBuildings) {
                sb.append("\n  ").append(line);
            }
        }

        if (acting != null && isLocalPlayer(acting)) {
            sb.append("\n\nHint: use 'pick <upper|lower> <t|b> <index>' to choose a card.");
        }

        if (toPlace != null && isLocalPlayer(toPlace)) {
            if (tiles != null && !tiles.isBlank()) {
                sb.append("\n\nAvailable offer tiles: ").append(tiles);
            }
            sb.append("\nHint: use 'totem <letter>' to choose an available tile.");
        }

        return sb.toString();
    }

    /**
     * Compares a nickname with the player authenticated in this terminal session.
     *
     * @param nickname nickname to compare
     * @return {@code true} when the nickname belongs to the local player
     */
    private boolean isLocalPlayer(String nickname) {
        return localNickname != null
                && nickname != null
                && localNickname.trim().equalsIgnoreCase(nickname.trim());
    }
}
