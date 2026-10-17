package com.pip.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationManagerCompat;

/** A button on a notification was tapped. This runs even if the app is closed. */
public class NudgeActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int id = i.getIntExtra("id", 0);
        String kind = i.getStringExtra("kind");
        NotificationManagerCompat.from(c).cancel(id);
        long now = System.currentTimeMillis();
        if ("tap".equals(kind)) Nudge.addTap(c, i.getStringExtra("payload"), now);
        else if ("snooze".equals(kind)) Nudge.snooze(c, id, now + Math.max(1, i.getIntExtra("mins", 15)) * 60000L);
    }
}
