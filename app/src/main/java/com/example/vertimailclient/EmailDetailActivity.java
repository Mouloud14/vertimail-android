package com.example.vertimailclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class EmailDetailActivity extends AppCompatActivity {

    private static final String SERVER_BASE = "http://192.168.1.33:8080";

    private TextView subjectTxt, senderTxt, bodyTxt, dateTxt;
    private Button btnDelete, btnReply;
    private ImageButton btnStar;

    private String mailId, senderEmail, subjectStr, currentUser, currentFolder;
    private boolean isImportant;

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
        btnStar = findViewById(R.id.btn_star);

        Intent intent = getIntent();
        if (intent != null) {
            currentUser = intent.getStringExtra("CURRENT_USER");
            currentFolder = intent.getStringExtra("CURRENT_FOLDER");
            mailId = intent.getStringExtra("ID_KEY");
            subjectStr = intent.getStringExtra("SUJET_KEY");
            senderEmail = intent.getStringExtra("SENDER_KEY");
            String body = intent.getStringExtra("BODY_KEY");
            String date = intent.getStringExtra("DATE_KEY");
            isImportant = intent.getBooleanExtra("IMPORTANT_KEY", false);

            subjectTxt.setText(subjectStr);
            senderTxt.setText("De: " + senderEmail);
            bodyTxt.setText(body);
            updateStarIcon();

            if(date != null && date.length() > 16) {
                date = date.substring(0, 16).replace("T", " à ");
            }
            if(dateTxt != null) dateTxt.setText(date);

            markAsRead();
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
            }
        });

        btnStar.setOnClickListener(v -> toggleImportant());
    }

    private void updateStarIcon() {
        // CORRECTION : Ajout du ".R." manquant
        btnStar.setImageResource(isImportant ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
    }

    private void markAsRead() {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_BASE + "/api/read");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                String params = "username=" + URLEncoder.encode(currentUser, "UTF-8")
                        + "&folder=" + URLEncoder.encode(currentFolder, "UTF-8")
                        + "&filename=" + URLEncoder.encode(mailId, "UTF-8");
                try (OutputStream os = conn.getOutputStream()) { os.write(params.getBytes()); }
                conn.getResponseCode();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void toggleImportant() {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_BASE + "/api/important");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                
                isImportant = !isImportant;
                String params = "username=" + URLEncoder.encode(currentUser, "UTF-8")
                        + "&folder=" + URLEncoder.encode(currentFolder, "UTF-8")
                        + "&filename=" + URLEncoder.encode(mailId, "UTF-8")
                        + "&important=" + isImportant;

                try (OutputStream os = conn.getOutputStream()) { os.write(params.getBytes()); }
                
                if (conn.getResponseCode() == 200) {
                    runOnUiThread(this::updateStarIcon);
                } else {
                    isImportant = !isImportant;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void deleteEmail(String user, String folder, String filename) {
        new Thread(() -> {
            try {
                String urlStr = SERVER_BASE + "/delete";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                String params = "username=" + URLEncoder.encode(user, "UTF-8")
                        + "&folder=" + URLEncoder.encode(folder, "UTF-8")
                        + "&filename=" + URLEncoder.encode(filename, "UTF-8");
                try (OutputStream os = conn.getOutputStream()) { os.write(params.getBytes()); }
                if (conn.getResponseCode() == 200 || conn.getResponseCode() == 302) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Message supprimé.", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(EmailDetailActivity.this, "Erreur réseau lors de la suppression.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}