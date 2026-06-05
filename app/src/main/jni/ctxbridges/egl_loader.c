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

//
// Created by maks on 21.09.2022.
// DroidBridge: Mesa/KGSL loader hardening for Android EGL platform display.
//
#include <stddef.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <string.h>
#include <stdio.h>
#include <stdbool.h>
#include <stdarg.h>
#include <android/log.h>
#include "br_loader.h"
#include "egl_loader.h"
#include "../driver_helper/nsbypass.h"

static const char* EGL_LOADER_TAG = "DroidBridgeEGLLoader";
static void* g_egl_handle = NULL;
static char g_egl_loaded_name[512];

EGLBoolean (*eglMakeCurrent_p) (EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx);
EGLBoolean (*eglDestroyContext_p) (EGLDisplay dpy, EGLContext ctx);
EGLBoolean (*eglDestroySurface_p) (EGLDisplay dpy, EGLSurface surface);
EGLBoolean (*eglTerminate_p) (EGLDisplay dpy);
EGLBoolean (*eglReleaseThread_p) (void);
EGLContext (*eglGetCurrentContext_p) (void);
EGLDisplay (*eglGetDisplay_p) (NativeDisplayType display);
EGLDisplay (*eglGetPlatformDisplay_p) (EGLenum platform, void* native_display, const EGLint* attrib_list);
EGLBoolean (*eglInitialize_p) (EGLDisplay dpy, EGLint *major, EGLint *minor);
EGLBoolean (*eglChooseConfig_p) (EGLDisplay dpy, const EGLint *attrib_list, EGLConfig *configs, EGLint config_size, EGLint *num_config);
EGLBoolean (*eglGetConfigAttrib_p) (EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint *value);
EGLBoolean (*eglBindAPI_p) (EGLenum api);
EGLSurface (*eglCreatePbufferSurface_p) (EGLDisplay dpy, EGLConfig config, const EGLint *attrib_list);
EGLSurface (*eglCreateWindowSurface_p) (EGLDisplay dpy, EGLConfig config, NativeWindowType window, const EGLint *attrib_list);
EGLBoolean (*eglSwapBuffers_p) (EGLDisplay dpy, EGLSurface draw);
EGLint (*eglGetError_p) (void);
EGLContext (*eglCreateContext_p) (EGLDisplay dpy, EGLConfig config, EGLContext share_list, const EGLint *attrib_list);
EGLBoolean (*eglSwapInterval_p) (EGLDisplay dpy, EGLint interval);
EGLBoolean (*eglSurfaceAttrib_p) (EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint value);
EGLSurface (*eglGetCurrentSurface_p) (EGLint readdraw);
EGLBoolean (*eglQuerySurface_p)(EGLDisplay display, EGLSurface surface, EGLint attribute, EGLint * value);
const char* (*eglQueryString_p)(EGLDisplay display, EGLint name);

static bool env_enabled(const char* name) {
    const char* value = getenv(name);
    return value != NULL && value[0] != '\0' && strcmp(value, "0") != 0 && strcmp(value, "false") != 0;
}

static void loader_log(const char* fmt, ...) {
    char buffer[2048];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, ap);
    va_end(ap);
    __android_log_print(ANDROID_LOG_INFO, EGL_LOADER_TAG, "%s", buffer);
    fprintf(stderr, "%s: %s\n", EGL_LOADER_TAG, buffer);
    fflush(stderr);
    fprintf(stdout, "%s: %s\n", EGL_LOADER_TAG, buffer);
    fflush(stdout);
}

static void* try_dlopen_egl(const char* name, int flags) {
    if (name == NULL || name[0] == '\0') return NULL;
    dlerror();
    void* handle = dlopen(name, flags);
    if (handle != NULL) {
        snprintf(g_egl_loaded_name, sizeof(g_egl_loaded_name), "%s", name);
        loader_log("loaded EGL library %s handle=%p flags=0x%x", name, handle, flags);
        return handle;
    }
    const char* error = dlerror();
    loader_log("failed to load EGL library %s: %s", name, error ? error : "unknown");
    return NULL;
}


static const char* file_name_only(const char* path) {
    if (path == NULL || path[0] == '\0') return path;
    const char* slash = strrchr(path, '/');
    return slash != NULL ? slash + 1 : path;
}

static void* try_namespace_egl_once(const char* namespace_path, const char* short_name, int flags) {
    if (namespace_path == NULL || namespace_path[0] == '\0') return NULL;

    if (!linker_ns_load(namespace_path)) {
        loader_log("Mesa namespace load failed path=%s library=%s", namespace_path, short_name);
        return NULL;
    }

    /* Mojo's working KGSL path loads libEGL_mesa.so in an Android linker namespace.
     * Keep this local to the namespace; EGL symbols are resolved from the returned handle.
     */
    dlerror();
    void* handle = linker_ns_dlopen(short_name, flags | RTLD_GLOBAL);
    if (handle != NULL) {
        snprintf(g_egl_loaded_name, sizeof(g_egl_loaded_name), "%s (namespace)", short_name);
        loader_log("Loaded EGL %s (in namespace: 1) path=%s handle=%p", short_name, namespace_path, handle);
        return handle;
    }

    const char* error = dlerror();
    loader_log("Mesa namespace EGL short-name load failed library=%s path=%s error=%s",
               short_name, namespace_path, error ? error : "unknown");

    /* Some Android 13 linker configurations create the namespace but do not use
     * the custom ld_library_path for short-name lookup. If that happens, retry
     * the absolute APK native path inside the same namespace. This still keeps
     * Mesa in the namespace, unlike the old plain dlopen fallback.
     */
    const char* absolute = getenv("DROIDBRIDGE_MESA_EGL");
    if (absolute != NULL && absolute[0] != '\0') {
        dlerror();
        handle = linker_ns_dlopen(absolute, flags | RTLD_GLOBAL);
        if (handle != NULL) {
            snprintf(g_egl_loaded_name, sizeof(g_egl_loaded_name), "%s (namespace)", absolute);
            loader_log("Loaded EGL %s (in namespace: 1) path=%s handle=%p", absolute, namespace_path, handle);
            return handle;
        }
        error = dlerror();
        loader_log("Mesa namespace EGL absolute load failed library=%s path=%s error=%s",
                   absolute, namespace_path, error ? error : "unknown");
    }

    return NULL;
}

static void* try_namespace_egl(const char* library_name, int flags) {
    if (!env_enabled("DROIDBRIDGE_MESA_NAMESPACE")) return NULL;

    const char* short_name = file_name_only(library_name);
    if (short_name == NULL || short_name[0] == '\0') short_name = "libEGL_mesa.so";

    /* Mojo passes POJAV_NATIVEDIR as a single app-native lib directory. Passing a
     * colon-separated LD path here can make android_create_namespace fail before
     * libEGL_mesa.so is even tried. Use the single native directory first; Mesa
     * still finds DRI driver aliases through LIBGL_DRIVERS_PATH/DRIVER_PATH.
     */
    void* handle = try_namespace_egl_once(getenv("DROIDBRIDGE_MESA_NATIVE_DIR"), short_name, flags);
    if (handle != NULL) return handle;

    handle = try_namespace_egl_once(getenv("POJAV_NATIVEDIR"), short_name, flags);
    if (handle != NULL) return handle;

    /* Last resort: allow an explicit override path for diagnostics. */
    handle = try_namespace_egl_once(getenv("DROIDBRIDGE_MESA_NAMESPACE_PATH"), short_name, flags);
    if (handle != NULL) return handle;

    loader_log("Mesa namespace requested but namespace EGL loading did not succeed");
    return NULL;
}

void* droidbridge_egl_get_handle(void) {
    return g_egl_handle;
}

const char* droidbridge_egl_get_loaded_name(void) {
    return g_egl_loaded_name[0] != '\0' ? g_egl_loaded_name : "<none>";
}

void dlsym_EGL() {
    if (g_egl_handle != NULL) return;

    char* eglName = NULL;
    char* gles = getenv("LIBGL_GLES");
    bool mesa = env_enabled("DROIDBRIDGE_MESA");

    if (gles && !strncmp(gles, "libGLESv2_angle.so", 18)) {
        eglName = "libEGL_angle.so";
    } else {
        eglName = getenv("POJAVEXEC_EGL");
    }

    int flags = RTLD_NOW | (mesa ? RTLD_GLOBAL : RTLD_LOCAL);

    if (mesa) {
        /* Prefer Mojo-style Android linker namespace loading first. On KGSL,
         * loading libEGL_mesa.so in the normal app namespace can resolve symbols
         * but still fail eglInitialize(EGL_NOT_INITIALIZED). Mojo's working log
         * shows: Loaded EGL libEGL_mesa.so (in namespace: 1).
         */
        g_egl_handle = try_namespace_egl(getenv("POJAVEXEC_EGL"), flags);
        if (g_egl_handle == NULL) {
            g_egl_handle = try_namespace_egl(getenv("DROIDBRIDGE_MESA_EGL"), flags);
        }
        if (g_egl_handle == NULL) {
            /* Fallback to the absolute APK-native Mesa EGL path. */
            g_egl_handle = try_dlopen_egl(getenv("DROIDBRIDGE_MESA_EGL"), flags);
        }
    }

    if (g_egl_handle == NULL && eglName != NULL) {
        g_egl_handle = try_dlopen_egl(eglName, flags);
    }

    if (g_egl_handle == NULL) {
        g_egl_handle = try_dlopen_egl("libEGL.so", RTLD_NOW | RTLD_LOCAL);
    }

    if (g_egl_handle == NULL) abort();

#define EGLSYM(name) GLGetProcAddress(g_egl_handle, name)
    eglBindAPI_p = EGLSYM("eglBindAPI");
    eglChooseConfig_p = EGLSYM("eglChooseConfig");
    eglCreateContext_p = EGLSYM("eglCreateContext");
    eglCreatePbufferSurface_p = EGLSYM("eglCreatePbufferSurface");
    eglCreateWindowSurface_p = EGLSYM("eglCreateWindowSurface");
    eglDestroyContext_p = EGLSYM("eglDestroyContext");
    eglDestroySurface_p = EGLSYM("eglDestroySurface");
    eglGetConfigAttrib_p = EGLSYM("eglGetConfigAttrib");
    eglGetCurrentContext_p = EGLSYM("eglGetCurrentContext");
    eglGetDisplay_p = EGLSYM("eglGetDisplay");
    eglGetPlatformDisplay_p = EGLSYM("eglGetPlatformDisplay");
    if (eglGetPlatformDisplay_p == NULL) {
        eglGetPlatformDisplay_p = EGLSYM("eglGetPlatformDisplayEXT");
    }
    eglGetError_p = EGLSYM("eglGetError");
    eglInitialize_p = EGLSYM("eglInitialize");
    eglMakeCurrent_p = EGLSYM("eglMakeCurrent");
    eglSwapBuffers_p = EGLSYM("eglSwapBuffers");
    eglReleaseThread_p = EGLSYM("eglReleaseThread");
    eglSwapInterval_p = EGLSYM("eglSwapInterval");
    eglSurfaceAttrib_p = EGLSYM("eglSurfaceAttrib");
    eglTerminate_p = EGLSYM("eglTerminate");
    eglGetCurrentSurface_p = EGLSYM("eglGetCurrentSurface");
    eglQuerySurface_p = EGLSYM("eglQuerySurface");
    eglQueryString_p = EGLSYM("eglQueryString");
#undef EGLSYM

    loader_log("EGL symbols loaded from %s getPlatformDisplay=%p queryString=%p",
               droidbridge_egl_get_loaded_name(), eglGetPlatformDisplay_p, eglQueryString_p);
}
