# Educational Session 3: Resilience & Auto-Action Logic (Mesos Project)

This document tracks the technical reasoning and architectural patterns used to implement robustness, handling client disconnections, and ensuring game continuity in a distributed environment.

---

## Lesson 1: The "Line Alive" vs. "User Thinking" Distinction

### 1. The Concept: Two Different Clocks
**Problem**: In a distributed board game, inactivity can mean two things:
1.  **Network Failure**: The client's internet died, or the app crashed.
2.  **User Choice**: The player is simply thinking deeply about their move.
Treating a "thinking player" as a "disconnected player" leads to a terrible user experience (kicking them for being slow).

### 2. The Solution: Multi-Layered Timeouts
We separate connection health from game logic:
*   **Heartbeat (Every 5-10s)**: A silent "ping" sent by the client software. This proves the *connection* is alive.
*   **Thinking Timer (2 Minutes)**: A game-rule limit. This proves the *player* is still participating.

---

## Lesson 2: Heartbeat Implementation (Passive vs. Active)

### 1. The Concept: Background Monitoring
**Problem**: A server cannot "ask" a socket if it is still there without trying to write to it. If we only check during moves, a player could be "ghost-connected" for 30 minutes before we realize they are gone.

### 2. The Solution: Timestamp-based "Sweeping"
Instead of letting every `ClientHandler` manage its own timer, we use a **Monitor Pattern**:
1.  **Timestamp**: Each handler has a `lastActivity` long.
2.  **Update**: Every message (including the silent `HEARTBEAT`) updates this timestamp.
3.  **The Sweeper**: A single background thread in the `Lobby` loops through all handlers every 5 seconds. If `currentTime - lastActivity > 20s`, it kills the socket.

---

## Lesson 3: Graceful Degradation (The Auto-Player)

### 1. The Concept: "The Show Must Go On"
**Problem**: In a 4-player game, if Player B disappears, Players A, C, and D shouldn't have to quit. However, the game cannot continue because it's Player B's turn.

### 2. The Solution: The Online/Offline State Machine
We added an `online` flag to the `Player` model.
*   **Transition**: When the network layer detects a drop, it doesn't delete the player. It calls `controller.setPlayerOffline(nickname)`.
*   **Execution**: The `GameController` now checks `if (!player.isOnline())`. If true, it automatically triggers a **Default Action** (like picking the first available card) and moves the turn forward.

---

## Lesson 4: Thinking Timers & Asynchronous Interruption

### 1. The Concept: Forcing the Pace
**Problem**: Even if a player is online, they might walk away from their computer, "soft-locking" the game for everyone else.

### 2. The Solution: `ScheduledFuture` Cancellation
We use Java's `ScheduledExecutorService` to manage the 2-minute limit:
1.  **Start**: When a turn begins, schedule a task for 120 seconds.
2.  **Interrupt**: If an `ActionMessage` arrives at the `GameController`, we immediately call `future.cancel(false)`.
3.  **Timeout**: If the timer hits zero, the task executes the same "Auto-Action" logic used for offline players.

---

## Lesson 5: Secure Reconnection (The Identity Problem)

### 1. The Concept: "Seat Stealing"
**Problem**: If Bob is offline for 10 seconds, Alice could try to log in with the name "Bob" and take over his hand/score. We need a way to prove Bob is the *real* Bob.

### 2. The Solution: Identity Persistence (Room + PIN)
We explored three ways to identify a returning player:
1.  **IP Address (Bad)**: Changes if you switch from Wi-Fi to 4G; shared in houses/labs.
2.  **Automatic Tokens (Good)**: Secure, but requires the client to store data on disk/RAM.
3.  **Room + PIN (Best for UX)**: 
    *   The first player creates a **Room ID**. 
    *   Every player chooses a **secret PIN** at login.
    *   To reconnect, you must provide: `Nickname + RoomID + PIN`.
    *   This provides high security with zero background complexity for the server.

---

## Lesson 6: Thread Safety in the Controller

### 1. The Concept: Race Conditions
**Problem**: The `GameController` can now be triggered by two different things at the exact same time:
1.  A network message from a `ClientHandler` thread.
2.  A timeout from the `TurnTimer` thread.

### 2. The Solution: Method Synchronization
Every entry point in `GameController` (like `handleCommand` and `setPlayerOffline`) is marked as `synchronized`. This ensures that even if a message and a timeout arrive at the same millisecond, the Game Model is only updated by one at a time, preventing state corruption.
