package com.carmode.bridge;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;

/** Synchronous, bounded boot history independent of normal logger and crash recovery. */
final class BootEvidence {
    static synchronized JSONArray history(Context context) {
        try { return new JSONArray(BootStorage.device(context).getSharedPreferences("boot_evidence", 0).getString("history", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    static synchronized void record(Context context, String stage, String reason) {
        try {
            JSONArray items = history(context);
            while (items.length() >= 32) items.remove(0);
            items.put(new JSONObject().put("stage", stage).put("reason", reason)
                    .put("timestamp", System.currentTimeMillis()).put("elapsed_ms", SystemClock.elapsedRealtime())
                    .put("user_unlocked", BootStorage.unlocked(context)).put("storage_ready", BootStorage.ready(context))
                    .put("pid", android.os.Process.myPid()));
            if (!BootStorage.device(context).getSharedPreferences("boot_evidence", 0).edit().putString("history", items.toString()).commit())
                android.util.Log.e("CarModeBoot", "Boot evidence write failed");
        } catch (Exception e) { android.util.Log.e("CarModeBoot", "Boot evidence unavailable", e); }
    }
}
