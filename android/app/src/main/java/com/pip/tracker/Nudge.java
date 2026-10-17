package com.pip.tracker;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Check-ins that live on the phone. The web app works out what should happen in the next day or so
 * and hands over a list; this class sets the alarms, checks the conditions again when each one goes
 * off, and posts the notification. Buttons are handled here too, so one tap logs without opening the app.
 */
final class Nudge {
    private Nudge() {}

    static final String CH = "pip_nudges";
    static volatile boolean foreground = false;
    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("nudge", Context.MODE_PRIVATE); }

    static JSONArray items(Context c) {
        try { return new JSONArray(sp(c).getString("items", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    static JSONObject snap(Context c) {
        try { return new JSONObject(sp(c).getString("snap", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static JSONObject find(Context c, int id) {
        JSONArray a = items(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optInt("id") == id) return o;
        }
        return null;
    }

    // ---------- alarms ----------
    private static PendingIntent alarmPi(Context c, int id, boolean snoozed) {
        Intent i = new Intent(c, NudgeReceiver.class).setAction("pip.nudge." + id).putExtra("id", id).putExtra("snoozed", snoozed);
        return PendingIntent.getBroadcast(c, id, i, FLAGS);
    }

    static void arm(Context c, int id, long at, boolean snoozed) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = alarmPi(c, id, snoozed);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    static void cancelAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        JSONArray a = items(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) am.cancel(alarmPi(c, o.optInt("id"), false));
        }
    }

    /** Sets an alarm for every item still in the future. Used after a sync and after a reboot. */
    static void rebuild(Context c) {
        long now = System.currentTimeMillis();
        JSONArray a = items(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optLong("at") > now) arm(c, o.optInt("id"), o.optLong("at"), false);
        }
        // snoozed ones survive a re-sync
        JSONObject z = snoozed(c);
        java.util.Iterator<String> ks = z.keys();
        while (ks.hasNext()) {
            JSONObject e = z.optJSONObject(ks.next());
            if (e != null && e.optLong("at") > now) arm(c, e.optJSONObject("item").optInt("id"), e.optLong("at"), true);
        }
    }

    private static JSONObject snoozed(Context c) {
        try { return new JSONObject(sp(c).getString("snz", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static void snooze(Context c, int id, long at) {
        try {
            JSONObject it = find(c, id);
            JSONObject z = snoozed(c);
            if (it == null) { JSONObject old = z.optJSONObject(String.valueOf(id)); if (old != null) it = old.optJSONObject("item"); }
            if (it == null) return;
            long now = System.currentTimeMillis();
            java.util.Iterator<String> ks = z.keys();
            java.util.ArrayList<String> dead = new java.util.ArrayList<>();
            while (ks.hasNext()) { String k = ks.next(); JSONObject e = z.optJSONObject(k); if (e == null || e.optLong("at") < now - 3600000L) dead.add(k); }
            for (String k : dead) z.remove(k);
            z.put(String.valueOf(id), new JSONObject().put("at", at).put("item", it));
            sp(c).edit().putString("snz", z.toString()).apply();
            arm(c, id, at, true);
        } catch (Exception ignored) {}
    }

    // ---------- conditions, checked again at the moment it fires ----------
    private static String today() { return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()); }

    private static int dayCount(Context c) {
        SharedPreferences p = sp(c);
        return today().equals(p.getString("cntDay", "")) ? p.getInt("cnt", 0) : 0;
    }

    private static boolean tappedNight(Context c, String key) {
        try {
            JSONArray t = new JSONArray(sp(c).getString("taps", "[]"));
            for (int i = 0; i < t.length(); i++) {
                JSONObject o = t.optJSONObject(i);
                if (o != null && key.equals(o.optString("night") + "|" + o.optString("nd"))) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean condOk(Context c, JSONObject it) {
        JSONObject cond = it.optJSONObject("cond");
        if (cond == null) return true;
        SharedPreferences p = sp(c);
        JSONObject s = snap(c);
        long now = System.currentTimeMillis();
        long lastAct = Math.max(s.optLong("lastLogAt", 0), p.getLong("lastTapAt", 0));
        String rc = s.optString("runningCol", "");
        if (cond.has("skipIfLogAfter") && lastAct > cond.optLong("skipIfLogAfter")) return false;
        if (cond.has("minIdleMs") && now - Math.max(lastAct, p.getLong("lastNudgeAt", 0)) < cond.optLong("minIdleMs")) return false;
        if (cond.optBoolean("skipIfRunning", false) && !rc.isEmpty()) return false;
        if (cond.has("runningIs") && !rc.equals(cond.optString("runningIs"))) return false;
        if (cond.has("runningIsNot") && rc.equals(cond.optString("runningIsNot"))) return false;
        if (cond.has("runningStart") && s.optLong("runningStart", 0) != cond.optLong("runningStart")) return false;
        if (cond.has("skipIfTapNight") && tappedNight(c, cond.optString("skipIfTapNight"))) return false;
        if (cond.has("maxPerDay") && dayCount(c) >= cond.optInt("maxPerDay")) return false;
        return true;
    }

    static void fire(Context c, int id, boolean snoozed) {
        JSONObject it = null;
        if (snoozed) {
            JSONObject e = snoozed(c).optJSONObject(String.valueOf(id));
            if (e != null) it = e.optJSONObject("item");
        }
        if (it == null) it = find(c, id);
        if (it == null || foreground) return;   // while the app is open, the page shows its own card
        if (!snoozed && !condOk(c, it)) return;
        post(c, it);
        String k = it.optString("kind");
        if (k.equals("idle") || k.equals("leaves")) {
            SharedPreferences p = sp(c);
            p.edit().putString("cntDay", today()).putInt("cnt", dayCount(c) + 1).putLong("lastNudgeAt", System.currentTimeMillis()).apply();
        }
    }

    // ---------- showing it ----------
    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH, "Check-ins", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Friendly one-tap nudges from Pip");
        nm.createNotificationChannel(ch);
    }

    @SuppressLint("MissingPermission")
    static void post(Context c, JSONObject it) {
        ensureChannel(c);
        int id = it.optInt("id");
        String body = it.optString("body");
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_stat_pip)
            .setColor(0xFFF4B740)
            .setContentTitle(it.optString("title"))
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(PendingIntent.getActivity(c, 500000 + id, open, FLAGS));
        JSONArray acts = it.optJSONArray("actions");
        int n = acts == null ? 0 : Math.min(3, acts.length());
        for (int k = 0; k < n; k++) {
            JSONObject a = acts.optJSONObject(k);
            if (a == null) continue;
            String kind = a.optString("kind", "dismiss");
            PendingIntent pi;
            if (kind.equals("open")) {
                Intent oi = new Intent(c, MainActivity.class).setAction("pip.open." + id + "." + k)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("pip_act", a.optString("act", "")).putExtra("pip_go", a.optString("go", ""));
                pi = PendingIntent.getActivity(c, 600000 + id * 10 + k, oi, FLAGS);
            } else {
                JSONObject pl = a.optJSONObject("payload");
                Intent ai = new Intent(c, NudgeActionReceiver.class).setAction("pip.act." + id + "." + k)
                    .putExtra("id", id).putExtra("kind", kind).putExtra("mins", a.optInt("mins", 0))
                    .putExtra("payload", pl == null ? "{}" : pl.toString());
                pi = PendingIntent.getBroadcast(c, 700000 + id * 10 + k, ai, FLAGS);
            }
            b.addAction(0, a.optString("label"), pi);
        }
        NotificationManagerCompat.from(c).notify(id, b.build());
    }

    // ---------- taps made on a notification, waiting for the app to pick them up ----------
    static void addTap(Context c, String payload, long ts) {
        try {
            JSONObject p = new JSONObject(payload);
            p.put("ts", ts);
            JSONArray t = new JSONArray(sp(c).getString("taps", "[]"));
            t.put(p);
            JSONObject s = snap(c);
            if (p.has("col")) { s.put("runningCol", p.getString("col")); s.put("runningStart", ts); }
            else if ("end".equals(p.optString("act"))) { s.put("runningCol", ""); s.put("runningStart", 0); }
            sp(c).edit().putString("taps", t.toString()).putString("snap", s.toString()).putLong("lastTapAt", ts).apply();
        } catch (Exception ignored) {}
    }
}
