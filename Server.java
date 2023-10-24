package application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

public class Server {
    private static final int PORT = 1328;
    private List<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }

    public void start() {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Server started. Listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress().getHostAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                clientHandler.start();
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    private class ClientHandler extends Thread {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);

                String clientMessage;
                while ((clientMessage = in.readLine()) != null) {
                    System.out.println("Received from client: " + clientMessage);
                    String checksum = calculateChecksum(clientMessage);
                    String messageWithChecksum = clientMessage + "|" + checksum;
                    broadcastMessage(messageWithChecksum);
                }

                System.out.println("Client disconnected: " + clientSocket.getInetAddress().getHostAddress());
                clients.remove(this);
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Error handling client: " + e.getMessage());
            }
        }

        private void broadcastMessage(String message) {
            for (ClientHandler client : clients) {
                client.sendMessage(message);
            }
        }

        private void sendMessage(String message) {
            out.println(message);
        }

        private String calculateChecksum(String message) {
            CRC32 crc32 = new CRC32();
            crc32.update(message.getBytes());
            return Long.toHexString(crc32.getValue());
        }
    }
}