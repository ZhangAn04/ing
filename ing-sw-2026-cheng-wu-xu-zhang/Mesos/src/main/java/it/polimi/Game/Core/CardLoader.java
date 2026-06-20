package it.polimi.Game.Core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import it.polimi.Abstract.Building;
import it.polimi.Abstract.Card;
import it.polimi.Abstract.Event;
import it.polimi.Abstract.Character;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import it.polimi.Events.Hunting;
import it.polimi.Events.RockPaintings;
import it.polimi.Events.ShamanicRitual;
import it.polimi.Events.Sustenance;
import it.polimi.Game.Elements.OfferTile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads cards and offer tiles from the JSON resources bundled with the application.
 * <p>
 * This class uses the Jackson library to deserialize game assets from the resources folder,
 * providing the necessary data to initialize the game state.
 * </p>
 */
public class CardLoader {

    /** Creates a card loader. */
    public CardLoader() {
    }
    /** The Jackson object mapper used for JSON deserialization. */
    private final ObjectMapper mapper = new ObjectMapper();

    /** Field names used in start.json, for example p1-food or p2-points. */
    private static final Pattern TURN_ORDER_FIELD = Pattern.compile("p(\\d+)-(food|points)");

    /**
     * Loads all character cards from characters.json.
     *
     * @return A list of {@link Character} objects.
     * @throws Exception If the resource file is missing or malformed.
     */
    public List<Character> loadCharacters() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("characters.json")) {
            if (is == null) {
                throw new IllegalArgumentException("characters.json not found");
            }

            String json = new String(readAllBytes(is), StandardCharsets.UTF_8);
            CharactersData data = mapper.readValue(json, CharactersData.class);
            List<Character> characters = data.getCharacters();

            JsonNode root = mapper.readTree(json);
            applyAssetIds(characters, root.get("characters"));
            return characters;
        }
    }

    /**
     * Loads all event cards from events.json.
     *
     * @return A list of {@link Event} objects.
     * @throws IOException If an I/O error occurs during resource loading.
     */
    public List<Event> loadEvents() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("events.json")) {
            if (is == null) {
                throw new IllegalArgumentException("events.json not found");
            }

            JsonNode root = mapper.readTree(is);
            JsonNode eventsNode = root.get("events");

            if (eventsNode == null || !eventsNode.isArray()) {
                throw new IllegalArgumentException("events.json has no valid 'events' array");
            }

            List<Event> events = new ArrayList<>();
            for (JsonNode node : eventsNode) {
                Event event = parseEventNode(node);
                int assetId = parseAssetId(node);
                if (event != null && assetId > 0 && event.getAssetId() <= 0) {
                    event.setAssetId(assetId);
                }
                events.add(event);
            }

            return events;
        }
    }

    /**
     * Converts one JSON event record to the corresponding concrete Event implementation.
     */
    private Event parseEventNode(JsonNode node) {
        String type = requiredText(node, "type");
        Era era = Era.valueOf(requiredText(node, "era"));

        switch (type) {
            case "HUNT":
            case "HUNTING":
                return new Hunting(
                        era,
                        Position.DECK,
                        requiredInt(node, "foodPerHunter"),
                        requiredInt(node, "ppPerHunter")
                );

            case "SUSTENANCE":
                return new Sustenance(
                        era,
                        Position.DECK,
                        requiredInt(node, "foodPerCharacter"),
                        requiredInt(node, "ppPenaltyPerUnfed")
                );

            case "RITUAL":
                return new ShamanicRitual(
                        era,
                        Position.DECK,
                        requiredInt(node, "ppRewardMost"),
                    -Math.abs(requiredInt(node, "ppPenaltyLeast"))
                );

            case "ROCK_PAINTINGS":
                return new RockPaintings(
                        era,
                        Position.DECK,
                        requiredInt(node, "thresholdHigh"),
                        requiredInt(node, "thresholdLow"),
                        requiredInt(node, "ppIfMet"),
                        requiredInt(node, "ppIfNotMet")
                );

            default:
                throw new IllegalArgumentException("Unknown event type: " + type);
        }
    }

    /**
     * Reads a required string field from a JSON node.
     */
    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing required text field: " + fieldName);
        }
        return value.asText();
    }

    /**
     * Reads a required integer field from a JSON node.
     */
    private int requiredInt(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing required int field: " + fieldName);
        }
        return value.asInt();
    }

    /**
     * Loads all building cards from buildings.json.
     *
     * @return A list of {@link Building} objects.
     * @throws IOException If an I/O error occurs during resource loading.
     */
    public List<Building> loadBuildings() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("buildings.json")) {
            if (is == null) {
                throw new IllegalArgumentException("buildings.json not found");
            }
            String json = new String(readAllBytes(is), StandardCharsets.UTF_8);
            BuildingsData data = mapper.readValue(json, BuildingsData.class);
            List<Building> buildings = data.getBuildings();

            JsonNode root = mapper.readTree(json);
            applyAssetIds(buildings, root.get("buildings"));
            return buildings;
        }
    }

    /**
     * Copies asset identifiers from JSON entries to their deserialized cards.
     *
     * @param cards cards corresponding to the JSON entries
     * @param arrayNode JSON array containing asset identifiers
     */
    private void applyAssetIds(List<? extends Card> cards, JsonNode arrayNode) {
        if (cards == null || arrayNode == null || !arrayNode.isArray()) {
            return;
        }

        int limit = Math.min(cards.size(), arrayNode.size());
        for (int i = 0; i < limit; i++) {
            Card card = cards.get(i);
            if (card == null) {
                continue;
            }

            int assetId = parseAssetId(arrayNode.get(i));
            if (assetId > 0 && card.getAssetId() <= 0) {
                card.setAssetId(assetId);
            }
        }
    }

    /**
     * Parses an asset identifier from its numeric or textual JSON representation.
     *
     * @param node JSON value to parse
     * @return the parsed identifier, or {@code 0} when absent or malformed
     */
    private int parseAssetId(JsonNode node) {
        if (node == null) {
            return -1;
        }

        JsonNode idNode = node.get("id");
        if (idNode == null || idNode.isNull()) {
            return -1;
        }

        String raw = idNode.asText("");
        if (raw.isEmpty()) {
            return -1;
        }

        String digits = raw.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return -1;
        }

        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Reads an input stream completely without closing it.
     *
     * @param is stream to read
     * @return all bytes read from the stream
     * @throws IOException if reading fails
     */
    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    /**
     * Loads all offer tiles from offer_tiles.json.
     *
     * @return A list of {@link OfferTile} objects.
     * @throws IOException If an I/O error occurs during resource loading.
     */
    public List<OfferTile> loadOfferTiles() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("offer_tiles.json")) {
            if (is == null) {
                throw new IllegalArgumentException("offer_tiles.json not found");
            }

            OfferTilesData data = mapper.readValue(is, OfferTilesData.class);
            return data.getOfferTiles();
        }
    }

    /**
     * Loads turn-order track food and prestige fallback rules from start.json.
     *
     * @return rules indexed by player count
     * @throws IOException if the resource cannot be read
     */
    public Map<Integer, TurnOrderTrackRule[]> loadTurnOrderTrackRules() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("start.json")) {
            if (is == null) {
                throw new IllegalArgumentException("start.json not found");
            }

            JsonNode root = mapper.readTree(is);
            JsonNode playersNode = root.get("players");
            if (playersNode == null || !playersNode.isArray()) {
                throw new IllegalArgumentException("start.json has no valid 'players' array");
            }

            Map<Integer, TurnOrderTrackRule[]> rulesByPlayerCount = new LinkedHashMap<>();
            for (JsonNode node : playersNode) {
                String id = requiredText(node, "id");
                int playerCount = parsePlayerCount(id);
                int[] foodDeltas = new int[playerCount];
                int[] prestigePenalties = new int[playerCount];

                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    Matcher matcher = TURN_ORDER_FIELD.matcher(field.getKey());
                    if (!matcher.matches()) {
                        continue;
                    }

                    int slot = Integer.parseInt(matcher.group(1));
                    if (slot < 1 || slot > playerCount) {
                        throw new IllegalArgumentException("Invalid turn-order slot in start.json: " + field.getKey());
                    }

                    int value = field.getValue().asInt();
                    if ("food".equals(matcher.group(2))) {
                        foodDeltas[slot - 1] = value;
                    } else {
                        prestigePenalties[slot - 1] = Math.abs(value);
                    }
                }

                TurnOrderTrackRule[] rules = new TurnOrderTrackRule[playerCount];
                for (int i = 0; i < playerCount; i++) {
                    rules[i] = new TurnOrderTrackRule(foodDeltas[i], prestigePenalties[i]);
                }
                rulesByPlayerCount.put(playerCount, rules);
            }

            return rulesByPlayerCount;
        }
    }

    /**
     * Parses player-count identifiers from start.json, such as {@code 2p}.
     *
     * @param id player-count identifier.
     * @return parsed player count.
     */
    private int parsePlayerCount(String id) {
        if (id == null || !id.endsWith("p")) {
            throw new IllegalArgumentException("Invalid player-count id in start.json: " + id);
        }
        try {
            return Integer.parseInt(id.substring(0, id.length() - 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid player-count id in start.json: " + id, e);
        }
    }

    /**
     * Container used by Jackson to map the characters.json structure.
     */
    public static class CharactersData {
        /** Creates an empty character-data container for JSON deserialization. */
        public CharactersData() {
        }
        /** The list of loaded characters. */
        private List<Character> characters;

        /**
         * Returns the loaded characters.
         *
         * @return A list of characters.
         */
        public List<Character> getCharacters() {
            return characters;
        }

        /**
         * Updates the loaded characters.
         *
         * @param characters The list of characters to set.
         */
        public void setCharacters(List<Character> characters) {
            this.characters = characters;
        }
    }

    /**
     * Container used by Jackson to map the events.json structure.
     */
    public static class EventsData {
        /** Creates an empty event-data container for JSON deserialization. */
        public EventsData() {
        }
        /** The list of loaded events. */
        private List<Event> events;

        /**
         * Returns the loaded events.
         *
         * @return A list of events.
         */
        public List<Event> getEvents() {
            return events;
        }

        /**
         * Updates the loaded events.
         *
         * @param events The list of events to set.
         */
        public void setEvents(List<Event> events) {
            this.events = events;
        }
    }

    /**
     * Container used by Jackson to map the buildings.json structure.
     */
    public static class BuildingsData {
        /** Creates an empty building-data container for JSON deserialization. */
        public BuildingsData() {
        }
        /** The list of loaded buildings. */
        private List<Building> buildings;

        /**
         * Returns the loaded buildings.
         *
         * @return A list of buildings.
         */
        public List<Building> getBuildings() {
            return buildings;
        }

        /**
         * Updates the loaded buildings.
         *
         * @param buildings The list of buildings to set.
         */
        public void setBuildings(List<Building> buildings) {
            this.buildings = buildings;
        }
    }

    /**
     * Container used by Jackson to map the offer_tiles.json structure.
     */
    public static class OfferTilesData {
        /** Creates an empty offer-tile data container for JSON deserialization. */
        public OfferTilesData() {
        }
        /** The list of loaded offer tiles. */
        private List<OfferTile> offerTiles;

        /**
         * Returns the loaded offer tiles.
         *
         * @return A list of offer tiles.
         */
        public List<OfferTile> getOfferTiles() {
            return offerTiles;
        }

        /**
         * Updates the loaded offer tiles.
         *
         * @param offerTiles The list of offer tiles to set.
         */
        public void setOfferTiles(List<OfferTile> offerTiles) {
            this.offerTiles = offerTiles;
        }
    }

    /**
     * Rule applied when a player returns to a turn-order track slot.
     */
    public static class TurnOrderTrackRule {
        private final int foodDelta;
        private final int prestigePenalty;

        /**
         * Constructs a new turn order track rule.
         *
         * @param foodDelta The change in food resources.
         * @param prestigePenalty The penalty in prestige points.
         */
        public TurnOrderTrackRule(int foodDelta, int prestigePenalty) {
            this.foodDelta = foodDelta;
            this.prestigePenalty = prestigePenalty;
        }

        /**
         * Returns the change in food resources.
         *
         * @return The food delta.
         */
        public int getFoodDelta() {
            return foodDelta;
        }

        /**
         * Returns the penalty in prestige points.
         *
         * @return The prestige penalty.
         */
        public int getPrestigePenalty() {
            return prestigePenalty;
        }
    }
}
