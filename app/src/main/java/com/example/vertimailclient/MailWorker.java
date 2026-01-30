package com.example.vertimailclient;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MailWorker extends Worker {

    private static final String CHANNEL_ID = "MAIL_NOTIFICATIONS";
    private static final String SERVER_BASE = "http://192.168.1.33:8080"; // Vérifie ton IP !

    public MailWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("VertimailPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("username", null);

        if (username == null) return Result.success();

        try {
            // 1. On demande la liste des mails récents
            URL url = new URL(SERVER_BASE + "/api/mails?username=" + username + "&folder=inbox");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) response.append(line);
            in.close();

            JSONObject json = new JSONObject(response.toString());
            JSONArray mails = json.optJSONArray("mails");

            if (mails != null && mails.length() > 0) {
                // On récupère le mail le plus récent (le premier après notre tri)
                JSONObject latestMail = mails.getJSONObject(0);
                
                // On vérifie s'il est non lu
                if (!latestMail.optBoolean("isRead", false)) {
                    String subject = latestMail.optString("subject", "Nouveau message");
                    String sender = latestMail.optString("from", "Inconnu");
                    
                    // On affiche la notification
                    showNotification(sender, subject);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        }

        return Result.success();
    }

    private void showNotification(String sender, String subject) {
        Context context = getApplicationContext();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Création du canal pour Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Nouveaux Courriers", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        // Intention de rediriger vers le site web (comme demandé)
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:8080")); // À adapter si besoin
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, browserIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Nouveau mail de " + sender)
                .setContentText(subject)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify(1, builder.build());
    }
}