package com.projectpilot.data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * Serializable DTO snapshot for projects (active + history).
 * NOTE: Activity is persisted separately (projectpilot-activity.dat) to avoid breaking this format.
 */
public record AppSnapshot(
        List<ProjectSnap> activeProjects,
        List<ProjectSnap> historyProjects
) implements Serializable {

    public record ProjectSnap(
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            String health,                 // ProjectHealth.name()
            String status,                 // Project.ProjectStatus.name()
            LocalDate completedDate,
            List<MemberSnap> members,
            List<PhaseSnap> phases,
            List<TaskSnap> tasks,
            List<MilestoneSnap> milestones
    ) implements Serializable {}

    public record MemberSnap(
            String name,
            String role                    // ProjectRole.name()
    ) implements Serializable {}

    public record PhaseSnap(
            String name
    ) implements Serializable {}

    public record TaskSnap(
            String title,
            String description,
            String status,                 // TaskStatus.name()
            String priority,               // Priority.name()
            LocalDate dueDate,
            int assigneeIndex,             // -1 if none
            int phaseIndex                 // -1 if none
    ) implements Serializable {}

    public record MilestoneSnap(
            String name,
            LocalDate date                 // nullable (only if your Milestone supports it)
    ) implements Serializable {}
}
