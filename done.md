# Project Mesos - Implementation Progress (Done)

## ✅ Core Game Logic & Rules (Completed 2026-05-14)
Fully implemented all missing mechanics and aligned the Java model with the official `rule.md`.

### Characters & Abilities
- **Hunter (Cacciatori)**: Added immediate food reward logic when acquiring a hunter with a meat icon.
- **Gatherer (Raccoglitori)**: Integrated food discounts (3 per gatherer) into the Sustenance event resolution.

### Event Resolution Overhaul
- **Sustenance (Sostentamento)**: 
    - Implemented correct shortage calculation (Total Required - Discounts).
    - Added "Pay all available food" logic.
    - Implemented per-character penalty for unfed members.
- **Rock Paintings (Pitture Rupestri)**:
    - Updated `CardLoader` to parse `thresholdLow` and `ppIfNotMet`.
    - Implemented per-artist scoring (High Threshold) and fixed penalty logic (Low Threshold).
- **Shamanic Ritual (Rituale Sciamanico)**:
    - Fixed tie-breaking logic to allow simultaneous reward/penalty in extreme ties.
    - Integrated Ritual Building bonuses directly into the resolution flow.

### Specialized Building Integration
- **InGame_Effect**: 
    - Added automated checking when cards/buildings are acquired.
    - Implemented "Set of 6" and "Inventor Pairs" detection.
    - Added state tracking to ensure players only get food for *newly* completed sets.
- **Event_Add_Effect**: Hooked into the end-of-round event sequence to grant bonuses for Hunting/Rock Paintings/etc.
- **Ritual Protection/Mastery**: Added logic for `Event_Less_Ritual` (penalty protection) and `Event_Most_Ritual` (multiplier bonus).
- **Turn Order & Extra Actions**:
    - `Turn_Start_Effect`: Linked to Turn Order track to grant +1 extra food on bonus slots.
    - `Turn_End_Card`: Implemented "Extra Pick" logic by increasing `remainingUpperPicks` dynamically in the controller.
