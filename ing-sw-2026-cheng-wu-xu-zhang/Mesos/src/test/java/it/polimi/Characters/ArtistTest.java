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
 * Unit tests for the {@link Artist} character.
 */
class ArtistTest {

    /**
     * Verifies that artist constructor initializes inherited fields correctly.
     */
    @Test
    void constructorSetsAllFields() {
        Artist artist = new Artist(Era.I, 2);

        assertEquals(Era.I, artist.getEra());
        assertEquals(2, artist.getPlayers());
        assertEquals(Position.DECK, artist.getPosition());
        assertEquals(CharacterType.ARTIST, artist.getType());
        assertFalse(artist.isEvent());
        assertEquals(0, artist.getPrestigePoints());
    }

    /**
     * Verifies that assigning an artist to a player stores it in player characters.
     */
    @Test
    void assignToPlayerAddsCardToPlayer() {
        Artist artist = new Artist(Era.II, 3);
        Player player = new Player(Color.YELLOW);

        artist.assignToPlayer(player);

        assertEquals(1, player.getCharacters().size());
        assertEquals(artist, player.getCharacters().get(0));
    }
}
