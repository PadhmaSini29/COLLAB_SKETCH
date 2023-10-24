package application;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.zip.CRC32;

public class Clientt extends Application {
    private static final int PORT = 4320;

    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private GraphicsContext gc;
    private Color currentColor = Color.BLACK;
    private double currentLineWidth = 1.0;
    private double eraserLineWidth = 10.0;
    private boolean eraserMode = false;

    private double startX, startY;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();

        TextField addressField = new TextField();
        addressField.setPromptText("Server Address");
        root.setTop(addressField);

        Button connectButton = new Button("Connect");
        connectButton.setOnAction(e -> {
            String serverAddress = addressField.getText();
            if (!serverAddress.isEmpty()) {
                connectToServer(serverAddress);
                createWhiteboard(primaryStage);
                startServerListener(); // Start listening for updates from the server
            }
        });
        root.setCenter(connectButton);

        Scene scene = new Scene(root, 300, 200);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Whiteboard - Client");
        primaryStage.show();
    }

    private void connectToServer(String serverAddress) {
        try {
            clientSocket = new Socket(serverAddress, PORT);
            System.out.println("Connected to server.");

            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void createWhiteboard(Stage primaryStage) {
        primaryStage.setTitle("Whiteboard - Client");
        BorderPane root = new BorderPane();
        Canvas canvas = new Canvas(800, 600);
        gc = canvas.getGraphicsContext2D();
        root.setCenter(canvas);

        ColorPicker colorPicker = new ColorPicker();
        colorPicker.setOnAction(e -> {
            currentColor = colorPicker.getValue();
            sendToServer("COLOR " + getColorString(currentColor)); // Send the color information to the server
        });
        root.setTop(colorPicker);

        Button eraserButton = new Button("Eraser");
        eraserButton.setOnAction(e -> {
            currentColor = Color.WHITE; // Set the color to white for erasing
            currentLineWidth = eraserLineWidth; // Set the line width to eraser line width
            eraserMode = true;
            sendToServer("COLOR " + getColorString(currentColor)); // Send the color information to the server
        });
        root.setBottom(eraserButton);

        Button drawButton = new Button("Draw");
        drawButton.setOnAction(e -> {
            currentColor = colorPicker.getValue(); // Reset to the selected color for drawing
            currentLineWidth = 1.0; // Reset to default line width for drawing
            eraserMode = false;
            sendToServer("COLOR " + getColorString(currentColor)); // Send the color information to the server
        });
        root.setRight(drawButton);

        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> {
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            sendToServer("CLEAR"); // Send the clear signal to the server
        });
        root.setLeft(clearButton);

        Scene scene = new Scene(root, 800, 650);
        scene.setOnMousePressed(event -> {
            startX = event.getX();
            startY = event.getY();
        });
        scene.setOnMouseDragged(event -> {
            double endX = event.getX();
            double endY = event.getY();
            gc.setStroke(currentColor);
            gc.setLineWidth(eraserMode ? eraserLineWidth : currentLineWidth);
            gc.strokeLine(startX, startY, endX, endY);
            sendToServer("LINE " + startX + "," + startY + "," + endX + "," + endY); // Send the line information to the server
            startX = endX;
            startY = endY;
        });

        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            try {
                stop();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        // Close the resources
        if (in != null) {
            in.close();
        }
        if (out != null) {
            out.close();
        }
        if (clientSocket != null) {
            clientSocket.close();
        }
    }

    private String getColorString(Color color) {
        return color.toString();
    }

    private void startServerListener() {
        new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    processServerMessage(message);
                }
            } catch (IOException e) {
                System.out.println("Error reading from server: " + e.getMessage());
            }
        }).start();
    }

    private void processServerMessage(String message) {
        String[] parts = message.split(" ", 2);
        String command = parts[0];
        String data = parts[1];

        switch (command) {
            case "LINE":
                drawLine(data);
                break;
            case "COLOR":
                setColor(data);
                break;
            case "CLEAR":
                clearWhiteboard();
                break;
            default:
                System.out.println("Unknown command from server: " + command);
        }
    }

    private void drawLine(String lineData) {
        String[] coordinates = lineData.split(",");
        double startX = Double.parseDouble(coordinates[0]);
        double startY = Double.parseDouble(coordinates[1]);
        double endX = Double.parseDouble(coordinates[2]);
        double endY = Double.parseDouble(coordinates[3]);

        gc.setStroke(currentColor);
        gc.setLineWidth(currentLineWidth);
        gc.strokeLine(startX, startY, endX, endY);
    }

    private void setColor(String colorData) {
        Color color = Color.valueOf(colorData);
        currentColor = color;
    }

    private void clearWhiteboard() {
        gc.clearRect(0, 0, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());
    }

    private void sendToServer(String message) {
        String messageWithChecksum = addChecksumToMessage(message);
        out.println(messageWithChecksum);
    }

    private String addChecksumToMessage(String message) {
        CRC32 crc32 = new CRC32();
        crc32.update(message.getBytes());
        long checksum = crc32.getValue();
        return message + ";" + checksum;
    }
}