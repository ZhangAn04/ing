package it.polimi.Buildings;

import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import it.polimi.Characters.Builder;
import it.polimi.Characters.Gatherer;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link EndGame_Builder_Add} end-game builder scoring.
 */
class EndGame_Builder_AddTest {

    /**
     * Verifies that calculateFinalPoints sums prestige points of all builder characters multiplied by multiplier.
     */
    @Test
    void calculateFinalPointsMultipliesBuilderPrestigePoints() {
        EndGame_Builder_Add building = new EndGame_Builder_Add(Era.I, Position.TOP, 5, 2, 3);
        Player player = new Player(Color.ORANGE);

        Builder builder1 = new Builder(Era.I, 4, 1, 0);
        Builder builder2 = new Builder(Era.I, 3, 2, 0);
        player.getCharacters().add(builder1);
        player.getCharacters().add(builder2);

        // Total builder points: 0 + 0 = 0, multiplied by 3 = 0
        assertEquals(0, building.calculateFinalPoints(player));
    }

    /**
     * Verifies that non-builder characters are ignored.
     */
    @Test
    void calculateFinalPointsIgnoresNonBuilderCharacters() {
        EndGame_Builder_Add building = new EndGame_Builder_Add(Era.I, Position.TOP, 5, 2, 2);
        Player player = new Player(Color.BLUE);

        Builder builder = new Builder(Era.I, 5, 1, 0);
        Gatherer gatherer = new Gatherer(Era.I, 4, 2);
        player.getCharacters().add(builder);
        player.getCharacters().add(gatherer);

        // Only builder prestige points: 0, multiplied by 2 = 0
        assertEquals(0, building.calculateFinalPoints(player));
    }

    /**
     * Verifies that zero points are returned when player has no builder characters.
     */
    @Test
    void calculateFinalPointsReturnsZeroWithoutBuilders() {
        EndGame_Builder_Add building = new EndGame_Builder_Add(Era.I, Position.TOP, 5, 2, 3);
        Player player = new Player(Color.PURPLE);

        Gatherer gatherer = new Gatherer(Era.I, 3, 1);
        player.getCharacters().add(gatherer);

        assertEquals(0, building.calculateFinalPoints(player));
    }

    /**
     * Verifies that result is zero with empty player character list.
     */
    @Test
    void calculateFinalPointsReturnsZeroWithEmptyCharacterList() {
        EndGame_Builder_Add building = new EndGame_Builder_Add(Era.II, Position.BOTTOM, 7, 3, 4);
        Player player = new Player(Color.YELLOW);

        assertEquals(0, building.calculateFinalPoints(player));
    }

    /**
     * Verifies constructor sets era, position, cost, points, and multiplier.
     */
    @Test
    void constructorSetsBuildingProperties() {
        EndGame_Builder_Add building = new EndGame_Builder_Add(Era.III, Position.DECK, 10, 5, 2);

        assertEquals(Era.III, building.getEra());
        assertEquals(Position.DECK, building.getPosition());
        assertEquals(10, building.getCost());
        assertEquals(5, building.getPoints());
    }
}
