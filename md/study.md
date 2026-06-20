# Educational Guide: Building Distributed Systems in Java

This guide explains the architectural transition from a local application to a multi-user distributed system. It uses the "Mesos Board Game" as a case study, but the principles (Sockets, Multi-threading, and the Remote View Pattern) are universal to any Java network application.

---

## 1. The Foundation: TCP Sockets
### The Problem
How do two programs on different computers talk to each other? They need a reliable, ordered "pipe" to exchange data.

### The Solution: TCP Sockets
Transmission Control Protocol (TCP) ensures that every packet sent arrives in the correct order and without errors. In Java, we use `ServerSocket` for the listener and `Socket` for the connector.

### Core Classes & Code
*   **`ServerSocket(port)`**: Opens a "door" on the server.
*   **`serverSocket.accept()`**: A **blocking** call. The server stops here and sleeps until a client "knocks" on the door.
*   **`Socket(ip, port)`**: The client's way of knocking on that door.

**Example Implementation (`ServerMain.java`):**
```java
ServerSocket serverSocket = new ServerSocket(1234);
Socket clientSocket = serverSocket.accept(); // Stops here until someone connects
```

---

## 2. Communication: Streams & Buffers
### The Problem
Once the "pipe" (Socket) is open, how do we send text? Binary data is hard to read manually.

### The Solution: I/O Wrappers
We wrap the raw Socket streams into high-level readers and writers.
*   **`PrintWriter(stream, true)`**: Allows us to send strings like `out.println("Hello")`. The `true` parameter is **Auto-flush**—it forces the data out immediately so it doesn't get stuck in a buffer.
*   **`BufferedReader`**: Efficiently reads text line-by-line.

### The Critical Newline Rule
`readLine()` only finishes when it sees a `\n` (newline). 
**Pitfall**: If the sender uses `print()` instead of `println()`, the receiver will hang forever waiting for the end of the line.

---

## 3. Scalability: Multi-threading
### The Problem (The Blocking Bug)
If the server is busy inside a `while` loop reading messages from **Player 1**, it cannot return to the `accept()` line to let **Player 2** join. The server is "blocked" by the first player.

### The Solution: One Thread per Client
Every time `accept()` returns a new socket, we "hand it off" to a separate worker thread.

**The Workflow (`ClientHandler.java`):**
1.  Server `accepts` connection.
2.  Server creates a new `ClientHandler` (which `implements Runnable`).
3.  Server starts a `new Thread(handler).start()`.
4.  The Main Server loop immediately goes back to `accept()` to wait for the next player.

**Why `Runnable`?**
Using the `Runnable` interface is better than extending `Thread` because it allows your class to inherit from other classes (like `VirtualView`) while still being executable in the background.

---

## 4. Asynchronous Updates: The Client Listening Thread
### The Problem
In a game, the server might send you an update (e.g., "Player 2 joined") at any time. However, if the client is waiting for **your** input via `Scanner.nextLine()`, the program is frozen. You won't see the server's message until **after** you type something.

### The Solution: Background Listening (`ClientThread.java`)
We split the client into two parts:
1.  **Main Thread**: Waits for user input and sends it to the server.
2.  **ClientThread**: A background loop that does nothing but listen to the server and print updates to the screen immediately.

**Code Logic:**
```java
public void run() {
    while (true) {
        String msg = in.readLine(); // Waits for server
        System.out.println(msg);    // Shows it immediately
    }
}
```

---

## 5. Architecture: The "Remote View" Pattern
### The Problem
How do we keep the Game Logic (`Model`) separate from the Network (`Socket`)? If we put `socket.println()` inside the `Game` class, we can no longer test the game without a network.

### The Solution: The Virtual View
We use the **Observer Pattern**.
1.  **The Game (Observable)**: Has no idea the network exists. It just says "Hey, the board changed!" using `PropertyChangeSupport`.
2.  **The Virtual View (Observer)**: Lives on the server. It "listens" to the Game. When the game changes, the Virtual View catches the event, turns it into JSON, and sends it over the socket.

**Benefits:**
*   **Decoupling**: You can swap the Network for a Local GUI easily.
*   **Security**: The client never sees the full `Game` object, only the filtered JSON updates the `VirtualView` sends.

---

## 6. Advanced Concept: Serialization (JSON)
### The Problem
We can't send a Java `Object` directly over a string-based socket.

### The Solution
We "Serialize" the object into a JSON string.
*   **Object -> JSON**: `{"action": "pick", "cardId": 5}`
*   **The Single-Line Requirement**: Because our server uses `readLine()`, we **must** serialize the JSON into a single line. "Pretty-printing" (multi-line) will crash the parser.

---

## 7. The Three Golden Rules of Network Programming
1.  **Thread Safety**: If two `ClientHandler` threads try to modify the `List<Player>` at the same time, the list will crash. Always use `synchronized` on methods that modify shared data.
2.  **GUI Thread Safety**: In JavaFX/Swing, you **cannot** update a Label from a background thread. You must use `Platform.runLater(() -> label.setText(msg))`.
3.  **Clean Teardown**: Always close sockets in a `finally` block. A "hanging" socket can prevent your OS from re-using that port for minutes.
