package org.cefs.tv;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import org.json.JSONObject;

/** Toutes les 15 minutes environ : un direct a-t-il commencé ? Si oui, une notification. */
public class Direct extends JobService {

    static final int JOB = 42;

    static void programmer(Context ctx) {
        JobScheduler js = ctx.getSystemService(JobScheduler.class);
        if (js.getPendingJob(JOB) != null) return;
        JobInfo job = new JobInfo.Builder(JOB, new ComponentName(ctx, Direct.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build();
        js.schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                JSONObject d = Rappels.donnees(this);
                String yt = "", t = "";
                String key = d.optString("liveKey", "");
                if (!key.isEmpty()) {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(
                            "https://abacus.jasoncameron.dev/get/cefstv-kpan/" + key).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(8000);
                    try (java.io.InputStream in = c.getInputStream()) {
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[1024]; int n; while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        long v = new JSONObject(out.toString("UTF-8")).optLong("value", 0);
                        if (v > 0) {
                            String[] T = {"Émission en direct", "Culte en direct", "Prière en direct", "Enseignement en direct", "Émission spéciale", "Veillée de prière"};
                            int ti = v < 100 ? (int) v - 1 : (int) (v % 100);
                            yt = String.valueOf(v); t = ti >= 0 && ti < T.length ? T[ti] : T[0];
                        }
                    } catch (Exception ignored) {
                    } finally { c.disconnect(); }
                }
                JSONObject live = d.optJSONObject("live");
                if (yt.isEmpty() && live != null && live.optBoolean("on")) {
                    yt = live.optString("yt", "").isEmpty() ? live.optString("cam", "") : live.optString("yt", "");
                    t = live.optString("title", "");
                }
                String last = Rappels.prefs(this).getString("direct", "");
                if (!yt.isEmpty() && !yt.equals(last)) {
                    Rappels.montrer(this, 20, "🔴 En direct maintenant sur CEFS TV",
                            (t.isEmpty() ? "Une émission en direct" : "« " + t + " »") + " — rejoignez-nous !");
                }
                if (!yt.equals(last)) Rappels.prefs(this).edit().putString("direct", yt).apply();
            } catch (Exception ignored) {
            } finally {
                jobFinished(params, false);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }
}
