package com.carmode.bridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AppResolver {
    public static final class Entry {
        public final String label;
        public final ComponentName component;
        Entry(String label, ComponentName component) { this.label = label; this.component = component; }
        @Override public String toString() { return label + "\n" + component.flattenToShortString(); }
    }
    public static List<Entry> list(Context context, boolean allActivities) {
        PackageManager pm = context.getPackageManager();
        List<Entry> entries = new ArrayList<>();
        for (PackageInfo pkg : pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)) {
            if (pkg.packageName.equals(context.getPackageName()) || pkg.activities == null) continue;
            Intent launcher = pm.getLaunchIntentForPackage(pkg.packageName);
            for (ActivityInfo activity : pkg.activities) {
                ComponentName component = new ComponentName(activity.packageName, activity.name);
                if (!allActivities && (launcher == null || !component.equals(launcher.getComponent()))) continue;
                if (!launchable(context, activity)) continue;
                entries.add(new Entry(activity.loadLabel(pm).toString(), component));
            }
        }
        entries.sort(Comparator.comparingInt((Entry e) -> candidate(e) ? 0 : 1)
                .thenComparing(e -> e.label.toLowerCase(Locale.ROOT))
                .thenComparing(e -> e.component.flattenToString()));
        return entries;
    }
    static boolean candidate(Entry entry) {
        String name = (entry.label + " " + entry.component.flattenToString()).toLowerCase(Locale.ROOT);
        return name.contains("zlink") || name.contains("360") || name.contains("camera") || name.contains("av-in");
    }
    private static boolean launchable(Context context, ActivityInfo activity) {
        PackageManager pm = context.getPackageManager();
        int state = pm.getComponentEnabledSetting(new ComponentName(activity.packageName, activity.name));
        boolean enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && activity.enabled);
        return enabled && activity.applicationInfo.enabled && activity.exported
                && (activity.permission == null || context.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED);
    }
    public static Intent resolve(Context context, String flattened) throws PackageManager.NameNotFoundException {
        if (!flattened.contains("/")) {
            if (flattened.equals(context.getPackageName())) throw new IllegalArgumentException(I18n.text(context, R.string.app_not_assigned));
            Intent launcher = context.getPackageManager().getLaunchIntentForPackage(flattened);
            if (launcher == null || launcher.getComponent() == null)
                throw new IllegalArgumentException(I18n.text(context, R.string.default_app_unavailable, flattened));
            ActivityInfo info = context.getPackageManager().getActivityInfo(launcher.getComponent(), 0);
            if (!launchable(context, info)) throw new IllegalArgumentException(I18n.text(context, R.string.activity_unavailable));
            return launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        ComponentName component = ComponentName.unflattenFromString(flattened);
        if (component == null || component.getPackageName().equals(context.getPackageName())) throw new IllegalArgumentException(I18n.text(context, R.string.app_not_assigned));
        ActivityInfo info = context.getPackageManager().getActivityInfo(component, 0);
        if (!launchable(context, info)) throw new IllegalArgumentException(I18n.text(context, R.string.activity_unavailable));
        return new Intent(Intent.ACTION_MAIN).setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
