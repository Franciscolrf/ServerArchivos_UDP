import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.InetAddress;

public class UDPFileServer {
    private static final int SERVER_PORT = 5005;

    public static void main(String[] args) {
        // Creamos un pool de hilos que crezca según la demanda
        ExecutorService executor = Executors.newCachedThreadPool();

        try (DatagramSocket mainSocket = new DatagramSocket(SERVER_PORT)) {
            System.out.println("Servidor UDP escuchando en el puerto " + SERVER_PORT);

            while (true) {
                // Esperar solicitud (nombre de archivo) en el socket principal
                byte[] buffer = new byte[1024];
                DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                mainSocket.receive(requestPacket);

                InetAddress clientAddress = requestPacket.getAddress();
                int clientPort = requestPacket.getPort();

                String fileName = new String(requestPacket.getData(), 0, requestPacket.getLength()).trim();
                System.out.println(" Solicitud de archivo '" + fileName + "' desde "
                                   + clientAddress + ":" + clientPort);

                // Verificar si el archivo existe
                File file = new File(fileName);
                if (!file.exists()) {
                    String errorMsg = "ERROR: Archivo no encontrado";
                    DatagramPacket errorPacket = new DatagramPacket(
                            errorMsg.getBytes(), errorMsg.length(),
                            clientAddress, clientPort
                    );
                    mainSocket.send(errorPacket);
                    System.out.println(" Archivo no encontrado: " + fileName);
                    continue; // Volver a esperar otra solicitud
                }

                // Crear un socket en un puerto efímero para atender a este cliente
                DatagramSocket ephemeralSocket = new DatagramSocket(0); // puerto aleatorio
                int ephemeralPort = ephemeralSocket.getLocalPort();

                // Notificar al cliente el puerto efímero
                String portMsg = "PORT:" + ephemeralPort;
                DatagramPacket portPacket = new DatagramPacket(
                        portMsg.getBytes(), portMsg.length(),
                        clientAddress, clientPort
                );
                mainSocket.send(portPacket);
                System.out.println(" Archivo encontrado. Se usará el puerto " + ephemeralPort
                                   + " para la transferencia.");

                // Crear y lanzar el handler en un hilo aparte
                executor.execute(new ClientHandler(ephemeralSocket, file, clientAddress));
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Si cierras el servidor, no olvides apagar el Executor
            // executor.shutdown();
        }
    }
}
