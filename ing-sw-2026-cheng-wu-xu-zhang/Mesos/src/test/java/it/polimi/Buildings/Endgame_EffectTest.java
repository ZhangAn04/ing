package it.polimi.Buildings;

import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import it.polimi.Characters.Builder;
import it.polimi.Characters.Gatherer;
import it.polimi.Characters.Hunter;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link Endgame_Effect} scoring effects.
 */
class Endgame_EffectTest {

    /**
     * Verifies that bonus points are awarded when all target requirements are met.
     */
    @Test
    void calculateFinalPointsAwardsBonus() {
        Endgame_Effect building = new Endgame_Effect(
                Era.I, Position.TOP, 5, 3,
                Arrays.asList(CharacterType.HUNTER),
                2, 5);
        Player player = new Player(Color.ORANGE);

        Hunter h1 = new Hunter(Era.I, 2, false);
        Hunter h2 = new Hunter(Era.I, 2, false);
        player.getCharacters().add(h1);
        player.getCharacters().add(h2);

        // Base points: 3 + bonus: 5 = 8
        assertEquals(8, building.calculateFinalPoints(player));
    }

    /**
     * Verifies that bonus is not awarded when requirements are not met.
     */
    @Test
    void calculateFinalPointsNoBonus() {
        Endgame_Effect building = new Endgame_Effect(
                Era.I, Position.TOP, 5, 3,
                Arrays.asList(CharacterType.BUILDER),
                3, 5);
        Player player = new Player(Color.BLUE);

        Builder b1 = new Builder(Era.I, 2, 1, 0);
        Builder b2 = new Builder(Era.I, 2, 1, 0);
        player.getCharacters().add(b1);
        player.getCharacters().add(b2);

        // Only base points: 3
        assertEquals(3, building.calculateFinalPoints(player));
    }

    /**
     * Verifies that all target types must be satisfied simultaneously.
     */
    @Test
    void calculateFinalPointsRequiresAllTargets() {
        Endgame_Effect building = new Endgame_Effect(
                Era.II, Position.BOTTOM, 6, 4,
                Arrays.asList(CharacterType.HUNTER, CharacterType.BUILDER),
                1, 6);
        Player player = new Player(Color.PURPLE);

        Hunter h = new Hunter(Era.II, 2, false);
        player.getCharacters().add(h);

        // Only one target type satisfied, so no bonus
        assertEquals(4, building.calculateFinalPoints(player));
    }

    /**
     * Verifies handling of null target list.
     */
    @Test
    void calculateFinalPointsHandlesNullTarget() {
        Endgame_Effect building = new Endgame_Effect(
                Era.I, Position.TOP, 5, 2, null, 1, 3);
        Player player = new Player(Color.YELLOW);

        // No targets => unconditional bonus
        assertEquals(5, building.calculateFinalPoints(player));
    }

    /**
     * Verifies handling of empty target list.
     */
    @Test
    void calculateFinalPointsHandlesEmptyTarget() {
        Endgame_Effect building = new Endgame_Effect(
                Era.I, Position.TOP, 5, 2,
                Arrays.asList(), 1, 3);
        Player player = new Player(Color.ORANGE);

        // No targets => unconditional bonus
        assertEquals(5, building.calculateFinalPoints(player));
    }

    /**
     * Verifies getPoints returns the base points.
     */
    @Test
    void getPointsReturnsBasePoints() {
        Endgame_Effect building = new Endgame_Effect(
                Era.III, Position.DECK, 10, 7,
                Arrays.asList(CharacterType.INVENTOR),
                2, 5);

        assertEquals(7, building.getPoints());
    }
}
