package dev.visoftware.artiferrium.constants;

public final class ApiConstants {
    public static final String BASE_URL = "https://api.visoftware.dev";

    public static final String SERVER_RUNTIME_BASE = BASE_URL + "/services/runtime/server";
    public static final String SERVER_AUTH_ENDPOINT = SERVER_RUNTIME_BASE + "/authenticate";
    public static final String SERVER_HEARTBEAT_ENDPOINT = SERVER_RUNTIME_BASE + "/heartbreath";
    public static final String SERVER_ALLOWLIST_ENDPOINT = SERVER_RUNTIME_BASE + "/fetchallowlist";

    private ApiConstants() {
        throw new UnsupportedOperationException("Utility class");
    }
}
