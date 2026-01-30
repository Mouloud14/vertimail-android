package com.example.vertimailclient;

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

public class ChangePasswordActivity extends AppCompatActivity {

    private static final String CHANGE_PASSWORD_URL = "http://192.168.1.42:8080/api/change-password";
    
    EditText edtUser, edtOldPass, edtNewPass, edtConfirmPass;
    Button btnChange;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        edtUser = findViewById(R.id.edtUsernameChange);
        edtOldPass = findViewById(R.id.edtOldPassword);
        edtNewPass = findViewById(R.id.edtNewPassword);
        edtConfirmPass = findViewById(R.id.edtConfirmPassword);
        btnChange = findViewById(R.id.btnChangePassword);

        // Récupération éventuelle du pseudo pré-rempli
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
                Toast.makeText(this, "La confirmation ne correspond pas au nouveau mot de passe.", Toast.LENGTH_SHORT).show();
                return;
            }

            doChangePassword(user, oldP, newP);
        });
    }

    private void doChangePassword(String user, String oldPass, String newPass) {
        new Thread(() -> {
            try {
                URL url = new URL(CHANGE_PASSWORD_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);

                // On envoie maintenant le pseudo explicitement car on peut ne pas être encore connecté
                String params = "username=" + URLEncoder.encode(user, "UTF-8")
                        + "&oldPassword=" + URLEncoder.encode(oldPass, "UTF-8") 
                        + "&newPassword=" + URLEncoder.encode(newPass, "UTF-8");

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
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Mot de passe modifié avec succès.", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    String errorMessage = jsonResponse.optString("message", "Erreur lors du changement.");
                    runOnUiThread(() -> Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Erreur réseau : " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}