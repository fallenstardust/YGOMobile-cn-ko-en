//
// Created by kenan on 19-10-18.
//

#include "AndroidGameUI.h"

using namespace ygo;

AndroidGameUI::AndroidGameUI(ANDROID_APP _app, jobject _host) {
    host = _host;
    app = _app;
}

AndroidGameUI::~AndroidGameUI() {
    JNIEnv *env = irr::android::getJniEnv(app);
    env->DeleteGlobalRef(host);
}

void AndroidGameUI::initMethods(JNIEnv *env) {
    jclass clazz = env->GetObjectClass(host);
    java_toggleIME = env->GetMethodID(clazz, "toggleIME", "(ZLjava/lang/String;)V");
    java_performHapticFeedback = env->GetMethodID(clazz, "performHapticFeedback", "()V");
    java_showComboBoxCompat = env->GetMethodID(clazz, "showComboBoxCompat",
                                               "([Ljava/lang/String;ZI)V");
    java_getWindowWidth = env->GetMethodID(clazz, "getWindowWidth", "()I");
    java_getWindowHeight = env->GetMethodID(clazz, "getWindowHeight", "()I");
    java_getInitOptions = env->GetMethodID(clazz, "getInitOptions", "()Ljava/nio/ByteBuffer;");
    java_attachNativeDevice = env->GetMethodID(clazz, "attachNativeDevice", "(I)V");
    java_onGameLaunch = env->GetMethodID(clazz, "onGameLaunch", "()V");
    env->DeleteLocalRef(clazz);
}

void AndroidGameUI::toggleIME(JNIEnv *env, bool show, const char *_msg) {
    if (!java_toggleIME) {
        return;
    }
    jstring msg = env->NewStringUTF(_msg);
    env->CallVoidMethod(host, java_toggleIME, (jboolean) show, msg);
    if (msg) {
        env->DeleteLocalRef(msg);
    }
}

void AndroidGameUI::performHapticFeedback(JNIEnv *env) {
    if (!java_performHapticFeedback) {
        return;
    }
    env->CallVoidMethod(host, java_performHapticFeedback);
}

void
AndroidGameUI::showAndroidComboBoxCompat(JNIEnv *env, bool pShow, char **pContents, int count,
                                         int mode) {
    if (!java_showComboBoxCompat) {
        return;
    }
    jclass strClass = env->FindClass("java/lang/String");
    jobjectArray array = env->NewObjectArray(count, strClass, 0);
    jstring str;
    for (int i = 0; i < count; i++) {
        str = env->NewStringUTF(*(pContents + i));
        env->SetObjectArrayElement(array, i, str);
    }
    env->CallVoidMethod(host, java_showComboBoxCompat, array, (jboolean) pShow,
                        (jint) mode);

}

int AndroidGameUI::getWindowWidth(JNIEnv *env) {
    if (!java_getWindowWidth) {
        return 0;
    }
    return env->CallIntMethod(host, java_getWindowWidth);
}

int AndroidGameUI::getWindowHeight(JNIEnv *env) {
    if (!java_getWindowHeight) {
        return 0;
    }
    return env->CallIntMethod(host, java_getWindowHeight);
}

irr::android::InitOptions* AndroidGameUI::getInitOptions(JNIEnv *env) {
    if (!java_getInitOptions) {
        return nullptr;
    }
    jobject buffer = env->CallObjectMethod(host, java_getInitOptions);
    if(buffer) {
        void *data = env->GetDirectBufferAddress(buffer);
        return new irr::android::InitOptions(data);
    }
    LOGE("getInitOptions == null");
    return NULL;
}

void AndroidGameUI::attachNativeDevice(JNIEnv*env, void* device){
    if (!java_attachNativeDevice) {
        LOGW("not found attachNativeDevice");
        return;
    }
    jint value = (int)device;
    LOGI("attachNativeDevice %d", value);
    env->CallVoidMethod(host, java_attachNativeDevice, value);
}

void AndroidGameUI::onGameLaunch(JNIEnv *env) {
    if (!java_onGameLaunch) {
        return;
    }
    env->CallVoidMethod(host, java_onGameLaunch);
}