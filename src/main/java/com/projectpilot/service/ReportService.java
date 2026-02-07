package com.projectpilot.service;

import com.projectpilot.model.ActivityItem;
import com.projectpilot.model.Member;
import com.projectpilot.model.Milestone;
import com.projectpilot.model.Phase;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.TaskStatus;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public class ReportService {
    private final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String generateHtml(Project p, List<ActivityItem> activity) {
        return generateHtml(p, activity, ReportOptions.defaults());
    }

    public String generateHtml(Project p, List<ActivityItem> activity, ReportOptions opts) {
        ReportOptions o = ReportOptions.orDefault(opts);

        List<Task> tasks = ReportFilters.filterTasks(p, o);
        List<Milestone> milestones = ReportFilters.filterMilestones(p, o);
        List<ActivityItem> activityFiltered = ReportFilters.filterActivity(activity, o);
        String filterSummary = ReportFilters.describeFilters(p, o);

        int pct = ReportFilters.progressPercent(tasks);
        long todo = ReportFilters.countStatus(tasks, TaskStatus.TODO);
        long ip = ReportFilters.countStatus(tasks, TaskStatus.IN_PROGRESS);
        long blocked = ReportFilters.countStatus(tasks, TaskStatus.BLOCKED);
        long done = ReportFilters.countStatus(tasks, TaskStatus.DONE);

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
                .append(esc(safeDate(p.getStartDate()))).append(" -> ").append(esc(safeDate(p.getEndDate())))
                .append("</div>");

        if (!"All data".equals(filterSummary)) {
            sb.append("<div class='muted'>Filters: ").append(esc(filterSummary)).append("</div>");
        }

        if (o.includeSummary()) {
            sb.append("<div class='card'>");
            sb.append("<div><b>Progress:</b> ").append(pct).append("%</div>");
            sb.append("<div class='muted'>Tasks (filtered): TODO: ").append(todo)
                    .append(" | IN PROGRESS: ").append(ip)
                    .append(" | BLOCKED: ").append(blocked)
                    .append(" | DONE: ").append(done)
                    .append("</div>");
            sb.append("</div>");
        }

        if (o.includePhases()) {
            sb.append("<div class='card'><h3>Phases</h3>");
            if (p.getPhases().isEmpty()) {
                sb.append("<div class='muted'>No phases.</div>");
            } else {
                sb.append("<table><tr><th>Phase</th><th>Progress</th><th>Open Tasks</th></tr>");
                for (Phase ph : p.getPhases()) {
                    List<Task> phTasks = tasks.stream().filter(t -> t.getPhase() == ph).toList();
                    int phPct = ReportFilters.progressPercent(phTasks);
                    long open = phTasks.stream().filter(t -> t.getStatus() != TaskStatus.DONE).count();
                    sb.append("<tr><td>").append(esc(ph.getName())).append("</td><td>")
                            .append(phPct).append("%</td><td>").append(open).append("</td></tr>");
                }
                sb.append("</table>");
            }
            sb.append("</div>");
        }

        if (o.includeTasks()) {
            sb.append("<div class='card'><h3>Tasks</h3>");
            if (tasks.isEmpty()) {
                sb.append("<div class='muted'>No tasks match the current filters.</div>");
            } else {
                appendTaskSection(sb, "TODO", tasks.stream().filter(t -> t.getStatus() == TaskStatus.TODO).toList());
                appendTaskSection(sb, "IN PROGRESS", tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).toList());
                appendTaskSection(sb, "BLOCKED", tasks.stream().filter(t -> t.getStatus() == TaskStatus.BLOCKED).toList());
                appendTaskSection(sb, "DONE", tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).toList());
            }
            sb.append("</div>");
        }

        if (o.includeTeam()) {
            sb.append("<div class='card'><h3>Team</h3>");
            if (p.getMembers().isEmpty()) {
                sb.append("<div class='muted'>No members.</div>");
            } else {
                sb.append("<table><tr><th>Member</th><th>Open Tasks</th></tr>");
                for (Member m : p.getMembers()) {
                    long open = tasks.stream()
                            .filter(t -> t.getAssignee() != null && t.getAssignee() == m)
                            .filter(t -> t.getStatus() != TaskStatus.DONE)
                            .count();
                    sb.append("<tr><td>").append(esc(m.getName())).append("</td><td>").append(open).append("</td></tr>");
                }
                sb.append("</table>");
            }
            sb.append("</div>");
        }

        if (o.includeMilestones()) {
            sb.append("<div class='card'><h3>Milestones</h3>");
            if (milestones.isEmpty()) {
                sb.append("<div class='muted'>No milestones.</div>");
            } else {
                sb.append("<table><tr><th>Status</th><th>Milestone</th><th>Due</th></tr>");
                for (Milestone m : milestones) {
                    String state = m.completedProperty().get() ? "Done" : "Pending";
                    sb.append("<tr><td>")
                            .append("<span class='badge ").append(m.completedProperty().get() ? "done" : "").append("'>")
                            .append(state).append("</span>")
                            .append("</td><td>").append(esc(m.nameProperty().get()))
                            .append("</td><td>").append(esc(safeDate(m.dueDateProperty().get())))
                            .append("</td></tr>");
                }
                sb.append("</table>");
            }
            sb.append("</div>");
        }

        if (o.includeActivity()) {
            sb.append("<div class='card'><h3>Recent Activity</h3>");
            if (activityFiltered.isEmpty()) {
                sb.append("<div class='muted'>No activity.</div>");
            } else {
                sb.append("<table><tr><th>Time</th><th>Project</th><th>Event</th></tr>");
                activityFiltered.stream().limit(o.activityLimit()).forEach(a -> {
                    sb.append("<tr><td>")
                            .append(esc(a.getTime().format(DateTimeFormatter.ofPattern("HH:mm"))))
                            .append("</td><td>").append(esc(a.getProjectName()))
                            .append("</td><td>").append(esc(a.getMessage()))
                            .append("</td></tr>");
                });
                sb.append("</table>");
            }
            sb.append("</div>");
        }

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

    private String safeDate(java.time.LocalDate d) {
        return d == null ? "-" : d.format(dateFmt);
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
