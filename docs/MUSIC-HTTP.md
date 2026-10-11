# Optional Music HTTP request — 0.1.17

The user clarified that their phone receives this request and offers to start music. Version 0.1.16 did not send it. Version 0.1.17 implements an outgoing request after the Music destination is successfully requested, independent of MacroDroid on the tablet. The existing phone receiver/server is still required. A successful Android Activity request does not prove foreground display; an HTTP 2xx does not prove playback or acceptance of the phone prompt.

On Current, below the Music app picker:

- “Send music HTTP request after launch”, off by default; disabling cancels an unsent delayed request.
- An address editor, initially `http://192.168.43.239:8080/test` from Music.macro. Verify the actual phone address. HTTP(S) only; embedded credentials and URL fragments are rejected. The address is included in exported settings, so do not embed secrets in it.

Selecting Music requests its configured application first, then schedules a GET after 1200 ms (the macro's post-ZLink delay). There is no request on boot/Home, camera/maps, app assignment, failed launch or when the switch is off. A successful camera/maps selection cancels a still-pending music request. Preferences are rechecked before sending. The operation is app-owned rather than settings-Activity-owned, and network I/O runs on a separate thread.

Only one request is in flight; additional sends during that operation are skipped and logged. No automatic retry, redirects, request body, log upload, media-key injection, Back actions, volume changes or foreign-app stopping are added. Unlike the macro's 30-second timeout and redirect-following option, this implementation uses 3-second connect and 5-second read timeouts and does not follow redirects. This bounds waiting and avoids duplicate/redirected commands. Disabling cannot retract an already-sent command, and process death can cancel unsent work without replay.

INTERNET permission and cleartext support are declared because the supplied address is HTTP, not HTTPS. Only the opt-in Music action performs networking. HTTP is unencrypted: use a trusted network, or a valid HTTPS endpoint. Standard certificate/hostname verification is retained; no permissive TLS bypass exists. These options do not create a receiver on the phone or make its old address current.

Events: `music_http_scheduled`, `music_http_started`, `music_http_result`, `music_http_error`, `music_http_cancelled`, `music_http_skipped`. Logs contain request IDs, host, result codes and exception class, not response content. `playback_confirmed` stays false. The response is closed without collecting its body.

## Test

1. Install over the previous APK. Home proxy, assignments and permissions are preserved, including 0.1.16's visual fixes.
2. Ensure the phone server is running and reachable from the tablet; edit the address if it changed. Turn on the switch.
3. Select Music with two MODE presses and wait for the two-second menu countdown plus 1.2 seconds. Check the phone prompt.
4. Switch off and repeat: Music still opens, but the phone receives no new request. Camera/maps/Home should not trigger it either.
5. If it fails, export JSON; HTTP status/errors identify the network result, not playback state.

JVM tests use only a local loopback fixture, never the user's phone/server. They cover GET delivery, non-2xx results, no redirects/retries, timeouts/cleanup, URL validation and Music-only successful-launch gating. Real Android networking and phone prompts require device testing.
