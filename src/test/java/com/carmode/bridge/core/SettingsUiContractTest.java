package com.carmode.bridge.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source-level UI wiring guards; Android interaction still needs device verification. */
class SettingsUiContractTest {
    @Test void settingsAndModeMenuDefaultToEnglishUnlessRussianWasSelected() throws Exception {
        String prefs = new String(Files.readAllBytes(Paths.get("src/main/java/com/carmode/bridge/Prefs.java")), StandardCharsets.UTF_8);
        String i18n = new String(Files.readAllBytes(Paths.get("src/main/java/com/carmode/bridge/I18n.java")), StandardCharsets.UTF_8);
        String menu = new String(Files.readAllBytes(Paths.get("src/main/java/com/carmode/bridge/MenuView.java")), StandardCharsets.UTF_8);
        assertTrue(prefs.contains("\"ru\".equals(data.getString(\"language\", \"en\")) ? \"ru\" : \"en\""));
        assertTrue(i18n.contains("config.setLocale(new Locale(new Prefs(context).language()))"));
        assertTrue(source().contains("I18n.wrap(base)"));
        assertTrue(menu.contains("Context localized = I18n.wrap(context)"));
        assertTrue(menu.contains("localized.getString(Actions.LABELS[i])"));
    }
    @Test void successfulModeLaunchIsSilentButFailuresAndLogsRemain() throws Exception {
        String actions = new String(Files.readAllBytes(Paths.get("src/main/java/com/carmode/bridge/Actions.java")), StandardCharsets.UTF_8);
        String launch = actions.substring(actions.indexOf("@Override public void launch"), actions.indexOf("private void error("));
        assertFalse(launch.contains("Toast.makeText"));
        assertFalse(actions.contains("opening_zlink"));
        assertTrue(launch.contains("\"launch_requested\""));
        assertTrue(launch.contains("afterMusicLaunch()"));
        assertTrue(actions.contains("R.string.action_failed"));
    }
    private String source() throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/carmode/bridge/MainActivity.java")), StandardCharsets.UTF_8);
    }
    @Test void apkExportControlsShareTheArchiveOnlyBlock() throws Exception {
        String source = source();
        source = source.substring(source.indexOf("private void render()"), source.indexOf("private void confirmClearLogs()"));
        int start = source.indexOf("if (archived) {", source.indexOf("button(getString(R.string.export_log)"));
        assertTrue(start >= 0);
        int depth = 1, end = source.indexOf('{', start) + 1;
        while (depth > 0 && end < source.length()) {
            char c = source.charAt(end++);
            if (c == '{') depth++;
            if (c == '}') depth--;
        }
        String block = source.substring(start, end);
        for (String name : new String[]{"copy_zlink_apk", "share_zlink_apk", "copy_factory_apk", "share_factory_apk"}) {
            assertTrue(block.contains("R.string." + name));
            assertEquals(source.indexOf("R.string." + name), source.lastIndexOf("R.string." + name));
        }
    }
    @Test void explanationsAreHelpActionsNotPersistentLabels() throws Exception {
        String source = source();
        for (String name : new String[]{"intro", "auto_start_hint", "background_hint", "home_hint", "hcn_hint",
                "archive_hint", "simulate_hint", "diagnostics_hint", "oem_diagnostics_hint", "test_hint",
                "hcn_test_hint", "test_keyboard_hint", "export_oem_hint", "export_zlink_hint",
                "export_factory_hint", "selection_hint", "music_http_hint"}) {
            assertTrue(source.contains("hint(R.string." + name + ")"), name);
            assertFalse(source.contains("label(getString(R.string." + name + "))"), name);
        }
    }
    @Test void helpUsesNativeNonModalToastAndAccessibleTouchTarget() throws Exception {
        String source = source();
        String helper = source.substring(source.indexOf("private void hint("), source.indexOf("private void heading("));
        assertTrue(helper.contains("Toast.makeText"));
        assertTrue(helper.contains("hintToast.cancel()"));
        assertTrue(helper.contains("setTooltipText"));
        assertTrue(helper.contains("setContentDescription"));
        assertTrue(helper.contains("dp(48), dp(48)"));
        assertFalse(helper.contains("AlertDialog"));
    }
}
