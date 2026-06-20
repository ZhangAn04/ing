package it.polimi.Game.Elements;

import it.polimi.Abstract.Card;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;

/**
 * Base type for every tribe card in the game.
 * <p>
 * A tribe card represents either a character or an event that can be drafted
 * by a player and incorporated into their collection. It inherits common
 * card properties like Era and Position from the {@link Card} class.
 * </p>
 */
public abstract class Tribe extends Card {
    /**
     * Creates a tribe card with the given era and position.
     *
     * @param era      The {@link Era} this card belongs to.
     * @param position The {@link Position} (Upper or Lower) this card occupies.
     */
    public Tribe(Era era, Position position) {
        super(era, position);
    }

    /**
     * Returns whether this tribe card is an event.
     *
     * @return {@code true} if the card is an event, {@code false} if it is a character.
     */
    public abstract boolean isEvent();

    /**
     * Assigns the tribe card to the given player.
     * <p>
     * Implementation of this method should handle adding the card to the
     * player's collections or triggering specific assignment logic.
     * </p>
     *
     * @param player The {@link Player} receiving the card.
     */
    public abstract void assignToPlayer(Player player);
}
