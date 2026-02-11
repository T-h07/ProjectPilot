ProjectPilot Data Invariants

These invariants are used as pass/fail criteria for audits and validators.

1. Referential integrity
- Every `task.project_id`, `phase.project_id`, `milestone.project_id`, `resource.project_id`, and `note.project_id` must reference an existing `projects.id`.
- Every `task.phase_id` if non-null must reference an existing `phases.id` that belongs to the same project.
- Every `task.assignee_member_id` if non-null must reference an existing `members.id` that is a member of the same project.
- Every row in `project_members` must reference an existing `projects.id` and `members.id`.

2. Domain constraints
- `Project.status` is one of the known enum values (ACTIVE, DONE, etc.).
- `Task.status`, `Task.priority`, `Resource.type`, and member/project role fields must be valid enum values.

3. UI model invariants
- In-memory `Project` collections (tasks, phases, members, milestones, resources, notes) must not contain duplicate IDs.
- `Task.assignee` (model object) is either `null` or a `Member` present in the owning project's `members` list.
- `Task.phase` (model object) is either `null` or a `Phase` present in the owning project's `phases` list.
- UI selections (`selectedProject`, `selectedTask`, etc.) must not reference items that have been removed from their backing lists.

4. Persistence ↔ in-memory sync
- `DbStore` write-through listeners must persist all adds/updates/deletes for projects and project-level entities.
- SQL schema foreign keys and `ON DELETE` policies are relied upon for cascade/cleanup; validator should report violations.

5. LAN sync
- Snapshots are authoritative but local deletes must not be resurrected by an in-flight snapshot; short-lived tombstones/local guards are acceptable.
- Actions (add/update/delete) should be idempotent when possible; validator can flag repeated conflicting history but won't attempt conflict resolution.

6. Activity log
- `activity_log.project_id` may be null; if present it must reference an existing project.
- `activity_log.entity_id` should reference an existing entity when possible (optional check).

Notes
- These invariants are intentionally conservative to avoid false positives in mixed-version deployments.
- The validator should provide issue severity (warn vs error) when extended.
