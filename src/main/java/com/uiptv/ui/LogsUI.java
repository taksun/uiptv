package com.uiptv.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.io.PrintWriter;
import java.io.StringWriter;

public class LogsUI extends BorderPane {
    public static final String ERROR = "ERROR";
    public static final String INFO = "INFO";
    private static final int MAX_LINES = 1000; // Maksymalna liczba linii w logach
    private static TextArea logTextArea; // TextArea do wyświetlania logów
    private static final StringBuilder logs = new StringBuilder(); // Przechowuje wszystkie logi jako tekst

    public LogsUI() {
        logTextArea = new TextArea(); // TextArea do wyświetlania logów
        logTextArea.setEditable(false); // Uniemożliwia edycję logów przez użytkownika
        logTextArea.setWrapText(true); // Zawijanie tekstu
        logTextArea.setMinHeight(500);

        // Przycisk do czyszczenia logów
        Button clearButton = new Button("Wyczyść logi");
        clearButton.setOnAction(e -> clearLogs()); // Obsługa zdarzenia kliknięcia

        // Przycisk do kopiowania zaznaczonych logów do schowka
        Button copySelectedLogsButton = new Button("Kopiuj zaznaczone logi");
        copySelectedLogsButton.setOnAction(e -> copySelectedLogsToClipboard()); // Obsługa zdarzenia kliknięcia

        setCenter(logTextArea); // TextArea w centrum
        setBottom(new HBox(10, clearButton, copySelectedLogsButton)); // Przyciski na dole
    }

    public static void logError(String message) {
        log(message, ERROR);
    }

    public static void logError(String message, Throwable exception) {
        log(message, ERROR);
        log("Błąd: " + exception.getMessage(), ERROR);
        log(getStackTraceAsString(exception), ERROR);
    }

    public static void logInfo(String message) {
        log(message, INFO);
    }

    public static void logInfoNoRefresh(String message) {
        log(message, INFO, false);
    }

    public static void log(String message, String level) {
        log(message, level, true);
    }

    public static void log(String message, String level, boolean refreshTextArea) {
        // Dodaj log z odpowiednim formatowaniem
        String formattedMessage = "[" + level + "] " + message + "\n";

        // Dodaj log do StringBuilder
        logs.append(formattedMessage);

        // Ograniczenie liczby linii w logach
        if (logs.toString().split("\n").length > MAX_LINES) {
            int firstLineEnd = logs.indexOf("\n") + 1; // Znajdź koniec pierwszej linii
            logs.delete(0, firstLineEnd); // Usuń najstarszą linię
        }

        if (refreshTextArea) {
            Platform.runLater(() -> {
                // Zaktualizuj TextArea
                logTextArea.setText(logs.toString());

                // Przewiń do końca po zaktualizowaniu zawartości
                scrollToBottom();
            });
        }
    }

    // Metoda do przewijania TextArea do końca
    private static void scrollToBottom() {
        Platform.runLater(() -> {
            logTextArea.setScrollTop(Double.MAX_VALUE); // Przewiń do końca
        });
    }

    // Metoda do czyszczenia logów
    public void clearLogs() {
        logs.setLength(0); // Wyczyść StringBuilder
        logTextArea.clear(); // Wyczyść TextArea
    }

    // Metoda do konwersji stack trace na ciąg znaków
    private static String getStackTraceAsString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw); // Zapisanie stack trace do StringWriter
        return sw.toString(); // Zwrócenie stack trace jako ciąg znaków
    }

    // Metoda do kopiowania zaznaczonych logów do schowka
    private void copySelectedLogsToClipboard() {
        String selectedText = logTextArea.getSelectedText();
        if (selectedText != null && !selectedText.isEmpty()) {
            // Skopiuj zaznaczony tekst do schowka
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(selectedText); // Umieść zaznaczony tekst w schowku
            clipboard.setContent(content);

            // Opcjonalnie: Powiadom użytkownika, że logi zostały skopiowane
            log("Zaznaczone logi zostały skopiowane do schowka.", INFO);
        } else {
            log("Nie zaznaczono żadnych logów do skopiowania.", INFO);
        }
    }
}