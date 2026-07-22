/*
 * Copyright (c) 2026 DNA Mobile Applications.
 * DroidBridge / FlintLauncher fork addition.
 */

package ca.dnamobile.javalauncher.auth;

public final class MicrosoftAuthConfigPersonal {
    private MicrosoftAuthConfigPersonal() {
    }

    /**
     * Register a free app at https://portal.azure.com -> App registrations -> New registration.
     * Platform type: "Mobile and desktop applications" (public client, no secret needed).
     * Add these API permissions under "APIs my organization uses" is NOT needed —
     * XboxLive.signin is requested directly at sign-in time via SCOPE below.
     * Paste the "Application (client) ID" from that registration here.
     */
    public static final String CLIENT_ID = "YOUR_AZURE_APP_CLIENT_ID_HERE";

    public static final String SCOPE = "XboxLive.signin offline_access";

    // "consumers" tenant, since Minecraft accounts are personal Microsoft accounts.
    public static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    public static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

    public static final String XBOX_USER_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    public static final String XBOX_XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    public static final String MINECRAFT_LOGIN_WITH_XBOX_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    public static final String MINECRAFT_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";
    public static final String MINECRAFT_ENTITLEMENTS_URL =
            "https://api.minecraftservices.com/entitlements/mcstore";

    public static boolean isConfigured() {
        return CLIENT_ID != null
                && !CLIENT_ID.trim().isEmpty()
                && !CLIENT_ID.contains("YOUR_AZURE_APP_CLIENT_ID_HERE");
    }
}
