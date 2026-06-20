package it.polimi.Characters;

import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for the {@link Builder} character.
 */
class BuilderTest {

    /**
     * Verifies that builder constructor and getters expose the configured values.
     */
    @Test
    void constructorAndGettersExposeBuilderData() {
        Builder builder = new Builder(Era.II, 4, 2, 5);

        assertEquals(Era.II, builder.getEra());
        assertEquals(4, builder.getPlayers());
        assertEquals(Position.DECK, builder.getPosition());
        assertEquals(CharacterType.BUILDER, builder.getType());
        assertEquals(2, builder.getDiscount());
        assertEquals(5, builder.getPoints());
        assertFalse(builder.isEvent());
        assertEquals(5, builder.getPrestigePoints());
    }

    /**
     * Verifies that assigning a builder to a player adds it to player characters.
     */
    @Test
    void assignToPlayerAddsCardToPlayer() {
        Builder builder = new Builder(Era.I, 2, 1, 3);
        Player player = new Player(Color.ORANGE);

        builder.assignToPlayer(player);

        assertEquals(1, player.getCharacters().size());
        assertEquals(builder, player.getCharacters().get(0));
    }
}
