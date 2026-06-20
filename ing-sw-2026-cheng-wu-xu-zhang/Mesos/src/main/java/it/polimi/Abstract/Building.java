package it.polimi.Abstract;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Constants.Era;
import it.polimi.Constants.Position;

import it.polimi.Buildings.*;
import it.polimi.Buildings.Ritual.*;

/**
 * Base class for all building cards in the game.
 * Buildings provide various effects, ranging from immediate bonuses to end-game scoring multipliers.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "effect")        
@JsonSubTypes({
        @JsonSubTypes.Type(value = InGame_Effect.class, name = "INGAME_EFFECT"),
        @JsonSubTypes.Type(value = Event_Sustenance_Effect.class, name = "EVENT_SUST_EFFECT"),
        @JsonSubTypes.Type(value = Event_Less_Ritual.class, name = "EVENT_LESS_RITUAL"),
        @JsonSubTypes.Type(value = Turn_Start_Effect.class, name = "TURN_START_EFFECT"),
        @JsonSubTypes.Type(value = Event_Most_Ritual.class, name = "EVENT_MOST_RITUAL"),
        @JsonSubTypes.Type(value = AddStar.class, name = "ADDSTAR"),
        @JsonSubTypes.Type(value = Event_Add_Effect.class, name = "EVENT_ADD_EFFECT"),
        @JsonSubTypes.Type(value = EndGame_Builder_Add.class, name = "ENDGAME_BUILDER_MULTIPLIER"),       
        @JsonSubTypes.Type(value = Endgame_Effect.class, name = "ENDGAME_EFFECT"),
        @JsonSubTypes.Type(value = Turn_End_Card.class, name = "TURN_END_CARD")
})
public class Building extends Card {

    /** The resource cost to construct this building. */
    private final int cost;
    /** The base prestige points granted by this building. */
    private final int points;

    /**
     * Creates a building card with the given era, position, cost, and points.
     *
     * @param era The era this building belongs to.
     * @param position The initial board position.
     * @param cost The construction cost.
     * @param points The base points granted.
     */
    @JsonCreator
    public Building(
            @JsonProperty("era") Era era,
            @JsonProperty("position") Position position,
            @JsonProperty("cost") int cost,
            @JsonProperty("points") int points) {

        super(era, position);
        this.cost = cost;
        this.points = points;
    }

    /**
     * Returns the construction cost of the building.
     *
     * @return The cost value.
     */
    public int getCost() {
        return cost;
    }

    /**
     * Returns the points granted by the building.
     *
     * @return The prestige points value.
     */
    public int getPoints() {
        return points;
    }

    /**
     * Applies ritual-specific effects for the building.
     * To be overridden by ritual building subclasses.
     */
    public void applyRitualEffect() {
    }

    /**
     * Applies the standard card effect for the building (e.g., drawing extra cards).
     * To be overridden by relevant subclasses.
     */
    public void applyCardEffect(){

    }

    /**
     * Applies a continuous effect triggered during the game.
     * To be overridden by buildings with passive/persistent abilities.
     */
    public void applyContinuousEffect(){

    }

    /**
     * Applies the effect that depends on event interactions.
     * To be overridden by buildings that react to specific game events.
     */
    public void applyEventsEffect(){

    }

    /**
     * Applies the effect that depends on order or turn sequence.
     * To be overridden by buildings that grant bonuses based on player order.
     */
    public void applyOrderEffect(){

    }

    /**
     * Returns any discount granted by the building for resource gathering or construction.
     *
     * @return The discount value (default is 0).
     */
    public int applyDiscountEffect(){
        return 0;
    }
}
