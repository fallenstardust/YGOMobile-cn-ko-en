# File: Android.mk
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := cspmemvfs
LOCAL_SRC_FILES := spmemvfs.c
                   
LOCAL_CFLAGS    := -O2
include $(BUILD_STATIC_LIBRARY)

