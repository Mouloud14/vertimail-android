package com.example.vertimailclient;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
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
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private static final String SERVER_BASE = "http://192.168.1.37:8080";

    DrawerLayout drawerLayout;
    NavigationView navigationView;
    ListView listView;
    FloatingActionButton fab;
    EditText edtSearch;
    String currentUser;
    String currentFolder = "inbox";

    List<JSONObject> allMails = new ArrayList<>();
    List<JSONObject> filteredMails = new ArrayList<>();
    MailAdapter mailAdapter;

    TextView tvHeaderUser, tvStorageInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        currentUser = getIntent().getStringExtra("CURRENT_USER");
        if (currentUser == null || currentUser.isEmpty()) {
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
        edtSearch = findViewById(R.id.edtSearch);

        View headerView = navigationView.getHeaderView(0);
        tvHeaderUser = headerView.findViewById(R.id.tvHeaderUser);
        tvStorageInfo = headerView.findViewById(R.id.tvStorageInfo);
        tvHeaderUser.setText(currentUser);

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
            JSONObject selectedMail = filteredMails.get(position);
            String mailId = selectedMail.optString("id");

            try {
                selectedMail.put("isRead", true);
                mailAdapter.notifyDataSetChanged();
            } catch (Exception e) { e.printStackTrace(); }

            Intent intent = new Intent(DashboardActivity.this, EmailDetailActivity.class);
            intent.putExtra("CURRENT_USER", currentUser);
            intent.putExtra("CURRENT_FOLDER", currentFolder);
            intent.putExtra("ID_KEY", mailId);
            intent.putExtra("SUJET_KEY", selectedMail.optString("subject"));
            intent.putExtra("SENDER_KEY", selectedMail.optString("from"));
            intent.putExtra("BODY_KEY", selectedMail.optString("content"));
            intent.putExtra("DATE_KEY", selectedMail.optString("date"));
            intent.putExtra("IMPORTANT_KEY", selectedMail.optBoolean("important", false));

            startActivity(intent);
        });

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterList(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadMails();
        loadStorageInfo();
    }

    private void filterList(String query) {
        String lowerQuery = query.toLowerCase().trim();
        filteredMails.clear();
        if (lowerQuery.isEmpty()) {
            filteredMails.addAll(allMails);
        } else {
            for (JSONObject mail : allMails) {
                if (mail.optString("from", "").toLowerCase().contains(lowerQuery) ||
                    mail.optString("subject", "").toLowerCase().contains(lowerQuery)) {
                    filteredMails.add(mail);
                }
            }
        }
        if (mailAdapter != null) mailAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMails();
        loadStorageInfo();
    }

    private void loadMails() {
        new Thread(() -> {
            try {
                String urlStr = SERVER_BASE + "/api/mails?username=" + currentUser + "&folder=" + currentFolder;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder content = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) content.append(inputLine);
                in.close();

                JSONObject jsonResponse = new JSONObject(content.toString());
                JSONArray mailsArray = jsonResponse.optJSONArray("mails");
                final List<JSONObject> mailList = new ArrayList<>();
                if (mailsArray != null) {
                    for (int i = 0; i < mailsArray.length(); i++) mailList.add(mailsArray.getJSONObject(i));
                }
                Collections.sort(mailList, (a, b) -> b.optString("date", "").compareTo(a.optString("date", "")));

                int unreadCount = 0;
                for (JSONObject mail : mailList) if (!mail.optBoolean("isRead", false)) unreadCount++;

                final int finalUnreadCount = unreadCount;
                runOnUiThread(() -> {
                    allMails.clear();
                    allMails.addAll(mailList);
                    filterList(edtSearch.getText().toString());
                    mailAdapter = new MailAdapter(filteredMails);
                    listView.setAdapter(mailAdapter);
                    if (currentFolder.equals("inbox")) updateUnreadCounter(finalUnreadCount);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void loadStorageInfo() {
        new Thread(() -> {
            try {
                String urlStr = SERVER_BASE + "/api/storage-info?username=" + currentUser;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = in.readLine();
                in.close();

                JSONObject json = new JSONObject(line);
                String sizeStr = json.optString("sizeReadable", "0 Ko");

                runOnUiThread(() -> tvStorageInfo.setText("Espace utilisé : " + sizeStr));
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void updateUnreadCounter(int count) {
        MenuItem inboxItem = navigationView.getMenu().findItem(R.id.nav_inbox);
        if (inboxItem != null) {
            inboxItem.setTitle(count > 0 ? "Boîte de réception (" + count + ")" : "Boîte de réception");
        }
    }

    class MailAdapter extends ArrayAdapter<JSONObject> {
        public MailAdapter(List<JSONObject> mails) { super(DashboardActivity.this, R.layout.item_email, mails); }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = getLayoutInflater().inflate(R.layout.item_email, parent, false);
            JSONObject mail = getItem(position);
            
            View indicator = convertView.findViewById(R.id.unread_indicator);
            TextView txtSender = convertView.findViewById(R.id.emailSender);
            TextView txtSubject = convertView.findViewById(R.id.emailSubject);
            ImageView imgImportant = convertView.findViewById(R.id.imgImportant);

            if (mail != null) {
                txtSender.setText(mail.optString("from", "Inconnu"));
                txtSubject.setText(mail.optString("subject", "(Sans sujet)"));
                
                boolean isRead = mail.optBoolean("isRead", false);
                boolean isImportant = mail.optBoolean("important", false);

                if (!isRead && currentFolder.equals("inbox")) {
                    indicator.setVisibility(View.VISIBLE);
                    txtSender.setTypeface(null, Typeface.BOLD);
                    txtSubject.setTypeface(null, Typeface.BOLD);
                } else {
                    indicator.setVisibility(View.GONE);
                    txtSender.setTypeface(null, Typeface.NORMAL);
                    txtSubject.setTypeface(null, Typeface.NORMAL);
                }
                imgImportant.setVisibility(isImportant ? View.VISIBLE : View.GONE);
            }
            return convertView;
        }
    }
}