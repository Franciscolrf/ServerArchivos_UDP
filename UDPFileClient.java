import java.io.*;
import java.net.*;
import java.util.HashMap;

public class UDPFileClient {
    public static void main(String[] args) {
        try (DatagramSocket clientSocket = new DatagramSocket()) {
            InetAddress serverAddress = InetAddress.getByName(Config.SERVER_IP);
            
            // Pedir archivo al usuario
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("Ingrese el nombre del archivo a descargar: ");
            String fileName = reader.readLine();

            // Enviar solicitud al servidor
            byte[] requestData = fileName.getBytes();
            DatagramPacket requestPacket = new DatagramPacket(requestData, requestData.length, serverAddress, Config.SERVER_PORT);
            clientSocket.send(requestPacket);

            // Recibir confirmación del servidor
            byte[] buffer = new byte[Config.BUFFER_SIZE];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            clientSocket.receive(responsePacket);

            String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (response.equals("ERROR: Archivo no encontrado")) {
                System.out.println("El archivo solicitado no existe en el servidor.");
                return;
            }

            // Mapa para almacenar los fragmentos en orden
            HashMap<Integer, byte[]> fragmentBuffer = new HashMap<>();

            // Recibir archivo
            File file = new File("descarga_" + fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                while (true) {
                    clientSocket.receive(responsePacket);
                    
                    // Revisar si es el fin del archivo
                    String checkEnd = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    if (checkEnd.equals("END")) {
                        break;  // Fin de la transferencia
                    }

                    // Separar número de fragmento y datos binarios
                    byte[] receivedData = responsePacket.getData();
                    int separatorIndex = -1;

                    // Buscar el separador "|"
                    for (int i = 0; i < receivedData.length; i++) {
                        if (receivedData[i] == '|') {
                            separatorIndex = i;
                            break;
                        }
                    }

                    if (separatorIndex == -1) {
                        System.out.println("Error: No se encontró separador en el paquete recibido.");
                        continue;
                    }

                    // Extraer número de fragmento
                    int fragmentNumber = Integer.parseInt(new String(receivedData, 0, separatorIndex));

                    // Extraer los datos binarios del fragmento
                    byte[] fileData = new byte[responsePacket.getLength() - separatorIndex - 1];
                    System.arraycopy(receivedData, separatorIndex + 1, fileData, 0, fileData.length);

                    // Guardar fragmento en el buffer
                    fragmentBuffer.put(fragmentNumber, fileData);
                }

                // Escribir los fragmentos en el orden correcto
                int fragmentIndex = 0;
                while (fragmentBuffer.containsKey(fragmentIndex)) {
                    fos.write(fragmentBuffer.get(fragmentIndex));
                    fragmentIndex++;
                }

                System.out.println("Archivo descargado exitosamente: " + file.getName());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
