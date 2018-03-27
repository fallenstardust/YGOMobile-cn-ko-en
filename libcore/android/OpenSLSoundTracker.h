/*
 * OpenSLSoundTracker.h
 *
 *  Created on: 2014-9-30
 *      Author: mabin
 */

#ifndef OPENSLSOUNDTRACKER_H_
#define OPENSLSOUNDTRACKER_H_

#include "../../Classes/gframe/IAudioTracker.h"
// for native audio
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
// for native asset manager
#include <sys/types.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

namespace ygo {

class OpenSLSoundTracker: public virtual ygo::IAudioTracker {
public:
	OpenSLSoundTracker(AAssetManager* am);
	virtual ~OpenSLSoundTracker();

	virtual void playBGM(irr::io::path path, AUDIO_PATH_TYPE type);

	virtual void playSound(irr::io::path path, AUDIO_PATH_TYPE type);

private:
	void initAudioEngine();

	void createBufferQueuePlayer();

	void createBufferQueuePlayer2();

	void createAssetFilePlayer();

	void createUriFilePlayer();

	SLObjectItf mEngineObject;
	SLObjectItf mOutputMixObject;
	SLEnvironmentalReverbItf mOutputMixEnvironmentalReverb;
	SLEngineItf mEngine;

	SLObjectItf mBQPlayerObject;
	SLPlayItf mBQPlayerPlay;
	SLAndroidSimpleBufferQueueItf mBQPlayerBufferQueue;
	AAssetManager* mAm;

	SLObjectItf mFDPlayerObject;
	SLPlayItf mFDPlayerPlay;
};

}
/* namespace ygo */
#endif /* OPENSLSOUNDTRACKER_H_ */
