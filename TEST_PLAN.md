# ProjectPilot Quality Audit – Test Plan & Manual Verification Guide

## Quick Verification Checklist

### 1. Tombstone Guard (Snapshot Resurrection Prevention)
**Location**: `RemoteStore.java` (lines ~130–160)  
**What to test**: When a project/task is deleted locally on a client, an incoming snapshot from the LAN host should NOT resurrect it within 60 seconds.

**Manual Test Steps**:
1. Start ProjectPilot in LAN client mode (connected to a host).
2. View a project (e.g., "Demo Project").
3. Delete the project locally (right-click → Delete).
4. Verify it's gone from the Projects list.
5. Quickly navigate to the LAN host and restore/re-add the project.
6. Return to the client.
7. **Expected**: The project list on the client should NOT show the deleted project for ~60s (tombstone TTL).
8. Wait ~60s and refresh (or trigger a sync).
9. **Expected**: After TTL expires, the project MAY re-appear if still in host state.

**Automated Test**: `RemoteStoreSnapshotRaceTest.java` (in `src/test/java/com/projectpilot/data/`)

---

### 2. UI Selection Clearing (Stale Reference Prevention)
**Location**: `Main.java` (lines ~165–195 and `AppState.java`)  
**What to test**: When a selected project or task is removed, the UI selection should be cleared to avoid crashes.

**Manual Test Steps**:
1. Start ProjectPilot (host or client mode).
2. In the Projects view, select a project (click it to highlight).
3. Open another application or window (minimizing/switching away).
4. Delete the selected project from another client or admin action.
5. Return to the first window.
6. **Expected**: The selection should be cleared; no NullPointerException or stale reference errors in logs.
7. Try the same with Tasks: select a task, delete it in another session, verify it's cleared.

**Automated Test**: `AppStateSelectionClearingTest.java` (in `src/test/java/com/projectpilot/core/`)

---

### 3. Logging & Exception Visibility
**What to test**: All exceptions are now logged via `AppLog.warn()` instead of silently ignored.

**Manual Test Steps**:
1. Open the Admin page (if admin user).
2. Run the "Validate Data" button to see diagnostics.
3. Check the console or application log for structured warnings (e.g., `[db]`, `[lan]`, `[ui]` tags).
4. Trigger an error scenario (e.g., intentional DB corruption or network drop).
5. **Expected**: Error messages should appear in logs with context tags.

**What was fixed**:
- All `catch (Exception ignored) {}` replaced with `catch (Exception e) { AppLog.warn(...); }`
- System.out/err prints replaced with structured logging.
- ~25+ silent catch sites converted to logged warnings.

---

### 4. Data Validator (Admin Page)
**Location**: `AdminPage.java`, `DbAdminService.java`, `LanAdminClient.java`  
**What to test**: Admin can run a data validation check to detect referential integrity issues.

**Manual Test Steps**:
1. Log in as admin user.
2. Navigate to Admin page.
3. Click "Validate Data" button.
4. **Expected**: A report appears showing:
   - ✅ Passed checks (if no issues)
   - ❌ Failed checks (if any referential integrity, orphan task, or missing FK errors)
5. Example failures to look for:
   - Tasks with non-existent project IDs.
   - Members not in project's member list but assigned to tasks.
   - Chat messages referencing deleted users.

**Automated Check**: `DataValidator.java` (read-only SQL queries in DB transaction)

---

## Regression Testing

### 5. Core Functionality Not Broken
Run these quick sanity checks to ensure no new crashes:

1. **Login**: Can you log in with existing admin/user accounts?
2. **Project CRUD**: Create, rename, delete a project. Verify it syncs across LAN clients.
3. **Task CRUD**: Create, assign, mark done/undone a task.
4. **Chat**: Send a message; verify it's delivered and persisted.
5. **Notifications**: Check that task due-date notifications still appear.
6. **Settings**: Change theme, density, user profile — verify persistence.

---

## Known Limitations & Next Steps

### Short-term Mitigations (Already Implemented)
- ✅ Tombstone TTL in `RemoteStore` (60s) prevents immediate resurrection.
- ✅ UI selection-clearing guards in `Main.java` and `AppState`.
- ✅ Comprehensive logging instead of silent catches.
- ✅ Admin validator for data integrity checks.

### Long-term Improvements (Not Yet Implemented)
- [ ] Deduplicate incoming LAN sync packets (add sequence numbers + dedup window).
- [ ] Implement conflict resolution rules (last-write-wins vs. application logic).
- [ ] Enhance LAN protocol versioning for safe forward/backward compatibility.
- [ ] Add persistent WAL (write-ahead logging) for uncommitted deletes on clients.
- [ ] Formal consistency proof for multi-client sync scenarios.

---

## Running the Tests

### Prerequisites
- Java 11+ and Maven installed.
- JUnit 5 dependencies already in `pom.xml` (if not, add: `junit-jupiter-api`, `junit-jupiter-engine`).

### Run All Tests
```bash
cd c:\Users\Admin\OneDrive\Documents\GitHub\ProjectPilot
mvn clean test
```

### Run Specific Test Class
```bash
mvn test -Dtest=RemoteStoreSnapshotRaceTest
mvn test -Dtest=AppStateSelectionClearingTest
```

### Run Tests with Logging
```bash
mvn test -DfailIfNoTests=false -X
```

---

## Audit Summary

| Area | Status | Risk | Mitigations |
|------|--------|------|-------------|
| **Snapshot Resurrection** | Identified | High | Tombstone guard (60s TTL), logged warnings |
| **Stale UI Selection** | Identified | Medium | Selection-clearing listeners, AppState guards |
| **Silent Exception Catches** | Fixed | Medium | All 25+ catches now logged via AppLog.warn |
| **Data Integrity** | Monitored | Low | Admin validator, FK constraints in schema |
| **LAN Sync Dedupe** | Not Implemented | Low-Medium | Use sequence numbers (future enhancement) |
| **Protocol Versioning** | Not Implemented | Low | Document breaking changes; implement versioning (future) |

---

## Questions?
Refer to:
- [docs/INVARIANTS.md](../docs/INVARIANTS.md) — Data consistency invariants.
- [docs/DELETION_AUDIT.md](../docs/DELETION_AUDIT.md) — Detailed deletion correctness audit.
- [src/main/java/com/projectpilot/util/AppLog.java](../src/main/java/com/projectpilot/util/AppLog.java) — Logging implementation.
