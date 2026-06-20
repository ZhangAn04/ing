package it.polimi.Buildings;

import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.EventType;
import it.polimi.Constants.Position;
import it.polimi.Events.Hunting;
import it.polimi.Events.Sustenance;
import it.polimi.Characters.Hunter;
import it.polimi.Characters.Gatherer;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link Event_Add_Effect} event-based scoring.
 */
class Event_Add_EffectTest {

    /**
     * Verifies that applyEventsEffect awards food and prestige when event matches.
     */
    @Test
    void applyEventsEffectAwardsRewardOnEventMatch() {
        Event_Add_Effect building = new Event_Add_Effect(
                Era.I, Position.TOP, 5, 2,
                EventType.HUNTING, CharacterType.HUNTER, 3, 2);
        Player player = new Player(Color.ORANGE);

        Hunter h1 = new Hunter(Era.I, 2, false);
        Hunter h2 = new Hunter(Era.I, 2, false);
        player.getCharacters().add(h1);
        player.getCharacters().add(h2);

        int initialFood = player.getFood();
        int initialPrestige = player.getPrestigePoints();

        Hunting hunting = new Hunting(Era.I, Position.TOP, 2, 1);
        building.applyEventsEffect(player, hunting);

        // 2 hunters * 3 food = 6 food, 2 hunters * 2 prestige = 4 prestige
        assertEquals(initialFood + 6, player.getFood());
        assertEquals(initialPrestige + 4, player.getPrestigePoints());
    }

    /**
     * Verifies that applyEventsEffect does nothing when event does not match.
     */
    @Test
    void applyEventsEffectIgnoresNonMatchingEvent() {
        Event_Add_Effect building = new Event_Add_Effect(
                Era.I, Position.TOP, 5, 2,
                EventType.HUNTING, CharacterType.HUNTER, 3, 2);
        Player player = new Player(Color.BLUE);

        Hunter h = new Hunter(Era.I, 2, false);
        player.getCharacters().add(h);

        int initialFood = player.getFood();
        int initialPrestige = player.getPrestigePoints();

        Sustenance sustenance = new Sustenance(Era.I, Position.BOTTOM, 1, 2);
        building.applyEventsEffect(player, sustenance);

        // No change should occur
        assertEquals(initialFood, player.getFood());
        assertEquals(initialPrestige, player.getPrestigePoints());
    }

    /**
     * Verifies that no reward is given when player lacks target character type.
     */
    @Test
    void applyEventsEffectNoRewardWithoutTargetCharacters() {
        Event_Add_Effect building = new Event_Add_Effect(
                Era.II, Position.BOTTOM, 6, 3,
                EventType.HUNTING, CharacterType.BUILDER, 2, 1);
        Player player = new Player(Color.PURPLE);

        Hunter h = new Hunter(Era.II, 3, false);
        player.getCharacters().add(h);

        int initialFood = player.getFood();
        int initialPrestige = player.getPrestigePoints();

        Hunting hunting = new Hunting(Era.II, Position.TOP, 2, 1);
        building.applyEventsEffect(player, hunting);

        // No change (no builders)
        assertEquals(initialFood, player.getFood());
        assertEquals(initialPrestige, player.getPrestigePoints());
    }

    /**
     * Verifies that constructor sets all properties correctly.
     */
    @Test
    void constructorSetsBuildingProperties() {
        Event_Add_Effect building = new Event_Add_Effect(
                Era.III, Position.DECK, 10, 5,
                EventType.RITUAL, CharacterType.INVENTOR, 4, 3);

        assertEquals(Era.III, building.getEra());
        assertEquals(Position.DECK, building.getPosition());
        assertEquals(10, building.getCost());
        assertEquals(5, building.getPoints());
    }
}
