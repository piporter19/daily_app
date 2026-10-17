package com.pip.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms disappear when the phone restarts or the app updates, so set them again. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Nudge.rebuild(c);
    }
}
