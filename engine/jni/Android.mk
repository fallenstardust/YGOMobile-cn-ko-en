# File: Android.mk  (薄 JNI 决斗引擎 libygoengine.so)
# 仅链接 ocgcore + lua + sqlite3，不引入 irrlicht/渲染/音频/网络栈；
# 录像 LZMA 压缩在 Java 侧（org.tukaani）完成，故本 .so 无需 lzma/spmemvfs。
LOCAL_PATH := $(call my-dir)
CLASSES_PATH := $(LOCAL_PATH)/../../Classes

include $(CLEAR_VARS)

LOCAL_MODULE := ygoengine

LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384

# 与 ocgcore 保持一致的 C++ 标准与 RTTI（interpreter 依赖 std::type_info）
LOCAL_CFLAGS := -D_IRR_ANDROID_PLATFORM_ -pipe -fexceptions -fPIC -DLUA_COMPAT_5_2
LOCAL_CPPFLAGS := -frtti -std=gnu++14

ifndef NDEBUG
LOCAL_CFLAGS += -g -D_DEBUG
else
LOCAL_CFLAGS += -O3
endif

ifeq ($(TARGET_ARCH_ABI),x86)
LOCAL_CFLAGS += -fno-stack-protector
endif

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_CFLAGS += -mno-unaligned-access
endif

LOCAL_C_INCLUDES += $(CLASSES_PATH)/ocgcore
LOCAL_C_INCLUDES += $(CLASSES_PATH)/sqlite3

LOCAL_SRC_FILES := ygo_engine_jni.cpp

LOCAL_LDLIBS := -llog

LOCAL_STATIC_LIBRARIES := libocgcore_static liblua5.4 sqlite3

include $(BUILD_SHARED_LIBRARY)

$(call import-add-path,$(CLASSES_PATH))
$(call import-module,ocgcore)
$(call import-module,lua)
$(call import-module,sqlite3)
