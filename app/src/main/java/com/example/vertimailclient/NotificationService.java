package com.example.vertimailclient;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class NotificationService extends Service {
    private static final String TAG = "VertimailService";
    private static final int UDP_PORT = 5000;
    private static final String CHANNEL_ID = "vertimail_notifications";
    private static final String SERVICE_CHANNEL_ID = "vertimail_service_channel";

    private boolean isRunning = true;
    private DatagramSocket socket;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Pour que le service ne soit pas tué par Android, on le met en "Premier Plan"
        Intent notificationIntent = new Intent(this, DashboardActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder serviceNotification = new NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                .setContentTitle("Vertimail est actif")
                .setContentText("Recherche de nouveaux messages...")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentIntent(pendingIntent);

        // Lancement en Foreground (obligatoire pour rester en vie en arrière-plan)
        startForeground(1, serviceNotification.build());

        new Thread(this::listenForPackets).start();

        return START_STICKY;
    }

    private void listenForPackets() {
        try {
            socket = new DatagramSocket(UDP_PORT);
            byte[] buffer = new byte[1024];
            Log.d(TAG, "Écoute UDP démarrée sur le port " + UDP_PORT);

            while (isRunning) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // Bloquant jusqu'à réception

                String message = new String(packet.getData(), 0, packet.getLength());
                Log.d(TAG, "Message UDP reçu : " + message);

                // 1. Afficher la vraie notification de mail
                showMailNotification("Nouveau message Vertimail", message);

                // 2. Prévenir le Dashboard de se rafraîchir
                Intent broadcastIntent = new Intent("com.vertimail.REFRESH_MAILS");
                sendBroadcast(broadcastIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur Socket UDP: " + e.getMessage());
            if (isRunning) restartService();
        }
    }

    private void showMailNotification(String title, String content) {
        Intent intent = new Intent(this, DashboardActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            // ID unique par notification pour ne pas écraser les précédentes
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);

            // Canal pour les Emails
            NotificationChannel mailChannel = new NotificationChannel(CHANNEL_ID, "Nouveaux Messages", NotificationManager.IMPORTANCE_HIGH);

            // Canal pour l'icône de service (plus discret)
            NotificationChannel serviceChannel = new NotificationChannel(SERVICE_CHANNEL_ID, "Service de surveillance", NotificationManager.IMPORTANCE_LOW);

            if (manager != null) {
                manager.createNotificationChannel(mailChannel);
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void restartService() {
        // Petite sécurité pour relancer si crash
        Intent restartIntent = new Intent(getApplicationContext(), this.getClass());
        startService(restartIntent);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (socket != null) socket.close();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}