package com.projectpilot.service;

import com.projectpilot.model.Task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class CsvReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void exportTasksCsv(String projectName, List<Task> tasks, File outFile) throws IOException {
        if (outFile == null) throw new IOException("Output file is required");
        try (BufferedWriter out = Files.newBufferedWriter(outFile.toPath(), StandardCharsets.UTF_8)) {
            out.write("Project,Title,Status,Priority,Phase,Assignee,Due,Description");
            out.newLine();

            if (tasks == null) return;
            String project = projectName == null ? "" : projectName;

            for (Task t : tasks) {
                if (t == null) continue;
                String phase = t.getPhase() == null ? "" : safe(t.getPhase().getName());
                String assignee = t.getAssignee() == null ? "" : safe(t.getAssignee().getName());
                String due = t.getDueDate() == null ? "" : t.getDueDate().format(DATE);
                String desc = t.getDescription() == null ? "" : t.getDescription();

                out.write(csv(project));
                out.write(",");
                out.write(csv(safe(t.getTitle())));
                out.write(",");
                out.write(csv(String.valueOf(t.getStatus())));
                out.write(",");
                out.write(csv(String.valueOf(t.getPriority())));
                out.write(",");
                out.write(csv(phase));
                out.write(",");
                out.write(csv(assignee));
                out.write(",");
                out.write(csv(due));
                out.write(",");
                out.write(csv(desc));
                out.newLine();
            }
        }
    }

    private static String csv(String v) {
        String s = v == null ? "" : v;
        s = s.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
