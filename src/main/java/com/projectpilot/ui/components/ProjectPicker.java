package com.projectpilot.ui.components;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Project;
import com.projectpilot.security.AccessPolicy;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;

import java.util.IdentityHashMap;
import java.util.Map;

public class ProjectPicker extends ComboBox<Project> {

    private final AccessPolicy policy = new AccessPolicy();
    private final FilteredList<Project> visibleProjects;

    // refresh filter when membership changes inside projects
    private final Map<Project, ListChangeListener<?>> memberHooks = new IdentityHashMap<>();

    // prevents UI<->state ping-pong during programmatic updates / predicate refresh
    private boolean syncing = false;

    public ProjectPicker(InMemoryStore store, AppState appState) {
        visibleProjects = new FilteredList<>(store.getProjects(), p -> policy.canViewProject(appState, p));
        setItems(visibleProjects);

        setPromptText("Select project");
        setPrefWidth(320);

        hookProjectsForMembership(store, appState);

        // Initial alignment (also picks first visible if needed)
        refreshPredicate(appState);

        // UI -> state
        valueProperty().addListener((obs, oldV, newV) -> {
            if (syncing) return;
            if (oldV == newV) return;

            appState.setSelectedProject(newV);

            // If AppState rejected it (non-member), revert UI to accepted selection
            Project accepted = appState.getSelectedProject();
            if (accepted != newV) {
                syncToUi(accepted);
            }
        });

        // state -> UI
        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> {
            if (syncing) return;
            if (newV == getValue()) return;

            if (newV != null && !visibleProjects.contains(newV)) {
                // state points to something user can’t see -> show empty
                syncToUi(null);
            } else {
                syncToUi(newV);
            }
        });

        // session changes -> refresh predicate
        appState.sessionProperty().addListener((obs, o, n) -> refreshPredicate(appState));

        // IMPORTANT: removing this breaks the StackOverflow loop.
        // currentProjectRole is derived from selectedProject; it should NOT drive project visibility refresh.
        // appState.currentProjectRoleProperty().addListener((obs, o, n) -> refreshPredicate(appState));
    }

    private void syncToUi(Project p) {
        syncing = true;
        try {
            if (getValue() != p) {
                setValue(p);
            }
        } finally {
            syncing = false;
        }
    }

    private void hookProjectsForMembership(InMemoryStore store, AppState appState) {
        // hook existing
        store.getProjects().forEach(p -> hookOne(p, appState));

        // hook add/remove
        store.getProjects().addListener((ListChangeListener<Project>) c -> {
            while (c.next()) {
                if (c.wasAdded()) c.getAddedSubList().forEach(p -> hookOne(p, appState));
                if (c.wasRemoved()) c.getRemoved().forEach(this::unhookOne);
            }
            refreshPredicate(appState);
        });
    }

    private void hookOne(Project p, AppState appState) {
        if (p == null || memberHooks.containsKey(p)) return;
        ListChangeListener<?> l = c -> refreshPredicate(appState);
        try {
            p.getMembers().addListener((ListChangeListener) l);
            memberHooks.put(p, l);
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("project-picker", "Failed to attach member listener: " + (e == null ? "" : e.getMessage())); }
    }

    private void unhookOne(Project p) {
        if (p == null) return;
        ListChangeListener<?> l = memberHooks.remove(p);
        if (l == null) return;
        try {
            p.getMembers().removeListener((ListChangeListener) l);
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("project-picker", "Failed to remove member listener: " + (e == null ? "" : e.getMessage())); }
    }

    private void refreshPredicate(AppState appState) {
        syncing = true;
        try {
            visibleProjects.setPredicate(p -> policy.canViewProject(appState, p));

            Project stateSel = appState.getSelectedProject();

            // If state selection is no longer visible, clear it
            if (stateSel != null && !visibleProjects.contains(stateSel)) {
                appState.setSelectedProject(null);
                stateSel = null;
            }

            // If there’s no selection but we have visible projects, pick the first
            if (stateSel == null && !visibleProjects.isEmpty()) {
                appState.setSelectedProject(visibleProjects.get(0));
                stateSel = appState.getSelectedProject();
            }

            // reflect final state into UI
            if (getValue() != stateSel) {
                setValue(stateSel);
            }
        } finally {
            syncing = false;
        }
    }
}
