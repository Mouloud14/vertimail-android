package com.example.vertimailclient;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RegisterActivity extends AppCompatActivity {

    private static final String REGISTER_URL = "http://192.168.1.35:8080/api/register";

    EditText edtUser, edtPass, edtConfirm;
    Button btnRegister;
    ImageView imgAvatar;
    String base64Image = ""; 
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
                        imgAvatar.setPadding(0, 0, 0, 0);
                        
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                        byte[] b = baos.toByteArray();
                        base64Image = Base64.encodeToString(b, Base64.NO_WRAP);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        edtUser = findViewById(R.id.regUsername);
        edtPass = findViewById(R.id.regPassword);
        edtConfirm = findViewById(R.id.regConfirmPassword);
        btnRegister = findViewById(R.id.btnDoRegister);
        imgAvatar = findViewById(R.id.imgRegisterAvatar);

        findViewById(R.id.framePhoto).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        btnRegister.setOnClickListener(v -> {
            String u = edtUser.getText().toString().trim();
            String p = edtPass.getText().toString().trim();
            String cp = edtConfirm.getText().toString().trim();

            if (u.isEmpty() || p.isEmpty() || cp.isEmpty()) {
                Toast.makeText(this, "Remplissez tous les champs", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!p.equals(cp)) {
                Toast.makeText(this, "Les mots de passe ne correspondent pas", Toast.LENGTH_SHORT).show();
                return;
            }

            doRegister(u, p);
        });
    }

    private void doRegister(String user, String pass) {
        FormBody.Builder bodyBuilder = new FormBody.Builder()
                .add("username", user)
                .add("password", pass);
        
        if (!base64Image.isEmpty()) {
            bodyBuilder.add("avatar", base64Image);
        }

        Request request = new Request.Builder()
                .url(REGISTER_URL)
                .post(bodyBuilder.build())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(RegisterActivity.this, "Serveur injoignable", Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseData = response.body().string();
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this, "Compte créé !", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(RegisterActivity.this, "Erreur : " + responseData, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}