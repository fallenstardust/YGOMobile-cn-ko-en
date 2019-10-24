//
// Created by user on 2019/10/12.
//

#include "AndroidGameHost.h"
#include "android_tools.h"

#define CONF_LAST_DECK "lastdeck"
#define CONF_LAST_CATEGORY "lastcategory"

using namespace ygo;

AndroidGameHost::AndroidGameHost(ANDROID_APP _app) {
    app = _app;
}

AndroidGameHost::~AndroidGameHost() {
    app = NULL;
//    JNIEnv *env = irr::android::getJniEnv(app);
//    env->DeleteGlobalRef(app->activity->clazz);
}

void AndroidGameHost::initMethods(JNIEnv *env) {
    jclass clazz = env->GetObjectClass(app->activity->clazz);
    java_getSetting = env->GetMethodID(clazz, "getSetting",
                                       "(Ljava/lang/String;)Ljava/lang/String;");
    java_getIntSetting = env->GetMethodID(clazz, "getIntSetting", "(Ljava/lang/String;I)I");
    java_saveIntSetting = env->GetMethodID(clazz, "saveIntSetting", "(Ljava/lang/String;I)V");
    java_saveSetting = env->GetMethodID(clazz, "saveSetting", "(Ljava/lang/String;Ljava/lang/String;)V");
    java_runWindbot = env->GetMethodID(clazz, "runWindbot", "(Ljava/lang/String;)V");
    java_getLocalAddr = env->GetMethodID(clazz, "getLocalAddr", "()I");
    env->DeleteLocalRef(clazz);
}

irr::io::path AndroidGameHost::getSetting(JNIEnv *env, const char *_key) {
    irr::io::path ret;
    if (!java_getSetting) {
        return ret;
    }
    jstring key = env->NewStringUTF(_key);
    jstring value = (jstring) env->CallObjectMethod(app->activity->clazz, java_getSetting, key);
    if (key) {
        env->DeleteLocalRef(key);
    }
    if(value != NULL) {
        const char *chars = env->GetStringUTFChars(value, NULL);
        ret.append(chars);
        env->ReleaseStringUTFChars(value, chars);
    }
    return ret;
}

int AndroidGameHost::getIntSetting(JNIEnv *env, const char *_key, int defvalue) {
    if (!java_getIntSetting) {
        return 0;
    }
    jstring key = env->NewStringUTF(_key);
    jint value = env->CallIntMethod(app->activity->clazz, java_getIntSetting, key, defvalue);
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
    env->CallVoidMethod(app->activity->clazz, java_saveIntSetting, key, value);
    if (key) {
        env->DeleteLocalRef(key);
    }
}

void AndroidGameHost::runWindbot(JNIEnv *env, const char* _cmd) {
    if (!java_runWindbot) {
        return;
    }
    jstring cmd = env->NewStringUTF(_cmd);
    env->CallVoidMethod(app->activity->clazz, java_runWindbot, cmd);
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
    env->CallVoidMethod(app->activity->clazz, java_saveSetting, key, value);
    if (key) {
        env->DeleteLocalRef(key);
    }
    if (value) {
        env->DeleteLocalRef(value);
    }
}

void AndroidGameHost::setLastDeck(JNIEnv *env, const char* deckname) {
    saveSetting(env, CONF_LAST_DECK, deckname);
}

void AndroidGameHost::setLastCategory(JNIEnv *env, const char* catename) {
    saveSetting(env, CONF_LAST_CATEGORY, catename);
}

irr::io::path AndroidGameHost::getLastDeck(JNIEnv *env) {
    return getSetting(env, CONF_LAST_DECK);
}

irr::io::path AndroidGameHost::getLastCategory(JNIEnv *env) {
    return getSetting(env, CONF_LAST_CATEGORY);
}

int AndroidGameHost::getLocalAddr(JNIEnv *env) {
    if (!java_getLocalAddr) {
        return 0;
    }
    return env->CallIntMethod(app->activity->clazz, java_getLocalAddr);
}

