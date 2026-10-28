package com.carmode.bridge;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.accessibility.AccessibilityManager;
import org.json.JSONArray;
import org.json.JSONObject;

/** Permission evidence, not a claim that another filter consumed a key. */
final class DiagnosticState {
    static JSONObject capture(Context context) {
        JSONObject state = new JSONObject();
        JSONArray errors = new JSONArray(), services = new JSONArray(), filters = new JSONArray();
        try {
            state.put("home_proxy", HomeBridge.snapshot(context));
            state.put("accessibility_connected", ModeService.active() != null);
            state.put("accessibility_lifecycle", ServiceEvidence.capture(context));
            state.put("previous_accessibility_callback", ServiceEvidence.previous(context));
            state.put("overlay_allowed", Settings.canDrawOverlays(context));
            state.put("camera_boot_return", CameraBootReturn.snapshot(context));
            AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
            if (manager != null) for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                boolean own = info.getResolveInfo().serviceInfo.packageName.equals(context.getPackageName());
                boolean filter = (info.flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0;
                JSONObject item = new JSONObject();
                item.put("id", info.getId()); item.put("flags", info.flags); item.put("capabilities", info.getCapabilities());
                item.put("requests_key_filter", filter); services.put(item);
                if (!own && filter) filters.put(info.getId());
            }
            ModeService service = ModeService.active();
            state.put("hcn_mode", ModeRuntimeService.active() == null ? new JSONObject().put("receiver_registered", false)
                    .put("enabled", CarModeApp.from(context).prefs().hcnMode()) : ModeRuntimeService.active().hcnSnapshot());
            AccessibilityServiceInfo own = service == null ? null : service.getServiceInfo();
            state.put("can_retrieve_window_content", own == null ? JSONObject.NULL :
                    (own.getCapabilities() & AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) != 0);
            state.put("retrieve_interactive_windows", own == null ? JSONObject.NULL :
                    (own.flags & AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS) != 0);
            state.put("can_filter_keys", own == null ? JSONObject.NULL :
                    (own.getCapabilities() & AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS) != 0);
            state.put("requests_key_filter", own == null ? JSONObject.NULL :
                    (own.flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0);
        } catch (Exception e) { errors.put(e.getClass().getSimpleName()); }
        JSONArray devices = new JSONArray();
        try {
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice device = InputDevice.getDevice(id);
                if (device == null) continue; // May have disconnected between the two queries.
                JSONObject item = new JSONObject();
                item.put("deviceId", device.getId()); item.put("name", device.getName());
                item.put("sources", device.getSources()); item.put("keyboard_type", device.getKeyboardType());
                item.put("supports_keyboard_source", device.supportsSource(InputDevice.SOURCE_KEYBOARD));
                item.put("virtual", device.isVirtual()); item.put("vendor_id", device.getVendorId());
                item.put("product_id", device.getProductId()); devices.put(item);
            }
        } catch (Exception e) {
            try { state.put("input_device_query_error", e.getClass().getSimpleName()); }
            catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
        }
        try {
            state.put("input_devices", devices);
            state.put("enabled_services", services); state.put("other_key_filters", filters);
            state.put("permission_query_errors", errors);
        } catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
        return state;
    }
}
