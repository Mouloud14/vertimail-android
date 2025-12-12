package com.example.vertimailclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MenuActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        Button btnAnonyme = findViewById(R.id.btnModeAnonyme);
        Button btnConnecte = findViewById(R.id.btnModeConnecte);

        // Vers le mode UDP (Anonyme) - Celui que tu as déjà fait
        btnAnonyme.setOnClickListener(v -> {
            Intent intent = new Intent(MenuActivity.this, MainActivity.class);
            startActivity(intent);
        });

        // Vers le mode HTTP (Connecté) - Le nouveau !
        btnConnecte.setOnClickListener(v -> {
            Intent intent = new Intent(MenuActivity.this, LoginActivity.class);
            startActivity(intent);
        });
    }
}