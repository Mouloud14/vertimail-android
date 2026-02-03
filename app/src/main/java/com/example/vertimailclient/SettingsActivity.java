package com.example.vertimailclient;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SettingsActivity extends AppCompatActivity {

    private static final String SERVER_BASE = "http://192.168.1.35:8080";

    private String currentUser;
    private EditText edtOldPass, edtNewPass, edtConfirmPass;
    private ImageView imgAvatar;
    private TextView tvAvatarLetter;
    private View avatarBg;
    private String base64Image = "";
    private final OkHttpClient client = new OkHttpClient();

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    try {
                        InputStream is = getContentResolver().openInputStream(imageUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(is);
                        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 300, 300, true);
                        imgAvatar.setImageBitmap(scaled);
                        imgAvatar.setVisibility(View.VISIBLE);
                        tvAvatarLetter.setVisibility(View.GONE);
                        
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                        byte[] b = baos.toByteArray();
                        base64Image = Base64.encodeToString(b, Base64.NO_WRAP);
                        
                        updateAvatarOnServer();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        currentUser = getIntent().getStringExtra("CURRENT_USER");

        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        edtOldPass = findViewById(R.id.edtOldPassword);
        edtNewPass = findViewById(R.id.edtNewPassword);
        edtConfirmPass = findViewById(R.id.edtConfirmNewPassword);
        imgAvatar = findViewById(R.id.imgSettingsAvatar);
        tvAvatarLetter = findViewById(R.id.settingsAvatarLetter);
        avatarBg = findViewById(R.id.settingsAvatarBg);

        tvAvatarLetter.setText(currentUser.substring(0, 1).toUpperCase());

        loadCurrentProfile();

        findViewById(R.id.btnChangePhoto).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> {
            String oldP = edtOldPass.getText().toString();
            String newP = edtNewPass.getText().toString();
            String confP = edtConfirmPass.getText().toString();

            if (!oldP.isEmpty() && !newP.isEmpty()) {
                if (newP.equals(confP)) {
                    changePassword(oldP, newP);
                } else {
                    Toast.makeText(this, "Les mots de passe ne correspondent pas", Toast.LENGTH_SHORT).show();
                }
            } else if (oldP.isEmpty() && newP.isEmpty()) {
                Toast.makeText(this, "Rien à enregistrer", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Veuillez remplir tous les champs mot de passe", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadCurrentProfile() {
        Request request = new Request.Builder()
                .url(SERVER_BASE + "/api/user-profile?username=" + currentUser)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) return;
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    String b64 = json.optString("avatar_base64", "").trim().replaceAll("\\s", "");
                    if (!b64.isEmpty()) {
                        byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                        Bitmap bit = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        runOnUiThread(() -> {
                            imgAvatar.setImageBitmap(bit);
                            imgAvatar.setVisibility(View.VISIBLE);
                            tvAvatarLetter.setVisibility(View.GONE);
                        });
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void updateAvatarOnServer() {
        FormBody body = new FormBody.Builder()
                .add("username", currentUser)
                .add("avatar", base64Image)
                .build();

        Request request = new Request.Builder()
                .url(SERVER_BASE + "/api/settings/avatar")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Erreur réseau avatar", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(SettingsActivity.this, "Photo mise à jour !", Toast.LENGTH_SHORT).show();
                        sendBroadcast(new Intent("com.vertimail.REFRESH_MAILS")); // Pour rafraîchir le header du dashboard
                    }
                });
            }
        });
    }

    private void changePassword(String oldP, String newP) {
        FormBody body = new FormBody.Builder()
                .add("username", currentUser)
                .add("oldPassword", oldP)
                .add("newPassword", newP)
                .build();

        Request request = new Request.Builder()
                .url(SERVER_BASE + "/api/change-password")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Erreur réseau", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String resp = response.body().string();
                runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(resp);
                        if (response.isSuccessful() && "ok".equals(json.optString("status"))) {
                            Toast.makeText(SettingsActivity.this, "Mot de passe changé !", Toast.LENGTH_SHORT).show();
                            edtOldPass.setText("");
                            edtNewPass.setText("");
                            edtConfirmPass.setText("");
                        } else {
                            Toast.makeText(SettingsActivity.this, json.optString("message", "Erreur"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(SettingsActivity.this, "Erreur serveur", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}