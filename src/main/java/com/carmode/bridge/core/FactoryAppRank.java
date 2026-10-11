package com.carmode.bridge.core;

import java.util.Locale;

/** Discovery hints only; never assume a specific OEM package name or hide other apps. */
public final class FactoryAppRank {
    private FactoryAppRank() { }
    public static int rank(String label, String packageName) {
        String name = (label + " " + packageName).toLowerCase(Locale.ROOT);
        if (name.contains("factory") || name.contains("carsetting") || name.contains("car setting")
                || name.contains("завод") || name.contains("автонастр")) return 0;
        if (name.contains("setting") || name.contains("настрой")) return 1;
        return 2;
    }
}
