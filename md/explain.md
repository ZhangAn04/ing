# Mesos Project - Network Layer Report / Progetto Mesos - Relazione Livello di Rete / Mesos 项目 - 网络层报告

## 1. Overview of Implementation / Panoramica dell'implementazione / 实施概览

### **English**
I have implemented a **Distributed MVC Architecture** using **TCP Sockets**, following the "Esercitazione 2" guidelines. The network layer is completely decoupled from the game logic through the **Remote View** pattern.
*   **Infrastructure**: A multi-threaded server using the "Thread-per-client" model.
*   **Communication**: A bi-directional JSON protocol powered by the **Jackson** library.
*   **Handshake**: A stateful login process ensuring nickname uniqueness via a central **Lobby**.
*   **Configuration**: A hybrid configuration system (CLI arguments > JSON file > Hardcoded defaults).

### **Italiano**
Ho implementato un'architettura **MVC Distribuita** utilizzando **Socket TCP**, seguendo le linee guida dell'Esercitazione 2. Il livello di rete è completamente disaccoppiato dalla logica di gioco attraverso il pattern **Remote View**.
*   **Infrastruttura**: Un server multi-thread che utilizza il modello "Thread-per-client".
*   **Comunicazione**: Un protocollo JSON bidirezionale gestito dalla libreria **Jackson**.
*   **Handshake**: Un processo di login con gestione dello stato che garantisce l'univocità dei nickname tramite una **Lobby** centrale.
*   **Configurazione**: Un sistema di configurazione ibrido (Argomenti CLI > File JSON > Default predefiniti).

### **Chinese**
我根据“Esercitazione 2”的指南，使用 **TCP Socket** 实现了一个**分布式 MVC 架构**。通过 **Remote View（远程视图）** 模式，网络层与游戏逻辑完全解耦。
*   **基础设施**：采用“每个客户端一个线程”模型的线程并发服务器。
*   **通信协议**：基于 **Jackson** 库实现的双向 JSON 协议。
*   **握手机制**：带状态的登录流程，通过中央 **Lobby（大厅）** 确保玩家昵称的唯一性。
*   **配置系统**：混合配置系统（命令行参数 > JSON 配置文件 > 硬编码默认值）。

---

## 2. Technical Details / Dettagli Tecnici / 技术细节

### **English**
*   **ServerMain & ClientHandler**: The server listens on a configurable port. When a client connects, a `ClientHandler` is spawned in a new thread. This ensures the server can handle 2-5 players simultaneously without blocking.
*   **ClientThread**: The client uses a background thread to listen for server updates. This prevents the UI from freezing while waiting for user input (`readLine()` logic).
*   **VirtualView**: This class implements the `GameView` interface. To the Controller, it looks like a local view, but internally it serializes updates into **single-line JSON** strings to be sent over the socket.
*   **Thread Safety**: The `Lobby` uses `synchronized` methods and `CopyOnWriteArrayList` to prevent race conditions when multiple threads register players at the same time.

### **Italiano**
*   **ServerMain e ClientHandler**: Il server ascolta su una porta configurabile. Quando un client si connette, viene creato un `ClientHandler` in un nuovo thread. Questo assicura che il server possa gestire 2-5 giocatori simultaneamente senza blocchi.
*   **ClientThread**: Il client utilizza un thread in background per ascoltare gli aggiornamenti del server. Ciò impedisce il congelamento dell'interfaccia mentre si attende l'input dell'utente.
*   **VirtualView**: Questa classe implementa l'interfaccia `GameView`. Per il Controller appare come una vista locale, ma internamente serializza gli aggiornamenti in stringhe **JSON su singola riga** inviate via socket.
*   **Sicurezza dei Thread**: La `Lobby` utilizza metodi `synchronized` e `CopyOnWriteArrayList` per prevenire "race conditions" quando più thread registrano giocatori contemporaneamente.

### **Chinese**
*   **ServerMain 与 ClientHandler**：服务器在可配置的端口上监听。当客户端连接时，会在新线程中生成一个 `ClientHandler`。这确保了服务器可以同时处理 2-5 名玩家而不会产生阻塞。
*   **ClientThread**：客户端使用后台线程监听服务器更新。这防止了在等待用户输入时界面卡死（即解决了 `readLine()` 的阻塞问题）。
*   **VirtualView**：该类实现了 `GameView` 接口。对于控制器（Controller）来说，它看起来像是一个本地视图，但在内部，它将更新序列化为**单行 JSON** 字符串并通过 Socket 发送。
*   **线程安全**：`Lobby` 类使用 `synchronized` 同步方法和 `CopyOnWriteArrayList` 来防止多个线程同时注册玩家时产生的竞态条件（Race Condition）。

---

## 3. Potential Teacher Questions / Possibili domande del docente / 老师可能提出的问题

**Q1: Why did you choose multi-threading instead of non-blocking I/O (NIO)?**
*   **Answer**: Multi-threading (Thread-per-client) is the model suggested in the Esercitazione slides for this complexity level. It is easier to implement and sufficient for a board game with 5 players.

**Q2: Why is the "single-line JSON" rule important?**
*   **Answer**: Because we use `BufferedReader.readLine()`. This method identifies the end of a message only by the `\n` character. Multi-line JSON would cause the parser to fail after the first line.

**Q3: How is the Model (Game) protected from the Network?**
*   **Answer**: Through the **VirtualView**. The Model only fires events to its listeners. It has no reference to Sockets or IP addresses, maintaining perfect encapsulation.

---

## 4. Questions for the Teacher / Domande da porre al docente / 可以问老师的问题

1. "Is `CopyOnWriteArrayList` inside the Lobby considered sufficient for thread-safety, or should I explicitly synchronize the entire broadcast loop to ensure message order?"
2. "For the Advanced Requirement of 'Persistence', should the JSON state be saved by the Lobby or by the Game Model itself?"
3. "What is the recommended heartbeat frequency to detect silent disconnections before the TCP timeout occurs?"

---

## 5. Socket vs. RMI / Socket vs. RMI / Socket 与 RMI 的区别

### **English**
The project implements two different transport layers to satisfy different requirements (Esercitazione 2 for Sockets, Final Project for RMI).
*   **Socket**: A low-level approach where we manage raw streams and manually serialize/deserialize JSON using Jackson. It requires a persistent background thread (`ClientThread`) to listen for updates.
*   **RMI**: A high-level approach where the server exports a `Remote` interface (`GameServiceRemote`). Communication happens through remote method calls instead of JSON strings. It uses a **Callback Pattern** to push updates to the client.

### **Italiano**
Il progetto implementa due diversi livelli di trasporto per soddisfare requisiti differenti (Esercitazione 2 per i Socket, Progetto Finale per RMI).
*   **Socket**: Un approccio di basso livello in cui gestiamo flussi grezzi e serializziamo/deserializziamo manualmente JSON tramite Jackson. Richiede un thread in background persistente (`ClientThread`) per ascoltare gli aggiornamenti.
*   **RMI**: Un approccio di alto livello in cui il server esporta un'interfaccia `Remote` (`GameServiceRemote`). La comunicazione avviene tramite chiamate a metodi remoti invece che stringhe JSON. Utilizza un **Callback Pattern** per inviare aggiornamenti al client.

### **Chinese**
该项目实现了两种不同的传输层，以满足不同的需求（Esercitazione 2 使用 Socket，最终项目使用 RMI）。
*   **Socket**：一种底层方法，我们需要管理原始流，并使用 Jackson 手动进行 JSON 的序列化和反序列化。它需要一个持久的后台线程 (`ClientThread`) 来监听更新。
*   **RMI**：一种高层方法，服务器导出 `Remote` 接口 (`GameServiceRemote`)。通信通过远程方法调用而不是 JSON 字符串进行。它使用**回调模式 (Callback Pattern)** 向客户端推送更新。

