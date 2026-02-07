package com.projectpilot.lan;

import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class LanStoreBroadcaster {

    private final InMemoryStore store;
    private final LanWsServer wsServer;
    private final ScheduledExecutorService exec;

    private final Map<String, Runnable> detachByProjectId = new HashMap<>();
    private final Map<Task, ChangeListener<Object>> taskListeners = new IdentityHashMap<>();
    private final Map<Member, ChangeListener<Object>> memberListeners = new IdentityHashMap<>();
    private final Map<Phase, ChangeListener<Object>> phaseListeners = new IdentityHashMap<>();
    private final Map<Milestone, ChangeListener<Object>> milestoneListeners = new IdentityHashMap<>();
    private volatile boolean pending = false;

    public LanStoreBroadcaster(InMemoryStore store, LanWsServer wsServer) {
        this.store = store;
        this.wsServer = wsServer;
        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pp-lan-broadcast");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        attachProjectListListeners();
        signal();
    }

    public void stop() {
        exec.shutdownNow();
        detachByProjectId.values().forEach(Runnable::run);
        detachByProjectId.clear();
    }

    private void attachProjectListListeners() {
        for (Project p : store.getProjects()) attachProjectListeners(p);
        for (Project p : store.getHistoryProjects()) attachProjectListeners(p);

        store.getProjects().addListener((ListChangeListener<Project>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (Project p : c.getAddedSubList()) attachProjectListeners(p);
                }
                if (c.wasRemoved()) {
                    for (Project p : c.getRemoved()) detachProjectListeners(p.getId());
                }
            }
            signal();
        });

        store.getHistoryProjects().addListener((ListChangeListener<Project>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (Project p : c.getAddedSubList()) attachProjectListeners(p);
                }
                if (c.wasRemoved()) {
                    for (Project p : c.getRemoved()) detachProjectListeners(p.getId());
                }
            }
            signal();
        });
    }

    private void attachProjectListeners(Project p) {
        if (p == null || p.getId() == null) return;
        if (detachByProjectId.containsKey(p.getId())) return;

        List<Runnable> detach = new ArrayList<>();

        ChangeListener<Object> projectDirty = (obs, o, n) -> signal();

        p.nameProperty().addListener(projectDirty);
        p.descriptionProperty().addListener(projectDirty);
        p.stakeholdersProperty().addListener(projectDirty);
        p.phaseTemplateProperty().addListener(projectDirty);
        p.startDateProperty().addListener(projectDirty);
        p.endDateProperty().addListener(projectDirty);
        p.healthProperty().addListener(projectDirty);
        p.statusProperty().addListener(projectDirty);
        p.completedDateProperty().addListener(projectDirty);

        detach.add(() -> p.nameProperty().removeListener(projectDirty));
        detach.add(() -> p.descriptionProperty().removeListener(projectDirty));
        detach.add(() -> p.stakeholdersProperty().removeListener(projectDirty));
        detach.add(() -> p.phaseTemplateProperty().removeListener(projectDirty));
        detach.add(() -> p.startDateProperty().removeListener(projectDirty));
        detach.add(() -> p.endDateProperty().removeListener(projectDirty));
        detach.add(() -> p.healthProperty().removeListener(projectDirty));
        detach.add(() -> p.statusProperty().removeListener(projectDirty));
        detach.add(() -> p.completedDateProperty().removeListener(projectDirty));

        ListChangeListener<Task> tasksListener = ch -> {
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (Task t : ch.getAddedSubList()) attachTaskListeners(t);
                }
                if (ch.wasRemoved()) {
                    for (Task t : ch.getRemoved()) detachTaskListeners(t);
                }
            }
            signal();
        };
        p.getTasks().addListener(tasksListener);
        detach.add(() -> p.getTasks().removeListener(tasksListener));
        for (Task t : p.getTasks()) attachTaskListeners(t);

        ListChangeListener<Member> membersListener = ch -> {
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (Member m : ch.getAddedSubList()) attachMemberListeners(m);
                }
                if (ch.wasRemoved()) {
                    for (Member m : ch.getRemoved()) detachMemberListeners(m);
                }
            }
            signal();
        };
        p.getMembers().addListener(membersListener);
        detach.add(() -> p.getMembers().removeListener(membersListener));
        for (Member m : p.getMembers()) attachMemberListeners(m);

        ListChangeListener<Phase> phasesListener = ch -> {
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (Phase ph : ch.getAddedSubList()) attachPhaseListeners(ph);
                }
                if (ch.wasRemoved()) {
                    for (Phase ph : ch.getRemoved()) detachPhaseListeners(ph);
                }
            }
            signal();
        };
        p.getPhases().addListener(phasesListener);
        detach.add(() -> p.getPhases().removeListener(phasesListener));
        for (Phase ph : p.getPhases()) attachPhaseListeners(ph);

        ListChangeListener<Milestone> milestonesListener = ch -> {
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (Milestone ms : ch.getAddedSubList()) attachMilestoneListeners(ms);
                }
                if (ch.wasRemoved()) {
                    for (Milestone ms : ch.getRemoved()) detachMilestoneListeners(ms);
                }
            }
            signal();
        };
        p.getMilestones().addListener(milestonesListener);
        detach.add(() -> p.getMilestones().removeListener(milestonesListener));
        for (Milestone ms : p.getMilestones()) attachMilestoneListeners(ms);

        detach.add(() -> {
            for (Task t : p.getTasks()) detachTaskListeners(t);
            for (Member m : p.getMembers()) detachMemberListeners(m);
            for (Phase ph : p.getPhases()) detachPhaseListeners(ph);
            for (Milestone ms : p.getMilestones()) detachMilestoneListeners(ms);
        });

        detachByProjectId.put(p.getId(), () -> detach.forEach(Runnable::run));
    }

    private void detachProjectListeners(String projectId) {
        if (projectId == null) return;
        Runnable detach = detachByProjectId.remove(projectId);
        if (detach != null) detach.run();
    }

    private void attachTaskListeners(Task t) {
        if (t == null) return;
        if (taskListeners.containsKey(t)) return;
        ChangeListener<Object> dirty = (obs, o, n) -> signal();
        t.titleProperty().addListener(dirty);
        t.descriptionProperty().addListener(dirty);
        t.statusProperty().addListener(dirty);
        t.priorityProperty().addListener(dirty);
        t.dueDateProperty().addListener(dirty);
        t.assigneeProperty().addListener(dirty);
        t.phaseProperty().addListener(dirty);
        taskListeners.put(t, dirty);
    }

    private void detachTaskListeners(Task t) {
        if (t == null) return;
        ChangeListener<Object> l = taskListeners.remove(t);
        if (l == null) return;
        t.titleProperty().removeListener(l);
        t.descriptionProperty().removeListener(l);
        t.statusProperty().removeListener(l);
        t.priorityProperty().removeListener(l);
        t.dueDateProperty().removeListener(l);
        t.assigneeProperty().removeListener(l);
        t.phaseProperty().removeListener(l);
    }

    private void attachMemberListeners(Member m) {
        if (m == null) return;
        if (memberListeners.containsKey(m)) return;
        ChangeListener<Object> dirty = (obs, o, n) -> signal();
        m.nameProperty().addListener(dirty);
        m.roleProperty().addListener(dirty);
        memberListeners.put(m, dirty);
    }

    private void detachMemberListeners(Member m) {
        if (m == null) return;
        ChangeListener<Object> l = memberListeners.remove(m);
        if (l == null) return;
        m.nameProperty().removeListener(l);
        m.roleProperty().removeListener(l);
    }

    private void attachPhaseListeners(Phase ph) {
        if (ph == null) return;
        if (phaseListeners.containsKey(ph)) return;
        ChangeListener<Object> dirty = (obs, o, n) -> signal();
        ph.nameProperty().addListener(dirty);
        ph.startProperty().addListener(dirty);
        ph.endProperty().addListener(dirty);
        phaseListeners.put(ph, dirty);
    }

    private void detachPhaseListeners(Phase ph) {
        if (ph == null) return;
        ChangeListener<Object> l = phaseListeners.remove(ph);
        if (l == null) return;
        ph.nameProperty().removeListener(l);
        ph.startProperty().removeListener(l);
        ph.endProperty().removeListener(l);
    }

    private void attachMilestoneListeners(Milestone ms) {
        if (ms == null) return;
        if (milestoneListeners.containsKey(ms)) return;
        ChangeListener<Object> dirty = (obs, o, n) -> signal();
        ms.nameProperty().addListener(dirty);
        ms.dueDateProperty().addListener(dirty);
        ms.completedProperty().addListener(dirty);
        milestoneListeners.put(ms, dirty);
    }

    private void detachMilestoneListeners(Milestone ms) {
        if (ms == null) return;
        ChangeListener<Object> l = milestoneListeners.remove(ms);
        if (l == null) return;
        ms.nameProperty().removeListener(l);
        ms.dueDateProperty().removeListener(l);
        ms.completedProperty().removeListener(l);
    }

    private void signal() {
        if (pending) return;
        pending = true;
        exec.schedule(() -> {
            pending = false;
            wsServer.broadcastRefresh();
        }, 200, TimeUnit.MILLISECONDS);
    }
}
