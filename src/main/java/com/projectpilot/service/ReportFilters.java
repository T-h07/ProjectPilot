package com.projectpilot.service;

import com.projectpilot.model.ActivityItem;
import com.projectpilot.model.Milestone;
import com.projectpilot.model.Phase;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class ReportFilters {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ReportFilters() {}

    public static List<Task> filterTasks(Project p, ReportOptions opts) {
        if (p == null) return List.of();
        ReportOptions o = ReportOptions.orDefault(opts);

        Set<TaskStatus> statuses = o.statuses() == null ? Set.of() : o.statuses();
        Set<Priority> priorities = o.priorities() == null ? Set.of() : o.priorities();
        String assigneeId = safe(o.assigneeId());
        String phaseId = safe(o.phaseId());
        LocalDate from = o.dueFrom();
        LocalDate to = o.dueTo();

        List<Task> out = new ArrayList<>();
        for (Task t : p.getTasks()) {
            if (t == null) continue;

            TaskStatus status = t.getStatus();
            if (o.onlyOpenTasks() && status == TaskStatus.DONE) continue;
            if (!statuses.isEmpty() && (status == null || !statuses.contains(status))) continue;

            Priority pr = t.getPriority();
            if (!priorities.isEmpty() && (pr == null || !priorities.contains(pr))) continue;

            if (!assigneeId.isBlank()) {
                if (ReportOptions.ASSIGNEE_UNASSIGNED.equals(assigneeId)) {
                    if (t.getAssignee() != null) continue;
                } else {
                    if (t.getAssignee() == null || !assigneeId.equals(t.getAssignee().getId())) continue;
                }
            }

            if (!phaseId.isBlank()) {
                if (ReportOptions.PHASE_NONE.equals(phaseId)) {
                    if (t.getPhase() != null) continue;
                } else {
                    if (t.getPhase() == null || !phaseId.equals(t.getPhase().getId())) continue;
                }
            }

            if ((from != null || to != null) && !inDateRange(t.getDueDate(), from, to)) continue;

            out.add(t);
        }

        return out;
    }

    public static List<Milestone> filterMilestones(Project p, ReportOptions opts) {
        if (p == null) return List.of();
        ReportOptions o = ReportOptions.orDefault(opts);
        LocalDate from = o.dueFrom();
        LocalDate to = o.dueTo();

        List<Milestone> out = new ArrayList<>();
        for (Milestone m : p.getMilestones()) {
            if (m == null) continue;
            if ((from != null || to != null) && !inDateRange(m.dueDateProperty().get(), from, to)) continue;
            out.add(m);
        }
        return out;
    }

    public static List<ActivityItem> filterActivity(List<ActivityItem> activity, ReportOptions opts) {
        if (activity == null || activity.isEmpty()) return List.of();
        ReportOptions o = ReportOptions.orDefault(opts);
        LocalDate from = o.dueFrom();
        LocalDate to = o.dueTo();

        List<ActivityItem> out = new ArrayList<>();
        for (ActivityItem a : activity) {
            if (a == null) continue;
            LocalDateTime at = a.getTime();
            if ((from != null || to != null) && !inDateRange(at == null ? null : at.toLocalDate(), from, to)) continue;
            out.add(a);
        }
        return out;
    }

    public static String describeFilters(Project p, ReportOptions opts) {
        ReportOptions o = ReportOptions.orDefault(opts);

        List<String> parts = new ArrayList<>();

        if (o.onlyOpenTasks()) parts.add("open tasks only");

        if (o.statuses() != null && !o.statuses().isEmpty()) {
            String v = o.statuses().stream()
                    .sorted(Comparator.comparing(Enum::name))
                    .map(Enum::name)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            if (!v.isBlank()) parts.add("status: " + v);
        }

        if (o.priorities() != null && !o.priorities().isEmpty()) {
            String v = o.priorities().stream()
                    .sorted(Comparator.comparing(Enum::name))
                    .map(Enum::name)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            if (!v.isBlank()) parts.add("priority: " + v);
        }

        String assigneeId = safe(o.assigneeId());
        if (!assigneeId.isBlank()) {
            if (ReportOptions.ASSIGNEE_UNASSIGNED.equals(assigneeId)) {
                parts.add("assignee: unassigned");
            } else {
                parts.add("assignee: " + memberName(p, assigneeId));
            }
        }

        String phaseId = safe(o.phaseId());
        if (!phaseId.isBlank()) {
            if (ReportOptions.PHASE_NONE.equals(phaseId)) {
                parts.add("phase: none");
            } else {
                parts.add("phase: " + phaseName(p, phaseId));
            }
        }

        if (o.dueFrom() != null || o.dueTo() != null) {
            String from = o.dueFrom() == null ? "-" : o.dueFrom().format(DATE);
            String to = o.dueTo() == null ? "-" : o.dueTo().format(DATE);
            parts.add("due: " + from + " to " + to);
        }

        if (parts.isEmpty()) return "All data";
        return String.join("; ", parts);
    }

    public static int progressPercent(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) return 0;
        double total = 0;
        for (Task t : tasks) {
            if (t == null || t.getStatus() == null) continue;
            total += switch (t.getStatus()) {
                case TODO -> 0.0;
                case IN_PROGRESS -> 0.5;
                case BLOCKED -> 0.25;
                case DONE -> 1.0;
            };
        }
        return (int) Math.round((total / tasks.size()) * 100.0);
    }

    public static long countStatus(List<Task> tasks, TaskStatus status) {
        if (tasks == null || tasks.isEmpty() || status == null) return 0;
        return tasks.stream().filter(t -> t != null && status == t.getStatus()).count();
    }

    private static boolean inDateRange(LocalDate d, LocalDate from, LocalDate to) {
        if (d == null) return false;
        if (from != null && d.isBefore(from)) return false;
        if (to != null && d.isAfter(to)) return false;
        return true;
    }

    private static String memberName(Project p, String id) {
        if (p == null || id == null) return "-";
        return p.getMembers().stream()
                .filter(m -> m != null && id.equals(m.getId()))
                .map(m -> m.getName() == null || m.getName().isBlank() ? id : m.getName())
                .findFirst()
                .orElse(id);
    }

    private static String phaseName(Project p, String id) {
        if (p == null || id == null) return "-";
        return p.getPhases().stream()
                .filter(ph -> ph != null && id.equals(ph.getId()))
                .map(ph -> ph.getName() == null || ph.getName().isBlank() ? id : ph.getName())
                .findFirst()
                .orElse(id);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
