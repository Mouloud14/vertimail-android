package com.example.vertimailclient;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class DashboardActivity extends AppCompatActivity {

    private static final String SERVER_BASE = "http://192.168.1.35:8080";
    private static final String WS_URL = "ws://192.168.1.35:8080/api/ws";

    DrawerLayout drawerLayout;
    NavigationView navigationView;
    ListView listView;
    FloatingActionButton fab;
    EditText edtSearch;
    SwipeRefreshLayout swipeRefresh;
    String currentUser;
    String currentFolder = "inbox";

    List<JSONObject> allMails = new ArrayList<>();
    List<JSONObject> filteredMails = new ArrayList<>();
    MailAdapter mailAdapter;

    TextView tvHeaderUser, tvStorageInfo, headerAvatarLetter;
    ImageView imgHeaderAvatar;
    View headerAvatarBg;
    private final OkHttpClient client = new OkHttpClient();
    private WebSocket webSocket;

    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.vertimail.REFRESH_MAILS".equals(intent.getAction())) {
                runOnUiThread(() -> {
                    loadMails();
                    loadUserProfile();
                });
            }
        }
    };

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
        swipeRefresh = findViewById(R.id.swipeRefresh);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, android.R.string.ok, android.R.string.cancel);
        toggle.getDrawerArrowDrawable().setColor(Color.WHITE);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        View headerView = navigationView.getHeaderView(0);
        tvHeaderUser = headerView.findViewById(R.id.tvHeaderUser);
        tvStorageInfo = headerView.findViewById(R.id.tvStorageInfo);
        imgHeaderAvatar = headerView.findViewById(R.id.imgHeaderAvatar);
        headerAvatarLetter = headerView.findViewById(R.id.headerAvatarLetter);
        headerAvatarBg = headerView.findViewById(R.id.headerAvatarBg);

        tvHeaderUser.setText(currentUser);
        headerAvatarLetter.setText(currentUser.substring(0, 1).toUpperCase());

        IntentFilter filter = new IntentFilter("com.vertimail.REFRESH_MAILS");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(refreshReceiver, filter);
        }

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            
            if (id == R.id.nav_inbox) {
                allMails.clear(); filteredMails.clear();
                currentFolder = "inbox";
                getSupportActionBar().setTitle("Boîte de réception");
                loadMails();
            } else if (id == R.id.nav_drafts) { // AJOUT BROUILLONS
                allMails.clear(); filteredMails.clear();
                currentFolder = "draft";
                getSupportActionBar().setTitle("Brouillons");
                loadMails();
            } else if (id == R.id.nav_outbox) {
                allMails.clear(); filteredMails.clear();
                currentFolder = "outbox";
                getSupportActionBar().setTitle("Messages envoyés");
                loadMails();
            } else if (id == R.id.nav_trash) {
                allMails.clear(); filteredMails.clear();
                currentFolder = "trash";
                getSupportActionBar().setTitle("Corbeille");
                loadMails();
            } else if (id == R.id.nav_settings) {
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.putExtra("CURRENT_USER", currentUser);
                startActivity(intent);
            } else if (id == R.id.nav_logout) {
                getSharedPreferences("VertimailPrefs", MODE_PRIVATE).edit().clear().apply();
                startActivity(new Intent(this, LoginActivity.class));
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

        swipeRefresh.setOnRefreshListener(this::loadMails);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (filteredMails.isEmpty()) return;
            JSONObject selectedMail = filteredMails.get(position);
            
            if (currentFolder.equals("draft")) {
                // Si c'est un brouillon, on ouvre l'écran de composition
                Intent intent = new Intent(this, SendAuthActivity.class);
                intent.putExtra("CURRENT_USER", currentUser);
                intent.putExtra("DRAFT_ID", selectedMail.optString("id"));
                intent.putExtra("RECIPIENT", selectedMail.optString("to"));
                intent.putExtra("SUBJECT", selectedMail.optString("subject"));
                intent.putExtra("CONTENT", selectedMail.optString("content"));
                startActivity(intent);
            } else {
                try { selectedMail.put("isRead", true); if (mailAdapter != null) mailAdapter.notifyDataSetChanged(); } catch (Exception ignored) {}

                Intent intent = new Intent(this, EmailDetailActivity.class);
                intent.putExtra("CURRENT_USER", currentUser);
                intent.putExtra("CURRENT_FOLDER", currentFolder);
                intent.putExtra("ID_KEY", selectedMail.optString("id"));
                intent.putExtra("SUJET_KEY", selectedMail.optString("subject"));
                intent.putExtra("SENDER_KEY", selectedMail.optString("from"));
                intent.putExtra("BODY_KEY", selectedMail.optString("content"));
                intent.putExtra("DATE_KEY", selectedMail.optString("date"));
                
                JSONArray atts = selectedMail.optJSONArray("attachments");
                if (atts != null && atts.length() > 0) {
                    JSONObject first = atts.optJSONObject(0);
                    intent.putExtra("ATTACH_NAME", first.optString("name"));
                    intent.putExtra("ATTACH_HASH", first.optString("hash"));
                }
                startActivity(intent);
            }
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (filteredMails.isEmpty()) return true;
            JSONObject mail = filteredMails.get(position);
            new AlertDialog.Builder(this).setTitle("Supprimer").setMessage("Dossier Corbeille ?")
                .setPositiveButton("Oui", (d, w) -> deleteMail(mail.optString("id"))).setNegativeButton("Non", null).show();
            return true;
        });

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterList(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadMails();
        loadStorageInfo();
        loadUserProfile();
        initWebSocket();
    }

    private void deleteMail(String mailId) {
        FormBody body = new FormBody.Builder().add("username", currentUser).add("folder", currentFolder).add("id", mailId).build();
        Request request = new Request.Builder().url(SERVER_BASE + "/api/delete").post(body).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> { Toast.makeText(DashboardActivity.this, "Message supprimé", Toast.LENGTH_SHORT).show(); loadMails(); });
                }
            }
        });
    }

    private void loadMails() {
        swipeRefresh.setRefreshing(true);
        Request request = new Request.Builder().url(SERVER_BASE + "/api/mails?username=" + currentUser + "&folder=" + currentFolder + "&limit=50").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> swipeRefresh.setRefreshing(false));
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) return;
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray array = json.optJSONArray("mails");
                    final List<JSONObject> list = new ArrayList<>();
                    if (array != null) {
                        for (int i = 0; i < array.length(); i++) list.add(array.getJSONObject(i));
                    }
                    Collections.sort(list, (a, b) -> b.optString("id").compareTo(a.optString("id")));
                    runOnUiThread(() -> {
                        allMails.clear(); allMails.addAll(list);
                        filterList(edtSearch.getText().toString());
                        swipeRefresh.setRefreshing(false);
                    });
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    private void filterList(String query) {
        String q = query.toLowerCase().trim();
        filteredMails.clear();
        for (JSONObject m : allMails) {
            String person = (currentFolder.equals("outbox") || currentFolder.equals("draft")) ? m.optString("to") : m.optString("from");
            if (person.toLowerCase().contains(q) || m.optString("subject").toLowerCase().contains(q)) filteredMails.add(m);
        }
        if (mailAdapter == null) { mailAdapter = new MailAdapter(filteredMails); listView.setAdapter(mailAdapter); }
        else mailAdapter.notifyDataSetChanged();
    }

    private void loadUserProfile() {
        Request request = new Request.Builder().url(SERVER_BASE + "/api/user-profile?username=" + currentUser).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    String b64 = json.optString("avatar_base64", "").trim();
                    if (!b64.isEmpty()) runOnUiThread(() -> setAvatar(b64, imgHeaderAvatar, headerAvatarLetter, null));
                } catch (Exception ignored) {}
            }
        });
    }

    private void loadStorageInfo() {
        Request request = new Request.Builder().url(SERVER_BASE + "/api/storage-info?username=" + currentUser).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    String size = json.optString("sizeReadable", "0 Ko");
                    runOnUiThread(() -> tvStorageInfo.setText("Espace : " + size));
                } catch (Exception ignored) {}
            }
        });
    }

    private void initWebSocket() {
        Request request = new Request.Builder().url(WS_URL + "?username=" + currentUser).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                if ("NEW_MAIL".equals(text)) runOnUiThread(() -> loadMails());
            }
        });
    }

    private void setAvatar(String b64, ImageView img, TextView letter, View bg) {
        try {
            String cleanB64 = b64.replaceAll("\\s", "");
            if (cleanB64.contains(",")) cleanB64 = cleanB64.split(",")[1];
            byte[] bytes = Base64.decode(cleanB64, Base64.DEFAULT);
            Bitmap bit = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bit != null) {
                img.setImageBitmap(bit); img.setVisibility(View.VISIBLE);
                if (letter != null) letter.setVisibility(View.GONE);
                if (bg != null) bg.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            img.setVisibility(View.GONE);
            if (letter != null) letter.setVisibility(View.VISIBLE);
            if (bg != null) bg.setVisibility(View.VISIBLE);
        }
    }

    @Override protected void onDestroy() { try { unregisterReceiver(refreshReceiver); } catch (Exception ignored) {} super.onDestroy(); }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { drawerLayout.openDrawer(GravityCompat.START); return true; }
        return super.onOptionsItemSelected(item);
    }

    class MailAdapter extends ArrayAdapter<JSONObject> {
        public MailAdapter(List<JSONObject> mails) { super(DashboardActivity.this, R.layout.item_email, mails); }
        @Override public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) convertView = getLayoutInflater().inflate(R.layout.item_email, parent, false);
            JSONObject mail = getItem(position);
            TextView txtSender = convertView.findViewById(R.id.emailSender);
            TextView txtSubject = convertView.findViewById(R.id.emailSubject);
            View unreadInd = convertView.findViewById(R.id.unread_indicator);
            View avatarBg = convertView.findViewById(R.id.avatarBackground);
            TextView avatarLet = convertView.findViewById(R.id.avatarLetter);
            ImageView imgAvat = convertView.findViewById(R.id.imgAvatar);

            if (mail != null) {
                String person = (currentFolder.equals("outbox") || currentFolder.equals("draft")) ? mail.optString("to") : mail.optString("from");
                String prefix = currentFolder.equals("outbox") ? "À: " : (currentFolder.equals("draft") ? "Brouillon pour: " : "");
                
                txtSender.setText(prefix + person);
                txtSubject.setText(mail.optString("subject", "(Sans sujet)"));
                avatarLet.setText(person.isEmpty() ? "?" : person.substring(0, 1).toUpperCase());
                avatarBg.setBackgroundTintList(ColorStateList.valueOf(getFixedColorForUser(person)));

                String b64 = mail.optString("avatar_base64", "").trim().replaceAll("\\s", "");
                if (!b64.isEmpty()) setAvatar(b64, imgAvat, avatarLet, avatarBg);
                else { imgAvat.setVisibility(View.GONE); avatarLet.setVisibility(View.VISIBLE); avatarBg.setVisibility(View.VISIBLE); }

                boolean isRead = mail.optBoolean("isRead", false);
                unreadInd.setVisibility(!isRead && currentFolder.equals("inbox") ? View.VISIBLE : View.GONE);
                txtSender.setTypeface(null, isRead ? Typeface.NORMAL : Typeface.BOLD);
                txtSubject.setTypeface(null, isRead ? Typeface.NORMAL : Typeface.BOLD);
            }
            return convertView;
        }
        private int getFixedColorForUser(String name) {
            String[] colors = {"#EF5350", "#EC407A", "#AB47BC", "#7E57C2", "#5C6BC0", "#42A5F5", "#26A69A", "#66BB6A", "#FFA726", "#795548"};
            return Color.parseColor(colors[Math.abs(name.hashCode()) % colors.length]);
        }
    }
}