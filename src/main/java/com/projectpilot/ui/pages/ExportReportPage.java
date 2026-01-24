package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Project;
import com.projectpilot.service.PdfReportService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;

public class ExportReportPage extends VBox {

    private final Label header = new Label("Export Report");
    private final TextArea preview = new TextArea();

    private final Button exportPdfBtn = new Button("Export PDF…");
    private final PdfReportService pdfService = new PdfReportService();

    public ExportReportPage(InMemoryStore store, AppState appState) {
        setPadding(new Insets(16));
        setSpacing(12);

        header.getStyleClass().add("page-title");

        preview.setEditable(false);
        preview.setPrefHeight(600);
        preview.getStyleClass().add("pp-textarea");

        exportPdfBtn.getStyleClass().add("primary");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox toolbar = new HBox(10, spacer, exportPdfBtn);

        exportPdfBtn.setOnAction(e -> {
            Project p = appState.getSelectedProject();
            if (p == null) return;

            FileChooser fc = new FileChooser();
            fc.setTitle("Export ProjectPilot Report (PDF)");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF (*.pdf)", "*.pdf"));
            fc.setInitialFileName(safeFileName(p.getName()) + "-report.pdf");

            File file = fc.showSaveDialog(getScene() == null ? null : getScene().getWindow());
            if (file == null) return;

            try {
                pdfService.exportProjectReport(p, store.getActivity(), file);

                Alert a = new Alert(Alert.AlertType.INFORMATION);
                a.setTitle("Export complete");
                a.setHeaderText("PDF report saved");
                a.setContentText(file.getAbsolutePath());
                a.showAndWait();

            } catch (Exception ex) {
            ex.printStackTrace(); // so you see it in Run output

            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Export failed");
            a.setHeaderText("Could not create PDF");
            a.setContentText(ex.toString());
            a.showAndWait();
        }

    });

        refresh(appState.getSelectedProject());
        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> refresh(newV));

        getChildren().addAll(header, toolbar, preview);
    }

    private void refresh(Project p) {
        if (p == null) {
            header.setText("Export Report (no project selected)");
            preview.setText("Select a project, then click “Export PDF…”.");
            exportPdfBtn.setDisable(true);
            return;
        }

        exportPdfBtn.setDisable(false);
        header.setText("Export Report — " + p.getName());
        preview.setText(
                "This will export a PDF mirror of the selected project:\n\n" +
                        "• Overall progress + status counts\n" +
                        "• Phase breakdown (progress + open tasks)\n" +
                        "• Milestones (done/pending)\n" +
                        "• Team workload (open tasks)\n" +
                        "• Tasks (detailed: title, status, priority, phase, assignee, due, description)\n" +
                        "• Recent activity (latest 10)\n\n" +
                        "Click “Export PDF…” to save it."
        );
    }

    private String safeFileName(String s) {
        if (s == null || s.isBlank()) return "project";
        return s.trim().replaceAll("[\\\\/:*?\"<>|]+", "-").replaceAll("\\s+", "_");
    }
}
