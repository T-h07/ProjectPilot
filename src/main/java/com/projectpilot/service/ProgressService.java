package com.projectpilot.service;

import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.TaskStatus;

public class ProgressService {
    public int projectProgressPercent(Project project) {
        if (project == null || project.getTasks().isEmpty()) return 0;

        double total = 0;
        for (Task t : project.getTasks()) {
            total += statusToProgress(t.getStatus());
        }
        return (int) Math.round((total / project.getTasks().size()) * 100.0);
    }

    private double statusToProgress(TaskStatus s) {
        return switch (s) {
            case TODO -> 0.0;
            case IN_PROGRESS -> 0.5;
            case BLOCKED -> 0.25;
            case DONE -> 1.0;
        };
    }
}
