package com.example.vertimailclient;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;

public class SendAuthActivity extends AppCompatActivity {

    private static final String SERVER_URL = "http://192.168.1.37:8080/api/send";

    String currentUser;
    EditText edtDest, edtSujet, edtMsg;
    Button btnSend, btnAttach;
    LinearLayout layoutAttachment;
    TextView tvAttachmentName;
    ImageButton btnRemoveAttachment;

    Uri fileUri;
    String fileName;
    String fileHash;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    fileUri = result.getData().getData();
                    fileName = getFileName(fileUri);
                    calculateHashAndShowUI(fileUri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_auth);

        currentUser = getIntent().getStringExtra("CURRENT_USER");

        edtDest = findViewById(R.id.authDest);
        edtSujet = findViewById(R.id.authSujet);
        edtMsg = findViewById(R.id.authMsg);
        btnSend = findViewById(R.id.btnAuthSend);
        btnAttach = findViewById(R.id.btnAttachFile);
        layoutAttachment = findViewById(R.id.layoutAttachment);
        tvAttachmentName = findViewById(R.id.tvAttachmentName);
        btnRemoveAttachment = findViewById(R.id.btnRemoveAttachment);

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

        btnAttach.setOnClickListener(v -> {
            Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
            fileIntent.setType("*/*");
            filePickerLauncher.launch(fileIntent);
        });

        btnRemoveAttachment.setOnClickListener(v -> {
            fileUri = null;
            layoutAttachment.setVisibility(View.GONE);
        });

        btnSend.setOnClickListener(v -> sendMailHttp());
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) result = cursor.getString(index);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    private void calculateHashAndShowUI(Uri uri) {
        new Thread(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
                byte[] hash = digest.digest();
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) hexString.append(String.format("%02x", b));
                fileHash = hexString.toString();

                runOnUiThread(() -> {
                    tvAttachmentName.setText(fileName + " (SHA-256 calculé)");
                    layoutAttachment.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void sendMailHttp() {
        String dest = edtDest.getText().toString();
        String sujet = edtSujet.getText().toString();
        String content = edtMsg.getText().toString();

        if (dest.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Destinataire et message requis.", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                String boundary = "*****" + System.currentTimeMillis() + "*****";
                URL url = new URL(SERVER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                DataOutputStream request = new DataOutputStream(conn.getOutputStream());

                addFormField(request, "sender", currentUser, boundary);
                addFormField(request, "recipient", dest, boundary);
                addFormField(request, "subject", sujet, boundary);
                addFormField(request, "content", content, boundary);
                
                if (fileUri != null) {
                    addFormField(request, "fileHash", fileHash, boundary);
                    addFormField(request, "fileName", fileName, boundary);
                    
                    request.writeBytes("--" + boundary + "\r\n");
                    request.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
                    request.writeBytes("Content-Type: application/octet-stream\r\n\r\n");

                    try (InputStream is = getContentResolver().openInputStream(fileUri)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) > 0) {
                            request.write(buffer, 0, read);
                        }
                    }
                    request.writeBytes("\r\n");
                }

                request.writeBytes("--" + boundary + "--\r\n");
                request.flush();
                request.close();

                int code = conn.getResponseCode();
                runOnUiThread(() -> {
                    if (code == 200) {
                        Toast.makeText(this, "Mail envoyé !", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Erreur serveur : " + code, Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void addFormField(DataOutputStream request, String name, String value, String boundary) throws Exception {
        request.writeBytes("--" + boundary + "\r\n");
        request.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        request.writeBytes(value + "\r\n");
    }
}