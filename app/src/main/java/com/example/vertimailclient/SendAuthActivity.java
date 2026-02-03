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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SendAuthActivity extends AppCompatActivity {

    private static final String SERVER_BASE = "http://192.168.1.35:8080";

    String currentUser, draftId;
    EditText edtDest, edtSujet, edtMsg;
    Button btnSend, btnSaveDraft, btnAttach;
    LinearLayout layoutAttachment;
    TextView tvAttachmentName;
    ImageButton btnRemoveAttachment;

    Uri fileUri;
    String fileName;
    private final OkHttpClient client = new OkHttpClient();

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    fileUri = result.getData().getData();
                    fileName = getFileName(fileUri);
                    tvAttachmentName.setText(fileName);
                    layoutAttachment.setVisibility(View.VISIBLE);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_auth);

        currentUser = getIntent().getStringExtra("CURRENT_USER");
        draftId = getIntent().getStringExtra("DRAFT_ID");

        edtDest = findViewById(R.id.authDest);
        edtSujet = findViewById(R.id.authSujet);
        edtMsg = findViewById(R.id.authMsg);
        btnSend = findViewById(R.id.btnAuthSend);
        btnSaveDraft = findViewById(R.id.btnSaveDraft);
        btnAttach = findViewById(R.id.btnAttachFile);
        layoutAttachment = findViewById(R.id.layoutAttachment);
        tvAttachmentName = findViewById(R.id.tvAttachmentName);
        btnRemoveAttachment = findViewById(R.id.btnRemoveAttachment);

        if (draftId != null) {
            edtDest.setText(getIntent().getStringExtra("RECIPIENT"));
            edtSujet.setText(getIntent().getStringExtra("SUBJECT"));
            edtMsg.setText(getIntent().getStringExtra("CONTENT"));
            ((TextView)findViewById(R.id.txtWelcome)).setText("Modifier le brouillon");
        }

        btnAttach.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            filePickerLauncher.launch(intent);
        });

        btnRemoveAttachment.setOnClickListener(v -> {
            fileUri = null;
            layoutAttachment.setVisibility(View.GONE);
        });

        btnSend.setOnClickListener(v -> sendMail());
        btnSaveDraft.setOnClickListener(v -> saveDraft());
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

    private void saveDraft() {
        FormBody body = new FormBody.Builder()
                .add("sender", currentUser)
                .add("recipient", edtDest.getText().toString())
                .add("subject", edtSujet.getText().toString())
                .add("content", edtMsg.getText().toString())
                .add("draftId", draftId != null ? draftId : "")
                .build();

        // CHANGEMENT ICI : On utilise /api/draft pour passer le middleware
        Request request = new Request.Builder().url(SERVER_BASE + "/api/draft").post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(SendAuthActivity.this, "Serveur injoignable", Toast.LENGTH_SHORT).show());
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(SendAuthActivity.this, "📁 Brouillon enregistré !", Toast.LENGTH_SHORT).show();
                        sendBroadcast(new Intent("com.vertimail.REFRESH_MAILS"));
                        finish();
                    });
                }
            }
        });
    }

    private void sendMail() {
        String dest = edtDest.getText().toString();
        if (dest.isEmpty()) { Toast.makeText(this, "Destinataire requis", Toast.LENGTH_SHORT).show(); return; }

        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("sender", currentUser)
                .addFormDataPart("recipient", dest)
                .addFormDataPart("subject", edtSujet.getText().toString())
                .addFormDataPart("content", edtMsg.getText().toString());

        if (draftId != null) builder.addFormDataPart("draftId", draftId);

        if (fileUri != null) {
            try {
                InputStream is = getContentResolver().openInputStream(fileUri);
                byte[] bytes = new byte[is.available()];
                is.read(bytes);
                builder.addFormDataPart("file", fileName, RequestBody.create(bytes));
            } catch (Exception e) { e.printStackTrace(); }
        }

        Request request = new Request.Builder().url(SERVER_BASE + "/api/send").post(builder.build()).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(SendAuthActivity.this, "Erreur réseau", Toast.LENGTH_SHORT).show());
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(SendAuthActivity.this, "🚀 Message envoyé !", Toast.LENGTH_SHORT).show();
                        sendBroadcast(new Intent("com.vertimail.REFRESH_MAILS"));
                        finish();
                    });
                }
            }
        });
    }
}