/*
 * Copyright (c) 2026 DNA Mobile Applications.
 * DroidBridge / FlintLauncher fork addition.
 *
 * Local, on-device account store for Microsoft, offline, and server-auth
 * (authlib-injector, e.g. Ely.by) accounts. Backed by a single JSON file
 * in app-private storage. No cloud dependency, no external libraries.
 */

package ca.dnamobile.javalauncher.data;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;

import ca.dnamobile.javalauncher.skin.CustomSkinStore;
import ca.dnamobile.javalauncher.skin.SkinModelType;

public final class AccountStore {

    private static final String STORE_FILE_NAME = "account_store.json";

    private static final String KEY_HAS_COMPLETED_MS_LOGIN_ONCE = "hasCompletedMicrosoftLoginOnce";
    private static final String KEY_ACTIVE_ACCOUNT_ID = "activeAccountId";
    private static final String KEY_LAST_MICROSOFT_ACCOUNT_ID = "lastMicrosoftAccountId";
    private static final String KEY_MICROSOFT_REFRESH_TOKEN = "microsoftRefreshToken";
    private static final String KEY_ACCOUNTS = "accounts";

    private final Context appContext;
    private final File storeFile;
    private final File offlineSkinsDir;

    public AccountStore(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.storeFile = new File(appContext.getFilesDir(), STORE_FILE_NAME);
        this.offlineSkinsDir = new File(appContext.getFilesDir(), "offline_account_skins");
        if (!offlineSkinsDir.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            offlineSkinsDir.mkdirs();
        }
    }

    // ─── Account model ─────────────────────────────────────────────────────

    public enum AccountType {
        MICROSOFT,
        OFFLINE,
        SERVER_AUTH
    }

    public static final class Account {
        public String accountId;
        public AccountType type = AccountType.OFFLINE;
        public String displayName;
        public String minecraftUuid;
        public String minecraftAccessToken;
        public long minecraftAccessTokenExpiresAtMillis;
        /** Remote skin URL, used for Microsoft accounts. */
        public String skinUrl;
        /** Local file path to a custom skin PNG, used for offline accounts. */
        public String offlineSkinPath;
        /** Human readable model name, e.g. "Classic" or "Slim". */
        public String offlineSkinModel;
        /** authlib-injector API root URL (e.g. https://authserver.ely.by/), for SERVER_AUTH accounts. */
        public String serverAuthUrl;

        @NonNull
        public String getBestDisplayName() {
            if (displayName == null || displayName.trim().isEmpty()) return "Player";
            return displayName.trim();
        }

        public boolean isOfflineAccount() {
            return type == AccountType.OFFLINE;
        }

        public boolean isMicrosoftAccount() {
            return type == AccountType.MICROSOFT;
        }

        public boolean isServerAuthAccount() {
            return type == AccountType.SERVER_AUTH;
        }

        public boolean hasOfflineSkin() {
            if (!isOfflineAccount() || offlineSkinPath == null) return false;
            File file = new File(offlineSkinPath);
            return file.isFile() && file.length() > 0;
        }

        public boolean hasMinecraftSession() {
            return minecraftAccessToken != null && !minecraftAccessToken.trim().isEmpty();
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("id", accountId);
            json.put("type", type.name());
            json.put("displayName", displayName == null ? "" : displayName);
            json.put("minecraftUuid", minecraftUuid == null ? "" : minecraftUuid);
            json.put("minecraftAccessToken", minecraftAccessToken == null ? "" : minecraftAccessToken);
            json.put("minecraftAccessTokenExpiresAtMillis", minecraftAccessTokenExpiresAtMillis);
            json.put("skinUrl", skinUrl == null ? "" : skinUrl);
            json.put("offlineSkinPath", offlineSkinPath == null ? "" : offlineSkinPath);
            json.put("offlineSkinModel", offlineSkinModel == null ? "" : offlineSkinModel);
            json.put("serverAuthUrl", serverAuthUrl == null ? "" : serverAuthUrl);
            return json;
        }

        @NonNull
        static Account fromJson(@NonNull JSONObject json) {
            Account account = new Account();
            account.accountId = json.optString("id", UUID.randomUUID().toString());
            try {
                account.type = AccountType.valueOf(json.optString("type", "OFFLINE"));
            } catch (IllegalArgumentException e) {
                account.type = AccountType.OFFLINE;
            }
            account.displayName = emptyToNull(json.optString("displayName", ""));
            account.minecraftUuid = emptyToNull(json.optString("minecraftUuid", ""));
            account.minecraftAccessToken = emptyToNull(json.optString("minecraftAccessToken", ""));
            account.minecraftAccessTokenExpiresAtMillis = json.optLong("minecraftAccessTokenExpiresAtMillis", 0L);
            account.skinUrl = emptyToNull(json.optString("skinUrl", ""));
            account.offlineSkinPath = emptyToNull(json.optString("offlineSkinPath", ""));
            account.offlineSkinModel = emptyToNull(json.optString("offlineSkinModel", ""));
            account.serverAuthUrl = emptyToNull(json.optString("serverAuthUrl", ""));
            return account;
        }

        @Nullable
        private static String emptyToNull(@Nullable String value) {
            return (value == null || value.trim().isEmpty()) ? null : value;
        }
    }

    // ─── Active account ─────────────────────────────────────────────────────

    @Nullable
    public synchronized Account load() {
        JSONObject root = readRoot();
        String activeId = root.optString(KEY_ACTIVE_ACCOUNT_ID, "");
        if (activeId.isEmpty()) return null;
        return findAccountById(root, activeId);
    }

    private synchronized void setActiveAccountId(@Nullable String accountId) {
        JSONObject root = readRoot();
        try {
            root.put(KEY_ACTIVE_ACCOUNT_ID, accountId == null ? "" : accountId);
        } catch (JSONException ignored) {
        }
        writeRoot(root);
    }

    // ─── Microsoft account flags ────────────────────────────────────────────

    public synchronized boolean hasMicrosoftLoginCompletedOnce() {
        return readRoot().optBoolean(KEY_HAS_COMPLETED_MS_LOGIN_ONCE, false);
    }

    public synchronized boolean hasStoredMicrosoftAccount() {
        JSONObject root = readRoot();
        String lastId = root.optString(KEY_LAST_MICROSOFT_ACCOUNT_ID, "");
        return !lastId.isEmpty() && findAccountById(root, lastId) != null;
    }

    @Nullable
    public synchronized Account loadLastMicrosoftAccount() {
        JSONObject root = readRoot();
        String lastId = root.optString(KEY_LAST_MICROSOFT_ACCOUNT_ID, "");
        if (lastId.isEmpty()) return null;
        return findAccountById(root, lastId);
    }

    public synchronized void useLastMicrosoftAccount() {
        JSONObject root = readRoot();
        String lastId = root.optString(KEY_LAST_MICROSOFT_ACCOUNT_ID, "");
        if (lastId.isEmpty() || findAccountById(root, lastId) == null) {
            throw new IllegalStateException("No remembered Microsoft account to switch to.");
        }
        try {
            root.put(KEY_ACTIVE_ACCOUNT_ID, lastId);
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to switch account.", e);
        }
        writeRoot(root);
    }

    @Nullable
    public synchronized String getMicrosoftRefreshToken() {
        String token = readRoot().optString(KEY_MICROSOFT_REFRESH_TOKEN, "");
        return token.isEmpty() ? null : token;
    }

    /**
     * Called by MicrosoftAuthManagerPersonal after a successful sign-in or refresh.
     * Saves/updates the Microsoft account, remembers it, marks the one-time unlock
     * flag, activates it, and stores the OAuth refresh token for silent re-auth.
     */
    @NonNull
    public synchronized Account saveMicrosoftAccount(
            @NonNull String minecraftUuid,
            @NonNull String minecraftUsername,
            @NonNull String minecraftAccessToken,
            long accessTokenExpiresAtMillis,
            @Nullable String skinUrl,
            @Nullable String refreshToken
    ) {
        JSONObject root = readRoot();
        JSONArray accounts = getAccountsArray(root);

        Account account = null;
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject candidate = accounts.optJSONObject(i);
            if (candidate == null) continue;
            if ("MICROSOFT".equals(candidate.optString("type"))
                    && minecraftUuid.equals(candidate.optString("minecraftUuid"))) {
                account = Account.fromJson(candidate);
                accounts.remove(i);
                break;
            }
        }
        if (account == null) {
            account = new Account();
            account.accountId = UUID.randomUUID().toString();
        }
        account.type = AccountType.MICROSOFT;
        account.displayName = minecraftUsername;
        account.minecraftUuid = minecraftUuid;
        account.minecraftAccessToken = minecraftAccessToken;
        account.minecraftAccessTokenExpiresAtMillis = accessTokenExpiresAtMillis;
        account.skinUrl = skinUrl;

        appendAccount(accounts, account);

        try {
            root.put(KEY_ACCOUNTS, accounts);
            root.put(KEY_ACTIVE_ACCOUNT_ID, account.accountId);
            root.put(KEY_LAST_MICROSOFT_ACCOUNT_ID, account.accountId);
            root.put(KEY_HAS_COMPLETED_MS_LOGIN_ONCE, true);
            if (refreshToken != null && !refreshToken.trim().isEmpty()) {
                root.put(KEY_MICROSOFT_REFRESH_TOKEN, refreshToken);
            }
        } catch (JSONException ignored) {
        }
        writeRoot(root);
        return account;
    }

    /**
     * Signs out of Microsoft: clears the refresh token and, if a Microsoft
     * account is currently active, clears the active account. Does NOT clear
     * hasMicrosoftLoginCompletedOnce — that unlock is permanent by design.
     */
    public synchronized void clearMicrosoftAccount() {
        JSONObject root = readRoot();
        try {
            root.put(KEY_MICROSOFT_REFRESH_TOKEN, "");
        } catch (JSONException ignored) {
        }
        String activeId = root.optString(KEY_ACTIVE_ACCOUNT_ID, "");
        Account active = activeId.isEmpty() ? null : findAccountById(root, activeId);
        if (active != null && active.isMicrosoftAccount()) {
            try {
                root.put(KEY_ACTIVE_ACCOUNT_ID, "");
            } catch (JSONException ignored) {
            }
        }
        writeRoot(root);
    }

    // ─── Offline accounts ───────────────────────────────────────────────────

    @NonNull
    public synchronized ArrayList<Account> listOfflineAccounts() {
        return listAccountsOfType(AccountType.OFFLINE);
    }

    public synchronized void activateOfflineAccount(@NonNull String accountId) {
        activateAccountOfType(accountId, AccountType.OFFLINE);
    }

    /**
     * Creates a new offline account, or updates an existing one if existingId is non-null.
     * Copies the picked skin PNG into app-private storage. Activates the account.
     */
    @NonNull
    public synchronized Account saveOrUpdateOfflineAccount(
            @Nullable String existingId,
            @NonNull String name,
            @Nullable Uri skinUri,
            boolean clearSkin
    ) throws Exception {
        JSONObject root = readRoot();
        JSONArray accounts = getAccountsArray(root);

        Account account = null;
        if (existingId != null) {
            for (int i = 0; i < accounts.length(); i++) {
                JSONObject candidate = accounts.optJSONObject(i);
                if (candidate != null && existingId.equals(candidate.optString("id"))) {
                    account = Account.fromJson(candidate);
                    accounts.remove(i);
                    break;
                }
            }
        }
        if (account == null) {
            account = new Account();
            account.accountId = UUID.randomUUID().toString();
        }
        account.type = AccountType.OFFLINE;
        account.displayName = name;

        if (clearSkin && account.offlineSkinPath != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(account.offlineSkinPath).delete();
            account.offlineSkinPath = null;
            account.offlineSkinModel = null;
        } else if (skinUri != null) {
            File skinFile = new File(offlineSkinsDir, account.accountId + ".png");
            copyUriToFile(skinUri, skinFile);
            if (!CustomSkinStore.isSkinValid(skinFile)) {
                //noinspection ResultOfMethodCallIgnored
                skinFile.delete();
                throw new IllegalArgumentException("That image isn't a valid Minecraft skin (must be a 64x64 or 64x32 PNG).");
            }
            SkinModelType model = CustomSkinStore.getSkinModel(skinFile);
            account.offlineSkinPath = skinFile.getAbsolutePath();
            account.offlineSkinModel = model == SkinModelType.SLIM ? "Slim" : "Classic";
        }

        appendAccount(accounts, account);

        try {
            root.put(KEY_ACCOUNTS, accounts);
            root.put(KEY_ACTIVE_ACCOUNT_ID, account.accountId);
        } catch (JSONException e) {
            throw new Exception("Unable to save offline account.", e);
        }
        writeRoot(root);
        return account;
    }

    public synchronized void deleteOfflineAccount(@NonNull String accountId) {
        deleteAccountOfType(accountId, AccountType.OFFLINE);
    }

    // ─── Server-auth accounts (authlib-injector, e.g. Ely.by) ──────────────

    @NonNull
    public synchronized ArrayList<Account> listServerAuthAccounts() {
        return listAccountsOfType(AccountType.SERVER_AUTH);
    }

    public synchronized void activateServerAuthAccount(@NonNull String accountId) {
        activateAccountOfType(accountId, AccountType.SERVER_AUTH);
    }

    /**
     * Called by ServerAuthManager after a successful authlib-injector login.
     */
    @NonNull
    public synchronized Account saveOrUpdateServerAuthAccount(
            @Nullable String existingId,
            @NonNull String serverUrl,
            @NonNull String minecraftUuid,
            @NonNull String minecraftUsername,
            @NonNull String minecraftAccessToken
    ) {
        JSONObject root = readRoot();
        JSONArray accounts = getAccountsArray(root);

        Account account = null;
        String targetId = existingId != null ? existingId : null;
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject candidate = accounts.optJSONObject(i);
            if (candidate == null) continue;
            boolean idMatch = targetId != null && targetId.equals(candidate.optString("id"));
            boolean serverAndUuidMatch = "SERVER_AUTH".equals(candidate.optString("type"))
                    && serverUrl.equals(candidate.optString("serverAuthUrl"))
                    && minecraftUuid.equals(candidate.optString("minecraftUuid"));
            if (idMatch || serverAndUuidMatch) {
                account = Account.fromJson(candidate);
                accounts.remove(i);
                break;
            }
        }
        if (account == null) {
            account = new Account();
            account.accountId = UUID.randomUUID().toString();
        }
        account.type = AccountType.SERVER_AUTH;
        account.displayName = minecraftUsername;
        account.minecraftUuid = minecraftUuid;
        account.minecraftAccessToken = minecraftAccessToken;
        account.serverAuthUrl = serverUrl;

        appendAccount(accounts, account);

        try {
            root.put(KEY_ACCOUNTS, accounts);
            root.put(KEY_ACTIVE_ACCOUNT_ID, account.accountId);
        } catch (JSONException ignored) {
        }
        writeRoot(root);
        return account;
    }

    public synchronized void deleteServerAuthAccount(@NonNull String accountId) {
        deleteAccountOfType(accountId, AccountType.SERVER_AUTH);
    }

    // ─── Shared helpers ──────────────────────────────────────────────────────

    @NonNull
    private ArrayList<Account> listAccountsOfType(@NonNull AccountType type) {
        JSONObject root = readRoot();
        JSONArray accounts = getAccountsArray(root);
        ArrayList<Account> result = new ArrayList<>();
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject candidate = accounts.optJSONObject(i);
            if (candidate == null) continue;
            Account account = Account.fromJson(candidate);
            if (account.type == type) result.add(account);
        }
        return result;
    }

    private void activateAccountOfType(@NonNull String accountId, @NonNull AccountType expectedType) {
        JSONObject root = readRoot();
        Account account = findAccountById(root, accountId);
        if (account == null || account.type != expectedType) return;
        try {
            root.put(KEY_ACTIVE_ACCOUNT_ID, accountId);
        } catch (JSONException ignored) {
        }
        writeRoot(root);
    }

    private void deleteAccountOfType(@NonNull String accountId, @NonNull AccountType expectedType) {
        JSONObject root = readRoot();
        JSONArray accounts = getAccountsArray(root);
        JSONArray kept = new JSONArray();
        Account removed = null;

        for (int i = 0; i < accounts.length(); i++) {
            JSONObject candidate = accounts.optJSONObject(i);
            if (candidate == null) continue;
            Account account = Account.fromJson(candidate);
            if (account.accountId.equals(accountId) && account.type == expectedType) {
                removed = account;
                continue;
            }
            kept.put(candidate);
        }

        if (removed != null && removed.offlineSkinPath != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(removed.offlineSkinPath).delete();
        }

        try {
            root.put(KEY_ACCOUNTS, kept);
            String activeId = root.optString(KEY_ACTIVE_ACCOUNT_ID, "");
            if (activeId.equals(accountId)) {
                root.put(KEY_ACTIVE_ACCOUNT_ID, "");
            }
        } catch (JSONException ignored) {
        }
        writeRoot(root);
    }

    @Nullable
    private Account findAccountById(@NonNull JSONObject root, @NonNull String accountId) {
        JSONArray accounts = getAccountsArray(root);
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject candidate = accounts.optJSONObject(i);
            if (candidate != null && accountId.equals(candidate.optString("id"))) {
                return Account.fromJson(candidate);
            }
        }
        return null;
    }

    private void appendAccount(@NonNull JSONArray accounts, @NonNull Account account) {
        try {
            accounts.put(account.toJson());
        } catch (JSONException ignored) {
        }
    }

    @NonNull
    private JSONArray getAccountsArray(@NonNull JSONObject root) {
        JSONArray accounts = root.optJSONArray(KEY_ACCOUNTS);
        return accounts != null ? accounts : new JSONArray();
    }

    @NonNull
    private synchronized JSONObject readRoot() {
        if (!storeFile.isFile()) return new JSONObject();
        StringBuilder builder = new StringBuilder();
        try (InputStream input = new java.io.FileInputStream(storeFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                builder.append(new String(buffer, 0, read, "UTF-8"));
            }
            return new JSONObject(builder.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private synchronized void writeRoot(@NonNull JSONObject root) {
        try (FileWriter writer = new FileWriter(storeFile, false)) {
            writer.write(root.toString());
        } catch (Exception ignored) {
        }
    }

    private void copyUriToFile(@NonNull Uri sourceUri, @NonNull File destination) throws Exception {
        try (InputStream input = appContext.getContentResolver().openInputStream(sourceUri);
             FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) throw new IllegalStateException("Could not open selected skin.");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }
}
