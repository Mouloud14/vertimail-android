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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class RegisterActivity extends AppCompatActivity {

    private static final String REGISTER_URL = "http://192.168.1.40:8080/api/register";

    EditText edtUser, edtPass;
    Button btnRegister;
    ImageView imgAvatar;
    String base64Image = ""; 

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    try {
                        InputStream is = getContentResolver().openInputStream(imageUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(is);
                        imgAvatar.setImageBitmap(bitmap);
                        imgAvatar.setPadding(0, 0, 0, 0);
                        
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                        byte[] b = baos.toByteArray();
                        // CORRECTION : NO_WRAP pour éviter les erreurs de format
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
        btnRegister = findViewById(R.id.btnDoRegister);
        imgAvatar = findViewById(R.id.imgRegisterAvatar);

        findViewById(R.id.framePhoto).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        btnRegister.setOnClickListener(v -> {
            String u = edtUser.getText().toString().trim();
            String p = edtPass.getText().toString().trim();
            if (!u.isEmpty() && !p.isEmpty()) {
                doRegister(u, p);
            } else {
                Toast.makeText(this, "Remplissez tous les champs", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doRegister(String user, String pass) {
        new Thread(() -> {
            try {
                URL url = new URL(REGISTER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                String params = "username=" + URLEncoder.encode(user, "UTF-8")
                        + "&password=" + URLEncoder.encode(pass, "UTF-8")
                        + "&avatar=" + URLEncoder.encode(base64Image, "UTF-8");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(params.getBytes());
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Compte créé avec succès !", Toast.LENGTH_LONG).show();
                        finish();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Erreur lors de l'inscription", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}