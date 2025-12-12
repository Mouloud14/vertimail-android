import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpClientTest {
    public static void main(String[] args) {
        try {
            System.out.println("--- Démarrage du client de test local ---");
            DatagramSocket clientSocket = new DatagramSocket();

            // On cible le serveur sur la machine locale (localhost)
            InetAddress address = InetAddress.getByName("127.0.0.1");

            String message = "testeur\nSujet du test\nCeci est un test local.";
            byte[] buffer = message.getBytes();

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, 9999);

            // Envoi du message
            clientSocket.send(packet);
            System.out.println("Message de test local envoyé !");

            clientSocket.close();
            System.out.println("--- Client de test terminé ---");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}