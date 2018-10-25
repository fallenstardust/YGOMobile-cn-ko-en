# File: Android.mk
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := sqlite3
LOCAL_MODULE_FILENAME := libsqlite
LOCAL_SRC_FILES := sqlite3.c 
                   
LOCAL_CFLAGS    :=  -O2 -Wall 
#LOCAL_CFLAGS += -DSQLITE_THREADSAFE=1

include $(BUILD_STATIC_LIBRARY)

