import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private DatagramPacket requestPacket;
    private DatagramSocket serverSocket;

    public ClientHandler(DatagramPacket requestPacket, DatagramSocket serverSocket) {
        this.requestPacket = requestPacket;
        this.serverSocket = serverSocket;
    }

    @Override
    public void run() {
        try {
            InetAddress clientAddress = requestPacket.getAddress();
            int clientPort = requestPacket.getPort();
            String fileName = new String(requestPacket.getData(), 0, requestPacket.getLength()).trim();

            File file = new File(fileName);
            if (!file.exists()) {
                serverSocket.send(new DatagramPacket("ERROR: Archivo no encontrado".getBytes(),
                        "ERROR: Archivo no encontrado".length(), clientAddress, clientPort));
                return;
            }

            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                byte[] buffer = new byte[Config.BUFFER_SIZE];
                int bytesRead;
                int fragmentNumber = 0;

                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    String header = fragmentNumber + "|";
                    byte[] headerBytes = header.getBytes();
                    byte[] dataPacket = new byte[headerBytes.length + bytesRead];

                    System.arraycopy(headerBytes, 0, dataPacket, 0, headerBytes.length);
                    System.arraycopy(buffer, 0, dataPacket, headerBytes.length, bytesRead);

                    serverSocket.send(new DatagramPacket(dataPacket, dataPacket.length, clientAddress, clientPort));

                    fragmentNumber++;
                }

                serverSocket.send(new DatagramPacket("END".getBytes(), 3, clientAddress, clientPort));
                System.out.println("Archivo '" + fileName + "' enviado a " + clientAddress + ":" + clientPort);

            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
