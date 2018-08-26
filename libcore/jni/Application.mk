APP_ABI := armeabi-v7a
#APP_ABI := armeabi-v7a 
APP_PLATFORM := android-14
#APP_MODULES := YGOMobile
#NDK_TOOLCHAIN_VERSION=4.8
APP_ALLOW_MISSING_DEPS=true
#APP_STL := gnustl_static
APP_STL := c++_static
APP_CPPFLAGS := -Wno-error=format-security -std=gnu++11 -fpermissive -D__cplusplus=201103L
APP_OPTIM := release
