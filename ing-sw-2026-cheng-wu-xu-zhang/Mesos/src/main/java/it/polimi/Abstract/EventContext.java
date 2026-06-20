package it.polimi.Abstract;

import it.polimi.Game.Elements.Player;
import java.util.List;

/**
 * Data container used to pass the current game context (like involved players) to event effects.
 * This allows event resolution to remain decoupled from the main Game class.
 */
public class EventContext {

    /** The list of players affected by or participating in the event. */
    private final List<Player> players;

    /**
     * Creates a context containing the players involved in the event.
     *
     * @param players The list of players.
     */
    public EventContext(List<Player> players) {
        this.players = players;
    }

    /**
     * Returns the players associated with this context.
     *
     * @return The list of players.
     */
    public List<Player> getPlayers() {
        return players;
    }
}
