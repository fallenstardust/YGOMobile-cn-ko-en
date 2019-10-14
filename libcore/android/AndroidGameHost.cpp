//
// Created by user on 2019/10/12.
//

#include "AndroidGameHost.h"
#include "android_tools.h"

using namespace ygo;

AndroidGameHost::AndroidGameHost(ANDROID_APP _app, jobject _host) {
    host = _host;
    app = _app;
}

AndroidGameHost::~AndroidGameHost() {
    JNIEnv *env = irr::android::getJniEnv(app);
    env->DeleteGlobalRef(host);
}

void AndroidGameHost::initMethods(JNIEnv *env) {
    jclass clazz = env->GetObjectClass(host);
    java_getSetting = env->GetMethodID(clazz, "getSetting",
                                       "(Ljava/lang/String;)Ljava/lang/String;");
    java_getFontPath = env->GetMethodID(clazz, "getFontPath", "()Ljava/lang/String;");
    java_getIntSetting = env->GetMethodID(clazz, "getIntSetting", "(Ljava/lang/String;I)I");
    java_saveIntSetting = env->GetMethodID(clazz, "saveIntSetting", "(Ljava/lang/String;I)V");
    java_getResourcePath = env->GetMethodID(clazz, "getResourcePath", "()Ljava/lang/String;");
    java_getCardImagePath = env->GetMethodID(clazz, "getCardImagePath", "()Ljava/lang/String;");
    java_runWindbot = env->GetMethodID(clazz, "runWindbot", "(Ljava/lang/String;)V");
    java_saveSetting = env->GetMethodID(clazz, "saveSetting", "(Ljava/lang/String;Ljava/lang/String;)V");
    java_getLocalAddr = env->GetMethodID(clazz, "getLocalAddr", "()I");
    java_toggleIME = env->GetMethodID(clazz, "toggleIME", "(ZLjava/lang/String;)V");
    java_performHapticFeedback = env->GetMethodID(clazz, "performHapticFeedback", "()V");
    java_showComboBoxCompat = env->GetMethodID(clazz, "showComboBoxCompat", "([Ljava/lang/String;ZI)V");
    java_playSoundEffect = env->GetMethodID(clazz, "playSoundEffect", "(Ljava/lang/String;)V");
    java_getInitOptions = env->GetMethodID(clazz, "getInitOptions", "()Ljava/nio/ByteBuffer;");
    java_getWindowWidth = env->GetMethodID(clazz, "getWindowWidth", "()I");
    java_getWindowHeight = env->GetMethodID(clazz, "getWindowHeight", "()I");
    java_attachNativeDevice = env->GetMethodID(clazz, "attachNativeDevice", "(I)V");
    env->DeleteLocalRef(clazz);
}
irr::io::path AndroidGameHost::getResourcePath(JNIEnv *env) {
    irr::io::path ret;
    if (!java_getResourcePath) {
        return ret;
    }
    jstring value = (jstring) env->CallObjectMethod(host, java_getResourcePath);
    const char *chars = env->GetStringUTFChars(value, NULL);
    ret.append(chars);
    env->ReleaseStringUTFChars(value, chars);
    return ret;
}

irr::io::path AndroidGameHost::getCardImagePath(JNIEnv *env) {
    irr::io::path ret;
    if (!java_getCardImagePath) {
        return ret;
    }
    jstring value = (jstring) env->CallObjectMethod(host, java_getCardImagePath);
    const char *chars = env->GetStringUTFChars(value, NULL);
    ret.append(chars);
    env->ReleaseStringUTFChars(value, chars);
    return ret;
}

irr::io::path AndroidGameHost::getLastDeck(JNIEnv *env) {
    return getSetting(env, "lastdeck");
}

irr::io::path AndroidGameHost::getLastCategory(JNIEnv *env) {
    return getSetting(env, "lastcategory");
}

irr::io::path AndroidGameHost::getSetting(JNIEnv *env, const char *_key) {
    irr::io::path ret;
    if (!java_getSetting) {
        return ret;
    }
    jstring key = env->NewStringUTF(_key);
    jstring value = (jstring) env->CallObjectMethod(host, java_getSetting, key);
    if (key) {
        env->DeleteLocalRef(key);
    }
    const char *chars = env->GetStringUTFChars(value, NULL);
    ret.append(chars);
    env->ReleaseStringUTFChars(value, chars);
    return ret;
}

irr::io::path AndroidGameHost::getFontPath(JNIEnv *env) {
    irr::io::path ret;
    if (!java_getFontPath) {
        return ret;
    }
    jstring value = (jstring) env->CallObjectMethod(host, java_getFontPath);
    const char *chars = env->GetStringUTFChars(value, NULL);
    LOGD("getFontPath:%s", chars);
    ret.append(chars);
    env->ReleaseStringUTFChars(value, chars);
    return ret;
}

int AndroidGameHost::getIntSetting(JNIEnv *env, const char *_key, int defvalue) {
    if (!java_getIntSetting) {
        return 0;
    }
    jstring key = env->NewStringUTF(_key);
    jint value = env->CallIntMethod(host, java_getIntSetting, key, defvalue);
    if (key) {
        env->DeleteLocalRef(key);
    }
    return (int)value;
}

void AndroidGameHost::saveIntSetting(JNIEnv *env, const char *_key, int value) {
    if (!java_saveIntSetting) {
        return;
    }
    jstring key = env->NewStringUTF(_key);
    env->CallVoidMethod(host, java_saveIntSetting, key, value);
    if (key) {
        env->DeleteLocalRef(key);
    }
}

void AndroidGameHost::runWindbot(JNIEnv *env, const char* _cmd) {
    if (!java_runWindbot) {
        return;
    }
    jstring cmd = env->NewStringUTF(_cmd);
    env->CallVoidMethod(host, java_runWindbot, cmd);
    if (cmd) {
        env->DeleteLocalRef(cmd);
    }
}

void AndroidGameHost::saveSetting(JNIEnv *env, const char *_key, const char *_value) {
    if (!java_saveSetting) {
        return;
    }
    jstring key = env->NewStringUTF(_key);
    jstring value = env->NewStringUTF(_value);
    env->CallVoidMethod(host, java_saveSetting, key, value);
    if (key) {
        env->DeleteLocalRef(key);
    }
    if (value) {
        env->DeleteLocalRef(value);
    }
}

void AndroidGameHost::setLastDeck(JNIEnv *env, const char* deckname) {
    saveSetting(env, "lastdeck", deckname);
}

void AndroidGameHost::setLastCategory(JNIEnv *env, const char* catename) {
    saveSetting(env, "lastcategory", catename);
}

int AndroidGameHost::getLocalAddr(JNIEnv *env) {
    if (!java_getLocalAddr) {
        return 0;
    }
    return env->CallIntMethod(host, java_getLocalAddr);
}

void AndroidGameHost::toggleIME(JNIEnv *env, bool show, const char *_msg) {
    if (!java_toggleIME) {
        return;
    }
    jstring msg = env->NewStringUTF(_msg);
    env->CallVoidMethod(host, java_toggleIME, (jboolean)show, msg);
    if (msg) {
        env->DeleteLocalRef(msg);
    }
}

void AndroidGameHost::performHapticFeedback(JNIEnv *env) {
    if(!java_performHapticFeedback){
        return;
    }
    env->CallVoidMethod(host, java_performHapticFeedback);
}

void
AndroidGameHost::showAndroidComboBoxCompat(JNIEnv *env, bool pShow, char **pContents, int count,
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

void AndroidGameHost::playSoundEffect(JNIEnv *env, const char *_name) {
    if (!java_playSoundEffect) {
        return;
    }
    jstring name = env->NewStringUTF(_name);
    env->CallVoidMethod(host, java_playSoundEffect, name);
    if (name) {
        env->DeleteLocalRef(name);
    }
}

irr::android::InitOptions* AndroidGameHost::getInitOptions(JNIEnv *env) {
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

int AndroidGameHost::getWindowWidth(JNIEnv *env) {
    if (!java_getWindowWidth) {
        return 0;
    }
    return env->CallIntMethod(host, java_getWindowWidth);
}

int AndroidGameHost::getWindowHeight(JNIEnv *env) {
    if (!java_getWindowHeight) {
        return 0;
    }
    return env->CallIntMethod(host, java_getWindowHeight);
}

void AndroidGameHost::attachNativeDevice(JNIEnv*env, void* device){
    if (!java_attachNativeDevice) {
        LOGW("not found attachNativeDevice");
        return;
    }
    jint value = (int)device;
    LOGI("attachNativeDevice %d", value);
    env->CallVoidMethod(host, java_attachNativeDevice, value);
}