/*
 * Copyright (c) 2026 DNA Mobile Applications.
 * DroidBridge / FlintLauncher fork addition.
 *
 * Bundles authlib-injector.jar and nide8auth.jar as APK assets
 * (assets/components/other-login/) instead of requiring a manual adb push,
 * mirroring ZalithLauncher's ZalithLauncher/src/main/assets/components/other-login/
 * convention. A "version" text file in that same asset folder controls
 * re-installation: bump it whenever you replace either jar, and this will
 * re-copy on next launch. Leave either jar out of assets entirely and it's
 * just skipped — no crash, features that need it simply report "missing".
 */

package ca.dnamobile.javalauncher.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import ca.dnamobile.javalauncher.utils.path.LibPath;

public final class OtherLoginAssetInstaller {

    private static final String ASSET_DIR = "components/other-login";
    private static final String VERSION_ASSET_PATH = ASSET_DIR + "/version";
    private static final String AUTHLIB_INJECTOR_ASSET_PATH = ASSET_DIR + "/authlib-injector.jar";
    private static final String NIDE8AUTH_ASSET_PATH = ASSET_DIR + "/nide8auth.jar";

    private static final String PREFS_NAME = "other_login_component";
    private static final String KEY_INSTALLED_VERSION = "installed_version";

    private OtherLoginAssetInstaller() {
    }

    /**
     * Call once early in the app's lifecycle (SplashActivity.onCreate or your
     * Application subclass's onCreate is ideal). Cheap no-op once installed
     * and up to date, so it's safe to also call again in LauncherSettingsActivity
     * as a safety net before "Add Server Auth" is used.
     */
    public static void installIfNeeded(@NonNull Context context) {
        Context appContext = context.getApplicationContext();

        String bundledVersion;
        try {
            bundledVersion = readAssetText(appContext, VERSION_ASSET_PATH);
        } catch (IOException e) {
            // No version file bundled yet — nothing to install, features fall
            // back to reporting the jar as missing until you add the assets.
            return;
        }

        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String installedVersion = prefs.getString(KEY_INSTALLED_VERSION, null);

        boolean authlibMissing = LibPath.AUTHLIB_INJECTOR == null || !LibPath.AUTHLIB_INJECTOR.isFile();
        boolean nide8Missing = LibPath.NIDE_8_AUTH == null || !LibPath.NIDE_8_AUTH.isFile();

        if (bundledVersion.equals(installedVersion) && !authlibMissing && !nide8Missing) {
            return;
        }

        File targetDir = LibPath.AUTHLIB_INJECTOR != null ? LibPath.AUTHLIB_INJECTOR.getParentFile() : null;
        if (targetDir != null && !targetDir.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            targetDir.mkdirs();
        }

        copyAssetIfPresent(appContext, AUTHLIB_INJECTOR_ASSET_PATH, LibPath.AUTHLIB_INJECTOR);
        copyAssetIfPresent(appContext, NIDE8AUTH_ASSET_PATH, LibPath.NIDE_8_AUTH);

        prefs.edit().putString(KEY_INSTALLED_VERSION, bundledVersion).apply();
    }

    private static void copyAssetIfPresent(@NonNull Context context, @NonNull String assetPath, File destination) {
        if (destination == null) return;
        try (InputStream input = context.getAssets().open(assetPath);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (IOException ignored) {
            // This particular jar wasn't bundled — fine, it's optional.
        }
    }

    @NonNull
    private static String readAssetText(@NonNull Context context, @NonNull String assetPath) throws IOException {
        try (InputStream input = context.getAssets().open(assetPath)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString("UTF-8").trim();
        }
    }
}
