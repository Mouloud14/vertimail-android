import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ServerUDP {
    public static void main(String[] args) throws Exception {
        // On écoute sur le port 4444
        DatagramSocket socket = new DatagramSocket(4444);
        byte[] buffer = new byte[1024];

        System.out.println("--- SERVEUR UDP PRÊT (IP 192.168.1.33) ---");
        System.out.println("En attente de messages du téléphone...");

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet); // Le programme s'arrête ici et attend

            String msg = new String(packet.getData(), 0, packet.getLength());
            System.out.println("REÇU DU TÉLÉPHONE : " + msg);
        }
    }
}