APP_ABI := armeabi-v7a //arm64-v8a x86
APP_PLATFORM := android-21
#APP_MODULES := YGOMobile
#NDK_TOOLCHAIN_VERSION=4.8
APP_ALLOW_MISSING_DEPS=true
#APP_STL := gnustl_static
APP_STL := c++_static
APP_CPPFLAGS := -Wno-error=format-security -std=gnu++14 -fpermissive -D__cplusplus=201402L
APP_OPTIM := release
# 防止路径过长
APP_SHORT_COMMANDS := true
