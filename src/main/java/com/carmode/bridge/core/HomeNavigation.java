package com.carmode.bridge.core;

/** Only recognized navigation requests are diverted; a normal app-icon launch stays settings. */
public final class HomeNavigation {
    public static final String MAIN = "android.intent.action.MAIN";
    public static final String ALL_APPS = "android.intent.action.ALL_APPS";
    public static final String SHOW_PAGE = "extra.show_page";
    private HomeNavigation() {}
    public static boolean allApps(String action, String page) {
        return ALL_APPS.equals(action) || (MAIN.equals(action) && "allapps".equals(page));
    }
    public static boolean navigation(String action, boolean homeCategory, String page) {
        return allApps(action, page) || (MAIN.equals(action) && homeCategory);
    }
    public static String forwardAction(String action, String page) {
        return allApps(action, page) ? ALL_APPS : MAIN;
    }
}
