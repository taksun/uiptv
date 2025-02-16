package com.uiptv.ui;

import com.uiptv.service.ConfigurationService;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;

import java.io.PrintWriter;
import java.io.StringWriter;

public class LogsUI extends BorderPane {
    public static final String ERROR = "ERROR";
    public static final String INFO = "INFO";
    private static final int MAX_LINES = 1000; // Maksymalna liczba linii w logach
    private static WebView logWebView;
    private static final StringBuilder logs = new StringBuilder(); // Przechowuje wszystkie logi jako HTML

    public LogsUI() {
        logWebView = new WebView(); // WebView do wyświetlania kolorowanych logów
        logWebView.setMinHeight(500);

        // Ścieżka do pliku CSS (musi być dostępna w classpath lub jako plik)
        String cssFile = ConfigurationService.getInstance().read().isDarkTheme() ? "/dark-application.css" : "/application.css";
        String cssPath = LogsUI.class.getResource(cssFile).toExternalForm();

        // HTML z zewnętrznym CSS
        String htmlContent = """
                <html>
                    <head>
                        <link rel="stylesheet" type="text/css" href="%s">
                    </head>
                """.formatted(cssPath);
        logs.append(htmlContent).append("<body><pre>");

        // Przycisk do czyszczenia logów
        Button clearButton = new Button("Wyczyść logi");
        clearButton.setOnAction(e -> clearLogs()); // Obsługa zdarzenia kliknięcia

        // Przycisk do kopiowania zaznaczonych logów do schowka
        Button copySelectedLogsButton = new Button("Kopiuj zaznaczone logi");
        copySelectedLogsButton.setOnAction(e -> copySelectedLogsToClipboard()); // Obsługa zdarzenia kliknięcia

        setCenter(logWebView); // WebView w centrum
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

    public static void logInfoNoRefreshWeb(String message) {
        log(message, INFO, false);
    }

    public static void log(String message, String level) {
        log(message, level, true);
    }

    public static void log(String message, String level, boolean refreshWeb) {
        // Dodaj log z odpowiednim kolorem
        String color = "black"; // Domyślny kolor
        if (ERROR.equals(level)) {
            color = "red"; // Czerwony dla błędów
        } else if (ConfigurationService.getInstance().read().isDarkTheme()) {
            color = "white";
        }

        // Dodaj log do StringBuilder
        logs.append("<span style='color:").append(color).append(";'>").append(message).append("</span><br>");

        // Ograniczenie liczby linii w logach
        if (logs.toString().split("<br>").length > MAX_LINES) {
            int firstLineEnd = logs.indexOf("<br>") + 4; // Znajdź koniec pierwszej linii
            logs.delete(0, firstLineEnd); // Usuń najstarszą linię
        }

        if (refreshWeb) {
            Platform.runLater(() -> {
                // Zaktualizuj WebView
                logWebView.getEngine().loadContent(logs + "</pre></body></html>");

                // Przewiń do końca po zaktualizowaniu zawartości
                scrollToBottom();
            });
        }
    }

    // Metoda do przewijania WebView do końca
    private static void scrollToBottom() {
        // Dodaj opóźnienie, aby zapewnić, że zawartość jest renderowana
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(100); // Opóźnienie 100 ms
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Platform.runLater(() -> logWebView.getEngine().executeScript(
                    "window.scrollTo(0, document.body.scrollHeight);"
            ));
        });
    }

    // Metoda do czyszczenia logów
    public void clearLogs() {
        logs.setLength(0); // Wyczyść StringBuilder
        logs.append("<html><body><pre>"); // Zresetuj HTML
        logWebView.getEngine().loadContent(logs + "</pre></body></html>");
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
        String selectedText = (String) logWebView.getEngine().executeScript("window.getSelection().toString();");
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