package org.example;

import com.fazecast.jSerialComm.SerialPort;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

public class SerialReader {

    public static void main(String[] args) {



        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports.length == 0) {
            System.out.println("No serial ports found.");
            return;
        }

        System.out.println("Available Ports:");
        for (int i = 0; i < ports.length; i++) {
            System.out.printf("%d: %s%n", i, ports[i].getSystemPortName());
        }


        String portNameToUse = "COM3";

        SerialPort arduinoPort = SerialPort.getCommPort(portNameToUse);


        arduinoPort.setBaudRate(9600);



        if (!arduinoPort.openPort()) {
            System.out.println("I can not open this port: " + portNameToUse);
            return;
        }
        System.out.println("Port is open: " + arduinoPort.getSystemPortName());

        System.out.println("Waiting for device to initialize...");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Device should be ready. Starting to listen...");
        arduinoPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);



        HttpClient httpClient = HttpClient.newHttpClient();
        InputStream in = arduinoPort.getInputStream();
        Scanner scanner = new Scanner(in);

        try {


            while (scanner.hasNextLine()) {

                String rawLine = scanner.nextLine().trim();
                System.out.println("--> Line read from serial: '" + rawLine + "'");

                if (rawLine.isEmpty()) {
                    continue;
                }

                if (rawLine.startsWith("{") && rawLine.endsWith("}")) {
                    String jsonLine = rawLine;
                    System.out.println("--> Line is a valid JSON object. Attempting to send HTTP POST...");


                    try {

                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:8080/data"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(jsonLine))
                                .build();

                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        System.out.println("--> SUCCESS! Server response: " + response.statusCode());
                    } catch (Exception e) {
                        System.err.println("--> HTTP REQUEST FAILED! Reason: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                        System.err.println("--> CHECK YOUR FIREWALL! It might be blocking Java from accessing the network.");
                    }
                }
                else {

                        System.out.println("Skipping malformed or incomplete line: " + rawLine);
                    }
                }

        } finally {

            scanner.close();
            arduinoPort.closePort();
            System.out.println("Port is closed.");
        }
    }
}