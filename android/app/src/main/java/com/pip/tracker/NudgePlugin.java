package com.pip.tracker;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

@CapacitorPlugin(name = "Nudge")
public class NudgePlugin extends Plugin {

    /** Replace the whole schedule: the list of upcoming notifications and a snapshot of what the app knows. */
    @PluginMethod
    public void sync(PluginCall call) {
        Context c = getContext();
        Nudge.cancelAll(c);
        JSArray items = call.getArray("items");
        JSObject snap = call.getObject("snapshot");
        Nudge.sp(c).edit()
            .putString("items", items == null ? "[]" : items.toString())
            .putString("snap", snap == null ? "{}" : snap.toString()).apply();
        Nudge.rebuild(c);
        call.resolve();
    }

    @PluginMethod
    public void drainTaps(PluginCall call) {
        Context c = getContext();
        JSArray out = new JSArray();
        try {
            JSONArray t = new JSONArray(Nudge.sp(c).getString("taps", "[]"));
            for (int i = 0; i < t.length(); i++) out.put(t.get(i));
        } catch (Exception ignored) {}
        Nudge.sp(c).edit().putString("taps", "[]").apply();
        JSObject r = new JSObject();
        r.put("taps", out);
        call.resolve(r);
    }

    @PluginMethod
    public void getLaunchAct(PluginCall call) {
        Context c = getContext();
        JSObject r = new JSObject();
        String act = Nudge.sp(c).getString("launchAct", "");
        r.put("act", act);
        r.put("go", Nudge.sp(c).getString("launchGo", ""));
        Nudge.sp(c).edit().remove("launchAct").remove("launchGo").apply();
        call.resolve(r);
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        Context c = getContext();
        JSObject r = new JSObject();
        r.put("notifications", NotificationManagerCompat.from(c).areNotificationsEnabled());
        boolean exact = true;
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            exact = am != null && am.canScheduleExactAlarms();
        }
        r.put("exact", exact);
        call.resolve(r);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getActivity() != null)
            ActivityCompat.requestPermissions(getActivity(), new String[]{"android.permission.POST_NOTIFICATIONS"}, 4242);
        call.resolve();
    }

    @PluginMethod
    public void openSettings(PluginCall call) {
        Context c = getContext();
        Intent i;
        if ("alarms".equals(call.getString("which", "notifications")) && Build.VERSION.SDK_INT >= 31)
            i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + c.getPackageName()));
        else
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void test(PluginCall call) {
        try {
            JSONObject it = new JSONObject();
            it.put("id", 999);
            it.put("kind", "test");
            it.put("title", "Pip is connected 🌱");
            it.put("body", "Check-ins will arrive like this, even when the app is closed.");
            JSONArray a = new JSONArray();
            a.put(new JSONObject().put("label", "Open my tracker").put("kind", "open").put("act", ""));
            a.put(new JSONObject().put("label", "Thanks").put("kind", "dismiss"));
            it.put("actions", a);
            Nudge.post(getContext(), it);
            call.resolve();
        } catch (Exception e) { call.reject("failed"); }
    }
}
