package com.example.vertimailclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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

    // --- CONFIGURATION SERVEUR ---
    private static final String SERVER_IP = "192.168.1.40";
    private static final String SERVER_BASE = "http://" + SERVER_IP + ":8080";
    private static final String WS_URL = "ws://" + SERVER_IP + ":8080/api/ws";

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ListView listView;
    private SwipeRefreshLayout swipeRefresh;
    private EditText edtSearch;
    private TextView tvHeaderUser, tvStorageInfo, headerAvatarLetter;
    private ImageView imgHeaderAvatar;

    private String currentUser;
    private String currentFolder = "inbox";
    private List<JSONObject> allMails = new ArrayList<>();
    private List<JSONObject> filteredMails = new ArrayList<>();
    private MailAdapter mailAdapter;

    private final OkHttpClient okHttpClient = new OkHttpClient();
    private WebSocket webSocket;

    // Receiver pour rafraîchir quand le service UDP reçoit un message
    private final BroadcastReceiver udpRefreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadMails();
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

        startNotificationService();
        initUI();
        loadMails();
        loadStorageInfo();
        loadUserProfile();
        initWebSocket();
    }

    private void initUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        listView = findViewById(R.id.listMails);
        edtSearch = findViewById(R.id.edtSearch);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        FloatingActionButton fab = findViewById(R.id.fab_compose);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, 0, 0);
        toggle.getDrawerArrowDrawable().setColor(Color.WHITE);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        View header = navigationView.getHeaderView(0);
        tvHeaderUser = header.findViewById(R.id.tvHeaderUser);
        tvStorageInfo = header.findViewById(R.id.tvStorageInfo);
        imgHeaderAvatar = header.findViewById(R.id.imgHeaderAvatar);
        headerAvatarLetter = header.findViewById(R.id.headerAvatarLetter);

        tvHeaderUser.setText(currentUser);
        headerAvatarLetter.setText(currentUser.substring(0, 1).toUpperCase());

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inbox) {
                currentFolder = "inbox";
                getSupportActionBar().setTitle("Boîte de réception");
            } else if (id == R.id.nav_outbox) {
                currentFolder = "outbox";
                getSupportActionBar().setTitle("Messages envoyés");
            } else if (id == R.id.nav_trash) {
                currentFolder = "trash";
                getSupportActionBar().setTitle("Corbeille");
            } else if (id == R.id.nav_logout) {
                finish();
                return true;
            }
            loadMails();
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        swipeRefresh.setOnRefreshListener(this::loadMails);

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterList(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Clic simple : Lire le mail
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (filteredMails.isEmpty()) return;
            openMailDetail(filteredMails.get(position));
        });

        // Clic Long : Supprimer le mail
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            JSONObject mail = filteredMails.get(position);
            showDeleteDialog(mail.optString("id"));
            return true;
        });

        fab.setOnClickListener(v -> {
            Intent intent = new Intent(this, SendAuthActivity.class);
            intent.putExtra("CURRENT_USER", currentUser);
            startActivity(intent);
        });
    }

    private void showDeleteDialog(String mailId) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer le message")
                .setMessage("Voulez-vous déplacer ce message vers la corbeille ?")
                .setPositiveButton("Supprimer", (dialog, which) -> deleteMailOnServer(mailId))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void deleteMailOnServer(String mailId) {
        FormBody body = new FormBody.Builder()
                .add("username", currentUser)
                .add("folder", currentFolder)
                .add("id", mailId)
                .build();

        Request request = new Request.Builder()
                .url(SERVER_BASE + "/api/delete")
                .post(body)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DashboardActivity.this, "Erreur réseau", Toast.LENGTH_SHORT).show());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    Toast.makeText(DashboardActivity.this, "Message supprimé", Toast.LENGTH_SHORT).show();
                    loadMails(); // Rafraîchir la liste
                });
            }
        });
    }

    private void startNotificationService() {
        Intent serviceIntent = new Intent(this, NotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void loadMails() {
        swipeRefresh.setRefreshing(true);
        String url = SERVER_BASE + "/api/mails?username=" + currentUser + "&folder=" + currentFolder;

        Request request = new Request.Builder().url(url).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(DashboardActivity.this, "Serveur injoignable", Toast.LENGTH_SHORT).show();
                });
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) return;
                try {
                    String data = response.body().string();
                    JSONObject jsonResponse = new JSONObject(data);
                    JSONArray array = jsonResponse.optJSONArray("mails");

                    final List<JSONObject> temp = new ArrayList<>();
                    int unread = 0;

                    if (array != null) {
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject m = array.getJSONObject(i);
                            temp.add(m);
                            if (!m.optBoolean("isRead", false)) unread++;
                        }
                    }

                    Collections.sort(temp, (a, b) -> b.optString("id", "").compareTo(a.optString("id", "")));

                    final int finalUnread = unread;
                    runOnUiThread(() -> {
                        allMails.clear();
                        allMails.addAll(temp);
                        filterList(edtSearch.getText().toString());
                        updateUnreadBadge(finalUnread);
                        swipeRefresh.setRefreshing(false);
                    });
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    private void openMailDetail(JSONObject mail) {
        String id = mail.optString("id");
        if (currentFolder.equals("inbox") && !mail.optBoolean("isRead", false)) {
            markAsReadOnServer(id);
            try { mail.put("isRead", true); } catch (Exception ignored) {}
            mailAdapter.notifyDataSetChanged();
        }

        Intent intent = new Intent(this, EmailDetailActivity.class);
        intent.putExtra("CURRENT_USER", currentUser);
        intent.putExtra("CURRENT_FOLDER", currentFolder);
        intent.putExtra("ID_KEY", id);
        intent.putExtra("SUJET_KEY", mail.optString("subject"));
        intent.putExtra("SENDER_KEY", mail.optString("from"));
        intent.putExtra("BODY_KEY", mail.optString("content"));
        intent.putExtra("DATE_KEY", mail.optString("date"));
        JSONArray atts = mail.optJSONArray("attachments");
        intent.putExtra("ATTACH_ARRAY", atts != null ? atts.toString() : null);
        startActivity(intent);
    }

    private void markAsReadOnServer(String mailId) {
        FormBody body = new FormBody.Builder()
                .add("username", currentUser)
                .add("folder", currentFolder)
                .add("id", mailId)
                .build();
        Request request = new Request.Builder().url(SERVER_BASE + "/api/mark-read").post(body).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {}
        });
    }

    private void filterList(String query) {
        String q = query.toLowerCase().trim();
        filteredMails.clear();
        for (JSONObject m : allMails) {
            if (m.optString("from").toLowerCase().contains(q) || m.optString("subject").toLowerCase().contains(q)) {
                filteredMails.add(m);
            }
        }
        if (mailAdapter == null) {
            mailAdapter = new MailAdapter(filteredMails);
            listView.setAdapter(mailAdapter);
        } else {
            mailAdapter.notifyDataSetChanged();
        }
    }

    private void updateUnreadBadge(int count) {
        MenuItem item = navigationView.getMenu().findItem(R.id.nav_inbox);
        if (item != null) {
            item.setTitle(count > 0 ? "Boîte de réception (" + count + ")" : "Boîte de réception");
        }
    }

    private void initWebSocket() {
        Request request = new Request.Builder().url(WS_URL + "?username=" + currentUser).build();
        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                if ("NEW_MAIL".equals(text)) runOnUiThread(() -> loadMails());
            }
            @Override public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> initWebSocket(), 5000);
            }
        });
    }

    private void loadUserProfile() {
        Request request = new Request.Builder().url(SERVER_BASE + "/api/user-profile?username=" + currentUser).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    String b64 = json.optString("avatar_base64", "");
                    runOnUiThread(() -> { if (!b64.isEmpty()) setAvatar(b64, imgHeaderAvatar, headerAvatarLetter); });
                } catch (Exception ignored) {}
            }
        });
    }

    private void loadStorageInfo() {
        Request request = new Request.Builder().url(SERVER_BASE + "/api/storage-info?username=" + currentUser).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
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

    private void setAvatar(String base64, ImageView img, TextView letter) {
        try {
            byte[] decoded = Base64.decode(base64.contains(",") ? base64.split(",")[1] : base64, Base64.DEFAULT);
            Bitmap bit = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            if (bit != null) {
                img.setImageBitmap(bit);
                img.setVisibility(View.VISIBLE);
                if (letter != null) letter.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            img.setVisibility(View.GONE);
            if (letter != null) letter.setVisibility(View.VISIBLE);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        registerReceiver(udpRefreshReceiver, new IntentFilter("com.vertimail.REFRESH_MAILS"), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override protected void onStop() {
        super.onStop();
        unregisterReceiver(udpRefreshReceiver);
    }

    @Override protected void onResume() {
        super.onResume();
        loadMails();
    }

    class MailAdapter extends ArrayAdapter<JSONObject> {
        public MailAdapter(List<JSONObject> mails) { super(DashboardActivity.this, R.layout.item_email, mails); }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) convertView = getLayoutInflater().inflate(R.layout.item_email, parent, false);

            JSONObject mail = getItem(position);
            TextView txtSender = convertView.findViewById(R.id.emailSender);
            TextView txtSubject = convertView.findViewById(R.id.emailSubject);
            View unreadInd = convertView.findViewById(R.id.unread_indicator);
            ImageView imgAvat = convertView.findViewById(R.id.imgAvatar);
            TextView txtLet = convertView.findViewById(R.id.avatarLetter);
            View avatBg = convertView.findViewById(R.id.avatarBackground);
            ImageView imgPin = convertView.findViewById(R.id.imgAttachmentPin);

            if (mail != null) {
                boolean isOutbox = currentFolder.equals("outbox");
                String displayName = isOutbox ? mail.optString("to") : mail.optString("from");

                txtSender.setText((isOutbox ? "À: " : "") + displayName);
                txtSubject.setText(mail.optString("subject", "(Sans sujet)"));

                txtLet.setText(displayName.isEmpty() ? "?" : displayName.substring(0, 1).toUpperCase());
                avatBg.setBackgroundColor(getFixedColor(displayName));

                String b64 = mail.optString("avatar_base64", "");
                if (!b64.isEmpty()) setAvatar(b64, imgAvat, txtLet);
                else { imgAvat.setVisibility(View.GONE); txtLet.setVisibility(View.VISIBLE); }

                JSONArray atts = mail.optJSONArray("attachments");
                imgPin.setVisibility(atts != null && atts.length() > 0 ? View.VISIBLE : View.GONE);

                boolean isRead = mail.optBoolean("isRead", false);
                if (!isRead && currentFolder.equals("inbox")) {
                    unreadInd.setVisibility(View.VISIBLE);
                    txtSender.setTypeface(null, Typeface.BOLD);
                    txtSubject.setTypeface(null, Typeface.BOLD);
                    txtSender.setTextColor(Color.BLACK);
                } else {
                    unreadInd.setVisibility(View.GONE);
                    txtSender.setTypeface(null, Typeface.NORMAL);
                    txtSubject.setTypeface(null, Typeface.NORMAL);
                    txtSender.setTextColor(Color.GRAY);
                }
            }
            return convertView;
        }

        private int getFixedColor(String name) {
            String[] colors = {"#EF5350", "#EC407A", "#AB47BC", "#7E57C2", "#5C6BC0", "#42A5F5", "#26A69A", "#66BB6A"};
            return Color.parseColor(colors[Math.abs(name.hashCode()) % colors.length]);
        }
    }
}