package com.projectpilot.service;

import com.projectpilot.model.ActivityItem;
import com.projectpilot.model.Member;
import com.projectpilot.model.Milestone;
import com.projectpilot.model.Phase;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.TaskStatus;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public class PdfReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final PDRectangle PAGE = PDRectangle.A4;
    private static final float MARGIN = 48f;

    private static final PDFont FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final float H1 = 18f;
    private static final float H2 = 13f;
    private static final float BODY = 10.5f;
    private static final float LEADING = 14f;

    public void exportProjectReport(Project p, List<ActivityItem> activity, File outFile) throws IOException {
        exportProjectReport(p, activity, outFile, ReportOptions.defaults());
    }

    public void exportProjectReport(Project p, List<ActivityItem> activity, File outFile, ReportOptions opts) throws IOException {
        ReportOptions o = ReportOptions.orDefault(opts);

        List<Task> tasks = ReportFilters.filterTasks(p, o);
        List<Milestone> milestones = ReportFilters.filterMilestones(p, o);
        List<ActivityItem> activityFiltered = ReportFilters.filterActivity(activity, o);
        String filterSummary = ReportFilters.describeFilters(p, o);

        try (PDDocument doc = new PDDocument()) {
            PageWriter w = new PageWriter(doc);

            w.h1("ProjectPilot - Project Health Report");
            w.spacer(6);
            w.h2(p.getName());
            w.text("Date range: " + safe(p.getStartDate()) + " -> " + safe(p.getEndDate()));
            w.text("Exported: " + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            if (!"All data".equals(filterSummary)) {
                w.text("Filters: " + filterSummary);
            }
            w.spacer(10);

            int pct = ReportFilters.progressPercent(tasks);
            long todo = ReportFilters.countStatus(tasks, TaskStatus.TODO);
            long ip = ReportFilters.countStatus(tasks, TaskStatus.IN_PROGRESS);
            long blocked = ReportFilters.countStatus(tasks, TaskStatus.BLOCKED);
            long done = ReportFilters.countStatus(tasks, TaskStatus.DONE);

            if (o.includeSummary()) {
                w.h2("Overall Status");
                w.text("Progress: " + pct + "%");
                w.text("Tasks (filtered): TODO " + todo + " | IN PROGRESS " + ip + " | BLOCKED " + blocked + " | DONE " + done);
                w.text("Members: " + p.getMembers().size() + " | Phases: " + p.getPhases().size() + " | Milestones: " + p.getMilestones().size());
                w.spacer(10);
            }

            if (o.includePhases()) {
                w.h2("Phases");
                if (p.getPhases().isEmpty()) {
                    w.text("No phases.");
                } else {
                    for (Phase ph : p.getPhases()) {
                        List<Task> phTasks = tasks.stream().filter(t -> t.getPhase() == ph).toList();
                        int phPct = ReportFilters.progressPercent(phTasks);
                        long open = phTasks.stream().filter(t -> t.getStatus() != TaskStatus.DONE).count();
                        w.text("- " + ph.getName() + " - " + phPct + "%, open tasks: " + open);
                    }
                }
                w.spacer(10);
            }

            if (o.includeMilestones()) {
                w.h2("Milestones");
                if (milestones.isEmpty()) {
                    w.text("No milestones.");
                } else {
                    for (Milestone m : milestones) {
                        String status = m.completedProperty().get() ? "DONE" : "PENDING";
                        String due = (m.dueDateProperty().get() == null) ? "-" : m.dueDateProperty().get().format(DATE);
                        w.text("- [" + status + "] " + m.nameProperty().get() + " (due " + due + ")");
                    }
                }
                w.spacer(10);
            }

            if (o.includeTeam()) {
                w.h2("Team Workload");
                if (p.getMembers().isEmpty()) {
                    w.text("No members.");
                } else {
                    for (Member m : p.getMembers()) {
                        long open = tasks.stream()
                                .filter(t -> t.getAssignee() != null && t.getAssignee() == m)
                                .filter(t -> t.getStatus() != TaskStatus.DONE)
                                .count();
                        w.text("- " + m.getName() + " - open tasks: " + open);
                    }
                }
                w.spacer(10);
            }

            if (o.includeTasks()) {
                w.h2("Tasks (Detailed)");
                var sorted = tasks.stream()
                        .sorted(Comparator
                                .comparing(Task::getStatus)
                                .thenComparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();

                if (sorted.isEmpty()) {
                    w.text("No tasks.");
                } else {
                    for (Task t : sorted) {
                        String ph = t.getPhase() == null ? "-" : t.getPhase().getName();
                        String asg = t.getAssignee() == null ? "-" : t.getAssignee().getName();
                        String due = t.getDueDate() == null ? "-" : t.getDueDate().format(DATE);

                        w.text("- " + safe(t.getTitle()));
                        w.text("   Status: " + t.getStatus() + " | Priority: " + t.getPriority()
                                + " | Phase: " + ph + " | Assignee: " + asg + " | Due: " + due);

                        String desc = t.getDescription();
                        if (desc != null && !desc.isBlank()) {
                            w.textWrapped("   Description: " + desc.trim());
                        }
                        w.spacer(6);
                    }
                }
                w.spacer(6);
            }

            if (o.includeActivity()) {
                w.h2("Recent Activity (latest " + o.activityLimit() + ")");
                if (activityFiltered.isEmpty()) {
                    w.text("No activity yet.");
                } else {
                    int limit = Math.min(o.activityLimit(), activityFiltered.size());
                    for (int i = 0; i < limit; i++) {
                        ActivityItem a = activityFiltered.get(i);
                        w.text("- " + a.getTime().format(TIME) + " - [" + a.getProjectName() + "] " + a.getMessage());
                    }
                }
            }

            w.close();
            doc.save(outFile);
        }
    }

    private String safe(Object o) {
        return (o == null) ? "-" : o.toString();
    }

    private static class PageWriter {
        private final PDDocument doc;
        private PDPage page;
        private PDPageContentStream cs;

        private float x;
        private float y;
        private float width;

        PageWriter(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage();
        }

        private static String pdfSafe(String s) {
            if (s == null) return "";

            StringBuilder out = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (ch == '\n' || ch == '\r' || ch == '\t') {
                    out.append(' ');
                } else if (ch >= 32 && ch <= 126) {
                    out.append(ch);
                } else {
                    out.append('?');
                }
            }
            return out.toString();
        }

        void newPage() throws IOException {
            close();
            page = new PDPage(PAGE);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            x = MARGIN;
            y = PAGE.getHeight() - MARGIN;
            width = PAGE.getWidth() - (2 * MARGIN);
        }

        void h1(String s) throws IOException { line(FONT_BOLD, H1, s); }
        void h2(String s) throws IOException { line(FONT_BOLD, H2, s); }
        void text(String s) throws IOException { line(FONT, BODY, s); }

        void spacer(float px) { y -= px; }

        void textWrapped(String s) throws IOException {
            for (String ln : wrap(FONT, BODY, s, width)) {
                line(FONT, BODY, ln);
            }
        }

        private void line(PDFont font, float size, String s) throws IOException {
            ensureSpace();
            cs.beginText();
            cs.setFont(font, size);
            cs.newLineAtOffset(x, y);
            cs.showText(pdfSafe(s));
            cs.endText();
            y -= LEADING;
        }

        private void ensureSpace() throws IOException {
            if (y <= MARGIN + LEADING * 2) {
                newPage();
            }
        }

        void close() throws IOException {
            if (cs != null) {
                cs.close();
                cs = null;
            }
        }

        private static List<String> wrap(PDFont font, float size, String text, float maxWidth) throws IOException {
            text = pdfSafe(text);
            if (text.isBlank()) return List.of("");
            String[] words = text.replace("\r", "").split("\\s+");
            StringBuilder line = new StringBuilder();
            java.util.ArrayList<String> out = new java.util.ArrayList<>();

            for (String w : words) {
                String candidate = line.isEmpty() ? w : line + " " + w;
                float candWidth = stringWidth(font, size, candidate);
                if (candWidth <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    if (!line.isEmpty()) out.add(line.toString());
                    line.setLength(0);
                    line.append(w);
                }
            }
            if (!line.isEmpty()) out.add(line.toString());
            return out;
        }

        private static float stringWidth(PDFont font, float size, String s) throws IOException {
            return (font.getStringWidth(s) / 1000f) * size;
        }
    }
}
