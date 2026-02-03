package com.example.vertimailclient;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChangePasswordActivity extends AppCompatActivity {

    private static final String CHANGE_PASSWORD_URL = "http://192.168.1.35:8080/api/change-password";

    EditText edtUser, edtOldPass, edtNewPass, edtConfirmPass;
    Button btnChange;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        edtUser = findViewById(R.id.edtUsernameChange);
        edtOldPass = findViewById(R.id.edtOldPassword);
        edtNewPass = findViewById(R.id.edtNewPassword);
        edtConfirmPass = findViewById(R.id.edtConfirmPassword);
        btnChange = findViewById(R.id.btnChangePassword);

        String prefilledUser = getIntent().getStringExtra("PREFILLED_USER");
        if (prefilledUser != null) {
            edtUser.setText(prefilledUser);
        }

        btnChange.setOnClickListener(v -> {
            String user = edtUser.getText().toString().trim();
            String oldP = edtOldPass.getText().toString();
            String newP = edtNewPass.getText().toString();
            String confirmP = edtConfirmPass.getText().toString();

            if (user.isEmpty() || oldP.isEmpty() || newP.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir tous les champs.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newP.equals(confirmP)) {
                Toast.makeText(this, "La confirmation ne correspond pas.", Toast.LENGTH_SHORT).show();
                return;
            }

            doChangePassword(user, oldP, newP);
        });
    }

    private void doChangePassword(String user, String oldPass, String newPass) {
        FormBody body = new FormBody.Builder()
                .add("username", user)
                .add("oldPassword", oldPass)
                .add("newPassword", newPass)
                .build();

        Request request = new Request.Builder().url(CHANGE_PASSWORD_URL).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(ChangePasswordActivity.this, "Erreur réseau : " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(ChangePasswordActivity.this, "Erreur serveur : " + response.code(), Toast.LENGTH_SHORT).show());
                    return;
                }
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    if ("ok".equals(json.optString("status"))) {
                        runOnUiThread(() -> {
                            Toast.makeText(ChangePasswordActivity.this, "Mot de passe modifié !", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    } else {
                        String msg = json.optString("message", "Erreur.");
                        runOnUiThread(() -> Toast.makeText(ChangePasswordActivity.this, msg, Toast.LENGTH_LONG).show());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}