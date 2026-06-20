package it.polimi.Buildings;

import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import it.polimi.Characters.Artist;
import it.polimi.Characters.Builder;
import it.polimi.Characters.Gatherer;
import it.polimi.Characters.Hunter;
import it.polimi.Characters.Inventor;
import it.polimi.Characters.Shaman;
import it.polimi.Constants.InventorIcon;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link InGame_Effect} immediate building effects.
 */
class InGame_EffectTest {

    /**
     * Verifies that applyContinuousEffect awards food when all target requirements are met.
     */
    @Test
    void applyContinuousEffectAwardsFoodWhenRequirementsMet() {
        InGame_Effect building = new InGame_Effect(
                Era.I, Position.TOP, 5, 2,
                Arrays.asList(CharacterType.GATHERER),
                2, 4);
        Player player = new Player(Color.ORANGE);

        Gatherer g1 = new Gatherer(Era.I, 2, 1);
        Gatherer g2 = new Gatherer(Era.I, 2, 1);
        player.getCharacters().add(g1);
        player.getCharacters().add(g2);

        int initialFood = player.getFood();
        building.applyContinuousEffect(player);

        // 1 satisfied target * 4 meat = 4 food
        assertEquals(initialFood + 4, player.getFood());
    }

    /**
     * Verifies that effect does not award food when gatherer count is below threshold.
     */
    @Test
    void applyContinuousEffectNoFoodWhenGathererBelowThreshold() {
        InGame_Effect building = new InGame_Effect(
                Era.I, Position.TOP, 5, 2,
                Arrays.asList(CharacterType.GATHERER),
                3, 5);
        Player player = new Player(Color.ORANGE);

        Gatherer g1 = new Gatherer(Era.I, 2, 1);
        Gatherer g2 = new Gatherer(Era.I, 2, 1);
        player.getCharacters().add(g1);
        player.getCharacters().add(g2);

        int initialFood = player.getFood();
        building.applyContinuousEffect(player);

        // Only 2 gatherers, need 3, so no food
        assertEquals(initialFood, player.getFood());
    }

    /**
     * Verifies that no food is awarded when requirements are not met.
     */
    @Test
    void applyContinuousEffectNoFoodWhenRequirementsNotMet() {
        InGame_Effect building = new InGame_Effect(
                Era.I, Position.TOP, 5, 2,
                Arrays.asList(CharacterType.BUILDER),
                3, 5);
        Player player = new Player(Color.BLUE);

        Builder b1 = new Builder(Era.I, 2, 1, 0);
        Builder b2 = new Builder(Era.I, 2, 1, 0);
        player.getCharacters().add(b1);
        player.getCharacters().add(b2);

        int initialFood = player.getFood();
        building.applyContinuousEffect(player);

        // Requirements not met (only 2 builders, need 3)
        assertEquals(initialFood, player.getFood());
    }

    /**
     * Verifies effect with multiple target types.
     */
    @Test
    void applyContinuousEffectMultipleTargets() {
        InGame_Effect building = new InGame_Effect(
                Era.II, Position.BOTTOM, 6, 3,
                Arrays.asList(CharacterType.BUILDER, CharacterType.GATHERER),
                1, 3);
        Player player = new Player(Color.PURPLE);

        Builder b = new Builder(Era.II, 2, 1, 0);
        Gatherer g = new Gatherer(Era.II, 2, 1);
        player.getCharacters().add(b);
        player.getCharacters().add(g);

        int initialFood = player.getFood();
        building.applyContinuousEffect(player);

        // 2 satisfied targets * 3 meat = 6 food
        assertEquals(initialFood + 6, player.getFood());
    }

    /**
     * Verifies effect with empty character list.
     */
    @Test
    void applyContinuousEffectWithEmptyCharacterList() {
        InGame_Effect building = new InGame_Effect(
                Era.I, Position.TOP, 5, 2,
                Arrays.asList(CharacterType.HUNTER),
                1, 4);
        Player player = new Player(Color.YELLOW);

        int initialFood = player.getFood();
        building.applyContinuousEffect(player);

        // No characters, so no food
        assertEquals(initialFood, player.getFood());
    }

    /**
     * Verifies effect with threshold of 0.
     */
    @Test
    void applyContinuousEffectWithZeroThreshold() {
        InGame_Effect building = new InGame_Effect(
                Era.I, Position.TOP, 5, 2,
                Arrays.asList(CharacterType.ARTIST),
                0, 5);
        Player player = new Player(Color.YELLOW);

        Artist a = new Artist(Era.I, 3);
        player.getCharacters().add(a);

        int initialFood = player.getFood();
        building.applyContinuousEffect(player);

        // Threshold is 0, all players have >= 0 artists, so award food
        assertEquals(initialFood + 5, player.getFood());
    }

    /**
     * Verifies constructor sets all properties correctly.
     */
    @Test
    void constructorSetsBuildingProperties() {
        InGame_Effect building = new InGame_Effect(
                Era.III, Position.DECK, 10, 5,
                Arrays.asList(CharacterType.INVENTOR, CharacterType.ARTIST),
                2, 6);

        assertEquals(Era.III, building.getEra());
        assertEquals(Position.DECK, building.getPosition());
        assertEquals(10, building.getCost());
        assertEquals(5, building.getPoints());
    }

    /**
     * Verifies that an absent target configuration never awards food.
     */
    @Test
    void applyContinuousEffectIgnoresMissingTargets() {
        Player player = new Player(Color.BLUE);
        InGame_Effect nullTarget = new InGame_Effect(Era.I, Position.TOP, 1, 1, null, 1, 4);
        InGame_Effect emptyTarget = new InGame_Effect(
                Era.I, Position.TOP, 1, 1, Collections.emptyList(), 1, 4);

        nullTarget.applyContinuousEffect(player);
        emptyTarget.applyContinuousEffect(player);

        assertEquals(0, player.getFood());
    }

    /**
     * Verifies complete-set counting across all six character types and incremental rewards.
     */
    @Test
    void applyContinuousEffectRewardsCompleteSixTypeSetsIncrementally() {
        InGame_Effect building = new InGame_Effect(
                Era.II, Position.TOP, 4, 2,
                Arrays.asList(CharacterType.HUNTER, CharacterType.GATHERER, CharacterType.ARTIST,
                        CharacterType.SHAMAN, CharacterType.BUILDER, CharacterType.INVENTOR),
                1, 3);
        Player player = new Player(Color.WHITE);

        addCompleteCharacterSet(player);
        building.applyContinuousEffect(player);
        assertEquals(3, player.getFood());

        building.applyContinuousEffect(player);
        assertEquals(3, player.getFood());

        addCompleteCharacterSet(player);
        building.applyContinuousEffect(player);
        assertEquals(6, player.getFood());
    }

    /**
     * Verifies that Inventor rewards count pairs sharing the same invention icon.
     */
    @Test
    void applyContinuousEffectCountsMatchingInventorPairs() {
        InGame_Effect building = new InGame_Effect(
                Era.II, Position.TOP, 4, 2,
                Collections.singletonList(CharacterType.INVENTOR), 2, 5);
        Player player = new Player(Color.PURPLE);
        player.addCharacter(new Inventor(Era.I, 2, InventorIcon.rope));
        player.addCharacter(new Inventor(Era.I, 2, InventorIcon.rope));
        player.addCharacter(new Inventor(Era.I, 2, InventorIcon.arrow));

        building.applyContinuousEffect(player);

        assertEquals(5, player.getFood());
    }

    /**
     * Adds one character of every supported type to a player.
     *
     * @param player player receiving the complete set
     */
    private static void addCompleteCharacterSet(Player player) {
        player.addCharacter(new Hunter(Era.I, 2, false));
        player.addCharacter(new Gatherer(Era.I, 2, 1));
        player.addCharacter(new Artist(Era.I, 2));
        player.addCharacter(new Shaman(Era.I, 2, 1));
        player.addCharacter(new Builder(Era.I, 2, 1, 1));
        player.addCharacter(new Inventor(Era.I, 2, InventorIcon.bowl));
    }
}
