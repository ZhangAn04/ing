package it.polimi.Abstract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.polimi.Constants.*;

/**
 * Base class for every card in the game, storing era and board position.
 * This common foundation is used for both Tribe cards (Characters/Events) and Buildings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Card {

    /** The era (I, II, III) during which this card is active. */
    @JsonProperty("era")
    private Era era;

    /** The current location of the card on the game board (e.g., TOP, BOTTOM, DECK). */
    private Position position;

    /**
     * Optional numeric asset identifier used by GUI clients to map cards to image files.
     * Not part of the JSON assets.
     */
    @JsonIgnore
    private int assetId = -1;

    /**
     * Default constructor required by JSON deserialization.
     */
    public Card() {}

    /**
     * Creates a card with the given era and position.
     *
     * @param era The era associated with the card.
     * @param position The initial board position.
     */
    public Card(Era era, Position position) {
        this.era = era;
        this.position = position;
    }

    /**
     * Returns the current board position of the card.
     *
     * @return The current position.
     */
    public Position getPosition() {
        return position;
    }

    /**
     * Returns the era associated with this card.
     *
     * @return The era (I, II, or III).
     */
    public Era getEra() {
        return era;
    }

    /**
     * Returns the GUI asset identifier, or {@code -1} if not assigned.
     *
     * @return the GUI asset identifier
     */
    public int getAssetId() {
        return assetId;
    }

    /**
     * Assigns the GUI asset identifier.
     *
     * @param assetId GUI asset identifier
     */
    public void setAssetId(int assetId) {
        this.assetId = assetId;
    }

    /**
     * Updates the board position of the card.
     *
     * @param position The new board position to assign.
     */
    public void setPosition(Position position) {
        this.position = position;
    }
}
