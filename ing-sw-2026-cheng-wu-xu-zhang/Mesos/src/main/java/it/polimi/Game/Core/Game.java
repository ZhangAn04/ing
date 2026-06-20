package it.polimi.Game.Core;

import it.polimi.Abstract.*;
import it.polimi.Abstract.Character;
import it.polimi.Buildings.Event_Add_Effect;
import it.polimi.Buildings.Event_Sustenance_Effect;
import it.polimi.Buildings.Ritual.AddStar;
import it.polimi.Buildings.Ritual.Event_Less_Ritual;
import it.polimi.Buildings.Ritual.RitualData;
import it.polimi.Characters.Builder;
import it.polimi.Characters.Gatherer;
import it.polimi.Characters.Shaman;
import it.polimi.Events.Hunting;
import it.polimi.Events.RockPaintings;
import it.polimi.Events.ShamanicRitual;
import it.polimi.Events.Sustenance;
import it.polimi.Game.Elements.OfferTile;
import it.polimi.Game.Elements.Player;
import it.polimi.Game.Elements.Tribe;

import it.polimi.Constants.*;

import java.util.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Main model class representing the state of a Mesos game session.
 * Manages players, card decks, board rows, and the progression of rounds and eras.
 */
public class Game {

    /** Total number of rounds in a standard Mesos game. */
    public static final int MAX_ROUNDS = 10;

    /**
     * Total number of event card images reserved in the provided assets.
     * (Includes 2 final-event images even if the model doesn't currently use them.)
     */
    private static final int EVENT_IMAGE_COUNT = 12;

    /** Current round number of the game. */
    private int round;
    /** Current era of the game (I, II, or III). */
    private Era currentEra;
    /** Number of cards to be placed in the upper row. */
    private int numUpperCards;
    /** Number of cards to be placed in the lower row. */
    private int numLowerCards;
    /** List of cards currently in the upper row of the board. */
    private List<Tribe> upperRow;
    /** List of cards currently in the lower row of the board. */
    private List<Tribe> lowerRow;
    /** List of players participating in the game. */
    private List<Player> players;
    /** List of available offer tiles for the current round. */
    private List<OfferTile> offerTiles;

    /** Buildings available in the upper row of the building board. */
    private List<Building> upperBuildings;
    /** Buildings available in the lower row of the building board. */
    private List<Building> lowerBuildings;

    /** Deck of buildings for Era I. */
    private Stack<Building> era1BuildingDeck;
    /** Deck of buildings for Era II. */
    private Stack<Building> era2BuildingDeck;
    /** Deck of buildings for Era III. */
    private Stack<Building> era3BuildingDeck;

    /** Main deck containing Character and Event cards. */
    private Stack<Tribe> tribeDeck;
    /** Support for broadcasting property change events to listeners. */
    private final PropertyChangeSupport support;

    /**
     * Constructs a new Game instance with default initial values.
     */
    public Game() {

        this.round = 1;
        this.currentEra = Era.I;
        this.upperRow = new ArrayList<>();
        this.lowerRow = new ArrayList<>();
        this.players = new ArrayList<>();
        this.offerTiles = new ArrayList<>();

        this.tribeDeck = new Stack<>();
        this.era1BuildingDeck = new Stack<>();
        this.era2BuildingDeck = new Stack<>();
        this.era3BuildingDeck = new Stack<>();

        this.upperBuildings = new ArrayList<>();
        this.lowerBuildings = new ArrayList<>();
        this.support = new PropertyChangeSupport(this);
    }

    /**
     * Registers a listener that will be notified when the game state changes.
     *
     * @param listener The listener to add.
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    /**
     * Broadcasts a non-state-critical message to all subscribed views.
     * This is used for game notifications (e.g., end-of-game final scores).
     *
     * @param message message to broadcast (ignored if null/blank)
     */
    public void notifyNotification(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        support.firePropertyChange("notification", null, message);
    }

    /**
     * Removes a previously registered game state listener.
     *
     * @param listener The listener to remove.
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }

    /**
     * Notifies all listeners that the game state has changed.
     *
     * @param eventName The name of the property change event.
     */
    private void notifyStateChange(String eventName) {
        support.firePropertyChange(eventName, null, toString());
    }

    /**
     * Initializes the game state for the given number of players.
     * Loads cards, creates the deck, and prepares the initial round data.
     *
     * @param numPlayers The number of players (2-5).
     * @throws Exception If card loading fails.
     * @throws IllegalArgumentException If numPlayers is outside the allowed range.
     */
    public void initialize(int numPlayers) throws Exception {
        if (numPlayers < 2 || numPlayers > 5) {
            throw new IllegalArgumentException("Invalid number of players.");
        }
        initialize(numPlayers, defaultPlayerColors(numPlayers));
    }

    /**
     * Initializes the game state for the given players using the provided player colors.
     *
     * @param numPlayers The number of players (2-5).
     * @param playerColors The colors selected by players, in player order.
     * @throws Exception If card loading fails.
     * @throws IllegalArgumentException If numPlayers or playerColors are invalid.
     */
    public void initialize(int numPlayers, List<Color> playerColors) throws Exception {
        if (numPlayers < 2 || numPlayers > 5) {
            throw new IllegalArgumentException("Invalid number of players.");
        }
        if (playerColors == null || playerColors.size() != numPlayers) {
            throw new IllegalArgumentException("A color must be selected for each player.");
        }

        this.round = 1;
        this.currentEra = Era.I;

        this.upperRow.clear();
        this.lowerRow.clear();
        this.players.clear();
        this.offerTiles.clear();
        this.upperBuildings.clear();
        this.lowerBuildings.clear();

        initializePlayers(playerColors);

        numUpperCards = numPlayers + 4;
        numLowerCards = numPlayers + 1;

        CardLoader loader = new CardLoader();
        List<Character> allCharacters = loader.loadCharacters();
        List<Event> allEvents = loader.loadEvents();
        List<Building> allBuildings = loader.loadBuildings();

        assignAssetIds(allCharacters, allEvents, allBuildings);

        this.tribeDeck = buildTribeDeck(numPlayers, allCharacters, allEvents);

        initializeBuildings(numPlayers, allBuildings);

        System.out.println("Game initialized with " + numPlayers + " players.");
        notifyStateChange("gameInitialized");
    }

    /**
     * Assigns unique asset IDs to all cards (characters, events, buildings) for UI display.
     * Asset IDs are used to match cards with their corresponding image files.
     * IDs are assigned sequentially: characters start at 1, events follow after characters,
     * and buildings follow after events.
     *
     * @param characters the list of character cards to assign IDs to
     * @param events the list of event cards to assign IDs to
     * @param buildings the list of building cards to assign IDs to
     */
    private void assignAssetIds(List<Character> characters, List<Event> events, List<Building> buildings) {
        if (characters != null) {
            int id = 1;
            for (Character character : characters) {
                if (character != null && character.getAssetId() <= 0) {
                    character.setAssetId(id++);
                }
            }
        }

        int eventStart = 1 + (characters == null ? 0 : characters.size());
        if (events != null) {
            int id = eventStart;
            for (Event event : events) {
                if (event != null && event.getAssetId() <= 0) {
                    event.setAssetId(id++);
                }
            }
        }

        int buildingStart = eventStart + EVENT_IMAGE_COUNT;
        if (buildings != null) {
            int id = buildingStart;
            for (Building building : buildings) {
                if (building != null && building.getAssetId() <= 0) {
                    building.setAssetId(id++);
                }
            }
        }
    }

    /**
     * Initializes the building decks and the visible building rows according to the rules.
     */
    private void initializeBuildings(int numPlayers, List<Building> buildings) {
        era1BuildingDeck.clear();
        era2BuildingDeck.clear();
        era3BuildingDeck.clear();
        upperBuildings.clear();
        lowerBuildings.clear();

        if (buildings == null || buildings.isEmpty()) {
            return;
        }

        List<Building> era1 = new ArrayList<>();
        List<Building> era2 = new ArrayList<>();
        List<Building> era3 = new ArrayList<>();

        for (Building building : buildings) {
            if (building == null || building.getEra() == null) {
                continue;
            }
            switch (building.getEra()) {
                case I:
                    era1.add(building);
                    break;
                case II:
                    era2.add(building);
                    break;
                case III:
                    era3.add(building);
                    break;
                default:
                    break;
            }
        }

        Collections.shuffle(era1);
        Collections.shuffle(era2);
        Collections.shuffle(era3);

        int era1Count;
        int era2Count;
        int era3Count;

        switch (numPlayers) {
            case 2:
                era1Count = 1;
                era2Count = 2;
                era3Count = 3;
                break;
            case 3:
                era1Count = 2;
                era2Count = 2;
                era3Count = 4;
                break;
            case 4:
                era1Count = 2;
                era2Count = 3;
                era3Count = 4;
                break;
            case 5:
                era1Count = 2;
                era2Count = 3;
                era3Count = 5;
                break;
            default:
                throw new IllegalArgumentException("Invalid number of players.");
        }

        for (int i = 0; i < Math.min(era1Count, era1.size()); i++) {
            upperBuildings.add(era1.get(i));
        }

        for (int i = 0; i < Math.min(era2Count, era2.size()); i++) {
            era2BuildingDeck.push(era2.get(i));
        }

        for (int i = 0; i < Math.min(era3Count, era3.size()); i++) {
            era3BuildingDeck.push(era3.get(i));
        }
    }

    /**
     * Creates one player for each supported color and adds them to the game.
     *
     * @param numPlayers The number of players to create.
     */
    private void initializePlayers(List<Color> colors) {
        Set<Color> usedColors = new HashSet<>();
        for (Color color : colors) {
            if (color == null || !usedColors.add(color)) {
                throw new IllegalArgumentException("Player colors must be unique.");
            }
            players.add(new Player(color));
        }
    }

    /**
     * Selects the first distinct colors for a game that has no explicit color choices.
     *
     * @param numPlayers number of colors to select
     * @return the default player colors
     */
    private List<Color> defaultPlayerColors(int numPlayers) {
        Color[] colors = Color.values();
        List<Color> selected = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) {
            selected.add(colors[i]);
        }
        return selected;
    }

    /**
     * Builds the tribe deck by collecting all valid cards and ordering them by era.
     *
     * @param numPlayers Number of players to filter cards.
     * @param characters List of all character cards.
     * @param events List of all event cards.
     * @return A stack representing the shuffled tribe deck.
     */
    private Stack<Tribe> buildTribeDeck(int numPlayers, List<Character> characters, List<Event> events) {
        Stack<Tribe> deck = new Stack<>();

        List<Tribe> era3Cards = filterAndShuffle(characters, events, Era.III, numPlayers);
        List<Tribe> era2Cards = filterAndShuffle(characters, events, Era.II, numPlayers);
        List<Tribe> era1Cards = filterAndShuffle(characters, events, Era.I, numPlayers);

        for (Tribe tribe : era3Cards) {
            deck.push(tribe);
        }

        for (Tribe tribe : era2Cards) {
            deck.push(tribe);
        }

        for (Tribe tribe : era1Cards) {
            deck.push(tribe);
        }

        return deck;
    }

    /**
     * Filters cards by era and player count, then shuffles the resulting list.
     *
     * @param characters List of characters to filter.
     * @param events List of events to filter.
     * @param era The era to filter by.
     * @param numPlayers The current number of players.
     * @return A shuffled list of filtered Tribe cards.
     */
    private List<Tribe> filterAndShuffle(List<Character> characters, List<Event> events, Era era, int numPlayers) {
        List<Tribe> result = new ArrayList<>();

        for (Character character : characters) {
            if (character.getEra() == era && character.getPlayers() <= numPlayers) {
                result.add(character);
            }
        }

        for (Event event : events) {
            if (event.getEra() == era) {
                result.add(event);
            }
        }

        Collections.shuffle(result);
        return result;
    }

    /**
     * Executes a full round: prepares tiles, resolves the first round if needed,
     * updates rows, and advances the round counter.
     *
     * @throws Exception If preparing offer tiles fails.
     */
    public void executeRound() throws Exception {

        prepareOfferTiles();

        if (round == 1 && upperRow.isEmpty() && lowerRow.isEmpty()) {
            prepareFirstRound();
        }

        finishRoundAfterPlayerActions();
    }

    /**
     * Completes a round after every player has resolved their offer tile action.
     * End-of-round events are resolved before the lower row is discarded and
     * before the upper row slides down.
     */
    public void finishRoundAfterPlayerActions() {
        resolveEndOfRoundEvents();
        manageCards();

        if (round < MAX_ROUNDS) {
            round++;
        }
        notifyStateChange("roundExecuted");
    }

    /**
     * Builds the initial table layout for the first round.
     */
    public void prepareFirstRound() {
        upperRow.clear();
        lowerRow.clear();

        // Draw cards for the lower row. Events drawn are placed on the upper row
        // (they may temporarily increase its size); continue drawing until the
        // lower row has the required number of non-event cards.
        while (lowerRow.size() < numLowerCards && !tribeDeck.isEmpty()) {
            Tribe card = tribeDeck.pop();

            if (card.isEvent()) {
                // Events always go to the upper row when revealed
                upperRow.add(card);
            } else {
                lowerRow.add(card);
            }
        }

        // Now ensure the upper row has the configured number of cards.
        // If events were already placed there above, only draw the remaining needed.
        while (upperRow.size() < numUpperCards && !tribeDeck.isEmpty()) {
            upperRow.add(tribeDeck.pop());
        }

        notifyStateChange("firstRoundPrepared");
    }

    /**
     * Moves cards between rows and draws new cards to keep the table balanced.
     */
    public void manageCards() {
        // End-of-round rules:
        // 1) discard all tribe cards (characters/events) in the lower row
        // 2) move remaining tribe cards from upper row to lower row
        // 3) refill upper row with (numPlayers + 4) tribe cards

        lowerRow.clear();
        lowerRow.addAll(upperRow);
        upperRow.clear();

        while (upperRow.size() < numUpperCards && !tribeDeck.isEmpty()) {
            Tribe card = tribeDeck.pop();

            if (card.getEra() != currentEra) {
                handleEraChange(card.getEra());
            }

            upperRow.add(card);
        }

        notifyStateChange("cardsManaged");
    }

    /**
     * Assigns the chosen offer tile to the player while keeping it visible in the offer track.
     *
     * @param player The player choosing the tile.
     * @param tile The selected offer tile.
     * @throws IllegalArgumentException If the tile is not available.
     */
    public void chooseOfferTile(Player player, OfferTile tile) {
        if (offerTiles.contains(tile)) {
            player.setOfferTile(tile);
            notifyStateChange("offerTileChosen");
        } else {
            throw new IllegalArgumentException("Offer tile not available.");
        }
    }

    /**
     * Loads the available offer tiles and keeps only the ones valid for the current player count.
     *
     * @throws Exception If card loading fails.
     */
    public void prepareOfferTiles() throws Exception {
        offerTiles.clear();

        CardLoader loader = new CardLoader();
        List<OfferTile> allTiles = loader.loadOfferTiles();

        int numPlayers = players.size();

        for (OfferTile tile : allTiles) {
            if (tile.availableFor(numPlayers)) {
                offerTiles.add(tile);
            }
        }
    }

    /**
     * Returns the building deck associated with the specified era.
     *
     * @param era The era to retrieve the deck for.
     * @return The building deck for the specified era.
     * @throws IllegalArgumentException If the era is invalid.
     */
    private Stack<Building> getBuildingDeckForEra(Era era) {
        switch (era) {
            case I:
                return era1BuildingDeck;
            case II:
                return era2BuildingDeck;
            case III:
                return era3BuildingDeck;
            default:
                throw new IllegalArgumentException("Invalid era: " + era);
        }
    }

    /**
     * Validates a player's selected cards and applies the turn to the game state.
     *
     * @param player The player executing the turn.
     * @param upperChoices Cards selected from the upper row.
     * @param lowerChoices Cards selected from the lower row.
     * @throws IllegalArgumentException If the selections are invalid.
     */
    public void executeTurn(Player player, List<Tribe> upperChoices, List<Tribe> lowerChoices) {
        OfferTile tile = player.getOfferTile();

        if (upperChoices.size() != tile.getNumUpperCards()) {
            throw new IllegalArgumentException("You selected an incorrect number of cards from the upper row.");
        }

        if (lowerChoices.size() != tile.getNumLowerCards()) {
            throw new IllegalArgumentException("You selected an incorrect number of cards from the lower row.");
        }

        for (Tribe choice : upperChoices) {
            if (choice.isEvent()) {
                throw new IllegalArgumentException("You cannot select events.");
            }

            if (!upperRow.contains(choice)) {
                throw new IllegalArgumentException("Card not available in the upper row.");
            }
        }

        for (Tribe choice : lowerChoices) {
            if (choice.isEvent()) {
                throw new IllegalArgumentException("You cannot select events.");
            }

            if (!lowerRow.contains(choice)) {
                throw new IllegalArgumentException("Card not available in the lower row.");
            }
        }

        for (Tribe choice : upperChoices) {
            upperRow.remove(choice);
            choice.assignToPlayer(player);
        }

        for (Tribe choice : lowerChoices) {
            lowerRow.remove(choice);
            choice.assignToPlayer(player);
        }

        notifyStateChange("turnExecuted");
    }

    /**
     * Resolves end-of-round event effects.
     */
    public void resolveEndOfRoundEvents() {
        boolean includeUpperRow = (round == MAX_ROUNDS);

        // Preserve the order of event *types* as they appear, but resolve duplicates of the
        // same type in ascending era order (rules).
        Map<EventType, List<Event>> nonSustenanceByType = new LinkedHashMap<>();
        List<Event> sustenance = new ArrayList<>();

        List<Tribe> visible = new ArrayList<>();
        visible.addAll(lowerRow);
        if (includeUpperRow) {
            visible.addAll(upperRow);
        }

        for (Tribe card : visible) {
            if (!(card instanceof Event)) {
                continue;
            }
            Event event = (Event) card;
            if (event.getType() == EventType.SUSTENANCE) {
                sustenance.add(event);
            } else {
                nonSustenanceByType.computeIfAbsent(event.getType(), ignored -> new ArrayList<>()).add(event);
            }
        }

        if (nonSustenanceByType.isEmpty() && sustenance.isEmpty()) {
            return;
        }

        List<Event> nonSustenance = new ArrayList<>();
        for (List<Event> eventsOfType : nonSustenanceByType.values()) {
            eventsOfType.sort(Comparator.comparing(Event::getEra));
            nonSustenance.addAll(eventsOfType);
        }

        sustenance.sort(Comparator.comparing(Event::getEra));

        // Resolve non-sustenance events first, with Sustenance always last.
        for (Event event : nonSustenance) {
            String eventMsg = "🔥 EVENT: " + event.getType() + " (Era " + event.getEra() + ") resolved!";
            support.firePropertyChange("notification", null, eventMsg);

            Map<Player, PlayerEventSnapshot> before = snapshotPlayers();
            RitualData ritualData = null;
            if (event.getType() == EventType.RITUAL) {
                ritualData = buildRitualData();
                event.applyEffect(new RitualEventContext(players, ritualData));
            } else {
                event.applyEffect(new EventContext(players));
            }

            applyEventBuildingEffects(event);
            support.firePropertyChange("notification", null, eventCalculationReport(event, before, ritualData));
        }

        for (Event event : sustenance) {
            support.firePropertyChange("notification", null, "🍖 SUSTENANCE: Time to feed the tribe!");
            Map<Player, PlayerEventSnapshot> before = snapshotPlayers();
            event.applyEffect(new EventContext(players));
            applyEventBuildingEffects(event);
            support.firePropertyChange("notification", null, eventCalculationReport(event, before, null));
        }

        notifyStateChange("eventsResolved");
    }

    /**
     * Applies player building effects that trigger from a resolved event.
     */
    private void applyEventBuildingEffects(Event event) {
        for (Player player : players) {
            for (Building building : player.getBuildings()) {
                if (building instanceof Event_Add_Effect) {
                    ((Event_Add_Effect) building).applyEventsEffect(player, event);
                }
            }
        }
    }

    /**
     * Captures food and prestige values before resolving an event.
     *
     * @return a snapshot for every player
     */
    private Map<Player, PlayerEventSnapshot> snapshotPlayers() {
        Map<Player, PlayerEventSnapshot> snapshots = new LinkedHashMap<>();
        for (Player player : players) {
            snapshots.put(player, new PlayerEventSnapshot(player.getFood(), player.getPrestigePoints()));
        }
        return snapshots;
    }

    /**
     * Builds a per-player report of the changes caused by an event.
     *
     * @param event resolved event
     * @param before player values captured before resolution
     * @param ritualData ritual totals, or {@code null} for non-ritual events
     * @return the formatted calculation report
     */
    private String eventCalculationReport(Event event, Map<Player, PlayerEventSnapshot> before, RitualData ritualData) {
        StringBuilder sb = new StringBuilder("Event calculations - ")
                .append(event.getType())
                .append(" Era ")
                .append(event.getEra());

        for (Player player : players) {
            PlayerEventSnapshot snapshot = before.get(player);
            if (snapshot == null) {
                continue;
            }

            int foodDelta = player.getFood() - snapshot.food;
            int prestigeDelta = player.getPrestigePoints() - snapshot.prestige;
            sb.append("\n")
                    .append(safePlayerLabel(player))
                    .append(": ")
                    .append(eventFormula(event, player, snapshot, ritualData))
                    .append(" | food ")
                    .append(snapshot.food)
                    .append(" -> ")
                    .append(player.getFood())
                    .append(" (")
                    .append(signed(foodDelta))
                    .append("), prestige ")
                    .append(snapshot.prestige)
                    .append(" -> ")
                    .append(player.getPrestigePoints())
                    .append(" (")
                    .append(signed(prestigeDelta))
                    .append(")");
        }

        return sb.toString();
    }

    /**
     * Describes the rule calculation applied to one player by an event.
     *
     * @param event resolved event
     * @param player affected player
     * @param snapshot values captured before resolution
     * @param ritualData ritual totals, or {@code null} when not applicable
     * @return a human-readable formula
     */
    private String eventFormula(Event event, Player player, PlayerEventSnapshot snapshot, RitualData ritualData) {
        if (event instanceof Hunting hunting) {
            long hunters = player.getCharacters().stream()
                    .filter(character -> character.getType() == CharacterType.HUNTER)
                    .count();
            return hunters + " hunters x food " + hunting.getFood() + " = " + (hunters * hunting.getFood())
                    + ", " + hunters + " hunters x prestige " + hunting.getPoints() + " = " + (hunters * hunting.getPoints());
        }

        if (event instanceof Sustenance sustenance) {
            int characterCount = player.getCharacters().size();
            int rawCost = characterCount * sustenance.getFood();
            int gathererDiscount = gathererSustenanceDiscount(player);
            int buildingDiscount = buildingSustenanceDiscount(player);
            int discount = gathererDiscount + buildingDiscount;
            int requiredFood = Math.max(0, rawCost - discount);
            int paidFood = Math.min(snapshot.food, requiredFood);
            int shortage = Math.max(0, requiredFood - snapshot.food);
            int unfed = sustenance.getFood() <= 0 ? 0 : (int) Math.ceil((double) shortage / sustenance.getFood());
            int penalty = shortage == 0 ? 0 : -(unfed * sustenance.getPoints());
            return "food required: " + characterCount + " characters x " + sustenance.getFood()
                    + " = " + rawCost
                    + " - gatherers " + gathererDiscount
                    + " - buildings " + buildingDiscount
                    + " = " + requiredFood
                    + "; food: " + snapshot.food + " - " + paidFood + " = " + player.getFood()
                    + "; missing food " + shortage
                    + " -> unfed characters " + unfed
                    + "; points: " + snapshot.prestige + " - (" + unfed + " x " + sustenance.getPoints()
                    + ") = " + (snapshot.prestige + penalty);
        }

        if (event instanceof RockPaintings paintings) {
            long artists = player.getCharacters().stream()
                    .filter(character -> character.getType() == CharacterType.ARTIST)
                    .count();
            if (artists >= paintings.getThresholdHigh()) {
                return artists + " artists >= " + paintings.getThresholdHigh()
                        + ", prestige " + artists + " x " + paintings.getPpIfMet()
                        + " = " + (artists * paintings.getPpIfMet());
            }
            if (artists <= paintings.getThresholdLow()) {
                return artists + " artists <= " + paintings.getThresholdLow()
                        + ", prestige " + paintings.getPpIfNotMet();
            }
            return artists + " artists between thresholds, no direct event change";
        }

        if (event instanceof ShamanicRitual ritual && ritualData != null) {
            int stars = ritualData.getStar(player);
            boolean max = stars == ritualData.getMaxStar();
            boolean min = stars == ritualData.getMinStar();
            boolean protectedFromMin = player.getBuildings().stream().anyMatch(building -> building instanceof Event_Less_Ritual);
            return "stars " + stars
                    + ", max " + ritualData.getMaxStar()
                    + (max ? " -> prestige " + ritual.getMaxPoints() : "")
                    + ", min " + ritualData.getMinStar()
                    + (min ? protectedFromMin ? " -> min protected" : " -> prestige " + ritual.getMinPoints() : "");
        }

        return "food/prestige delta shown";
    }

    /**
     * Sums the Sustenance discount supplied by a player's Gatherers.
     *
     * @param player player whose characters are inspected
     * @return the total Gatherer discount
     */
    private int gathererSustenanceDiscount(Player player) {
        int discount = 0;
        for (Character character : player.getCharacters()) {
            if (character instanceof Gatherer) {
                discount += ((Gatherer) character).getDiscount();
            }
        }
        return discount;
    }

    /**
     * Sums the Sustenance discount supplied by a player's buildings.
     *
     * @param player player whose buildings are inspected
     * @return the total building discount
     */
    private int buildingSustenanceDiscount(Player player) {
        int discount = 0;
        for (Building building : player.getBuildings()) {
            if (building instanceof Event_Sustenance_Effect) {
                discount += ((Event_Sustenance_Effect) building).applyDiscountEffect(player);
            }
        }
        return discount;
    }

    /**
     * Selects a stable display label for a player.
     *
     * @param player player to label
     * @return the nickname when available, otherwise the color name
     */
    private String safePlayerLabel(Player player) {
        if (player.getNickname() != null && !player.getNickname().isBlank()) {
            return player.getNickname();
        }
        return player.getColor().name();
    }

    /**
     * Formats an integer with an explicit sign for non-negative values.
     *
     * @param value value to format
     * @return the signed decimal representation
     */
    private String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    private static final class PlayerEventSnapshot {
        private final int food;
        private final int prestige;

        /**
         * Creates an immutable snapshot of event-sensitive player values.
         *
         * @param food food available before event resolution
         * @param prestige prestige points before event resolution
         */
        private PlayerEventSnapshot(int food, int prestige) {
            this.food = food;
            this.prestige = prestige;
        }
    }

    /**
     * Builds the ritual star totals used by Shamanic Ritual events.
     */
    private RitualData buildRitualData() {
        RitualData ritualData = new RitualData();

        for (Player player : players) {
            ritualData.addStar(player, 0);

            for (Character character : player.getCharacters()) {
                if (character instanceof Shaman) {
                    ritualData.addStar(player, ((Shaman) character).getStar());
                }
            }

            for (Building building : player.getBuildings()) {
                if (building instanceof AddStar) {
                    ((AddStar) building).applyRitualEffect(player, ritualData);
                }
            }
        }

        return ritualData;
    }

    /**
        * Updates the current era and applies the start-of-era building steps.
        * <p>
        * When entering Era II or Era III:
        * <ol>
        *   <li>(Era III only) discard any buildings in the lower row</li>
        *   <li>move all upper-row buildings to the lower row</li>
        *   <li>reveal the new era's buildings in the upper row</li>
        * </ol>
     *
     * @param newEra The new era to transition to.
     */
    public void handleEraChange(Era newEra) {
        if (newEra == null || newEra == currentEra) {
            return;
        }

        currentEra = newEra;
        support.firePropertyChange("notification", null, "✨ THE TRIBE ADVANCES TO ERA " + newEra + "!");

        // Start-of-era rules (Era II and Era III):
        // 1) Discard lower-row buildings only when entering Era III.
        // 2) Move upper-row buildings to the lower row.
        // 3) Reveal the new era's buildings in the upper row.
        if (currentEra == Era.III) {
            for (Building building : lowerBuildings) {
                if (building != null) {
                    building.setPosition(Position.DECK);
                }
            }
            lowerBuildings.clear();
        }

        if (currentEra == Era.II || currentEra == Era.III) {
            for (Building building : upperBuildings) {
                if (building != null) {
                    building.setPosition(Position.BOTTOM);
                }
            }
            lowerBuildings.addAll(upperBuildings);
            upperBuildings.clear();

            Stack<Building> newEraDeck = getBuildingDeckForEra(currentEra);
            while (!newEraDeck.isEmpty()) {
                Building building = newEraDeck.pop();
                if (building != null) {
                    building.setPosition(Position.TOP);
                }
                upperBuildings.add(building);
            }
        }
        
        notifyStateChange("eraChanged");
    }

    /**
     * Returns the buildings currently visible in the upper row.
     *
     * @return the upper-row buildings
     */
    public List<Building> getUpperBuildings() {
        return upperBuildings;
    }

    /**
     * Returns the buildings currently visible in the lower row.
     *
     * @return the lower-row buildings
     */
    public List<Building> getLowerBuildings() {
        return lowerBuildings;
    }

    /**
     * Applies all continuous building effects for the player.
     * This should be called whenever the player's tribe or buildings change.
     */
    private void applyContinuousEffects(Player player) {
        for (Building building : player.getBuildings()) {
            if (building instanceof it.polimi.Buildings.InGame_Effect) {
                ((it.polimi.Buildings.InGame_Effect) building).applyContinuousEffect(player);
            }
        }
    }

    /**
     * Takes one character card from the upper tribe row by index.
     *
     * @param player player receiving the card
     * @param index zero-based card index
     * @return the selected card
     */
    public Tribe takeUpperTribeCard(Player player, int index) {
        if (player == null) {
            throw new IllegalArgumentException("Player is null");
        }
        if (index < 0 || index >= upperRow.size()) {
            throw new IllegalArgumentException("Invalid upper row index.");
        }

        Tribe card = upperRow.get(index);
        if (card.isEvent()) {
            throw new IllegalArgumentException("You cannot select events.");
        }

        upperRow.remove(index);
        card.assignToPlayer(player);
        applyContinuousEffects(player);
        notifyStateChange("tribeCardTaken");
        return card;
    }

    /**
     * Takes one character card from the lower tribe row by index.
     *
     * @param player player receiving the card
     * @param index zero-based card index
     * @return the selected card
     */
    public Tribe takeLowerTribeCard(Player player, int index) {
        if (player == null) {
            throw new IllegalArgumentException("Player is null");
        }
        if (index < 0 || index >= lowerRow.size()) {
            throw new IllegalArgumentException("Invalid lower row index.");
        }

        Tribe card = lowerRow.get(index);
        if (card.isEvent()) {
            throw new IllegalArgumentException("You cannot select events.");
        }

        lowerRow.remove(index);
        card.assignToPlayer(player);
        applyContinuousEffects(player);
        notifyStateChange("tribeCardTaken");
        return card;
    }

    /**
     * Takes one building from the upper building row by index, paying its food cost.
     *
     * @param player player purchasing the building
     * @param index zero-based building index
     * @return the purchased building
     */
    public Building takeUpperBuildingCard(Player player, int index) {
        if (player == null) {
            throw new IllegalArgumentException("Player is null");
        }
        if (index < 0 || index >= upperBuildings.size()) {
            throw new IllegalArgumentException("Invalid upper building index.");
        }

        Building building = upperBuildings.get(index);
        payBuildingCost(player, building);
        upperBuildings.remove(index);
        player.addBuilding(building);
        applyContinuousEffects(player);
        notifyStateChange("buildingTaken");
        return building;
    }

    /**
     * Takes one building from the lower building row by index, paying its food cost.
     *
     * @param player player purchasing the building
     * @param index zero-based building index
     * @return the purchased building
     */
    public Building takeLowerBuildingCard(Player player, int index) {
        if (player == null) {
            throw new IllegalArgumentException("Player is null");
        }
        if (index < 0 || index >= lowerBuildings.size()) {
            throw new IllegalArgumentException("Invalid lower building index.");
        }

        Building building = lowerBuildings.get(index);
        payBuildingCost(player, building);
        lowerBuildings.remove(index);
        player.addBuilding(building);
        applyContinuousEffects(player);
        notifyStateChange("buildingTaken");
        return building;
    }

    /**
     * Processes the cost payment for acquiring a building card.
     * Applies builder character discounts and deducts food from the player.
     * Throws an exception if the player doesn't have enough food even after discounts.
     *
     * @param player the player purchasing the building
     * @param building the building card being purchased
     * @throws IllegalArgumentException if the player doesn't have enough food for the effective cost
     */
    private void payBuildingCost(Player player, Building building) {
        int discount = 0;
        for (Character character : player.getCharacters()) {
            if (character instanceof Builder) {
                discount += ((Builder) character).getDiscount();
            }
        }

        int effectiveCost = Math.max(0, building.getCost() - discount);
        if (player.getFood() < effectiveCost) {
            throw new IllegalArgumentException("Not enough food to take this building.");
        }

        player.payFood(effectiveCost, 0);
    }

    /**
     * Returns the remaining tribe deck.
     *
     * @return The stack of tribe cards.
     */
    public Stack<Tribe> getTribeDeck() {
        return tribeDeck;
    }

    /**
     * Returns the current round number.
     *
     * @return The current round.
     */
    public int getRound() {
        return round;
    }

    /**
     * Returns the current era of the game.
     *
     * @return The current era.
     */
    public Era getCurrentEra() {
        return currentEra;
    }

    /**
     * Returns the cards currently visible in the upper row.
     *
     * @return List of tribe cards in the upper row.
     */
    public List<Tribe> getUpperRow() {
        return upperRow;
    }

    /**
     * Returns the cards currently visible in the lower row.
     *
     * @return List of tribe cards in the lower row.
     */
    public List<Tribe> getLowerRow() {
        return lowerRow;
    }

    /**
     * Returns the list of registered players.
     *
     * @return List of players.
     */
    public List<Player> getPlayers() {
        return players;
    }

    /**
     * Returns the offer tiles currently available to players.
     *
     * @return List of available offer tiles.
     */
    public List<OfferTile> getOfferTiles() {
        return offerTiles;
    }

    /**
     * Returns a compact textual summary of the current game state.
     *
     * @return A string representation of the game state.
     */
    @Override
    public String toString() {
        return "Game{"
                + "round=" + round
                + ", currentEra=" + currentEra
                + ", players=" + players.size()
                + ", offerTiles=" + offerTiles.size()
                + ", upperRow=" + upperRow.size()
                + ", lowerRow=" + lowerRow.size()
                + ", upperBuildings=" + upperBuildings.size()
                + ", lowerBuildings=" + lowerBuildings.size()
                + '}';
    }
}
