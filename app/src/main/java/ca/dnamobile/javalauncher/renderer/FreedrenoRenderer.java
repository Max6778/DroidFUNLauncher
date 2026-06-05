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

package ca.dnamobile.javalauncher.renderer;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mojo-style Mesa Freedreno/KGSL renderer selection.
 *
 * The old saved Freedreno UUID is intentionally preserved, but it now follows
 * the same direct KGSL Mesa route Mojo uses on Adreno instead of forcing the
 * Zink/Turnip path. Zink can load and create a desktop context, but on the
 * tested Adreno 740 build it corrupts chunk/cloud geometry.
 */
public final class FreedrenoRenderer implements RendererInterface {
    @NonNull
    @Override
    public String getRendererId() {
        return DroidBridgeMesaSupport.RENDERER_ID_FREEDRENO_KGSL;
    }

    @NonNull
    @Override
    public String getUniqueIdentifier() {
        return DroidBridgeMesaSupport.LEGACY_FREEDRENO_RENDERER_UUID;
    }

    @NonNull
    @Override
    public String getRendererName() {
        return "Mesa Freedreno KGSL";
    }

    @NonNull
    @Override
    public String getRendererDescription() {
        return "Mesa renderer for Adreno using the bundled Freedreno/KGSL Mesa driver path. "
                + "This mirrors Mojo Launcher’s freedreno_kgsl route instead of Zink/Turnip.";
    }

    @NonNull
    @Override
    public Map<String, String> getRendererEnv() {
        LinkedHashMap<String, String> env = new LinkedHashMap<>();

        env.put("DROIDBRIDGE_MESA", "1");
        env.put("DROIDBRIDGE_MESA_MODE", "freedreno_kgsl");
        env.put("DROIDBRIDGE_MESA_SAFE_SWAPS", "1");
        env.put("DROIDBRIDGE_MESA_DRIVER", "kgsl");

        // Mojo-style path: use the Mesa KGSL loader override, not Zink/Turnip.
        env.put("POJAV_RENDERER", "freedreno_kgsl");
        env.put("POJAV_RENDERER_MESA_MODE", "freedreno_kgsl");
        env.put("MESA_LOADER_DRIVER_OVERRIDE", "kgsl");
        env.put("GALLIUM_DRIVER", "");
        env.put("EGL_PLATFORM", "android");
        env.put("FORCE_VSYNC", "false");

        // Match Mojo’s Mesa-side compatibility flags.
        env.put("LIBGL_ES", "2");
        // Minecraft 26.x requires #version 330 shaders. Mojo's renderspec hook advertises a higher profile;
        // DroidBridge has to expose the same profile through Mesa until that hook is fully ported.
        env.put("MESA_GL_VERSION_OVERRIDE", "3.3COMPAT");
        env.put("MESA_GLSL_VERSION_OVERRIDE", "330");
        env.put("MESA_GLSL_CACHE_DISABLE", "false");
        env.put("MESA_SHADER_CACHE_DISABLE", "false");
        env.put("LIBGL_MIPMAP", "3");
        env.put("LIBGL_NOINTOVLHACK", "1");
        env.put("LIBGL_NORMALIZE", "1");
        env.put("LIBGL_NOERROR", "0");
        env.put("allow_higher_compat_version", "true");
        env.put("force_glsl_extensions_warn", "true");
        env.put("allow_glsl_extension_directive_midshader", "true");

        // DroidBridge still needs the GL bridge to request a desktop OpenGL context.
        // KGSL is the Mesa backend; this flag controls EGL context type only.
        env.put("DROIDBRIDGE_MESA_DESKTOP_GL", "1");
        env.put("DROIDBRIDGE_EGL_FORCE_DESKTOP_GL", "1");
        env.put("DROIDBRIDGE_EGL_NO_SYSTEM_FALLBACK", "1");

        env.put("POJAVEXEC_EGL", DroidBridgeMesaSupport.LIB_EGL_MESA);
        env.put("POJAV_EGL_LIBRARY", DroidBridgeMesaSupport.LIB_EGL_MESA);
        env.put("POJAVEXEC_EGL_LIBRARY", DroidBridgeMesaSupport.LIB_EGL_MESA);
        env.put("POJAV_RENDERER_LIBRARY", DroidBridgeMesaSupport.LIB_EGL_MESA);
        env.put("POJAVEXEC_RENDERER", DroidBridgeMesaSupport.LIB_EGL_MESA);
        env.put("LIB_MESA_NAME", DroidBridgeMesaSupport.LIB_EGL_MESA);

        // KGSL path must not load Turnip/Zink or OSMesa.
        env.put("POJAV_USE_SYSTEM_VULKAN", "");
        env.put("POJAV_LOAD_TURNIP", "");
        env.put("DROIDBRIDGE_LOAD_TURNIP", "");
        env.put("DROIDBRIDGE_USE_CUSTOM_TURNIP", "");
        env.put("DROIDBRIDGE_CUSTOM_VULKAN_DRIVER", "");
        env.put("POJAV_CUSTOM_VULKAN_DRIVER", "");
        env.put("VK_ICD_FILENAMES", "");
        env.put("VK_DRIVER_FILES", "");
        env.put("ZINK_DEBUG", "");
        env.put("ZINK_DESCRIPTORS", "");

        env.put("OSMESA_LIB", "");
        env.put("POJAV_OSMESA_LIBRARY", "");
        env.put("OSMESA_LIBRARY", "");
        env.put("LIBGL_OSMESA", "");
        return env;
    }

    @NonNull
    @Override
    public List<String> getDlopenLibrary() {
        // Do not Java-preload Mesa EGL. The native bridge must own the namespace.
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public String getRendererLibrary() {
        return DroidBridgeMesaSupport.LIB_EGL_MESA;
    }

    @Override
    public String getRendererEGL() {
        return DroidBridgeMesaSupport.LIB_EGL_MESA;
    }
}
