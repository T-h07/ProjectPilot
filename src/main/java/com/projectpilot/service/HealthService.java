package com.projectpilot.service;

import com.projectpilot.model.Project;
import com.projectpilot.model.enums.ProjectHealth;
import com.projectpilot.model.enums.TaskStatus;

public class HealthService {
    public ProjectHealth computeHealth(Project p) {
        if (p == null) return ProjectHealth.ON_TRACK;

        long blocked = p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.BLOCKED).count();
        long overdue = 0; // keep simple for now; we’ll add dueDate logic next

        if (blocked >= 2 || overdue >= 3) return ProjectHealth.BLOCKED;
        if (blocked >= 1 || overdue >= 1) return ProjectHealth.AT_RISK;
        return ProjectHealth.ON_TRACK;
    }
}
