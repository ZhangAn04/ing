# Educational Session 2: Advanced Distributed Patterns (Mesos Project)

This document tracks the technical deep-dives and architectural lessons learned during the second phase of development. Each entry focuses on transforming raw networking into a professional, decoupled game system.

---

## Lesson 1: JSON Serialization for Distributed Logic

### 1. The Concept: "Language of the Pipe"
**Problem**: TCP Sockets only understand a stream of characters (Strings). In a board game like Mesos, we need to send complex data like "Player 'Marco' picked the 3rd card from the upper row". 
**Manual Solution (Bad)**: Sending a string like `"PICK|MARCO|3"`. This is hard to parse, fragile, and prone to errors if a player's name contains a `|`.
**Architectural Solution**: **Serialization**. We take a Java Object (with fields, lists, and numbers) and "flatten" it into a standard format that any system can understand.

### 2. The Choice: Jackson vs. Gson
*   **Gson (Google)**: Very simple, used in the Esercitazione slides.
*   **Jackson**: Highly performant, industry standard, and already included in your project's `pom.xml`.
**Decision**: Use Jackson to keep dependencies lean.

### 3. The Implementation (Code)
To send a message, we perform three steps:
1.  **Define a POJO** (Plain Old Java Object): A class with getters/setters that represents the message.
2.  **Serialize**: Convert the Object to a JSON String using `mapper.writeValueAsString(obj)`.
3.  **Deserialize**: On the server, convert the JSON back to a Java Object using `mapper.readValue(json, Class.class)`.

### 4. The "One-Line JSON" Golden Rule (Slide 29)
**Crucial Lesson**: Most network implementations use `BufferedReader.readLine()` which waits for a `\n` character. 
*   **If you send**: `{"type": "MOVE"}` (Single line) -> The server receives it instantly.
*   **If you send "Pretty Print"**: The server's `readLine()` will only receive `{` and then stop, waiting for a newline that never comes. This causes the parser to crash or the connection to hang.

---

## Lesson 2: UI Synchronization (The Threading Conflict)

### 1. The Concept: "The UI is a Jealous Goddess"
**Problem**: Most graphical libraries (JavaFX, Swing, Android) are **Single-Threaded**. Only the "Main UI Thread" is allowed to change what the user sees. In our project, the `ClientThread` is a **Background Thread**. If it receives a server update and tries to update a button or label directly, the application will crash.

### 2. The Solution: Task Queuing
Instead of updating the UI directly, the background thread must "ask permission" to run code on the main thread. 

### 3. The Implementation (`Platform.runLater`)
In JavaFX, we use the `Platform.runLater(Runnable)` method. This puts your update task into a queue that the Main UI Thread checks hundreds of times per second.

---

## Lesson 3: Deserialization (The Server's Intelligence)

### 1. The Concept: "Turning Data back into Meaning"
**Problem**: The Server receives a String from the network. A String is just a sequence of characters. The server cannot call `message.getType()` on a String.

### 2. The Solution: Deserialization
Deserialization is the reverse of Serialization. We take the "flat" JSON string and reconstruct the original Java Object in the Server's memory.

---

## Lesson 4: Dynamic Configuration (Combining Approach)

### 1. The Concept: "Soft-Coding" vs. "Hard-Coding"
**Problem (Slide 10)**: Writing `static int port = 1234;` inside your code is a "Hard-coded" value. If port 1234 is occupied, your software fails. Professional software must be configurable.

### 2. The Solution: Multi-Source Configuration
Instead of a fixed value, the software should look for configuration in this order of priority:
1.  **Command Line Arguments**: Overrides everything (for power users).
2.  **Config Files (JSON)**: Used for persistent user settings.
3.  **Default Values**: Used if nothing else is found.

---

## Lesson 5: The Virtual View Pattern

### 1. The Concept: "The Avatar of the Client"
**Problem (Slide 40)**: In a distributed game, the `GameController` needs to update the player's screen. But the player's screen is on a different computer! We cannot pass a `Socket` to the `GameController` because that would mix "Game Logic" with "Networking Code".

### 2. The Solution: The Virtual View
We create a class called `VirtualView` that lives on the **Server**.
*   To the **Controller**, the `VirtualView` looks like a normal UI (it implements the `GameView` interface).
*   To the **Network**, the `VirtualView` looks like a messenger (it holds the `Socket`).

---

## Lesson 6: Handshaking & Protocols

### 1. The Concept: "The First Handshake"
**Problem**: When a socket connects, the server knows NOTHING about who is there. It doesn't know the player's name or if they are the first or fifth person to join.

### 2. The Solution: The Handshake Protocol
Before the game starts, the client and server must perform a sequence of "Setup" messages.
1.  **Connection**: Socket opened.
2.  **Challenge**: Server sends `{"type": "LOGIN_REQUEST"}`.
3.  **Response**: Client sends `{"type": "LOGIN_ACTION", "nickname": "Marco"}`.
4.  **Verification**: Server checks if "Marco" is taken. If not, sends `{"type": "LOGIN_SUCCESS"}`.

---

## Lesson 7: Full Duplex JSON Communication

### 1. The Concept: "The Complete Loop"
**Problem**: We had a "one-way" smart connection (Client sending objects). But if the Server replied with raw text, the Client had to use fragile logic to understand the server.

### 2. The Solution: Bi-Directional Serialization
We now use structured objects for **both** directions:
*   **`ActionMessage`**: Client -> Server (Intent)
*   **`SerializedUpdate`**: Server -> Client (State/Feedback)

---

## Lesson 8: Handoff Compliance Check (Final Phase)

### 1. What has been implemented (The "Pipe")
The Networking layer is now complete. It provides:
*   **A Multi-threaded Tunnel**: Multiple players can connect to `ServerMain`.
*   **A Bi-directional Translator**: JSON objects (`ActionMessage` and `SerializedUpdate`) move across the pipe.
*   **A Virtual Avatar**: The `VirtualView` class is ready to act as the "Remote Eye" for each player.

---

## Lesson 9: Automated Testing with JUnit 5

### 1. The Concept: "Dynamic Verification"
**Problem (Slide 22)**: Manual testing is slow and doesn't find all bugs. Every time you change the code, you might break something else.
**The Solution**: Unit Testing. Small, fast, automated scripts that verify every single piece of logic in isolation.

---

## Lesson 10: Robust Protocol Testing

### 1. The Concept: "The Contract Verification"
**Problem**: In distributed systems, the Client and Server live in different "worlds." If the Client changes the name of a field but the Server doesn't, the game breaks silently.

### 2. The Solution: The Round-Trip Test
We write a test that simulates the entire journey of a message from Creation to Verification.

---

## Lesson 11: Testing Concurrent Clients

### 1. The Problem: "The Development Solo Trap"
**Problem**: Developers usually test by running one Server and one Client. This fails to detect race conditions or multi-user bugs.

### 2. The Solution: Multiple Instance Simulation
In IntelliJ, we enable "Allow multiple instances" in the Run Configuration. This allows us to run 3+ clients on the same machine to verify the Lobby logic and broadcasting.

---

## Lesson 12: Dynamic Identity & Handshake Sequence

### 1. The Concept: "Who am I?"
**Problem**: If the nickname is hardcoded (e.g., `nickname = "Player1"`), the server will reject every connection after the first one because the name is already taken. 

### 2. The Solution: Bootstrapping State
The Client must capture the user's identity **before** entering the main game loop.
1.  **Identity Capture**: Read the first line of terminal input as the `nickname`.
2.  **State Initialization**: Store that name in a variable.
3.  **Inheritance**: Use that variable for every subsequent `ActionMessage` sent to the server.

### 3. Implementation Logic
We split the `ClientMain` into two phases:
*   **Phase A**: Handshake (Read name -> Send Login JSON).
*   **Phase B**: Interaction (Read command -> Send Action JSON using the name from Phase A).

---

## Lesson 13: Socket vs. RMI (Abstraction Levels)

### 1. The Concept: "High-Level vs. Low-Level"
**The Problem**: Writing raw Socket code is powerful but repetitive. You have to handle byte streams, JSON serialization, and manual threading. 
**The Solution**: **RMI (Remote Method Invocation)**. RMI allows you to treat a remote computer as if it were a local Java object.

### 2. Key Differences in Mesos
| Feature | Socket (Esercitazione 2) | RMI (Final Project) |
| :--- | :--- | :--- |
| **Data Format** | JSON (via Jackson) | Java Serialization (Native) |
| **Call Type** | Message-based (Async) | Method-based (RPC) |
| **Discovery** | IP + Port | RMI Registry (Binding Name) |
| **Updates** | Push (via TCP Pipe) | Callback (via Remote Interface) |

### 3. The Callback Pattern
In RMI, since there is no permanent "stream" like in Sockets, the Server needs a way to call the Client back.
1.  **Client exports** a `Remote` object (the Callback).
2.  **Client passes** this object to the Server during login.
3.  **Server stores** the callback and calls `callback.onUpdate(state)` whenever the model changes.
