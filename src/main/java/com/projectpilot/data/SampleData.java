package com.projectpilot.data;

import com.projectpilot.model.Member;
import com.projectpilot.model.Milestone;
import com.projectpilot.model.Phase;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.model.enums.TaskStatus;

public class SampleData {
    public static void seed(InMemoryStore store) {
        Project p1 = store.createProject("SmartCampus Cloud Portal");
        p1.getMembers().addAll(
                new Member("Taulant", ProjectRole.LEADER),
                new Member("Ardi", ProjectRole.MEMBER),
                new Member("Elira", ProjectRole.MEMBER)
        );
        p1.getPhases().addAll(
                new Phase("Planning"),
                new Phase("Build"),
                new Phase("Test"),
                new Phase("Deploy")
        );
        p1.getMilestones().addAll(
                new Milestone("MVP demo"),
                new Milestone("Release candidate")
        );

        Task t1 = new Task("Define requirements");
        t1.setStatus(TaskStatus.DONE);
        t1.setAssignee(p1.getMembers().get(0));

        Task t2 = new Task("Build dashboard UI shell");
        t2.setStatus(TaskStatus.IN_PROGRESS);
        t2.setAssignee(p1.getMembers().get(1));

        Task t3 = new Task("Export report prototype");
        t3.setStatus(TaskStatus.TODO);
        t3.setAssignee(p1.getMembers().get(2));

        p1.getTasks().addAll(t1, t2, t3);

        Project p2 = store.createProject("Homework Planner AI Bot");
        p2.getMembers().add(new Member("Taulant", ProjectRole.LEADER));
        p2.getPhases().addAll(new Phase("Design"), new Phase("Build"), new Phase("QA"));
        p2.getTasks().add(new Task("Draft feature list"));
    }
}
