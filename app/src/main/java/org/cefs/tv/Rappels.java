package org.cefs.tv;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Notifications « CEFS TV » : rappels avant les émissions (heure d'Abidjan)
 * et alerte quand un direct commence. Aucun serveur : tout se calcule sur le téléphone.
 */
public class Rappels extends BroadcastReceiver {

    static final String CANAL = "cefs_emissions";
    static final String ACTION = "org.cefs.tv.RAPPEL";

    /** Rappels : jour (0 = lundi … 6 = dimanche, -1 = chaque jour), heure, minute, type. */
    static final Object[][] RAPPELS = {
            {-1, 7, 0, "matin"},
            {5, 9, 55, "film"}, {5, 17, 55, "film"}, {5, 20, 55, "film"}, {6, 15, 55, "film"},
            {0, 20, 55, "doc"}, {1, 20, 55, "doc"}, {2, 20, 55, "doc"}, {3, 20, 55, "doc"}, {4, 20, 55, "doc"}, {6, 20, 55, "doc"},
    };

    static Calendar abidjan() { return Calendar.getInstance(TimeZone.getTimeZone("UTC")); }
    static int jour(Calendar c) { return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; }

    /** Prépare le canal, l'alarme du prochain rappel et la veille des directs. */
    static void demarrer(Context ctx) {
        canal(ctx);
        programmer(ctx);
        Direct.programmer(ctx);
    }

    static void canal(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CANAL, "Émissions CEFS TV", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Rappels avant les émissions et alertes de direct");
            ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    static void programmer(Context ctx) {
        Calendar now = abidjan();
        long best = Long.MAX_VALUE; String type = null;
        for (int add = 0; add <= 7; add++) {
            for (Object[] r : RAPPELS) {
                Calendar c = abidjan();
                c.add(Calendar.DAY_OF_MONTH, add);
                c.set(Calendar.HOUR_OF_DAY, (Integer) r[1]); c.set(Calendar.MINUTE, (Integer) r[2]);
                c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
                int d = (Integer) r[0];
                if (d >= 0 && jour(c) != d) continue;
                long t = c.getTimeInMillis();
                if (t > now.getTimeInMillis() + 30000 && t < best) { best = t; type = (String) r[3]; }
            }
            if (type != null) break;
        }
        if (type == null) return;
        Intent i = new Intent(ctx, Rappels.class).setAction(ACTION).putExtra("type", type);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ACTION.equals(intent.getAction())) { demarrer(ctx); return; }
        final String type = intent.getStringExtra("type");
        final PendingResult res = goAsync();
        new Thread(() -> {
            try {
                JSONObject d = null;
                try { d = donnees(ctx); } catch (Exception ignored) {}
                envoyerRappel(ctx, type, d);
            } catch (Exception ignored) {
            } finally {
                programmer(ctx);
                res.finish();
            }
        }).start();
    }

    static JSONObject donnees(Context ctx) throws Exception {
        URL u = new URL(ctx.getString(R.string.cefs_url) + "data/tv.json?t=" + System.currentTimeMillis());
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return new JSONObject(out.toString("UTF-8"));
        } finally { c.disconnect(); }
    }

    static String titre(JSONObject d, String id) {
        try {
            JSONArray v = d.getJSONArray("videos");
            for (int i = 0; i < v.length(); i++) if (id.equals(v.getJSONObject(i).optString("id"))) return v.getJSONObject(i).optString("title");
        } catch (Exception ignored) {}
        return null;
    }

    static long semaines(String epoch) {
        String[] e = epoch.split("-");
        Calendar m = abidjan(); m.clear();
        m.set(Integer.parseInt(e[0]), Integer.parseInt(e[1]) - 1, Integer.parseInt(e[2]));
        Calendar n = abidjan();
        Calendar t = abidjan(); t.clear(); t.set(n.get(Calendar.YEAR), n.get(Calendar.MONTH), n.get(Calendar.DAY_OF_MONTH));
        return Math.floorDiv(t.getTimeInMillis() - m.getTimeInMillis(), 86400000L * 7);
    }

    /** Même calcul que la page : film ou documentaire du jour. */
    static String emission(JSONObject d, String type) {
        if (d == null) return null;
        try {
            Calendar now = abidjan(); int day = jour(now);
            String hm = String.format("%02d:00", now.get(Calendar.HOUR_OF_DAY) + 1);
            if ("film".equals(type)) {
                JSONObject f = d.getJSONObject("films"); JSONArray s = f.getJSONArray("slots"), l = f.getJSONArray("list");
                long w = semaines(f.optString("epoch", "2026-10-12"));
                for (int i = 0; i < s.length(); i++) {
                    JSONObject o = s.getJSONObject(i);
                    if (o.getInt("day") == day && o.getString("at").equals(hm)) {
                        int L = l.length(); int k = (int) (((w * s.length() + i) % L + L) % L);
                        return titre(d, l.getString(k));
                    }
                }
            } else if ("doc".equals(type)) {
                JSONObject f = d.getJSONObject("docs"); JSONArray l = f.getJSONArray("list"), sk = f.optJSONArray("skip");
                boolean[] skip = new boolean[7];
                if (sk != null) for (int i = 0; i < sk.length(); i++) skip[sk.getInt(i)] = true; else skip[5] = true;
                if (skip[day]) return null;
                int per = 0, before = 0;
                for (int x = 0; x < 7; x++) if (!skip[x]) { per++; if (x < day) before++; }
                long w = semaines(f.optString("epoch", "2026-10-12"));
                int L = l.length(); int k = (int) (((w * per + before) % L + L) % L);
                return titre(d, l.getString(k));
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Programme spécial du jour sans rendez-vous : pas de rappel de film ou de documentaire. */
    static boolean journeeSpeciale(JSONObject d) {
        if (d == null) return false;
        try {
            Calendar n = abidjan();
            String today = String.format("%04d-%02d-%02d", n.get(Calendar.YEAR), n.get(Calendar.MONTH) + 1, n.get(Calendar.DAY_OF_MONTH));
            JSONArray s = d.optJSONArray("specials");
            if (s != null) for (int i = 0; i < s.length(); i++) {
                JSONObject o = s.getJSONObject(i);
                if (today.equals(o.optString("date")) && !o.optBoolean("keepFixed", false)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    static void envoyerRappel(Context ctx, String type, JSONObject d) {
        String titre, texte;
        if ("matin".equals(type)) {
            titre = "🙏 Bonjour, la prière vous attend";
            texte = "Commencez la journée avec Dieu : CEFS TV est à l'antenne.";
        } else {
            if (journeeSpeciale(d)) return;
            String nom = emission(d, type);
            if ("doc".equals(type) && d != null && nom == null) return;
            if ("film".equals(type)) {
                titre = "🎬 Le film commence dans 5 minutes";
                texte = nom != null ? "« " + nom + " » sur CEFS TV. Installez-vous en famille !" : "Film chrétien sur CEFS TV. Installez-vous en famille !";
            } else {
                titre = "📺 Documentaire à 21 h";
                texte = nom != null ? "« " + nom + " » dans 5 minutes sur CEFS TV." : "Le documentaire du soir commence dans 5 minutes sur CEFS TV.";
            }
        }
        montrer(ctx, 10, titre, texte);
    }

    static void montrer(Context ctx, int id, String titre, String texte) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        canal(ctx);
        Intent open = new Intent(ctx, MainActivity.class).setData(Uri.parse("cefstv://ouvrir"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CANAL) : new Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.ic_notif).setColor(0xFFD3352A)
                .setContentTitle(titre).setContentText(texte)
                .setStyle(new Notification.BigTextStyle().bigText(texte))
                .setContentIntent(pi).setAutoCancel(true);
        ctx.getSystemService(NotificationManager.class).notify(id, b.build());
    }

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences("cefs", Context.MODE_PRIVATE); }
}
