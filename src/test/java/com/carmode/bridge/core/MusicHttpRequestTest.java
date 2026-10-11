package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import static org.junit.jupiter.api.Assertions.*;

class MusicHttpRequestTest {
    @Test void defaultMatchesMacro() {
        assertEquals("http://192.168.43.239:8080/test", MusicHttpRequest.DEFAULT_URL);
        assertEquals(1200, MusicHttpRequest.DELAY_MS);
    }
    @Test void acceptsTrimmedHttpAndHttps() {
        assertEquals("http", MusicHttpRequest.validate("  http://127.0.0.1:8080/test  ").getProtocol());
        assertEquals("https", MusicHttpRequest.validate("https://example.test/play").getProtocol());
    }
    @Test void rejectsNonHttpInvalidAndCredentialUrls() {
        for (String value : new String[]{"", "file:///tmp/test", "ftp://example.test/test", "http:///test", "http://user:pass@example.test/test",
                "http://example.test/test#fragment", "http://example.test:0/test", "http://example.test:65536/test", "http://bad host/test"})
            assertThrows(IllegalArgumentException.class, () -> MusicHttpRequest.validate(value), value);
    }
    @Test void onlyMusicAfterSuccessfulLaunchCanTrigger() {
        for (ActionDispatcher.Action action : ActionDispatcher.Action.values())
            assertEquals(action == ActionDispatcher.Action.MUSIC, MusicHttpRequest.forSelection(action, true, true));
    }
    @Test void disabledAndFailedLaunchNeverTrigger() {
        assertFalse(MusicHttpRequest.forSelection(ActionDispatcher.Action.MUSIC, true, false));
        assertFalse(MusicHttpRequest.forSelection(ActionDispatcher.Action.MUSIC, false, true));
    }
    @Test void sendsOneGetToLocalFixture() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/test", exchange -> {
            requests.incrementAndGet();
            if (!"GET".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); }
            else exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            assertEquals(204, MusicHttpRequest.send(new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/test")));
            assertEquals(1, requests.get());
        } finally { server.stop(0); }
    }
    @Test void reportsHttpErrorWithoutRetry() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/test", exchange -> { requests.incrementAndGet(); exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            assertEquals(503, MusicHttpRequest.send(new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/test")));
            assertEquals(1, requests.get());
        } finally { server.stop(0); }
    }
    @Test void doesNotFollowRedirects() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger redirected = new AtomicInteger();
        server.createContext("/test", exchange -> {
            exchange.getResponseHeaders().add("Location", "/other"); exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/other", exchange -> { redirected.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
        server.start();
        try {
            assertEquals(302, MusicHttpRequest.send(new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/test")));
            assertEquals(0, redirected.get());
        } finally { server.stop(0); }
    }
    @Test void setsTimeoutsAndDisconnectsOnFailure() throws Exception {
        final HttpURLConnection[] created = new HttpURLConnection[1];
        URL url = new URL(null, "http://fixture.test/", new URLStreamHandler() {
            protected URLConnection openConnection(URL target) {
                created[0] = new HttpURLConnection(target) {
                    public void disconnect() { disconnected = true; }
                    public boolean usingProxy() { return false; }
                    public void connect() {}
                    public int getResponseCode() throws IOException { throw new SocketTimeoutException(); }
                };
                return created[0];
            }
        });
        disconnected = false;
        assertThrows(SocketTimeoutException.class, () -> MusicHttpRequest.send(url));
        assertTrue(disconnected);
        assertEquals(3000, created[0].getConnectTimeout()); assertEquals(5000, created[0].getReadTimeout());
        assertFalse(created[0].getInstanceFollowRedirects()); assertFalse(created[0].getUseCaches());
    }
    private boolean disconnected;
}
