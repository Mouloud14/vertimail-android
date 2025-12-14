package com.example.vertimailclient;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class MainActivity extends AppCompatActivity {

    // --- RETOUR EN MODE DÉVELOPPEMENT LOCAL ---
    private static final String UDP_IP = "192.168.1.33";
    private static final int UDP_PORT = 9999;
    // ------------------------------------------

    EditText inputDest, inputSujet, inputMsg;
    Button btnEnvoyer;
    TextView txtResultat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputDest = findViewById(R.id.inputDestinataire);
        inputSujet = findViewById(R.id.inputSujet);
        inputMsg = findViewById(R.id.inputMessage);
        btnEnvoyer = findViewById(R.id.btnEnvoyer);
        txtResultat = findViewById(R.id.txtResultat);

        btnEnvoyer.setOnClickListener(v -> sendUdpMessage());
    }

    private void sendUdpMessage() {
        String destinataire = inputDest.getText().toString().trim();
        String sujet = inputSujet.getText().toString().trim();
        String contenu = inputMsg.getText().toString().trim();

        if (destinataire.isEmpty() || contenu.isEmpty()) {
            Toast.makeText(this, "Veuillez renseigner un destinataire et un message.", Toast.LENGTH_SHORT).show();
            return;
        }

        txtResultat.setText("Envoi en cours...");

        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(5000); // Délai d'attente de 5 secondes

                String messageFinal = destinataire + "\n" + sujet + "\n" + contenu;
                byte[] buffer = messageFinal.getBytes();
                InetAddress address = InetAddress.getByName(UDP_IP);

                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, UDP_PORT);
                socket.send(packet);

                // Attente de la réponse du serveur
                byte[] bufferRecv = new byte[1024];
                DatagramPacket packetRecv = new DatagramPacket(bufferRecv, bufferRecv.length);
                socket.receive(packetRecv);

                String reponseServeur = new String(packetRecv.getData(), 0, packetRecv.getLength());

                runOnUiThread(() -> {
                    txtResultat.setText("Réponse du serveur : " + reponseServeur);
                    Toast.makeText(MainActivity.this, "Message envoyé avec succès.", Toast.LENGTH_SHORT).show();
                });

            } catch (SocketTimeoutException e) {
                runOnUiThread(() -> txtResultat.setText("Le serveur n'a pas répondu dans le temps imparti."));
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> txtResultat.setText("Erreur d'envoi : " + e.getMessage()));
            }
        }).start();
    }
}
