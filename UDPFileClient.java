import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPFileClient {
    private static final int SERVER_PORT = 5005;
    private static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) {
        try (DatagramSocket clientSocket = new DatagramSocket()) { 
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

            System.out.print("Ingrese el nombre del archivo a descargar: ");
            String fileName = console.readLine();

            InetAddress serverAddr = InetAddress.getByName("127.0.0.1");

            // Solicitar el archivo al puerto principal
            byte[] requestData = fileName.getBytes();
            DatagramPacket requestPacket = new DatagramPacket(
                    requestData, requestData.length, serverAddr, SERVER_PORT);
            clientSocket.send(requestPacket);

            // Recibir el puerto efímero asignado por el servidor
            byte[] buffer = new byte[100];
            DatagramPacket portPacket = new DatagramPacket(buffer, buffer.length);
            clientSocket.receive(portPacket);

            String portMsg = new String(portPacket.getData(), 0, portPacket.getLength()).trim();
            if (portMsg.startsWith("ERROR")) {
                System.out.println("El servidor respondió: " + portMsg);
                return;
            }
            if (!portMsg.startsWith("PORT:")) {
                System.out.println("Respuesta inesperada: " + portMsg);
                return;
            }

            // Extraer el puerto efímero
            int ephemeralPort = Integer.parseInt(portMsg.substring(5));
            System.out.println("Servidor indicó puerto efímero: " + ephemeralPort);

            // Enviar "HELLO" para iniciar la transferencia
            String hello = "HELLO from client";
            DatagramPacket helloPacket = new DatagramPacket(
                    hello.getBytes(), hello.length(), serverAddr, ephemeralPort);
            clientSocket.send(helloPacket);

            // Recibir el número total de paquetes
            byte[] totBuf = new byte[20];
            DatagramPacket totPacket = new DatagramPacket(totBuf, totBuf.length);
            clientSocket.receive(totPacket);
            String totStr = new String(totPacket.getData(), 0, totPacket.getLength()).trim();
            int totalPackets = Integer.parseInt(totStr);
            System.out.println("Total de fragmentos a recibir: " + totalPackets);

            // Preparar el archivo en disco
            File outFile = new File("descarga_" + fileName);
            try (RandomAccessFile raf = new RandomAccessFile(outFile, "rw")) {  
                
                while (true) {
                    byte[] recvBuf = new byte[BUFFER_SIZE + 20]; // buffer para cabecera + datos
                    DatagramPacket fragmentPacket = new DatagramPacket(recvBuf, recvBuf.length);
                    clientSocket.receive(fragmentPacket);

                    int length = fragmentPacket.getLength();
                    byte[] packetData = fragmentPacket.getData();

                    // Verificar si es "END"
                    String possibleEnd = new String(packetData, 0, length).trim();
                    if (possibleEnd.equals("END")) {
                        System.out.println("Fin de la transmisión recibido.");
                        break;
                    }

                    // Buscar el delimitador '|' para separar el número de fragmento
                    int sepIndex = -1;
                    for (int i = 0; i < length; i++) {
                        if (packetData[i] == '|') {
                            sepIndex = i;
                            break;
                        }
                    }
                    if (sepIndex == -1) {
                        System.out.println("Cabecera inválida, se ignora este fragmento.");
                        continue;
                    }

                    // Extraer el número de fragmento
                    String fragmentNumberStr = new String(packetData, 0, sepIndex);
                    int fragmentNumber;
                    try {
                        fragmentNumber = Integer.parseInt(fragmentNumberStr);
                    } catch (NumberFormatException e) {
                        System.out.println("Número de fragmento inválido: " + fragmentNumberStr);
                        continue;
                    }

                    // Extraer los datos del fragmento
                    int dataSize = length - (sepIndex + 1);
                    byte[] fileData = new byte[dataSize];
                    System.arraycopy(packetData, sepIndex + 1, fileData, 0, dataSize);

                    // Escribir el fragmento en su posición correcta
                    raf.seek((long) fragmentNumber * BUFFER_SIZE);  
                    raf.write(fileData);
                    
                    System.out.println("Escrito fragmento #" + fragmentNumber + " en disco.");

                    // Enviar ACK al servidor
                    String ackMsg = "ACK-" + fragmentNumber;
                    byte[] ackBytes = ackMsg.getBytes();
                    DatagramPacket ackPacket = new DatagramPacket(
                            ackBytes, ackBytes.length, serverAddr, ephemeralPort);
                    clientSocket.send(ackPacket);
                }

            }

            System.out.println("Archivo descargado exitosamente: " + outFile.getName());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
