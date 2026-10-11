package com.carmode.bridge;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/** Explicit app language, independent of the head unit's system language. */
final class I18n {
    private I18n() {}
    static Context wrap(Context context) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(new Locale(new Prefs(context).language()));
        return context.createConfigurationContext(config);
    }
    static String text(Context context, int resource, Object... args) {
        return wrap(context).getString(resource, args);
    }
}
