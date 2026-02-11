Deletion Correctness Audit — Summary

Scope
- Focus: deletion flows and stale-reference risks for Projects, Tasks, Members, Phases, Milestones, Resources, Notes.
- Source locations discovered via code scan; core delete logic lives in `InMemoryStore`, `DbStore`, LAN server (`LanServer`), and sync code (`RemoteStore`, `LanStoreBroadcaster`).

Findings

1) Delete entry points
- UI calls `store.deleteProject(p)` in `ProjectsPage.java`, `HistoryPage.java`.
- `InMemoryStore.deleteProject` removes project from in-memory lists.
- `DbStore.deleteProject` calls `super.deleteProject(...)`, then persists with `deleteProjectById` and detaches listeners.
- `DbStore` listeners remove project-level items and call delete methods: `deleteTaskById`, `deletePhaseById`, `deleteMilestoneById`, `deleteResourceById`, `deleteNoteById`.
- LAN: `LanServer` handles `PROJECT_DELETE`, `MEMBER_REMOVE` actions and calls `store.deleteProject` / `store.removeMember`.
- Broadcaster/Remote: `LanStoreBroadcaster` and `RemoteStore` listen to list changes and broadcast actions; `RemoteStore` now records tombstones (patched).

2) Stale-reference risks observed
- Snapshot resurrection: authoritative snapshot application (`RemoteStore.applySnapshot`) can reintroduce recently-deleted entities if host snapshot contains them while client delete hasn't propagated — mitigated by tombstones (60s TTL) added to `RemoteStore`.
- UI selection retention: selected UI objects (e.g., `selectedTask`) may remain after deletion; `AppState` now clears `selectedTask` when its containing task list removes it (patched), but other selection properties may need similar guards.
- Member removal side-effects: `removeMember` unassigns tasks via `nullAssigneeForMember` in DB; in-memory `InMemoryStore.removeMember` also clears assignees — watch for ordering races between DB write and broadcasts.
- Listener detach: `DbStore` detaches listeners on project delete; many UI pages also unhook on `wasRemoved` events, but ensure all components using direct references unregister to avoid NPEs.
- Silently swallowed exceptions in UI rebinding can hide failures where listeners are not reattached; logging should replace `ignored` catches in key spots.

3) Files of interest (non-exhaustive)
- `src/main/java/com/projectpilot/data/InMemoryStore.java` — base delete/add/remove semantics
- `src/main/java/com/projectpilot/data/db/DbStore.java` — write-through persistence and deleteById implementations
- `src/main/java/com/projectpilot/lan/LanServer.java` — server-side delete handlers
- `src/main/java/com/projectpilot/lan/RemoteStore.java` — snapshot application and tombstone guard (patched)
- `src/main/java/com/projectpilot/core/AppState.java` — selection safety (patched)
- UI pages that handle removals: `ProjectsPage.java`, `HistoryPage.java`, `ProjectOverviewPage.java`, `TasksPage.java`, `TeamPage.java`, `GanttPage.java`, and UI components like `ProjectPicker.java`.

Recommendations (short-term, minimal-risk)
- Keep the `RemoteStore` tombstone TTL approach for now; add tests that simulate host snapshot arriving after client delete.
- Sweep UI pages and key state holders for selection guards similar to the `selectedTask` fix; add listeners that clear selections when the backing list removes the selected item.
- Replace silent `catch (Exception ignored)` with `AppLog.warn("component", "...", e)` where safe to surface issues.
- Add unit/integration tests covering: local delete + host snapshot race; removing a member with assigned tasks; project delete cascades and UI cleanup.
- Extend validator to include resources/notes/chat references and to report severity.

Next steps I can take now
- Add selection-clearing listeners for other common selections (`selectedProject`, `selectedMilestone`, etc.) where appropriate.
- Add targeted tests for the tombstone case in `RemoteStore`.
- Replace a set of highest-impact silent catches with `AppLog.warn`.

If you want, I will start by adding selection-clearing guards for other AppState selections and adding a unit test skeleton for the `RemoteStore` tombstone behavior. Otherwise I can proceed to the logging changes.
