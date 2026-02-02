package com.example.vertimailclient;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

public class LoginActivity extends AppCompatActivity {

    private static final String LOGIN_URL = "http://192.168.1.40:8080/api/login";

    EditText edtUser, edtPass;
    CheckBox cbStayLoggedIn;
    Button btnLogin, btnGoRegister;
    TextView tvForgotPass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(new CookieManager());
        }

        edtUser = findViewById(R.id.edtUsername);
        edtPass = findViewById(R.id.edtPassword);
        cbStayLoggedIn = findViewById(R.id.cbStayLoggedIn);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoRegister = findViewById(R.id.btnGoToRegister);
        tvForgotPass = findViewById(R.id.tvForgotPassword);

        // --- CORRECTION : Lancer le worker même en auto-connexion ---
        SharedPreferences prefs = getSharedPreferences("VertimailPrefs", Context.MODE_PRIVATE);
        boolean wasStayLoggedIn = prefs.getBoolean("stayLoggedIn", false);
        String savedUser = prefs.getString("username", null);

        if (wasStayLoggedIn && savedUser != null) {
            startMailCheckWorker(); // On lance la surveillance des mails !
            goToDashboard(savedUser);
            return;
        }
        // ----------------------------------------------------------

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        btnLogin.setOnClickListener(v -> {
            String u = edtUser.getText().toString().trim();
            String p = edtPass.getText().toString().trim();
            boolean stayLoggedIn = cbStayLoggedIn.isChecked();
            
            if (!u.isEmpty()) {
                doLogin(u, p, stayLoggedIn);
            } else {
                Toast.makeText(this, "Veuillez entrer un nom d'utilisateur.", Toast.LENGTH_SHORT).show();
            }
        });

        btnGoRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        tvForgotPass.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ChangePasswordActivity.class);
            intent.putExtra("PREFILLED_USER", edtUser.getText().toString().trim());
            startActivity(intent);
        });
    }

    private void doLogin(String user, String pass, boolean stayLoggedIn) {
        Toast.makeText(this, "Connexion au serveur...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                URL url = new URL(LOGIN_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);

                String params = "username=" + URLEncoder.encode(user, "UTF-8") 
                        + "&password=" + URLEncoder.encode(pass, "UTF-8")
                        + "&stayLoggedIn=" + stayLoggedIn;

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(params.getBytes());
                }

                int code = conn.getResponseCode();
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) response.append(inputLine);
                in.close();

                JSONObject jsonResponse = new JSONObject(response.toString());

                if (code == 200 && jsonResponse.getString("status").equals("ok")) {
                    SharedPreferences prefs = getSharedPreferences("VertimailPrefs", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("username", user);
                    editor.putBoolean("stayLoggedIn", stayLoggedIn);
                    editor.apply();

                    startMailCheckWorker();

                    runOnUiThread(() -> {
                        Toast.makeText(this, "Authentification réussie.", Toast.LENGTH_SHORT).show();
                        goToDashboard(user);
                    });
                } else {
                    String errorMessage = jsonResponse.optString("message", "Erreur inconnue");
                    runOnUiThread(() -> Toast.makeText(this, "Erreur: " + errorMessage, Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Erreur de connexion au réseau.", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void goToDashboard(String user) {
        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
        intent.putExtra("CURRENT_USER", user);
        startActivity(intent);
        finish();
    }

    private void startMailCheckWorker() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest mailCheckRequest = new PeriodicWorkRequest.Builder(MailWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueue(mailCheckRequest);
    }
}