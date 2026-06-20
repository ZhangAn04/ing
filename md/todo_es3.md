# TODO - Esercitazione 3: Client Resilience & Auto-Action Logic

## 📡 1. Connection Monitoring (Heartbeat)
- [x] **Client-Side Heartbeat**: Implement a `ScheduledExecutorService` in `ClientMain` to send a `"HEARTBEAT"` `ActionMessage` every 5-10 seconds.
- [x] **Server-Side Activity Tracking**: 
    - [x] Add `long lastActivity` timestamp to `ClientHandler`.
    - [x] Update `lastActivity` in `processAction()` whenever any message (including Heartbeat) is received.
- [x] **Disconnection Detection**: Implement a "Monitor" thread in `Lobby` or `ServerMain` that checks if `currentTime - lastActivity > TIMEOUT` (e.g., 20s).

## 👤 2. Player State Management
- [x] **Online Flag**: Add `private boolean online = true` to the `Player` class with getter/setter.
- [x] **Status Transition**: Update `ClientHandler` cleanup logic to call `controller.setPlayerOffline(nickname)` instead of fully unregistering the player from the match.

## 🤖 3. Auto-Action Logic (Game Controller)
- [x] **Offline Turn Detection**: In `GameController`, check `player.isOnline()` at the start of every turn.
- [ ] **Default Action Execution**: If a player is offline, the controller must automatically trigger a default action:
    - [ ] Drafting Phase: Automatically pick the first available card (index 0).
    - [ ] Placement Phase: Automatically place the totem on the first available slot.
    - [ ] Event Phase: Skip optional effects or take 0-value default actions.
- [x] **Notification**: Broadcast a `STATUS_UPDATE` or `NOTIFICATION` to all online players when an "Auto-Move" is performed for an offline player. (Skeleton implemented via TODO)

## ⏱️ 4. Thinking Timer (Active Players)
- [x] **Turn Timer**: Implement a 2-minute "Thinking Timer" for online players.
- [x] **Forced Auto-Move**: If the timer expires without receiving an `ActionMessage`, trigger the same "Auto-Action" logic used for offline players.

## 🔄 5. Partite Multiple & Secure Reconnection (Room + PIN)
*Advanced Requirement: Support multiple concurrent matches with choose-or-create logic.*

- [x] **GameRoom Abstraction**: Create a `GameRoom` class to encapsulate a unique `Game`, `GameController`, and its associated `Player` list.
- [x] **Lobby Refactoring**: Update `Lobby` to manage a `Map<Integer, GameRoom>` instead of a single global game.
- [x] **PIN Storage**: Add `private String pin` to the `Player` class to store the user-provided secret for reconnections.
- [x] **Match Creation Logic**:
    - [x] Implement `CREATE_ROOM` command: First player defines RoomID, MaxPlayers, and (optional) Password.
    - [x] Implement `JOIN_ROOM` command: Players select an existing RoomID.
- [x] **Reconnection Protocol**:
    - [x] **Detection**: If a player is disconnected (`online = false`), the `GameRoom` persists.
    - [x] **Validation**: Returning players must provide `Nickname` + `RoomID` + `PIN`.
    - [x] **Re-attachment**: If validated, re-attach the new `ClientHandler` (Socket) or `Callback` (RMI) to the existing `VirtualView`.
- [x] **Multi-Protocol Support**: Ensure both Socket `ActionMessage` and `RmiLoginRequest` are updated with `RoomID` and `PIN` fields.



