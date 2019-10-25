//
// Created by kenan on 19-10-18.
//

#include "AndroidGameUI.h"

using namespace ygo;

AndroidGameUI::AndroidGameUI(ANDROID_APP _app) {
    app = _app;
}

AndroidGameUI::~AndroidGameUI() {
    app = NULL;
    //JNIEnv *env = irr::android::getJniEnv(app);
    //env->DeleteGlobalRef(app->activity->clazz);
}

void AndroidGameUI::initMethods(JNIEnv *env) {
    jclass clazz = env->GetObjectClass(app->activity->clazz);
    java_toggleIME = env->GetMethodID(clazz, "toggleIME", "(ZLjava/lang/String;)V");
    java_performHapticFeedback = env->GetMethodID(clazz, "performHapticFeedback", "()V");
    java_showComboBoxCompat = env->GetMethodID(clazz, "showComboBoxCompat",
                                               "([Ljava/lang/String;ZI)V");
    java_getWindowLeft = env->GetMethodID(clazz, "getWindowLeft", "()I");
    java_getWindowTop = env->GetMethodID(clazz, "getWindowTop", "()I");
    java_getWindowWidth = env->GetMethodID(clazz, "getWindowWidth", "()I");
    java_getWindowHeight = env->GetMethodID(clazz, "getWindowHeight", "()I");
    java_getInitOptions = env->GetMethodID(clazz, "getInitOptions", "()Ljava/nio/ByteBuffer;");
    java_attachNativeDevice = env->GetMethodID(clazz, "attachNativeDevice", "(I)V");
    java_getJoinOptions = env->GetMethodID(clazz, "getJoinOptions", "()Ljava/nio/ByteBuffer;");
    java_playSoundEffect = env->GetMethodID(clazz, "playSoundEffect", "(Ljava/lang/String;)V");
    java_onReportProblem = env->GetMethodID(clazz, "onReportProblem", "()V");
    env->DeleteLocalRef(clazz);
}

void AndroidGameUI::toggleIME(JNIEnv *env, bool show, const char *_msg) {
    if (!java_toggleIME) {
        return;
    }
    jstring msg = env->NewStringUTF(_msg);
    env->CallVoidMethod(app->activity->clazz, java_toggleIME, (jboolean) show, msg);
    if (msg) {
        env->DeleteLocalRef(msg);
    }
}

void AndroidGameUI::performHapticFeedback(JNIEnv *env) {
    if (!java_performHapticFeedback) {
        return;
    }
    env->CallVoidMethod(app->activity->clazz, java_performHapticFeedback);
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
    env->CallVoidMethod(app->activity->clazz, java_showComboBoxCompat, array, (jboolean) pShow,
                        (jint) mode);

}

int AndroidGameUI::getWindowWidth(JNIEnv *env) {
    if (!java_getWindowWidth) {
        return 0;
    }
    return env->CallIntMethod(app->activity->clazz, java_getWindowWidth);
}

int AndroidGameUI::getWindowHeight(JNIEnv *env) {
    if (!java_getWindowHeight) {
        return 0;
    }
    return env->CallIntMethod(app->activity->clazz, java_getWindowHeight);
}

irr::android::InitOptions* AndroidGameUI::getInitOptions(JNIEnv *env) {
    if (!java_getInitOptions) {
        return nullptr;
    }
    jobject buffer = env->CallObjectMethod(app->activity->clazz, java_getInitOptions);
    if(buffer) {
        void *data = env->GetDirectBufferAddress(buffer);
        return new irr::android::InitOptions(data);
    }
    LOGE("getInitOptions == null");
    return NULL;
}

irr::android::YGOGameOptions* AndroidGameUI::getJoinOptions(JNIEnv *env) {
    if (!java_getJoinOptions) {
        return nullptr;
    }
    jobject buffer = env->CallObjectMethod(app->activity->clazz, java_getJoinOptions);
    if(buffer) {
        void *data = env->GetDirectBufferAddress(buffer);
        return new irr::android::YGOGameOptions(data);
    }
    LOGE("getJoinOptions == null");
    return NULL;
}

void AndroidGameUI::attachNativeDevice(JNIEnv*env, void* device){
    if (!java_attachNativeDevice) {
        LOGW("not found attachNativeDevice");
        return;
    }
    jint value = (int)device;
    LOGI("attachNativeDevice %d", value);
    env->CallVoidMethod(app->activity->clazz, java_attachNativeDevice, value);
}

int AndroidGameUI::getWindowLeft(JNIEnv *env) {
    if (!java_getWindowLeft) {
        return 0;
    }
    return env->CallIntMethod(app->activity->clazz, java_getWindowLeft);
}

int AndroidGameUI::getWindowTop(JNIEnv *env) {
    if (!java_getWindowTop) {
        return 0;
    }
    return env->CallIntMethod(app->activity->clazz, java_getWindowTop);
}

void AndroidGameUI::playSoundEffect(JNIEnv *env, const char *_name) {
    if (!java_playSoundEffect) {
        return;
    }
    jstring name = env->NewStringUTF(_name);
    env->CallVoidMethod(app->activity->clazz, java_playSoundEffect, name);
    if (name) {
        env->DeleteLocalRef(name);
    }
}

void AndroidGameUI::onReportProblem(JNIEnv *env) {
    if (!java_onReportProblem) {
        return;
    }
    env->CallVoidMethod(app->activity->clazz, java_onReportProblem);
}
