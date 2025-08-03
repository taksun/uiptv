package com.uiptv.util;

import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.LogsUI;
import com.uiptv.ui.RootApplication;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FileDownloader {

    private static final int READ_BUFFER_SIZE = 256 * 1024;

    public static void openDownloadWindow(Map<String, String> filesToDownload) {
        // Tworzenie nowego okna
        Stage downloadStage = new Stage();
        downloadStage.initModality(Modality.APPLICATION_MODAL);
        downloadStage.initStyle(StageStyle.UTILITY);
        downloadStage.setTitle("Pobieranie plików");

        // Tworzenie paska postępu
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);

        // Etykieta do wyświetlania statusu
        Label statusLabel = new Label("Rozpoczynanie pobierania...");

        // Etykieta aktualnie pobieranego pliku
        Label currentFileLabel = new Label("Brak aktywnych pobrań.");

        // Lista plików oczekujących
        ListView<String> queueListView = new ListView<>();
        queueListView.getItems().setAll(filesToDownload.values());
        queueListView.setPrefHeight(100);

        // Przycisk do anulowania pobierania
        Button cancelButton = new Button("Anuluj");
        cancelButton.setDisable(true);

        downloadStage.setOnHiding(event -> cancelButton.fire());

        // Tworzenie zadania do pobierania plików
        Task<Void> downloadTask = createDownloadTask(filesToDownload, currentFileLabel, queueListView);

        // Powiązanie paska postępu z zadaniem
        progressBar.progressProperty().bind(downloadTask.progressProperty());

        // Powiązanie etykiety statusu z właściwością message zadania
        statusLabel.textProperty().bind(downloadTask.messageProperty());

        // Obsługa zdarzenia po zakończeniu zadania
        downloadTask.setOnSucceeded(e -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("Wszystkie pliki zostały pobrane!");
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1);
            cancelButton.setDisable(true);
        });

        downloadTask.setOnFailed(e -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("Błąd podczas pobierania: " + downloadTask.getException().getMessage());
            LogsUI.logError("Błąd podczas pobierania: " + downloadTask.getException().getMessage(), downloadTask.getException());
            System.err.println("Błąd podczas pobierania: " + downloadTask.getException().getMessage());
            downloadTask.getException().printStackTrace();
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
            cancelButton.setDisable(true);
        });

        // Akcja dla przycisku anulowania
        cancelButton.setOnAction(event -> {
            LogsUI.logInfo("Anulowanie pobierania");
            if (downloadTask.isRunning()) {
                downloadTask.cancel();
                statusLabel.textProperty().unbind();
                statusLabel.setText("Pobieranie anulowane.");
                progressBar.progressProperty().unbind();
                progressBar.setProgress(0);
                LogsUI.logInfo("Pobieranie anulowane");
            } else {
                LogsUI.logInfo("Task pobierania nie był aktywny");
            }
        });

        // Uruchomienie zadania w nowym wątku
        new Thread(downloadTask).start();
        cancelButton.setDisable(false);

        // Tworzenie layoutu dla nowego okna
        VBox downloadLayout = new VBox(10, new Label("Kolejka pobierania:"), queueListView, new Label("Aktualnie pobierany plik:"), currentFileLabel, progressBar, statusLabel, cancelButton);
        downloadLayout.setAlignment(Pos.CENTER);

        // Tworzenie sceny i ustawienie jej na stage
        Scene downloadScene = new Scene(downloadLayout, 400, 300);
        downloadStage.setScene(downloadScene);
        RootApplication.configureFontStyles(downloadScene);
        downloadStage.show();

        // Wymuszenie odświeżenia interfejsu użytkownika
        Platform.runLater(downloadStage::sizeToScene);
    }

    private static Task<Void> createDownloadTask(Map<String, String> filesToDownload, Label currentFileLabel, ListView<String> queueListView) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                while (!filesToDownload.isEmpty()) {
                    if (isCancelled()) {
                        break;
                    }

                    Optional<Map.Entry<String, String>> first = filesToDownload.entrySet().stream().findFirst();
                    if (first.isEmpty()) {
                        LogsUI.logError("Pusta lista filesToDownload");
                        System.out.println("Pusta lista filesToDownload");
                        return null;
                    }

                    String fileUrl = first.get().getKey();
                    String destinationFileName = first.get().getValue().replaceAll("[\\\\/:*?\"<>|]", "");

                    LogsUI.logInfo("Rozpoczynam pobieranie: " + destinationFileName);

                    // Zaktualizuj etykietę aktualnie pobieranego pliku
                    updateMessage("Pobieranie: " + destinationFileName);
                    Platform.runLater(() -> {
                        currentFileLabel.setText("Pobieranie: " + destinationFileName);
                        Tooltip tooltip = new Tooltip(fileUrl);
                        currentFileLabel.setTooltip(tooltip);
                    });

                    // Pobierz rozmiar pliku
                    long fileSize = getFileSize(fileUrl);

                    LogsUI.logInfo("Rozmiar pliku do pobrania: " + fileSize);

                    // Sprawdź dostępność miejsca na dysku
                    String downloadPath = ConfigurationService.getInstance().read().getDownloadPath() + File.separator;
                    Path destination = FileSystems.getDefault().getPath(downloadPath + destinationFileName);

                    // Obsługa wznowienia pobierania
                    AtomicLong downloadedBytes = new AtomicLong(0); // Użyj AtomicLong zamiast long
                    if (Files.exists(destination)) {
                        downloadedBytes.set(Files.size(destination));
                        if (downloadedBytes.get() >= fileSize) {
                            LogsUI.logInfo("Plik już został pobrany.");
                            updateMessage("Plik już został pobrany.");
                            removeFileFromDownloadList(fileUrl, destinationFileName, filesToDownload, queueListView);
                            continue;
                        }
                        LogsUI.logInfo("Plik ma już pobrane: " + downloadedBytes.get() + ", kontynuujemy pobieranie");
                    }

                    // Zmienna do przechowywania całkowitej liczby pobranych bajtów
                    AtomicLong totalBytesRead = new AtomicLong(downloadedBytes.get());

                    Thread transferThread = new Thread(() -> {
                        try (ReadableByteChannel sourceChannel = Channels.newChannel(openStreamWithRange(fileUrl, downloadedBytes.get())); FileChannel fileChannel = FileChannel.open(destination, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

                            fileChannel.position(downloadedBytes.get()); // Ustaw pozycję zapisu

                            // Wątek do monitorowania postępu
                            Thread progressThread = new Thread(() -> {
                                long lastUpdateTime = System.currentTimeMillis();
                                long lastBytesRead = downloadedBytes.get();

                                while (totalBytesRead.get() < fileSize && !Thread.currentThread().isInterrupted()) {
                                    try {
                                        Thread.sleep(1000); // Sprawdzaj postęp co sekundę

                                        long currentBytesRead = totalBytesRead.get();
                                        long currentTime = System.currentTimeMillis();

                                        // Oblicz prędkość pobierania
                                        double speed = (currentBytesRead - lastBytesRead) / ((currentTime - lastUpdateTime) / 1000.0);
                                        lastBytesRead = currentBytesRead;
                                        lastUpdateTime = currentTime;

                                        // Aktualizuj informacje o postępie
                                        Platform.runLater(() -> {
                                            updateMessage(formatBytes(currentBytesRead) + " z " + formatBytes(fileSize) + " (" + formatSpeed(speed) + ") - ETA: " + formatTime((fileSize - currentBytesRead) / speed));
                                            updateProgress(currentBytesRead, fileSize);
                                        });
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            });

                            // Uruchom wątek monitorujący postęp
                            progressThread.start();

                            // Użyj transferFrom do przesyłania danych
                            long transferredBytes;
                            while ((transferredBytes = fileChannel.transferFrom(sourceChannel, totalBytesRead.get(), READ_BUFFER_SIZE)) > 0) {
                                totalBytesRead.addAndGet(transferredBytes);
                                if (isCancelled()) {
                                    break;
                                }
                            }

                            // Zakończ wątek monitorujący postęp
                            progressThread.interrupt();
                            progressThread.join();

                            fileChannel.force(true); // Wymuś zapis na dysk

                            LogsUI.logInfo("Zapisano plik na dysk: " + destination);
                            System.out.println("Zapisano plik na dysk: " + destination);
                        } catch (IOException | InterruptedException e) {
                            updateMessage("Błąd podczas przesyłania: " + e.getMessage());
                            LogsUI.logError("Błąd podczas przesyłania: " + e.getMessage(), e);
                            System.err.println("Błąd podczas przesyłania: " + e.getMessage());
                        }
                    });

                    // Uruchomienie wątku
                    transferThread.start();
                    transferThread.join();

                    // Sprawdź, czy cały plik został pobrany
                    if (fileSize > 0 && !isCancelled() && totalBytesRead.get() < fileSize) {
                        updateMessage("Pobieranie przerwane: pobrano tylko " + totalBytesRead.get() + " bajtów z " + fileSize);
                        LogsUI.logError("Pobieranie przerwane: pobrano tylko " + totalBytesRead.get() + " bajtów z " + fileSize);
                        System.err.println("Pobieranie przerwane: pobrano tylko " + totalBytesRead.get() + " bajtów z " + fileSize);
                    } else {
                        removeFileFromDownloadList(fileUrl, destinationFileName, filesToDownload, queueListView);
                    }
                }
                return null;
            }
        };
    }

    private static void removeFileFromDownloadList(String fileUrl, String destinationFileName, Map<String, String> filesToDownload, ListView<String> queueListView) {
        filesToDownload.remove(fileUrl);
        Platform.runLater(() -> queueListView.getItems().remove(destinationFileName));
    }

    // Metoda do otwierania strumienia z obsługą zakresu (wznowienie pobierania)
    private static InputStream openStreamWithRange(String fileUrl, long startByte) throws IOException {
        URL url = URI.create(fileUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        if (startByte > 0) {
            connection.setRequestProperty("Range", "bytes=" + startByte + "-");
        }
        connection.connect();
        return new BufferedInputStream(connection.getInputStream(), READ_BUFFER_SIZE);
    }

    // Metoda do pobrania rozmiaru pliku za pomocą połączenia HTTP
    private static long getFileSize(String fileUrl) throws IOException {
        URL url = URI.create(fileUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0"); // Dodaj nagłówek User-Agent
        connection.connect();
        long fileSize = connection.getContentLengthLong();
        connection.disconnect();

        return fileSize;
    }

    // Metoda do formatowania bajtów na czytelne jednostki (KB, MB, GB)
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    // Metoda do formatowania prędkości pobierania (B/s, KB/s, MB/s)
    private static String formatSpeed(double speed) {
        if (speed < 1024) {
            return String.format("%.2f B/s", speed);
        } else if (speed < 1024 * 1024) {
            return String.format("%.2f KB/s", speed / 1024.0);
        } else {
            return String.format("%.2f MB/s", speed / (1024.0 * 1024.0));
        }
    }

    // Metoda do formatowania czasu (sekundy na HH:MM:SS)
    private static String formatTime(double seconds) {
        int hours = (int) (seconds / 3600);
        int minutes = (int) ((seconds % 3600) / 60);
        int secs = (int) (seconds % 60);
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
}