package it.polimi.Abstract;

import it.polimi.Game.Elements.Player;
import it.polimi.Game.Elements.Tribe;
import it.polimi.Constants.*;

/**
 * Base class for all event cards in the game.
 * Events are a subset of Tribe cards that trigger special effects during the game rounds.
 */
public abstract class Event extends Tribe {

    /** The specific type of the event (e.g., SUSTENANCE, HUNTING). */
    private final EventType type;

    /**
     * Creates an event card with the given era, position, and event type.
     *
     * @param era The era this event belongs to.
     * @param position The initial board position.
     * @param type The event's type.
     */
    public Event(Era era, Position position, EventType type) {
        super(era, position);
        this.type = type;
    }

    /**
     * Returns the type of the event.
     *
     * @return The EventType enum value.
     */
    public EventType getType() {
        return type;
    }

    /**
     * Events are always marked as event cards.
     *
     * @return Always true.
     */
    @Override
    public boolean isEvent() {
        return true;
    }

    /**
     * Events are not assigned to players directly, so this method does nothing.
     *
     * @param player The player (ignored).
     */
    @Override
    public void assignToPlayer(Player player) {
    }

    /**
     * Applies the effect of the event to the provided context.
     * Subclasses must implement specific effect logic.
     *
     * @param context The context containing players and other data needed for resolution.
     */
    public abstract void applyEffect(EventContext context);
}
