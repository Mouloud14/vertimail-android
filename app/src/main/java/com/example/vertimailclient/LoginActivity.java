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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {

    // --- IP MISE À JOUR : 192.168.1.35 ---
    private static final String LOGIN_URL = "http://192.168.1.35:8080/api/login";

    EditText edtUser, edtPass;
    CheckBox cbStayLoggedIn;
    Button btnLogin, btnGoRegister;
    TextView tvForgotPass;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        edtUser = findViewById(R.id.edtUsername);
        edtPass = findViewById(R.id.edtPassword);
        cbStayLoggedIn = findViewById(R.id.cbStayLoggedIn);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoRegister = findViewById(R.id.btnGoToRegister);
        tvForgotPass = findViewById(R.id.tvForgotPassword);

        SharedPreferences prefs = getSharedPreferences("VertimailPrefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("stayLoggedIn", false)) {
            String savedUser = prefs.getString("username", null);
            if (savedUser != null) {
                startMailCheckWorker();
                goToDashboard(savedUser);
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        btnLogin.setOnClickListener(v -> {
            String u = edtUser.getText().toString().trim();
            String p = edtPass.getText().toString().trim();
            if (!u.isEmpty()) {
                doLogin(u, p, cbStayLoggedIn.isChecked());
            } else {
                Toast.makeText(this, "Pseudo requis", Toast.LENGTH_SHORT).show();
            }
        });

        btnGoRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void doLogin(String user, String pass, boolean stayLoggedIn) {
        FormBody body = new FormBody.Builder()
                .add("username", user)
                .add("password", pass)
                .add("stayLoggedIn", String.valueOf(stayLoggedIn))
                .build();

        Request request = new Request.Builder().url(LOGIN_URL).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Erreur réseau : " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Erreur serveur : " + response.code(), Toast.LENGTH_SHORT).show());
                    return;
                }
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    if ("ok".equals(json.optString("status"))) {
                        SharedPreferences.Editor editor = getSharedPreferences("VertimailPrefs", Context.MODE_PRIVATE).edit();
                        editor.putString("username", user);
                        editor.putBoolean("stayLoggedIn", stayLoggedIn);
                        editor.apply();

                        startMailCheckWorker();
                        runOnUiThread(() -> goToDashboard(user));
                    } else {
                        String msg = json.optString("message", "Erreur");
                        runOnUiThread(() -> Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void goToDashboard(String user) {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.putExtra("CURRENT_USER", user);
        startActivity(intent);
        finish();
    }

    private void startMailCheckWorker() {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(MailWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(this).enqueue(request);
    }
}