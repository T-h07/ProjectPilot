package com.projectpilot.service;

import com.projectpilot.model.*;
import com.projectpilot.model.enums.TaskStatus;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public class ReportService {

    private final ProgressService progressService = new ProgressService();
    private final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String generateHtml(Project p, List<ActivityItem> activity) {
        int pct = progressService.projectProgressPercent(p);

        long todo = p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.TODO).count();
        long ip = p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long blocked = p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.BLOCKED).count();
        long done = p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();

        StringBuilder sb = new StringBuilder();
        sb.append("""
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8"/>
              <title>ProjectPilot Report</title>
              <style>
                body{font-family:system-ui,Segoe UI,Arial,sans-serif;margin:32px;color:#0f172a;}
                h1{margin:0 0 6px 0;}
                .muted{color:#475569;}
                .card{border:1px solid #e2e8f0;border-radius:12px;padding:16px;margin:14px 0;}
                table{width:100%;border-collapse:collapse;margin-top:10px;}
                th,td{border-bottom:1px solid #e2e8f0;padding:8px 10px;text-align:left;font-size:14px;}
                th{background:#f8fafc;}
                .badge{display:inline-block;padding:2px 8px;border-radius:999px;font-size:12px;border:1px solid #cbd5e1;}
                .done{background:#dcfce7;border-color:#86efac;}
                .blocked{background:#fee2e2;border-color:#fca5a5;}
                .ip{background:#dbeafe;border-color:#93c5fd;}
              </style>
            </head>
            <body>
            """);

        sb.append("<h1>").append(esc(p.getName())).append("</h1>");
        sb.append("<div class='muted'>")
                .append(esc(p.getStartDate().toString())).append(" → ").append(esc(p.getEndDate().toString()))
                .append("</div>");

        sb.append("<div class='card'>");
        sb.append("<div><b>Progress:</b> ").append(pct).append("%</div>");
        sb.append("<div class='muted'>TODO: ").append(todo)
                .append(" | IN PROGRESS: ").append(ip)
                .append(" | BLOCKED: ").append(blocked)
                .append(" | DONE: ").append(done)
                .append("</div>");
        sb.append("</div>");

        // Phase breakdown
        sb.append("<div class='card'><h3>Phases</h3><table><tr><th>Phase</th><th>Progress</th><th>Open Tasks</th></tr>");
        for (Phase ph : p.getPhases()) {
            int phPct = phaseProgressPercent(p, ph);
            long open = p.getTasks().stream()
                    .filter(t -> t.getPhase() == ph)
                    .filter(t -> t.getStatus() != TaskStatus.DONE)
                    .count();
            sb.append("<tr><td>").append(esc(ph.getName())).append("</td><td>")
                    .append(phPct).append("%</td><td>").append(open).append("</td></tr>");
        }
        sb.append("</table></div>");

        // Tasks grouped by status
        sb.append("<div class='card'><h3>Tasks</h3>");
        appendTaskSection(sb, "TODO", p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.TODO).toList());
        appendTaskSection(sb, "IN PROGRESS", p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).toList());
        appendTaskSection(sb, "BLOCKED", p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.BLOCKED).toList());
        appendTaskSection(sb, "DONE", p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.DONE).toList());
        sb.append("</div>");

        // Team
        sb.append("<div class='card'><h3>Team</h3><table><tr><th>Member</th><th>Open Tasks</th></tr>");
        for (Member m : p.getMembers()) {
            long open = p.getTasks().stream()
                    .filter(t -> t.getAssignee() != null && t.getAssignee() == m)
                    .filter(t -> t.getStatus() != TaskStatus.DONE)
                    .count();
            sb.append("<tr><td>").append(esc(m.getName())).append("</td><td>").append(open).append("</td></tr>");
        }
        sb.append("</table></div>");

        // Milestones
        sb.append("<div class='card'><h3>Milestones</h3><table><tr><th>Status</th><th>Milestone</th><th>Due</th></tr>");
        for (Milestone m : p.getMilestones()) {
            String state = m.completedProperty().get() ? "Done" : "Pending";
            sb.append("<tr><td>")
                    .append("<span class='badge ").append(m.completedProperty().get() ? "done" : "").append("'>")
                    .append(state).append("</span>")
                    .append("</td><td>").append(esc(m.nameProperty().get()))
                    .append("</td><td>").append(esc(m.dueDateProperty().get().toString()))
                    .append("</td></tr>");
        }
        sb.append("</table></div>");

        // Recent activity (last 10)
        sb.append("<div class='card'><h3>Recent Activity</h3><table><tr><th>Time</th><th>Project</th><th>Event</th></tr>");
        activity.stream().limit(10).forEach(a -> {
            sb.append("<tr><td>")
                    .append(esc(a.getTime().format(DateTimeFormatter.ofPattern("HH:mm"))))
                    .append("</td><td>").append(esc(a.getProjectName()))
                    .append("</td><td>").append(esc(a.getMessage()))
                    .append("</td></tr>");
        });
        sb.append("</table></div>");

        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendTaskSection(StringBuilder sb, String title, List<Task> tasks) {
        sb.append("<h4>").append(esc(title)).append(" (").append(tasks.size()).append(")</h4>");
        if (tasks.isEmpty()) {
            sb.append("<div class='muted'>No tasks.</div>");
            return;
        }
        sb.append("<table><tr><th>Title</th><th>Phase</th><th>Assignee</th><th>Due</th><th>Status</th></tr>");
        tasks.stream()
                .sorted(Comparator.comparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(t -> {
                    String ph = t.getPhase() == null ? "-" : t.getPhase().getName();
                    String asg = t.getAssignee() == null ? "-" : t.getAssignee().getName();
                    String due = t.getDueDate() == null ? "-" : t.getDueDate().format(dateFmt);
                    sb.append("<tr><td>").append(esc(t.getTitle()))
                            .append("</td><td>").append(esc(ph))
                            .append("</td><td>").append(esc(asg))
                            .append("</td><td>").append(esc(due))
                            .append("</td><td>").append(esc(String.valueOf(t.getStatus())))
                            .append("</td></tr>");
                });
        sb.append("</table>");
    }

    private int phaseProgressPercent(Project p, Phase phase) {
        var tasks = p.getTasks().stream().filter(t -> t.getPhase() == phase).toList();
        if (tasks.isEmpty()) return 0;

        double total = 0;
        for (var t : tasks) {
            total += switch (t.getStatus()) {
                case TODO -> 0.0;
                case IN_PROGRESS -> 0.5;
                case BLOCKED -> 0.25;
                case DONE -> 1.0;
            };
        }
        return (int) Math.round((total / tasks.size()) * 100.0);
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
