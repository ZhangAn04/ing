# Critical Implementation Notes (Esercitazione 2)

These are the key pitfalls and technical requirements identified during the "Minimal TCP" and "Remote View" lessons.

### 1. The "One-Line JSON" Rule
*   **Pitfall**: Using "Pretty Print" (multi-line) JSON serialization.
*   **Reason**: The server and client use `BufferedReader.readLine()` to receive messages. This method triggers only when it sees a newline character (`\n`). If a JSON spans multiple lines, the receiver will only get the first line (usually just `{`) and fail to parse it.
*   **Action**: Always configure your JSON library (Jackson/Gson) to serialize objects into a **single line**.

### 2. Thread Safety & Synchronization
*   **Pitfall**: Multiple `ClientHandler` threads modifying the `Game` state simultaneously.
*   **Reason**: In a multi-threaded server, two players might click "Pick Card" at the exact same time. Without synchronization, the internal state of the `Game` (e.g., lists of cards) can become corrupted (Race Condition).
*   **Action**: Use the `synchronized` keyword on methods in `Game.java` or `GameController.java` that modify shared data.

### 3. JavaFX and the "UI Thread"
*   **Pitfall**: Updating GUI elements (Labels, Buttons, etc.) directly from `ClientThread`.
*   **Reason**: JavaFX (and Swing) is not thread-safe. Only the "Main JavaFX Thread" can modify the UI. Since `ClientThread` is a background network thread, calling `label.setText()` from it will cause a crash or undefined behavior.
*   **Action**: Wrap all UI updates in `Platform.runLater(() -> { ... })`.

### 4. Loose Coupling (Observer Pattern)
*   **Pitfall**: Passing a `Socket` or `PrintWriter` directly into the `Game` class.
*   **Reason**: The core game logic should not know that a network exists. This makes the code hard to test and impossible to reuse for a local-only mode.
*   **Action**: Use `PropertyChangeSupport`. The `Game` fires events (e.g., "card_drawn"), and the `VirtualView` (which lives on the server) listens to these events and forwards them over the network.

### 5. Proper Resource Closing
*   **Pitfall**: Leaving Sockets or Streams open after a player disconnects.
*   **Reason**: This can lead to memory leaks and port-binding errors.
*   **Action**: Always use `try-with-resources` or a `finally` block to ensure `socket.close()`, `in.close()`, and `out.close()` are called.
