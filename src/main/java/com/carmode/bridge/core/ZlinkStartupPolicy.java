package com.carmode.bridge.core;

/** Persist the claim BEFORE launching. Unknown boot identity is deliberately conservative. */
public final class ZlinkStartupPolicy {
    public interface Store {
        String last();
        boolean save(String boot);
    }
    private ZlinkStartupPolicy() {}
    public static String epoch(int bootCount) { return bootCount < 0 ? "unknown" : "boot:" + bootCount; }
    public static boolean claim(boolean enabled, boolean recovering, boolean foregroundHome,
                                String epoch, Store store) {
        if (!enabled || recovering || !foregroundHome || epoch.equals(store.last())) return false;
        return store.save(epoch);
    }
}
