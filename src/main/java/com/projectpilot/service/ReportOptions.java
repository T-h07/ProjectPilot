package com.projectpilot.service;

import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;

import java.time.LocalDate;
import java.util.Set;

public record ReportOptions(
        boolean includeSummary,
        boolean includePhases,
        boolean includeMilestones,
        boolean includeTasks,
        boolean includeTeam,
        boolean includeActivity,
        boolean onlyOpenTasks,
        Set<TaskStatus> statuses,
        Set<Priority> priorities,
        String assigneeId,
        String phaseId,
        LocalDate dueFrom,
        LocalDate dueTo,
        int activityLimit
) {
    public static final String ASSIGNEE_UNASSIGNED = "__UNASSIGNED__";
    public static final String PHASE_NONE = "__NONE__";

    public static ReportOptions defaults() {
        return new ReportOptions(
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                Set.of(),
                Set.of(),
                "",
                "",
                null,
                null,
                10
        );
    }

    public static ReportOptions orDefault(ReportOptions opts) {
        return opts == null ? defaults() : opts;
    }
}
