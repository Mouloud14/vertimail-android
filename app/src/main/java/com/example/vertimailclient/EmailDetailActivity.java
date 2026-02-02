package com.example.vertimailclient;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class EmailDetailActivity extends AppCompatActivity {

    private static final String SERVER_BASE = "http://192.168.1.40:8080";

    private TextView subjectTxt, senderTxt, bodyTxt, dateTxt, tvAttachName;
    private Button btnDelete, btnReply, btnDownload;
    private ImageButton btnStar;
    private ImageView imgPreview;
    private LinearLayout layoutAttachBar;

    private String mailId, senderEmail, subjectStr, currentUser, currentFolder;
    private String attachmentName, attachmentHash;
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
        imgPreview = findViewById(R.id.imgAttachmentPreview);
        
        layoutAttachBar = findViewById(R.id.layoutAttachmentBar);
        tvAttachName = findViewById(R.id.tvAttachmentName);
        btnDownload = findViewById(R.id.btnDownloadFile);

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
            
            attachmentName = intent.getStringExtra("ATTACH_NAME");
            attachmentHash = intent.getStringExtra("ATTACH_HASH");

            subjectTxt.setText(subjectStr);
            senderTxt.setText("De: " + senderEmail);
            bodyTxt.setText(body);
            updateStarIcon();

            if(date != null && date.length() > 16) {
                date = date.substring(0, 16).replace("T", " à ");
            }
            if(dateTxt != null) dateTxt.setText(date);

            // Gestion de la pièce jointe
            if (attachmentName != null && !attachmentName.isEmpty() && attachmentHash != null) {
                showAttachmentBar();
            }

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
        
        btnDownload.setOnClickListener(v -> downloadFileToPhone(attachmentHash, attachmentName));
    }

    private void showAttachmentBar() {
        layoutAttachBar.setVisibility(View.VISIBLE);
        tvAttachName.setText(attachmentName);
        
        String lowerName = attachmentName.toLowerCase();
        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif")) {
            downloadAndShowImage(attachmentHash);
        }
    }

    private void downloadFileToPhone(String hash, String name) {
        Toast.makeText(this, "Téléchargement de " + name + "...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_BASE + "/api/download?hash=" + hash);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                if (conn.getResponseCode() != 200) {
                    runOnUiThread(() -> Toast.makeText(this, "Erreur serveur lors du téléchargement", Toast.LENGTH_SHORT).show());
                    return;
                }

                InputStream is = conn.getInputStream();
                
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                        }
                    }
                    runOnUiThread(() -> Toast.makeText(this, "Fichier enregistré dans Downloads !", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Erreur de téléchargement : " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void downloadAndShowImage(String hash) {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_BASE + "/api/download?hash=" + hash);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();
                InputStream is = conn.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                runOnUiThread(() -> {
                    if (bitmap != null) {
                        imgPreview.setImageBitmap(bitmap);
                        imgPreview.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void updateStarIcon() {
        btnStar.setImageResource(isImportant ? android.R.drawable.star_on : android.R.drawable.star_off);
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
            } catch (Exception e) { e.printStackTrace(); }
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