package com.projectpilot.data;

import com.projectpilot.data.db.DbStore;
import com.projectpilot.data.db.TeamService;
import com.projectpilot.model.Member;
import com.projectpilot.model.Milestone;
import com.projectpilot.model.Phase;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.ProjectHealth;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.model.enums.TaskStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SampleData {

    public record UserSeed(String id, String name) {}

    public static void seed(InMemoryStore store) {
        seed(store, null, null, List.of());
    }

    public static void seed(InMemoryStore store, String primaryMemberId, String primaryMemberName, List<UserSeed> knownUsers) {
        if (store == null) return;

        LocalDate today = LocalDate.now();

        List<UserSeed> seeds = buildSeeds(primaryMemberId, primaryMemberName, knownUsers);
        UserSeed leadSeed = seeds.get(0);

        // Project 1
        Project p1 = store.createProject(new Project("Apollo Supply Chain Revamp"));
        p1.setDescription("Modernize supplier workflows, reduce lead time, and improve inventory accuracy.");
        p1.setHealth(ProjectHealth.ON_TRACK);
        p1.setStartDate(today.minusDays(20));
        p1.setEndDate(today.plusDays(60));

        Member p1Lead = addMember(store, p1, leadSeed, ProjectRole.LEADER);
        Member p1M1 = addMember(store, p1, seeds.get(1), ProjectRole.MEMBER);
        Member p1M2 = addMember(store, p1, seeds.get(2), ProjectRole.MEMBER);
        addMember(store, p1, seeds.get(3), ProjectRole.VIEWER);

        Phase p1Discovery = addPhase(store, p1, "Discovery", today.minusDays(20), today.minusDays(5));
        Phase p1Build = addPhase(store, p1, "Build", today.minusDays(4), today.plusDays(20));
        Phase p1Pilot = addPhase(store, p1, "Pilot", today.plusDays(21), today.plusDays(35));
        Phase p1Launch = addPhase(store, p1, "Launch", today.plusDays(36), today.plusDays(60));

        addMilestone(store, p1, "Pilot kickoff", today.plusDays(21), false);
        addMilestone(store, p1, "Go-live", today.plusDays(60), false);

        addTask(store, p1, task("Stakeholder interviews", TaskStatus.DONE, Priority.HIGH, today.minusDays(10), p1Lead, p1Discovery,
                "Capture requirements across ops, finance, and compliance."));
        addTask(store, p1, task("Process map current flow", TaskStatus.DONE, Priority.MEDIUM, today.minusDays(6), p1M1, p1Discovery,
                "Document handoffs, delays, and bottlenecks."));
        addTask(store, p1, task("Define target KPIs", TaskStatus.IN_PROGRESS, Priority.HIGH, today.plusDays(5), p1Lead, p1Build,
                "Agree on lead time, fill rate, and cost targets."));
        addTask(store, p1, task("Integrate supplier API", TaskStatus.IN_PROGRESS, Priority.HIGH, today.plusDays(14), p1M2, p1Build,
                "Connect top 3 suppliers to the new gateway."));
        addTask(store, p1, task("Warehouse scanner rollout", TaskStatus.TODO, Priority.MEDIUM, today.plusDays(22), p1M1, p1Pilot,
                "Procure devices and stage training."));
        addTask(store, p1, task("Pilot training plan", TaskStatus.TODO, Priority.LOW, today.plusDays(25), p1M2, p1Pilot,
                "Create quick guides and onboarding checklist."));
        addTask(store, p1, task("Cutover checklist", TaskStatus.TODO, Priority.HIGH, today.plusDays(40), p1Lead, p1Launch,
                "Finalize rollback, monitoring, and support plan."));
        addTask(store, p1, task("Go-live support rotation", TaskStatus.TODO, Priority.MEDIUM, today.plusDays(50), p1M1, p1Launch,
                "Schedule on-call coverage for week one."));
        addTask(store, p1, task("Post-launch metrics review", TaskStatus.TODO, Priority.MEDIUM, today.plusDays(58), p1Lead, p1Launch,
                "Report initial KPIs and adoption."));

        // Project 2
        Project p2 = store.createProject(new Project("Nimbus Mobile App"));
        p2.setDescription("Rebuild the mobile experience with offline-first flows and push notifications.");
        p2.setHealth(ProjectHealth.AT_RISK);
        p2.setStartDate(today.minusDays(10));
        p2.setEndDate(today.plusDays(45));

        Member p2Lead = addMember(store, p2, leadSeed, ProjectRole.LEADER);
        Member p2M1 = addMember(store, p2, seeds.get(1), ProjectRole.MEMBER);
        Member p2M2 = addMember(store, p2, seeds.get(2), ProjectRole.MEMBER);
        Member p2M3 = addMember(store, p2, seeds.get(4), ProjectRole.MEMBER);
        addMember(store, p2, seeds.get(3), ProjectRole.VIEWER);

        Phase p2Design = addPhase(store, p2, "Design", today.minusDays(10), today.plusDays(5));
        Phase p2Dev = addPhase(store, p2, "Development", today.plusDays(6), today.plusDays(28));
        Phase p2Qa = addPhase(store, p2, "QA", today.plusDays(29), today.plusDays(38));
        Phase p2Release = addPhase(store, p2, "Release", today.plusDays(39), today.plusDays(45));

        addMilestone(store, p2, "Beta build", today.plusDays(28), false);
        addMilestone(store, p2, "Store submission", today.plusDays(42), false);

        addTask(store, p2, task("Wireframes for onboarding", TaskStatus.DONE, Priority.MEDIUM, today.minusDays(2), p2M2, p2Design,
                "Finalize welcome, signup, and verification screens."));
        addTask(store, p2, task("Design system tokens", TaskStatus.IN_PROGRESS, Priority.MEDIUM, today.plusDays(3), p2M1, p2Design,
                "Standardize color, type, and spacing scale."));
        addTask(store, p2, task("Auth flow implementation", TaskStatus.IN_PROGRESS, Priority.HIGH, today.plusDays(12), p2M1, p2Dev,
                "Implement secure login + refresh handling."));
        addTask(store, p2, task("Push notifications", TaskStatus.BLOCKED, Priority.HIGH, today.plusDays(16), p2M2, p2Dev,
                "Blocked on vendor credentials."));
        addTask(store, p2, task("Offline caching", TaskStatus.TODO, Priority.MEDIUM, today.plusDays(22), p2M3, p2Dev,
                "Cache core data for field use."));
        addTask(store, p2, task("Crash analytics", TaskStatus.TODO, Priority.LOW, today.plusDays(25), p2Lead, p2Dev,
                "Integrate crash and performance monitoring."));
        addTask(store, p2, task("Regression test suite", TaskStatus.TODO, Priority.HIGH, today.plusDays(32), p2M1, p2Qa,
                "Cover critical flows and edge cases."));
        addTask(store, p2, task("App store assets", TaskStatus.TODO, Priority.MEDIUM, today.plusDays(40), p2Lead, p2Release,
                "Prepare screenshots and release notes."));

        // Project 3
        Project p3 = store.createProject(new Project("Orion Analytics Upgrade"));
        p3.setDescription("Upgrade analytics models and dashboards to the new data contract.");
        p3.setHealth(ProjectHealth.BLOCKED);
        p3.setStartDate(today.minusDays(30));
        p3.setEndDate(today.plusDays(30));

        Member p3Lead = addMember(store, p3, leadSeed, ProjectRole.LEADER);
        Member p3M1 = addMember(store, p3, seeds.get(1), ProjectRole.MEMBER);
        Member p3M2 = addMember(store, p3, seeds.get(2), ProjectRole.MEMBER);
        Member p3M3 = addMember(store, p3, seeds.get(4), ProjectRole.MEMBER);
        addMember(store, p3, seeds.get(5), ProjectRole.VIEWER);

        Phase p3Plan = addPhase(store, p3, "Plan", today.minusDays(30), today.minusDays(10));
        Phase p3Impl = addPhase(store, p3, "Implement", today.minusDays(9), today.plusDays(10));
        Phase p3Validate = addPhase(store, p3, "Validate", today.plusDays(11), today.plusDays(25));

        addMilestone(store, p3, "Model migration", today.plusDays(10), false);
        addMilestone(store, p3, "Executive dashboard", today.plusDays(25), false);

        addTask(store, p3, task("Inventory legacy models", TaskStatus.DONE, Priority.MEDIUM, today.minusDays(20), p3M3, p3Plan,
                "Catalog data sources and owners."));
        addTask(store, p3, task("Define new data contract", TaskStatus.DONE, Priority.HIGH, today.minusDays(12), p3Lead, p3Plan,
                "Finalize schema and naming conventions."));
        addTask(store, p3, task("ETL pipeline refactor", TaskStatus.BLOCKED, Priority.HIGH, today.plusDays(5), p3M2, p3Impl,
                "Blocked on upstream schema changes."));
        addTask(store, p3, task("Backfill historical data", TaskStatus.IN_PROGRESS, Priority.HIGH, today.plusDays(8), p3M1, p3Impl,
                "Run incremental backfills for last 24 months."));
        addTask(store, p3, task("KPI validation", TaskStatus.TODO, Priority.MEDIUM, today.plusDays(18), p3M3, p3Validate,
                "Validate parity vs. legacy dashboards."));
        addTask(store, p3, task("Executive dashboard polish", TaskStatus.TODO, Priority.LOW, today.plusDays(22), p3Lead, p3Validate,
                "Refine layout and annotations."));

        // Project 4 (History)
        Project p4 = store.createProject(new Project("Legacy CRM Decommission"));
        p4.setDescription("Retire legacy CRM infrastructure after data migration.");
        p4.setHealth(ProjectHealth.ON_TRACK);
        p4.setStartDate(today.minusDays(90));
        p4.setEndDate(today.minusDays(10));

        Member p4Lead = addMember(store, p4, leadSeed, ProjectRole.LEADER);
        Member p4M1 = addMember(store, p4, seeds.get(1), ProjectRole.MEMBER);
        addMember(store, p4, seeds.get(2), ProjectRole.MEMBER);

        Phase p4Assess = addPhase(store, p4, "Assess", today.minusDays(90), today.minusDays(70));
        Phase p4Migrate = addPhase(store, p4, "Migrate", today.minusDays(69), today.minusDays(30));
        Phase p4Shutdown = addPhase(store, p4, "Shutdown", today.minusDays(29), today.minusDays(10));

        addMilestone(store, p4, "Data migration complete", today.minusDays(30), true);
        addMilestone(store, p4, "Infrastructure shutdown", today.minusDays(10), true);

        addTask(store, p4, task("Export customer data", TaskStatus.DONE, Priority.HIGH, today.minusDays(60), p4Lead, p4Assess,
                "Export all CRM entities to staging."));
        addTask(store, p4, task("Import into new CRM", TaskStatus.DONE, Priority.HIGH, today.minusDays(45), p4M1, p4Migrate,
                "Verify field mapping and transforms."));
        addTask(store, p4, task("Read-only freeze", TaskStatus.DONE, Priority.MEDIUM, today.minusDays(20), p4Lead, p4Shutdown,
                "Disable writes and notify stakeholders."));
        addTask(store, p4, task("Decommission servers", TaskStatus.DONE, Priority.MEDIUM, today.minusDays(10), p4M1, p4Shutdown,
                "Remove compute and archive backups."));

        store.markProjectDone(p4);

        createTeamsIfPossible(store, p1, p2, p1Lead, p1M1, p1M2, p2M1, p2M2);
    }

    private static List<UserSeed> buildSeeds(String primaryId, String primaryName, List<UserSeed> knownUsers) {
        List<UserSeed> seeds = new ArrayList<>();
        if (primaryId != null && !primaryId.isBlank()) {
            seeds.add(new UserSeed(primaryId, safeName(primaryName, "Owner")));
        }
        if (knownUsers != null) {
            for (UserSeed u : knownUsers) {
                if (u == null || u.id() == null || u.id().isBlank()) continue;
                if (seeds.stream().anyMatch(s -> s.id() != null && s.id().equals(u.id()))) continue;
                seeds.add(new UserSeed(u.id(), safeName(u.name(), "User")));
            }
        }
        String[] fallbacks = new String[] { "Alex", "Mira", "Sam", "Jordan", "Riley", "Noa", "Tariq" };
        int i = 0;
        while (seeds.size() < 6 && i < fallbacks.length) {
            seeds.add(new UserSeed(null, fallbacks[i++]));
        }
        if (seeds.isEmpty()) {
            seeds.add(new UserSeed(null, "Owner"));
            seeds.add(new UserSeed(null, "Alex"));
            seeds.add(new UserSeed(null, "Mira"));
            seeds.add(new UserSeed(null, "Sam"));
            seeds.add(new UserSeed(null, "Jordan"));
            seeds.add(new UserSeed(null, "Riley"));
        }
        return seeds;
    }

    private static Member addMember(InMemoryStore store, Project project, UserSeed seed, ProjectRole role) {
        Member m = seed != null && seed.id() != null && !seed.id().isBlank()
                ? new Member(seed.id(), safeName(seed.name(), "User"), role)
                : new Member(safeName(seed == null ? null : seed.name(), "User"), role);
        store.addMember(project, m);
        return m;
    }

    private static Phase addPhase(InMemoryStore store, Project project, String name, LocalDate start, LocalDate end) {
        Phase ph = new Phase(name);
        if (start != null) ph.startProperty().set(start);
        if (end != null) ph.endProperty().set(end);
        store.addPhase(project, ph);
        return ph;
    }

    private static Milestone addMilestone(InMemoryStore store, Project project, String name, LocalDate due, boolean done) {
        Milestone ms = new Milestone(name);
        if (due != null) ms.dueDateProperty().set(due);
        ms.completedProperty().set(done);
        store.addMilestone(project, ms);
        return ms;
    }

    private static Task task(String title, TaskStatus status, Priority priority, LocalDate due, Member assignee, Phase phase, String desc) {
        Task t = new Task(title);
        t.setStatus(status);
        t.setPriority(priority);
        t.setDueDate(due);
        t.setAssignee(assignee);
        t.setPhase(phase);
        t.setDescription(desc == null ? "" : desc);
        return t;
    }

    private static void addTask(InMemoryStore store, Project project, Task task) {
        store.addTask(project, task);
    }

    private static void createTeamsIfPossible(
            InMemoryStore store,
            Project p1,
            Project p2,
            Member lead,
            Member m1,
            Member m2,
            Member p2m1,
            Member p2m2
    ) {
        if (!(store instanceof DbStore dbStore)) return;

        dbStore.createTeam("Platform Core", lead.getId(), List.of(
                new TeamService.TeamMemberSpec(m1.getId(), ProjectRole.MEMBER),
                new TeamService.TeamMemberSpec(m2.getId(), ProjectRole.MEMBER)
        ));

        dbStore.createTeam("Release Ops", p2m1.getId(), List.of(
                new TeamService.TeamMemberSpec(p2m2.getId(), ProjectRole.MEMBER),
                new TeamService.TeamMemberSpec(lead.getId(), ProjectRole.MEMBER)
        ));

        String coreId = findTeamId(dbStore, "Platform Core");
        if (coreId != null) {
            dbStore.assignTeamToProject(coreId, p1.getId());
            dbStore.assignTeamToProject(coreId, p2.getId());
        }

        String releaseId = findTeamId(dbStore, "Release Ops");
        if (releaseId != null) {
            dbStore.assignTeamToProject(releaseId, p2.getId());
        }
    }

    private static String findTeamId(DbStore store, String name) {
        if (store == null || name == null) return null;
        for (TeamService.TeamRow row : store.listTeams()) {
            if (row != null && name.equalsIgnoreCase(row.name())) return row.id();
        }
        return null;
    }

    private static String safeName(String value, String fallback) {
        if (value == null) return fallback;
        String v = value.trim();
        return v.isEmpty() ? fallback : v;
    }
}
