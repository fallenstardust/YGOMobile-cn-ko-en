# File: Android.mk
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := ocgcore_static
LOCAL_MODULE_FILENAME := libocgcore
LOCAL_SRC_FILES := card.cpp \
                   duel.cpp \
                   effect.cpp \
                   field.cpp \
                   group.cpp \
                   interpreter.cpp \
                   libcard.cpp \
                   libdebug.cpp \
                   libduel.cpp \
                   libeffect.cpp \
                   libgroup.cpp \
                   mem.cpp \
                   ocgapi.cpp \
                   operations.cpp \
                   playerop.cpp \
                   processor.cpp \
                   scriptlib.cpp
                   
LOCAL_CFLAGS    := -frtti -std=gnu++14 -D_IRR_ANDROID_PLATFORM_  # 适配NDK29: 原为 -std=gnu++0x(C++11草案), 统一到 C++14, 与 Application.mk 的 -std=gnu++14 保持一致
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua

include $(BUILD_STATIC_LIBRARY)

