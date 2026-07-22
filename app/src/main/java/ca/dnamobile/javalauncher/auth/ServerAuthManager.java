/*
 * Copyright (c) 2026 DNA Mobile Applications.
 * DroidBridge / FlintLauncher fork addition.
 *
 * Logs in against any authlib-injector-compatible Yggdrasil server
 * (Ely.by, LittleSkin, self-hosted authlib-injector deployments, etc).
 * Standard legacy Yggdrasil /authserver/authenticate endpoint.
 */

package ca.dnamobile.javalauncher.auth;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ca.dnamobile.javalauncher.data.AccountStore;

public final class ServerAuthManager {

    public interface Listener {
        void onSuccess(@NonNull AccountStore.Account account);

        void onError(@NonNull String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private ServerAuthManager() {
    }

    /**
     * @param serverUrl the authlib-injector API root, e.g. "https://authserver.ely.by/"
     *                   or "https://littleskin.cn/api/yggdrasil/". The
     *                   /authserver/authenticate path is appended automatically
     *                   if not already present.
     */
    public static void authenticate(
            @NonNull AccountStore accountStore,
            @Nullable String existingAccountId,
            @NonNull String serverUrl,
            @NonNull String username,
            @NonNull String password,
            @NonNull Listener listener
    ) {
        EXECUTOR.submit(() -> {
            try {
                String normalizedServerUrl = normalizeServerRoot(serverUrl);
                String authenticateUrl = joinUrl(normalizedServerUrl, "authserver/authenticate");

                JSONObject requestBody = new JSONObject()
                        .put("agent", new JSONObject().put("name", "Minecraft").put("version", 1))
                        .put("username", username)
                        .put("password", password)
                        .put("requestUser", false);

                JSONObject response = postJson(authenticateUrl, requestBody);

                String accessToken = response.getString("accessToken");
                JSONObject selectedProfile = response.optJSONObject("selectedProfile");
                if (selectedProfile == null) {
                    JSONArray profiles = response.optJSONArray("availableProfiles");
                    if (profiles != null && profiles.length() > 0) {
                        selectedProfile = profiles.getJSONObject(0);
                    }
                }
                if (selectedProfile == null) {
                    notifyError(listener, "This account has no Minecraft profile on that server.");
                    return;
                }

                String uuid = selectedProfile.getString("id");
                String name = selectedProfile.getString("name");

                AccountStore.Account account = accountStore.saveOrUpdateServerAuthAccount(
                        existingAccountId, normalizedServerUrl, uuid, name, accessToken
                );

                MAIN_HANDLER.post(() -> listener.onSuccess(account));
            } catch (AuthErrorException e) {
                notifyError(listener, e.getMessage());
            } catch (Throwable t) {
                String message = t.getMessage();
                notifyError(listener, "Sign-in failed" + (message != null ? ": " + message : "."));
            }
        });
    }

    @NonNull
    private static String normalizeServerRoot(@NonNull String input) {
        String trimmed = input.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        if (!trimmed.endsWith("/")) trimmed = trimmed + "/";
        return trimmed;
    }

    @NonNull
    private static String joinUrl(@NonNull String root, @NonNull String path) {
        return root.endsWith("/") ? root + path : root + "/" + path;
    }

    private static final class AuthErrorException extends Exception {
        AuthErrorException(String message) {
            super(message);
        }
    }

    @NonNull
    private static JSONObject postJson(@NonNull String url, @NonNull JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String responseBody = stream != null ? readFully(stream) : "";

        if (status < 200 || status >= 300) {
            throw new AuthErrorException(extractErrorMessage(responseBody, status));
        }
        if (responseBody.trim().isEmpty()) return new JSONObject();
        return new JSONObject(responseBody);
    }

    @NonNull
    private static String extractErrorMessage(@NonNull String body, int status) {
        try {
            JSONObject json = new JSONObject(body);
            String message = json.optString("errorMessage", null);
            if (message != null && !message.trim().isEmpty()) return message;
        } catch (Throwable ignored) {
        }
        return "Sign-in failed (HTTP " + status + "). Check the server URL and credentials.";
    }

    @NonNull
    private static String readFully(@NonNull InputStream stream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString("UTF-8");
    }

    private static void notifyError(@NonNull Listener listener, @NonNull String message) {
        MAIN_HANDLER.post(() -> listener.onError(message));
    }
}
