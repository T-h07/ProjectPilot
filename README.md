# ProjectPilot

**ProjectPilot** is a **JavaFX desktop project-management app** focused on clean architecture, real persistence, and a “live” admin/dashboard experience.  
It’s built as a portfolio-grade project to practice desktop UI engineering, SQLite persistence, schema migrations, and role-based administration — with a LAN mode for syncing clients in the same network.

> **Tagline:** Plan. Track. Deliver.

---

## ✨ What it does

ProjectPilot helps teams manage projects with:

- **Projects, tasks, phases, milestones**
- **Gantt + Calendar** views for scheduling and timelines
- **Activity feed** (audit-style history of what changed)
- **Team management** (project members + roles)
- **Files & links** per task (workspace-like organization)
- **Admin tools** (user directory + integrity checks + exports)

---

## 🧠 Key features (implemented)

### ✅ SQLite persistence (write-through)
- Loads your workspace at startup
- Saves changes **live** as you edit (write-through pattern)
- Persistence is wired to UI changes via listeners so state stays consistent

### ✅ Schema migrations (versioned + idempotent)
- Uses **SQL migration files** with a schema version (`PRAGMA user_version`)
- Migrations are designed to be **safe to re-run** (idempotent)
- Supports incremental upgrades (e.g., v1 → v2 → v3)

### ✅ Authentication + roles (with safeguards)
- Login users stored in DB (auth module)
- **Global roles** (e.g., Admin vs User)
- Admin safeguards like preventing removal of the **last active admin**
- Project roles (Leader / Member, etc.) tied to membership

### ✅ Admin experience that feels “alive”
- User management (create/edit/deactivate)
- Right-side **user detail drawer**
- Quick actions like exports / resets / integrity scans
- “Alerts” style indicators (duplicates/orphans/unassigned/overdue spikes)

### ✅ Dashboard + metrics
- KPI tiles (overdue / in progress / blocked / workload)
- Mini trend charts (visual quick-read health signals)
- “At Risk” and “Recent Activity” sections to guide next actions

### ✅ LAN mode (host + clients)
- Host a workspace on the local network
- Clients can connect via **HTTP** (optional HTTPS depending on setup)
- Uses **tokens** for access and **WebSocket refresh** for near-real-time UI updates

---

## 🧱 Architecture overview

The goal is maintainability and separation of concerns:

- **UI layer (JavaFX):** pages, panels, dialogs, bindings  
- **Core/services:** app logic, orchestration, validation  
- **Data layer:** Store abstraction + SQLite store implementation  
- **Auth module:** user accounts, password hashing, sessions  
- **Migrations:** versioned SQL scripts applied on startup  
- **Activity log:** append-only change history that updates the UI

This approach keeps UI reactive while the underlying data remains consistent and durable.

---

## 🧪 Data integrity principles

ProjectPilot is built with “don’t lose data” as a priority:

- Write-through persistence with change listeners
- Defensive checks in admin flows (especially user management)
- Migration safety (versioning + idempotent scripts)
- Integrity signals surfaced in the Admin page (alerts + scans)

---

## 🚀 Running the app

### Prerequisites
- **JDK 17+** (recommended)
- Build tool: **Maven** or **Gradle** (depending on your setup)
- SQLite included via dependency (no server required)

### Run (typical)
- Import into IntelliJ / VS Code
- Run the JavaFX application entry point  
- The database file will be created/loaded automatically based on your configuration

> If you want, paste your `pom.xml`/Gradle snippet here and I’ll tailor the exact run steps (including JavaFX plugin commands).

---

## 🔐 Notes on security (current scope)
This is a desktop/LAN portfolio project:
- Passwords are hashed (not stored in plaintext)
- LAN mode uses tokens; HTTPS is optional depending on environment
- Hardening (TLS setup, advanced threat model) is out of scope for the current milestone, but the structure supports it

---

## 🛣️ Roadmap (next)
- Richer dashboard metrics (more trends, deeper drill-down)
- Smooth LAN workflow (pairing UX, reconnect handling, conflict strategy)
- More exports (PDF/CSV), reporting, and data audit views
- UI polish passes (animations, micro-interactions, empty-states)

---

## 🤝 Feedback
If you’re into **JavaFX**, **SQLite**, **desktop architecture**, or **LAN syncing**, I’d love feedback — or a quick roast 😄  
Open an issue or message me on LinkedIn.

---

## 🔎 Tech stack
- Java
- JavaFX
- SQLite (JDBC)
- (Optional) HTTP server + WebSockets for LAN mode

---

## 📄 License
Copyright (c) 2026 Taulant Haxhiu
