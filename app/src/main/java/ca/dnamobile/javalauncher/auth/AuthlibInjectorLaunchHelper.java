/*
 * Copyright (c) 2026 DNA Mobile Applications.
 * DroidBridge / FlintLauncher fork addition.
 *
 * Mirrors the existing MethodInjectorAgentInstaller pattern in
 * ca.dnamobile.javalauncher.modcompat, but for authlib-injector.jar,
 * which the user supplies manually at LibPath.AUTHLIB_INJECTOR
 * (files/other_login/authlib-injector.jar) rather than unpacking it
 * from APK assets.
 */

package ca.dnamobile.javalauncher.auth;

import androidx.annotation.NonNull;

import java.util.List;

import ca.dnamobile.javalauncher.utils.path.LibPath;

public final class AuthlibInjectorLaunchHelper {

    private AuthlibInjectorLaunchHelper() {
    }

    /**
     * If authlib-injector.jar is present and apiRootUrl is non-empty, appends
     * -javaagent:<jar>=<apiRootUrl> to jvmArgs. Call this once per launch,
     * only when the active account is a SERVER_AUTH account.
     *
     * @return true if the arg was added, false if the jar is missing (in
     *         which case the caller should surface a "place authlib-injector.jar
     *         in other_login/" message rather than silently launching offline).
     */
    public static boolean addAuthlibInjectorArg(@NonNull List<String> jvmArgs, @NonNull String apiRootUrl) {
        if (LibPath.AUTHLIB_INJECTOR == null || !LibPath.AUTHLIB_INJECTOR.isFile()) {
            return false;
        }
        String root = apiRootUrl.trim();
        if (root.isEmpty()) return false;

        jvmArgs.add("-javaagent:" + LibPath.AUTHLIB_INJECTOR.getAbsolutePath() + "=" + root);
        return true;
    }
}
