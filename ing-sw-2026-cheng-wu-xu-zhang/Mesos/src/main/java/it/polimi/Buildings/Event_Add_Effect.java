package it.polimi.Buildings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Game.Elements.Player;
import it.polimi.Abstract.Building;
import it.polimi.Abstract.Event;
import it.polimi.Abstract.Character;
import it.polimi.Constants.Era;
import it.polimi.Constants.EventType;
import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Position;

/**
 * Building effect that rewards the player when a specific event occurs.
 * The reward (food and/or bonus points) is scaled by the number of target characters owned by the player.
 */
public class Event_Add_Effect extends Building {

    /** The event type that triggers this building's effect. */
    private EventType event;
    /** The amount of food rewarded per target character. */
    private int meat;
    /** The amount of bonus prestige points rewarded per target character. */
    private int bonusPoints;
    /** The character type used as a multiplier for the reward. */
    private CharacterType target;

    /**
     * Creates an event-triggered effect building.
     *
     * @param era The era this building belongs to.
     * @param position The initial board position.
     * @param cost The construction cost.
     * @param points The base points granted.
     * @param event The triggering event type.
     * @param target The target character type for scaling rewards.
     * @param meat The food reward per character.
     * @param bonusPoints The prestige reward per character.
     */
    @JsonCreator
    public Event_Add_Effect(
            @JsonProperty("era") Era era,
            @JsonProperty("position") Position position,
            @JsonProperty("cost") int cost,
            @JsonProperty("points") int points,
            @JsonProperty("event") EventType event,
            @JsonProperty("target") CharacterType target,
            @JsonProperty("food") int meat,
            @JsonProperty("bonusPoints") int bonusPoints) {
        super(era, position, cost, points);
        this.event = event;
        this.meat = meat;
        this.bonusPoints = bonusPoints;
        this.target = target;
    }

    /**
     * Applies the event-based reward to the given player if the event matches.
     *
     * @param player The player receiving the reward.
     * @param eventCard The event card that was triggered.
     */
    public void applyEventsEffect(Player player, Event eventCard) {
        // Apply rewards only when the triggering event matches this building effect.

        int count = 0;

        if (eventCard.getType() != event) {
            return;
        }

        for (Character character : player.getCharacters()) {
            if (character.getType() == target) {
                count++;
            }
        }
        player.receiveFood(count * meat);
        player.modifyPrestigePoints(count * bonusPoints);

    }

}
