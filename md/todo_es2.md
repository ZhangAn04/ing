# Esercitazione 2: Task Checklist & Status

This file tracks the implementation progress of the Distributed MVC architecture following the lesson slides.

---

## ✅ Completed Tasks
These features are fully implemented and verified in the current `network` package.

- [x] **Infrastructure: Multi-threaded Server**
  - `ServerMain` handles multiple connections via `ClientHandler`.
- [x] **Infrastructure: Asynchronous Client**
  - `ClientMain` uses `ClientThread` for non-blocking server updates.
- [x] **Step 1: Background Listening**
  - `ClientThread` implemented.
- [x] **Step 7: Client-Side Deserialization**
  - `ClientThread` parses `SerializedUpdate` JSON.
- [x] **Dynamic Port Configuration**
  - Hybrid approach (CLI or Defaults) implemented.
- [x] **Protocol: ActionMessage & SerializedUpdate**
  - Bi-directional JSON data structures defined.
- [x] **Step 6: Handshake Validation (Nickname Uniqueness)**
  - `Lobby.java` created to manage players.
  - `ClientHandler` uses Lobby to prevent duplicate nicknames.

---

## ⏳ Pending Tasks (Waiting for Dependencies)
These steps require implementation in files managed by others or are blocked by missing logic.

### 🔴 Awaiting Game Logic (Partner)
- [ ] **Step 5: Observer Pattern (Model)**
  - *Location*: `Game.java`
  - *Status*: **WAITING** for Partner to add `PropertyChangeSupport` and broadcasting logic.
- [ ] **Step 4: Virtual View & Controller Link**
  - *Location*: `ServerMain.java`
  - *Status*: **WAITING** for `GameController` to accept `GameView` in its constructor.
- [ ] **Step 3: Specific Action Serialization**
  - *Location*: `ClientMain.java`
  - *Status*: **WAITING** for game rules to define specific Actions (Pick, Move, etc.).

### 🔵 Awaiting GUI Implementation
- [ ] **Step 2: UI Synchronization**
  - *Location*: `ClientThread.java`
  - *Status*: **WAITING** for JavaFX integration to implement `Platform.runLater()`.

---

## 🚀 Future Network Tasks ("We Could Now")
Independent features to further improve the network layer.

- [ ] **Lobby Broadcasts**
  - Notify all connected players whenever a new player joins or leaves.
- [ ] **Player Count Setup**
  - Implement logic where the 1st player to connect chooses the game size (2-5 players).
- [ ] **Server Persistence Placeholder**
  - Add a class to save/load the Lobby state to a file (Advanced Requirement).

---

## 🧪 Testing Tasks (ActionMessage Verification)
Tasks to ensure our JSON "language" is robust.

- [ ] **Serialization Loop Test**
  - Verify: `Object -> JSON -> Object` results in identical data.
- [ ] **Validation Error Test**
  - Verify: Server correctly handles malformed JSON without crashing.
- [ ] **Null Field Test**
  - Verify: Jackson handles missing fields in `ActionMessage` gracefully.
