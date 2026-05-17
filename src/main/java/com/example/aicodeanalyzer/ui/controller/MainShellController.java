package com.example.aicodeanalyzer.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

/**
 * FXML controller for the main JavaFX application shell.
 */
public class MainShellController {
    @FXML
    private BorderPane root;

    public BorderPane root() {
        return root;
    }

    public void setHeader(Node header) {
        root.setTop(header);
    }

    public void setSidebar(Node sidebar) {
        root.setLeft(sidebar);
    }

    public void setContent(Node content) {
        root.setCenter(content);
    }
}
