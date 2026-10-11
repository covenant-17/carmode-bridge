package com.carmode.bridge;

import android.content.Context;
import android.os.UserManager;
import java.io.File;
import java.io.IOException;
import com.carmode.bridge.core.StorageMigration;

/** One storage domain for UI and the pre-unlock runtime. Never overwrite migrated data. */
final class BootStorage {
    private static Context credentialContext;
    static Context device(Context context) { return context.createDeviceProtectedStorageContext(); }
    static boolean unlocked(Context context) { return context.getSystemService(UserManager.class).isUserUnlocked(); }
    static boolean ready(Context context) {
        return device(context).getSharedPreferences("boot_storage", 0).getBoolean("ready", false);
    }
    static void prepare(Context context) {
        // Retain Android's original application base; the reverse storage-context API is hidden.
        if (!context.isDeviceProtectedStorage()) credentialContext = context;
        Context target = device(context);
        if (ready(target) || !unlocked(target)) return;
        Context source = credentialContext;
        if (source == null) throw new IllegalStateException("Credential storage context unavailable");
        try {
            for (String name : new String[]{"settings", "diagnostic_metadata", "service_lifecycle"}) {
                File old = new File(source.getDataDir(), "shared_prefs/" + name + ".xml");
                File existing = new File(target.getDataDir(), "shared_prefs/" + name + ".xml");
                boolean hasOld = old.exists() || new File(old.getPath() + ".bak").exists();
                boolean hasTarget = existing.exists() || new File(existing.getPath() + ".bak").exists();
                if (hasOld && !hasTarget && !target.moveSharedPreferencesFrom(source, name))
                    throw new IOException("Cannot migrate preferences: " + name);
            }
            for (String name : new String[]{"events.jsonl", "events.previous.jsonl", "crashes"})
                StorageMigration.move(new File(source.getFilesDir(), name), new File(target.getFilesDir(), name));
            if (!target.getSharedPreferences("boot_storage", 0).edit().putBoolean("ready", true).commit())
                throw new IOException("Cannot persist storage migration marker");
        } catch (IOException e) { throw new IllegalStateException("Direct Boot storage migration failed", e); }
    }
}
