/*
 * TouchEventTransferAndroid.h
 *
 *  Created on: 2014年2月19日
 *      Author: mabin
 */

#ifndef TOUCHEVENTTRANSFERANDROID_H_
#define TOUCHEVENTTRANSFERANDROID_H_

#include <irrlicht.h>
#include "../gframe/os.h"
#include <unistd.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>

#define LONG_CLICK_MODE_AS_RIGHTCLICK 0
#define LONG_CLICK_MODE_AS_LEFTCLICK 1

namespace irr {
namespace android {

class TouchEventTransferAndroid {
public:
	TouchEventTransferAndroid();
	static bool OnTransferCommon(const SEvent& event, bool isRightClickNeeded);
	static bool OnTransferDeckEdit(const SEvent& event);
private:
	static void long_press_handler(sigval_t info);
	static bool is_timer_set;
	static timer_t long_press_tid;
	static void set_long_click_handler(int mode);
	static int s_current_x;
	static int s_current_y;
};

} /* namespace android */
} /* namespace irr */
#endif /* TOUCHEVENTTRANSFERANDROID_H_ */
