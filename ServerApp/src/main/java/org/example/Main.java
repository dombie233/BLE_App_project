package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fazecast.jSerialComm.SerialPort;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import java.io.InputStream;
import java.util.Map;
import java.util.Scanner;

public class Main {

    // Zmienna, którą oba wątki (webowy i serialowy) będą współdzieliły.
    private static volatile SensorData lastReceivedData = null;

    // ObjectMapper do konwersji JSON na obiekt Javy. Tworzymy go raz.
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public static void main(String[] args) {
        // Uruchom nasłuchiwanie na porcie szeregowym w osobnym wątku.
        startSerialReaderThread();

        // Uruchom serwer Javalin (to dzieje się w wątku głównym).
        var app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson());
        }).start(8080);

        app.get("/", ctx -> ctx.result("Hello World"));

        // Ten endpoint POST nie jest już potrzebny, bo dane aktualizujemy bezpośrednio,
        // ale można go zostawić do testów np. z Postmana.
        // app.post("/data", ...);

        // Endpoint GET działa tak jak wcześniej - odczytuje współdzieloną zmienną.
        app.get("/data", ctx -> {
            if (lastReceivedData != null) {
                ctx.json(lastReceivedData);
            } else {
                ctx.status(404);
                ctx.json(Map.of("status", "Not found", "message", "Server is running, but no data has been received from the serial port yet."));
            }
        });

        System.out.println("Javalin server started. Serial port reader is running in the background.");
    }

    private static void startSerialReaderThread() {
        Thread serialThread = new Thread(() -> {
            String portNameToUse = "COM3"; // <--- ZMIEŃ PORT, JEŚLI TRZEBA
            SerialPort arduinoPort = SerialPort.getCommPort(portNameToUse);
            arduinoPort.setBaudRate(9600);

            if (!arduinoPort.openPort()) {
                System.err.println("FATAL: Could not open serial port: " + portNameToUse);
                return;
            }
            System.out.println("Serial port " + portNameToUse + " opened successfully.");

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Nie ustawiamy już timeoutów tutaj, będziemy czytać w pętli
            InputStream in = arduinoPort.getInputStream();

            // === ZMIANA: CZYTANIE BAJT PO BAJCIE ZAMIAST SCANNERA ===
            try {
                // Bufor do składania odczytanych bajtów w linię
                StringBuilder lineBuilder = new StringBuilder();

                while (true) {
                    if (!arduinoPort.isOpen()) {
                        System.err.println("Serial port has been closed. Exiting reader thread.");
                        break;
                    }

                    // Sprawdź, czy są dostępne dane do odczytu
                    if (in.available() > 0) {
                        // Odczytaj jeden bajt
                        char readChar = (char) in.read();

                        // Jeśli bajt to znak nowej linii, to znaczy, że mamy kompletną wiadomość
                        if (readChar == '\n') {
                            String rawLine = lineBuilder.toString().trim(); // Pobierz złożoną linię

                            // Logujemy surową linię, aby zobaczyć, co dokładnie dostajemy
                            System.out.println("[RAW LINE RECEIVED]: " + rawLine);

                            if (rawLine.startsWith("{") && rawLine.endsWith("}")) {
                                try {
                                    SensorData data = jsonMapper.readValue(rawLine, SensorData.class);
                                    lastReceivedData = data;
                                    System.out.println("Updated sensor data from serial: " + data);
                                } catch (Exception e) {
                                    System.err.println("Failed to parse JSON: " + rawLine + " | Error: " + e.getMessage());
                                }
                            }

                            // Zresetuj bufor, aby był gotowy na następną linię
                            lineBuilder = new StringBuilder();
                        } else {
                            // Jeśli to nie jest znak nowej linii, dodaj znak do naszego bufora
                            lineBuilder.append(readChar);
                        }
                    } else {
                        // Jeśli nie ma danych, poczekaj chwilę, aby nie obciążać procesora
                        Thread.sleep(20);
                    }
                }
            } catch (Exception e) {
                System.err.println("An unexpected error occurred in the serial reader thread: " + e.getMessage());
            } finally {
                // Ten kod wykona się, jeśli pętla zostanie przerwana
                try {
                    in.close();
                    arduinoPort.closePort();
                    System.out.println("Serial port " + portNameToUse + " has been properly closed.");
                } catch (Exception e) {
                    // ignore
                }
            }
        });

        serialThread.setDaemon(true);
        serialThread.start();
    }

    // Klasa Response i SensorData bez zmian
    static class Response {
        public String status;
        public Response(String status) { this.status = status; }
    }
}