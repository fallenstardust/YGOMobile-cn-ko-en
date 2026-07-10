# File: Android.mk
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := lua5.4
LOCAL_SRC_FILES := lapi.c \
                   lauxlib.c \
                   lbaselib.c \
                   lbitlib.c \
                   lcode.c \
                   lcorolib.c \
                   lctype.c \
                   ldblib.c \
                   ldebug.c \
                   ldo.c \
                   ldump.c \
                   lfunc.c \
                   lgc.c \
                   linit.c \
                   liolib.c \
                   llex.c \
                   lmathlib.c \
                   lmem.c \
                   loadlib.c \
                   lobject.c \
                   lopcodes.c \
                   loslib.c \
                   lparser.c \
                   lstate.c \
                   lstring.c \
                   lstrlib.c \
                   ltable.c \
                   ltablib.c \
                   ltm.c \
                   lua.c \
                   luac.c \
                   lundump.c \
                   lutf8lib.c \
                   lvm.c \
                   lzio.c
LOCAL_CFLAGS    := -DLUA_USE_POSIX -O2 -Wall -DLUA_COMPAT_5_2 -D"getlocaledecpoint()='.'" -Wno-psabi -fexceptions -Dl_fseek=fseek -Dl_ftell=ftell -Dl_seeknum=long  # 适配NDK29: -DLUA_USE_POSIX 会让 liolib.c 使用 fseeko/ftello, 但二者在 android-21 不可用(API24+ 才有), 这里预定义宏强制走 ISO C 的 fseek/ftell
#LOCAL_CPP_EXTENSION := .c
include $(BUILD_STATIC_LIBRARY)

