package com.example.vertimailclient;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private static final String SERVER_BASE = "http://192.168.1.33:8080";

    DrawerLayout drawerLayout;
    NavigationView navigationView;
    ListView listView;
    FloatingActionButton fab;
    String currentUser;
    String currentFolder = "inbox";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        currentUser = getIntent().getStringExtra("CURRENT_USER");
        if (currentUser == null || currentUser.isEmpty()) {
            Toast.makeText(this, "Erreur critique : Utilisateur non défini.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Boîte de réception");

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        listView = findViewById(R.id.listMails);
        fab = findViewById(R.id.fab_compose);

        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inbox) {
                currentFolder = "inbox";
                getSupportActionBar().setTitle("Boîte de réception");
                loadMails();
            } else if (id == R.id.nav_outbox) {
                currentFolder = "outbox";
                getSupportActionBar().setTitle("Messages envoyés");
                loadMails();
            } else if (id == R.id.nav_trash) {
                currentFolder = "trash";
                getSupportActionBar().setTitle("Corbeille");
                loadMails();
            } else if (id == R.id.nav_logout) {
                finish();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        fab.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, SendAuthActivity.class);
            intent.putExtra("CURRENT_USER", currentUser);
            startActivity(intent);
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            JSONObject selectedMail = (JSONObject) parent.getItemAtPosition(position);
            String mailId = selectedMail.optString("id");

            if (mailId != null && !mailId.isEmpty() && !selectedMail.optBoolean("isRead")) {
                markEmailAsRead(mailId);
                try {
                    selectedMail.put("isRead", true);
                    ((MailAdapter)parent.getAdapter()).notifyDataSetChanged();
                } catch (Exception e) { e.printStackTrace(); }
            }

            Intent intent = new Intent(DashboardActivity.this, EmailDetailActivity.class);
            intent.putExtra("CURRENT_USER", currentUser);
            intent.putExtra("CURRENT_FOLDER", currentFolder);
            intent.putExtra("ID_KEY", mailId);
            intent.putExtra("SUJET_KEY", selectedMail.optString("subject"));
            intent.putExtra("SENDER_KEY", selectedMail.optString("from"));
            intent.putExtra("BODY_KEY", selectedMail.optString("content"));
            intent.putExtra("DATE_KEY", selectedMail.optString("date"));

            startActivity(intent);
        });

        loadMails();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMails();
    }

    private void loadMails() {
        new Thread(() -> {
            try {
                String urlStr = SERVER_BASE + "/api/mails?username=" + currentUser + "&folder=" + currentFolder;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder content = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) content.append(inputLine);
                in.close();

                JSONObject jsonResponse = new JSONObject(content.toString());
                JSONArray mailsArray = jsonResponse.optJSONArray("mails");

                final List<JSONObject> mailList = new ArrayList<>();
                if (mailsArray != null) {
                    for (int i = 0; i < mailsArray.length(); i++) {
                        mailList.add(mailsArray.getJSONObject(i));
                    }
                }

                Collections.sort(mailList, (a, b) -> b.optString("date", "").compareTo(a.optString("date", "")));

                int unreadCount = 0;
                for (JSONObject mail : mailList) {
                    if (!mail.optBoolean("isRead", false)) {
                        unreadCount++;
                    }
                }

                final int finalUnreadCount = unreadCount;
                runOnUiThread(() -> {
                    MailAdapter adapter = new MailAdapter(mailList);
                    listView.setAdapter(adapter);
                    if (currentFolder.equals("inbox")) {
                        updateUnreadCounter(finalUnreadCount);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(DashboardActivity.this, "Erreur de chargement des messages.", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void markEmailAsRead(String mailId) {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_BASE + "/api/read");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);

                String params = "username=" + URLEncoder.encode(currentUser, "UTF-8")
                        + "&folder=" + URLEncoder.encode(currentFolder, "UTF-8")
                        + "&filename=" + URLEncoder.encode(mailId, "UTF-8");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(params.getBytes());
                }
                conn.getResponseCode();
                if (currentFolder.equals("inbox")) {
                    runOnUiThread(this::loadMails);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updateUnreadCounter(int count) {
        MenuItem inboxItem = navigationView.getMenu().findItem(R.id.nav_inbox);
        if (inboxItem != null) {
            if (count > 0) {
                inboxItem.setTitle("Boîte de réception (" + count + ")");
            } else {
                inboxItem.setTitle("Boîte de réception");
            }
        }
    }

    class MailAdapter extends ArrayAdapter<JSONObject> {
        public MailAdapter(List<JSONObject> mails) {
            super(DashboardActivity.this, 0, mails);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_email, parent, false);
            }

            JSONObject mail = getItem(position);

            View indicator = convertView.findViewById(R.id.unread_indicator);
            TextView txtSender = convertView.findViewById(R.id.emailSender);
            TextView txtSubject = convertView.findViewById(R.id.emailSubject);
            TextView txtSnippet = convertView.findViewById(R.id.emailSnippet);
            TextView txtDate = convertView.findViewById(R.id.emailDate);

            if (mail != null) {
                txtSender.setText(mail.optString("from", "Inconnu"));
                txtSubject.setText(mail.optString("subject", "(Sans sujet)"));
                String content = mail.optString("content", "");
                txtSnippet.setText(content.replace("\n", " "));

                String dateRaw = mail.optString("date", "");
                if (dateRaw.length() > 16) {
                    dateRaw = dateRaw.substring(11, 16);
                }
                txtDate.setText(dateRaw);

                // --- LA CORRECTION EST ICI ---
                boolean isRead = mail.optBoolean("isRead", false); // La valeur par défaut est maintenant false (non lu)
                // ---------------------------

                if (!isRead && currentFolder.equals("inbox")) {
                    indicator.setVisibility(View.VISIBLE);
                    txtSender.setTypeface(null, Typeface.BOLD);
                    txtSubject.setTypeface(null, Typeface.BOLD);
                } else {
                    indicator.setVisibility(View.GONE);
                    txtSender.setTypeface(null, Typeface.NORMAL);
                    txtSubject.setTypeface(null, Typeface.NORMAL);
                }
            }
            return convertView;
        }
    }
}