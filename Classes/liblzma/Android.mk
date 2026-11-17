# liblzma (xz-utils) 静态库，供 Classes/gframe/replay.cpp 使用 <lzma.h> / LZMA1EXT。
# 源码由 tools/fetch-liblzma.ps1 从 xz-utils 官方发布包抽取到本目录；
# Android.mk 与 config.h 随仓库提交，重新抽取源码不会覆盖它们。
LOCAL_PATH := $(call my-dir)

ifeq ($(wildcard $(LOCAL_PATH)/common/common.c),)
$(error liblzma sources are missing; run tools/fetch-liblzma.ps1 first)
endif

include $(CLEAR_VARS)

LOCAL_MODULE := liblzma_xz

# 通配收集 liblzma 源码，兼容 5.4/5.6/5.8 各版本的文件增删。
LZMA_SRC_DIRS := common check delta lz lzma rangecoder simple
LZMA_SRC := $(foreach dir,$(LZMA_SRC_DIRS),$(wildcard $(LOCAL_PATH)/$(dir)/*.c))

# 只构建单线程版本：排除线程编解码器、备用小体积 CRC 实现、
# CRC 表生成器以及本项目用不到的可选实现。
LZMA_SRC := $(filter-out \
	$(LOCAL_PATH)/common/stream_encoder_mt.c \
	$(LOCAL_PATH)/common/stream_decoder_mt.c \
	$(LOCAL_PATH)/common/mt_decoder.c \
	$(LOCAL_PATH)/common/hardware_cputhreads.c \
	$(LOCAL_PATH)/common/microlzma_encoder.c \
	$(LOCAL_PATH)/common/microlzma_decoder.c \
	$(LOCAL_PATH)/common/lzip_decoder.c \
	$(LOCAL_PATH)/check/crc32_small.c \
	$(LOCAL_PATH)/check/crc64_small.c \
	$(LOCAL_PATH)/check/crc32_tablegen.c \
	$(LOCAL_PATH)/check/crc64_tablegen.c, \
	$(LZMA_SRC))

LOCAL_SRC_FILES := $(patsubst $(LOCAL_PATH)/%,%,$(LZMA_SRC))

# liblzma 的内部头文件分散在各子目录（如 lzma_decoder.h、check.h），
# 官方 CMake 会把全部子目录加入 include 路径。
LZMA_INCLUDE_DIRS := $(foreach dir,$(LZMA_SRC_DIRS),$(LOCAL_PATH)/$(dir))

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH) \
	$(LOCAL_PATH)/api \
	$(LOCAL_PATH)/tuklib \
	$(LZMA_INCLUDE_DIRS)

# replay.cpp 通过 <lzma.h> 引用，需要 api/ 目录参与头文件搜索。
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/api

LOCAL_CFLAGS := -std=gnu99 -O2 -fPIC -fvisibility=hidden -DHAVE_CONFIG_H -DLZMA_API_STATIC

include $(BUILD_STATIC_LIBRARY)
