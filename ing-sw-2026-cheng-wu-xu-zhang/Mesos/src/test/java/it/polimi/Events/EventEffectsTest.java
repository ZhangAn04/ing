package it.polimi.Events;

import it.polimi.Abstract.EventContext;
import it.polimi.Abstract.RitualEventContext;
import it.polimi.Buildings.Ritual.RitualData;
import it.polimi.Characters.Artist;
import it.polimi.Characters.Gatherer;
import it.polimi.Characters.Hunter;
import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.EventType;
import it.polimi.Constants.Position;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for event card effects.
 */
class EventEffectsTest {

    /**
     * Verifies event getters and base event behavior.
     */
    @Test
    void eventGettersAndBaseBehaviorAreConsistent() {
        Hunting hunting = new Hunting(Era.I, Position.TOP, 2, 1);
        Sustenance sustenance = new Sustenance(Era.II, Position.BOTTOM, 3, 4);
        RockPaintings paintings = new RockPaintings(Era.III, Position.TOP, 2, 0, 5, -3);
        ShamanicRitual ritual = new ShamanicRitual(Era.I, Position.BOTTOM, 6, -2);
        Player player = new Player(Color.ORANGE);

        assertEquals(EventType.HUNTING, hunting.getType());
        assertEquals(2, hunting.getFood());
        assertEquals(1, hunting.getPoints());
        assertEquals(3, sustenance.getFood());
        assertEquals(4, sustenance.getPoints());
        assertEquals(2, paintings.getThresholdHigh());
        assertEquals(0, paintings.getThresholdLow());
        assertEquals(5, paintings.getPpIfMet());
        assertEquals(-3, paintings.getPpIfNotMet());
        assertEquals(6, ritual.getMaxPoints());
        assertEquals(-2, ritual.getMinPoints());
        assertEquals(true, hunting.isEvent());

        hunting.assignToPlayer(player);
        assertEquals(0, player.getCharacters().size());
    }

    /**
     * Verifies that hunting rewards only players with hunter characters.
     */
    @Test
    void huntingAppliesFoodAndPrestigePerHunter() {
        Player playerWithHunters = new Player(Color.ORANGE);
        Player playerWithoutHunters = new Player(Color.BLUE);
        Hunting event = new Hunting(Era.I, Position.TOP, 2, 1);

        playerWithHunters.addCharacter(new Hunter(Era.I, 2, false));
        playerWithHunters.addCharacter(new Hunter(Era.I, 2, true));
        playerWithoutHunters.addCharacter(new Gatherer(Era.I, 2, 1));

        event.applyEffect(new EventContext(Arrays.asList(playerWithHunters, playerWithoutHunters)));

        assertEquals(4, playerWithHunters.getFood());
        assertEquals(2, playerWithHunters.getPrestigePoints());
        assertEquals(0, playerWithoutHunters.getFood());
        assertEquals(0, playerWithoutHunters.getPrestigePoints());
    }

    /**
     * Verifies that sustenance consumes food when possible and penalizes otherwise.
     */
    @Test
    void sustenanceConsumesFoodOrAppliesPenalty() {
        Player playerWithEnoughFood = new Player(Color.PURPLE);
        Player playerWithoutEnoughFood = new Player(Color.YELLOW);
        Sustenance event = new Sustenance(Era.II, Position.TOP, 2, 3);

        playerWithEnoughFood.addCharacter(new Gatherer(Era.I, 2, 1));
        playerWithEnoughFood.addCharacter(new Gatherer(Era.I, 2, 2));
        playerWithEnoughFood.receiveFood(4);

        playerWithoutEnoughFood.addCharacter(new Gatherer(Era.I, 2, 1));
        playerWithoutEnoughFood.receiveFood(1);

        event.applyEffect(new EventContext(Arrays.asList(playerWithEnoughFood, playerWithoutEnoughFood)));

        assertEquals(3, playerWithEnoughFood.getFood());
        assertEquals(0, playerWithEnoughFood.getPrestigePoints());
        assertEquals(0, playerWithoutEnoughFood.getFood());
        assertEquals(0, playerWithoutEnoughFood.getPrestigePoints());
    }

    /**
     * Verifies that rock paintings rewards only players who meet the artist threshold.
     */
    @Test
    void rockPaintingsRewardsOnlyPlayersMeetingThreshold() {
        Player qualifiedPlayer = new Player(Color.ORANGE);
        Player unqualifiedPlayer = new Player(Color.BLUE);
        RockPaintings event = new RockPaintings(Era.II, Position.TOP, 2, 0, 5, -3);

        qualifiedPlayer.addCharacter(new Artist(Era.I, 2));
        qualifiedPlayer.addCharacter(new Artist(Era.I, 2));
        unqualifiedPlayer.addCharacter(new Artist(Era.I, 2));

        event.applyEffect(new EventContext(Arrays.asList(qualifiedPlayer, unqualifiedPlayer)));

        assertEquals(10, qualifiedPlayer.getPrestigePoints());
        assertEquals(0, unqualifiedPlayer.getPrestigePoints());
    }

    /**
     * Verifies that ritual points are applied to max and min star owners only.
     */
    @Test
    void shamanicRitualRewardsMaxAndMinStars() {
        Player maxStarPlayer = new Player(Color.ORANGE);
        Player minStarPlayer = new Player(Color.BLUE);
        Player middleStarPlayer = new Player(Color.PURPLE);
        RitualData ritualData = new RitualData();
        ShamanicRitual event = new ShamanicRitual(Era.III, Position.TOP, 7, -2);

        ritualData.addStar(maxStarPlayer, 3);
        ritualData.addStar(minStarPlayer, 1);
        ritualData.addStar(middleStarPlayer, 2);

        event.applyEffect(new RitualEventContext(
                Arrays.asList(maxStarPlayer, minStarPlayer, middleStarPlayer),
                ritualData
        ));

        assertEquals(7, maxStarPlayer.getPrestigePoints());
        assertEquals(-2, minStarPlayer.getPrestigePoints());
        assertEquals(0, middleStarPlayer.getPrestigePoints());
    }

    /**
     * Verifies that Gatherer discount reduces food cost during Sustenance events.
     */
    @Test
    void sustenanceAppliesGathererDiscounts() {
        Player player = new Player(Color.PURPLE);
        Sustenance event = new Sustenance(Era.I, Position.TOP, 2, 3);

        // 2 characters (both Gatherers, discount = 2 each)
        player.addCharacter(new Gatherer(Era.I, 2, 2));
        player.addCharacter(new Gatherer(Era.I, 2, 2));
        player.receiveFood(1); // has 1 food

        // Total required = 2 * 2 = 4 food.
        // Total Gatherer discount = 2 + 2 = 4.
        // Discounted requirement = max(0, 4 - 4) = 0 food.
        event.applyEffect(new EventContext(Arrays.asList(player)));

        // Food should remain 1 and prestige points should be 0 (no penalty)
        assertEquals(1, player.getFood());
        assertEquals(0, player.getPrestigePoints());
    }

    /**
     * Verifies that Event_Sustenance_Effect buildings grant a discount during Sustenance events.
     */
    @Test
    void sustenanceAppliesBuildingDiscounts() {
        Player player = new Player(Color.YELLOW);
        Sustenance event = new Sustenance(Era.I, Position.TOP, 2, 3);

        // 1 character (Artist, no Gatherer discount)
        player.addCharacter(new Artist(Era.I, 2));
        
        // 1 Sustenance discount building (grants 1 food discount per Artist)
        player.addBuilding(new it.polimi.Buildings.Event_Sustenance_Effect(
            Era.I, 
            Position.TOP, 
            1, 
            1, 
            1, 
            it.polimi.Constants.CharacterType.ARTIST
        ));
        
        player.receiveFood(2); // has 2 food

        // Total required = 1 * 2 = 2 food.
        // Building discount = 1 * 1 = 1 food.
        // Discounted requirement = max(0, 2 - 1) = 1 food.
        event.applyEffect(new EventContext(Arrays.asList(player)));

        // Food should be 2 - 1 = 1 food, prestige points should be 0 (no penalty)
        assertEquals(1, player.getFood());
        assertEquals(0, player.getPrestigePoints());
    }

    /**
     * Verifies that insufficient food during Sustenance events applies correct prestige penalties.
     */
    @Test
    void sustenanceAppliesPrestigePenaltyForInsufficientFood() {
        Player player = new Player(Color.BLUE);
        Sustenance event = new Sustenance(Era.I, Position.TOP, 2, 3);

        // 1 character (Artist, no Gatherer discount, total required = 1 * 2 = 2 food)
        player.addCharacter(new Artist(Era.I, 2));
        player.receiveFood(0); // has 0 food

        // Total required = 2. Food = 0. Food shortage = 2.
        // Unfed characters = ceil(2 / 2) = 1.
        // Penalty = 1 * 3 = 3 prestige points.
        event.applyEffect(new EventContext(Arrays.asList(player)));

        // Food should remain 0, prestige points should drop by 3
        assertEquals(0, player.getFood());
        assertEquals(-3, player.getPrestigePoints());
    }
}
