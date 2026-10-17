package com.pip.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NudgeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Nudge.fire(c, i.getIntExtra("id", 0), i.getBooleanExtra("snoozed", false));
    }
}
