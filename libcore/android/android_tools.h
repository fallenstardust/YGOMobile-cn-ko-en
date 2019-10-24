// This file is part of the "Irrlicht Engine".
// For conditions of distribution and use, see copyright notice in irrlicht.h

#ifndef __IRR_ANDROID_TOOLS_H__
#define __IRR_ANDROID_TOOLS_H__

#include <irrlicht.h>
#include <signal.h>
#include <android/log.h>
#include <jni.h>

#define LOG_TAG "ygomobile-native"
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

namespace irr {
namespace android {

class InitOptions {
public:
	InitOptions(void* data);
	inline irr::io::path getWorkDir() {
		return m_work_dir;
	}
	inline irr::io::path* getDBFiles() {
		return m_db_files;
	}
	inline irr::io::path* getArchiveFiles() {
		return m_archive_files;
	}
	inline int getDbCount() {
		return cdb_count;
	}
	inline int getArchiveCount() {
		return zip_count;
	}
	inline int getOpenglVersion() {
		return m_opengles_version;
	}
	inline int getCardQualityOp() {
		return m_card_quality;
	}
	inline bool isFontAntiAliasEnabled() {
		return m_font_aa_enabled;
	}
	inline bool isSoundEffectEnabled() {
		return m_se_enabled;
	}
	inline bool isPendulumScaleEnabled() {
		return m_ps_enabled;
	}
    inline irr::io::path getFontFile() {
        return m_font_path;
    }
    inline irr::io::path getResourceDir() {
        return m_res_path;
    }
    inline irr::io::path getImageDir() {
        return m_image_path;
    }
	~InitOptions()
    {
		if(m_db_files != NULL){
			delete[] m_db_files;
		}
		m_db_files = NULL;
		if(m_archive_files != NULL){
			delete[] m_archive_files;
		}
		m_archive_files = NULL;
    }
private:
	irr::io::path m_work_dir;
    irr::io::path* m_db_files;
    irr::io::path* m_archive_files;
    irr::io::path m_font_path;
    irr::io::path m_res_path;
    irr::io::path m_image_path;

    int m_opengles_version;
    int m_card_quality;
	int cdb_count;
	int zip_count;
    bool m_font_aa_enabled;
    bool m_se_enabled;
    bool m_ps_enabled;
};

s32 handleInput(ANDROID_APP app, AInputEvent* androidEvent);

JNIEnv *getJniEnv(ANDROID_APP app);
}
}

#endif // __IRR_ANDROID_TOOLS_H__
