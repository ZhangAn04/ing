# Project Mesos - Context & Architecture Summary


## 🎯 Overall Goal
Complete the "Mesos" board game project for the 2025/2026 Software Engineering course, delivering a distributed (Client-Server) application with both a Textual User Interface (TUI) and a Graphical User Interface (GUI).
**Group:** cheng-wu-xu-zhang
**Deadline:** 2026-06-23 13:00 CEST
**Evaluation Starting Date:** 2026-06-24

---

## 🛠 Development Environment & Tools
*   **Language:** JavaSE 8
* **Documentation Standards:**
    * **Minimal Intervention:** When providing code, modify ONLY the necessary lines. Do not rewrite, refactor, or delete unrelated code blocks.
    * **JavaDoc:** Mandatory for all public classes, interfaces, and methods. Clearly describe parameters, return values, and exceptions.
*   **IDE:** IntelliJ IDEA
*   **Project Management & Build:** Maven
*   **Testing:** JUnit 5
*   **Version Control:** Git
*   **Communication Language:** English for Source code, JavaDoc, comments. UI can be English/Italian. When answering me you have to use the same language that i used to ask the question.

---

## 🏗 Architecture
The application follows a **Distributed MVC Pattern** implemented via **TCP Sockets** and **RMI**.

### Current Implementation Status
1.  **Multi-match Server**: The server can host multiple parallel matches using the `GameRoom` abstraction.
2.  **Lobby & Routing**: `Lobby` manages a `Map` of active rooms and routes players via `CREATE_ROOM`, `JOIN_ROOM`, and `RECONNECT_ROOM` commands.
3.  **Secure Reconnection**: Players use a secret **PIN** to claim their spot back if they disconnect during a match.
4.  **Dual Transport Layer**: Fully functional implementation of both **TCP Sockets** (JSON/Jackson) and **Java RMI**.
5.  **Multi-Screen JavaFX GUI**: The GUI now features a two-step flow (Connection Screen -> Gateway Menu) supporting all multi-match operations.
6.  **CLI Gateway Menu**: Both Socket and RMI terminal clients include an interactive menu for room management.

---

## 🚀 Latest Updates
*   **Partite Multiple Implemented**: Architecture shifted from single-match to multi-match via `GameRoom`.
*   **Protocol Upgrade**: `ActionMessage` and `RmiLoginRequest` now carry `roomID` and `pin`.
*   **GUI Overhaul**: `ClientGuiMain` completely re-architected into a Connection Scene and a Gateway Scene while maintaining custom visual style.
*   **Documentation Sync**: `explain.md`, `study2.md`, and `todo_es3.md` updated to cover advanced networking and multi-game logic.

---

## 🗺 Project Roadmap / Next Steps

1.  **In-Game GUI Robustness**:
    *   Enhance the Game Board UI to dynamically reflect Era changes and event resolutions.
2.  **Game Logic Deep-Dive**:
    *   Complete remaining building effects and specialized character abilities (Artist, Inventor).
3.  **Persistence (Advanced)**:
    *   Implement state saving to disk to allow server restarts without losing ongoing matches.
4.  **Error Handling Polish**:
    *   Fine-tune timeout notifications and automatic turn-skipping for disconnected players.
