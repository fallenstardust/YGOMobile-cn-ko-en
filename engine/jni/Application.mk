# File: Application.mk  (薄 JNI 决斗引擎)
# 与 libcore/jni/Application.mk 对齐 STL 与全局编译开关，避免混链时 C++ 运行期不一致。
APP_ABI := armeabi-v7a arm64-v8a
APP_PLATFORM := android-21
APP_STL := c++_static
APP_CPPFLAGS := -Wno-error=format-security -std=gnu++14 -frtti
APP_CFLAGS := -fcommon
APP_OPTIM := release
APP_SHORT_COMMANDS := true
