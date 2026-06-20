package it.polimi.Abstract;

import it.polimi.Game.Elements.Player;
import it.polimi.Buildings.Ritual.RitualData;
import java.util.List;

/**
 * Specialized event context that includes ritual-specific data.
 * Used when resolving events that depend on ritual star counts (e.g., Shamanic Ritual).
 */
public class RitualEventContext extends EventContext {

    /** The ritual star counts for each player. */
    private final RitualData ritualData;

    /**
     * Creates a ritual-aware context for event resolution.
     *
     * @param players The list of players involved.
     * @param ritualData The current ritual state.
     */
    public RitualEventContext(List<Player> players, RitualData ritualData) {
        super(players);
        this.ritualData = ritualData;
    }

    /**
     * Returns the ritual data linked to this context.
     *
     * @return The ritual data instance.
     */
    public RitualData getRitualData() {
        return ritualData;
    }
}
