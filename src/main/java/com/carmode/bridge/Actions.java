package com.carmode.bridge;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import com.carmode.bridge.core.ActionDispatcher;
import com.carmode.bridge.core.ActionDispatcher.Action;

public final class Actions implements ActionDispatcher.Port {
    public static final int[] LABELS = {R.string.camera, R.string.music, R.string.navigation};
    private final Context context;
    private final CarModeApp app;
    public Actions(Context context) { this.context = context; app = CarModeApp.from(context); }
    public void select(int index, String source) {
        Action action = Action.values()[index];
        app.log().record("native_action_requested", "action", action.name(), "source", source);
        new ActionDispatcher(this).select(action);
    }
    public void menu(boolean opened, String source, String reason) {
        app.log().record(opened ? "menu_open" : "menu_close", "source", source, "reason", reason);
    }
    @Override public void launch(Action action) {
        // End the old session before resolving camera; show a Camry transition session only after launch succeeds.
        if (action == Action.CAMERAS) DockService.hide(context);
        String component = ActionDispatcher.target(action, app.prefs().component(action));
        try {
            Intent intent = AppResolver.resolve(context, component);
            // Match the camera macro's Recents behavior for the default camera only.
            if (action == Action.CAMERAS && app.prefs().component(action).trim().isEmpty())
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            boolean zlink = CamryNavigation.isZlinkTarget(context, intent);
            boolean homeOnly = CamryNavigation.homeOnly(context, intent);
            boolean cameraWindow = CamryNavigation.cameraWindow(context, intent);
            if (zlink || cameraWindow)
                CamryNavigation.launch(context, intent, null);
            else context.startActivity(intent);
            if ((action == Action.CAMERAS && !cameraWindow) || (zlink && !homeOnly))
                DockService.transition(context);
            // Cancel an unsent music request when another destination is selected successfully.
            if (action != Action.MUSIC) app.musicHttp().cancel("other_action_selected");
            app.log().record("launch_requested", "action", action.name(), "target", component,
                    "component", intent.getComponent().flattenToString(), "flags", intent.getFlags(),
                    "foreground_confirmed", false);
            if (com.carmode.bridge.core.MusicHttpRequest.forSelection(action, true, app.prefs().musicHttpEnabled()))
                app.musicHttp().afterMusicLaunch();
        } catch (Exception e) { error("launch_error", e); }
    }
    private void error(String type, Exception e) {
        app.log().record(type, "error", e.getClass().getSimpleName(), "message", e.getMessage());
        Toast.makeText(context, I18n.text(context, R.string.action_failed, e.getMessage()), Toast.LENGTH_LONG).show();
    }
}
