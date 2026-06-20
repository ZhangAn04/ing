package it.polimi.Network.Client;

import it.polimi.Network.Common.SerializedUpdate;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the console TUI renderer.
 */
class ConsoleTuiRendererTest {

    /**
     * Verifies that the lobby status summary only shows lobby-related information.
     */
    @Test
    void lobbyStatusShowsOnlyLobbyInformation() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleTuiRenderer renderer = new ConsoleTuiRenderer(new PrintStream(output));
        renderer.markManualStatusRequest();
        String status = "Status: initialized=false, round=1, era=I\n"
                + "Phase: LOBBY\n"
                + "Required players: 3\n"
                + "Connected players: Alice, Bob\n"
                + "Ready players: Alice\n"
                + "Ready count: 1/3\n"
                + "Offer tiles: A, B, C\n"
                + "Upper tribe row:\n"
                + "[0] Hunter\n"
                + "Upper buildings:\n"
                + "[0] Hut\n"
                + "Lower tribe row:\n"
                + "[0] Artist\n"
                + "Lower buildings:\n"
                + "[0] Shelter\n";

        renderer.renderServerUpdate(new SerializedUpdate("STATUS_UPDATE", status, true));

        String rendered = output.toString();
        assertTrue(rendered.contains("[STATUS_UPDATE] Lobby"));
        assertTrue(rendered.contains("Required players: 3"));
        assertTrue(rendered.contains("Connected players: Alice, Bob"));
        assertTrue(rendered.contains("Ready players: Alice"));
        assertTrue(rendered.contains("Ready count: 1/3"));
        assertFalse(rendered.contains("Round 1"));
        assertFalse(rendered.contains("Offer tiles:"));
        assertFalse(rendered.contains("Upper tribe row:"));
        assertFalse(rendered.contains("Upper buildings:"));
        assertFalse(rendered.contains("Lower tribe row:"));
        assertFalse(rendered.contains("Lower buildings:"));
    }

    /**
     * Verifies that automatic lobby status snapshots are not rendered.
     */
    @Test
    void automaticLobbyStatusIsNotRendered() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleTuiRenderer renderer = new ConsoleTuiRenderer(new PrintStream(output));
        String status = "Status: initialized=false, round=1, era=I\n"
                + "Phase: LOBBY\n"
                + "Connected players: Alice, Bob\n"
                + "Ready players: Alice\n"
                + "Ready count: 1/2\n";

        renderer.renderServerUpdate(new SerializedUpdate("STATUS_UPDATE", status, true));

        assertFalse(output.toString().contains("[STATUS_UPDATE] Lobby"));
    }

    /**
     * Verifies that compact model updates do not print unknown round or era labels.
     */
    @Test
    void compactModelUpdatesDoNotRenderUnknownRoundAndEra() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleTuiRenderer renderer = new ConsoleTuiRenderer(new PrintStream(output));

        renderer.renderServerUpdate(new SerializedUpdate(
                "STATUS_UPDATE",
                "Game{round=1, currentEra=I, players=2, offerTiles=3}",
                true));

        String rendered = output.toString();
        assertFalse(rendered.contains("Round ?"));
        assertFalse(rendered.contains("Era ?"));
    }

    /**
     * Verifies that the game status shows totems placed by every player.
     */
    @Test
    void gameStatusShowsTotemsPlacedByEveryPlayer() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleTuiRenderer renderer = new ConsoleTuiRenderer(new PrintStream(output));
        String status = "Status: initialized=true, round=1, era=I\n"
                + "Phase: PLACING_TOTEMS\n"
                + "To place: Bob\n"
                + "Players:\n"
                + "Alice color=BLUE food=2 pp=0 chars=0 buildings=0 tile=A\n"
                + "Bob color=ORANGE food=3 pp=0 chars=0 buildings=0 tile=-\n";

        renderer.renderServerUpdate(new SerializedUpdate("STATUS_UPDATE", status, true));

        String rendered = output.toString();
        assertTrue(rendered.contains("Placed totems: Alice=A"));
        assertTrue(rendered.contains("Totem placement: Bob"));
        assertFalse(rendered.contains("Bob=-"));
    }

    /**
     * Verifies that the totem hint is shown only to the local player who must act.
     */
    @Test
    void gameStatusShowsTotemHintOnlyToCurrentLocalPlayer() {
        String status = "Status: initialized=true, round=1, era=I\n"
                + "Phase: PLACING_TOTEMS\n"
                + "To place: Bob\n"
                + "Available tiles: A, B, C\n";

        ByteArrayOutputStream bobOutput = new ByteArrayOutputStream();
        ConsoleTuiRenderer bobRenderer = new ConsoleTuiRenderer(new PrintStream(bobOutput));
        bobRenderer.setLocalNickname("Bob");
        bobRenderer.renderServerUpdate(new SerializedUpdate("STATUS_UPDATE", status, true));
        String bobStatus = bobOutput.toString();
        assertTrue(bobStatus.contains("Available offer tiles: A, B, C"));
        assertTrue(bobStatus.contains("Hint: use 'totem <letter>'"));
        assertTrue(bobStatus.indexOf("Available offer tiles:") < bobStatus.indexOf("Hint: use 'totem <letter>'"));

        ByteArrayOutputStream aliceOutput = new ByteArrayOutputStream();
        ConsoleTuiRenderer aliceRenderer = new ConsoleTuiRenderer(new PrintStream(aliceOutput));
        aliceRenderer.setLocalNickname("Alice");
        aliceRenderer.renderServerUpdate(new SerializedUpdate("STATUS_UPDATE", status, true));
        assertFalse(aliceOutput.toString().contains("Hint: use 'totem <letter>'"));
    }

    /**
     * Verifies that the pick hint is shown to the local player during action resolution.
     */
    @Test
    void gameStatusShowsPickHintToCurrentLocalPlayer() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleTuiRenderer renderer = new ConsoleTuiRenderer(new PrintStream(output));
        renderer.setLocalNickname("Alice");
        String status = "Status: initialized=true, round=1, era=I\n"
                + "Phase: RESOLVING_ACTIONS\n"
                + "Acting: Alice (tile B) remaining upper=1 lower=0\n";

        renderer.renderServerUpdate(new SerializedUpdate("STATUS_UPDATE", status, true));

        assertTrue(output.toString().contains(
                "Hint: use 'pick <upper|lower> <t|b> <index>'"));
    }

    /**
     * Verifies that the pick hint is rendered below all available card rows.
     */
    @Test
    void gameStatusShowsPickHintBelowCardRows() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleTuiRenderer renderer = new ConsoleTuiRenderer(new PrintStream(output));
        renderer.setLocalNickname("Alice");
        String status = "Status: initialized=true, round=1, era=I\n"
                + "Phase: RESOLVING_ACTIONS\n"
                + "Acting: Alice (tile B) remaining upper=1 lower=1\n"
                + "Upper tribe row:\n[0] Hunter\n"
                + "Lower buildings:\n[0] Shelter\n";

        renderer.renderServerUpdate(new SerializedUpdate("STATUS_UPDATE", status, true));

        String rendered = output.toString();
        assertTrue(rendered.indexOf("[0] Shelter") < rendered.indexOf("Hint: use 'pick"));
    }

    /**
     * Verifies that image metadata is removed from terminal output.
     */
    @Test
    void terminalOutputHidesImageMetadata() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleTuiRenderer renderer = new ConsoleTuiRenderer(new PrintStream(output));
        String status = "Status: initialized=true, round=1, era=I\n"
                + "Phase: RESOLVING_ACTIONS\n"
                + "Upper tribe row:\n"
                + "[0] Hunter {img=001}\n"
                + "Lower buildings:\n"
                + "[0] Shelter {img: 042}\n";

        renderer.renderServerUpdate(new SerializedUpdate("STATUS_UPDATE", status, true));
        renderer.renderServerUpdate(new SerializedUpdate(
                "NOTIFICATION", "Taken: Hunter {img=001}", true));

        String rendered = output.toString();
        assertTrue(rendered.contains("[0] Hunter"));
        assertTrue(rendered.contains("[0] Shelter"));
        assertFalse(rendered.contains("{img="));
        assertFalse(rendered.contains("{img:"));
    }

    /**
     * Verifies that the first player who chooses a color receives the color hint.
     */
    @Test
    void notificationShowsColorHintToFirstChooser() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleTuiRenderer renderer = new ConsoleTuiRenderer(new PrintStream(output));
        renderer.setLocalNickname("Alice");

        renderer.renderServerUpdate(new SerializedUpdate(
                "NOTIFICATION", "All players ready. Alice chooses color first.", true));

        assertTrue(output.toString().contains("Hint: use 'color <name>'"));
    }

    /**
     * Verifies that the color hint is shown only to the next player who must choose.
     */
    @Test
    void notificationShowsColorHintOnlyToNextChooser() {
        String notification = "Alice chose BLUE. Available colors: ORANGE, PURPLE. Next color choice: Bob";

        ByteArrayOutputStream bobOutput = new ByteArrayOutputStream();
        ConsoleTuiRenderer bobRenderer = new ConsoleTuiRenderer(new PrintStream(bobOutput));
        bobRenderer.setLocalNickname("Bob");
        bobRenderer.renderServerUpdate(new SerializedUpdate("NOTIFICATION", notification, true));
        assertTrue(bobOutput.toString().contains("Hint: use 'color <name>'"));

        ByteArrayOutputStream aliceOutput = new ByteArrayOutputStream();
        ConsoleTuiRenderer aliceRenderer = new ConsoleTuiRenderer(new PrintStream(aliceOutput));
        aliceRenderer.setLocalNickname("Alice");
        aliceRenderer.renderServerUpdate(new SerializedUpdate("NOTIFICATION", notification, true));
        assertFalse(aliceOutput.toString().contains("Hint: use 'color <name>'"));
    }

    /**
     * Verifies that asynchronous notifications clear the old prompt line and restore the prompt below.
     */
    @Test
    void asynchronousNotificationPrintsCommandPrompt() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleTuiRenderer renderer = new ConsoleTuiRenderer(new PrintStream(output));
        renderer.renderPrompt();

        renderer.renderServerUpdate(new SerializedUpdate(
                "NOTIFICATION", "Bob connected to the room.", true));

        String rendered = output.toString();
        assertTrue(rendered.contains("> \r  \r" + System.lineSeparator() + "[NOTIFICATION]"));
        assertTrue(rendered.endsWith("> "));
        assertFalse(rendered.contains("> " + System.lineSeparator() + "[NOTIFICATION]"));
    }

    /**
     * Verifies that a filtered update leaves a previously visible prompt untouched.
     */
    @Test
    void filteredStatusUpdateKeepsVisiblePrompt() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleTuiRenderer renderer = new ConsoleTuiRenderer(new PrintStream(output));
        renderer.renderPrompt();
        String lobbyStatus = "Status: initialized=false, round=0, era=I\n"
                + "Required players: 2\nConnected players: Alice\nReady count: 0/2\n";

        renderer.renderServerUpdate(new SerializedUpdate("STATUS_UPDATE", lobbyStatus, true));

        assertEquals("> ", output.toString());
    }

    /**
     * Verifies that a local command response restores the command prompt.
     */
    @Test
    void localCommandResponsePrintsCommandPrompt() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleTuiRenderer renderer = new ConsoleTuiRenderer(new PrintStream(output));
        renderer.markCommandSent("ready");

        renderer.renderServerUpdate(new SerializedUpdate(
                "NOTIFICATION", "Alice is ready. Ready players: 1", true));

        assertTrue(output.toString().endsWith("> "));
    }

    /**
     * Verifies that the player command prints the logged-in nickname.
     */
    @Test
    void playerCommandShowsLoggedInNickname() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleTuiRenderer renderer = new ConsoleTuiRenderer(new PrintStream(output));
        renderer.setLocalNickname("Alice");

        renderer.renderPlayerIdentity();

        assertTrue(output.toString().contains("Logged in player: Alice"));
    }
}
