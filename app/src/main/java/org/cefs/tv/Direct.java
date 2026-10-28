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
                JSONObject live = d.optJSONObject("live");
                String yt = live != null && live.optBoolean("on") ? live.optString("yt", "") : "";
                String last = Rappels.prefs(this).getString("direct", "");
                if (!yt.isEmpty() && !yt.equals(last)) {
                    String t = live.optString("title", "");
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
