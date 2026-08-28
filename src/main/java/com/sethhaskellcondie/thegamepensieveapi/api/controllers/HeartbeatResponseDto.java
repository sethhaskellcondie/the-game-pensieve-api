package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

/**
 * Health-check payload. {@code secureMode} reports whether the backend is running the authenticated
 * ({@code secured} profile) build — {@code true} means protected routes require a Bearer token — or the default
 * permit-all build. Callers use it to tell which security posture the server is enforcing.
 * <p>
 * {@code version} is the release version of the running backend (the Maven project version baked in at build
 * time), so a caller can tell which build answered without shelling into the container.
 */
public record HeartbeatResponseDto(String message, boolean secureMode, String version) {
}
