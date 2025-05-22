#ifndef MENU_HANDLER_H
#define MENU_HANDLER_H

#include <irrlicht.h>
#include "replay.h"

#ifdef _IRR_ANDROID_PLATFORM_
#include <android/TouchEventTransferAndroid.h>
#endif

namespace ygo {

class MenuHandler: public irr::IEventReceiver {
public:
	bool OnEvent(const irr::SEvent& event) override;
	irr::s32 prev_operation{ 0 };
	int prev_sel{ -1 };
	Replay temp_replay;
};

}

#endif //MENU_HANDLER_H
