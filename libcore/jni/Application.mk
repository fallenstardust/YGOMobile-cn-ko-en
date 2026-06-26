APP_ABI := armeabi-v7a arm64-v8a x86
APP_PLATFORM := android-21
#APP_MODULES := YGOMobile
#NDK_TOOLCHAIN_VERSION=4.8
#APP_ALLOW_MISSING_DEPS=true
#APP_STL := gnustl_static
APP_STL := c++_static
APP_CPPFLAGS := -Wno-error=format-security -std=gnu++14 -fpermissive  # 适配NDK29: 移除了原 -D__cplusplus=201402L, 该宏强制覆盖会与 -std=gnu++14 冲突, 导致部分 constexpr 函数报错
APP_CFLAGS := -fcommon  # 适配NDK29: r29 默认 -fno-common(C99), 导致 openal 等老代码头文件内定义的全局变量在多个 .o 中重复定义, 此处恢复 NDK21 的 -fcommon 行为
APP_OPTIM := release
# 防止路径过长
APP_SHORT_COMMANDS := true
