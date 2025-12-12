package com.example.vertimailclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class EmailDetailActivity extends AppCompatActivity {

    // --- LA CORRECTION EST ICI ---
    private static final String SERVER_BASE = "http://192.168.1.33:8080";
    // --------------------------------

    private TextView subjectTxt, senderTxt, bodyTxt, dateTxt;
    private Button btnDelete, btnReply;

    private String mailId, senderEmail, subjectStr, currentUser, currentFolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_detail);

        subjectTxt = findViewById(R.id.detail_subject);
        senderTxt = findViewById(R.id.detail_sender);
        bodyTxt = findViewById(R.id.detail_body);
        dateTxt = findViewById(R.id.detail_date);
        btnDelete = findViewById(R.id.btn_delete);
        btnReply = findViewById(R.id.btn_reply);

        Intent intent = getIntent();
        if (intent != null) {
            currentUser = intent.getStringExtra("CURRENT_USER");
            currentFolder = intent.getStringExtra("CURRENT_FOLDER");
            mailId = intent.getStringExtra("ID_KEY");
            subjectStr = intent.getStringExtra("SUJET_KEY");
            senderEmail = intent.getStringExtra("SENDER_KEY");
            String body = intent.getStringExtra("BODY_KEY");
            String date = intent.getStringExtra("DATE_KEY");

            subjectTxt.setText(subjectStr);
            senderTxt.setText("De: " + senderEmail);
            bodyTxt.setText(body);

            if(date != null && date.length() > 16) {
                date = date.substring(0, 16).replace("T", " à ");
            }
            if(dateTxt != null) dateTxt.setText(date);
        }

        btnReply.setOnClickListener(v -> {
            Intent replyIntent = new Intent(EmailDetailActivity.this, SendAuthActivity.class);
            replyIntent.putExtra("CURRENT_USER", currentUser);
            replyIntent.putExtra("REPLY_TO", senderEmail);
            replyIntent.putExtra("REPLY_SUBJECT", "Re: " + subjectStr);
            startActivity(replyIntent);
        });

        btnDelete.setOnClickListener(v -> {
            if (mailId != null && !mailId.isEmpty() && currentUser != null && currentFolder != null) {
                deleteEmail(currentUser, currentFolder, mailId);
            } else {
                Toast.makeText(this, "Erreur: Informations de suppression manquantes.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteEmail(String user, String folder, String filename) {
        new Thread(() -> {
            try {
                String urlStr = SERVER_BASE + "/delete";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);

                String params = "username=" + URLEncoder.encode(user, "UTF-8")
                        + "&folder=" + URLEncoder.encode(folder, "UTF-8")
                        + "&filename=" + URLEncoder.encode(filename, "UTF-8");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(params.getBytes());
                }

                int responseCode = conn.getResponseCode();

                runOnUiThread(() -> {
                    if (responseCode == 302 || responseCode == 200) { // 302 = Redirection (succès pour Vert.x)
                        Toast.makeText(this, "Message déplacé vers la corbeille.", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Échec de la suppression (Code " + responseCode + ")", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(EmailDetailActivity.this, "Erreur réseau lors de la suppression.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}