package com.example.vertimailclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class LoginActivity extends AppCompatActivity {

    // --- PASSAGE EN PRODUCTION ---
    private static final String LOGIN_URL = "https://vertimail.onrender.com/api/login";
    // ---------------------------

    EditText edtUser, edtPass;
    Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        edtUser = findViewById(R.id.edtUsername);
        edtPass = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> {
            String u = edtUser.getText().toString().trim();
            String p = edtPass.getText().toString().trim();
            if (!u.isEmpty()) {
                doLogin(u, p);
            } else {
                Toast.makeText(this, "Veuillez entrer un nom d'utilisateur.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doLogin(String user, String pass) {
        Toast.makeText(this, "Connexion au serveur...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                URL url = new URL(LOGIN_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);

                String params = "username=" + URLEncoder.encode(user, "UTF-8") + "&password=" + URLEncoder.encode(pass, "UTF-8");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(params.getBytes());
                }

                int code = conn.getResponseCode();

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject jsonResponse = new JSONObject(response.toString());

                if (code == 200 && jsonResponse.getString("status").equals("ok")) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Authentification réussie.", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                        intent.putExtra("CURRENT_USER", user);
                        startActivity(intent);
                        finish();
                    });
                } else {
                    String errorMessage = jsonResponse.optString("message", "Erreur inconnue du serveur");
                    runOnUiThread(() -> Toast.makeText(this, "Erreur : " + errorMessage, Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                final String msg = e.getMessage();
                runOnUiThread(() -> Toast.makeText(this, "Erreur de connexion au réseau : " + msg, Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}