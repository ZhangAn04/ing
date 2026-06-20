package it.polimi.Buildings;

import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import it.polimi.Characters.Gatherer;
import it.polimi.Characters.Hunter;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link Event_Sustenance_Effect} sustenance bonuses.
 */
class Event_Sustenance_EffectTest {

    /**
     * Verifies that applyDiscountEffect returns correct discount based on target character count.
     */
    @Test
    void applyDiscountEffectCalculatesDiscount() {
        Event_Sustenance_Effect building = new Event_Sustenance_Effect(
                Era.I, Position.TOP, 5, 2, 3, CharacterType.GATHERER);
        Player player = new Player(Color.ORANGE);

        Gatherer g1 = new Gatherer(Era.I, 2, 1);
        Gatherer g2 = new Gatherer(Era.I, 2, 1);
        player.getCharacters().add(g1);
        player.getCharacters().add(g2);

        // 2 gatherers * 3 food discount = 6
        assertEquals(6, building.applyDiscountEffect(player));
    }

    /**
     * Verifies that applyDiscountEffect with single gatherer.
     */
    @Test
    void applyDiscountEffectSingleGatherer() {
        Event_Sustenance_Effect building = new Event_Sustenance_Effect(
                Era.I, Position.TOP, 5, 2, 4, CharacterType.GATHERER);
        Player player = new Player(Color.ORANGE);

        Gatherer g = new Gatherer(Era.I, 2, 1);
        player.getCharacters().add(g);

        // 1 gatherer * 4 food discount = 4
        assertEquals(4, building.applyDiscountEffect(player));
    }

    /**
     * Verifies that discount is zero when player lacks target character type.
     */
    @Test
    void applyDiscountEffectReturnsZeroWithoutTargetCharacters() {
        Event_Sustenance_Effect building = new Event_Sustenance_Effect(
                Era.I, Position.TOP, 5, 2, 3, CharacterType.HUNTER);
        Player player = new Player(Color.BLUE);

        Gatherer g = new Gatherer(Era.I, 2, 1);
        player.getCharacters().add(g);

        // No hunters, discount = 0
        assertEquals(0, building.applyDiscountEffect(player));
    }

    /**
     * Verifies that discount is zero when player has no characters.
     */
    @Test
    void applyDiscountEffectReturnsZeroWithEmptyCharacterList() {
        Event_Sustenance_Effect building = new Event_Sustenance_Effect(
                Era.II, Position.BOTTOM, 6, 3, 2, CharacterType.BUILDER);
        Player player = new Player(Color.PURPLE);

        // No characters, discount = 0
        assertEquals(0, building.applyDiscountEffect(player));
    }

    /**
     * Verifies that only target character type is counted.
     */
    @Test
    void applyDiscountEffectCountsOnlyTargetType() {
        Event_Sustenance_Effect building = new Event_Sustenance_Effect(
                Era.I, Position.TOP, 5, 2, 2, CharacterType.GATHERER);
        Player player = new Player(Color.YELLOW);

        Gatherer g = new Gatherer(Era.I, 2, 1);
        Hunter h = new Hunter(Era.I, 2, false);
        player.getCharacters().add(g);
        player.getCharacters().add(h);

        // Only 1 gatherer * 2 discount = 2
        assertEquals(2, building.applyDiscountEffect(player));
    }

    /**
     * Verifies that constructor sets all properties correctly.
     */
    @Test
    void constructorSetsBuildingProperties() {
        Event_Sustenance_Effect building = new Event_Sustenance_Effect(
                Era.III, Position.DECK, 10, 5, 4, CharacterType.INVENTOR);

        assertEquals(Era.III, building.getEra());
        assertEquals(Position.DECK, building.getPosition());
        assertEquals(10, building.getCost());
        assertEquals(5, building.getPoints());
    }
}
