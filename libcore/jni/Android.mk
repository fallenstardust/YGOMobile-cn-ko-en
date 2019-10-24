LOCAL_PATH := $(call my-dir)/..
IRRLICHT_PROJECT_PATH := $(LOCAL_PATH)

#include $(CLEAR_VARS)
#LOCAL_MODULE := Irrlicht
#LOCAL_SRC_FILES := $(IRRLICHT_PROJECT_PATH)/../irrlicht/lib/Android/$(TARGET_ARCH_ABI)/libIrrlicht.a
#include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE := YGOMobile

LOCAL_CFLAGS := -D_IRR_ANDROID_PLATFORM_ -pipe -fno-rtti -fno-exceptions -fstrict-aliasing -D_ANDROID -fPIC -DLUA_COMPAT_5_2
TARGET_FORMAT_STRING_CFLAGS := 

LOCAL_CPPFLAGS := -std=c++11

ifndef NDEBUG
LOCAL_CFLAGS += -g -D_DEBUG 
else
LOCAL_CFLAGS += -fexpensive-optimizations -O3 
endif

ifeq ($(TARGET_ARCH_ABI),x86)
LOCAL_CFLAGS += -fno-stack-protector
endif

ifeq ($(TARGET_ARCH_ABI), armeabi-v7a)
LOCAL_CFLAGS += -mno-unaligned-access
endif
 
LOCAL_C_INCLUDES += $(LOCAL_PATH)
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../irrlicht/include
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../irrlicht/source/Irrlicht
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../irrlicht/source/Irrlicht/Android
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../Classes/freetype/include
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../Classes/sqlite3
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../Classes/libevent/include

LOCAL_SRC_FILES := $(LOCAL_PATH)/android/android_tools.cpp \
				$(LOCAL_PATH)/android/xstring.cpp \
				$(LOCAL_PATH)/android/TouchEventTransferAndroid.cpp \
				$(LOCAL_PATH)/android/CAndroidGUIEditBox.cpp \
				$(LOCAL_PATH)/android/CAndroidGUIComboBox.cpp \
				$(LOCAL_PATH)/android/CAndroidGUISkin.cpp \
				$(LOCAL_PATH)/android/CustomShaderConstantSetCallBack.cpp \
				$(LOCAL_PATH)/android/YGOGameOptions.cpp \
				$(LOCAL_PATH)/android/AndroidSoundEffectPlayer.cpp \
				$(LOCAL_PATH)/android/OpenSLSoundTracker.cpp \
				$(LOCAL_PATH)/android/AndroidGameHost.cpp \
				$(LOCAL_PATH)/android/AndroidGameUI.cpp \
				$(LOCAL_PATH)/android/SoundPoolWrapperTracker.cpp \
				$(LOCAL_PATH)/../Classes/gframe/CGUIEditBox.cpp \
				$(LOCAL_PATH)/../Classes/gframe/CGUIButton.cpp \
				$(LOCAL_PATH)/../Classes/gframe/CGUIComboBox.cpp \
				$(LOCAL_PATH)/../Classes/gframe/CGUIImageButton.cpp \
				$(LOCAL_PATH)/../Classes/gframe/CGUITTFont.cpp \
				$(LOCAL_PATH)/../Classes/gframe/client_card.cpp \
				$(LOCAL_PATH)/../Classes/gframe/client_field.cpp \
				$(LOCAL_PATH)/../Classes/gframe/data_manager.cpp \
				$(LOCAL_PATH)/../Classes/gframe/deck_con.cpp \
				$(LOCAL_PATH)/../Classes/gframe/deck_manager.cpp \
				$(LOCAL_PATH)/../Classes/gframe/drawing.cpp \
				$(LOCAL_PATH)/../Classes/gframe/duelclient.cpp \
				$(LOCAL_PATH)/../Classes/gframe/event_handler.cpp \
				$(LOCAL_PATH)/../Classes/gframe/game.cpp \
				$(LOCAL_PATH)/../Classes/gframe/gframe.cpp \
				$(LOCAL_PATH)/../Classes/gframe/image_manager.cpp \
				$(LOCAL_PATH)/../Classes/gframe/materials.cpp \
				$(LOCAL_PATH)/../Classes/gframe/menu_handler.cpp \
				$(LOCAL_PATH)/../Classes/gframe/netserver.cpp \
				$(LOCAL_PATH)/../Classes/gframe/replay_mode.cpp \
				$(LOCAL_PATH)/../Classes/gframe/replay.cpp \
				$(LOCAL_PATH)/../Classes/gframe/single_duel.cpp \
				$(LOCAL_PATH)/../Classes/gframe/single_mode.cpp \
				$(LOCAL_PATH)/../Classes/gframe/tag_duel.cpp \
				$(LOCAL_PATH)/jni/YGOCore.cpp \
				$(LOCAL_PATH)/jni/BPG.cpp

LOCAL_LDLIBS := -lEGL -llog -lGLESv1_CM -lGLESv2 -landroid -lOpenSLES

LOCAL_STATIC_LIBRARIES := Irrlicht
LOCAL_STATIC_LIBRARIES += android_native_app_glue
LOCAL_STATIC_LIBRARIES += libssl_static
LOCAL_STATIC_LIBRARIES += libcrypto_static
LOCAL_STATIC_LIBRARIES += libevent2
LOCAL_STATIC_LIBRARIES += libocgcore_static
LOCAL_STATIC_LIBRARIES += liblua5.3
LOCAL_STATIC_LIBRARIES += clzma
LOCAL_STATIC_LIBRARIES += cspmemvfs
LOCAL_STATIC_LIBRARIES += sqlite3
LOCAL_STATIC_LIBRARIES += libft2

include $(BUILD_SHARED_LIBRARY)
$(call import-add-path,$(LOCAL_PATH)/../Classes)
$(call import-add-path,$(LOCAL_PATH)/../irrlicht/source)
$(call import-add-path,$(LOCAL_PATH)/android)
$(call import-module,Irrlicht/Android/jni)
$(call import-module,openssl)
$(call import-module,libevent)
$(call import-module,sqlite3)
$(call import-module,ocgcore)
$(call import-module,lua)
$(call import-module,freetype)
$(call import-module,gframe/lzma)
$(call import-module,gframe/spmemvfs)

$(call import-module,android/native_app_glue)


