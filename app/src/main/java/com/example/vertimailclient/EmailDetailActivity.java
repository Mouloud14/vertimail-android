package com.example.vertimailclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class EmailDetailActivity extends AppCompatActivity {

    private TextView subjectTxt, senderTxt, bodyTxt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_detail);

        subjectTxt = findViewById(R.id.detail_subject);
        senderTxt = findViewById(R.id.detail_sender);
        bodyTxt = findViewById(R.id.detail_body);

        Intent intent = getIntent();
        if (intent != null) {
            String subject = intent.getStringExtra("SUJET_KEY");
            String sender = intent.getStringExtra("SENDER_KEY");
            String body = intent.getStringExtra("BODY_KEY");

            subjectTxt.setText(subject);
            senderTxt.setText("De: " + sender);
            bodyTxt.setText(body);
        }
    }
}