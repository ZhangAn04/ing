package it.polimi.Game.Core;

import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import it.polimi.Game.Elements.OfferTile;
import it.polimi.Game.Elements.Player;
import it.polimi.Abstract.Building;
import it.polimi.Buildings.Turn_Start_Effect;
import it.polimi.Characters.Artist;
import it.polimi.Characters.Builder;
import it.polimi.Game.Persistence.GameResult;
import it.polimi.Game.Persistence.GameResultRepository;
import it.polimi.Events.Hunting;
import it.polimi.Events.RockPaintings;
import it.polimi.Events.ShamanicRitual;
import it.polimi.Events.Sustenance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GameController} command handling and phase flow.
 */
class GameControllerTest {

    /**
     * Verifies that the controller refuses a null game model.
     */
    @Test
    void constructorRejectsNullGame() {
        assertThrows(IllegalArgumentException.class, () -> new GameController(null));
    }

    /**
     * Verifies that the bound game instance is exposed as-is.
     */
    @Test
    void getGameReturnsBoundInstance() {
        FakeGame game = new FakeGame();
        GameController controller = new GameController(game);

        assertSame(game, controller.getGame());
    }

    /**
     * Verifies that blank commands are reported as empty.
     */
    @Test
    void handleCommandReturnsEmptyCommandForBlankInput() {
        GameController controller = new GameController(new FakeGame());

        assertEquals("Empty command.", controller.handleCommand("   "));
    }

    /**
     * Verifies that null input is treated as an empty command.
     */
    @Test
    void handleCommandReturnsEmptyCommandForNullInput() {
        GameController controller = new GameController(new FakeGame());

        assertEquals("Empty command.", controller.handleCommand(null));
    }

    /**
     * Verifies that the help command returns the supported command list.
     */
    @Test
    void helpCommandReturnsAvailableCommands() {
        GameController controller = new GameController(new FakeGame());

        String result = controller.handleCommand("help");

        assertTrue(result.contains("init <players>"));
        assertTrue(result.contains("hand <player>"));
        assertTrue(result.contains("stats [player]"));
        assertTrue(result.contains("prepare <tiles|firstround>"));
    }

    /**
     * Verifies hand lookup, card formatting, usage validation, and unknown-player handling.
     */
    @Test
    void handCommandShowsRequestedPlayerCards() {
        GameController controller = new GameController(new Game());

        assertEquals("Usage: hand <player>", controller.handleCommand("hand"));
        assertEquals("Game not initialized. Use INIT <players> first.", controller.handleCommand("hand Alice"));
        assertEquals("Game initialized.", controller.handleCommand("init 2"));
        controller.registerPlayer("Alice");
        controller.registerPlayer("Bob");

        Player alice = findPlayer(controller.getGame(), "Alice");
        Artist artist = new Artist(Era.I, 2);
        artist.setAssetId(7);
        alice.addCharacter(artist);
        Turn_Start_Effect building = new Turn_Start_Effect(Era.I, Position.DECK, 2, 1);
        building.setAssetId(42);
        alice.addBuilding(building);

        String result = controller.handleCommand("hand alice");

        assertTrue(result.contains("Hand of Alice:"));
        assertTrue(result.contains("[0] ARTIST (Era I)"));
        assertTrue(result.contains("[0] Turn_Start_Effect (Era I, cost=2, points=1)"));
        assertFalse(result.contains("{img="));
        assertEquals("Player not found: Nobody", controller.handleCommand("hand Nobody"));
    }

    /**
     * Verifies stats lookup for the authenticated player and for an explicit target.
     */
    @Test
    void statsCommandShowsPointsAndFood() {
        GameController controller = new GameController(new Game());
        assertEquals("Game initialized.", controller.handleCommand("init 2"));
        controller.registerPlayer("Alice");
        controller.registerPlayer("Bob");

        Player alice = findPlayer(controller.getGame(), "Alice");
        alice.modifyPrestigePoints(7);
        alice.receiveFood(3);
        int expectedFood = alice.getFood();

        assertEquals("Stats of Alice: points=7, food=" + expectedFood,
                controller.handlePlayerCommand("Alice", "stats"));
        assertEquals("Stats of Alice: points=7, food=" + expectedFood,
                controller.handlePlayerCommand("Bob", "stats alice"));
        assertEquals("Usage: stats [player]",
                controller.handlePlayerCommand("Alice", "stats Bob extra"));
        assertEquals("Player not found: Nobody",
                controller.handlePlayerCommand("Alice", "stats Nobody"));
    }

    /**
     * Verifies that round execution is blocked before initialization.
     */
    @Test
    void roundIsBlockedBeforeInit() {
        GameController controller = new GameController(new FakeGame());

        String result = controller.handleCommand("round");

        assertEquals("Game not initialized. Use INIT <players> first.", result);
    }

    /**
     * Verifies that preparation commands are blocked before initialization.
     */
    @Test
    void prepareIsBlockedBeforeInit() {
        GameController controller = new GameController(new FakeGame());

        String result = controller.handleCommand("prepare tiles");

        assertEquals("Game not initialized. Use INIT <players> first.", result);
    }

    /**
     * Verifies prepare usage guidance when no mode is provided.
     */
    @Test
    void prepareWithoutModeReturnsUsage() {
        GameController controller = new GameController(new FakeGame());
        controller.handleCommand("init 2");

        assertEquals("Usage: prepare <tiles|firstround>", controller.handleCommand("prepare"));
    }

    /**
     * Verifies prepare tiles path after initialization.
     */
    @Test
    void prepareTilesAfterInitSucceeds() {
        FakeGame game = new FakeGame();
        GameController controller = new GameController(game);
        controller.handleCommand("init 2");

        String result = controller.handleCommand("prepare tiles");

        assertEquals("Offer tiles prepared.", result);
        assertEquals(2, game.prepareOfferTilesCalls);
    }

    /**
     * Verifies prepare firstround path after initialization.
     */
    @Test
    void prepareFirstRoundAfterInitSucceeds() {
        FakeGame game = new FakeGame();
        GameController controller = new GameController(game);
        controller.handleCommand("init 2");

        String result = controller.handleCommand("prepare firstround");

        assertEquals("First round prepared.", result);
        assertEquals(2, game.prepareFirstRoundCalls);
    }

    /**
     * Verifies unknown prepare mode validation.
     */
    @Test
    void prepareUnknownModeReturnsError() {
        GameController controller = new GameController(new FakeGame());
        controller.handleCommand("init 2");

        assertEquals("Unknown prepare mode: mystery", controller.handleCommand("prepare mystery"));
    }

    /**
     * Verifies that initialization updates the status snapshot.
     */
    @Test
    void initThenStatusShowsInitializedTrue() {
        GameController controller = new GameController(new FakeGame());

        String initResult = controller.handleCommand("init 2");
        String status = controller.handleCommand("status");

        assertEquals("Game initialized.", initResult);
        assertTrue(status.contains("initialized=true"));
    }

    /**
     * Verifies that invalid player counts are reported as errors.
     */
    @Test
    void initWithInvalidPlayerCountReturnsError() {
        GameController controller = new GameController(new FakeGame());

        String result = controller.handleCommand("init 1");

        assertTrue(result.startsWith("Error:"));
    }

    /**
     * Verifies that invalid numeric input is handled cleanly.
     */
    @Test
    void invalidNumberFormatIsHandled() {
        GameController controller = new GameController(new FakeGame());

        assertEquals("Invalid number format.", controller.handleCommand("init abc"));
    }

    /**
     * Verifies that init usage guidance is returned when argument count is invalid.
     */
    @Test
    void initWithoutPlayersReturnsUsage() {
        GameController controller = new GameController(new FakeGame());

        assertEquals("Usage: init <players>", controller.handleCommand("init"));
    }

    /**
     * Verifies that unknown commands are rejected with a clear message.
     */
    @Test
    void unknownCommandIsHandled() {
        GameController controller = new GameController(new FakeGame());

        assertEquals("Unknown command: ping", controller.handleCommand("ping"));
    }

    /**
     * Verifies that the quit command returns the quit sentinel.
     */
    @Test
    void quitCommandReturnsQuitSentinel() {
        GameController controller = new GameController(new FakeGame());

        assertEquals("QUIT", controller.handleCommand("quit"));
    }

    /**
     * Verifies that the manage command succeeds after initialization.
     */
    @Test
    void manageAfterInitExecutesSuccessfully() {
        FakeGame game = new FakeGame();
        GameController controller = new GameController(game);

        controller.handleCommand("init 2");
        String result = controller.handleCommand("manage");

        assertEquals("Cards managed.", result);
        assertEquals(1, game.manageCardsCalls);
    }

    /**
     * Verifies that round command triggers game round execution after init.
     */
    @Test
    void roundAfterInitExecutesSuccessfully() {
        FakeGame game = new FakeGame();
        GameController controller = new GameController(game);
        controller.handleCommand("init 2");

        String result = controller.handleCommand("round");

        assertEquals("Round executed.", result);
        assertEquals(1, game.executeRoundCalls);
    }

    /**
     * Verifies that nickname registration fills the first free player slot.
     */
    @Test
    void registerPlayerAssignsFirstAvailableSlot() {
        Game game = new Game();
        Player first = new Player(Color.ORANGE);
        Player second = new Player(Color.BLUE);
        game.getPlayers().add(first);
        game.getPlayers().add(second);
        GameController controller = new GameController(game);

        controller.registerPlayer("Alice");
        controller.registerPlayer("Bob");

        assertEquals("Alice", first.getNickname());
        assertEquals("Bob", second.getNickname());
    }

    /**
     * Verifies that setting a player offline updates the player status.
     */
    @Test
    void setPlayerOfflineMarksMatchingPlayer() {
        Game game = new Game();
        Player player = new Player(Color.PURPLE);
        player.setNickname("Nick");
        game.getPlayers().add(player);
        GameController controller = new GameController(game);

        controller.setPlayerOffline("Nick");

        assertTrue(!player.isOnline());
    }

    /**
     * Verifies that an active game is suspended when only one player remains online.
     */
    @Test
    void soleOnlinePlayerSuspendsGame() {
        GameController controller = initializedControllerWithTwoPlayers(5, TimeUnit.SECONDS);

        controller.setPlayerOffline("Bob");

        assertTrue(controller.isSuspendedForDisconnection());
        assertTrue(controller.statusSnapshot().contains("Suspended: waiting for another player to reconnect"));
        assertEquals("Game suspended: waiting for another player to reconnect.",
                controller.handlePlayerCommand("Alice", "totem A"));
    }

    /**
     * Verifies that reconnecting a second player cancels the victory timeout and resumes gameplay.
     */
    @Test
    void secondOnlinePlayerResumesGameAndCancelsTimeout() throws InterruptedException {
        GameController controller = initializedControllerWithTwoPlayers(150, TimeUnit.MILLISECONDS);
        controller.setPlayerOffline("Bob");

        controller.registerPlayer("Bob");
        Thread.sleep(250);

        assertFalse(controller.isSuspendedForDisconnection());
        assertFalse(controller.isGameFinished());
        assertFalse(controller.statusSnapshot().contains("Suspended:"));
    }

    /**
     * Verifies that timeout expiry awards victory to the player who remains connected alone.
     */
    @Test
    void disconnectionTimeoutAwardsVictoryToSoleOnlinePlayer() throws InterruptedException {
        GameController controller = initializedControllerWithTwoPlayers(30, TimeUnit.MILLISECONDS);
        controller.setPlayerOffline("Bob");

        long deadline = System.currentTimeMillis() + 2000;
        while (!controller.isGameFinished() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        assertTrue(controller.isGameFinished());
        assertFalse(controller.isSuspendedForDisconnection());
        assertTrue(controller.finalReportText().contains("Reason: Reconnection timeout expired."));
        assertTrue(controller.finalReportText().contains("Winner: Alice"));
    }

    /**
     * Creates an initialized two-player controller with both players online.
     *
     * @param timeout timeout value used by the disconnection suspension.
     * @param unit unit of the timeout value.
     * @return initialized controller containing Alice and Bob.
     */
    private static GameController initializedControllerWithTwoPlayers(long timeout, TimeUnit unit) {
        GameController controller = new GameController(
                new Game(), null, timeout, unit);
        assertEquals("Game initialized.", controller.handleCommand("init 2"));
        controller.registerPlayer("Alice");
        controller.registerPlayer("Bob");
        return controller;
    }

    /**
     * Verifies timer method guards for null and offline players.
     */
    @Test
    void startTurnTimerHandlesNullAndOfflinePlayers() {
        GameController controller = new GameController(new Game());
        Player offline = new Player(Color.YELLOW);
        offline.setOnline(false);

        controller.startTurnTimer(null);
        controller.startTurnTimer(offline);
    }

    /**
     * Verifies that an active timer is canceled when a new command arrives.
     */
    @Test
    void handleCommandCancelsActiveTurnTimer() {
        GameController controller = new GameController(new Game());
        Player online = new Player(Color.ORANGE);

        controller.startTurnTimer(online);

        assertTrue(controller.handleCommand("help").contains("Commands:"));
    }

    /**
     * Verifies guard clauses for null nicknames in player lifecycle methods.
     */
    @Test
    void registerAndOfflineIgnoreNullNickname() {
        Game game = new Game();
        Player player = new Player(Color.BLUE);
        game.getPlayers().add(player);
        GameController controller = new GameController(game);

        controller.registerPlayer(null);
        controller.setPlayerOffline(null);

        assertEquals(null, player.getNickname());
        assertTrue(player.isOnline());
    }

    @Test
    void allPlayersReadyAutomaticallyAdvanceToColorSelection() {
        Game game = new TurnOrderTrackGame();
        GameController controller = new GameController(game);
        controller.configureLobby("Alice", 3);

        assertEquals("Alice is ready. Ready players: 1", controller.handlePlayerCommand("Alice", "ready"));
        assertEquals("Bob is ready. Ready players: 2", controller.handlePlayerCommand("Bob", "ready"));

        assertEquals("All players ready.\nAlice chooses color first.\nAvailable colors: BLUE, ORANGE, PURPLE, WHITE, YELLOW",
                controller.handlePlayerCommand("Carol", "ready"));
        assertEquals("Alice chose BLUE.\nAvailable colors: ORANGE, PURPLE, WHITE, YELLOW\nNext color choice: Bob",
                controller.handlePlayerCommand("Alice", "color BLUE"));
        assertEquals("Bob chose ORANGE.\nAvailable colors: PURPLE, WHITE, YELLOW\nNext color choice: Carol",
                controller.handlePlayerCommand("Bob", "color ORANGE"));
        assertEquals("Game started.", controller.handlePlayerCommand("Carol", "color PURPLE"));
        assertTrue(controller.handleCommand("status").contains("initialized=true"));
        assertEquals(Color.BLUE, findPlayer(game, "Alice").getColor());
        assertEquals(Color.ORANGE, findPlayer(game, "Bob").getColor());
        assertEquals(Color.PURPLE, findPlayer(game, "Carol").getColor());
    }

    @Test
    void unreadyReturnsPlayerToLobbyAndCancelsColorSelection() {
        Game game = new TurnOrderTrackGame();
        GameController controller = new GameController(game);
        controller.configureLobby("Alice", 2);

        assertEquals("Alice is ready. Ready players: 1", controller.handlePlayerCommand("Alice", "ready"));
        assertEquals("All players ready.\nAlice chooses color first.\nAvailable colors: BLUE, ORANGE, PURPLE, WHITE, YELLOW",
                controller.handlePlayerCommand("Bob", "ready"));
        assertTrue(controller.handleCommand("status").contains("Phase: SELECTING_COLORS"));

        assertEquals("Alice returned to the lobby. Ready players: 1", controller.handlePlayerCommand("Alice", "unready"));

        String status = controller.handleCommand("status");
        assertTrue(status.contains("Phase: LOBBY"));
        assertTrue(status.contains("Ready players: Bob"));
        assertTrue(status.contains("Ready count: 1/2"));
        assertFalse(status.contains("Color selection turn:"));
    }

    @Test
    void turnOrderTrackBonusesAreAvailableFromSecondRoundInFivePlayerGame() {
        TurnOrderTrackGame game = new TurnOrderTrackGame(0);
        GameController controller = new GameController(game);

        assertEquals("Game initialized.", controller.handleCommand("init 5"));

        controller.registerPlayer("Alice");
        controller.registerPlayer("Bob");
        controller.registerPlayer("Carol");
        controller.registerPlayer("Dave");
        controller.registerPlayer("Eve");

        Map<String, Character> tileByNick = new HashMap<>();
        tileByNick.put("Alice", 'A');
        tileByNick.put("Bob", 'B');
        tileByNick.put("Carol", 'C');
        tileByNick.put("Dave", 'D');
        tileByNick.put("Eve", 'E');

        int aliceInitialFood = findPlayer(game, "Alice").getFood();
        int bobInitialFood = findPlayer(game, "Bob").getFood();
        int carolInitialFood = findPlayer(game, "Carol").getFood();
        int daveInitialFood = findPlayer(game, "Dave").getFood();
        int eveInitialFood = findPlayer(game, "Eve").getFood();

        placeAllTotems(controller, tileByNick, 5);

        assertEquals(aliceInitialFood + 3, findPlayer(game, "Alice").getFood());
        assertEquals(bobInitialFood + 1, findPlayer(game, "Bob").getFood());
        assertEquals(carolInitialFood, findPlayer(game, "Carol").getFood());
        assertEquals(daveInitialFood, findPlayer(game, "Dave").getFood());
        assertEquals(eveInitialFood - 1, findPlayer(game, "Eve").getFood());

        assertEquals(0, findPlayer(game, "Eve").getPrestigePoints());
    }

    @Test
    void turnOrderTrackBonusesAreAvailableFromSecondRoundInFourPlayerGame() {
        TurnOrderTrackGame game = new TurnOrderTrackGame(0);
        GameController controller = new GameController(game);

        assertEquals("Game initialized.", controller.handleCommand("init 4"));

        controller.registerPlayer("Alice");
        controller.registerPlayer("Bob");
        controller.registerPlayer("Carol");
        controller.registerPlayer("Dave");

        Map<String, Character> tileByNick = new HashMap<>();
        tileByNick.put("Alice", 'A');
        tileByNick.put("Bob", 'B');
        tileByNick.put("Carol", 'C');
        tileByNick.put("Dave", 'D');

        int aliceInitialFood = findPlayer(game, "Alice").getFood();
        int bobInitialFood = findPlayer(game, "Bob").getFood();
        int carolInitialFood = findPlayer(game, "Carol").getFood();
        int daveInitialFood = findPlayer(game, "Dave").getFood();

        placeAllTotems(controller, tileByNick, 4);

        assertEquals(aliceInitialFood + 2, findPlayer(game, "Alice").getFood());
        assertEquals(bobInitialFood + 1, findPlayer(game, "Bob").getFood());
        assertEquals(carolInitialFood, findPlayer(game, "Carol").getFood());
        assertEquals(daveInitialFood - 1, findPlayer(game, "Dave").getFood());
        assertEquals(0, findPlayer(game, "Dave").getPrestigePoints());
    }

    @Test
    void turnOrderTrackPenaltyUsesStartJsonAtEndOfFirstRoundInTwoPlayerGame() {
        TurnOrderTrackGame game = new TurnOrderTrackGame(0);
        GameController controller = new GameController(game);

        assertEquals("Game initialized.", controller.handleCommand("init 2"));

        controller.registerPlayer("Alice");
        controller.registerPlayer("Bob");

        Map<String, Character> tileByNick = new HashMap<>();
        tileByNick.put("Alice", 'A');
        tileByNick.put("Bob", 'B');

        Player bob = findPlayer(game, "Bob");
        bob.payFood(bob.getFood(), 0);
        int aliceInitialFood = findPlayer(game, "Alice").getFood();

        placeAllTotems(controller, tileByNick, 2);

        assertEquals(aliceInitialFood + 1, findPlayer(game, "Alice").getFood());
        assertEquals(0, bob.getFood());
        assertEquals(-2, bob.getPrestigePoints());
    }

    @Test
    void playersStartFirstRoundWithFoodBasedOnRandomTurnOrder() {
        TurnOrderTrackGame game = new TurnOrderTrackGame(0);
        GameController controller = new GameController(game);

        assertEquals("Game initialized.", controller.handleCommand("init 2"));

        controller.registerPlayer("Alice");
        controller.registerPlayer("Bob");

        List<Integer> initialFood = new ArrayList<>();
        initialFood.add(findPlayer(game, "Alice").getFood());
        initialFood.add(findPlayer(game, "Bob").getFood());
        Collections.sort(initialFood);

        assertEquals(List.of(2, 3), initialFood);
        assertEquals(0, findPlayer(game, "Alice").getPrestigePoints());
        assertEquals(0, findPlayer(game, "Bob").getPrestigePoints());
    }

    @Test
    void endGameBonusBreakdownMentionsArtistContribution() throws Exception {
        GameController controller = new GameController(new Game());
        Player player = new Player(Color.BLUE);
        player.addCharacter(new Artist(Era.I, 2));
        player.addCharacter(new Artist(Era.I, 2));

        Method breakdownMethod = GameController.class.getDeclaredMethod("computeEndGameBonusBreakdown", Player.class);
        breakdownMethod.setAccessible(true);
        Object breakdown = breakdownMethod.invoke(controller, player);

        Method formatMethod = GameController.class.getDeclaredMethod("formatEndGameBonusBreakdown", breakdown.getClass());
        formatMethod.setAccessible(true);
        String text = (String) formatMethod.invoke(controller, breakdown);

        assertTrue(text.contains("artist +10 (2 artists)"));
    }

    /**
     * Verifies that when a player goes offline during the totem placement phase,
     * the controller automatically places their totem on the first available offer tile.
     */
    @Test
    void testAutoTotemPlacementForOfflinePlayer() {
        TurnOrderTrackGame game = new TurnOrderTrackGame();
        GameController controller = new GameController(game);

        assertEquals("Game initialized.", controller.handleCommand("init 3"));
        controller.registerPlayer("Alice");
        controller.registerPlayer("Bob");
        controller.registerPlayer("Carol");

        // Shuffling in game setup makes player placement order random.
        // Extract who is currently active to place their totem first.
        String statusText = controller.handleCommand("status");
        String activePlayer = extractAfterPrefix(statusText, "To place:");

        // Force starting the first round to enter PLACING_TOTEMS
        // Trigger offline auto-move for the active player
        controller.setPlayerOffline(activePlayer);
        Player activePlayerObj = findPlayer(game, activePlayer);

        assertTrue(activePlayerObj.getOfferTile() != null, activePlayer + "'s totem should have been placed.");
        assertEquals('A', activePlayerObj.getOfferTile().getLetter(), activePlayer + "'s totem should be on offer tile A.");

        String status = controller.handleCommand("status");
        assertFalse(status.contains("Suspended:"));
        assertFalse(status.contains("To place: " + activePlayer),
                "Should progress past the offline player. Status: " + status);
        if (status.contains("Available tiles:")) {
            String availableTiles = status.substring(status.indexOf("Available tiles:"));
            assertFalse(availableTiles.startsWith("Available tiles: A["),
                    "Occupied tile A must not remain available. Status: " + status);
            assertTrue(availableTiles.startsWith("Available tiles: B["),
                    "Tile B should remain available. Status: " + status);
        }
    }

    /**
     * Verifies that when a player goes offline during the action resolution phase,
     * the controller automatically executes their card picks based on the first available card rules.
     */
    @Test
    void testAutoCardPicksForOfflinePlayer() throws Exception {
        Game game = new Game();
        GameController controller = new GameController(game);
        controller.configureLobby("Alice", 3);

        assertEquals("Alice is ready. Ready players: 1", controller.handlePlayerCommand("Alice", "ready"));
        assertEquals("Bob is ready. Ready players: 2", controller.handlePlayerCommand("Bob", "ready"));
        assertEquals("All players ready.\nAlice chooses color first.\nAvailable colors: BLUE, ORANGE, PURPLE, WHITE, YELLOW",
                controller.handlePlayerCommand("Carol", "ready"));

        // Choose colors to start the game
        controller.handlePlayerCommand("Alice", "color blue");
        controller.handlePlayerCommand("Bob", "color orange");
        controller.handlePlayerCommand("Carol", "color purple");

        // Both players place totems in the correct dynamic sequence matching the shuffled turnOrder.
        Map<String, Character> tileByNick = new HashMap<>();
        tileByNick.put("Alice", 'B');
        tileByNick.put("Bob", 'C');
        tileByNick.put("Carol", 'D');
        placeAllTotems(controller, tileByNick, 3);

        // Alice is now acting in the RESOLVING_ACTIONS phase because 'B' is evaluated first.
        // Verify we are in resolving phase and Alice is the active player.
        String preStatus = controller.handleCommand("status");
        assertTrue(preStatus.contains("Phase: RESOLVING_ACTIONS"));
        assertTrue(preStatus.contains("Acting: Alice"));

        Player alice = game.getPlayers().stream()
            .filter(p -> "Alice".equals(p.getNickname()))
            .findFirst()
            .orElseThrow();
        int initialCards = alice.getCharacters().size() + alice.getBuildings().size();

        // Mark Alice as offline. This should trigger auto-picks.
        controller.setPlayerOffline("Alice");

        // Check if Alice has automatically picked cards
        int postCards = alice.getCharacters().size() + alice.getBuildings().size();
        assertTrue(postCards > initialCards, "Alice should have picked at least one card automatically.");

        // Game should have progressed past Alice (either to Bob or the next round placement)
        String postStatus = controller.handleCommand("status");
        assertFalse(postStatus.contains("Acting: Alice"), "Alice should no longer be the active player. Status: " + postStatus);
    }

    /**
     * Verifies that a player disconnected before their placement turn is moved automatically when reached.
     */
    @Test
    void offlinePlayerAutoMovesWhenPlacementTurnIsReached() throws Exception {
        Game game = new Game();
        GameController controller = new GameController(game);
        Player alice = new Player(Color.BLUE);
        alice.setNickname("Alice");
        Player bob = new Player(Color.ORANGE);
        bob.setNickname("Bob");
        Player carol = new Player(Color.PURPLE);
        carol.setNickname("Carol");
        game.getPlayers().add(alice);
        game.getPlayers().add(bob);
        game.getPlayers().add(carol);
        game.getOfferTiles().add(new OfferTile(1, 0, 'A', 0, 2));
        game.getOfferTiles().add(new OfferTile(1, 0, 'B', 0, 2));
        game.getOfferTiles().add(new OfferTile(1, 0, 'C', 0, 3));
        game.getUpperRow().add(new Artist(Era.I, 2));
        game.getUpperRow().add(new Artist(Era.I, 2));
        setControllerField(controller, "initialized", true);
        setControllerPhase(controller, "PLACING_TOTEMS");
        setControllerField(controller, "turnOrder", new ArrayList<>(List.of(alice, bob, carol)));

        controller.setPlayerOffline("Bob");
        controller.handlePlayerCommand("Alice", "totem A");

        String status = controller.statusSnapshot();
        assertFalse(status.contains("To place: Bob"));
        assertFalse(status.contains("Acting: Bob"));
    }

    /**
     * Verifies that a player disconnected before their action turn picks automatically when reached.
     */
    @Test
    void offlinePlayerAutoMovesWhenActionTurnIsReached() throws Exception {
        Game game = new Game();
        GameController controller = new GameController(game);
        Player alice = new Player(Color.BLUE);
        alice.setNickname("Alice");
        alice.setOfferTile(new OfferTile(1, 0, 'A', 0, 2));
        Player bob = new Player(Color.ORANGE);
        bob.setNickname("Bob");
        bob.setOfferTile(new OfferTile(1, 0, 'B', 0, 2));
        Player carol = new Player(Color.PURPLE);
        carol.setNickname("Carol");
        carol.setOfferTile(new OfferTile(0, 0, 'C', 0, 3));
        game.getPlayers().add(alice);
        game.getPlayers().add(bob);
        game.getPlayers().add(carol);
        game.getUpperRow().add(new Artist(Era.I, 2));
        game.getUpperRow().add(new Artist(Era.I, 2));
        setControllerField(controller, "initialized", true);
        setControllerPhase(controller, "RESOLVING_ACTIONS");
        setControllerField(controller, "actionOrder", new ArrayList<>(List.of(alice, bob, carol)));
        setControllerField(controller, "remainingUpperPicks", 1);

        controller.setPlayerOffline("Bob");
        controller.handlePlayerCommand("Alice", "pick upper t 0");

        assertEquals(1, bob.getCharacters().size());
        assertFalse(controller.statusSnapshot().contains("Acting: Bob"));
    }

    /**
     * Verifies command validation and character/building picks during action resolution.
     */
    @Test
    void pickCommandCoversValidationAndCardTypes() throws Exception {
        Game game = new Game();
        GameController controller = new GameController(game);
        Player alice = new Player(Color.BLUE);
        alice.setNickname("Alice");
        alice.receiveFood(10);
        alice.setOfferTile(new OfferTile(2, 2, 'A', 0, 2));
        game.getPlayers().add(alice);
        game.getUpperRow().add(new Artist(Era.I, 2));
        game.getLowerRow().add(new Artist(Era.I, 2));
        game.getUpperBuildings().add(new Building(Era.I, Position.TOP, 2, 1));

        setControllerField(controller, "initialized", true);
        setControllerPhase(controller, "RESOLVING_ACTIONS");
        setControllerField(controller, "actionOrder", new ArrayList<>(Collections.singletonList(alice)));
        setControllerField(controller, "actionIndex", 0);
        setControllerField(controller, "remainingUpperPicks", 2);
        setControllerField(controller, "remainingLowerPicks", 2);

        assertEquals("This command requires a logged-in player.", controller.handleCommand("pick upper t 0"));
        assertTrue(controller.handlePlayerCommand("Bob", "pick upper t 0").contains("Not your turn"));
        assertEquals("Usage: pick <upper|lower> <t|b> <index>",
                controller.handlePlayerCommand("Alice", "pick upper"));
        assertEquals("Invalid row: middle", controller.handlePlayerCommand("Alice", "pick middle t 0"));
        assertEquals("Invalid type: x", controller.handlePlayerCommand("Alice", "pick upper x 0"));

        setControllerField(controller, "remainingUpperPicks", 0);
        assertEquals("No more picks required from upper row.",
                controller.handlePlayerCommand("Alice", "pick upper t 0"));
        setControllerField(controller, "remainingUpperPicks", 2);

        assertTrue(controller.handlePlayerCommand("Alice", "pick upper t 0").contains("Taken: ARTIST"));
        assertTrue(controller.handlePlayerCommand("Alice", "pick upper b 0").contains("Taken: Building"));
        assertTrue(controller.handlePlayerCommand("Alice", "pick lower t 0").contains("Taken: ARTIST"));
    }

    /**
     * Verifies that terminal card rows explain the gameplay values of every event type.
     */
    @Test
    void statusDescribesEventGameplayValues() throws Exception {
        Game game = new Game();
        GameController controller = new GameController(game);
        setControllerField(controller, "initialized", true);
        game.getUpperRow().add(new RockPaintings(Era.III, Position.TOP, 2, 0, 5, -3));
        game.getUpperRow().add(new Hunting(Era.II, Position.TOP, 1, 2));
        game.getLowerRow().add(new Sustenance(Era.II, Position.BOTTOM, 1, 2));
        game.getLowerRow().add(new ShamanicRitual(Era.I, Position.BOTTOM, 5, -3));

        String status = controller.statusSnapshot();

        assertTrue(status.contains("EVENT ROCK PAINTINGS (Era III, Artists >= 2: +5 PP per Artist, Artists <= 0: -3 PP)"));
        assertTrue(status.contains("EVENT HUNTING (Era II, per Hunter: +1 Food, +2 PP)"));
        assertTrue(status.contains("EVENT SUSTENANCE (Era II, cost: 1 Food per character, shortage: -2 PP per unfed character)"));
        assertTrue(status.contains("EVENT RITUAL (Era I, most ritual stars: +5 PP, fewest ritual stars: -3 PP)"));
    }

    /**
     * Verifies final scoring, winner selection, result persistence, and idempotent finalization.
     */
    @Test
    void finalizeGameScoresPersistsAndReturnsStableReport() throws Exception {
        Game game = new Game();
        Player alice = new Player(Color.BLUE);
        alice.setNickname("Alice");
        alice.addCharacter(new Artist(Era.I, 2));
        alice.addCharacter(new Artist(Era.I, 2));
        alice.addCharacter(new Builder(Era.I, 2, 1, 3));
        alice.addBuilding(new Building(Era.I, Position.DECK, 1, 4));
        alice.modifyPrestigePoints(5);
        alice.receiveFood(2);

        Player bob = new Player(Color.ORANGE);
        bob.setNickname("Bob");
        bob.modifyPrestigePoints(5);
        bob.receiveFood(1);
        game.getPlayers().add(alice);
        game.getPlayers().add(bob);

        AtomicReference<GameResult> persisted = new AtomicReference<>();
        GameResultRepository repository = persisted::set;
        GameController controller = new GameController(game, repository);

        Method determineWinners = GameController.class.getDeclaredMethod("determineWinners");
        determineWinners.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Player> foodTieBreakWinners = (List<Player>) determineWinners.invoke(controller);
        assertEquals(Collections.singletonList(alice), foodTieBreakWinners);

        Method finalizeGame = GameController.class.getDeclaredMethod("finalizeGame");
        finalizeGame.setAccessible(true);
        String firstReport = (String) finalizeGame.invoke(controller);
        String secondReport = (String) finalizeGame.invoke(controller);

        assertTrue(firstReport.contains("Winner: Alice"));
        assertTrue(firstReport.contains("artist +10"));
        assertEquals(firstReport, secondReport);
        assertEquals(firstReport, controller.finalReportText());
        assertTrue(controller.isGameFinished());
        assertEquals(2, persisted.get().playerScores().size());
        assertTrue(persisted.get().playerScores().stream().anyMatch(score -> score.winner()));
    }

    /**
     * Sets a private controller field to prepare deterministic state-machine scenarios.
     *
     * @param controller controller under test
     * @param fieldName field to update
     * @param value replacement value
     */
    private static void setControllerField(GameController controller, String fieldName, Object value) throws Exception {
        Field field = GameController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(controller, value);
    }

    /**
     * Sets the controller's private interactive phase by enum constant name.
     *
     * @param controller controller under test
     * @param phaseName phase constant name
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setControllerPhase(GameController controller, String phaseName) throws Exception {
        Field field = GameController.class.getDeclaredField("phase");
        field.setAccessible(true);
        field.set(controller, Enum.valueOf((Class<? extends Enum>) field.getType(), phaseName));
    }


    private static void placeAllTotems(GameController controller, Map<String, Character> tileByNick, int players) {
        for (int i = 0; i < players; i++) {
            String status = controller.handleCommand("status");
            String toPlace = extractAfterPrefix(status, "To place:");
            Character tile = tileByNick.get(toPlace);
            assertTrue(tile != null, "No tile mapping for current player: " + toPlace);
            controller.handlePlayerCommand(toPlace, "totem " + tile);
        }
    }

    private static String extractAfterPrefix(String status, String prefix) {
        for (String line : status.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith(prefix.toLowerCase())) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        throw new AssertionError("Expected prefix not found in status: " + prefix);
    }

    private static Player findPlayer(Game game, String nickname) {
        for (Player p : game.getPlayers()) {
            if (nickname.equals(p.getNickname())) {
                return p;
            }
        }
        throw new AssertionError("Player not found: " + nickname);
    }

    /**
     * Lightweight fake game used to isolate controller tests from the real loader logic.
     */
    private static class FakeGame extends Game {
        private int prepareOfferTilesCalls;
        private int prepareFirstRoundCalls;
        private int manageCardsCalls;
        private int executeRoundCalls;

        /**
         * Validates the player count without loading external resources.
         */
        @Override
        public void initialize(int numPlayers) {
            if (numPlayers < 2 || numPlayers > 5) {
                throw new IllegalArgumentException("Invalid number of players.");
            }
        }

        @Override
        public void initialize(int numPlayers, List<Color> playerColors) {
            initialize(numPlayers);
            if (playerColors == null || playerColors.size() != numPlayers) {
                throw new IllegalArgumentException("A color must be selected for each player.");
            }
        }

        /**
         * No-op override used to keep controller tests isolated.
         */
        @Override
        public void prepareOfferTiles() {
            prepareOfferTilesCalls++;
        }

        /**
         * No-op override used to keep controller tests isolated.
         */
        @Override
        public void prepareFirstRound() {
            prepareFirstRoundCalls++;
        }

        /**
         * No-op override used to keep controller tests isolated.
         */
        @Override
        public void manageCards() {
            manageCardsCalls++;
        }

        /**
         * No-op override used to keep controller tests isolated.
         */
        @Override
        public void executeRound() {
            executeRoundCalls++;
        }
    }

    /**
     * Game test double that exposes deterministic turn-order track setup.
     */
    private static class TurnOrderTrackGame extends Game {
        private int numPlayers;
        private final int initialFood;

        private TurnOrderTrackGame() {
            this(5);
        }

        private TurnOrderTrackGame(int initialFood) {
            this.initialFood = initialFood;
        }

        @Override
        public void initialize(int numPlayers) {
            if (numPlayers < 2 || numPlayers > 5) {
                throw new IllegalArgumentException("Invalid number of players.");
            }
            this.numPlayers = numPlayers;

            getPlayers().clear();

            Color[] colors = Color.values();
            for (int i = 0; i < numPlayers; i++) {
                Player player = new Player(colors[i]);
                player.receiveFood(initialFood);
                getPlayers().add(player);
            }

            getOfferTiles().clear();
        }

        @Override
        public void initialize(int numPlayers, List<Color> playerColors) {
            initialize(numPlayers);
            for (int i = 0; i < numPlayers; i++) {
                getPlayers().set(i, new Player(playerColors.get(i)));
                getPlayers().get(i).receiveFood(initialFood);
            }
        }

        @Override
        public void prepareOfferTiles() {
            getOfferTiles().clear();
            for (int i = 0; i < numPlayers; i++) {
                char letter = (char) ('A' + i);
                getOfferTiles().add(new OfferTile(0, 0, letter, 0, 2));
            }
        }

        @Override
        public void prepareFirstRound() {
            // no-op
        }

        @Override
        public void manageCards() {
            // no-op
        }

        @Override
        public void executeRound() {
            // no-op
        }
    }
}
