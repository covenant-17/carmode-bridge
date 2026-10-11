package com.carmode.bridge.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;

/** One GET, no retries/redirects, response content is neither collected nor logged. */
public final class MusicHttpRequest {
    public static final String DEFAULT_URL = "http://192.168.43.239:8080/test";
    public static final int DELAY_MS = 1200;
    private MusicHttpRequest() {}
    public static boolean forSelection(ActionDispatcher.Action action, boolean launchSucceeded, boolean enabled) {
        return action == ActionDispatcher.Action.MUSIC && launchSucceeded && enabled;
    }
    public static URL validate(String value) {
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0 || uri.getPort() < -1)
                throw new IllegalArgumentException("Expected HTTP(S) URL without credentials or fragment");
            return uri.toURL();
        } catch (Exception e) { throw new IllegalArgumentException("Invalid music HTTP URL", e); }
    }
    public static int send(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000); connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            int code = connection.getResponseCode();
            // Close streams without downloading/logging a potentially large or private response.
            InputStream response = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (response != null) response.close();
            return code;
        } finally { connection.disconnect(); }
    }
}
