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
        jmethodID java_getSetting;
        jmethodID java_getIntSetting;
        jmethodID java_saveIntSetting;
        jmethodID java_runWindbot;
        jmethodID java_saveSetting;
        jmethodID java_getLocalAddr;

        ANDROID_APP app;
    public:
        AndroidGameHost(ANDROID_APP  app);

        //! destructor
        virtual ~AndroidGameHost();

        void initMethods(JNIEnv *env);

        irr::io::path getLastCategory(JNIEnv *env);

        irr::io::path getLastDeck(JNIEnv *env);

        irr::io::path getSetting(JNIEnv *env, const char *key);

        int getIntSetting(JNIEnv *env, const char *key, int defvalue);

        void saveIntSetting(JNIEnv *env, const char *key, int value);

        void runWindbot(JNIEnv *env, const char *cmd);

        void saveSetting(JNIEnv *env, const char *_key, const char *_value);

        void setLastDeck(JNIEnv *env, const char *deckname);

        void setLastCategory(JNIEnv *env, const char *catename);

        int getLocalAddr(JNIEnv *env);

    };
}
#endif //YGOMOBILE_CN_KO_EN_ANDROIDGAMEHOST_H
