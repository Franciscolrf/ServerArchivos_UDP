import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPFileServer {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(Config.THREAD_POOL_SIZE);
        try (DatagramSocket serverSocket = new DatagramSocket(Config.SERVER_PORT)){
            System.out.println("Servidor UDP escuchando en el puerto " + Config.SERVER_PORT);
            while (true){
                byte[] buffer = new byte[Config.BUFFER_SIZE];
                DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(requestPacket);
                executor.execute(new ClientHandler(requestPacket, serverSocket));
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }
}