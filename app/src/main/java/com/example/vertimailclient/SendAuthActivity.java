package com.example.vertimailclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;

public class SendAuthActivity extends AppCompatActivity {

    // --- PASSAGE EN PRODUCTION ---
    private static final String SERVER_URL = "https://vertimail.onrender.com/api/send";
    // ---------------------------

    // L'envoi anonyme (UDP) ne peut pas fonctionner en ligne, on le désactive pour la version de production.
    private static final String UDP_IP = "127.0.0.1"; // Adresse non joignable depuis l'extérieur
    private static final int UDP_PORT = 9999;

    String currentUser;
    EditText edtDest, edtSujet, edtMsg;
    Button btnSend, btnAnonyme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_auth);

        currentUser = getIntent().getStringExtra("CURRENT_USER");

        edtDest = findViewById(R.id.authDest);
        edtSujet = findViewById(R.id.authSujet);
        edtMsg = findViewById(R.id.authMsg);
        btnSend = findViewById(R.id.btnAuthSend);
        btnAnonyme = findViewById(R.id.btnSendAnonyme);

        TextView txtWelcome = findViewById(R.id.txtWelcome);
        if (txtWelcome != null) {
            txtWelcome.setText("Nouvel email de: " + currentUser);
        }

        Intent intent = getIntent();
        String replyTo = intent.getStringExtra("REPLY_TO");
        String replySubject = intent.getStringExtra("REPLY_SUBJECT");

        if (replyTo != null && !replyTo.isEmpty()) {
            edtDest.setText(replyTo);
            edtDest.setEnabled(false);
            edtSujet.setText(replySubject);
            edtMsg.requestFocus();
            if (txtWelcome != null) {
                txtWelcome.setText("Répondre à: " + replyTo);
            }
        }

        if (btnSend != null) {
            btnSend.setOnClickListener(v -> sendMailHttp());
        }

        // On désactive le bouton d'envoi anonyme pour la version de production
        if (btnAnonyme != null) {
            btnAnonyme.setVisibility(View.GONE);
        }
    }

    private void sendMailHttp() {
        String dest = edtDest.getText().toString();
        String sujet = edtSujet.getText().toString();
        String content = edtMsg.getText().toString();

        if (dest.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Le destinataire et le message sont requis.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Envoi en cours...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                String params = "sender=" + URLEncoder.encode(currentUser, "UTF-8")
                        + "&recipient=" + URLEncoder.encode(dest, "UTF-8")
                        + "&subject=" + URLEncoder.encode(sujet, "UTF-8")
                        + "&content=" + URLEncoder.encode(content, "UTF-8");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(params.getBytes());
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Message envoyé avec succès.", Toast.LENGTH_LONG).show();
                        finish();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Erreur du serveur (Code " + code + ")", Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Erreur de connexion : " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void sendMailUdp() {
        // Cette méthode ne sera plus appelée car le bouton est masqué.
    }
}
