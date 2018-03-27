#define __GAME_CONFIG
#include <jni.h>
#include "../Classes/gframe/config.h"

extern "C" {
JNIEXPORT jint JNICALL Java_cn_garymb_ygomobile_core_GameConfig_getGameVersion(
		JNIEnv* env, jclass clazz) {
	return (jint)DEF_PRO_VERSION;
}
}
