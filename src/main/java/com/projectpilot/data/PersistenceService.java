package com.projectpilot.data;

import com.projectpilot.model.*;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.ProjectHealth;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.model.enums.TaskStatus;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class PersistenceService {

    private static final Path SAVE_PATH = Paths.get(
            System.getProperty("user.home"),
            ".projectpilot",
            "projectpilot.dat"
    );

    private static final Path ACTIVITY_PATH = Paths.get(
            System.getProperty("user.home"),
            ".projectpilot",
            "projectpilot-activity.dat"
    );

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // Separate activity snapshot so we don't break the main AppSnapshot format
    private record ActivitySnap(
            LocalDateTime at,
            String project,
            String detail
    ) implements Serializable {}

    private PersistenceService() {}

    public static void loadOrSeed(InMemoryStore store, Runnable seeder) {
        if (tryLoad(store)) return;

        store.getProjects().clear();
        store.getHistoryProjects().clear();
        store.getActivity().clear();

        seeder.run();
        safeSave(store);
    }

    public static void safeSave(InMemoryStore store) {
        try {
            save(store);
        } catch (Exception ex) {
            System.err.println("[Persistence] Save failed: " + ex.getMessage());
        }
    }

    public static boolean tryLoad(InMemoryStore store) {
        if (!Files.exists(SAVE_PATH)) return false;
        try {
            load(store);
            return true;
        } catch (Exception ex) {
            System.err.println("[Persistence] Load failed, will seed: " + ex.getMessage());
            return false;
        }
    }

    public static void save(InMemoryStore store) throws IOException {
        Files.createDirectories(SAVE_PATH.getParent());

        AppSnapshot snap = snapshot(store);

        Path tmp = SAVE_PATH.resolveSibling(SAVE_PATH.getFileName() + ".tmp");
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(tmp))) {
            oos.writeObject(snap);
        }
        Files.move(tmp, SAVE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        // ✅ Also persist recent activity (separate file)
        saveActivity(store);
    }

    public static void load(InMemoryStore store) throws IOException, ClassNotFoundException {
        AppSnapshot snap;
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(SAVE_PATH))) {
            snap = (AppSnapshot) ois.readObject();
        }

        store.getProjects().clear();
        store.getHistoryProjects().clear();

        if (snap.activeProjects() != null) {
            for (AppSnapshot.ProjectSnap ps : snap.activeProjects()) {
                Project p = rebuildProject(ps);
                if (p.getStatus() == null) p.setStatus(Project.ProjectStatus.ACTIVE);
                store.getProjects().add(p);
            }
        }

        if (snap.historyProjects() != null) {
            for (AppSnapshot.ProjectSnap ps : snap.historyProjects()) {
                Project p = rebuildProject(ps);
                p.setStatus(Project.ProjectStatus.DONE);
                if (p.getCompletedDate() == null) {
                    p.setCompletedDate(ps.completedDate() != null ? ps.completedDate() : LocalDate.now());
                }
                store.getHistoryProjects().add(p);
            }
        }

        // ✅ Load activity (non-critical)
        tryLoadActivity(store);
    }

    // -----------------------------
    // Snapshot building
    // -----------------------------

    private static AppSnapshot snapshot(InMemoryStore store) {
        List<AppSnapshot.ProjectSnap> active = snapshotProjects(store.getProjects());
        List<AppSnapshot.ProjectSnap> history = snapshotProjects(store.getHistoryProjects());
        return new AppSnapshot(active, history);
    }

    private static List<AppSnapshot.ProjectSnap> snapshotProjects(List<Project> projects) {
        List<AppSnapshot.ProjectSnap> snaps = new ArrayList<>(projects.size());
        for (Project p : projects) snaps.add(snapshotProject(p));
        return snaps;
    }

    private static AppSnapshot.ProjectSnap snapshotProject(Project p) {
        // Members
        List<AppSnapshot.MemberSnap> members = new ArrayList<>();
        for (Member m : p.getMembers()) {
            members.add(new AppSnapshot.MemberSnap(
                    safe(m.getName()),
                    m.getRole() == null ? ProjectRole.MEMBER.name() : m.getRole().name()
            ));
        }

        // Phases
        List<AppSnapshot.PhaseSnap> phases = new ArrayList<>();
        for (Phase ph : p.getPhases()) {
            phases.add(new AppSnapshot.PhaseSnap(extractPhaseName(ph)));
        }

        // Tasks (assignee + phase stored as indices)
        List<AppSnapshot.TaskSnap> tasks = new ArrayList<>();
        for (Task t : p.getTasks()) {
            int assigneeIndex = -1;
            if (t.getAssignee() != null) {
                assigneeIndex = p.getMembers().indexOf(t.getAssignee());
            }

            int phaseIndex = -1;
            if (t.getPhase() != null) {
                phaseIndex = p.getPhases().indexOf(t.getPhase());
            }

            tasks.add(new AppSnapshot.TaskSnap(
                    safe(t.getTitle()),
                    safe(t.getDescription()),
                    t.getStatus() == null ? TaskStatus.TODO.name() : t.getStatus().name(),
                    t.getPriority() == null ? Priority.MEDIUM.name() : t.getPriority().name(),
                    t.getDueDate(),
                    assigneeIndex,
                    phaseIndex
            ));
        }

        // Milestones (name is known; date is best-effort)
        List<AppSnapshot.MilestoneSnap> milestones = new ArrayList<>();
        for (Milestone ms : p.getMilestones()) {
            String name = "";
            try { name = ms.nameProperty().get(); } catch (Exception ignored) {}
            milestones.add(new AppSnapshot.MilestoneSnap(safe(name), extractMilestoneDate(ms)));
        }

        return new AppSnapshot.ProjectSnap(
                safe(p.getName()),
                safe(p.getDescription()),
                p.getStartDate(),
                p.getEndDate(),
                p.getHealth() == null ? ProjectHealth.ON_TRACK.name() : p.getHealth().name(),
                p.getStatus() == null ? Project.ProjectStatus.ACTIVE.name() : p.getStatus().name(),
                p.getCompletedDate(),
                members,
                phases,
                tasks,
                milestones
        );
    }

    // -----------------------------
    // Rebuild objects from snapshot
    // -----------------------------

    private static Project rebuildProject(AppSnapshot.ProjectSnap ps) {
        Project p = new Project(ps.name());

        p.setDescription(ps.description());
        p.setStartDate(ps.startDate());
        p.setEndDate(ps.endDate());

        // health
        if (ps.health() != null && !ps.health().isBlank()) {
            try { p.setHealth(ProjectHealth.valueOf(ps.health())); } catch (Exception ignored) {}
        }

        // status + completed date
        if (ps.status() != null && !ps.status().isBlank()) {
            try { p.setStatus(Project.ProjectStatus.valueOf(ps.status())); } catch (Exception ignored) {}
        }
        p.setCompletedDate(ps.completedDate());

        // phases first (so tasks can reference by index)
        if (ps.phases() != null) {
            for (AppSnapshot.PhaseSnap phs : ps.phases()) {
                Phase phase = createPhase(phs.name());
                if (phase != null) p.getPhases().add(phase);
            }
        }

        // members second (so tasks can reference by index)
        if (ps.members() != null) {
            for (AppSnapshot.MemberSnap ms : ps.members()) {
                ProjectRole role = ProjectRole.MEMBER;
                if (ms.role() != null && !ms.role().isBlank()) {
                    try { role = ProjectRole.valueOf(ms.role()); } catch (Exception ignored) {}
                }
                p.getMembers().add(new Member(ms.name(), role));
            }
        }

        // tasks last (resolve assignee + phase by index)
        if (ps.tasks() != null) {
            for (AppSnapshot.TaskSnap ts : ps.tasks()) {
                Task t = new Task(ts.title());
                t.setDescription(ts.description());

                try { t.setStatus(TaskStatus.valueOf(ts.status())); } catch (Exception ignored) {}
                try { t.setPriority(Priority.valueOf(ts.priority())); } catch (Exception ignored) {}

                t.setDueDate(ts.dueDate());

                // assignee
                if (ts.assigneeIndex() >= 0 && ts.assigneeIndex() < p.getMembers().size()) {
                    t.setAssignee(p.getMembers().get(ts.assigneeIndex()));
                } else {
                    t.setAssignee(null);
                }

                // phase
                if (ts.phaseIndex() >= 0 && ts.phaseIndex() < p.getPhases().size()) {
                    t.setPhase(p.getPhases().get(ts.phaseIndex()));
                } else {
                    t.setPhase(null);
                }

                p.getTasks().add(t);
            }
        }

        // milestones
        if (ps.milestones() != null) {
            for (AppSnapshot.MilestoneSnap ms : ps.milestones()) {
                Milestone m = createMilestone(ms.name(), ms.date());
                if (m != null) p.getMilestones().add(m);
            }
        }

        return p;
    }

    // -----------------------------
    // Activity persistence (FIXED)
    // -----------------------------

    private static void saveActivity(InMemoryStore store) throws IOException {
        Files.createDirectories(ACTIVITY_PATH.getParent());

        List<ActivitySnap> snaps = new ArrayList<>();
        for (ActivityItem ai : store.getActivity()) {
            LocalDateTime at = extractActivityTime(ai);
            String project = extractActivityProject(ai);
            String detail = extractActivityDetail(ai);

            // Never persist default Object#toString garbage
            if (detail.startsWith("com.") && detail.contains("@")) {
                detail = "";
            }

            snaps.add(new ActivitySnap(at, safe(project), safe(detail)));
        }

        Path tmp = ACTIVITY_PATH.resolveSibling(ACTIVITY_PATH.getFileName() + ".tmp");
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(tmp))) {
            oos.writeObject(snaps);
        }
        Files.move(tmp, ACTIVITY_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @SuppressWarnings("unchecked")
    private static void tryLoadActivity(InMemoryStore store) {
        if (!Files.exists(ACTIVITY_PATH)) return;

        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(ACTIVITY_PATH))) {
            Object obj = ois.readObject();
            if (!(obj instanceof List<?> list)) return;

            store.getActivity().clear();

            for (Object o : list) {
                if (!(o instanceof ActivitySnap s)) continue;
                ActivityItem item = createActivityItem(s);
                if (item != null) store.getActivity().add(item);
            }

        } catch (Exception ex) {
            System.err.println("[Persistence] Activity load failed (ignored): " + ex.getMessage());
        }
    }

    private static ActivityItem createActivityItem(ActivitySnap s) {
        // 1) (LocalDateTime, String, String)
        try {
            Constructor<ActivityItem> c =
                    ActivityItem.class.getConstructor(LocalDateTime.class, String.class, String.class);
            return c.newInstance(s.at(), safe(s.project()), safe(s.detail()));
        } catch (Exception ignored) {}

        // 2) (String time, String project, String detail)
        try {
            Constructor<ActivityItem> c =
                    ActivityItem.class.getConstructor(String.class, String.class, String.class);
            String timeStr = (s.at() == null) ? "" : s.at().format(TIME_FMT);
            return c.newInstance(timeStr, safe(s.project()), safe(s.detail()));
        } catch (Exception ignored) {}

        // 3) (String project, String detail) (your current constructor)
        try {
            ActivityItem ai = new ActivityItem(safe(s.project()), safe(s.detail()));
            // best-effort set time if it exists
            if (s.at() != null) trySetTime(ai, s.at());
            return ai;
        } catch (Exception ignored) {}

        return null;
    }

    private static void trySetTime(ActivityItem ai, LocalDateTime at) {
        // atProperty(): ObjectProperty<LocalDateTime>
        if (trySetObject(ai, "atProperty", at)) return;

        // timeProperty(): StringProperty ("HH:mm")
        String timeStr = at.format(TIME_FMT);
        trySetString(ai, "timeProperty", timeStr);
    }

    // --- extractors (try multiple common names) ---

    private static LocalDateTime extractActivityTime(ActivityItem ai) {
        Object v;

        v = invokeGetter(ai, "getAt");
        if (v instanceof LocalDateTime t) return t;

        v = invokeGetter(ai, "getTime");
        if (v instanceof LocalDateTime t2) return t2;

        v = invokeGetter(ai, "atProperty");
        LocalDateTime p1 = localDateTimeFromProperty(v);
        if (p1 != null) return p1;

        // Some models store time as StringProperty HH:mm
        v = invokeGetter(ai, "timeProperty");
        String ts = stringFromProperty(v);
        if (ts != null && !ts.isBlank()) {
            try {
                String[] parts = ts.split(":");
                int hh = Integer.parseInt(parts[0].trim());
                int mm = Integer.parseInt(parts[1].trim());
                return LocalDateTime.now().withHour(hh).withMinute(mm).withSecond(0).withNano(0);
            } catch (Exception ignored) {}
        }

        return LocalDateTime.now();
    }

    private static String extractActivityProject(ActivityItem ai) {
        String s;

        s = stringFromGetter(ai, "getProject");
        if (!s.isBlank()) return s;

        s = stringFromGetter(ai, "getProjectName");
        if (!s.isBlank()) return s;

        s = stringFromProperty(invokeGetter(ai, "projectProperty"));
        if (s != null && !s.isBlank()) return s;

        s = stringFromProperty(invokeGetter(ai, "projectNameProperty"));
        if (s != null && !s.isBlank()) return s;

        return "";
    }

    private static String extractActivityDetail(ActivityItem ai) {
        String s;

        s = stringFromGetter(ai, "getDetail");
        if (!s.isBlank()) return s;

        s = stringFromGetter(ai, "getMessage");
        if (!s.isBlank()) return s;

        s = stringFromGetter(ai, "getAction");
        if (!s.isBlank()) return s;

        s = stringFromProperty(invokeGetter(ai, "detailProperty"));
        if (s != null && !s.isBlank()) return s;

        s = stringFromProperty(invokeGetter(ai, "messageProperty"));
        if (s != null && !s.isBlank()) return s;

        s = stringFromProperty(invokeGetter(ai, "actionProperty"));
        if (s != null && !s.isBlank()) return s;

        // ✅ NEVER fall back to ai.toString() (that's what created com....@hash)
        return "";
    }

    private static String stringFromGetter(Object target, String method) {
        Object v = invokeGetter(target, method);
        return v == null ? "" : v.toString();
    }

    // -----------------------------
    // Phase / Milestone best-effort creation
    // -----------------------------

    private static String extractPhaseName(Phase ph) {
        if (ph == null) return "";
        try {
            Method m = ph.getClass().getMethod("nameProperty");
            Object prop = m.invoke(ph);
            Method get = prop.getClass().getMethod("get");
            Object val = get.invoke(prop);
            return val == null ? "" : val.toString();
        } catch (Exception ignored) {}
        return safe(ph.toString());
    }

    private static Phase createPhase(String name) {
        if (name == null) name = "";
        try {
            return Phase.class.getConstructor(String.class).newInstance(name);
        } catch (Exception ignored) {}
        try {
            Phase ph = Phase.class.getConstructor().newInstance();
            trySetNameProperty(ph, name);
            return ph;
        } catch (Exception ignored) {}
        return null;
    }

    private static void trySetNameProperty(Object obj, String name) {
        try {
            Method m = obj.getClass().getMethod("nameProperty");
            Object prop = m.invoke(obj);
            Method set = prop.getClass().getMethod("set", String.class);
            set.invoke(prop, name);
        } catch (Exception ignored) {}
    }

    private static LocalDate extractMilestoneDate(Milestone ms) {
        if (ms == null) return null;

        LocalDate d = (LocalDate) invokeGetter(ms, "getDate");
        if (d != null) return d;

        d = (LocalDate) invokeGetter(ms, "getDueDate");
        if (d != null) return d;

        Object dp = invokeGetter(ms, "dateProperty");
        d = localDateFromProperty(dp);
        if (d != null) return d;

        Object ddp = invokeGetter(ms, "dueDateProperty");
        return localDateFromProperty(ddp);
    }

    private static Milestone createMilestone(String name, LocalDate date) {
        try {
            Milestone m = Milestone.class.getConstructor(String.class).newInstance(name);
            trySetMilestoneDate(m, date);
            return m;
        } catch (Exception ignored) {}

        try {
            Milestone m = Milestone.class.getConstructor().newInstance();
            try { m.nameProperty().set(name); } catch (Exception ignored2) {}
            trySetMilestoneDate(m, date);
            return m;
        } catch (Exception ignored) {}

        return null;
    }

    private static void trySetMilestoneDate(Milestone m, LocalDate date) {
        if (m == null || date == null) return;

        try {
            Method setDate = m.getClass().getMethod("setDate", LocalDate.class);
            setDate.invoke(m, date);
            return;
        } catch (Exception ignored) {}

        try {
            Method setDue = m.getClass().getMethod("setDueDate", LocalDate.class);
            setDue.invoke(m, date);
            return;
        } catch (Exception ignored) {}

        try {
            Object dp = m.getClass().getMethod("dateProperty").invoke(m);
            if (dp != null) {
                Method set = dp.getClass().getMethod("set", Object.class);
                set.invoke(dp, date);
                return;
            }
        } catch (Exception ignored) {}

        try {
            Object ddp = m.getClass().getMethod("dueDateProperty").invoke(m);
            if (ddp != null) {
                Method set = ddp.getClass().getMethod("set", Object.class);
                set.invoke(ddp, date);
            }
        } catch (Exception ignored) {}
    }

    // -----------------------------
    // Generic reflection helpers
    // -----------------------------

    private static Object invokeGetter(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LocalDate localDateFromProperty(Object prop) {
        if (prop == null) return null;
        try {
            Method get = prop.getClass().getMethod("get");
            Object val = get.invoke(prop);
            return (val instanceof LocalDate) ? (LocalDate) val : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LocalDateTime localDateTimeFromProperty(Object prop) {
        if (prop == null) return null;
        try {
            Method get = prop.getClass().getMethod("get");
            Object val = get.invoke(prop);
            return (val instanceof LocalDateTime) ? (LocalDateTime) val : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stringFromProperty(Object prop) {
        if (prop == null) return null;
        try {
            Method get = prop.getClass().getMethod("get");
            Object val = get.invoke(prop);
            return val == null ? null : val.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean trySetString(Object target, String propertyMethod, String value) {
        try {
            Method m = target.getClass().getMethod(propertyMethod);
            Object prop = m.invoke(target);
            if (prop == null) return false;
            Method set = prop.getClass().getMethod("set", String.class);
            set.invoke(prop, value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean trySetObject(Object target, String propertyMethod, Object value) {
        try {
            Method m = target.getClass().getMethod(propertyMethod);
            Object prop = m.invoke(target);
            if (prop == null) return false;

            for (Method meth : prop.getClass().getMethods()) {
                if (!meth.getName().equals("set")) continue;
                if (meth.getParameterCount() != 1) continue;
                meth.invoke(prop, value);
                return true;
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
