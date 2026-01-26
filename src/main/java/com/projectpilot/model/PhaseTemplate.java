package com.projectpilot.model;

import java.util.List;

public enum PhaseTemplate {
    EMPTY("No phases (empty)", List.of()),

    SOFTWARE("Software Project", List.of(
            "Discovery",
            "Planning",
            "Design",
            "Implementation",
            "Testing",
            "Deployment",
            "Maintenance"
    )),

    MARKETING("Marketing Campaign", List.of(
            "Research",
            "Strategy",
            "Content Production",
            "Launch",
            "Optimization",
            "Reporting"
    )),

    RESEARCH("Research Project", List.of(
            "Literature Review",
            "Methodology",
            "Data Collection",
            "Analysis",
            "Write-up",
            "Presentation"
    ));

    private final String label;
    private final List<String> phases;

    PhaseTemplate(String label, List<String> phases) {
        this.label = label;
        this.phases = phases;
    }

    public List<String> phases() { return phases; }

    @Override public String toString() { return label; }
}
