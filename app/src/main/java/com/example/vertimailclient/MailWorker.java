package com.example.vertimailclient;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

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
    private static final String SERVER_BASE = "http://192.168.1.40:8080";

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
            // On vérifie les messages
            URL url = new URL(SERVER_BASE + "/api/mails?username=" + username + "&folder=inbox");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000); // On laisse un peu plus de temps

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) response.append(line);
            in.close();

            JSONObject json = new JSONObject(response.toString());
            JSONArray mails = json.optJSONArray("mails");

            if (mails != null && mails.length() > 0) {
                // On regarde les 3 derniers messages pour être sûr de ne rien rater
                for (int i = 0; i < Math.min(mails.length(), 3); i++) {
                    JSONObject mail = mails.getJSONObject(i);
                    if (!mail.optBoolean("isRead", false)) {
                        String subject = mail.optString("subject", "Nouveau message");
                        String sender = mail.optString("from", "Inconnu");
                        String mailId = mail.optString("id");

                        // On n'affiche la notif que si on ne l'a pas déjà affichée pour ce mailId
                        if (shouldNotify(context, mailId)) {
                            showNotification(sender, subject);
                            markAsNotified(context, mailId);
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e("MailWorker", "Erreur lors de la vérification : " + e.getMessage());
            return Result.retry();
        }

        return Result.success();
    }

    private boolean shouldNotify(Context context, String mailId) {
        SharedPreferences prefs = context.getSharedPreferences("NotifPrefs", Context.MODE_PRIVATE);
        return !prefs.getBoolean("notified_" + mailId, false);
    }

    private void markAsNotified(Context context, String mailId) {
        SharedPreferences prefs = context.getSharedPreferences("NotifPrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("notified_" + mailId, true).apply();
    }

    private void showNotification(String sender, String subject) {
        Context context = getApplicationContext();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Nouveaux Courriers", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        // On corrige le lien pour qu'il soit cliquable
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(SERVER_BASE));
        PendingIntent pendingIntent = PendingIntent.getActivity(context, (int)System.currentTimeMillis(), browserIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Nouveau mail de " + sender)
                .setContentText(subject)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}