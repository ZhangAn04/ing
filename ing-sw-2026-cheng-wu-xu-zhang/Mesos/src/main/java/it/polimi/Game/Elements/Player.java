package it.polimi.Game.Elements;

import it.polimi.Abstract.Building;
import it.polimi.Abstract.Character;
import it.polimi.Constants.*;

import java.util.*;

/**
 * Represents a player in the game and stores their resources, cards, and tile assignments.
 * <p>
 * A player is identified by a unique color and maintains a collection of 
 * {@link Character} and {@link Building} cards, along with their current 
 * reserves of food and prestige points.
 * </p>
 */
public class Player {

    /** The unique color identifying this player. */
    private final Color color;
    /** The verified nickname of the player. */
    private String nickname;
    /** The amount of food currently held by the player. */
    private int food;
    /** The number of prestige points accumulated by the player. */
    private int prestigePoints;
    /** The collection of character cards in the player's tribe. */
    private List<Character> characters;
    /** The collection of building cards constructed by the player. */
    private List<Building> buildings;
    /** The offer tile currently selected by the player for the round. */
    private OfferTile offerTile;

    /** Indicates whether the player is currently connected to the server. */
    private boolean online = true;

    /** The secret PIN used by the player to reconnect to the game room. */
    private String pin;

    /**
     * Constructs a new player with an initial empty state.
     *
     * @param color The {@link Color} assigned to this player.
     */
    public Player(Color color) {
        this.color = color;
        this.food = 0;
        this.prestigePoints = 0;

        this.characters = new ArrayList<>();
        this.buildings = new ArrayList<>();
    }

    /**
     * Retrieves the secret PIN for this player.
     *
     * @return The player's PIN.
     */
    public String getPin() {
        return pin;
    }

    /**
     * Sets the secret PIN for this player.
     *
     * @param pin The PIN to set.
     */
    public void setPin(String pin) {
        this.pin = pin;
    }

    /**
     * Checks if the player is currently online.
     *
     * @return {@code true} if online, {@code false} otherwise.
     */
    public boolean isOnline() {
        return online;
    }

    /**
     * Updates the player's online status.
     *
     * @param online {@code true} to set as online, {@code false} to set as offline.
     */
    public void setOnline(boolean online) {
        this.online = online;
    }

    /**
     * Gets the player's nickname.
     *
     * @return The nickname string.
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Sets the player's nickname.
     *
     * @param nickname The nickname to set.
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * Adds a character card to the player's collection.
     *
     * @param character The {@link Character} card to add.
     */
    public void addCharacter(Character character) {
        characters.add(character);
    }

    /**
     * Adds a building card to the player's collection.
     *
     * @param building The {@link Building} card to add.
     */
    public void addBuilding(Building building) {
        buildings.add(building);
    }

    /**
     * Increases the player's food reserve by the specified amount.
     *
     * @param amount The amount of food to receive.
     */
    public void receiveFood(int amount) {
        receiveFood(amount, "unspecified");
    }

    /**
     * Receives food with a textual reason for logging.
     *
     * @param amount food to add
     * @param reason operation label used in logs
     */
    public void receiveFood(int amount, String reason) {
        int before = food;
        food += amount;
        String who = nickname != null ? nickname : String.valueOf(color);
        System.out.println("[Player " + who + "] Food: " + before + " -> " + food + " (+" + amount + ") Reason: " + reason);
    }

    /**
     * Deducts food from the player's reserve.
     * <p>
     * If the player does not have enough food to cover the requirement, 
     * the food reserve remains unchanged, and a penalty is applied to 
     * the player's prestige points instead.
     * </p>
     *
     * @param amount          The amount of food to pay.
     * @param prestigePenalty The points to deduct if the payment fails.
     */
    public void payFood(int amount, int prestigePenalty) {
        payFood(amount, prestigePenalty, "unspecified");
    }

    /**
     * Pays food or prestige with a textual reason for logging.
     *
     * @param amount food required
     * @param prestigePenalty prestige lost when food is insufficient
     * @param reason operation label used in logs
     */
    public void payFood(int amount, int prestigePenalty, String reason) {
        String who = nickname != null ? nickname : String.valueOf(color);
        if (food >= amount) {
            int before = food;
            food -= amount;
            System.out.println("[Player " + who + "] Food: " + before + " -> " + food + " (-" + amount + ") Reason: " + reason);
        } else {
            int before = prestigePoints;
            prestigePoints -= prestigePenalty;
            System.out.println("[Player " + who + "] Prestige: " + before + " -> " + prestigePoints + " (-" + prestigePenalty + ") (insufficient food) Reason: " + reason);
        }
    }

    /**
     * Modifies the player's prestige points by the given delta.
     *
     * @param amount The value to add (can be negative).
     */
    public void modifyPrestigePoints(int amount) {
        modifyPrestigePoints(amount, "unspecified");
    }

    /**
     * Modifies prestige points with a textual reason for logging.
     *
     * @param amount signed prestige change
     * @param reason operation label used in logs
     */
    public void modifyPrestigePoints(int amount, String reason) {
        int before = prestigePoints;
        prestigePoints += amount;
        String who = nickname != null ? nickname : String.valueOf(color);
        System.out.println("[Player " + who + "] Prestige: " + before + " -> " + prestigePoints + " (" + (amount >= 0 ? "+" : "") + amount + ") Reason: " + reason);
    }

    /**
     * Returns the player's color.
     *
     * @return The {@link Color} of the player.
     */
    public Color getColor() {
        return color;
    }

    /**
     * Returns the player's current food amount.
     *
     * @return The food reserve.
     */
    public int getFood() {
        return food;
    }

    /**
     * Returns the player's current prestige points.
     *
     * @return The accumulated prestige points.
     */
    public int getPrestigePoints() {
        return prestigePoints;
    }

    /**
     * Returns the player's collected character cards.
     *
     * @return A list of {@link Character} cards.
     */
    public List<Character> getCharacters() {
        return characters;
    }

    /**
     * Returns the player's collected building cards.
     *
     * @return A list of {@link Building} cards.
     */
    public List<Building> getBuildings() {
        return buildings;
    }

    /**
     * Returns the offer tile currently assigned to the player.
     *
     * @return The {@link OfferTile} held by the player.
     */
    public OfferTile getOfferTile() {
        return offerTile;
    }

    /**
     * Assigns an offer tile to the player.
     *
     * @param offerTile The {@link OfferTile} to set.
     */
    public void setOfferTile(OfferTile offerTile) {
        this.offerTile = offerTile;
    }
}
