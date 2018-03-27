/*
 * IAudioTracker.h
 *
 *  Created on: 2014-9-30
 *      Author: mabin
 */

#ifndef IAUDIOTRACKER_H_
#define IAUDIOTRACKER_H_

#include <irrlicht.h>

namespace ygo {

enum AUDIO_PATH_TYPE
{
	AP_TYPE_ASSET=0,
	AP_TYPE_PATH
};

class IAudioTracker {
public:
	IAudioTracker(){}

	virtual ~IAudioTracker(){}

	virtual void playBGM(irr::io::path path, AUDIO_PATH_TYPE type) = 0;

	virtual void playSound(irr::io::path path, AUDIO_PATH_TYPE type) = 0;

};

}
#endif /* IAUDIOTRACKER_H_ */
