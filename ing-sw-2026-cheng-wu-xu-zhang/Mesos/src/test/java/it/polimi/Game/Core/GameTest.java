package it.polimi.Game.Core;

import it.polimi.Abstract.Building;
import it.polimi.Buildings.Event_Add_Effect;
import it.polimi.Buildings.Ritual.AddStar;
import it.polimi.Characters.Shaman;
import it.polimi.Characters.Builder;
import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.EventType;
import it.polimi.Constants.InventorIcon;
import it.polimi.Constants.Position;
import it.polimi.Characters.Artist;
import it.polimi.Characters.Hunter;
import it.polimi.Events.Hunting;
import it.polimi.Events.RockPaintings;
import it.polimi.Events.ShamanicRitual;
import it.polimi.Events.Sustenance;
import it.polimi.Characters.Inventor;
import it.polimi.Characters.Gatherer;
import it.polimi.Game.Elements.OfferTile;
import it.polimi.Game.Elements.Player;
import it.polimi.Game.Elements.Tribe;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Game} state management.
 */
class GameTest {

    /**
     * Verifies that choosing an available offer tile assigns it to the player and keeps it visible.
     */
    @Test
    void chooseOfferTileAssignsTileAndKeepsItVisible() {
        Game game = new Game();
        Player player = new Player(Color.ORANGE);
        OfferTile tile = new OfferTile(1, 0, 'A', 0, 2);

        game.getOfferTiles().add(tile);
        game.chooseOfferTile(player, tile);

        assertEquals(tile, player.getOfferTile());
        assertTrue(game.getOfferTiles().contains(tile));
    }

    /**
     * Verifies that choosing an unavailable offer tile raises an exception.
     */
    @Test
    void chooseOfferTileThrowsWhenTileIsUnavailable() {
        Game game = new Game();
        Player player = new Player(Color.BLUE);
        OfferTile tile = new OfferTile(1, 0, 'A', 0, 2);

        assertThrows(IllegalArgumentException.class, () -> game.chooseOfferTile(player, tile));
    }

    /**
     * Verifies that executeTurn moves the selected cards from the board to the player.
     */
    @Test
    void executeTurnMovesSelectedCardsToPlayer() {
        Game game = new Game();
        Player player = new Player(Color.PURPLE);
        OfferTile tile = new OfferTile(1, 1, 'B', 0, 2);
        Inventor upperChoice = new Inventor(Era.I, 2, InventorIcon.rope);
        Gatherer lowerChoice = new Gatherer(Era.I, 2, 3);

        player.setOfferTile(tile);
        game.getUpperRow().add(upperChoice);
        game.getLowerRow().add(lowerChoice);

        game.executeTurn(
            player,
            Collections.singletonList(upperChoice),
            Collections.singletonList(lowerChoice)
        );

        assertTrue(game.getUpperRow().isEmpty());
        assertTrue(game.getLowerRow().isEmpty());
        assertEquals(2, player.getCharacters().size());
        assertTrue(player.getCharacters().contains(upperChoice));
        assertTrue(player.getCharacters().contains(lowerChoice));
    }

    /**
     * Verifies that event cards cannot be selected during a turn.
     */
    @Test
    void executeTurnRejectsEventSelection() {
        Game game = new Game();
        Player player = new Player(Color.YELLOW);
        OfferTile tile = new OfferTile(1, 0, 'C', 0, 2);
        Hunting event = new Hunting(Era.I, Position.TOP, 2, 1);

        player.setOfferTile(tile);
        game.getUpperRow().add(event);

        assertThrows(
                IllegalArgumentException.class,
            () -> game.executeTurn(player, Collections.singletonList(event), Collections.emptyList())
        );
    }

    /**
     * Verifies that executeTurn rejects an invalid number of upper-row selections.
     */
    @Test
    void executeTurnRejectsWrongUpperSelectionCount() {
        Game game = new Game();
        Player player = new Player(Color.BLUE);
        OfferTile tile = new OfferTile(1, 0, 'D', 0, 2);

        player.setOfferTile(tile);

        assertThrows(
            IllegalArgumentException.class,
            () -> game.executeTurn(player, Collections.emptyList(), Collections.emptyList())
        );
    }

    /**
     * Verifies that executeTurn rejects cards that are not present in the chosen row.
     */
    @Test
    void executeTurnRejectsCardNotInUpperRow() {
        Game game = new Game();
        Player player = new Player(Color.ORANGE);
        OfferTile tile = new OfferTile(1, 0, 'E', 0, 2);
        Inventor notAvailable = new Inventor(Era.I, 2, InventorIcon.arrow);

        player.setOfferTile(tile);

        assertThrows(
            IllegalArgumentException.class,
            () -> game.executeTurn(player, Collections.singletonList(notAvailable), Collections.emptyList())
        );
    }

    /**
     * Verifies that initialize rejects player counts outside the allowed range.
     */
    @Test
    void initializeRejectsInvalidPlayerCounts() {
        Game game = new Game();

        assertThrows(IllegalArgumentException.class, () -> game.initialize(1));
        assertThrows(IllegalArgumentException.class, () -> game.initialize(6));
    }

    /**
     * Verifies that listeners receive notifications only while registered.
     */
    @Test
    void propertyChangeListenerCanBeAddedAndRemoved() {
        Game game = new Game();
        Player player = new Player(Color.ORANGE);
        OfferTile firstTile = new OfferTile(1, 0, 'F', 0, 2);
        OfferTile secondTile = new OfferTile(1, 0, 'G', 0, 2);
        AtomicInteger notifications = new AtomicInteger(0);

        PropertyChangeListener listener = event -> {
            if ("offerTileChosen".equals(event.getPropertyName())) {
                notifications.incrementAndGet();
            }
        };

        game.addPropertyChangeListener(listener);
        game.getOfferTiles().add(firstTile);
        game.chooseOfferTile(player, firstTile);
        assertEquals(1, notifications.get());

        game.removePropertyChangeListener(listener);
        game.getOfferTiles().add(secondTile);
        game.chooseOfferTile(player, secondTile);
        assertEquals(1, notifications.get());
    }

    /**
     * Verifies that initialize builds a valid starting state for a legal player count.
     */
    @Test
    void initializeSetsCoreStateForValidPlayerCount() throws Exception {
        Game game = new Game();

        game.initialize(3);

        assertEquals(1, game.getRound());
        assertEquals(Era.I, game.getCurrentEra());
        assertEquals(3, game.getPlayers().size());
        assertTrue(game.getUpperRow().isEmpty());
        assertTrue(game.getLowerRow().isEmpty());
        assertTrue(game.getOfferTiles().isEmpty());
        assertFalse(game.getTribeDeck().isEmpty());
    }

    /**
     * Verifies that only offer tiles valid for the current player count are exposed.
     */
    @Test
    void prepareOfferTilesKeepsOnlyTilesValidForPlayerCount() throws Exception {
        Game game = new Game();
        game.initialize(2);

        game.prepareOfferTiles();

        assertFalse(game.getOfferTiles().isEmpty());
        assertTrue(game.getOfferTiles().stream().allMatch(tile -> tile.availableFor(2)));
    }

    /**
     * Verifies that executeTurn rejects an invalid number of lower-row selections.
     */
    @Test
    void executeTurnRejectsWrongLowerSelectionCount() {
        Game game = new Game();
        Player player = new Player(Color.YELLOW);
        OfferTile tile = new OfferTile(0, 1, 'H', 0, 2);

        player.setOfferTile(tile);

        assertThrows(
            IllegalArgumentException.class,
            () -> game.executeTurn(player, Collections.emptyList(), Collections.emptyList())
        );
    }

    /**
     * Verifies that event cards cannot be selected from the lower row.
     */
    @Test
    void executeTurnRejectsLowerEventSelection() {
        Game game = new Game();
        Player player = new Player(Color.PURPLE);
        OfferTile tile = new OfferTile(0, 1, 'I', 0, 2);
        Hunting event = new Hunting(Era.I, Position.BOTTOM, 2, 1);

        player.setOfferTile(tile);
        game.getLowerRow().add(event);

        assertThrows(
            IllegalArgumentException.class,
            () -> game.executeTurn(player, Collections.emptyList(), Collections.singletonList(event))
        );
    }

    /**
     * Verifies that executeTurn rejects cards that are not present in the lower row.
     */
    @Test
    void executeTurnRejectsCardNotInLowerRow() {
        Game game = new Game();
        Player player = new Player(Color.BLUE);
        OfferTile tile = new OfferTile(0, 1, 'L', 0, 2);
        Gatherer notAvailable = new Gatherer(Era.I, 2, 1);

        player.setOfferTile(tile);

        assertThrows(
            IllegalArgumentException.class,
            () -> game.executeTurn(player, Collections.emptyList(), Collections.singletonList(notAvailable))
        );
    }

    /**
     * Verifies that prepareFirstRound distributes events to upper row and characters to lower row.
     */
    @Test
    void prepareFirstRoundDistributesCardsBetweenRows() throws Exception {
        Game game = new Game();
        Gatherer lowerCharacter = new Gatherer(Era.I, 2, 2);
        Hunting upperEvent = new Hunting(Era.I, Position.TOP, 1, 1);
        Inventor upperCharacter = new Inventor(Era.I, 2, InventorIcon.bowl);
        Stack<Tribe> customDeck = new Stack<>();

        customDeck.push(upperCharacter);
        customDeck.push(upperEvent);
        customDeck.push(lowerCharacter);

        setPrivateField(game, "numUpperCards", 2);
        setPrivateField(game, "numLowerCards", 1);
        setPrivateField(game, "tribeDeck", customDeck);

        game.prepareFirstRound();

        assertEquals(1, game.getLowerRow().size());
        assertTrue(game.getLowerRow().contains(lowerCharacter));
        assertEquals(2, game.getUpperRow().size());
        assertTrue(game.getUpperRow().contains(upperEvent));
        assertTrue(game.getUpperRow().contains(upperCharacter));
    }

    /**
     * Verifies that manageCards moves cards, refills the upper row, and handles era transitions.
     */
    @Test
    void manageCardsMovesCardsAndHandlesEraChange() throws Exception {
        Game game = new Game();
        Gatherer movedToLower = new Gatherer(Era.I, 2, 1);
        Inventor movedToLowerToo = new Inventor(Era.I, 2, InventorIcon.rope);
        Gatherer refillUpper = new Gatherer(Era.II, 2, 3);
        Inventor refillUpperToo = new Inventor(Era.II, 2, InventorIcon.arrow);
        Building oldUpperBuilding = new Building(Era.I, Position.TOP, 1, 1);
        Building era2BuildingA = new Building(Era.II, Position.TOP, 2, 2);
        Building era2BuildingB = new Building(Era.II, Position.TOP, 3, 3);
        Building existingLowerBuilding = new Building(Era.I, Position.BOTTOM, 1, 1);
        Stack<Tribe> customDeck = new Stack<>();
        Stack<Building> era2Deck = new Stack<>();
        List<Building> upperBuildings = new ArrayList<>();
        List<Building> lowerBuildings = new ArrayList<>();

        customDeck.push(refillUpperToo);
        customDeck.push(refillUpper);

        era2Deck.push(era2BuildingA);
        era2Deck.push(era2BuildingB);

        upperBuildings.add(oldUpperBuilding);
        lowerBuildings.add(existingLowerBuilding);

        game.getUpperRow().add(movedToLower);
        game.getUpperRow().add(movedToLowerToo);
        game.getLowerRow().add(new Gatherer(Era.I, 2, 5));

        setPrivateField(game, "numUpperCards", 2);
        setPrivateField(game, "numLowerCards", 2);
        setPrivateField(game, "currentEra", Era.I);
        setPrivateField(game, "tribeDeck", customDeck);
        setPrivateField(game, "upperBuildings", upperBuildings);
        setPrivateField(game, "lowerBuildings", lowerBuildings);
        setPrivateField(game, "era2BuildingDeck", era2Deck);

        game.manageCards();

        assertEquals(Era.II, game.getCurrentEra());
        assertEquals(2, game.getLowerRow().size());
        assertTrue(game.getLowerRow().contains(movedToLower));
        assertTrue(game.getLowerRow().contains(movedToLowerToo));
        assertEquals(2, game.getUpperRow().size());
        assertTrue(game.getUpperRow().contains(refillUpper));
        assertTrue(game.getUpperRow().contains(refillUpperToo));

        List<Building> updatedLowerBuildings = getPrivateBuildingListField(game, "lowerBuildings");
        List<Building> updatedUpperBuildings = getPrivateBuildingListField(game, "upperBuildings");
        Stack<Building> updatedEra2Deck = getPrivateBuildingStackField(game, "era2BuildingDeck");

        assertEquals(2, updatedLowerBuildings.size());
        assertTrue(updatedLowerBuildings.contains(existingLowerBuilding));
        assertTrue(updatedLowerBuildings.contains(oldUpperBuilding));
        assertEquals(2, updatedUpperBuildings.size());
        assertTrue(updatedUpperBuildings.contains(era2BuildingA));
        assertTrue(updatedUpperBuildings.contains(era2BuildingB));
        assertTrue(updatedEra2Deck.isEmpty());
    }

    /**
     * Verifies that Sustenance is resolved after all other lower-row events.
     * This matters because earlier events may change a player's food state.
     */
    @Test
    void resolveEndOfRoundEventsResolvesSustenanceLast() {
        Game game = new Game();
        Player player = new Player(Color.ORANGE);
        player.addCharacter(new Hunter(Era.I, 2, false));
        game.getPlayers().add(player);

        Sustenance sustenance = new Sustenance(Era.I, Position.BOTTOM, 1, 5);
        Hunting hunting = new Hunting(Era.I, Position.BOTTOM, 1, 0);

        // Put Sustenance first to ensure the resolver moves it to the end.
        game.getLowerRow().add(sustenance);
        game.getLowerRow().add(hunting);

        game.resolveEndOfRoundEvents();

        assertEquals(0, player.getFood());
        assertEquals(0, player.getPrestigePoints());
    }

    /**
     * Verifies that hunting also triggers matching event-based buildings.
     */
    @Test
    void resolveEndOfRoundEventsAppliesHuntingEventBuildingEffects() {
        Game game = new Game();
        Player player = new Player(Color.ORANGE);

        player.addCharacter(new Hunter(Era.I, 2, false));
        player.addCharacter(new Hunter(Era.I, 2, false));
        player.addBuilding(new Event_Add_Effect(
                Era.II, Position.BOTTOM, 7, 2,
                EventType.HUNTING, CharacterType.HUNTER, 1, 1));
        game.getPlayers().add(player);
        game.getLowerRow().add(new Hunting(Era.I, Position.BOTTOM, 1, 2));

        game.resolveEndOfRoundEvents();

        assertEquals(4, player.getFood());
        assertEquals(6, player.getPrestigePoints());
    }

    /**
     * Verifies that rock paintings also triggers matching event-based buildings.
     */
    @Test
    void resolveEndOfRoundEventsAppliesRockPaintingsEventBuildingEffects() {
        Game game = new Game();
        Player player = new Player(Color.BLUE);

        player.addCharacter(new Artist(Era.I, 2));
        player.addCharacter(new Artist(Era.I, 2));
        player.addBuilding(new Event_Add_Effect(
                Era.II, Position.BOTTOM, 5, 6,
                EventType.ROCK_PAINTINGS, CharacterType.ARTIST, 1, 0));
        game.getPlayers().add(player);
        game.getLowerRow().add(new RockPaintings(Era.II, Position.BOTTOM, 2, 0, 2, -2));

        game.resolveEndOfRoundEvents();

        assertEquals(2, player.getFood());
        assertEquals(4, player.getPrestigePoints());
    }

    /**
     * Verifies that Shamanic Ritual uses stars from shamans and ritual buildings.
     */
    @Test
    void resolveEndOfRoundEventsScoresShamanicRitualFromPlayerStars() {
        Game game = new Game();
        Player mostStars = new Player(Color.ORANGE);
        Player leastStars = new Player(Color.BLUE);
        Player middleStars = new Player(Color.PURPLE);

        mostStars.addCharacter(new Shaman(Era.I, 2, 2));
        mostStars.addBuilding(new AddStar(Era.II, Position.BOTTOM, 6, 4, 3));
        middleStars.addCharacter(new Shaman(Era.I, 2, 1));

        game.getPlayers().add(mostStars);
        game.getPlayers().add(leastStars);
        game.getPlayers().add(middleStars);
        game.getLowerRow().add(new ShamanicRitual(Era.I, Position.BOTTOM, 5, -3));

        game.resolveEndOfRoundEvents();

        assertEquals(5, mostStars.getPrestigePoints());
        assertEquals(-3, leastStars.getPrestigePoints());
        assertEquals(0, middleStars.getPrestigePoints());
    }

    /**
     * Verifies that handleEraChange leaves state untouched when the era is unchanged.
     */
    @Test
    void handleEraChangeDoesNothingWhenEraIsUnchanged() throws Exception {
        Game game = new Game();
        List<Building> upperBuildings = new ArrayList<>();
        List<Building> lowerBuildings = new ArrayList<>();

        upperBuildings.add(new Building(Era.I, Position.TOP, 1, 1));
        lowerBuildings.add(new Building(Era.I, Position.BOTTOM, 2, 2));

        setPrivateField(game, "upperBuildings", upperBuildings);
        setPrivateField(game, "lowerBuildings", lowerBuildings);

        game.handleEraChange(Era.I);

        assertEquals(Era.I, game.getCurrentEra());
        assertEquals(1, getPrivateField(game, "upperBuildings", List.class).size());
        assertEquals(1, getPrivateField(game, "lowerBuildings", List.class).size());
    }

    /**
     * Verifies that transitioning to era III loads buildings from the corresponding deck.
     */
    @Test
    void handleEraChangeLoadsEraThreeBuildings() throws Exception {
        Game game = new Game();
        Building oldUpperBuilding = new Building(Era.I, Position.TOP, 1, 1);
        Building eraThreeBuilding = new Building(Era.III, Position.TOP, 4, 5);
        Building existingLowerBuilding = new Building(Era.I, Position.BOTTOM, 2, 2);
        List<Building> upperBuildings = new ArrayList<>();
        List<Building> lowerBuildings = new ArrayList<>();
        Stack<Building> era3Deck = new Stack<>();

        upperBuildings.add(oldUpperBuilding);
        lowerBuildings.add(existingLowerBuilding);
        era3Deck.push(eraThreeBuilding);

        setPrivateField(game, "upperBuildings", upperBuildings);
        setPrivateField(game, "lowerBuildings", lowerBuildings);
        setPrivateField(game, "era3BuildingDeck", era3Deck);

        game.handleEraChange(Era.III);

        assertEquals(Era.III, game.getCurrentEra());
        assertEquals(1, getPrivateField(game, "lowerBuildings", List.class).size());
        assertTrue(getPrivateField(game, "lowerBuildings", List.class).contains(oldUpperBuilding));
        assertFalse(getPrivateField(game, "lowerBuildings", List.class).contains(existingLowerBuilding));
        assertEquals(Position.DECK, existingLowerBuilding.getPosition());
        assertEquals(1, getPrivateField(game, "upperBuildings", List.class).size());
        assertTrue(getPrivateField(game, "upperBuildings", List.class).contains(eraThreeBuilding));
        assertTrue(getPrivateField(game, "era3BuildingDeck", Stack.class).isEmpty());
    }

    /**
     * Verifies that executeRound triggers first-round setup when round one starts with empty rows.
     */
    @Test
    void executeRoundTriggersFirstRoundPreparationWhenBoardIsEmpty() throws Exception {
        SpyGame game = new SpyGame();

        game.executeRound();

        assertEquals(1, game.prepareOfferTilesCalls);
        assertEquals(1, game.prepareFirstRoundCalls);
        assertEquals(1, game.resolveEndOfRoundEventsCalls);
        assertEquals(1, game.manageCardsCalls);
        assertEquals(2, game.getRound());
    }

    /**
     * Verifies that executeRound skips first-round setup if the board is already populated.
     */
    @Test
    void executeRoundSkipsFirstRoundPreparationWhenBoardAlreadyPrepared() throws Exception {
        SpyGame game = new SpyGame();
        game.getUpperRow().add(new Gatherer(Era.I, 2, 1));

        game.executeRound();

        assertEquals(1, game.prepareOfferTilesCalls);
        assertEquals(0, game.prepareFirstRoundCalls);
        assertEquals(1, game.resolveEndOfRoundEventsCalls);
        assertEquals(1, game.manageCardsCalls);
        assertEquals(2, game.getRound());
    }

    /**
     * Verifies upper and lower tribe-card acquisition together with argument validation.
     */
    @Test
    void takeTribeCardsAssignsCharactersAndRejectsInvalidSelections() {
        Game game = new Game();
        Player player = new Player(Color.BLUE);
        Hunter upper = new Hunter(Era.I, 2, false);
        Artist lower = new Artist(Era.I, 2);
        Hunting event = new Hunting(Era.I, Position.TOP, 1, 1);
        game.getUpperRow().add(event);
        game.getUpperRow().add(upper);
        game.getLowerRow().add(lower);

        assertThrows(IllegalArgumentException.class, () -> game.takeUpperTribeCard(null, 0));
        assertThrows(IllegalArgumentException.class, () -> game.takeUpperTribeCard(player, -1));
        assertThrows(IllegalArgumentException.class, () -> game.takeUpperTribeCard(player, 0));

        assertEquals(upper, game.takeUpperTribeCard(player, 1));
        assertEquals(lower, game.takeLowerTribeCard(player, 0));
        assertTrue(player.getCharacters().contains(upper));
        assertTrue(player.getCharacters().contains(lower));
        assertThrows(IllegalArgumentException.class, () -> game.takeLowerTribeCard(player, 0));
    }

    /**
     * Verifies building acquisition, Builder discounts, food payment, and invalid purchases.
     */
    @Test
    void takeBuildingCardsAppliesDiscountAndValidatesPurchases() {
        Game game = new Game();
        Player player = new Player(Color.ORANGE);
        player.receiveFood(10);
        player.addCharacter(new Builder(Era.I, 2, 2, 1));
        Building upper = new Building(Era.I, Position.TOP, 5, 2);
        Building lower = new Building(Era.I, Position.BOTTOM, 4, 1);
        game.getUpperBuildings().add(upper);
        game.getLowerBuildings().add(lower);

        assertThrows(IllegalArgumentException.class, () -> game.takeUpperBuildingCard(null, 0));
        assertThrows(IllegalArgumentException.class, () -> game.takeUpperBuildingCard(player, -1));
        assertEquals(upper, game.takeUpperBuildingCard(player, 0));
        assertEquals(7, player.getFood());
        assertEquals(lower, game.takeLowerBuildingCard(player, 0));
        assertEquals(5, player.getFood());
        assertTrue(player.getBuildings().contains(upper));
        assertTrue(player.getBuildings().contains(lower));

        Player poorPlayer = new Player(Color.YELLOW);
        game.getLowerBuildings().add(new Building(Era.I, Position.BOTTOM, 3, 1));
        assertThrows(IllegalArgumentException.class, () -> game.takeLowerBuildingCard(null, 0));
        assertThrows(IllegalArgumentException.class, () -> game.takeLowerBuildingCard(poorPlayer, 2));
        assertThrows(IllegalArgumentException.class, () -> game.takeLowerBuildingCard(poorPlayer, 0));
    }

    /**
     * Verifies that toString exposes the core game counters for diagnostics.
     */
    @Test
    void toStringContainsCoreStateCounters() {
        Game game = new Game();

        String summary = game.toString();

        assertTrue(summary.contains("round=1"));
        assertTrue(summary.contains("currentEra=I"));
        assertTrue(summary.contains("players=0"));
        assertTrue(summary.contains("upperRow=0"));
        assertTrue(summary.contains("lowerRow=0"));
    }

    /**
     * Sets a private field value on Game to exercise internal paths.
     */
    private static void setPrivateField(Game game, String fieldName, Object value) throws Exception {
        Field field = Game.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(game, value);
    }

    /**
     * Reads a private Game field for test assertions.
     */
    private static <T> T getPrivateField(Game game, String fieldName, Class<T> type) throws Exception {
        Field field = Game.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return type.cast(field.get(game));
    }

    /**
     * Reads a private Game field expected to contain a List of Building instances.
     */
    @SuppressWarnings("unchecked")
    private static List<Building> getPrivateBuildingListField(Game game, String fieldName) throws Exception {
        Field field = Game.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (List<Building>) field.get(game);
    }

    /**
     * Reads a private Game field expected to contain a Stack of Building instances.
     */
    @SuppressWarnings("unchecked")
    private static Stack<Building> getPrivateBuildingStackField(Game game, String fieldName) throws Exception {
        Field field = Game.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Stack<Building>) field.get(game);
    }

    /**
     * Test double used to isolate executeRound control flow from data loading.
     */
    private static class SpyGame extends Game {
        private int prepareOfferTilesCalls;
        private int prepareFirstRoundCalls;
        private int resolveEndOfRoundEventsCalls;
        private int manageCardsCalls;

        @Override
        public void prepareOfferTiles() {
            prepareOfferTilesCalls++;
        }

        @Override
        public void prepareFirstRound() {
            prepareFirstRoundCalls++;
        }

        @Override
        public void resolveEndOfRoundEvents() {
            resolveEndOfRoundEventsCalls++;
        }

        @Override
        public void manageCards() {
            manageCardsCalls++;
        }
    }
}
