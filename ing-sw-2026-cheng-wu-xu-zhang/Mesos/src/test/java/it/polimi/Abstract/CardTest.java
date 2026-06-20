package it.polimi.Abstract;

import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the shared state and mutators provided by the base {@link Card} class.
 */
class CardTest {

    /**
     * Verifies that the default constructor initializes mutable fields to their defaults.
     */
    @Test
    void defaultConstructorKeepsUnsetValuesAndDefaultAssetId() {
        Card card = new TestCard();

        assertEquals(null, card.getEra());
        assertEquals(null, card.getPosition());
        assertEquals(-1, card.getAssetId());
    }

    /**
     * Verifies that constructor values and later mutations are exposed by getters.
     */
    @Test
    void constructorAndSettersUpdateVisibleState() {
        Card card = new TestCard(Era.III, Position.BOTTOM);

        card.setAssetId(42);
        card.setPosition(Position.TOP);

        assertEquals(Era.III, card.getEra());
        assertEquals(Position.TOP, card.getPosition());
        assertEquals(42, card.getAssetId());
    }

    /** Concrete card used to exercise the abstract base class. */
    private static final class TestCard extends Card {
        private TestCard() {
            super();
        }

        private TestCard(Era era, Position position) {
            super(era, position);
        }
    }
}
