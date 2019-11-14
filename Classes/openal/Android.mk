LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE     := openal

LOCAL_C_INCLUDES := $(LOCAL_PATH)/android				\
					$(LOCAL_PATH)/include				\
					$(LOCAL_PATH)/src/Alc				\
					$(LOCAL_PATH)/src/OpenAL32			\
					$(LOCAL_PATH)/src/OpenAL32/Include

LOCAL_SRC_FILES  := \
    src/Alc/ALc.c \
    src/Alc/alcConfig.c \
    src/Alc/alcRing.c \
    src/Alc/ALu.c \
    src/Alc/ambdec.c \
    src/Alc/bformatdec.c \
    src/Alc/bs2b.c \
    src/Alc/bsinc.c \
    src/Alc/helpers.c \
    src/Alc/hrtf.c \
    src/Alc/mixer.c \
    src/Alc/mixer_c.c \
    src/Alc/panning.c \
    src/Alc/uhjfilter.c \
    \
    src/Alc/effects/autowah.c \
    src/Alc/effects/chorus.c \
    src/Alc/effects/compressor.c \
    src/Alc/effects/dedicated.c \
    src/Alc/effects/distortion.c \
    src/Alc/effects/echo.c \
    src/Alc/effects/equalizer.c \
    src/Alc/effects/flanger.c \
    src/Alc/effects/modulator.c \
    src/Alc/effects/null.c \
    src/Alc/effects/reverb.c \
    \
    src/Alc/backends/base.c \
    src/Alc/backends/loopback.c \
    src/Alc/backends/null.c \
    src/Alc/backends/opensl.c \
    src/Alc/backends/wave.c \
    \
    src/OpenAL32/alAuxEffectSlot.c \
    src/OpenAL32/alBuffer.c \
    src/OpenAL32/alEffect.c \
    src/OpenAL32/alError.c \
    src/OpenAL32/alExtension.c \
    src/OpenAL32/alFilter.c \
    src/OpenAL32/alListener.c \
    src/OpenAL32/alSource.c \
    src/OpenAL32/alState.c \
    src/OpenAL32/alThunk.c \
    src/OpenAL32/sample_cvt.c \
    \
    src/common/almalloc.c \
    src/common/alhelpers.c \
    src/common/atomic.c \
    src/common/rwlock.c \
    src/common/threads.c \
    src/common/uintmap.c

LOCAL_CFLAGS     := -DAL_BUILD_LIBRARY -DAL_ALEXT_PROTOTYPES

include $(BUILD_STATIC_LIBRARY)
