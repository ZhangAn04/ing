package it.polimi.Game.Core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.Abstract.Character;
import it.polimi.Abstract.Event;
import it.polimi.Abstract.Building;
import it.polimi.Characters.Shaman;
import it.polimi.Constants.InventorIcon;
import it.polimi.Characters.Inventor;
import it.polimi.Constants.Position;
import it.polimi.Events.Hunting;
import it.polimi.Events.RockPaintings;
import it.polimi.Events.ShamanicRitual;
import it.polimi.Events.Sustenance;
import it.polimi.Game.Elements.OfferTile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CardLoader} resource loading.
 */
class CardLoaderTest {

    /**
     * Verifies that characters can be loaded from the bundled JSON resource.
     */
    @Test
    void loadCharactersReturnsData() throws Exception {
        CardLoader loader = new CardLoader();

        List<Character> characters = loader.loadCharacters();

        assertNotNull(characters);
        assertFalse(characters.isEmpty());
    }

    /**
     * Verifies that inventor icons are parsed correctly as enum values.
     */
    @Test
    void loadCharactersParsesInventorIconsAsEnum() throws Exception {
        CardLoader loader = new CardLoader();
        List<Character> characters = loader.loadCharacters();

        List<Inventor> inventors = characters.stream()
                .filter(Inventor.class::isInstance)
                .map(Inventor.class::cast)
                .collect(Collectors.toList());

        assertFalse(inventors.isEmpty());
        assertTrue(inventors.stream().allMatch(i -> i.getIcon() != null));
        assertTrue(inventors.stream().anyMatch(i -> i.getIcon() == InventorIcon.arrow));
    }

    /**
     * Verifies that shaman star values are parsed from characters.json.
     */
    @Test
    void loadCharactersParsesShamanStars() throws Exception {
        CardLoader loader = new CardLoader();
        List<Character> characters = loader.loadCharacters();

        List<Shaman> shamans = characters.stream()
                .filter(Shaman.class::isInstance)
                .map(Shaman.class::cast)
                .collect(Collectors.toList());

        assertFalse(shamans.isEmpty());
        assertTrue(shamans.stream().allMatch(shaman -> shaman.getStar() > 0));
        assertTrue(shamans.stream().anyMatch(shaman -> shaman.getStar() == 3));
    }

    /**
     * Verifies that events are loaded as concrete event subclasses with deck position.
     */
    @Test
    void loadEventsBuildsConcreteEventInstances() throws Exception {
        CardLoader loader = new CardLoader();

        List<Event> events = loader.loadEvents();

        assertNotNull(events);
        assertFalse(events.isEmpty());
        assertTrue(events.stream().allMatch(e -> e.getPosition() == Position.DECK));
        assertTrue(events.stream().anyMatch(Hunting.class::isInstance));
        assertTrue(events.stream().anyMatch(Sustenance.class::isInstance));
        assertTrue(events.stream().anyMatch(ShamanicRitual.class::isInstance));
        assertTrue(events.stream().anyMatch(RockPaintings.class::isInstance));

        long huntingCount = events.stream().filter(Hunting.class::isInstance).count();
        long sustenanceCount = events.stream().filter(Sustenance.class::isInstance).count();
        long ritualCount = events.stream().filter(ShamanicRitual.class::isInstance).count();
        long paintingsCount = events.stream().filter(RockPaintings.class::isInstance).count();

        assertEquals(3, huntingCount);
        assertEquals(2, sustenanceCount);
        assertEquals(2, ritualCount);
        assertEquals(3, paintingsCount);
    }

    /**
     * Verifies that building data can be loaded from the bundled resource.
     */
    @Test
    void loadBuildingsReturnsData() throws Exception {
        CardLoader loader = new CardLoader();

        List<Building> buildings = loader.loadBuildings();

        assertNotNull(buildings);
        assertFalse(buildings.isEmpty());
        assertTrue(buildings.stream().allMatch(building -> building.getEra() != null));
    }

    /**
     * Verifies that offer tiles can be loaded from the bundled resource.
     */
    @Test
    void loadOfferTilesReturnsData() throws Exception {
        CardLoader loader = new CardLoader();

        List<OfferTile> tiles = loader.loadOfferTiles();

        assertNotNull(tiles);
        assertFalse(tiles.isEmpty());
        assertTrue(tiles.stream().anyMatch(tile -> tile.availableFor(2)));
    }

    /**
     * Verifies that turn-order track rules are loaded from start.json.
     */
    @Test
    void loadTurnOrderTrackRulesReturnsStartData() throws Exception {
        CardLoader loader = new CardLoader();

        Map<Integer, CardLoader.TurnOrderTrackRule[]> rules = loader.loadTurnOrderTrackRules();

        assertEquals(4, rules.size());
        assertEquals(1, rules.get(2)[0].getFoodDelta());
        assertEquals(-1, rules.get(2)[1].getFoodDelta());
        assertEquals(2, rules.get(2)[1].getPrestigePenalty());
        assertEquals(3, rules.get(5)[0].getFoodDelta());
        assertEquals(-1, rules.get(5)[4].getFoodDelta());
        assertEquals(2, rules.get(5)[4].getPrestigePenalty());
    }

    /**
     * Verifies that the private event parser supports legacy HUNT alias.
     */
    @Test
    void parseEventNodeSupportsHuntAlias() throws Exception {
        CardLoader loader = new CardLoader();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree("{\"type\":\"HUNT\",\"era\":\"I\",\"foodPerHunter\":2,\"ppPerHunter\":1}");

        Event parsed = (Event) invokePrivate(loader, "parseEventNode", new Class<?>[]{JsonNode.class}, node);

        assertInstanceOf(Hunting.class, parsed);
    }

    /**
     * Verifies that each declared event type maps to the expected concrete class.
     */
    @Test
    void parseEventNodeMapsAllSupportedTypes() throws Exception {
        CardLoader loader = new CardLoader();
        ObjectMapper mapper = new ObjectMapper();

        JsonNode sustenanceNode = mapper.readTree("{\"type\":\"SUSTENANCE\",\"era\":\"I\",\"foodPerCharacter\":1,\"ppPenaltyPerUnfed\":2}");
        JsonNode ritualNode = mapper.readTree("{\"type\":\"RITUAL\",\"era\":\"II\",\"ppRewardMost\":2,\"ppPenaltyLeast\":1}");
        JsonNode paintingsNode = mapper.readTree("{\"type\":\"ROCK_PAINTINGS\",\"era\":\"III\",\"thresholdHigh\":5,\"thresholdLow\":1,\"ppIfMet\":3,\"ppIfNotMet\":-2}");

        assertInstanceOf(Sustenance.class, invokePrivate(loader, "parseEventNode", new Class<?>[]{JsonNode.class}, sustenanceNode));
        assertInstanceOf(ShamanicRitual.class, invokePrivate(loader, "parseEventNode", new Class<?>[]{JsonNode.class}, ritualNode));
        assertInstanceOf(RockPaintings.class, invokePrivate(loader, "parseEventNode", new Class<?>[]{JsonNode.class}, paintingsNode));
    }

    /**
     * Verifies that HUNTING alias is handled like HUNT.
     */
    @Test
    void parseEventNodeSupportsHuntingAlias() throws Exception {
        CardLoader loader = new CardLoader();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree("{\"type\":\"HUNTING\",\"era\":\"I\",\"foodPerHunter\":2,\"ppPerHunter\":1}");

        assertInstanceOf(Hunting.class, invokePrivate(loader, "parseEventNode", new Class<?>[]{JsonNode.class}, node));
    }

    /**
     * Verifies that unknown event types are rejected by the private parser.
     */
    @Test
    void parseEventNodeRejectsUnknownType() throws Exception {
        CardLoader loader = new CardLoader();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree("{\"type\":\"UNKNOWN\",\"era\":\"I\"}");

        assertThrows(IllegalArgumentException.class,
                () -> invokePrivate(loader, "parseEventNode", new Class<?>[]{JsonNode.class}, node));
    }

    /**
     * Verifies that missing required fields are rejected by private helper methods.
     */
    @Test
    void requiredFieldHelpersRejectMissingValues() throws Exception {
        CardLoader loader = new CardLoader();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree("{}");

        assertThrows(IllegalArgumentException.class,
                () -> invokePrivate(loader, "requiredText", new Class<?>[]{JsonNode.class, String.class}, node, "type"));
        assertThrows(IllegalArgumentException.class,
                () -> invokePrivate(loader, "requiredInt", new Class<?>[]{JsonNode.class, String.class}, node, "value"));
    }

        /**
         * Verifies that explicit JSON null values are rejected by required helpers.
         */
        @Test
        void requiredFieldHelpersRejectExplicitNullValues() throws Exception {
        CardLoader loader = new CardLoader();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree("{\"name\":null,\"count\":null}");

        assertThrows(IllegalArgumentException.class,
            () -> invokePrivate(loader, "requiredText", new Class<?>[]{JsonNode.class, String.class}, node, "name"));
        assertThrows(IllegalArgumentException.class,
            () -> invokePrivate(loader, "requiredInt", new Class<?>[]{JsonNode.class, String.class}, node, "count"));
        }

    /**
     * Verifies that required field helpers return values when fields are present.
     */
    @Test
    void requiredFieldHelpersReturnValuesWhenPresent() throws Exception {
        CardLoader loader = new CardLoader();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree("{\"name\":\"ok\",\"count\":7}");

        assertEquals("ok", invokePrivate(loader, "requiredText", new Class<?>[]{JsonNode.class, String.class}, node, "name"));
        assertEquals(7, invokePrivate(loader, "requiredInt", new Class<?>[]{JsonNode.class, String.class}, node, "count"));
    }

    /**
     * Verifies that Jackson container DTOs expose working getters and setters.
     */
    @Test
    void containerDtosExposeGettersAndSetters() {
        CardLoader.CharactersData charactersData = new CardLoader.CharactersData();
        CardLoader.EventsData eventsData = new CardLoader.EventsData();
        CardLoader.BuildingsData buildingsData = new CardLoader.BuildingsData();
        CardLoader.OfferTilesData offerTilesData = new CardLoader.OfferTilesData();

        List<Character> characters = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        List<Building> buildings = new ArrayList<>();
        List<OfferTile> tiles = new ArrayList<>();

        charactersData.setCharacters(characters);
        eventsData.setEvents(events);
        buildingsData.setBuildings(buildings);
        offerTilesData.setOfferTiles(tiles);

        assertEquals(characters, charactersData.getCharacters());
        assertEquals(events, eventsData.getEvents());
        assertEquals(buildings, buildingsData.getBuildings());
        assertEquals(tiles, offerTilesData.getOfferTiles());
    }

    private static Object invokePrivate(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }
}
