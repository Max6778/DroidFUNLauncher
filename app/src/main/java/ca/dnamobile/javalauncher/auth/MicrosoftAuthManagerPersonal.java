/*
 * Copyright (c) 2026 DNA Mobile Applications.
 * DroidBridge / FlintLauncher fork addition.
 *
 * Implements the publicly documented Microsoft device-code sign-in flow,
 * followed by the standard Xbox Live -> XSTS -> Minecraft Services
 * authentication chain used by every third-party Minecraft launcher.
 * Reference: https://wiki.vg/Microsoft_Authentication_Scheme
 *
 * No external HTTP library required — uses HttpURLConnection + org.json,
 * both part of the Android SDK.
 */

package ca.dnamobile.javalauncher.auth;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import ca.dnamobile.javalauncher.data.AccountStore;

public final class MicrosoftAuthManagerPersonal {

    public interface Listener {
        void onSignedIn(@NonNull AccountStore.Account account);

        void onError(@NonNull String message);
    }

    private final Context context;
    private final AccountStore accountStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    @Nullable
    private Listener listener;
    @Nullable
    private AlertDialog deviceCodeDialog;
    private volatile boolean cancelPolling;

    public MicrosoftAuthManagerPersonal(@NonNull Context context, @NonNull AccountStore accountStore) {
        this.context = context;
        this.accountStore = accountStore;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void dispose() {
        disposed.set(true);
        cancelPolling = true;
        if (deviceCodeDialog != null && deviceCodeDialog.isShowing()) {
            deviceCodeDialog.dismiss();
        }
        executor.shutdownNow();
    }

    // ─── Sign in ─────────────────────────────────────────────────────────────

    public void signIn() {
        cancelPolling = false;
        executor.submit(() -> {
            try {
                JSONObject deviceCodeResponse = requestDeviceCode();
                String deviceCode = deviceCodeResponse.getString("device_code");
                String userCode = deviceCodeResponse.getString("user_code");
                String verificationUri = deviceCodeResponse.optString(
                        "verification_uri", "https://microsoft.com/link");
                int intervalSeconds = deviceCodeResponse.optInt("interval", 5);
                int expiresInSeconds = deviceCodeResponse.optInt("expires_in", 900);

                runOnUi(() -> showDeviceCodeDialog(userCode, verificationUri));

                long deadline = System.currentTimeMillis() + (expiresInSeconds * 1000L);
                JSONObject tokenResponse = null;

                while (System.currentTimeMillis() < deadline && !cancelPolling && !disposed.get()) {
                    Thread.sleep(Math.max(2, intervalSeconds) * 1000L);
                    if (cancelPolling || disposed.get()) break;

                    PollResult result = pollForToken(deviceCode);
                    if (result.success) {
                        tokenResponse = result.json;
                        break;
                    }
                    if (result.fatalError != null) {
                        dismissDeviceCodeDialog();
                        notifyError(result.fatalError);
                        return;
                    }
                    if (result.newInterval > 0) intervalSeconds = result.newInterval;
                    // "authorization_pending" / "slow_down" -> keep polling.
                }

                dismissDeviceCodeDialog();

                if (cancelPolling || disposed.get()) return;
                if (tokenResponse == null) {
                    notifyError("Sign-in timed out. Please try again.");
                    return;
                }

                completeSignInWithMicrosoftToken(
                        tokenResponse.getString("access_token"),
                        tokenResponse.optString("refresh_token", null)
                );
            } catch (Throwable t) {
                dismissDeviceCodeDialog();
                notifyError(describeError("Sign-in failed", t));
            }
        });
    }

    public void cancelSignIn() {
        cancelPolling = true;
        dismissDeviceCodeDialog();
    }

    // ─── Refresh (silent re-auth using stored refresh token) ────────────────

    public void refreshMicrosoftAccount() {
        executor.submit(() -> {
            try {
                String refreshToken = accountStore.getMicrosoftRefreshToken();
                if (refreshToken == null) {
                    notifyError("No remembered Microsoft account to refresh.");
                    return;
                }
                JSONObject tokenResponse = refreshMsToken(refreshToken);
                completeSignInWithMicrosoftToken(
                        tokenResponse.getString("access_token"),
                        tokenResponse.optString("refresh_token", refreshToken)
                );
            } catch (Throwable t) {
                notifyError(describeError("Couldn't refresh Microsoft account", t));
            }
        });
    }

    // ─── Sign out ────────────────────────────────────────────────────────────

    public void signOut() {
        accountStore.clearMicrosoftAccount();
    }

    // ─── The auth chain: MS token -> Xbox Live -> XSTS -> Minecraft ─────────

    private void completeSignInWithMicrosoftToken(@NonNull String msAccessToken, @Nullable String refreshToken)
            throws Exception {
        JSONObject xblResponse = postJson(MicrosoftAuthConfigPersonal.XBOX_USER_AUTH_URL, xboxLiveAuthBody(msAccessToken), null);
        String xblToken = xblResponse.getString("Token");
        String userHash = xblResponse.getJSONObject("DisplayClaims")
                .getJSONArray("xui").getJSONObject(0).getString("uhs");

        JSONObject xstsResponse;
        try {
            xstsResponse = postJson(MicrosoftAuthConfigPersonal.XBOX_XSTS_AUTH_URL, xstsAuthBody(xblToken), null);
        } catch (XstsErrorException e) {
            notifyError(e.getMessage());
            return;
        }
        String xstsToken = xstsResponse.getString("Token");

        String identityToken = "XBL3.0 x=" + userHash + ";" + xstsToken;
        JSONObject mcLoginResponse = postJson(
                MicrosoftAuthConfigPersonal.MINECRAFT_LOGIN_WITH_XBOX_URL,
                new JSONObject().put("identityToken", identityToken),
                null
        );
        String minecraftAccessToken = mcLoginResponse.getString("access_token");
        int expiresInSeconds = mcLoginResponse.optInt("expires_in", 86400);
        long expiresAtMillis = System.currentTimeMillis() + (expiresInSeconds * 1000L);

        JSONObject profile;
        try {
            profile = getJson(MicrosoftAuthConfigPersonal.MINECRAFT_PROFILE_URL, minecraftAccessToken);
        } catch (NotFoundException e) {
            notifyError("This Microsoft account doesn't own Minecraft: Java Edition.");
            return;
        }

        String uuid = profile.getString("id");
        String username = profile.getString("name");
        String skinUrl = extractSkinUrl(profile);

        AccountStore.Account account = accountStore.saveMicrosoftAccount(
                uuid, username, minecraftAccessToken, expiresAtMillis, skinUrl, refreshToken
        );

        notifySignedIn(account);
    }

    @Nullable
    private String extractSkinUrl(@NonNull JSONObject profile) {
        JSONArray skins = profile.optJSONArray("skins");
        if (skins == null) return null;
        for (int i = 0; i < skins.length(); i++) {
            JSONObject skin = skins.optJSONObject(i);
            if (skin == null) continue;
            if ("ACTIVE".equalsIgnoreCase(skin.optString("state", ""))) {
                return skin.optString("url", null);
            }
        }
        return null;
    }

    // ─── Device code dialog UI ──────────────────────────────────────────────

    private void showDeviceCodeDialog(@NonNull String userCode, @NonNull String verificationUri) {
        dismissDeviceCodeDialog();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(24);
        root.setPadding(padding, dp(18), padding, dp(4));

        TextView instructions = new TextView(context);
        instructions.setText("Open " + verificationUri + " on any device and enter this code:");
        root.addView(instructions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView codeView = new TextView(context);
        codeView.setText(userCode);
        codeView.setTextSize(28f);
        codeView.setGravity(Gravity.CENTER);
        codeView.setTypeface(codeView.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        codeParams.topMargin = dp(16);
        codeParams.bottomMargin = dp(16);
        root.addView(codeView, codeParams);

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(buttonRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        MaterialButton openBrowser = new MaterialButton(context);
        openBrowser.setText("Open browser");
        openBrowser.setAllCaps(false);
        openBrowser.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(verificationUri));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Throwable ignored) {
            }
        });
        buttonRow.addView(openBrowser, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton copyCode = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        copyCode.setText("Copy code");
        copyCode.setAllCaps(false);
        copyCode.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Microsoft sign-in code", userCode));
                Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(8);
        buttonRow.addView(copyCode, copyParams);

        deviceCodeDialog = new AlertDialog.Builder(context)
                .setTitle("Sign in with Microsoft")
                .setView(root)
                .setNegativeButton("Cancel", (dialog, which) -> cancelSignIn())
                .setCancelable(false)
                .show();
    }

    private void dismissDeviceCodeDialog() {
        runOnUi(() -> {
            if (deviceCodeDialog != null && deviceCodeDialog.isShowing()) {
                deviceCodeDialog.dismiss();
            }
            deviceCodeDialog = null;
        });
    }

    // ─── HTTP: device code + token polling ──────────────────────────────────

    @NonNull
    private JSONObject requestDeviceCode() throws Exception {
        String form = "client_id=" + urlEncode(MicrosoftAuthConfigPersonal.CLIENT_ID)
                + "&scope=" + urlEncode(MicrosoftAuthConfigPersonal.SCOPE);
        return postForm(MicrosoftAuthConfigPersonal.DEVICE_CODE_URL, form);
    }

    private static final class PollResult {
        boolean success;
        JSONObject json;
        String fatalError;
        int newInterval;
    }

    @NonNull
    private PollResult pollForToken(@NonNull String deviceCode) {
        PollResult result = new PollResult();
        try {
            String form = "grant_type=urn:ietf:params:oauth:grant-type:device_code"
                    + "&client_id=" + urlEncode(MicrosoftAuthConfigPersonal.CLIENT_ID)
                    + "&device_code=" + urlEncode(deviceCode);
            JSONObject response = postForm(MicrosoftAuthConfigPersonal.TOKEN_URL, form);
            result.success = true;
            result.json = response;
        } catch (HttpErrorException e) {
            String error = e.errorBody != null ? optErrorCode(e.errorBody) : null;
            if ("authorization_pending".equals(error)) {
                // keep polling
            } else if ("slow_down".equals(error)) {
                result.newInterval = 10;
            } else if ("authorization_declined".equals(error)) {
                result.fatalError = "Sign-in was declined.";
            } else if ("expired_token".equals(error)) {
                result.fatalError = "Sign-in code expired. Please try again.";
            } else if ("bad_verification_code".equals(error)) {
                result.fatalError = "Sign-in failed, please try again.";
            } else {
                result.fatalError = describeError("Sign-in failed", e);
            }
        } catch (Throwable t) {
            result.fatalError = describeError("Sign-in failed", t);
        }
        return result;
    }

    @Nullable
    private String optErrorCode(@NonNull String body) {
        try {
            return new JSONObject(body).optString("error", null);
        } catch (Throwable t) {
            return null;
        }
    }

    @NonNull
    private JSONObject refreshMsToken(@NonNull String refreshToken) throws Exception {
        String form = "client_id=" + urlEncode(MicrosoftAuthConfigPersonal.CLIENT_ID)
                + "&grant_type=refresh_token"
                + "&refresh_token=" + urlEncode(refreshToken)
                + "&scope=" + urlEncode(MicrosoftAuthConfigPersonal.SCOPE);
        return postForm(MicrosoftAuthConfigPersonal.TOKEN_URL, form);
    }

    // ─── Xbox / XSTS request bodies ──────────────────────────────────────────

    @NonNull
    private JSONObject xboxLiveAuthBody(@NonNull String msAccessToken) throws Exception {
        JSONObject properties = new JSONObject()
                .put("AuthMethod", "RPS")
                .put("SiteName", "user.auth.xboxlive.com")
                .put("RpsTicket", "d=" + msAccessToken);
        return new JSONObject()
                .put("Properties", properties)
                .put("RelyingParty", "http://auth.xboxlive.com")
                .put("TokenType", "JWT");
    }

    @NonNull
    private JSONObject xstsAuthBody(@NonNull String xblToken) throws Exception {
        JSONObject properties = new JSONObject()
                .put("SandboxId", "RETAIL")
                .put("UserTokens", new JSONArray().put(xblToken));
        return new JSONObject()
                .put("Properties", properties)
                .put("RelyingParty", "rp://api.minecraftservices.com/")
                .put("TokenType", "JWT");
    }

    // ─── Low level HTTP helpers ──────────────────────────────────────────────

    private static final class HttpErrorException extends IOException {
        final int statusCode;
        @Nullable
        final String errorBody;

        HttpErrorException(int statusCode, @Nullable String errorBody) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
            this.errorBody = errorBody;
        }
    }

    private static final class NotFoundException extends IOException {
    }

    private static final class XstsErrorException extends IOException {
        XstsErrorException(String message) {
            super(message);
        }
    }

    @NonNull
    private JSONObject postForm(@NonNull String url, @NonNull String form) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(form.getBytes(StandardCharsets.UTF_8));
        }
        return readJsonResponse(connection);
    }

    @NonNull
    private JSONObject postJson(@NonNull String url, @NonNull JSONObject body, @Nullable String bearerToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        if (bearerToken != null) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        try {
            return readJsonResponse(connection);
        } catch (HttpErrorException e) {
            if (url.equals(MicrosoftAuthConfigPersonal.XBOX_XSTS_AUTH_URL) && e.errorBody != null) {
                throw new XstsErrorException(describeXstsError(e.errorBody));
            }
            throw e;
        }
    }

    @NonNull
    private JSONObject getJson(@NonNull String url, @NonNull String bearerToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        try {
            return readJsonResponse(connection);
        } catch (HttpErrorException e) {
            if (e.statusCode == 404) throw new NotFoundException();
            throw e;
        }
    }

    @NonNull
    private JSONObject readJsonResponse(@NonNull HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = stream != null ? readFully(stream) : "";
        if (status < 200 || status >= 300) {
            throw new HttpErrorException(status, body);
        }
        if (body.trim().isEmpty()) return new JSONObject();
        return new JSONObject(body);
    }

    @NonNull
    private String readFully(@NonNull InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString("UTF-8");
    }

    @NonNull
    private String urlEncode(@NonNull String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    @NonNull
    private String describeXstsError(@NonNull String errorBody) {
        try {
            long errorCode = new JSONObject(errorBody).optLong("XErr", -1);
            if (errorCode == 2148916233L) {
                return "This Microsoft account has no Xbox profile. Create one at xbox.com, then try again.";
            }
            if (errorCode == 2148916238L) {
                return "This account belongs to a child under 18 and needs a family group with adult consent added at account.microsoft.com.";
            }
        } catch (Throwable ignored) {
        }
        return "Xbox Live sign-in failed. Please try again.";
    }

    @NonNull
    private String describeError(@NonNull String prefix, @NonNull Throwable t) {
        String message = t.getMessage();
        return message != null && !message.trim().isEmpty() ? prefix + ": " + message : prefix + ".";
    }

    // ─── Dispatch helpers ────────────────────────────────────────────────────

    private void notifySignedIn(@NonNull AccountStore.Account account) {
        runOnUi(() -> {
            if (listener != null) listener.onSignedIn(account);
        });
    }

    private void notifyError(@NonNull String message) {
        runOnUi(() -> {
            if (listener != null) listener.onError(message);
        });
    }

    private void runOnUi(@NonNull Runnable runnable) {
        if (disposed.get()) return;
        mainHandler.post(runnable);
    }

    private int dp(float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
