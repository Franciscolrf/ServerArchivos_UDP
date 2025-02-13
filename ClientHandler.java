import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;

public class ClientHandler implements Runnable {
    private static final int PACKET_SIZE = 4096;
    private static final int TIMEOUT_MS = 3000; // Esperar 3s por ACK

    private DatagramSocket socket;
    private File file;
    private InetAddress clientAddress;

    public ClientHandler(DatagramSocket socket, File file, InetAddress clientAddress) {
        this.socket = socket;
        this.file = file;
        this.clientAddress = clientAddress;
    }

    @Override
    public void run() {
        try {
            // 1) Leer todo el archivo (para simplificar la demo).
            // Para archivos gigantes, se recomienda un bucle de lectura parcial.
            byte[] fileData = Files.readAllBytes(file.toPath());
            int totalPackets = (int) Math.ceil((double) fileData.length / PACKET_SIZE);

            // 2) Esperar que el cliente nos envíe un "HELLO" (o algo) para obtener su puerto
            socket.setSoTimeout(5000); // 5s para que el cliente nos mande HELLO
            byte[] helloBuffer = new byte[100];
            DatagramPacket helloPacket = new DatagramPacket(helloBuffer, helloBuffer.length);
            socket.receive(helloPacket);

            String helloMsg = new String(helloPacket.getData(), 0, helloPacket.getLength()).trim();
            if (!helloMsg.startsWith("HELLO")) {
                System.out.println("⚠️ No se recibió HELLO, sino: " + helloMsg);
                return;
            }
            int clientPort = helloPacket.getPort();
            System.out.println("🤝 Recibido HELLO del cliente " + clientAddress + ":" + clientPort);

            // 3) Enviar la cantidad total de fragmentos
            String totalPacketsStr = String.valueOf(totalPackets);
            DatagramPacket totalCountPacket = new DatagramPacket(
                    totalPacketsStr.getBytes(), totalPacketsStr.length(),
                    clientAddress, clientPort
            );
            socket.send(totalCountPacket);
            System.out.println("📤 Enviando total de paquetes: " + totalPackets);

            // 4) Enviar cada fragmento con "stop-and-wait" (esperar ACK antes de siguiente)
            int offset = 0;
            int packetNumber = 0;

            while (offset < fileData.length) {
                int length = Math.min(PACKET_SIZE, fileData.length - offset);

                // Cabecera textual: "00000|"
                String header = String.format("%05d|", packetNumber);
                byte[] headerBytes = header.getBytes();

                // Combinar cabecera + bytes del archivo
                byte[] packetData = new byte[headerBytes.length + length];
                System.arraycopy(headerBytes, 0, packetData, 0, headerBytes.length);
                System.arraycopy(fileData, offset, packetData, headerBytes.length, length);

                // Enviar fragmento
                DatagramPacket sendPacket = new DatagramPacket(
                        packetData, packetData.length,
                        clientAddress, clientPort
                );
                socket.send(sendPacket);
                System.out.println("📤 Enviado fragmento #" + packetNumber + " (" + length + " bytes)");

                // Esperar ACK
                byte[] ackBuffer = new byte[20];
                DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                socket.setSoTimeout(TIMEOUT_MS);

                try {
                    socket.receive(ackPacket);
                    String ackResponse = new String(ackPacket.getData(), 0, ackPacket.getLength()).trim();
                    if (!ackResponse.equals("ACK-" + packetNumber)) {
                        System.out.println("⚠️ ACK incorrecto: " + ackResponse
                                           + " (se reenvía fragmento #" + packetNumber + ")");
                        // No avanzamos offset ni packetNumber
                        continue;
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("⌛ Timeout esperando ACK del fragmento #" + packetNumber
                                       + ", reintentando...");
                    // Reenviar el mismo fragmento en la siguiente iteración
                    continue;
                }

                // Si ACK es correcto, avanzamos al siguiente fragmento
                offset += length;
                packetNumber++;
            }

            // 5) Enviar "END" para indicar fin de transmisión
            byte[] endBytes = "END".getBytes();
            DatagramPacket endPacket = new DatagramPacket(endBytes, endBytes.length,
                                                          clientAddress, clientPort);
            socket.send(endPacket);
            System.out.println("✅ Transmisión completada para: " + file.getName());

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Cerrar el socket efímero al terminar con este cliente
            socket.close();
        }
    }
}
