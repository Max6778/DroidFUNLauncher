LOCAL_PATH := $(call my-dir)
HERE_PATH := $(LOCAL_PATH)

# include $(HERE_PATH)/crash_dump/libbase/Android.mk
# include $(HERE_PATH)/crash_dump/libbacktrace/Android.mk
# include $(HERE_PATH)/crash_dump/debuggerd/Android.mk


LOCAL_PATH := $(HERE_PATH)

$(call import-module,prefab/bytehook)
LOCAL_PATH := $(HERE_PATH)


# Mesa direct-EGL mode for DroidBridge Mesa/Freedreno.
#
# Do NOT build an app-local LOCAL_MODULE := EGL shim for the Mojo-style KGSL
# path. Mojo's working path lets libpojavexec load libEGL_mesa.so through its
# own EGL loader while the bridge remains linked against Android's normal EGL
# symbols for ANativeWindow/GLSurface setup. Linking libpojavexec directly to
# the DroidBridge libEGL.so shim made GLFW fail before it could create the
# window/context.


include $(CLEAR_VARS)
LOCAL_LDLIBS := -ldl -llog -landroid -lEGL -lGLESv2
LOCAL_MODULE := pojavexec
LOCAL_SHARED_LIBRARIES := driver_helper
# Keep libpojavexec on Android system EGL symbols; it will still load Mesa via
# POJAVEXEC_EGL=libEGL_mesa.so at runtime, matching Mojo's KGSL path.
LOCAL_CFLAGS += -rdynamic
LOCAL_SRC_FILES := \
    bigcoreaffinity.c \
    egl_bridge.c \
    ctxbridges/br_loader.c \
    ctxbridges/gl_bridge.c \
    ctxbridges/osm_bridge.c \
    ctxbridges/egl_loader.c \
    ctxbridges/osmesa_loader.c \
    ctxbridges/swap_interval_no_egl.c \
    ctxbridges/virgl_bridge.c \
    environ/environ.c \
    input_bridge_v3.c \
    jre_launcher.c \
    utils.c \
    stdio_is.c \
    java_exec_hooks.c \
    lwjgl_dlopen_hook.c

include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := exithook
LOCAL_LDLIBS := -ldl -llog
LOCAL_SHARED_LIBRARIES := bytehook pojavexec
LOCAL_SRC_FILES := exit_hook.c
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_LDLIBS := -ldl -llog -landroid -lEGL -lGLESv2
LOCAL_MODULE := driver_helper
# Enable Android linker namespace loading for Mesa/Freedreno KGSL. This is the
# key difference from Mojo's working log: "Loaded EGL libEGL_mesa.so (in namespace: 1)".
LOCAL_CFLAGS += -DADRENO_POSSIBLE
# Keep driver_helper on Android system EGL symbols; Mesa itself is selected
# dynamically through POJAVEXEC_EGL/libEGL_mesa.so.
LOCAL_SRC_FILES := \
    driver_helper/driver_helper.c \
    driver_helper/nsbypass.c
LOCAL_CFLAGS += -g -rdynamic

include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := linkerhook
LOCAL_SRC_FILES := \
    linkerhook/linkerhook.cpp \
    linkerhook/linkerns.c
LOCAL_LDFLAGS := -z global
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := pojavexec_awt
LOCAL_SRC_FILES := \
    awt_bridge.c
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := awt_headless
include $(BUILD_SHARED_LIBRARY)


LOCAL_PATH := $(HERE_PATH)/awt_xawt
include $(CLEAR_VARS)
LOCAL_MODULE := awt_xawt
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)
LOCAL_SHARED_LIBRARIES := awt_headless
LOCAL_SRC_FILES := xawt_fake.c
include $(BUILD_SHARED_LIBRARY)

# Disable Android heap pointer tagging for child OpenJDK installer processes.
# Used by Forge/NeoForge installer ProcessBuilder path through LD_PRELOAD.
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := disable_heap_tagging
LOCAL_SRC_FILES := disable_heap_tagging.c
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)


# PulseAudio shim for Minecraft Snapshot 7+ / dev.onvoid.webrtc desktop Linux native.
# Android does not ship libpulse.so.0, but WebRTC's linux-aarch64 native asks
# for it. Build libpulse.so with SONAME libpulse.so.0, then preload it from
# Java before launching Minecraft.
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := pulse
LOCAL_SRC_FILES := pulse_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libpulse.so.0
include $(BUILD_SHARED_LIBRARY)


# udev shim for Minecraft Snapshot 7+ / dev.onvoid.webrtc desktop Linux native.
# Android does not ship libudev.so.1, but WebRTC's linux-aarch64 native asks
# for it after the PulseAudio shim is satisfied. Build libudev.so with SONAME
# libudev.so.1, then preload it from Java before launching Minecraft.
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := udev
LOCAL_SRC_FILES := udev_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libudev.so.1
include $(BUILD_SHARED_LIBRARY)



# X11 shim for Minecraft Snapshot 7+ / dev.onvoid.webrtc desktop Linux native.
# Android does not ship libX11.so.6, but WebRTC's linux-aarch64 native asks
# for it after PulseAudio and udev are satisfied. Build libX11.so with SONAME
# libX11.so.6, then preload it from Java before launching Minecraft.
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := X11
LOCAL_SRC_FILES := x11_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libX11.so.6
include $(BUILD_SHARED_LIBRARY)


# Xfixes shim for Minecraft Snapshot 7+ / dev.onvoid.webrtc desktop Linux native.
# Android does not ship libXfixes.so.3, but WebRTC's linux-aarch64 native asks
# for it after PulseAudio, udev, and X11 are satisfied. Build libXfixes.so
# with SONAME libXfixes.so.3, then preload it from Java before launching Minecraft.
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := Xfixes
LOCAL_SRC_FILES := xfixes_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libXfixes.so.3
include $(BUILD_SHARED_LIBRARY)


# Xrandr shim for Minecraft Snapshot 7+ / dev.onvoid.webrtc desktop Linux native.
# Android does not ship libXrandr.so.2, but WebRTC's linux-aarch64 native asks
# for it after PulseAudio, udev, X11, and Xfixes are satisfied.
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := Xrandr
LOCAL_SRC_FILES := xrandr_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libXrandr.so.2
include $(BUILD_SHARED_LIBRARY)


# Desktop Linux compatibility shim for Minecraft Snapshot WebRTC: libXcomposite.so.1
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := Xcomposite
LOCAL_SRC_FILES := xcomposite_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libXcomposite.so.1
include $(BUILD_SHARED_LIBRARY)


# Desktop Linux compatibility shim for Minecraft Snapshot WebRTC: libXdamage.so.1
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := Xdamage
LOCAL_SRC_FILES := xdamage_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libXdamage.so.1
include $(BUILD_SHARED_LIBRARY)


# Desktop Linux compatibility shim for Minecraft Snapshot WebRTC: libXrender.so.1
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := Xrender
LOCAL_SRC_FILES := xrender_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libXrender.so.1
include $(BUILD_SHARED_LIBRARY)


# Desktop Linux compatibility shim for Minecraft Snapshot WebRTC: libXext.so.6
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := Xext
LOCAL_SRC_FILES := xext_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libXext.so.6
include $(BUILD_SHARED_LIBRARY)


# Desktop Linux compatibility shim for Minecraft Snapshot WebRTC: libXi.so.6
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := Xi
LOCAL_SRC_FILES := xi_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libXi.so.6
include $(BUILD_SHARED_LIBRARY)


# Desktop Linux compatibility shim for Minecraft Snapshot WebRTC: libXtst.so.6
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := Xtst
LOCAL_SRC_FILES := xtst_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libXtst.so.6
include $(BUILD_SHARED_LIBRARY)


# Desktop Linux compatibility shim for Minecraft Snapshot WebRTC: libXcursor.so.1
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := Xcursor
LOCAL_SRC_FILES := xcursor_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libXcursor.so.1
include $(BUILD_SHARED_LIBRARY)


# Desktop Linux compatibility shim for Minecraft Snapshot WebRTC: libXinerama.so.1
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := Xinerama
LOCAL_SRC_FILES := xinerama_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libXinerama.so.1
include $(BUILD_SHARED_LIBRARY)


# Desktop Linux compatibility shim for Minecraft Snapshot WebRTC: libXss.so.1
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := Xss
LOCAL_SRC_FILES := xss_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libXss.so.1
include $(BUILD_SHARED_LIBRARY)


# Desktop Linux compatibility shim for Minecraft Snapshot WebRTC: libxcb.so.1
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := xcb
LOCAL_SRC_FILES := xcb_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libxcb.so.1
include $(BUILD_SHARED_LIBRARY)


# Desktop Linux compatibility shim for Minecraft Snapshot WebRTC: libXau.so.6
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := Xau
LOCAL_SRC_FILES := xau_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libXau.so.6
include $(BUILD_SHARED_LIBRARY)


# Desktop Linux compatibility shim for Minecraft Snapshot WebRTC: libXdmcp.so.6
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := Xdmcp
LOCAL_SRC_FILES := xdmcp_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libXdmcp.so.6
include $(BUILD_SHARED_LIBRARY)


# App-local libdrm compatibility layer for DroidBridge/Mojo Mesa.
#
# Do not use the old tiny WebRTC drm_stub.c here. Mesa's libEGL_mesa.so and
# libgallium_dri.so have DT_NEEDED libdrm.so and require symbols including
# drmGetDevice2, drmGetDevices2, drmCommandWriteRead, and syncobj helpers at
# dlopen time. This compatibility layer exports those symbols so Mesa can load
# inside the app namespace.
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := drm
LOCAL_SRC_FILES := droidbridge_libdrm_compat.c
LOCAL_LDLIBS := -ldl -llog
LOCAL_LDFLAGS += -Wl,-soname,libdrm.so
include $(BUILD_SHARED_LIBRARY)


# Desktop Linux compatibility shim for Minecraft Snapshot WebRTC: libgbm.so.1
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := gbm
LOCAL_SRC_FILES := gbm_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libgbm.so.1
include $(BUILD_SHARED_LIBRARY)


# DBus shim for Minecraft Snapshot 7+ / dev.onvoid.webrtc desktop Linux native.
# Android does not ship libdbus-1.so.3, but WebRTC's linux-aarch64 native asks
# for it after the X11-side shim dependencies are satisfied.
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := dbus-1
LOCAL_SRC_FILES := dbus_stub.c
LOCAL_LDFLAGS += -Wl,-soname,libdbus-1.so.3
include $(BUILD_SHARED_LIBRARY)


# awt_headless cleanup removed: the old rm shell call breaks Windows ndk-build hosts.



# App-local libcutils compatibility shim for Mesa builds that retain
# DT_NEEDED libcutils.so. Android app namespaces cannot link to the private
# platform libcutils.so, so DroidBridge packages a tiny compatible subset.
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := cutils
LOCAL_SRC_FILES := droidbridge_libcutils_compat.cpp
LOCAL_LDLIBS := -llog -ldl
LOCAL_LDFLAGS += -Wl,-soname,libcutils.so
include $(BUILD_SHARED_LIBRARY)


# App-local libhardware compatibility shim for Mesa builds that retain
# DT_NEEDED libhardware.so. Android app namespaces cannot link to the private
# platform libhardware.so, so DroidBridge packages a tiny delegate/stub.
LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := hardware
LOCAL_SRC_FILES := droidbridge_libhardware_compat.cpp
LOCAL_LDLIBS := -ldl -llog
LOCAL_LDFLAGS += -Wl,-soname,libhardware.so
include $(BUILD_SHARED_LIBRARY)

