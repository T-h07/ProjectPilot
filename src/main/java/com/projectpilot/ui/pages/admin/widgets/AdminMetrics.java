package com.projectpilot.ui.pages.admin.widgets;

import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserAdminService;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.TaskStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public final class AdminMetrics {

    private static final long ONLINE_WINDOW_MS = 5 * 60 * 1000L;
    private static final long IDLE_WINDOW_MS = 60 * 60 * 1000L;

    private AdminMetrics() {}

    public record Presence(int online, int idle, int offline) {}

    public record Snapshot(
            int totalUsers,
            int activeUsers,
            int adminCount,
            int userCount,
            int totalProjects,
            int activeProjects,
            int doneProjects,
            int totalTasks,
            Map<TaskStatus, Integer> taskStatusCounts,
            int overdueTasks,
            int unassignedTasks,
            Presence presence,
            int[] lastOnlineBins14d
    ) {}

    public static Snapshot compute(List<UserAdminService.UserRow> users, InMemoryStore store) {
        users = (users == null) ? List.of() : users;
        int totalUsers = users.size();
        int activeUsers = (int) users.stream().filter(UserAdminService.UserRow::active).count();

        int adminCount = 0;
        int userCount = 0;

        List<Long> lastOnline = new ArrayList<>(users.size());
        for (var u : users) {
            if (u.globalRole() == GlobalRole.ADMIN) adminCount++;
            else userCount++;

            if (u.lastOnlineAt() != null && u.lastOnlineAt() > 0) lastOnline.add(u.lastOnlineAt());
        }

        Presence presence = computePresence(users);

        int activeProjects = store.getProjects().size();
        int doneProjects = store.getHistoryProjects().size();
        int totalProjects = activeProjects + doneProjects;

        Map<TaskStatus, Integer> statusCounts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus st : TaskStatus.values()) statusCounts.put(st, 0);

        int totalTasks = 0;
        int overdue = 0;
        int unassigned = 0;
        LocalDate today = LocalDate.now();

        List<Project> allProjects = new ArrayList<>();
        allProjects.addAll(store.getProjects());
        allProjects.addAll(store.getHistoryProjects());

        for (Project p : allProjects) {
            for (Task t : p.getTasks()) {
                if (t == null) continue;
                totalTasks++;

                TaskStatus st = t.getStatus() == null ? TaskStatus.TODO : t.getStatus();
                statusCounts.merge(st, 1, Integer::sum);

                if (t.getAssignee() == null) unassigned++;

                if (t.getDueDate() != null && st != TaskStatus.DONE && t.getDueDate().isBefore(today)) {
                    overdue++;
                }
            }
        }

        int[] bins14 = TimeBinning.binEpochMillisByDay(lastOnline, 14, ZoneId.systemDefault());

        return new Snapshot(
                totalUsers,
                activeUsers,
                adminCount,
                userCount,
                totalProjects,
                activeProjects,
                doneProjects,
                totalTasks,
                statusCounts,
                overdue,
                unassigned,
                presence,
                bins14
        );
    }

    public static Presence computePresence(List<UserAdminService.UserRow> users) {
        long now = System.currentTimeMillis();
        int online = 0, idle = 0, offline = 0;

        for (var u : users) {
            if (u == null) continue;
            if (!u.active()) { // treat disabled as offline for distribution
                offline++;
                continue;
            }

            Long last = u.lastOnlineAt();
            if (last == null || last <= 0) {
                offline++;
                continue;
            }
            long delta = Math.max(0L, now - last);
            if (delta <= ONLINE_WINDOW_MS) online++;
            else if (delta <= IDLE_WINDOW_MS) idle++;
            else offline++;
        }

        return new Presence(online, idle, offline);
    }

    public static int sum(int[] bins) {
        if (bins == null) return 0;
        int s = 0;
        for (int v : bins) s += v;
        return s;
    }

    public static int last(int[] bins) {
        if (bins == null || bins.length == 0) return 0;
        return bins[bins.length - 1];
    }
}
