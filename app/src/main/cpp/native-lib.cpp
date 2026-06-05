
#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <unistd.h>
#include <fstream>
#include <string>
#include <vector>
#include <algorithm>
#include <fcntl.h>
#include <sys/stat.h>
#include <cstdlib>
#include <sys/system_properties.h>

#define LOG_TAG "JavaLauncherNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef jint (*JNI_CreateJavaVM_t)(JavaVM **, void **, void *);

static std::string gNativeLogPath;

static void appendNativeLog(const std::string &message) {
    if (gNativeLogPath.empty()) return;
    std::ofstream out(gNativeLogPath, std::ios::app);
    if (out.is_open()) out << message << std::endl;
}

static std::string safeDlError() {
    const char *err = dlerror();
    return err ? std::string(err) : std::string("unknown");
}

static std::string dirnameOf(const std::string &path) {
    size_t pos = path.find_last_of('/');
    if (pos == std::string::npos) return "";
    return path.substr(0, pos);
}

static std::string joinPaths(const std::vector<std::string>& parts) {
    std::string out;
    for (const auto& p : parts) {
        if (p.empty()) continue;
        if (!out.empty()) out += ":";
        out += p;
    }
    return out;
}

static std::vector<std::string> splitPathList(const std::string &value) {
    std::vector<std::string> out;
    size_t start = 0;
    while (start <= value.size()) {
        size_t end = value.find(':', start);
        if (end == std::string::npos) end = value.size();
        std::string part = value.substr(start, end - start);
        if (!part.empty()) out.push_back(part);
        start = end + 1;
    }
    return out;
}

static void redirectStdoutStderrToLog(const std::string& logPath) {
    int fd = open(logPath.c_str(), O_CREAT | O_WRONLY | O_APPEND, 0644);
    if (fd >= 0) {
        dup2(fd, STDOUT_FILENO);
        dup2(fd, STDERR_FILENO);
        close(fd);
    }
}

static bool loadLibrary(const std::string &path, bool required) {
    dlerror();
    void *handle = dlopen(path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        std::string err = safeDlError();
        appendNativeLog(std::string(required ? "Required" : "Optional") + " dlopen failed: " + path + " -> " + err);
        LOGE("dlopen failed: %s -> %s", path.c_str(), err.c_str());
        return false;
    }
    appendNativeLog("Loaded: " + path);
    LOGI("Loaded: %s", path.c_str());
    return true;
}

static void preloadOptionalFromDirs(const std::vector<std::string> &dirs, const std::vector<std::string> &libNames) {
    for (const auto &dir : dirs) {
        for (const auto &name : libNames) {
            std::string full = dir + "/" + name;
            if (access(full.c_str(), F_OK) == 0) {
                loadLibrary(full, false);
            }
        }
    }
}

static std::vector<std::string> normalizeJvmArgs(JNIEnv *env, jobjectArray jvmArgsArray) {
    std::vector<std::string> result;
    jsize len = env->GetArrayLength(jvmArgsArray);
    for (jsize i = 0; i < len; i++) {
        jstring item = (jstring) env->GetObjectArrayElement(jvmArgsArray, i);
        const char *utf = env->GetStringUTFChars(item, nullptr);
        std::string arg = utf ? utf : "";
        env->ReleaseStringUTFChars(item, utf);
        env->DeleteLocalRef(item);

        if (arg == "-cp") {
            if (i + 1 < len) {
                jstring cpItem = (jstring) env->GetObjectArrayElement(jvmArgsArray, ++i);
                const char *cpUtf = env->GetStringUTFChars(cpItem, nullptr);
                std::string cpValue = cpUtf ? cpUtf : "";
                env->ReleaseStringUTFChars(cpItem, cpUtf);
                env->DeleteLocalRef(cpItem);

                result.push_back("-Djava.class.path=" + cpValue);
                appendNativeLog("Converted -cp to -Djava.class.path");
            }
            continue;
        }

        result.push_back(arg);
    }
    return result;
}

static std::vector<std::string> collectGameArgs(JNIEnv *env, jobjectArray gameArgsArray) {
    std::vector<std::string> result;
    jsize len = env->GetArrayLength(gameArgsArray);
    for (jsize i = 0; i < len; i++) {
        jstring item = (jstring) env->GetObjectArrayElement(gameArgsArray, i);
        const char *utf = env->GetStringUTFChars(item, nullptr);
        std::string arg = utf ? utf : "";
        env->ReleaseStringUTFChars(item, utf);
        env->DeleteLocalRef(item);
        result.push_back(arg);
    }
    return result;
}

static std::string findArgValue(const std::vector<std::string> &args, const std::string &prefix) {
    for (const auto &arg : args) {
        if (arg.rfind(prefix, 0) == 0) {
            return arg.substr(prefix.size());
        }
    }
    return "";
}

extern "C"
JNIEXPORT jint JNICALL
Java_ca_dnamobile_javalauncher_launcher_NativeLauncherBridge_nativeLaunchJvm(
        JNIEnv *env,
        jobject /* thiz */,
        jstring javaBinaryPath_,
        jstring mainClass_,
        jstring workingDirectory_,
        jobjectArray jvmArgsArray,
        jobjectArray gameArgsArray) {

    const char *javaBinaryPath = env->GetStringUTFChars(javaBinaryPath_, nullptr);
    const char *mainClass = env->GetStringUTFChars(mainClass_, nullptr);
    const char *workingDirectory = env->GetStringUTFChars(workingDirectory_, nullptr);

    std::string javaPath = javaBinaryPath ? javaBinaryPath : "";
    std::string mainClassStr = mainClass ? mainClass : "net.minecraft.client.main.Main";
    std::string workDir = workingDirectory ? workingDirectory : "";
    std::string javaHome = dirnameOf(dirnameOf(javaPath));
    std::string libDir = javaHome + "/lib";
    std::string serverDir = javaHome + "/lib/server";
    std::string libJvmPath = serverDir + "/libjvm.so";

    gNativeLogPath = workDir + "/launcher-native.log";
    appendNativeLog("===== Native JNI launch start =====");
    appendNativeLog("javaPath=" + javaPath);
    appendNativeLog("javaHome=" + javaHome);
    appendNativeLog("workingDirectory=" + workDir);
    appendNativeLog("libJvmPath=" + libJvmPath);

    redirectStdoutStderrToLog(gNativeLogPath);

    std::vector<std::string> jvmArgs = normalizeJvmArgs(env, jvmArgsArray);
    std::vector<std::string> gameArgs = collectGameArgs(env, gameArgsArray);

    std::string nativeLibPathArg = findArgValue(jvmArgs, "-Djava.library.path=");
    std::string lwjglNativeDir = findArgValue(jvmArgs, "-Dorg.lwjgl.librarypath=");
    std::vector<std::string> nativeSearchDirs = splitPathList(nativeLibPathArg);

    std::string appNativeDir;
    if (!nativeSearchDirs.empty()) {
        appNativeDir = nativeSearchDirs.back();
    }

    appendNativeLog("java.library.path=" + nativeLibPathArg);
    appendNativeLog("appNativeDir=" + appNativeDir);
    appendNativeLog("lwjglNativeDir=" + lwjglNativeDir);

    setenv("JAVA_HOME", javaHome.c_str(), 1);
    setenv("HOME", workDir.c_str(), 1);
    setenv("TMPDIR", workDir.c_str(), 1);
    if (!appNativeDir.empty()) {
        setenv("POJAV_NATIVEDIR", appNativeDir.c_str(), 1);
    }

    // Preloads libraries properly in order
    preloadOptionalFromDirs(nativeSearchDirs, {
            "libpojavexec.so",
            "libpojavexec_awt.so",
            "libdriver_helper.so",
            "libjnidispatch.so",
            "libopenal.so",
            "libSDL3.so",
            "libSDL2.so",
            "libshaderc.so",
            "libshaderc_shared.so",
            "liblwjgl_vma.so"
    });

    if (!workDir.empty()) {
        chdir(workDir.c_str());
    }

    // Ordered JRE preload chain
    loadLibrary(libDir + "/libsleef.so", false);
    loadLibrary(libDir + "/libsyslookup.so", false);
    loadLibrary(libDir + "/libjli.so", false);
    loadLibrary(libDir + "/libjsig.so", false);
    loadLibrary(serverDir + "/libjvm.so", true);
    loadLibrary(libDir + "/libjava.so", false);
    loadLibrary(libDir + "/libverify.so", false);
    loadLibrary(libDir + "/libzip.so", false);
    loadLibrary(libDir + "/libjimage.so", false);
    loadLibrary(libDir + "/libnet.so", false);
    loadLibrary(libDir + "/libnio.so", false);
    loadLibrary(libDir + "/libprefs.so", false);
    loadLibrary(libDir + "/libextnet.so", false);
    loadLibrary(libDir + "/libsctp.so", false);
    loadLibrary(libDir + "/libmanagement.so", false);
    loadLibrary(libDir + "/libmanagement_ext.so", false);
    loadLibrary(libDir + "/libmanagement_agent.so", false);
    loadLibrary(libDir + "/librmi.so", false);
    loadLibrary(libDir + "/libinstrument.so", false);

    std::string ldLibraryPath = joinPaths({serverDir, libDir, nativeLibPathArg, lwjglNativeDir});
    setenv("LD_LIBRARY_PATH", ldLibraryPath.c_str(), 1);
    appendNativeLog("LD_LIBRARY_PATH=" + ldLibraryPath);

    dlerror();
    void *jvmHandle = dlopen(libJvmPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!jvmHandle) {
        appendNativeLog("Failed to open libjvm.so: " + safeDlError());
        env->ReleaseStringUTFChars(javaBinaryPath_, javaBinaryPath);
        env->ReleaseStringUTFChars(mainClass_, mainClass);
        env->ReleaseStringUTFChars(workingDirectory_, workingDirectory);
        return -50;
    }

    dlerror();
    auto createJavaVm = (JNI_CreateJavaVM_t) dlsym(jvmHandle, "JNI_CreateJavaVM");
    if (!createJavaVm) {
        appendNativeLog("Failed to resolve JNI_CreateJavaVM: " + safeDlError());
        env->ReleaseStringUTFChars(javaBinaryPath_, javaBinaryPath);
        env->ReleaseStringUTFChars(mainClass_, mainClass);
        env->ReleaseStringUTFChars(workingDirectory_, workingDirectory);
        return -51;
    }
    appendNativeLog("Resolved JNI_CreateJavaVM");

    std::vector<JavaVMOption> options;
    options.reserve(jvmArgs.size());
    for (const auto &arg : jvmArgs) {
        JavaVMOption opt{};
        opt.optionString = const_cast<char *>(arg.c_str());
        options.push_back(opt);
        appendNativeLog("JVM arg: " + arg);
    }

    JavaVMInitArgs vmArgs{};
    vmArgs.version = JNI_VERSION_1_6;
    vmArgs.nOptions = static_cast<jint>(options.size());
    vmArgs.options = options.data();
    vmArgs.ignoreUnrecognized = JNI_TRUE;

    JavaVM *vm = nullptr;
    JNIEnv *vmEnv = nullptr;

    appendNativeLog("Calling JNI_CreateJavaVM...");
    jint createResult = createJavaVm(&vm, reinterpret_cast<void **>(&vmEnv), &vmArgs);
    appendNativeLog("JNI_CreateJavaVM returned: " + std::to_string(createResult));

    if (createResult != JNI_OK || vmEnv == nullptr) {
        env->ReleaseStringUTFChars(javaBinaryPath_, javaBinaryPath);
        env->ReleaseStringUTFChars(mainClass_, mainClass);
        env->ReleaseStringUTFChars(workingDirectory_, workingDirectory);
        return createResult != JNI_OK ? createResult : -52;
    }

    std::replace(mainClassStr.begin(), mainClassStr.end(), '.', '/');
    jclass cls = vmEnv->FindClass(mainClassStr.c_str());
    if (!cls) {
        appendNativeLog("Could not find main class: " + mainClassStr);
        if (vmEnv->ExceptionCheck()) {
            vmEnv->ExceptionDescribe();
            vmEnv->ExceptionClear();
        }
        vm->DestroyJavaVM();
        env->ReleaseStringUTFChars(javaBinaryPath_, javaBinaryPath);
        env->ReleaseStringUTFChars(mainClass_, mainClass);
        env->ReleaseStringUTFChars(workingDirectory_, workingDirectory);
        return -53;
    }

    jmethodID mainMethod = vmEnv->GetStaticMethodID(cls, "main", "([Ljava/lang/String;)V");
    if (!mainMethod) {
        appendNativeLog("Could not find Main.main(String[])");
        if (vmEnv->ExceptionCheck()) {
            vmEnv->ExceptionDescribe();
            vmEnv->ExceptionClear();
        }
        vm->DestroyJavaVM();
        env->ReleaseStringUTFChars(javaBinaryPath_, javaBinaryPath);
        env->ReleaseStringUTFChars(mainClass_, mainClass);
        env->ReleaseStringUTFChars(workingDirectory_, workingDirectory);
        return -54;
    }

    jclass stringClass = vmEnv->FindClass("java/lang/String");
    jobjectArray mainArgs = vmEnv->NewObjectArray(static_cast<jsize>(gameArgs.size()), stringClass, nullptr);
    for (jsize i = 0; i < static_cast<jsize>(gameArgs.size()); i++) {
        jstring s = vmEnv->NewStringUTF(gameArgs[i].c_str());
        vmEnv->SetObjectArrayElement(mainArgs, i, s);
        vmEnv->DeleteLocalRef(s);
        appendNativeLog("Game arg: " + gameArgs[i]);
    }

    appendNativeLog("Calling Minecraft Main.main...");
    vmEnv->CallStaticVoidMethod(cls, mainMethod, mainArgs);

    if (vmEnv->ExceptionCheck()) {
        appendNativeLog("Java exception thrown from Main.main");
        vmEnv->ExceptionDescribe();
        vmEnv->ExceptionClear();
        vm->DestroyJavaVM();
        env->ReleaseStringUTFChars(javaBinaryPath_, javaBinaryPath);
        env->ReleaseStringUTFChars(mainClass_, mainClass);
        env->ReleaseStringUTFChars(workingDirectory_, workingDirectory);
        return -55;
    }

    appendNativeLog("Minecraft Main.main returned");
    vm->DestroyJavaVM();
    appendNativeLog("===== Native JNI launch end =====");

    env->ReleaseStringUTFChars(javaBinaryPath_, javaBinaryPath);
    env->ReleaseStringUTFChars(mainClass_, mainClass);
    env->ReleaseStringUTFChars(workingDirectory_, workingDirectory);
    return 0;
}
