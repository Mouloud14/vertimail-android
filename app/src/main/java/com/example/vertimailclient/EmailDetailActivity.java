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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class EmailDetailActivity extends AppCompatActivity {

    private static final String SERVER_BASE = "http://192.168.1.35:8080";

    private TextView subjectTxt, senderTxt, bodyTxt, dateTxt, tvAttachName;
    private Button btnDelete, btnReply, btnDownload;
    private ImageButton btnStar;
    private ImageView imgPreview;
    private LinearLayout layoutAttachBar;

    private String mailId, senderEmail, subjectStr, currentUser, currentFolder;
    private String attachmentName, attachmentHash;
    private boolean isImportant;
    private final OkHttpClient client = new OkHttpClient();

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

            if (attachmentName != null && !attachmentName.isEmpty()) {
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

        btnDelete.setOnClickListener(v -> deleteEmail());
        btnStar.setOnClickListener(v -> toggleImportant());
        btnDownload.setOnClickListener(v -> downloadFileToPhone(attachmentHash, attachmentName));
    }

    private void showAttachmentBar() {
        if (layoutAttachBar != null) {
            layoutAttachBar.setVisibility(View.VISIBLE);
            tvAttachName.setText(attachmentName);
            String lowerName = attachmentName.toLowerCase();
            if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif")) {
                downloadAndShowImage(attachmentHash);
            }
        }
    }

    private void downloadFileToPhone(String hash, String name) {
        Request request = new Request.Builder().url(SERVER_BASE + "/api/download?hash=" + hash).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) return;
                InputStream is = response.body().byteStream();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) != -1) os.write(buffer, 0, len);
                    }
                    runOnUiThread(() -> Toast.makeText(EmailDetailActivity.this, "Enregistré dans Downloads", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void downloadAndShowImage(String hash) {
        Request request = new Request.Builder().url(SERVER_BASE + "/api/download?hash=" + hash).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) return;
                Bitmap bitmap = BitmapFactory.decodeStream(response.body().byteStream());
                runOnUiThread(() -> { if (bitmap != null) { imgPreview.setImageBitmap(bitmap); imgPreview.setVisibility(View.VISIBLE); } });
            }
        });
    }

    private void updateStarIcon() {
        btnStar.setImageResource(isImportant ? android.R.drawable.star_on : android.R.drawable.star_off);
    }

    private void markAsRead() {
        String finalId = mailId;
        if (finalId != null && !finalId.endsWith(".json")) finalId += ".json";

        FormBody body = new FormBody.Builder()
                .add("username", currentUser)
                .add("folder", currentFolder)
                .add("id", finalId)
                .build();

        Request request = new Request.Builder().url(SERVER_BASE + "/api/mark-read").post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // LOG INDISPENSABLE POUR LE DEBUG
                android.util.Log.e("VERTMAIL", "Erreur mark-read: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    android.util.Log.d("VERTMAIL", "Mail marqué comme lu sur le serveur");
                    sendBroadcast(new Intent("com.vertimail.REFRESH_MAILS"));
                } else {
                    android.util.Log.e("VERTMAIL", "Réponse serveur négative: " + response.code());
                }
            }
        });
    }

    private void toggleImportant() {
        isImportant = !isImportant;
        updateStarIcon();
    }

    private void deleteEmail() {
        // FIABILISATION DE L'ID : On s'assure qu'il finit par .json
        String finalId = mailId;
        if (finalId != null && !finalId.endsWith(".json")) finalId += ".json";

        FormBody body = new FormBody.Builder()
                .add("username", currentUser)
                .add("folder", currentFolder)
                .add("id", finalId)
                .build();

        Request request = new Request.Builder()
                .url(SERVER_BASE + "/api/delete")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(EmailDetailActivity.this, "Erreur réseau", Toast.LENGTH_SHORT).show());
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(EmailDetailActivity.this, "Message supprimé.", Toast.LENGTH_SHORT).show();
                        sendBroadcast(new Intent("com.vertimail.REFRESH_MAILS"));
                        finish();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(EmailDetailActivity.this, "Le serveur a refusé la suppression", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}