/*
 * SoundPoolWrapperTracker.h
 *
 *  Created on: 2014-10-2
 *      Author: mabin
 */

#ifndef SOUNDPOOLWRAPPERTRACKER_H_
#define SOUNDPOOLWRAPPERTRACKER_H_

#include "../../Classes/gframe/IAudioTracker.h"
#include <android_native_app_glue.h>
#include "../../Classes/gframe/mysignal.h"
#include <list>
#include <pthread.h>

namespace ygo {

class SoundPoolWrapperTracker: public ygo::IAudioTracker {
public:
	SoundPoolWrapperTracker(ANDROID_APP app);

	virtual ~SoundPoolWrapperTracker();

	virtual void playBGM(irr::io::path path, AUDIO_PATH_TYPE type);

	virtual void playSound(irr::io::path path, AUDIO_PATH_TYPE type);

	inline const std::list<irr::io::path>* getSounds() {
		return m_sounds;
	}

	inline const ANDROID_APP getMainApp() {
		return m_mainApp;
	}

	inline const Signal* getPlaySignal() {
		return m_pPlaySignal;
	}

	inline const pthread_mutex_t getSoundLock() {
		return m_soundLock;
	}

	volatile bool m_isTerminated;

private:
	ANDROID_APP m_mainApp;
	pthread_t m_audioThread;
	Signal* m_pPlaySignal;
	pthread_mutex_t m_soundLock;
	std::list<irr::io::path> * m_sounds;
};

}

 /* namespace ygo */
#endif /* SOUNDPOOLWRAPPERTRACKER_H_ */
