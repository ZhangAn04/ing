package it.polimi.Game.Core;

import it.polimi.Buildings.EndGame_Builder_Add;
import it.polimi.Buildings.Endgame_Effect;
import it.polimi.Game.Elements.OfferTile;
import it.polimi.Game.Elements.Player;
import it.polimi.Game.Elements.Tribe;
import it.polimi.Abstract.Building;
import it.polimi.Abstract.Card;
import it.polimi.Abstract.Character;
import it.polimi.Characters.Builder;
import it.polimi.Characters.Gatherer;
import it.polimi.Characters.Hunter;
import it.polimi.Characters.Inventor;
import it.polimi.Characters.Shaman;
import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Color;
import it.polimi.Constants.InventorIcon;
import it.polimi.Events.Hunting;
import it.polimi.Events.RockPaintings;
import it.polimi.Events.ShamanicRitual;
import it.polimi.Events.Sustenance;
import it.polimi.Game.Persistence.GameResult;
import it.polimi.Game.Persistence.GameResultRepository;
import it.polimi.Game.Persistence.NoOpGameResultRepository;
import it.polimi.Game.Persistence.PlayerScore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates incoming commands from the network layer and translates them into operations on the {@link Game} model.
 * <p>
 * This controller acts as the central command processor in the MVC architecture. It is designed 
 * to be lightweight, as the high-level game flow is driven by client actions received via the 
 * network. It ensures that commands are parsed correctly and executed only when the game 
 * state allows (e.g., checking initialization).
 * </p>
 * <p>
 * It also manages "Thinking Timers" to ensure the game progresses if a player is inactive.
 * </p>
 */
public class GameController {
    /** The threshold for a player to make a decision (2 minutes). */
    private static final long THINKING_TIME_LIMIT = 120;

    /** Default time allowed for another player to reconnect before the sole online player wins. */
    private static final long DEFAULT_DISCONNECTION_TIMEOUT_SECONDS = 120;

    private static final int MIN_READY_PLAYERS = 2;

    /** The central game model instance managed by this controller. */
    private final Game game;
    
    /** Indicates whether the game has been properly initialized with players and decks. */
    private boolean initialized;

    /** True once end-game scoring has been applied and winners determined. */
    private boolean gameFinished;

    /** Cached final report to keep output stable if queried multiple times. */
    private String finalReport;

    /** Repository used to persist final match scores (no-op when disabled). */
    private final GameResultRepository gameResultRepository;

    /** Unique identifier for this match instance. */
    private final String matchId;

    /** Turn-order track rules loaded from start.json, indexed by player count. */
    private final Map<Integer, CardLoader.TurnOrderTrackRule[]> turnOrderTrackRules;

    /**
     * Interactive phases used to gate player commands during a match.
     */
    private enum InteractivePhase {
        LOBBY,
        SELECTING_COLORS,
        WAITING_FOR_PLAYERS,
        PLACING_TOTEMS,
        RESOLVING_ACTIONS,
        RESOLVING_END_ROUND_CARDS,
        FINISHED
    }

    private InteractivePhase phase = InteractivePhase.LOBBY;

    private Integer expectedPlayers;
    private Integer requiredPlayers;
    private final List<String> pendingNicknames = new ArrayList<>();
    private final List<String> readyNicknames = new ArrayList<>();
    private final List<Color> availableColors = new ArrayList<>();
    private final Map<String, Color> selectedColorsByNickname = new HashMap<>();
    private final Map<String, Player> nicknameToPlayer = new HashMap<>();

    private final ThreadLocal<String> actingNickname = new ThreadLocal<>();

    private List<Player> turnOrder = new ArrayList<>();
    private int placementIndex;

    private List<Player> actionOrder = new ArrayList<>();
    private int actionIndex;
    private int remainingUpperPicks;
    private int remainingLowerPicks;
    private final List<Player> nextRoundOrder = new ArrayList<>();
    private final List<Player> endRoundCardOrder = new ArrayList<>();
    private int endRoundCardIndex;

    /** Executor service for managing background turn timers. */
    private final ScheduledExecutorService turnTimerExecutor = Executors.newSingleThreadScheduledExecutor();

    /** Reference to the currently active turn timer task. */
    private ScheduledFuture<?> activeTurnTask;

    /** Duration of the single-player suspension timeout in milliseconds. */
    private final long disconnectionTimeoutMillis;

    /** Reference to the timeout that awards victory to the sole connected player. */
    private ScheduledFuture<?> disconnectionTimeoutTask;

    /** Periodic task that publishes the remaining reconnection time. */
    private ScheduledFuture<?> disconnectionCountdownTask;

    /** Absolute deadline of the active reconnection window, in epoch milliseconds. */
    private long disconnectionDeadlineMillis;

    /** Whether game actions are suspended because fewer than two players are online. */
    private boolean suspendedForDisconnection;

    /** Nickname of the sole online player for whom the current timeout was scheduled. */
    private String timeoutCandidateNickname;

    /**
     * Constructs a new GameController bounded to a specific game instance.
     *
     * @param game The {@link Game} instance to be managed.
     * @throws IllegalArgumentException if the provided game is null.
     */
    public GameController(Game game) {
        this(game, NoOpGameResultRepository.INSTANCE,
                DEFAULT_DISCONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Constructs a new GameController bounded to a specific game instance.
     *
     * @param game The {@link Game} instance to be managed.
     * @param gameResultRepository repository used to persist end-of-game scores (may be null).
     * @throws IllegalArgumentException if the provided game is null.
     */
    public GameController(Game game, GameResultRepository gameResultRepository) {
        this(game, gameResultRepository, DEFAULT_DISCONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Constructs a controller with a configurable disconnection timeout.
     * This overload is primarily useful for deployments that require a custom
     * reconnection window and for deterministic timeout tests.
     *
     * @param game The {@link Game} instance to manage.
     * @param gameResultRepository repository used to persist end-of-game scores; may be null.
     * @param disconnectionTimeout timeout value before the sole online player wins.
     * @param timeoutUnit unit of the disconnection timeout.
     * @throws IllegalArgumentException if the game or timeout unit is null, or the timeout is not positive.
     */
    public GameController(Game game, GameResultRepository gameResultRepository,
                          long disconnectionTimeout, TimeUnit timeoutUnit) {
        if (game == null) {
            throw new IllegalArgumentException("Game instance cannot be null.");
        }
        if (timeoutUnit == null || disconnectionTimeout <= 0) {
            throw new IllegalArgumentException("Disconnection timeout must be positive and have a unit.");
        }
        this.game = game;
        this.initialized = false;
        this.gameResultRepository = gameResultRepository == null
                ? NoOpGameResultRepository.INSTANCE
                : gameResultRepository;
        this.matchId = UUID.randomUUID().toString();
        this.turnOrderTrackRules = loadTurnOrderTrackRules();
        this.disconnectionTimeoutMillis = timeoutUnit.toMillis(disconnectionTimeout);
    }

    /**
     * Loads turn-order track rules and converts loading failures into an initialization error.
     *
     * @return rules grouped by player count
     * @throws IllegalStateException if the rule file cannot be loaded
     */
    private Map<Integer, CardLoader.TurnOrderTrackRule[]> loadTurnOrderTrackRules() {
        try {
            return new CardLoader().loadTurnOrderTrackRules();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load turn-order rules from start.json.", e);
        }
    }

    /**
     * Gets the game model instance currently handled by this controller.
     *
     * @return The active {@link Game} instance.
     */
    public Game getGame() {
        return game;
    }

    /**
     * Configures room-level lobby constraints received from the network layer.
     *
     * @param creatorNickname room creator nickname, currently not used by the ready flow.
     * @param requiredPlayers number of players requested by the creator.
     */
    public synchronized void configureLobby(String creatorNickname, int requiredPlayers) {
        this.requiredPlayers = normalizePlayerCount(requiredPlayers);
    }

    /**
     * Parses a raw textual command and executes the corresponding game action.
     * <p>
    * This method is <b>synchronized</b> to ensure thread-safe access to the {@link Game} model
    * when multiple {@link it.polimi.Network.Server.ClientHandler} threads attempt to process actions simultaneously.
     * It handles command tokenization, basic validation, and error reporting.
     * Processing a valid command will cancel any active thinking timer.
     * </p>
     *
     * @param rawCommand The string command received from a client handler.
     * @return A human-readable feedback message or error string to be returned to the client.
     */
    public synchronized String handleCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            return "Empty command.";
        }

        String[] tokens = rawCommand.trim().split("\\s+");
        String action = tokens[0].toLowerCase();
        String nickname = actingNickname.get();

        if (phase == InteractivePhase.FINISHED
                && !("status".equals(action) || "hand".equals(action) || "stats".equals(action)
                || "help".equals(action) || "hint".equals(action) || "quit".equals(action))) {
            return "Game finished. Use STATUS to view final scores.";
        }

        if (suspendedForDisconnection
                && !("status".equals(action) || "hand".equals(action) || "stats".equals(action)
                || "help".equals(action) || "hint".equals(action) || "quit".equals(action))) {
            return "Game suspended: waiting for another player to reconnect.";
        }

        // Reset the thinking timer upon receiving activity from a player.
        cancelTurnTimer();

        try {
            switch (action) {
                case "help":
                    return String.join("\n",
                            "Commands: help, hint, ready, unready, color <name>, init <players>, status, stats [player], hand <player>, prepare <tiles|firstround>, manage, round, totem <letter>, pick <upper|lower> <t|b> <index>, quit",
                            "",
                            "Totem / Offer tiles:",
                            "1) Choose an Offer Tile to place totem.",
                            "2) At the beginning of each round, each player must move their Totem from the Turn Order tile to one of the Offer tiles.",
                            "",
                            "This choice will determine:",
                            "• How many cards they can take, and from where.",
                            "• The turn order in the next round.",
                            "• How much food they will gain or spend.");
                case "init":
                    return initCommand(tokens);
                case "ready":
                    return readyCommand(nickname);
                case "unready":
                    return unreadyCommand(nickname);
                case "color":
                    return colorCommand(nickname, tokens);
                case "prepare":
                    return prepareCommand(tokens);
                case "totem":
                    return totemCommand(nickname, tokens);
                case "pick":
                    return pickCommand(nickname, tokens);
                case "manage":
                    if (!initialized) {
                        return "Game not initialized. Use INIT <players> first.";
                    }
                    game.manageCards();
                    return "Cards managed.";
                case "round":
                    if (!initialized) {
                        return "Game not initialized. Use INIT <players> first.";
                    }
                    game.executeRound();
                    return "Round executed.";
                case "status":
                    return statusText();
                case "hint":
                    return hintCommand(nickname);
                case "hand":
                    return handCommand(tokens);
                case "stats":
                    return statsCommand(nickname, tokens);
                case "quit":
                    return "QUIT";
                default:
                    return "Unknown command: " + action;
            }
        } catch (NumberFormatException e) {
            return "Invalid number format.";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Describes the next useful action for the requesting player. When another
     * player owns the turn, the response explicitly identifies who is expected
     * to act and what they must do.
     */
    private String hintCommand(String nickname) {
        if (suspendedForDisconnection) {
            return "Waiting for another player to reconnect.";
        }

        switch (phase) {
            case LOBBY:
                if (nickname != null && !readyNicknames.contains(nickname)) {
                    return "Type 'ready' when you are ready to start.";
                }
                for (String player : pendingNicknames) {
                    if (!readyNicknames.contains(player)) {
                        return "Waiting for player " + player + " to type 'ready'.";
                    }
                }
                return "Waiting for more players to join the lobby.";
            case SELECTING_COLORS:
                return turnHint(nickname, currentColorChooser(),
                        "choose a color with 'color <name>'");
            case WAITING_FOR_PLAYERS:
                return "Waiting for the remaining players to join the match.";
            case PLACING_TOTEMS:
                if (placementIndex >= turnOrder.size()) {
                    return "Waiting for action resolution to begin.";
                }
                return turnHint(nickname, safeNick(turnOrder.get(placementIndex)),
                        "place a totem with 'totem <letter>'");
            case RESOLVING_ACTIONS:
                if (actionIndex >= actionOrder.size()) {
                    return "Waiting for end-of-round effects.";
                }
                return turnHint(nickname, safeNick(actionOrder.get(actionIndex)),
                        pickInstruction());
            case RESOLVING_END_ROUND_CARDS:
                if (endRoundCardIndex >= endRoundCardOrder.size()) {
                    return "Waiting for the round to end.";
                }
                return turnHint(nickname, safeNick(endRoundCardOrder.get(endRoundCardIndex)),
                        "take one upper-row card with 'pick upper <t|b> <index>'");
            case FINISHED:
                return "The game is finished. Use 'status' to view the final scores.";
            default:
                return "No action is required right now.";
        }
    }

    /** Returns a player-relative turn suggestion. */
    private String turnHint(String requester, String currentPlayer, String action) {
        if (requester != null && requester.equals(currentPlayer)) {
            return "Your turn: " + action + ".";
        }
        return "Waiting for player " + currentPlayer + " to " + action + ".";
    }

    /** Describes the rows still required by the current offer-tile action. */
    private String pickInstruction() {
        if (remainingUpperPicks > 0 && remainingLowerPicks > 0) {
            return "pick " + remainingUpperPicks + " upper-row and " + remainingLowerPicks
                    + " lower-row card(s) with 'pick <upper|lower> <t|b> <index>'";
        }
        if (remainingUpperPicks > 0) {
            return "pick " + remainingUpperPicks
                    + " upper-row card(s) with 'pick upper <t|b> <index>'";
        }
        return "pick " + remainingLowerPicks
                + " lower-row card(s) with 'pick lower <t|b> <index>'";
    }

    /**
     * Handles a command coming from a specific logged-in player.
     * <p>
     * This method keeps backward compatibility with existing tests by delegating to
     * {@link #handleCommand(String)} after setting a per-thread nickname context.
     * </p>
     *
     * @param nickname   acting player's nickname.
     * @param rawCommand raw command string.
     * @return controller response.
     */
    public synchronized String handlePlayerCommand(String nickname, String rawCommand) {
        String trimmed = nickname == null ? null : nickname.trim();
        if (trimmed != null && trimmed.isEmpty()) {
            trimmed = null;
        }

        actingNickname.set(trimmed);
        try {
            return handleCommand(rawCommand);
        } finally {
            actingNickname.remove();
        }
    }

    /**
     * Starts a thinking timer for the specified player.
     * <p>
     * If the player does not send a command within {@link #THINKING_TIME_LIMIT} seconds,
     * the {@link #checkAndExecuteAutoMove(Player)} method is triggered.
     * </p>
     *
     * @param player The player who must make a decision.
     */
    public synchronized void startTurnTimer(Player player) {
        cancelTurnTimer();
        if (player != null && player.isOnline()) {
            activeTurnTask = turnTimerExecutor.schedule(() -> {
                System.out.println("[TIMER] Thinking time expired for " + player.getNickname());
                checkAndExecuteAutoMove(player);
            }, THINKING_TIME_LIMIT, TimeUnit.SECONDS);
        }
    }

    /**
     * Cancels the currently active thinking timer, if any.
     */
    private synchronized void cancelTurnTimer() {
        if (activeTurnTask != null && !activeTurnTask.isDone()) {
            activeTurnTask.cancel(false);
        }
    }

    /**
     * Registers a new nickname in the game model by assigning it to the first available player slot.
     *
     * @param nickname The nickname to register.
     */
    public synchronized void registerPlayer(String nickname) {
        if (nickname == null) return;

        if (!pendingNicknames.contains(nickname)) {
            pendingNicknames.add(nickname);
        }

        for (Player p : game.getPlayers()) {
            if (nickname.equals(p.getNickname())) {
                p.setOnline(true);
                nicknameToPlayer.put(nickname, p);
                System.out.println("Player " + nickname + " marked as ONLINE.");
                updateDisconnectionSuspension();
                return;
            }
            if (p.getNickname() == null) {
                p.setNickname(nickname);
                p.setOnline(true);
                System.out.println("Assigned nickname " + nickname + " to player color " + p.getColor());
                nicknameToPlayer.put(nickname, p);
                break;
            }
        }

        updateDisconnectionSuspension();

        // If the game was initialized while players were still connecting, we can start once ready.
        if (initialized && phase == InteractivePhase.WAITING_FOR_PLAYERS) {
            maybeStartFirstRound();
        }
    }

    /**
     * Marks a player as offline in the game model.
     * <p>
     * This is called by the network layer when a client disconnects or times out.
     * Marking a player offline allows the game to continue using auto-action logic.
     * If the player was currently active, an auto-move is triggered.
     * </p>
     *
     * @param nickname The nickname of the player who went offline.
     */
    public synchronized void setPlayerOffline(String nickname) {
        if (nickname == null) return;
        for (Player p : game.getPlayers()) {
            if (nickname.equals(p.getNickname())) {
                p.setOnline(false);
                System.out.println("Player " + nickname + " marked as OFFLINE.");
                if (!updateDisconnectionSuspension()) {
                    checkAndExecuteAutoMove(p);
                }
                break;
            }
        }
    }

    /**
     * Reports whether gameplay is currently suspended while waiting for a player to reconnect.
     *
     * @return {@code true} when fewer than two players are online during an active game.
     */
    public synchronized boolean isSuspendedForDisconnection() {
        return suspendedForDisconnection;
    }

    /**
     * Re-evaluates connectivity and starts, cancels, or completes a disconnection suspension.
     * A game with exactly one online player is suspended and a victory timeout is scheduled
     * for that player. The game resumes as soon as at least two players are online.
     *
     * @return {@code true} if gameplay must remain suspended.
     */
    private synchronized boolean updateDisconnectionSuspension() {
        if (!initialized || gameFinished) {
            return false;
        }

        List<Player> onlinePlayers = new ArrayList<>();
        for (Player player : game.getPlayers()) {
            if (player.isOnline()) {
                onlinePlayers.add(player);
            }
        }

        if (onlinePlayers.size() >= 2) {
            boolean wasSuspended = suspendedForDisconnection;
            suspendedForDisconnection = false;
            timeoutCandidateNickname = null;
            cancelDisconnectionTimeout();
            if (wasSuspended) {
                game.notifyNotification("Game resumed: at least two players are online.");
                continueCurrentTurnAfterResume();
            }
            return false;
        }

        boolean wasSuspended = suspendedForDisconnection;
        suspendedForDisconnection = true;
        cancelTurnTimer();

        if (onlinePlayers.isEmpty()) {
            timeoutCandidateNickname = null;
            cancelDisconnectionTimeout();
            if (!wasSuspended) {
                game.notifyNotification("Game suspended: no players are online.");
            }
            return true;
        }

        Player soleOnlinePlayer = onlinePlayers.get(0);
        String candidate = soleOnlinePlayer.getNickname();
        if (!candidate.equals(timeoutCandidateNickname)
                || disconnectionTimeoutTask == null
                || disconnectionTimeoutTask.isDone()) {
            cancelDisconnectionTimeout();
            timeoutCandidateNickname = candidate;
            disconnectionDeadlineMillis = System.currentTimeMillis() + disconnectionTimeoutMillis;
            disconnectionTimeoutTask = turnTimerExecutor.schedule(
                    () -> handleDisconnectionTimeout(candidate),
                    disconnectionTimeoutMillis,
                    TimeUnit.MILLISECONDS);
            publishDisconnectionCountdown();
            disconnectionCountdownTask = turnTimerExecutor.scheduleAtFixedRate(
                    this::publishDisconnectionCountdown,
                    1,
                    1,
                    TimeUnit.SECONDS);
        }
        return true;
    }

    /**
     * Publishes the player being awaited and the number of seconds left in the
     * active reconnection window.
     */
    private synchronized void publishDisconnectionCountdown() {
        if (!suspendedForDisconnection || timeoutCandidateNickname == null
                || disconnectionDeadlineMillis <= 0 || gameFinished) {
            return;
        }

        long remainingMillis = disconnectionDeadlineMillis - System.currentTimeMillis();
        long remainingSeconds = Math.max(0, (remainingMillis + 999) / 1000);
        if (remainingSeconds == 0) {
            return;
        }

        String awaitedPlayers = offlinePlayerNames();
        String awaitedTarget = "another player".equals(awaitedPlayers)
                ? awaitedPlayers
                : (awaitedPlayers.contains(",") ? "players " : "player ") + awaitedPlayers;
        game.notifyNotification("Waiting for " + awaitedTarget
                + " to reconnect. Time remaining: " + remainingSeconds + " second"
                + (remainingSeconds == 1 ? "." : "s."));
    }

    /**
     * Returns the nicknames of offline players, or a generic label if no nickname
     * is currently available.
     *
     * @return comma-separated offline player nicknames
     */
    private String offlinePlayerNames() {
        List<String> offlineNicknames = new ArrayList<>();
        for (Player player : game.getPlayers()) {
            if (!player.isOnline() && player.getNickname() != null) {
                offlineNicknames.add(player.getNickname());
            }
        }
        return offlineNicknames.isEmpty() ? "another player" : String.join(", ", offlineNicknames);
    }

    /**
     * Handles expiry of the reconnection window and awards victory only if the same
     * player is still the sole online participant.
     *
     * @param expectedCandidate nickname that was the sole online player when the timeout started.
     */
    private synchronized void handleDisconnectionTimeout(String expectedCandidate) {
        if (!suspendedForDisconnection || gameFinished) {
            return;
        }

        Player soleOnlinePlayer = null;
        for (Player player : game.getPlayers()) {
            if (player.isOnline()) {
                if (soleOnlinePlayer != null) {
                    return;
                }
                soleOnlinePlayer = player;
            }
        }

        if (soleOnlinePlayer != null && expectedCandidate.equals(soleOnlinePlayer.getNickname())) {
            finalizeGame(soleOnlinePlayer, "Reconnection timeout expired");
        }
    }

    /**
     * Cancels the pending timeout used to award victory after a disconnection suspension.
     */
    private synchronized void cancelDisconnectionTimeout() {
        if (disconnectionTimeoutTask != null && !disconnectionTimeoutTask.isDone()) {
            disconnectionTimeoutTask.cancel(false);
        }
        if (disconnectionCountdownTask != null && !disconnectionCountdownTask.isDone()) {
            disconnectionCountdownTask.cancel(false);
        }
        disconnectionTimeoutTask = null;
        disconnectionCountdownTask = null;
        disconnectionDeadlineMillis = 0;
    }

    /**
     * Restarts or advances the interrupted turn after enough players reconnect.
     */
    private synchronized void continueCurrentTurnAfterResume() {
        if (phase == InteractivePhase.PLACING_TOTEMS && placementIndex < turnOrder.size()) {
            Player current = turnOrder.get(placementIndex);
            if (current.isOnline()) {
                startTurnTimer(current);
            } else {
                checkAndExecuteAutoMove(current);
            }
        } else if (phase == InteractivePhase.RESOLVING_ACTIONS && actionIndex < actionOrder.size()) {
            Player current = actionOrder.get(actionIndex);
            if (current.isOnline()) {
                startTurnTimer(current);
            } else {
                checkAndExecuteAutoMove(current);
            }
        } else if (phase == InteractivePhase.RESOLVING_END_ROUND_CARDS
                && endRoundCardIndex < endRoundCardOrder.size()) {
            Player current = endRoundCardOrder.get(endRoundCardIndex);
            if (current.isOnline()) {
                startTurnTimer(current);
            } else {
                checkAndExecuteAutoMove(current);
            }
        }
    }

    /**
     * Checks if a player is offline during their turn and executes a default action.
     * <p>
     * This method serves as the entry point for the "Auto-Player" logic. It verifies
     * the player's connectivity and, if it's their turn, triggers the appropriate
     * default behavior for the current game phase.
     * </p>
     * <p>
     * This method is synchronized to ensure thread safety when called from background timer threads.
     * </p>
     *
     * @param player The player to check for auto-action eligibility.
     */
    private synchronized void checkAndExecuteAutoMove(Player player) {
        if (suspendedForDisconnection) {
            return;
        }
        if (player.isOnline()) {
            return;
        }

        if (phase == InteractivePhase.PLACING_TOTEMS) {
            if (!turnOrder.isEmpty() && placementIndex < turnOrder.size()) {
                Player current = turnOrder.get(placementIndex);
                if (current.equals(player)) {
                    executeAutoTotemPlacement(player);
                }
            }
        } else if (phase == InteractivePhase.RESOLVING_ACTIONS) {
            if (!actionOrder.isEmpty() && actionIndex < actionOrder.size()) {
                Player current = actionOrder.get(actionIndex);
                if (current.equals(player)) {
                    executeAutoCardPicks(player);
                }
            }
        } else if (phase == InteractivePhase.RESOLVING_END_ROUND_CARDS) {
            if (endRoundCardIndex < endRoundCardOrder.size()
                    && endRoundCardOrder.get(endRoundCardIndex).equals(player)) {
                executeAutoCardPicks(player);
            }
        }
    }

    /**
     * Automatically places the totem for an offline player on the first available offer tile.
     *
     * @param player The offline player.
     */
    private void executeAutoTotemPlacement(Player player) {
        OfferTile autoChosen = null;
        for (OfferTile tile : game.getOfferTiles()) {
            if (!isOfferTileOccupied(tile)) {
                autoChosen = tile;
                break;
            }
        }

        if (autoChosen != null) {
            game.chooseOfferTile(player, autoChosen);
            game.notifyNotification("[AUTO] Player " + player.getNickname() + " auto-placed Totem on " + autoChosen.getLetter());
            placementIndex++;

            if (placementIndex >= turnOrder.size()) {
                // All placed: start action resolution left-to-right on offer track.
                actionOrder = new ArrayList<>(turnOrder);
                actionOrder.sort((a, b) -> {
                    if (a.getOfferTile() == null && b.getOfferTile() == null) return 0;
                    if (a.getOfferTile() == null) return 1;
                    if (b.getOfferTile() == null) return -1;
                    return java.lang.Character.compare(a.getOfferTile().getLetter(), b.getOfferTile().getLetter());
                });
                phase = InteractivePhase.RESOLVING_ACTIONS;
                actionIndex = 0;
                nextRoundOrder.clear();
                advanceActionPlayer();
            } else {
                Player next = turnOrder.get(placementIndex);
                checkAndExecuteAutoMove(next);
            }
        }
    }

    /**
     * Executes automated card picks for an offline player.
     * Picks cards sequentially from upper and lower rows based on simplified rules.
     *
     * @param player The offline player.
     */
    private void executeAutoCardPicks(Player player) {
        // 1. Handle upper picks.
        while (remainingUpperPicks > 0 && !noSelectableUpperCards(player)) {
            int idx = findFirstSelectableCardIndex(player, true);
            if (idx != -1) {
                boolean isTribe = idx < game.getUpperRow().size();
                int realIndex = isTribe ? idx : idx - game.getUpperRow().size();
                executeSingleAutoPick(player, true, isTribe, realIndex);
            } else {
                remainingUpperPicks = 0;
            }
        }

        // 2. Handle lower picks.
        while (remainingLowerPicks > 0 && !noSelectableLowerCards(player)) {
            int idx = findFirstSelectableCardIndex(player, false);
            if (idx != -1) {
                boolean isTribe = idx < game.getLowerRow().size();
                int realIndex = isTribe ? idx : idx - game.getLowerRow().size();
                executeSingleAutoPick(player, false, isTribe, realIndex);
            } else {
                remainingLowerPicks = 0;
            }
        }

        // 3. Complete action when picks are satisfied.
        if (remainingUpperPicks == 0 && remainingLowerPicks == 0) {
            if (phase == InteractivePhase.RESOLVING_END_ROUND_CARDS) {
                endRoundCardIndex++;
                advanceEndRoundCardPlayer();
            } else {
                finishCurrentActionPlayer();
                if (actionIndex >= actionOrder.size()) {
                    beginEndRoundCardEffects();
                } else {
                    advanceActionPlayer();
                    Player next = actionOrder.get(actionIndex);
                    checkAndExecuteAutoMove(next);
                }
            }
        }
    }

    /**
     * Finds the index of the first selectable card in the upper or lower row.
     * Searches for Tribe cards first (non-event), then Building cards (if affordable).
     *
     * @param player  The player picking the card.
     * @param isUpper True for upper row, false for lower row.
     * @return The overall index of the selected card, where values < tribeRow.size()
     *         represent the Tribe card index, and values >= tribeRow.size() represent
     *         (tribeRow.size() + buildingRowIndex) for a Building card.
     *         Returns -1 if no card is selectable.
     */
    private int findFirstSelectableCardIndex(Player player, boolean isUpper) {
        List<Tribe> tribeRow = isUpper ? game.getUpperRow() : game.getLowerRow();
        List<Building> buildingRow = isUpper ? game.getUpperBuildings() : game.getLowerBuildings();

        // 1. Search for a Tribe card (character card) first.
        for (int i = 0; i < tribeRow.size(); i++) {
            Tribe card = tribeRow.get(i);
            if (card != null && !card.isEvent()) {
                return i;
            }
        }

        // 2. Search for a Building card that the player can afford.
        int discount = builderDiscount(player);
        for (int i = 0; i < buildingRow.size(); i++) {
            Building b = buildingRow.get(i);
            if (b != null && player.getFood() >= Math.max(0, b.getCost() - discount)) {
                return tribeRow.size() + i;
            }
        }

        return -1;
    }

    /**
     * Executes a single automated card pick for the given offline player.
     *
     * @param player  The player who is offline.
     * @param isUpper True if picking from the upper row, false for lower.
     * @param isTribe True if picking a Tribe card, false for Building.
     * @param index   The index of the card in its respective list.
     */
    private void executeSingleAutoPick(Player player, boolean isUpper, boolean isTribe, int index) {
        if (isTribe) {
            Tribe taken = isUpper ? game.takeUpperTribeCard(player, index) : game.takeLowerTribeCard(player, index);
            game.notifyNotification("[AUTO] Player " + player.getNickname() + " picked Tribe card: " + taken.getClass().getSimpleName());
        } else {
            Building taken = isUpper ? game.takeUpperBuildingCard(player, index) : game.takeLowerBuildingCard(player, index);
            game.notifyNotification("[AUTO] Player " + player.getNickname() + " built: " + taken.getClass().getSimpleName());
        }
        if (isUpper) {
            remainingUpperPicks--;
        } else {
            remainingLowerPicks--;
        }
    }


    /**
     * Initializes the {@link Game} for the requested number of players and prepares the first board state.
     *
     * @param tokens the parsed command tokens
     * @return a confirmation message or a usage/error message
     * @throws Exception if the model fails while loading cards or preparing the game
     */
    private String initCommand(String[] tokens) throws Exception {
        if (tokens.length != 2) {
            return "Usage: init <players>";
        }

        int players = Integer.parseInt(tokens[1]);
        return initializeGame(players, new ArrayList<>(pendingNicknames), "Game initialized.");
    }

    /**
     * Marks a logged-in player as ready in the lobby.
     *
     * @param nickname player nickname.
     * @return readiness feedback.
     */
    private String readyCommand(String nickname) {
        if (initialized) {
            return "Game already initialized.";
        }
        if (phase == InteractivePhase.SELECTING_COLORS) {
            return "All players are ready. Choose colors in ready order.";
        }
        if (nickname == null || nickname.isBlank()) {
            return "This command requires a logged-in player.";
        }
        if (!pendingNicknames.contains(nickname)) {
            pendingNicknames.add(nickname);
        }
        if (!readyNicknames.contains(nickname)) {
            readyNicknames.add(nickname);
        }
        int playersToStart = requiredPlayers != null ? requiredPlayers : MIN_READY_PLAYERS;
        if (readyNicknames.size() >= playersToStart) {
            beginColorSelection();
            return "All players ready.\n" + readyNicknames.get(0) + " chooses color first."
                    + "\nAvailable colors: " + formatColors(availableColors);
        }
        return nickname + " is ready. Ready players: " + readyNicknames.size();
    }

    /**
     * Removes a player from the ready queue and resets color selection when necessary.
     *
     * @param nickname player leaving the ready queue
     * @return lobby feedback
     */
    private String unreadyCommand(String nickname) {
        if (initialized) {
            return "Game already initialized.";
        }
        if (nickname == null || nickname.isBlank()) {
            return "This command requires a logged-in player.";
        }
        if (!readyNicknames.remove(nickname)) {
            return nickname + " is already in the lobby.";
        }

        selectedColorsByNickname.remove(nickname);
        if (phase == InteractivePhase.SELECTING_COLORS) {
            phase = InteractivePhase.LOBBY;
            selectedColorsByNickname.clear();
            availableColors.clear();
        }

        return nickname + " returned to the lobby. Ready players: " + readyNicknames.size();
    }

    /**
     * Applies the current player's color choice and starts the game after the final choice.
     *
     * @param nickname player issuing the command
     * @param tokens parsed command tokens
     * @return color-selection or game-start feedback
     * @throws Exception if game initialization fails
     */
    private String colorCommand(String nickname, String[] tokens) throws Exception {
        if (initialized) {
            return "Game already initialized.";
        }
        if (phase != InteractivePhase.SELECTING_COLORS) {
            return "Colors can be chosen only after all players are ready.";
        }
        if (nickname == null || nickname.isBlank()) {
            return "This command requires a logged-in player.";
        }
        String current = currentColorChooser();
        if (!nickname.equals(current)) {
            return "Not your turn to choose color. Current player: " + current;
        }
        if (tokens.length != 2 || tokens[1].isBlank()) {
            return "Usage: color <blue|orange|purple|white|yellow>";
        }

        Color chosen;
        try {
            chosen = Color.valueOf(tokens[1].trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Invalid color: " + tokens[1];
        }
        if (!availableColors.contains(chosen)) {
            return "Color already selected or unavailable: " + chosen;
        }

        selectedColorsByNickname.put(nickname, chosen);
        availableColors.remove(chosen);

        if (selectedColorsByNickname.size() == readyNicknames.size()) {
            int playersToStart = requiredPlayers != null ? requiredPlayers : readyNicknames.size();
            List<Color> selectedColors = new ArrayList<>();
            for (String ready : readyNicknames) {
                selectedColors.add(selectedColorsByNickname.get(ready));
            }
            return initializeGame(playersToStart, new ArrayList<>(readyNicknames), selectedColors, "Game started.");
        }

        return nickname + " chose " + chosen + "."
                + "\nAvailable colors: " + formatColors(availableColors)
                + "\nNext color choice: " + currentColorChooser();
    }

    /**
     * Initializes the game using the provided player count and nickname assignment order.
     *
     * @param players number of active players.
     * @param nicknames nicknames that should be assigned to player slots.
     * @param successMessage message returned after successful initialization.
     * @return success message.
     * @throws Exception if model setup fails.
     */
    private String initializeGame(int players, List<String> nicknames, String successMessage) throws Exception {
        return initializeGame(players, nicknames, null, successMessage);
    }

    /**
     * Initializes the model, binds nicknames, and prepares the first round.
     *
     * @param players number of active players
     * @param nicknames nicknames in player-slot order
     * @param playerColors selected colors, or {@code null} to use defaults
     * @param successMessage message returned after initialization
     * @return the supplied success message
     * @throws Exception if model setup fails
     */
    private String initializeGame(int players, List<String> nicknames, List<Color> playerColors, String successMessage) throws Exception {
        if (playerColors == null) {
            game.initialize(players);
        } else {
            game.initialize(players, playerColors);
        }

        game.prepareOfferTiles();
        game.prepareFirstRound();
        initialized = true;
        expectedPlayers = players;
        nicknameToPlayer.clear();

        // Bind any nicknames that arrived before initialization.
        if (!game.getPlayers().isEmpty()) {
            for (String pending : nicknames) {
                if (pending == null) {
                    continue;
                }
                if (nicknameToPlayer.containsKey(pending)) {
                    continue;
                }
                registerPlayer(pending);
            }
        }

        maybeStartFirstRound();
        return successMessage;
    }

    /** Starts color selection and restores the full set of available colors. */
    private void beginColorSelection() {
        phase = InteractivePhase.SELECTING_COLORS;
        availableColors.clear();
        Collections.addAll(availableColors, Color.values());
        selectedColorsByNickname.clear();
    }

    /**
     * Finds the first ready player who has not selected a color.
     *
     * @return the player's nickname, or {@code "(none)"} when all choices are complete
     */
    private String currentColorChooser() {
        for (String ready : readyNicknames) {
            if (!selectedColorsByNickname.containsKey(ready)) {
                return ready;
            }
        }
        return "(none)";
    }

    /**
     * Attempts to start the first round if initialization is complete and all players are ready.
     * Checks that the game is initialized, has the correct number of players, and all players have nicknames.
     * If ready, initializes turn order and starts the placement phase.
     */
    private void maybeStartFirstRound() {
        if (!initialized) {
            return;
        }

        if (game.getPlayers().isEmpty()) {
            phase = InteractivePhase.LOBBY;
            return;
        }

        if (expectedPlayers != null && game.getPlayers().size() != expectedPlayers) {
            // Defensive: the model should create exactly expectedPlayers slots.
            phase = InteractivePhase.LOBBY;
            return;
        }

        if (!allPlayersHaveNicknames()) {
            phase = InteractivePhase.WAITING_FOR_PLAYERS;
            return;
        }

        // Random initial turn order (rules).
        turnOrder = new ArrayList<>(game.getPlayers());
        Collections.shuffle(turnOrder);
        applyInitialFoodByTurnOrder();

        startPlacementPhase();
    }

    /**
     * Assigns the initial Food defined by the setup rules after the first-round
     * turn order has been randomized.
     */
    private void applyInitialFoodByTurnOrder() {
        int[] initialFoodByPosition = {2, 3, 3, 4, 4};
        int limit = Math.min(turnOrder.size(), initialFoodByPosition.length);

        for (int i = 0; i < limit; i++) {
            turnOrder.get(i).receiveFood(initialFoodByPosition[i], "gameStartTurnOrder");
        }
    }

    /**
     * Checks if all players in the game have been assigned nicknames.
     *
     * @return true if every player has a non-null nickname, false otherwise
     */
    private boolean allPlayersHaveNicknames() {
        for (Player p : game.getPlayers()) {
            if (p.getNickname() == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Initializes the totem placement phase.
     * Resets placement index, action order, pick counters, and prepares offer tiles if needed.
     * Sets the interactive phase to PLACING_TOTEMS.
     */
    private void startPlacementPhase() {
        placementIndex = 0;
        actionOrder.clear();
        actionIndex = 0;
        endRoundCardOrder.clear();
        endRoundCardIndex = 0;
        remainingUpperPicks = 0;
        remainingLowerPicks = 0;
        nextRoundOrder.clear();

        // Refresh offer tiles if needed.
        try {
            if (game.getOfferTiles().isEmpty()) {
                game.prepareOfferTiles();
            }
        } catch (Exception ignored) {
            // Keep placement phase even if tiles cannot be loaded in isolated tests.
        }

        phase = InteractivePhase.PLACING_TOTEMS;
    }

    /**
     * Processes a totem placement command.
     * Validates the current player's turn and places a totem on the specified offer tile.
     * Advances to the next player or starts action resolution if all players have placed totems.
     *
     * @param nickname the player attempting to place a totem
     * @param tokens the parsed command tokens (should include the letter of the tile)
     * @return a status message or error description
     */
    private String totemCommand(String nickname, String[] tokens) {
        if (!initialized) {
            return "Game not initialized. Use INIT <players> first.";
        }
        if (nickname == null) {
            return "This command requires a logged-in player.";
        }
        if (phase == InteractivePhase.WAITING_FOR_PLAYERS) {
            return "Waiting for players to join.";
        }
        if (phase != InteractivePhase.PLACING_TOTEMS) {
            return "You cannot place a totem right now.";
        }

        if (tokens.length != 2 || tokens[1].trim().isEmpty()) {
            return "Usage: totem <letter>";
        }

        Player current = turnOrder.get(placementIndex);
        if (!nickname.equals(current.getNickname())) {
            return "Not your turn. Current player: " + safeNick(current);
        }

        char letter = java.lang.Character.toUpperCase(tokens[1].trim().charAt(0));
        OfferTile chosen = null;
        for (OfferTile tile : game.getOfferTiles()) {
            if (tile.getLetter() == letter && !isOfferTileOccupied(tile)) {
                chosen = tile;
                break;
            }
        }

        if (chosen == null) {
            return "Offer tile not available: " + letter;
        }

        game.chooseOfferTile(current, chosen);

        placementIndex++;

        if (placementIndex >= turnOrder.size()) {
            // All placed: start action resolution left-to-right on offer track.
            actionOrder = new ArrayList<>(turnOrder);
            actionOrder.sort((a, b) -> {
                if (a.getOfferTile() == null && b.getOfferTile() == null) return 0;
                if (a.getOfferTile() == null) return 1;
                if (b.getOfferTile() == null) return -1;
                return java.lang.Character.compare(a.getOfferTile().getLetter(), b.getOfferTile().getLetter());
            });
            phase = InteractivePhase.RESOLVING_ACTIONS;
            actionIndex = 0;
            nextRoundOrder.clear();
            return advanceActionPlayer();
        }

        Player next = turnOrder.get(placementIndex);
        if (!next.isOnline()) {
            checkAndExecuteAutoMove(next);
            return "Totem placed on " + chosen.getLetter() + ".\n" + currentInteractiveTurnMessage();
        }
        return "Totem placed on " + chosen.getLetter() + ". Next to place: " + safeNick(next);
    }

    /**
     * Returns whether a player has already placed a totem on the given offer tile.
     *
     * @param tile tile to inspect.
     * @return true when the tile is already occupied by a player's totem.
     */
    private boolean isOfferTileOccupied(OfferTile tile) {
        if (tile == null) {
            return false;
        }

        for (Player player : game.getPlayers()) {
            OfferTile placed = player.getOfferTile();
            if (placed != null && placed.getLetter() == tile.getLetter()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Processes a card pick command during action resolution.
     * Validates turn, card availability, and pick requirements.
     * Automatically advances to the next player when all required picks are complete.
     *
     * @param nickname the player attempting to pick a card
     * @param tokens the parsed command tokens (row, type, index)
     * @return a status message or error description
     */
    private String pickCommand(String nickname, String[] tokens) {
        if (!initialized) {
            return "Game not initialized. Use INIT <players> first.";
        }
        if (nickname == null) {
            return "This command requires a logged-in player.";
        }
        if (phase != InteractivePhase.RESOLVING_ACTIONS
                && phase != InteractivePhase.RESOLVING_END_ROUND_CARDS) {
            return "You cannot pick cards right now.";
        }

        Player current = phase == InteractivePhase.RESOLVING_END_ROUND_CARDS
                ? endRoundCardOrder.get(endRoundCardIndex)
                : actionOrder.get(actionIndex);
        if (!nickname.equals(current.getNickname())) {
            return "Not your turn. Current player: " + safeNick(current);
        }

        if (tokens.length != 4) {
            return "Usage: pick <upper|lower> <t|b> <index>";
        }

        String rowToken = tokens[1].toLowerCase();
        String typeToken = tokens[2].toLowerCase();
        int index = Integer.parseInt(tokens[3]);

        boolean isUpper;
        if ("upper".equals(rowToken)) {
            isUpper = true;
        } else if ("lower".equals(rowToken)) {
            isUpper = false;
        } else {
            return "Invalid row: " + tokens[1];
        }

        boolean isTribe;
        if ("t".equals(typeToken)) {
            isTribe = true;
        } else if ("b".equals(typeToken)) {
            isTribe = false;
        } else {
            return "Invalid type: " + tokens[2];
        }

        if (isUpper && remainingUpperPicks <= 0) {
            return "No more picks required from upper row.";
        }
        if (!isUpper && remainingLowerPicks <= 0) {
            return "No more picks required from lower row.";
        }

        Card takenCard;
        String takenDescription;
        if (isTribe) {
            Tribe taken = isUpper
                ? game.takeUpperTribeCard(current, index)
                : game.takeLowerTribeCard(current, index);
            takenCard = taken;
            takenDescription = describeTribe(taken);
        } else {
            Building taken = isUpper
                ? game.takeUpperBuildingCard(current, index)
                : game.takeLowerBuildingCard(current, index);
            takenCard = taken;
            takenDescription = describeBuilding(taken);
        }

        String takenLine = "Taken: " + takenDescription + assetSuffix(takenCard);

        if (isUpper) {
            remainingUpperPicks--;
        } else {
            remainingLowerPicks--;
        }

        // Auto-skip remaining picks if the relevant row has no selectable cards left.
        if (remainingUpperPicks > 0 && noSelectableUpperCards(current)) {
            remainingUpperPicks = 0;
        }
        if (remainingLowerPicks > 0 && noSelectableLowerCards(current)) {
            remainingLowerPicks = 0;
        }

        if (remainingUpperPicks == 0 && remainingLowerPicks == 0) {
            if (phase == InteractivePhase.RESOLVING_END_ROUND_CARDS) {
                endRoundCardIndex++;
                return takenLine + "\n" + advanceEndRoundCardPlayer();
            }
            finishCurrentActionPlayer();
            if (actionIndex >= actionOrder.size()) {
                return takenLine + "\n" + beginEndRoundCardEffects();
            }
            return takenLine + "\n" + advanceActionPlayer();
        }

        return takenLine + "\nRemaining picks: upper=" + remainingUpperPicks
                + ", lower=" + remainingLowerPicks;
    }

    /**
     * Checks if a player has any selectable cards available in the upper row.
     * A card is selectable if it's a character or if the player can afford any building.
     *
     * @param player the player to check
     * @return true if no selectable cards are available in the upper row
     */
    private boolean noSelectableUpperCards(Player player) {
        boolean hasCharacter = game.getUpperRow().stream().anyMatch(card -> card != null && !card.isEvent());
        if (hasCharacter) {
            return false;
        }

        int discount = builderDiscount(player);
        for (Building building : game.getUpperBuildings()) {
            if (building != null && player.getFood() >= Math.max(0, building.getCost() - discount)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if a player has any selectable cards available in the lower row.
     * A card is selectable if it's a character or if the player can afford any building.
     *
     * @param player the player to check
     * @return true if no selectable cards are available in the lower row
     */
    private boolean noSelectableLowerCards(Player player) {
        boolean hasCharacter = game.getLowerRow().stream().anyMatch(card -> card != null && !card.isEvent());
        if (hasCharacter) {
            return false;
        }

        int discount = builderDiscount(player);
        for (Building building : game.getLowerBuildings()) {
            if (building != null && player.getFood() >= Math.max(0, building.getCost() - discount)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Calculates the building cost discount a player receives from their Builder characters.
     *
     * @param player the player to check
     * @return the total discount from all Builder characters owned by the player
     */
    private int builderDiscount(Player player) {
        int discount = 0;
        for (Character character : player.getCharacters()) {
            if (character instanceof Builder) {
                discount += ((Builder) character).getDiscount();
            }
        }
        return discount;
    }

    /**
     * Completes a player's action during action resolution.
     * Executes the offer tile's action, adds the player to next round's order,
     * and applies last-position penalty if applicable.
     * Advances to the next player.
     */
    private void finishCurrentActionPlayer() {
        Player current = actionOrder.get(actionIndex);

        // Apply offer tile bonus (if any).
        if (current.getOfferTile() != null) {
            current.getOfferTile().executeAction(current);
        }

        // Return to turn order track (next round order is action order).
        nextRoundOrder.add(current);

        applyTurnOrderTrackRule(current, nextRoundOrder.size());

        current.setOfferTile(null);
        actionIndex++;
    }

    /**
     * Applies the food reward or penalty associated with a turn-order slot.
     *
     * @param current player entering the track
     * @param slot one-based track position
     */
    private void applyTurnOrderTrackRule(Player current, int slot) {
        CardLoader.TurnOrderTrackRule[] rules = turnOrderTrackRules.get(actionOrder.size());
        if (rules == null || slot < 1 || slot > rules.length) {
            return;
        }

        CardLoader.TurnOrderTrackRule rule = rules[slot - 1];
        int delta = rule.getFoodDelta();
        int buildingBonus = 0;
        for (Building building : current.getBuildings()) {
            if (building instanceof it.polimi.Buildings.Turn_Start_Effect effect) {
                buildingBonus += effect.getFoodBonus();
            }
        }

        if (delta > 0) {
            current.receiveFood(delta + buildingBonus, "turnOrderTrack");
        } else if (delta < 0) {
            current.payFood(-delta, rule.getPrestigePenalty(), "turnOrderTrack");
            if (buildingBonus > 0) {
                current.receiveFood(buildingBonus, "turnOrderTrackBuilding");
            }
        } else if (buildingBonus > 0) {
            current.receiveFood(buildingBonus, "turnOrderTrackBuilding");
        }
    }

    /**
     * Advances to the next player's action during action resolution.
     * Automatically resolves players who have no required picks.
     * Returns a status message for the next player needing to pick cards.
     *
     * @return a message describing the current player and required picks, or end-of-round message
     */
    private String advanceActionPlayer() {
        // Auto-resolve players with zero required picks.
        while (actionIndex < actionOrder.size()) {
            Player current = actionOrder.get(actionIndex);
            OfferTile tile = current.getOfferTile();
            int requiredUpper = tile == null ? 0 : tile.getNumUpperCards();
            int requiredLower = tile == null ? 0 : tile.getNumLowerCards();
            
            remainingUpperPicks = requiredUpper;
            remainingLowerPicks = requiredLower;

            if (remainingUpperPicks > 0 && noSelectableUpperCards(current)) {
                remainingUpperPicks = 0;
            }
            if (remainingLowerPicks > 0 && noSelectableLowerCards(current)) {
                remainingLowerPicks = 0;
            }

            if (remainingUpperPicks == 0 && remainingLowerPicks == 0) {
                finishCurrentActionPlayer();
                continue;
            }

            if (!current.isOnline()) {
                executeAutoCardPicks(current);
                return currentInteractiveTurnMessage();
            }

            return "Now acting: " + safeNick(current) + " (tile " + (tile == null ? "?" : tile.getLetter())
                    + ")\nRemaining picks: upper=" + remainingUpperPicks + ", lower=" + remainingLowerPicks
                    + "\nUse: pick <upper|lower> <t|b> <index>";
        }

        return beginEndRoundCardEffects();
    }

    /**
     * Starts the optional building effects that occur after every player action and
     * before end-of-round events. Each owned Turn_End_Card grants one upper-row pick.
     */
    private String beginEndRoundCardEffects() {
        endRoundCardOrder.clear();
        endRoundCardIndex = 0;

        for (Player player : nextRoundOrder) {
            for (Building building : player.getBuildings()) {
                if (building instanceof it.polimi.Buildings.Turn_End_Card) {
                    endRoundCardOrder.add(player);
                }
            }
        }

        if (endRoundCardOrder.isEmpty()) {
            return endRoundAndStartNextPlacement();
        }

        phase = InteractivePhase.RESOLVING_END_ROUND_CARDS;
        return advanceEndRoundCardPlayer();
    }

    /** Advances the dedicated end-of-round building-effect sequence. */
    private String advanceEndRoundCardPlayer() {
        while (endRoundCardIndex < endRoundCardOrder.size()) {
            Player current = endRoundCardOrder.get(endRoundCardIndex);
            remainingUpperPicks = noSelectableUpperCards(current) ? 0 : 1;
            remainingLowerPicks = 0;

            if (remainingUpperPicks == 0) {
                endRoundCardIndex++;
                continue;
            }

            if (!current.isOnline()) {
                executeAutoCardPicks(current);
                return currentInteractiveTurnMessage();
            }

            return "Now acting: " + safeNick(current) + " (tile END-ROUND)"
                    + "\nEnd-of-round building effect: take 1 character or building"
                    + " from the upper row (paying the building cost)."
                    + "\nRemaining picks: upper=1, lower=0"
                    + "\nUse: pick upper <t|b> <index>";
        }

        return endRoundAndStartNextPlacement();
    }

    /**
     * Ends the current round and starts the next totem placement phase.
     * Executes the game round, updates the turn order based on player action order,
     * and resets the placement phase.
     *
     * @return a status message for the start of the next round
     */
    private String endRoundAndStartNextPlacement() {
        // Next round placement order is defined by the order players return to the track.
        turnOrder = new ArrayList<>(nextRoundOrder);
        boolean isFinalRound = (game.getRound() >= Game.MAX_ROUNDS);
        int resolvedEvents = countVisibleEvents(isFinalRound);

        try {
            game.finishRoundAfterPlayerActions();
            if (!isFinalRound) {
                game.prepareOfferTiles();
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }

        String eventMessage = resolvedEvents == 1
                ? " 1 event resolved."
                : " " + resolvedEvents + " events resolved.";

        if (isFinalRound) {
            return "Round ended." + eventMessage + "\n" + finalizeGame();
        }

        startPlacementPhase();

        if (turnOrder.isEmpty()) {
            return "Round ended." + eventMessage;
        }

        Player next = turnOrder.get(0);
        if (!next.isOnline()) {
            checkAndExecuteAutoMove(next);
            return "Round ended." + eventMessage + " " + currentInteractiveTurnMessage();
        }

        return "Round ended." + eventMessage + " Next to place: " + safeNick(next);
    }

    /**
     * Describes the player currently expected to act after automatic moves have completed.
     *
     * @return a concise turn message for the active phase
     */
    private String currentInteractiveTurnMessage() {
        if (phase == InteractivePhase.PLACING_TOTEMS
                && placementIndex < turnOrder.size()) {
            return "Next to place: " + safeNick(turnOrder.get(placementIndex));
        }
        if (phase == InteractivePhase.RESOLVING_ACTIONS
                && actionIndex < actionOrder.size()) {
            Player current = actionOrder.get(actionIndex);
            return "Now acting: " + safeNick(current)
                    + " (tile " + (current.getOfferTile() == null ? "?" : current.getOfferTile().getLetter()) + ")"
                    + "\nRemaining picks: upper=" + remainingUpperPicks + ", lower=" + remainingLowerPicks
                    + "\nUse: pick <upper|lower> <t|b> <index>";
        }
        if (phase == InteractivePhase.RESOLVING_END_ROUND_CARDS
                && endRoundCardIndex < endRoundCardOrder.size()) {
            return "Now acting: " + safeNick(endRoundCardOrder.get(endRoundCardIndex))
                    + " (tile END-ROUND)"
                    + "\nEnd-of-round building effect: take 1 card from the upper row"
                    + "\nRemaining picks: upper=1, lower=0"
                    + "\nUse: pick upper <t|b> <index>";
        }
        if (phase == InteractivePhase.FINISHED) {
            return "Game over.";
        }
        return "Automatic offline moves completed.";
    }

    /**
     * Counts event cards that will be resolved at the end of the round.
     *
     * @param includeUpperRow whether upper-row events are included
     * @return the number of visible events
     */
    private int countVisibleEvents(boolean includeUpperRow) {
        int count = 0;

        for (Tribe card : game.getLowerRow()) {
            if (card != null && card.isEvent()) {
                count++;
            }
        }

        if (includeUpperRow) {
            for (Tribe card : game.getUpperRow()) {
                if (card != null && card.isEvent()) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Applies end-game scoring once, determines winners, and persists the final report.
     *
     * @return the final game report
     */
    private String finalizeGame() {
        return finalizeGame(null, null);
    }

    /**
     * Applies end-game scoring and optionally forces a winner because a reconnection
     * timeout expired while that player was the sole online participant.
     *
     * @param forcedWinner player who must win independently of score, or {@code null} for normal scoring.
     * @param finishReason reason for forced termination, or {@code null} for a normal game end.
     * @return the final game report.
     */
    private String finalizeGame(Player forcedWinner, String finishReason) {
        if (gameFinished) {
            return finalReport == null ? "Game over." : finalReport;
        }

        // Apply end-game scoring (only once).
        Map<Player, EndGameBonusBreakdown> endGameBonusByPlayer = new HashMap<>();
        for (Player player : game.getPlayers()) {
            EndGameBonusBreakdown bonus = computeEndGameBonusBreakdown(player);
            endGameBonusByPlayer.put(player, bonus);
            player.modifyPrestigePoints(bonus.total);
        }

        List<Player> winners = forcedWinner == null
                ? determineWinners()
                : Collections.singletonList(forcedWinner);

        List<Player> sorted = new ArrayList<>(game.getPlayers());
        sorted.sort(Comparator.comparingInt(Player::getPrestigePoints).reversed()
                .thenComparingInt(Player::getFood).reversed()
                .thenComparing(p -> safeNick(p)));

        StringBuilder sb = new StringBuilder();
        sb.append("Game over. Final scores:");
        if (finishReason != null && !finishReason.isBlank()) {
            sb.append("\nReason: ").append(finishReason).append(".");
        }
        for (Player player : sorted) {
            EndGameBonusBreakdown bonus = endGameBonusByPlayer.getOrDefault(player, EndGameBonusBreakdown.empty());
            sb.append("\n")
                    .append(safeNick(player))
                    .append(" pp=").append(player.getPrestigePoints())
                    .append(" food=").append(player.getFood())
                .append(" (endgame +").append(bonus.total)
                .append(": ").append(formatEndGameBonusBreakdown(bonus))
                .append(")");
        }

        sb.append("\n");
        if (winners.size() == 1) {
            sb.append("Winner: ").append(safeNick(winners.get(0)));
        } else {
            sb.append("Winners (shared): ");
            for (int i = 0; i < winners.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(safeNick(winners.get(i)));
            }
        }

        String reportText = sb.toString();
        cancelTurnTimer();
        cancelDisconnectionTimeout();
        suspendedForDisconnection = false;
        timeoutCandidateNickname = null;
        gameFinished = true;
        phase = InteractivePhase.FINISHED;
        String databaseRanking = persistFinalScores(endGameBonusByPlayer, winners, sorted, reportText);
        finalReport = databaseRanking == null || databaseRanking.isBlank()
                ? reportText
                : reportText + "\n\n" + databaseRanking;
        System.out.println(finalReport);
        try {
            game.notifyNotification(finalReport);
        } catch (Exception ignored) {
            // Never break the end-of-game flow.
        }
        return finalReport;
    }

    /**
     * Persists final player scores without allowing storage failures to interrupt the game.
     *
     * @param endGameBonusByPlayer calculated bonuses by player
     * @param winners winning players
     * @param leaderboard players in final ranking order
     * @param reportText final textual report
     */
    private String persistFinalScores(Map<Player, EndGameBonusBreakdown> endGameBonusByPlayer, List<Player> winners, List<Player> leaderboard, String reportText) {
        try {
            List<PlayerScore> scores = new ArrayList<>();
            for (int i = 0; i < leaderboard.size(); i++) {
                Player player = leaderboard.get(i);
                EndGameBonusBreakdown bonus = endGameBonusByPlayer.getOrDefault(player, EndGameBonusBreakdown.empty());
                boolean isWinner = winners != null && winners.contains(player);
                scores.add(new PlayerScore(
                        safeNick(player),
                        player.getPrestigePoints(),
                        player.getFood(),
                        bonus.total,
                        i + 1,
                        isWinner));
            }

            int playerCount = game.getPlayers().size();
            gameResultRepository.saveGameResult(new GameResult(matchId, Instant.now(), reportText, scores, playerCount));
            return databaseRankingText(scores, playerCount);
        } catch (Exception e) {
            // Persistence must never break the game flow.
            System.err.println("[DB] Failed to save match scores: " + e.getMessage());
            return "";
        }
    }

    private String databaseRankingText(List<PlayerScore> scores, int playerCount) {
        if (scores == null || scores.isEmpty() || playerCount <= 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder("Database leaderboard:");
        boolean anyPosition = false;
        for (PlayerScore score : scores) {
            int position = gameResultRepository.getPlayerPosition(matchId, score.nickname(), playerCount);
            if (position <= 0) {
                continue;
            }
            sb.append("\n")
                    .append(score.nickname())
                    .append(" is ranked #")
                    .append(position)
                    .append(" among all completed ")
                    .append(playerCount)
                    .append("-player games.");
            anyPosition = true;
        }
        if (!anyPosition) {
            return "";
        }
        sb.append("\nUse the main menu Leaderboard to view the full ranking.");
        return sb.toString();
    }

    /**
     * Calculates all character and building bonuses awarded at game end.
     *
     * @param player player to score
     * @return the complete bonus breakdown
     */
    private EndGameBonusBreakdown computeEndGameBonusBreakdown(Player player) {
        if (player == null) {
            return EndGameBonusBreakdown.empty();
        }

        int builderPoints = 0;
        int artistCount = 0;
        int inventorCount = 0;
        Set<InventorIcon> inventorIcons = new HashSet<>();

        for (Character character : player.getCharacters()) {
            if (character == null) {
                continue;
            }

            if (character.getType() == CharacterType.BUILDER) {
                builderPoints += character.getPrestigePoints();
            } else if (character.getType() == CharacterType.ARTIST) {
                artistCount++;
            }

            if (character instanceof Inventor) {
                inventorCount++;
                inventorIcons.add(((Inventor) character).getIcon());
            }
        }

        int inventorPoints = inventorCount * inventorIcons.size();
        int artistPoints = (artistCount / 2) * 10;
        EndGameBuildingBreakdown buildingBreakdown = computeEndGameBuildingBreakdown(player);
        int total = builderPoints + inventorPoints + artistPoints + buildingBreakdown.total;

        return new EndGameBonusBreakdown(builderPoints, artistCount, artistPoints, inventorCount, inventorIcons.size(), inventorPoints, buildingBreakdown, total);
    }

    /**
     * Formats an end-game bonus breakdown for the final report.
     *
     * @param bonus breakdown to format
     * @return the formatted summary
     */
    private String formatEndGameBonusBreakdown(EndGameBonusBreakdown bonus) {
        return "builder +" + bonus.builderPoints
                + ", artist +" + bonus.artistPoints + " (" + bonus.artistCount + " artists)"
                + ", inventor +" + bonus.inventorPoints + " (" + bonus.inventorCount + " inventors, " + bonus.inventorIconCount + " icons)"
                + ", buildings +" + bonus.buildingBreakdown.total + " [" + bonus.buildingBreakdown.details + "]";
    }

    /**
     * Calculates base and conditional prestige supplied by a player's buildings.
     *
     * @param player player whose buildings are scored
     * @return the building score breakdown
     */
    private EndGameBuildingBreakdown computeEndGameBuildingBreakdown(Player player) {
        int total = 0;
        List<String> details = new ArrayList<>();
        for (Building building : player.getBuildings()) {
            if (building == null) {
                continue;
            }

            if (building instanceof Endgame_Effect) {
                Endgame_Effect effect = (Endgame_Effect) building;
                int finalPoints = effect.calculateFinalPoints(player);
                int basePoints = effect.getPoints();
                int extraPoints = finalPoints - basePoints;
                total += finalPoints;
                details.add(building.getClass().getSimpleName() + " +" + finalPoints
                        + " (base +" + basePoints + ", bonus +" + extraPoints + ")");
                continue;
            }

            if (building instanceof EndGame_Builder_Add) {
                EndGame_Builder_Add builderMultiplier = (EndGame_Builder_Add) building;
                int basePoints = builderMultiplier.getPoints();
                int bonusPoints = builderMultiplier.calculateFinalPoints(player);
                int finalPoints = basePoints + bonusPoints;
                total += finalPoints;
                details.add(building.getClass().getSimpleName() + " +" + finalPoints
                        + " (base +" + basePoints + ", builder multiplier bonus +" + bonusPoints + ")");
                continue;
            }

            int points = building.getPoints();
            total += points;
            details.add(building.getClass().getSimpleName() + " +" + points + " (base)");
        }
        return new EndGameBuildingBreakdown(total, details.isEmpty() ? "(none)" : String.join(", ", details));
    }

    private static final class EndGameBonusBreakdown {
        private final int builderPoints;
        private final int artistCount;
        private final int artistPoints;
        private final int inventorCount;
        private final int inventorIconCount;
        private final int inventorPoints;
        private final EndGameBuildingBreakdown buildingBreakdown;
        private final int total;

        /**
         * Creates a complete end-game bonus breakdown.
         *
         * @param builderPoints points supplied by Builders
         * @param artistCount number of Artists
         * @param artistPoints points supplied by Artist sets
         * @param inventorCount number of Inventors
         * @param inventorIconCount number of distinct Inventor icons
         * @param inventorPoints points supplied by Inventors
         * @param buildingBreakdown building score details
         * @param total total end-game bonus
         */
        private EndGameBonusBreakdown(int builderPoints, int artistCount, int artistPoints,
                                      int inventorCount, int inventorIconCount, int inventorPoints,
                                      EndGameBuildingBreakdown buildingBreakdown, int total) {
            this.builderPoints = builderPoints;
            this.artistCount = artistCount;
            this.artistPoints = artistPoints;
            this.inventorCount = inventorCount;
            this.inventorIconCount = inventorIconCount;
            this.inventorPoints = inventorPoints;
            this.buildingBreakdown = buildingBreakdown;
            this.total = total;
        }

        /** @return a zero-valued bonus breakdown */
        private static EndGameBonusBreakdown empty() {
            return new EndGameBonusBreakdown(0, 0, 0, 0, 0, 0, EndGameBuildingBreakdown.empty(), 0);
        }
    }

    private static final class EndGameBuildingBreakdown {
        private final int total;
        private final String details;

        /**
         * Creates a building score breakdown.
         *
         * @param total total building points
         * @param details formatted scoring details
         */
        private EndGameBuildingBreakdown(int total, String details) {
            this.total = total;
            this.details = details;
        }

        /** @return a zero-valued building breakdown */
        private static EndGameBuildingBreakdown empty() {
            return new EndGameBuildingBreakdown(0, "(none)");
        }
    }

    /**
     * Determines winners by prestige points and then food, preserving shared ties.
     *
     * @return all winning players
     */
    private List<Player> determineWinners() {
        int bestPrestige = Integer.MIN_VALUE;
        for (Player player : game.getPlayers()) {
            bestPrestige = Math.max(bestPrestige, player.getPrestigePoints());
        }

        List<Player> prestigeLeaders = new ArrayList<>();
        for (Player player : game.getPlayers()) {
            if (player.getPrestigePoints() == bestPrestige) {
                prestigeLeaders.add(player);
            }
        }

        if (prestigeLeaders.size() <= 1) {
            return prestigeLeaders;
        }

        int bestFood = Integer.MIN_VALUE;
        for (Player player : prestigeLeaders) {
            bestFood = Math.max(bestFood, player.getFood());
        }

        List<Player> foodLeaders = new ArrayList<>();
        for (Player player : prestigeLeaders) {
            if (player.getFood() == bestFood) {
                foodLeaders.add(player);
            }
        }

        return foodLeaders;
    }

    /**
     * Returns a player's nickname safely, handling null or unassigned players.
     *
     * @param player the player to describe
     * @return the nickname or "?" for null, or "(unassigned)" for players without a nickname
     */
    private String safeNick(Player player) {
        if (player == null) {
            return "?";
        }
        return player.getNickname() == null ? "(unassigned)" : player.getNickname();
    }

    /**
     * Generates a textual suffix for a card showing its asset ID for UI display.
     * Returns an empty string if the card is null or has no asset ID.
     *
     * @param card the card to describe
     * @return a suffix string like " {img=001}" or empty string
     */
    private String assetSuffix(Card card) {
        if (card == null) {
            return "";
        }

        int assetId = card.getAssetId();
        if (assetId <= 0) {
            return "";
        }

        return " {img=" + String.format("%03d", assetId) + "}";
    }

    /**
     * Generates a human-readable description of a tribe card for display.
     * Includes card type, era, and character-specific details (discount, points, icons, etc.).
     *
     * @param card the tribe card to describe
     * @return a detailed description string or "(null)" if card is null
     */
    private String describeTribe(Tribe card) {
        if (card == null) {
            return "(null)";
        }

        if (card.isEvent()) {
            if (card instanceof RockPaintings event) {
                return "EVENT ROCK PAINTINGS (Era " + event.getEra()
                        + ", Artists >= " + event.getThresholdHigh() + ": "
                        + String.format("%+d", event.getPpIfMet()) + " PP per Artist"
                        + ", Artists <= " + event.getThresholdLow() + ": "
                        + String.format("%+d", event.getPpIfNotMet()) + " PP)";
            }
            if (card instanceof Hunting event) {
                return "EVENT HUNTING (Era " + event.getEra()
                        + ", per Hunter: " + String.format("%+d", event.getFood()) + " Food, "
                        + String.format("%+d", event.getPoints()) + " PP)";
            }
            if (card instanceof Sustenance event) {
                return "EVENT SUSTENANCE (Era " + event.getEra()
                        + ", cost: " + event.getFood() + " Food per character"
                        + ", shortage: -" + Math.abs(event.getPoints()) + " PP per unfed character)";
            }
            if (card instanceof ShamanicRitual event) {
                return "EVENT RITUAL (Era " + event.getEra()
                        + ", most ritual stars: " + String.format("%+d", event.getMaxPoints()) + " PP"
                        + ", fewest ritual stars: " + String.format("%+d", event.getMinPoints()) + " PP)";
            }
            if (card instanceof it.polimi.Abstract.Event event) {
                return "EVENT " + event.getType() + " (Era " + event.getEra() + ")";
            }
            return "EVENT (Era " + card.getEra() + ")";
        }

        if (card instanceof Hunter) {
            return "HUNTER (Era " + card.getEra() + ", icon=" + ((Hunter) card).hasIcon() + ")";
        }
        if (card instanceof Gatherer) {
            return "GATHERER (Era " + card.getEra() + ", discount=" + ((Gatherer) card).getDiscount() + ")";
        }
        if (card instanceof Builder) {
            Builder b = (Builder) card;
            return "BUILDER (Era " + card.getEra() + ", discount=" + b.getDiscount() + ", points=" + b.getPoints() + ")";
        }
        if (card instanceof Shaman) {
            return "SHAMAN (Era " + card.getEra() + ", stars=" + ((Shaman) card).getStar() + ")";
        }
        if (card instanceof Inventor) {
            return "INVENTOR (Era " + card.getEra() + ", icon=" + ((Inventor) card).getIcon() + ")";
        }

        if (card instanceof Character) {
            Character c = (Character) card;
            CharacterType type = c.getType();
            return type + " (Era " + c.getEra() + ")";
        }

        return "TRIBE (Era " + card.getEra() + ")";
    }

    /**
     * Generates a human-readable description of a building card for display.
     * Includes the building class name, era, cost, and prestige points.
     *
     * @param building the building card to describe
     * @return a detailed description string or "(null)" if building is null
     */
    private String describeBuilding(Building building) {
        if (building == null) {
            return "(null)";
        }
        return building.getClass().getSimpleName() + " (Era " + building.getEra()
                + ", cost=" + building.getCost() + ", points=" + building.getPoints() + ")";
    }

    /**
     * Formats all character and building cards owned by a requested player.
     * Player matching is case-insensitive, while the displayed name preserves its original form.
     *
     * @param tokens parsed command tokens containing the target nickname
     * @return the player's formatted hand or a usage, initialization, or lookup error
     */
    private String handCommand(String[] tokens) {
        if (tokens.length != 2 || tokens[1].isBlank()) {
            return "Usage: hand <player>";
        }
        if (!initialized) {
            return "Game not initialized. Use INIT <players> first.";
        }

        Player target = null;
        for (Player player : game.getPlayers()) {
            if (player.getNickname() != null && player.getNickname().equalsIgnoreCase(tokens[1])) {
                target = player;
                break;
            }
        }
        if (target == null) {
            return "Player not found: " + tokens[1];
        }

        StringBuilder result = new StringBuilder("Hand of ").append(safeNick(target)).append(":");
        result.append("\nCharacters:");
        if (target.getCharacters().isEmpty()) {
            result.append("\n(none)");
        } else {
            for (int i = 0; i < target.getCharacters().size(); i++) {
                Character character = target.getCharacters().get(i);
                result.append("\n[").append(i).append("] ")
                        .append(describeTribe(character));
            }
        }

        result.append("\nBuildings:");
        if (target.getBuildings().isEmpty()) {
            result.append("\n(none)");
        } else {
            for (int i = 0; i < target.getBuildings().size(); i++) {
                Building building = target.getBuildings().get(i);
                result.append("\n[").append(i).append("] ")
                        .append(describeBuilding(building));
            }
        }
        return result.toString();
    }

    /**
     * Formats the prestige points and food owned by the requested player.
     * When no target is supplied, the authenticated player is used.
     *
     * @param nickname authenticated player issuing the command
     * @param tokens parsed command tokens with an optional target nickname
     * @return formatted player statistics or a usage, authentication, or lookup error
     */
    private String statsCommand(String nickname, String[] tokens) {
        if (tokens.length > 2) {
            return "Usage: stats [player]";
        }
        String targetNickname = tokens.length == 2 ? tokens[1] : nickname;
        if (targetNickname == null || targetNickname.isBlank()) {
            return "This command requires a logged-in player. Use STATS <player> for another player.";
        }

        Player target = null;
        for (Player player : game.getPlayers()) {
            if (player.getNickname() != null && player.getNickname().equalsIgnoreCase(targetNickname)) {
                target = player;
                break;
            }
        }
        if (target == null) {
            return "Player not found: " + targetNickname;
        }

        return "Stats of " + safeNick(target) + ": points=" + target.getPrestigePoints()
                + ", food=" + target.getFood();
    }

    /**
     * Executes one of the preparation commands used for debugging or network-driven setup.
     * Supported modes are preparing {@link OfferTile}s or preparing the first round.
     *
     * @param tokens the parsed command tokens
     * @return a confirmation message or a usage/error message
     * @throws Exception if the model fails while preparing the requested state
     */
    private String prepareCommand(String[] tokens) throws Exception {
        if (tokens.length != 2) {
            return "Usage: prepare <tiles|firstround>";
        }

        if (!initialized) {
            return "Game not initialized. Use INIT <players> first.";
        }

        String mode = tokens[1].toLowerCase();
        if ("tiles".equals(mode)) {
            game.prepareOfferTiles();
            return "Offer tiles prepared.";
        }
        if ("firstround".equals(mode)) {
            game.prepareFirstRound();
            return "First round prepared.";
        }

        return "Unknown prepare mode: " + mode;
    }

    /**
     * Builds a compact snapshot of the current game state for status reporting.
     *
     * @return a single-line textual summary of the game
     */
    private String statusText() {
        if (gameFinished) {
            return finalReport == null ? "Game over." : finalReport;
        }

        return statusSnapshotText();
    }

    /**
     * Returns a status payload meant for UI/network synchronization.
     * Unlike {@link #handleCommand(String)} with "status", this always returns the structured snapshot
     * even after the game has finished (so GUI parsers keep working).
     *
     * @return the structured status snapshot
     */
    public synchronized String statusSnapshot() {
        return statusSnapshotText();
    }

    /**
     * Reports whether final scoring has completed.
     *
     * @return true once the match has finished and final scoring has been applied.
     */
    public synchronized boolean isGameFinished() {
        return gameFinished;
    }

    /**
     * Returns the final game report.
     *
     * @return final report text if available, otherwise a generic game-over message.
     */
    public synchronized String finalReportText() {
        return finalReport == null ? "Game over." : finalReport;
    }

    /**
     * Builds the structured status payload consumed by clients.
     *
     * @return the current game-state snapshot
     */
    private String statusSnapshotText() {

        int remainingRounds = computeRemainingRounds();

        StringBuilder sb = new StringBuilder();
        sb.append("Status: initialized=").append(initialized)
                .append(", round=").append(game.getRound())
                .append(", era=").append(game.getCurrentEra())
            .append(", remainingRounds=").append(remainingRounds)
                .append(", players=").append(game.getPlayers().size())
                .append(", offerTiles=").append(game.getOfferTiles().size())
                .append(", upperRow=").append(game.getUpperRow().size())
                .append(", lowerRow=").append(game.getLowerRow().size())
                .append(", tribeDeck=").append(game.getTribeDeck().size())
                .append("\n")
                .append("Phase: ").append(phase);

        if (suspendedForDisconnection) {
            sb.append("\nSuspended: waiting for another player to reconnect");
            if (timeoutCandidateNickname != null) {
                sb.append(" (timeout candidate: ").append(timeoutCandidateNickname).append(")");
            }
        }

        if (!initialized) {
            int playersToStart = requiredPlayers != null ? requiredPlayers : MIN_READY_PLAYERS;
            sb.append("\nRequired players: ").append(playersToStart)
                    .append("\nConnected players: ").append(formatNames(pendingNicknames))
                    .append("\nReady players: ").append(formatNames(readyNicknames))
                    .append("\nReady count: ").append(readyNicknames.size()).append("/").append(playersToStart);
            if (phase == InteractivePhase.SELECTING_COLORS) {
                sb.append("\nColor selection turn: ").append(currentColorChooser())
                        .append("\nAvailable colors: ").append(formatColors(availableColors))
                        .append("\nChosen colors: ").append(formatChosenColors());
            }
            return sb.toString();
        }

        sb.append("\nOffer tiles: ").append(formatOfferTiles());

        if (!game.getPlayers().isEmpty()) {
            sb.append("\n\nPlayers:");
            for (Player p : game.getPlayers()) {
                sb.append("\n")
                        .append(safeNick(p))
                        .append(" color=").append(p.getColor())
                        .append(" food=").append(p.getFood())
                        .append(" pp=").append(p.getPrestigePoints())
                        .append(" chars=").append(p.getCharacters().size())
                        .append(" buildings=").append(p.getBuildings().size())
                        .append(" tile=").append(p.getOfferTile() == null ? "-" : p.getOfferTile().getLetter())
                        .append(p.isOnline() ? "" : " OFFLINE");
            }

            sb.append("\n\nTaken cards:");
            for (Player p : game.getPlayers()) {
                sb.append("\n").append(safeNick(p)).append(" cards=");
                boolean any = false;
                for (Character c : p.getCharacters()) {
                    sb.append(assetSuffix(c));
                    any = true;
                }
                for (Building b : p.getBuildings()) {
                    sb.append(assetSuffix(b));
                    any = true;
                }
                if (!any) {
                    sb.append(" (none)");
                }
            }
        }

        String nick = actingNickname.get();
        if (nick != null) {
            Player me = nicknameToPlayer.get(nick);
            if (me != null) {
                sb.append("\nYou: ").append(nick)
                        .append(" food=").append(me.getFood())
                        .append(" pp=").append(me.getPrestigePoints())
                        .append(" characters=").append(me.getCharacters().size())
                        .append(" buildings=").append(me.getBuildings().size());
            }
        }

        if (phase == InteractivePhase.PLACING_TOTEMS && !turnOrder.isEmpty()) {
            sb.append("\nTo place: ").append(safeNick(turnOrder.get(placementIndex)))
                    .append("\nAvailable tiles: ").append(formatAvailableOfferTiles());

            sb.append("\n\nPlacement order:");
            for (int i = 0; i < turnOrder.size(); i++) {
                Player p = turnOrder.get(i);
                sb.append("\n")
                        .append(i == placementIndex ? "-> " : "   ")
                        .append(safeNick(p));
            }
        }

        if (phase == InteractivePhase.RESOLVING_ACTIONS && !actionOrder.isEmpty() && actionIndex < actionOrder.size()) {
            Player current = actionOrder.get(actionIndex);
            sb.append("\nActing: ").append(safeNick(current))
                    .append(" (tile ").append(current.getOfferTile() == null ? "?" : current.getOfferTile().getLetter())
                    .append(")")
                    .append(" remaining upper=").append(remainingUpperPicks)
                    .append(" lower=").append(remainingLowerPicks);

            sb.append("\n\nAction order:");
            for (int i = 0; i < actionOrder.size(); i++) {
                Player p = actionOrder.get(i);
                sb.append("\n")
                        .append(i == actionIndex ? "-> " : "   ")
                        .append(safeNick(p))
                        .append(" tile=")
                        .append(p.getOfferTile() == null ? "-" : p.getOfferTile().getLetter());
            }
        }

        if (phase == InteractivePhase.RESOLVING_END_ROUND_CARDS
                && endRoundCardIndex < endRoundCardOrder.size()) {
            Player current = endRoundCardOrder.get(endRoundCardIndex);
            sb.append("\nActing: ").append(safeNick(current))
                    .append(" (tile END-ROUND)")
                    .append(" remaining upper=").append(remainingUpperPicks)
                    .append(" lower=0")
                    .append("\nEnd-round building effect active");
        }

        sb.append("\n\nUpper tribe row:");
        sb.append("\n").append(formatTribeRow(game.getUpperRow()));
        sb.append("\nUpper buildings:");
        sb.append("\n").append(formatBuildingRow(game.getUpperBuildings()));

        sb.append("\n\nLower tribe row:");
        sb.append("\n").append(formatTribeRow(game.getLowerRow()));
        sb.append("\nLower buildings:");
        sb.append("\n").append(formatBuildingRow(game.getLowerBuildings()));

        return sb.toString();
    }

    /**
     * Normalizes a requested player count to the supported lobby range.
     *
     * @param players requested player count.
     * @return the requested count when valid, otherwise the default four-player count.
     */
    private int normalizePlayerCount(int players) {
        return players >= MIN_READY_PLAYERS && players <= 5 ? players : 4;
    }

    /**
     * Formats a list of names for status output.
     *
     * @param names names to format
     * @return a comma-separated list, or {@code "(none)"}
     */
    private String formatNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "(none)";
        }
        return String.join(", ", names);
    }

    /**
     * Formats color constants for status output.
     *
     * @param colors colors to format
     * @return a comma-separated list, or {@code "(none)"}
     */
    private String formatColors(List<Color> colors) {
        if (colors == null || colors.isEmpty()) {
            return "(none)";
        }
        List<String> names = new ArrayList<>();
        for (Color color : colors) {
            names.add(color.name());
        }
        return String.join(", ", names);
    }

    /**
     * Formats completed nickname-to-color assignments.
     *
     * @return a comma-separated assignment list, or {@code "(none)"}
     */
    private String formatChosenColors() {
        if (selectedColorsByNickname.isEmpty()) {
            return "(none)";
        }
        List<String> assignments = new ArrayList<>();
        for (String nickname : readyNicknames) {
            Color color = selectedColorsByNickname.get(nickname);
            if (color != null) {
                assignments.add(nickname + "=" + color.name());
            }
        }
        return assignments.isEmpty() ? "(none)" : String.join(", ", assignments);
    }

    /**
     * Estimates remaining rounds from the deck size and active player count.
     *
     * @return the estimated number of rounds
     */
    private int computeRemainingRounds() {
        if (!initialized) {
            return 0;
        }

        int playersCount = game.getPlayers().size();
        if (playersCount <= 0) {
            return 0;
        }

        int cardsPerRound = playersCount + 4;
        int deckSize = game.getTribeDeck() == null ? 0 : game.getTribeDeck().size();
        if (deckSize <= 0) {
            return 0;
        }

        return (int) Math.ceil(deckSize / (double) cardsPerRound);
    }

    /**
     * Formats available offer tiles for display in status messages.
     * Shows each tile's letter and the number of upper/lower card picks it provides.
     *
     * @return a comma-separated string of offer tiles or "(none)" if no tiles available
     */
    private String formatOfferTiles() {
        if (game.getOfferTiles().isEmpty()) {
            return "(none)";
        }

        StringBuilder sb = new StringBuilder();
        for (OfferTile tile : game.getOfferTiles()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(tile.getLetter())
                    .append("[")
                    .append(tile.getNumUpperCards())
                    .append("U/")
                    .append(tile.getNumLowerCards())
                    .append("L")
                    .append("]");
        }
        return sb.toString();
    }

    /**
     * Formats the offer tiles that do not currently contain a player's Totem.
     *
     * @return a comma-separated description of unoccupied offer tiles, or
     *         {@code "(none)"} when every tile is occupied
     */
    private String formatAvailableOfferTiles() {
        StringBuilder sb = new StringBuilder();
        for (OfferTile tile : game.getOfferTiles()) {
            if (isOfferTileOccupied(tile)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(tile.getLetter())
                    .append("[")
                    .append(tile.getNumUpperCards())
                    .append("U/")
                    .append(tile.getNumLowerCards())
                    .append("L]");
        }
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    /**
     * Formats a list of tribe cards for display in status messages.
     * Each card is indexed and described with its asset ID suffix.
     *
     * @param row the list of tribe cards to format
     * @return a newline-separated list of formatted cards or "(empty)" if no cards
     */
    private String formatTribeRow(List<Tribe> row) {
        if (row == null || row.isEmpty()) {
            return "(empty)";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.size(); i++) {
            Tribe card = row.get(i);
            sb.append("[").append(i).append("] ").append(describeTribe(card)).append(assetSuffix(card));
            if (i < row.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Formats a list of building cards for display in status messages.
     * Each building is indexed and described with its cost and prestige points.
     *
     * @param row the list of building cards to format
     * @return a newline-separated list of formatted buildings or "(empty)" if no buildings
     */
    private String formatBuildingRow(List<Building> row) {
        if (row == null || row.isEmpty()) {
            return "(empty)";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.size(); i++) {
            Building building = row.get(i);
            sb.append("[").append(i).append("] ").append(describeBuilding(building)).append(assetSuffix(building));
            if (i < row.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
