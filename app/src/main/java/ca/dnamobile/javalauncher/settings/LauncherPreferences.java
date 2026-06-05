/*
 * Copyright (c) 2026 DNA Mobile Applications.
 * All rights reserved.
 *
 * This file is DroidBridge project code.
 * It is not part of Minecraft and does not grant rights to Minecraft,
 * Mojang, Microsoft, PojavLauncher, Zalith Launcher, or any third-party project.
 *
 * Files written entirely by DNA Mobile Applications are proprietary unless
 * a file header or separate license notice states otherwise.
 */

package ca.dnamobile.javalauncher.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

public final class LauncherPreferences {
    private static final String PREFS_NAME = "launcher_preferences";
    private static final String RESOLUTION_PREFS_NAME = "launcher_resolution_preferences";
    private static final String KEY_USE_NATIVE_SURFACE_VIEW = "use_native_surface_view";
    private static final String KEY_SHOW_SHARED_INSTALLS = "show_shared_installs";
    private static final String KEY_SELECTED_INSTANCE_FILTER = "selected_instance_filter";
    private static final String KEY_RECENT_INSTANCE_PREFIX = "recent_instance_last_played_";
    private static final String KEY_REMOVE_INHERITED_VANILLA_AFTER_LOADER_INSTALL = "remove_inherited_vanilla_after_loader_install";
    private static final String KEY_SELECTED_RENDERER_IDENTIFIER = "selected_renderer_identifier";
    private static final String KEY_SELECTED_VULKAN_DRIVER_NAME = "selected_vulkan_driver_name";
    private static final String KEY_USE_SYSTEM_VULKAN_DRIVER = "use_system_vulkan_driver";
    private static final String KEY_USE_OPENGL_FOR_MC_26_PLUS = "use_opengl_for_mc_26_plus";
    private static final String KEY_SHOW_GAME_LOG_OVERLAY = "show_game_log_overlay";
    private static final String KEY_SHOW_IN_GAME_SETTINGS_BUTTON = "show_in_game_settings_button";
    private static final String KEY_ENABLE_SDL_CONTROLLER_MOD_COMPAT = "enable_sdl_controller_mod_compat";
    private static final String KEY_SHOW_CONTROLLER_MOD_COMPAT_WARNINGS = "show_controller_mod_compat_warnings";
    private static final String KEY_FORCE_SDL_CONTROLLER_BRIDGE = "force_sdl_controller_bridge";
    private static final String KEY_ALLOCATED_MEMORY_MB = "allocated_memory_mb";
    private static final String KEY_GAME_RESOLUTION_SCALE_PERCENT = "game_resolution_scale_percent";
    private static final String KEY_FORCE_FULLSCREEN_MODE = "force_fullscreen_mode";
    private static final String KEY_AVOID_ROUNDED_DISPLAY_CORNERS = "avoid_rounded_display_corners";
    private static final String KEY_IGNORE_DISPLAY_CUTOUT = "ignore_display_cutout";

    public static final int MIN_GAME_RESOLUTION_SCALE_PERCENT = 25;
    public static final int MAX_GAME_RESOLUTION_SCALE_PERCENT = 200;
    public static final int DEFAULT_GAME_RESOLUTION_SCALE_PERCENT = 100;

    private static final String DEFAULT_RENDERER_IDENTIFIER = "e7b90ed6-e518-4d4e-93dc-5c7133cd5b31";
    private static final String DEFAULT_VULKAN_DRIVER_NAME = "Default Mesa driver";

    private LauncherPreferences() {
    }

    @SuppressWarnings("deprecation")
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS
        );
    }

    @SuppressWarnings("deprecation")
    private static SharedPreferences resolutionPrefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(
                RESOLUTION_PREFS_NAME,
                Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS
        );
    }

    private static void saveBoolean(@NonNull Context context, @NonNull String key, boolean enabled) {
        boolean saved = prefs(context).edit().putBoolean(key, enabled).commit();
        if (!saved) {
            prefs(context).edit().putBoolean(key, enabled).apply();
        }
    }

    /**
     * false = TextureView / SurfaceTexture path
     * true  = native Android SurfaceView / SurfaceHolder path
     *
     * Keep TextureView as the default because it is the current known-working path.
     */
    public static boolean isUseNativeSurfaceView(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_USE_NATIVE_SURFACE_VIEW, false);
    }

    public static void setUseNativeSurfaceView(@NonNull Context context, boolean enabled) {
        saveBoolean(context, KEY_USE_NATIVE_SURFACE_VIEW, enabled);
    }

    /**
     * When true, old/global installs under .minecraft/versions appear beside isolated
     * instances as "Shared" entries.
     */
    public static boolean isShowSharedInstalls(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_SHOW_SHARED_INSTALLS, false);
    }

    public static void setShowSharedInstalls(@NonNull Context context, boolean enabled) {
        saveBoolean(context, KEY_SHOW_SHARED_INSTALLS, enabled);
    }

    @NonNull
    public static String getSelectedInstanceFilter(@NonNull Context context, @NonNull String fallback) {
        String value = prefs(context).getString(KEY_SELECTED_INSTANCE_FILTER, fallback);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public static void setSelectedInstanceFilter(@NonNull Context context, @NonNull String filter) {
        prefs(context).edit().putString(KEY_SELECTED_INSTANCE_FILTER, filter).commit();
    }

    public static void recordInstancePlayed(@NonNull Context context, @NonNull String instanceId) {
        if (instanceId.trim().isEmpty()) return;
        prefs(context).edit()
                .putLong(KEY_RECENT_INSTANCE_PREFIX + instanceId, System.currentTimeMillis())
                .apply();
    }

    public static long getInstanceLastPlayed(@NonNull Context context, @NonNull String instanceId) {
        if (instanceId.trim().isEmpty()) return 0L;
        return prefs(context).getLong(KEY_RECENT_INSTANCE_PREFIX + instanceId, 0L);
    }

    public static void clearInstancePlayed(@NonNull Context context, @NonNull String instanceId) {
        if (instanceId.trim().isEmpty()) return;
        prefs(context).edit().remove(KEY_RECENT_INSTANCE_PREFIX + instanceId).apply();
    }

    /**
     * When enabled, loader installs are flattened after installation so the
     * matching vanilla version folder can be removed if no other profile still
     * inherits from it. Disabled by default because it permanently deletes the
     * shared vanilla version folder when safe.
     */
    public static boolean isRemoveInheritedVanillaAfterLoaderInstall(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_REMOVE_INHERITED_VANILLA_AFTER_LOADER_INSTALL, false);
    }

    public static void setRemoveInheritedVanillaAfterLoaderInstall(@NonNull Context context, boolean enabled) {
        saveBoolean(context, KEY_REMOVE_INHERITED_VANILLA_AFTER_LOADER_INSTALL, enabled);
    }

    @NonNull
    public static String getSelectedRendererIdentifier(@NonNull Context context) {
        String value = prefs(context).getString(KEY_SELECTED_RENDERER_IDENTIFIER, DEFAULT_RENDERER_IDENTIFIER);
        return value == null || value.trim().isEmpty() ? DEFAULT_RENDERER_IDENTIFIER : value;
    }

    public static void setSelectedRendererIdentifier(@NonNull Context context, @NonNull String rendererIdentifier) {
        prefs(context).edit().putString(KEY_SELECTED_RENDERER_IDENTIFIER, rendererIdentifier).commit();
    }

    @NonNull
    public static String getSelectedVulkanDriverName(@NonNull Context context) {
        String value = prefs(context).getString(KEY_SELECTED_VULKAN_DRIVER_NAME, DEFAULT_VULKAN_DRIVER_NAME);
        return value == null || value.trim().isEmpty() ? DEFAULT_VULKAN_DRIVER_NAME : value;
    }

    public static void setSelectedVulkanDriverName(@NonNull Context context, @NonNull String driverName) {
        prefs(context).edit().putString(KEY_SELECTED_VULKAN_DRIVER_NAME, driverName).commit();
    }

    /**
     * When enabled, launcher/Vulkan bridge code avoids custom Turnip/Adreno
     * DRIVER_PATH/VK_ICD values and lets Android's system Vulkan loader handle
     * Vulkan. This is global because Vulkan mods can use Vulkan even when the
     * selected OpenGL renderer is not Vulkan Zink.
     */
    public static boolean isUseSystemVulkanDriver(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_USE_SYSTEM_VULKAN_DRIVER, false);
    }

    public static void setUseSystemVulkanDriver(@NonNull Context context, boolean enabled) {
        saveBoolean(context, KEY_USE_SYSTEM_VULKAN_DRIVER, enabled);
    }

    /**
     * System Vulkan and forced 26+ OpenGL are mutually exclusive. Save both in
     * one synchronous editor so GameActivity / launch code cannot read stale or
     * contradictory values from another process.
     */
    public static void setSystemVulkanMode(@NonNull Context context, boolean enabled) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putBoolean(KEY_USE_SYSTEM_VULKAN_DRIVER, enabled);
        if (enabled) {
            editor.putBoolean(KEY_USE_OPENGL_FOR_MC_26_PLUS, false);
        }
        boolean saved = editor.commit();
        if (!saved) {
            SharedPreferences.Editor fallback = prefs(context).edit()
                    .putBoolean(KEY_USE_SYSTEM_VULKAN_DRIVER, enabled);
            if (enabled) {
                fallback.putBoolean(KEY_USE_OPENGL_FOR_MC_26_PLUS, false);
            }
            fallback.apply();
        }
    }

    /**
     * Only applies to Mojang's 26.x+ Vulkan/OpenGL backend selection. Older
     * versions do not use this options.txt value.
     */
    public static boolean isUseOpenGlForMinecraft26Plus(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_USE_OPENGL_FOR_MC_26_PLUS, false);
    }

    public static void setUseOpenGlForMinecraft26Plus(@NonNull Context context, boolean enabled) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putBoolean(KEY_USE_OPENGL_FOR_MC_26_PLUS, enabled);
        if (enabled) {
            editor.putBoolean(KEY_USE_SYSTEM_VULKAN_DRIVER, false);
        }
        boolean saved = editor.commit();
        if (!saved) {
            SharedPreferences.Editor fallback = prefs(context).edit()
                    .putBoolean(KEY_USE_OPENGL_FOR_MC_26_PLUS, enabled);
            if (enabled) {
                fallback.putBoolean(KEY_USE_SYSTEM_VULKAN_DRIVER, false);
            }
            fallback.apply();
        }
    }

    /**
     * Shows a small read-only latest-log overlay on the left side of GameActivity.
     * Disabled by default so normal gameplay remains clean.
     */
    public static boolean isShowGameLogOverlay(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_SHOW_GAME_LOG_OVERLAY, false);
    }

    public static void setShowGameLogOverlay(@NonNull Context context, boolean enabled) {
        saveBoolean(context, KEY_SHOW_GAME_LOG_OVERLAY, enabled);
    }

    /**
     * Shows the small floating in-game settings button that opens the controller/button
     * overlay while Minecraft is running. Enabled by default so users can still reach
     * the overlay without relying on Android's Back button.
     */
    public static boolean isShowInGameSettingsButton(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_SHOW_IN_GAME_SETTINGS_BUTTON, true);
    }

    public static void setShowInGameSettingsButton(@NonNull Context context, boolean enabled) {
        saveBoolean(context, KEY_SHOW_IN_GAME_SETTINGS_BUTTON, enabled);
    }

    /**
     * Enables compatibility hooks for controller mods that need launcher-side SDL/native
     * behavior on Android, such as Legacy4J and Controllable. Enabled by default because
     * the hooks only activate when a matching mod jar exists.
     */
    public static boolean isSdlControllerModCompatEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_ENABLE_SDL_CONTROLLER_MOD_COMPAT, true);
    }

    public static void setSdlControllerModCompatEnabled(@NonNull Context context, boolean enabled) {
        saveBoolean(context, KEY_ENABLE_SDL_CONTROLLER_MOD_COMPAT, enabled);
    }

    public static boolean isShowControllerModCompatWarnings(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_SHOW_CONTROLLER_MOD_COMPAT_WARNINGS, true);
    }

    public static void setShowControllerModCompatWarnings(@NonNull Context context, boolean enabled) {
        saveBoolean(context, KEY_SHOW_CONTROLLER_MOD_COMPAT_WARNINGS, enabled);
    }

    public static int getAllocatedMemoryMb(@NonNull Context context, int fallbackMb) {
        return prefs(context).getInt(KEY_ALLOCATED_MEMORY_MB, fallbackMb);
    }

    public static void setAllocatedMemoryMb(@NonNull Context context, int memoryMb) {
        prefs(context).edit().putInt(KEY_ALLOCATED_MEMORY_MB, memoryMb).commit();
    }

    /**
     * Resolution scale applied to the game render buffer.
     * 100 = native device view size, 25 = quarter-size render buffer, 200 = double-size render buffer.
     */
    public static int getGameResolutionScalePercent(@NonNull Context context) {
        SharedPreferences resolutionPreferences = resolutionPrefs(context);
        if (resolutionPreferences.contains(KEY_GAME_RESOLUTION_SCALE_PERCENT)) {
            return clampGameResolutionScalePercent(
                    resolutionPreferences.getInt(
                            KEY_GAME_RESOLUTION_SCALE_PERCENT,
                            DEFAULT_GAME_RESOLUTION_SCALE_PERCENT
                    )
            );
        }

        int legacyValue = prefs(context).getInt(
                KEY_GAME_RESOLUTION_SCALE_PERCENT,
                DEFAULT_GAME_RESOLUTION_SCALE_PERCENT
        );
        int clampedLegacyValue = clampGameResolutionScalePercent(legacyValue);
        setGameResolutionScalePercent(context, clampedLegacyValue);
        return clampedLegacyValue;
    }

    public static void setGameResolutionScalePercent(@NonNull Context context, int percent) {
        int clamped = clampGameResolutionScalePercent(percent);

        // GameActivity runs in the :game process while LauncherSettingsActivity runs in
        // the default app process. A normal SharedPreferences.apply() write can leave
        // the other process holding its old cached value, which is why the settings
        // screen could keep snapping the slider back to 70% after the in-game dialog
        // had already saved 100%. Keep this specific setting in a tiny dedicated
        // multi-process file and commit it synchronously so both processes see the
        // latest render scale before creating/resizing MinecraftGLSurface.
        boolean resolutionSaved = resolutionPrefs(context).edit()
                .putInt(KEY_GAME_RESOLUTION_SCALE_PERCENT, clamped)
                .commit();
        boolean legacySaved = prefs(context).edit()
                .putInt(KEY_GAME_RESOLUTION_SCALE_PERCENT, clamped)
                .commit();

        if (!resolutionSaved) {
            resolutionPrefs(context).edit()
                    .putInt(KEY_GAME_RESOLUTION_SCALE_PERCENT, clamped)
                    .apply();
        }
        if (!legacySaved) {
            prefs(context).edit()
                    .putInt(KEY_GAME_RESOLUTION_SCALE_PERCENT, clamped)
                    .apply();
        }
    }

    public static int clampGameResolutionScalePercent(int percent) {
        if (percent < MIN_GAME_RESOLUTION_SCALE_PERCENT) return MIN_GAME_RESOLUTION_SCALE_PERCENT;
        if (percent > MAX_GAME_RESOLUTION_SCALE_PERCENT) return MAX_GAME_RESOLUTION_SCALE_PERCENT;
        return percent;
    }

    /**
     * Keeps the existing JavaLauncher behavior by default: Minecraft launches in immersive fullscreen.
     * Turning this off lets Android system bars/safe areas remain visible.
     */
    public static boolean isForceFullscreenMode(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_FORCE_FULLSCREEN_MODE, true);
    }

    public static void setForceFullscreenMode(@NonNull Context context, boolean enabled) {
        saveBoolean(context, KEY_FORCE_FULLSCREEN_MODE, enabled);
    }

    /**
     * Adds a small safe inset around the game container for devices where rounded display corners
     * or cutouts hide the game edge. This does not change the physical screen shape.
     */
    public static boolean isAvoidRoundedDisplayCorners(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_AVOID_ROUNDED_DISPLAY_CORNERS, false);
    }

    public static void setAvoidRoundedDisplayCorners(@NonNull Context context, boolean enabled) {
        saveBoolean(context, KEY_AVOID_ROUNDED_DISPLAY_CORNERS, enabled);
    }

    /**
     * Allows the game/activity window to draw into display cutout/notch areas on supported devices.
     * Disabled by default because some phones hide UI behind camera cutouts unless the user opts in.
     */
    public static boolean isIgnoreDisplayCutout(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_IGNORE_DISPLAY_CUTOUT, false);
    }

    public static void setIgnoreDisplayCutout(@NonNull Context context, boolean enabled) {
        saveBoolean(context, KEY_IGNORE_DISPLAY_CUTOUT, enabled);
    }

    /**
     * Advanced/debug option. When enabled, controller mod compatibility will run
     * the full launcher SDL initialization path for mods such as Controllable.
     * Disabled by default because normal built-in controls should not use SDL routing.
     */
    public static boolean isForceSdlControllerBridge(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_FORCE_SDL_CONTROLLER_BRIDGE, false);
    }

    public static void setForceSdlControllerBridge(@NonNull Context context, boolean enabled) {
        saveBoolean(context, KEY_FORCE_SDL_CONTROLLER_BRIDGE, enabled);
    }
}
