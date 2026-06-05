/*
 * Derived from PojavLauncher native bridge code.
 *
 * Original project:
 * https://github.com/PojavLauncherTeam/PojavLauncher
 *
 * Original license: GNU Lesser General Public License v3.0,
 * unless this file or a bundled component states a different license.
 *
 * DroidBridge modifications:
 * Copyright (c) 2026 DNA Mobile Applications.
 *
 * This file remains available under the terms of the GNU LGPLv3
 * unless the original file or bundled component states a different license.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */

#include <android/api-level.h>
#include <android/log.h>
#include <jni.h>

#include <environ/environ.h>

#include <dlfcn.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

extern void* maybe_load_vulkan(void);

#define DB_OPENGL_PROXY_SONAME "libGLDroidBridge.so"
#define DB_OPENGL_PROXY_ALT_SONAME "libGLDroidBridgeMesa.so"
#define TAG "LwjglLinkerHook"

static const char* basename_or_self(const char* filename) {
    if (filename == NULL) return "";
    const char* base = strrchr(filename, '/');
    return base != NULL ? base + 1 : filename;
}

/**
 * Returns true when the requested library name is a Vulkan loader soname.
 * Accepts both direct names and full paths.
 */
static bool is_vulkan_loader_name(const char* filename) {
    const char* base = basename_or_self(filename);
    return strcmp(base, "libvulkan.so") == 0 ||
           strcmp(base, "libvulkan.so.1") == 0;
}

/**
 * DroidBridge OpenGL proxy names.
 *
 * Mojo uses libGLMojo.so as a sentinel name so LWJGL enters its custom
 * RenderSpec path instead of a normal system libGL load. DroidBridge does the
 * same thing with its own names. The legacy name remains accepted only as a
 * compatibility fallback for users who already have a JVM arg set from an
 * older test build.
 */
static bool is_droidbridge_opengl_proxy_name(const char* filename) {
    if (filename == NULL) return false;
    const char* base = basename_or_self(filename);

    /* Exact DroidBridge-owned sentinel names. */
    if (strcmp(base, DB_OPENGL_PROXY_SONAME) == 0 ||
        strcmp(base, DB_OPENGL_PROXY_ALT_SONAME) == 0) {
        return true;
    }

    /* Be tolerant of LWJGL name-mapping differences, such as GLDroidBridge,
     * /full/path/libGLDroidBridge.so, or accidental liblib... wrapping. */
    if (strstr(base, "GLDroidBridge") != NULL ||
        strstr(base, "DroidBridgeMesa") != NULL) {
        return true;
    }

    /* Legacy compatibility only. DroidBridge's Java side should not request it. */
    if (strcmp(base, "libGLMojo.so") == 0 || strstr(base, "GLMojo") != NULL) {
        return true;
    }

    return false;
}

static const char* first_non_empty(const char* a, const char* b, const char* c,
                                   const char* d, const char* e) {
    if (a != NULL && a[0] != '\0') return a;
    if (b != NULL && b[0] != '\0') return b;
    if (c != NULL && c[0] != '\0') return c;
    if (d != NULL && d[0] != '\0') return d;
    if (e != NULL && e[0] != '\0') return e;
    return NULL;
}

static void* try_dlopen_with_log(const char* library, int mode) {
    if (library == NULL || library[0] == '\0') return NULL;

    dlerror();
    void* handle = dlopen(library, mode);
    if (handle != NULL) {
        printf("LWJGL linkerhook: DroidBridge RenderSpec using %s handle=%p\n", library, handle);
        return handle;
    }

    const char* err = dlerror();
    printf("LWJGL linkerhook: DroidBridge RenderSpec failed to open %s: %s\n",
           library,
           err != NULL ? err : "unknown");
    return NULL;
}

/**
 * Acquire the GL provider handle for LWJGL's OpenGL module.
 *
 * This intentionally does not say Mojo anywhere in the DroidBridge log path.
 * The Java side should request DB_OPENGL_PROXY_SONAME through
 * -Dorg.lwjgl.opengl.libname=libGLDroidBridge.so.
 */
static void* acquire_droidbridge_opengl_handle(int mode) {
    int dl_mode = mode;
    if ((dl_mode & RTLD_NOW) == 0 && (dl_mode & RTLD_LAZY) == 0) {
        dl_mode |= RTLD_NOW;
    }
    dl_mode |= RTLD_GLOBAL;

    const char* renderer = getenv("POJAV_RENDERER");
    const char* mesa_mode = getenv("DROIDBRIDGE_MESA_MODE");
    const char* mesa_driver = getenv("DROIDBRIDGE_MESA_DRIVER");
    const char* renderer_mesa_mode = getenv("POJAV_RENDERER_MESA_MODE");

    printf("LWJGL linkerhook: DroidBridge RenderSpec request renderer=%s mesaMode=%s mesaDriver=%s rendererMesaMode=%s\n",
           renderer != NULL ? renderer : "",
           mesa_mode != NULL ? mesa_mode : "",
           mesa_driver != NULL ? mesa_driver : "",
           renderer_mesa_mode != NULL ? renderer_mesa_mode : "");

    const char* preferred = first_non_empty(
            getenv("DROIDBRIDGE_RENDERSPEC_EGL"),
            getenv("DROIDBRIDGE_MESA_EGL"),
            getenv("POJAVEXEC_EGL"),
            getenv("POJAV_EGL_LIBRARY"),
            getenv("POJAVEXEC_EGL_LIBRARY")
    );

    void* handle = try_dlopen_with_log(preferred, dl_mode);
    if (handle != NULL) return handle;

    handle = try_dlopen_with_log("libEGL_mesa.so", dl_mode);
    if (handle != NULL) return handle;

    handle = try_dlopen_with_log("libEGL.so", dl_mode);
    if (handle != NULL) return handle;

    printf("LWJGL linkerhook: DroidBridge RenderSpec failed; returning NULL for OpenGL proxy\n");
    return NULL;
}

/**
 * LWJGL dlopen hook.
 *
 * This keeps the normal dlopen() behavior for most libraries, but intercepts:
 * - Vulkan loader requests so Android can provide the system/custom Vulkan handle
 * - DroidBridge OpenGL proxy requests so LWJGL receives the same Mesa/EGL provider
 *   as the native window bridge instead of loading a random system libGL.
 */
static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv* env,
                            __attribute__((unused)) jclass clazz,
                            jlong filename_ptr,
                            jint jmode) {
    const char* filename = (const char*) filename_ptr;
    int mode = (int) jmode;

    if (is_vulkan_loader_name(filename)) {
        printf("LWJGL linkerhook: intercepted Vulkan load for %s\n", filename);

        void* handle = maybe_load_vulkan();
        if (handle != NULL) {
            printf("LWJGL linkerhook: using custom/system Vulkan handle %p for %s\n",
                   handle,
                   filename);
            return (jlong) handle;
        }

        printf("LWJGL linkerhook: maybe_load_vulkan() returned NULL, falling back to dlopen(%s)\n",
               filename);
    }

    if (is_droidbridge_opengl_proxy_name(filename)) {
        printf("LWJGL linkerhook: matched DroidBridge OpenGL proxy filename=%s\n",
               filename != NULL ? filename : "");
        void* handle = acquire_droidbridge_opengl_handle(mode);
        if (handle != NULL) {
            return (jlong) handle;
        }
    }

    return (jlong) dlopen(filename, mode);
}

/**
 * Install the LWJGL dlopen hook.
 *
 * This allows us to:
 * - override Vulkan loader resolution when needed
 * - override LWJGL's OpenGL provider for DroidBridge Mesa RenderSpec
 * - keep library loading inside the launcher namespace on older Android builds
 */
void installLwjglDlopenHook(void) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "Installing LWJGL dlopen() hook");

    JNIEnv* env = pojav_environ->runtimeJNIEnvPtr_JRE;
    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if (dynamicLinkLoader == NULL) {
        __android_log_print(ANDROID_LOG_ERROR,
                            TAG,
                            "Failed to find org/lwjgl/system/linux/DynamicLinkLoader");
        (*env)->ExceptionClear(env);
        return;
    }

    JNINativeMethod ndlopenMethod[] = {
            {"ndlopen", "(JI)J", &ndlopen_bugfix}
    };

    if ((*env)->RegisterNatives(env, dynamicLinkLoader, ndlopenMethod, 1) != 0) {
        __android_log_print(ANDROID_LOG_ERROR,
                            TAG,
                            "Failed to register hooked ndlopen() implementation");
        (*env)->ExceptionClear(env);
    }
}
