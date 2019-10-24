/*
 * SoundPoolWrapperTracker.cpp
 *
 *  Created on: 2014-10-2
 *      Author: mabin
 */

#include "SoundPoolWrapperTracker.h"
#include "../../Classes/gframe/game.h"

namespace ygo {

static void* audioPlayThread(void* param);

SoundPoolWrapperTracker::SoundPoolWrapperTracker(ANDROID_APP app) :
		m_isTerminated(false), m_sounds(NULL) {
	m_sounds = new std::list<irr::io::path>();
	m_pPlaySignal = new Signal();
	m_mainApp = app;
	pthread_mutex_init(&m_soundLock, NULL);
	//init Audio Thread;
	pthread_attr_t audioAttr;
	pthread_attr_init(&audioAttr);
	pthread_create(&m_audioThread, &audioAttr, audioPlayThread, (void *) this);
	pthread_attr_destroy(&audioAttr);
	pthread_detach(m_audioThread);
}

SoundPoolWrapperTracker::~SoundPoolWrapperTracker() {
	if (m_sounds != NULL) {
		delete m_sounds;
		m_sounds = NULL;
	}
	m_isTerminated = true;
	m_pPlaySignal->SetNoWait(true);
	m_pPlaySignal->Set();
	pthread_join(m_audioThread, NULL);
	pthread_mutex_destroy(&m_soundLock);
}

void* audioPlayThread(void* param) {
	SoundPoolWrapperTracker* spwt = (SoundPoolWrapperTracker*) param;
	Signal* signal = (Signal*)spwt->getPlaySignal();
	pthread_mutex_t soundlock = spwt->getSoundLock();
	std::list<irr::io::path>* sounds = (std::list<irr::io::path>*)spwt->getSounds();
	while (!spwt->m_isTerminated) {
		signal->Wait();
		if (signal->GetNoWait()) {
			break;
		}
		pthread_mutex_lock(&soundlock);
		for (auto iter = sounds->begin(); iter != sounds->end(); iter++) {
			if (*iter != NULL) {
				const char * pathStr = (*iter).c_str();
				if (pathStr != NULL && pathStr[0] != '\0') {
					ygo::mainGame->playSoundEffect(pathStr);
				}
			}
		}
		sounds->clear();
		pthread_mutex_unlock(&soundlock);
		signal->Reset();
	}
	delete signal;
	return NULL;
}

void ygo::SoundPoolWrapperTracker::playBGM(irr::io::path path,
		AUDIO_PATH_TYPE type) {
	pthread_mutex_lock(&m_soundLock);
	m_sounds->push_back(path);
	pthread_mutex_unlock(&m_soundLock);
	m_pPlaySignal->Set();
}

void ygo::SoundPoolWrapperTracker::playSound(irr::io::path path,
		AUDIO_PATH_TYPE type) {
}

} /* namespace ygo */
