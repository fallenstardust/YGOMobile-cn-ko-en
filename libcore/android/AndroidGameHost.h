//
// Created by user on 2019/10/12.
//

#ifndef YGOMOBILE_CN_KO_EN_ANDROIDGAMEHOST_H
#define YGOMOBILE_CN_KO_EN_ANDROIDGAMEHOST_H

#include <irrlicht.h>
#include <jni.h>
#include "android_tools.h"

namespace ygo {
    class AndroidGameHost {
    private:
        jobject host;
        jmethodID java_getSetting;
        jmethodID java_getFontPath;
        jmethodID java_getIntSetting;
        jmethodID java_saveIntSetting;
        jmethodID java_getCardImagePath;
        jmethodID java_getResourcePath;
        jmethodID java_runWindbot;
        jmethodID java_saveSetting;
        jmethodID java_getLocalAddr;
        jmethodID java_toggleIME;
        jmethodID java_performHapticFeedback;
        jmethodID java_showComboBoxCompat;
        jmethodID java_playSoundEffect;
        jmethodID java_getInitOptions;
        jmethodID java_getWindowWidth;
        jmethodID java_getWindowHeight;
        jmethodID java_attachNativeDevice;

        ANDROID_APP app;
    public:
        AndroidGameHost(ANDROID_APP  app, jobject host);

        //! destructor
        virtual ~AndroidGameHost();

        void initMethods(JNIEnv *env);

        irr::io::path getLastCategory(JNIEnv *env);

        irr::io::path getLastDeck(JNIEnv *env);

        irr::io::path getFontPath(JNIEnv *env);

        irr::io::path getSetting(JNIEnv *env, const char *key);

        int getIntSetting(JNIEnv *env, const char *key, int defvalue);

        void saveIntSetting(JNIEnv *env, const char *key, int value);

        irr::io::path getResourcePath(JNIEnv *env);

        irr::io::path getCardImagePath(JNIEnv *env);

        void runWindbot(JNIEnv *env, const char *cmd);

        void saveSetting(JNIEnv *env, const char *_key, const char *_value);

        void setLastDeck(JNIEnv *env, const char *deckname);

        void setLastCategory(JNIEnv *env, const char *catename);

        int getLocalAddr(JNIEnv *env);

        void toggleIME(JNIEnv *env, bool show, const char *msg);

        void performHapticFeedback(JNIEnv *env);

        void showAndroidComboBoxCompat(JNIEnv *env, bool pShow,
                                       char **pContents, int count, int mode = 0);

        void playSoundEffect(JNIEnv *env, const char *name);

        irr::android::InitOptions *getInitOptions(JNIEnv *env);

        int getWindowWidth(JNIEnv *env);

        int getWindowHeight(JNIEnv *env);

        void attachNativeDevice(JNIEnv* env, void* device);
    };
}
#endif //YGOMOBILE_CN_KO_EN_ANDROIDGAMEHOST_H
