/*
 * OpenSLSoundTracker.cpp
 *
 *  Created on: 2014-9-30
 *      Author: mabin
 */

#include "OpenSLSoundTracker.h"

namespace ygo {

OpenSLSoundTracker::OpenSLSoundTracker(AAssetManager* am) :
		mBQPlayerObject(NULL), mOutputMixObject(NULL), mEngineObject(NULL), mFDPlayerObject(
				NULL) {
	initAudioEngine();
	mAm = am;
}

OpenSLSoundTracker::~OpenSLSoundTracker() {
	// destroy buffer queue audio player object, and invalidate all associated interfaces
	if (mFDPlayerObject != NULL) {
		(*mFDPlayerObject)->Destroy(mFDPlayerObject);
		mFDPlayerObject = NULL;
		mFDPlayerPlay = NULL;
	}
	// destroy buffer queue audio player object, and invalidate all associated interfaces
	if (mBQPlayerObject != NULL) {
		(*mBQPlayerObject)->Destroy(mBQPlayerObject);
		mBQPlayerObject = NULL;
		mBQPlayerPlay = NULL;
		mBQPlayerBufferQueue = NULL;
	}
	// destroy output mix object, and invalidate all associated interfaces
	if (mOutputMixObject != NULL) {
		(*mOutputMixObject)->Destroy(mOutputMixObject);
		mOutputMixObject = NULL;
	}
	// destroy engine object, and invalidate all associated interfaces
	if (mEngineObject != NULL) {
		(*mEngineObject)->Destroy(mEngineObject);
		mEngineObject = NULL;
		mEngine = NULL;
	}
	mAm = NULL;
}

void OpenSLSoundTracker::initAudioEngine() {
	SLresult result;

	// create engine
	result = slCreateEngine(&mEngineObject, 0, NULL, 0, NULL, NULL);
	assert(SL_RESULT_SUCCESS == result);
	(void) result;

	// realize the engine
	result = (*mEngineObject)->Realize(mEngineObject, SL_BOOLEAN_FALSE);
	assert(SL_RESULT_SUCCESS == result);
	(void) result;

	// get the engine interface, which is needed in order to create other objects
	result = (*mEngineObject)->GetInterface(mEngineObject, SL_IID_ENGINE,
			&mEngine);
	assert(SL_RESULT_SUCCESS == result);
	(void) result;

	const SLInterfaceID ids[0] = {};
	const SLboolean req[0] = {};
	result = (*mEngine)->CreateOutputMix(mEngine, &mOutputMixObject, 0, ids,
			req);
	assert(SL_RESULT_SUCCESS == result);
	(void) result;

	// realize the output mix
	result = (*mOutputMixObject)->Realize(mOutputMixObject, SL_BOOLEAN_FALSE);
	assert(SL_RESULT_SUCCESS == result);
	(void) result;

}

void OpenSLSoundTracker::createBufferQueuePlayer() {
	SLresult result;

	// configure audio source
	SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {
			SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2 };
	SLDataFormat_PCM format_pcm = { SL_DATAFORMAT_PCM, 1, SL_SAMPLINGRATE_16,
			SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
			SL_SPEAKER_FRONT_CENTER, SL_BYTEORDER_LITTLEENDIAN };
	SLDataSource audioSrc = { &loc_bufq, &format_pcm };

	// configure audio sink
	SLDataLocator_OutputMix loc_outmix = { SL_DATALOCATOR_OUTPUTMIX,
			mOutputMixObject };
	SLDataSink audioSnk = { &loc_outmix, NULL };

	// create audio player
	const SLInterfaceID ids[1] = { SL_IID_BUFFERQUEUE };
	const SLboolean req[1] = { SL_BOOLEAN_TRUE };
	result = (*mEngine)->CreateAudioPlayer(mEngine, &mBQPlayerObject, &audioSrc,
			&audioSnk, 1, ids, req);
	assert(SL_RESULT_SUCCESS == result);
	(void) result;

	// realize the player
	result = (*mBQPlayerObject)->Realize(mBQPlayerObject, SL_BOOLEAN_FALSE);
	assert(SL_RESULT_SUCCESS == result);
	(void) result;

	// get the play interface
	result = (*mBQPlayerObject)->GetInterface(mBQPlayerObject, SL_IID_PLAY,
			&mBQPlayerPlay);
	assert(SL_RESULT_SUCCESS == result);
	(void) result;

	// get the buffer queue interface
	result = (*mBQPlayerObject)->GetInterface(mBQPlayerObject,
			SL_IID_BUFFERQUEUE, &mBQPlayerBufferQueue);
	assert(SL_RESULT_SUCCESS == result);
	(void) result;

	// set the player's state to playing
	result = (*mBQPlayerPlay)->SetPlayState(mBQPlayerPlay,
			SL_PLAYSTATE_PLAYING);
	assert(SL_RESULT_SUCCESS == result);
	(void) result;
}

void OpenSLSoundTracker::playBGM(irr::io::path path, AUDIO_PATH_TYPE type) {
	// destroy buffer queue audio player object, and invalidate all associated interfaces
	if (mFDPlayerObject != NULL) {
		(*mFDPlayerObject)->Destroy(mFDPlayerObject);
		mFDPlayerObject = NULL;
		mFDPlayerPlay = NULL;
	}
	SLresult result;
	AAsset* asset = AAssetManager_open(mAm, path.c_str(), AASSET_MODE_UNKNOWN);
	// the asset might not be found
	if (NULL == asset) {
		return;
	}
	// open asset as file descriptor
	off_t start, length;
	int fd = AAsset_openFileDescriptor(asset, &start, &length);
	assert(0 <= fd);
	AAsset_close(asset);

	// configure audio source
	SLDataLocator_AndroidFD loc_fd = { SL_DATALOCATOR_ANDROIDFD, fd, start,
			length };
	SLDataFormat_MIME format_mime = { SL_DATAFORMAT_MIME, NULL,
			SL_CONTAINERTYPE_UNSPECIFIED };
	SLDataSource audioSrc = { &loc_fd, &format_mime };

	// configure audio sink
	SLDataLocator_OutputMix loc_outmix = { SL_DATALOCATOR_OUTPUTMIX,
			mOutputMixObject };
	SLDataSink audioSnk = { &loc_outmix, NULL };

	// create audio player
	const SLInterfaceID ids[3] = { SL_IID_SEEK, SL_IID_MUTESOLO, SL_IID_VOLUME };
	const SLboolean req[3] =
			{ SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE };
	result = (*mEngine)->CreateAudioPlayer(mEngine, &mFDPlayerObject, &audioSrc,
			&audioSnk, 3, ids, req);
	assert(SL_RESULT_SUCCESS == result);
	(void) result;

	// realize the player
	result = (*mFDPlayerObject)->Realize(mFDPlayerObject, SL_BOOLEAN_FALSE);
	assert(SL_RESULT_SUCCESS == result);
	(void) result;

	// get the play interface
	result = (*mFDPlayerObject)->GetInterface(mFDPlayerObject, SL_IID_PLAY,
			&mFDPlayerPlay);
	assert(SL_RESULT_SUCCESS == result);
	(void) result;

	if (NULL != mFDPlayerPlay) {
		// set the player's state
		result = (*mFDPlayerPlay)->SetPlayState(mFDPlayerPlay, SL_PLAYSTATE_PLAYING);
		assert(SL_RESULT_SUCCESS == result);
		(void) result;
	}
	return;
}

void OpenSLSoundTracker::playSound(irr::io::path path, AUDIO_PATH_TYPE type) {
}

} /* namespace ygo */
