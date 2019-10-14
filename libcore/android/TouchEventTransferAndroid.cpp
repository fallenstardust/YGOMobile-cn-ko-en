/*
 * TouchEventTransferAndroid.cpp
 *
 *  Created on: 2014年2月19日
 *      Author: mabin
 */

#include "TouchEventTransferAndroid.h"
#include "../gframe/game.h"

using namespace irr;
using namespace os;

namespace irr {
namespace android {

bool TouchEventTransferAndroid::is_timer_set = false;
timer_t TouchEventTransferAndroid::long_press_tid = 0;
int TouchEventTransferAndroid::s_current_x = 0;
int TouchEventTransferAndroid::s_current_y = 0;

TouchEventTransferAndroid::TouchEventTransferAndroid() {
}

bool TouchEventTransferAndroid::OnTransferDeckEdit(const SEvent& event) {
	bool bRet = false;
	char log[256];
	switch (event.EventType) {
	case EET_GUI_EVENT: {
		break;
	}
	case EET_MOUSE_INPUT_EVENT: {
		break;
	}
	case EET_KEY_INPUT_EVENT: {
		break;
	}
	case EET_TOUCH_INPUT_EVENT: {
		bRet = true;
		SEvent transferEvent;
		transferEvent.EventType = EET_MOUSE_INPUT_EVENT;
		transferEvent.MouseInput.X = s_current_x = event.TouchInput.X;
		transferEvent.MouseInput.Y = s_current_y = event.TouchInput.Y;
		if (event.TouchInput.Event == ETIE_PRESSED_DOWN) {

			if (!(ygo::mainGame->scrFilter->isVisible()
							&& ygo::mainGame->scrFilter->isPointInside(position2d<s32>(s_current_x, s_current_y)))) {
				SEvent hoverEvent;
				hoverEvent.EventType = EET_MOUSE_INPUT_EVENT;
				hoverEvent.MouseInput.Event = EMIE_MOUSE_MOVED;
				hoverEvent.MouseInput.X = s_current_x;
				hoverEvent.MouseInput.Y = s_current_y;
				ygo::mainGame->device->postEventFromUser(hoverEvent);
			}
			transferEvent.MouseInput.Event = EMIE_LMOUSE_PRESSED_DOWN;
		} else if (event.TouchInput.Event == ETIE_LEFT_UP) {
			transferEvent.MouseInput.Event = EMIE_LMOUSE_LEFT_UP;
		} else if (event.TouchInput.Event == ETIE_MOVED) {
			transferEvent.MouseInput.ButtonStates = 0x01;
			transferEvent.MouseInput.Event = EMIE_MOUSE_MOVED;
		} else {
			bRet = false;
			break;
		}
		ygo::mainGame->device->postEventFromUser(transferEvent);
		break;
	}
	case EET_ACCELEROMETER_EVENT: {
		break;
	}
	case EET_GYROSCOPE_EVENT: {
		break;
	}
	case EET_DEVICE_MOTION_EVENT: {
		break;
	}
	case EET_JOYSTICK_INPUT_EVENT:
		break;
	case EET_LOG_TEXT_EVENT:
		break;
	case EET_USER_EVENT: {
		break;
	}
	default:
		break;
	}
	return bRet;
}

bool TouchEventTransferAndroid::OnTransferCommon(const SEvent& event,
		bool isRightClickNeeded) {
	bool bRet = false;
	char log[256];
	switch (event.EventType) {
	case EET_GUI_EVENT: {
		break;
	}
	case EET_MOUSE_INPUT_EVENT: {
		break;
	}
	case EET_KEY_INPUT_EVENT: {
		break;
	}
	case EET_TOUCH_INPUT_EVENT: {
		bRet = true;
		SEvent transferEvent;
		transferEvent.EventType = EET_MOUSE_INPUT_EVENT;
		transferEvent.MouseInput.X = s_current_x = event.TouchInput.X;
		transferEvent.MouseInput.Y = s_current_y = event.TouchInput.Y;
		if (event.TouchInput.Event ==  ETIE_PRESSED_DOWN) {
			if (isRightClickNeeded && !is_timer_set) {
				set_long_click_handler(0);
			}
			transferEvent.MouseInput.Event = EMIE_LMOUSE_PRESSED_DOWN;
			SEvent hoverEvent;
			hoverEvent.EventType = EET_MOUSE_INPUT_EVENT;
			hoverEvent.MouseInput.Event = EMIE_MOUSE_MOVED;
			hoverEvent.MouseInput.X = event.TouchInput.X;
			hoverEvent.MouseInput.Y = event.TouchInput.Y;
			ygo::mainGame->device->postEventFromUser(hoverEvent);
		} else if (event.TouchInput.Event ==  ETIE_LEFT_UP) {
			if (isRightClickNeeded && is_timer_set) {
				is_timer_set = false;
				timer_delete(long_press_tid);
			}
			transferEvent.MouseInput.Event = EMIE_LMOUSE_LEFT_UP;
		} else if (event.TouchInput.Event == ETIE_MOVED) {
			if (isRightClickNeeded) {
				set_long_click_handler(0);
			}
			transferEvent.MouseInput.ButtonStates = 0x01;
			transferEvent.MouseInput.Event = EMIE_MOUSE_MOVED;
		} else {
			LOGD("multitouch missed");
			return false;
		}
		if (ygo::mainGame->device && ygo::mainGame->device->isWindowFocused()) {
			ygo::mainGame->device->postEventFromUser(transferEvent);
		}
		break;
	}
	case EET_ACCELEROMETER_EVENT: {
		break;
	}
	case EET_GYROSCOPE_EVENT: {
		break;
	}
	case EET_DEVICE_MOTION_EVENT: {
		break;
	}
	case EET_JOYSTICK_INPUT_EVENT:
		break;
	case EET_LOG_TEXT_EVENT:
		break;
	case EET_USER_EVENT: {
		break;
	}
	default:
		break;
	}
	return bRet;
}

void TouchEventTransferAndroid::set_long_click_handler(int mode) {
	if (is_timer_set) {
		timer_delete(long_press_tid);
		is_timer_set = false;
	}
	sigevent sev;
	struct itimerspec ts;
	/*create timer*/
	sev.sigev_notify = SIGEV_THREAD;
	sev.sigev_notify_function = long_press_handler;
	sev.sigev_notify_attributes = NULL;
	sev.sigev_value.sival_ptr = (void*)mode;
	if (timer_create(CLOCK_REALTIME, &sev, &long_press_tid) == -1) {
		LOGD("create timer failed!");
	}
	/*start the timer*/
	ts.it_value.tv_sec = 1;
	ts.it_value.tv_nsec = 0L;
	ts.it_interval.tv_sec = 0;
	ts.it_interval.tv_nsec = 0;
	if (timer_settime(long_press_tid, 0, &ts, NULL) == -1) {
		LOGD("set timer failed!");
	}
	is_timer_set = true;
}

void TouchEventTransferAndroid::long_press_handler(sigval_t info) {
	int mode = (int)info.sival_ptr;
	LOGD("receve long click %d", mode);
	if (mode == LONG_CLICK_MODE_AS_RIGHTCLICK) {
		SEvent rdownEvent, rupEvent;
		rdownEvent.EventType = EET_MOUSE_INPUT_EVENT;
		rdownEvent.MouseInput.Event = EMIE_RMOUSE_PRESSED_DOWN;
		rdownEvent.MouseInput.X = s_current_x;
		rdownEvent.MouseInput.Y = s_current_y;
		ygo::mainGame->device->postEventFromUser(rdownEvent);
		rupEvent.EventType = EET_MOUSE_INPUT_EVENT;
		rupEvent.MouseInput.Event = EMIE_RMOUSE_LEFT_UP;
		rupEvent.MouseInput.X = s_current_x;
		rupEvent.MouseInput.Y = s_current_y;
		ygo::mainGame->device->postEventFromUser(rupEvent);
		ygo::mainGame->perfromHapticFeedback();
	} else if (mode == LONG_CLICK_MODE_AS_LEFTCLICK) {
		SEvent ldownEvent;
		ldownEvent.EventType = EET_MOUSE_INPUT_EVENT;
		ldownEvent.MouseInput.Event = EMIE_LMOUSE_PRESSED_DOWN;
		ldownEvent.MouseInput.X = s_current_x;
		ldownEvent.MouseInput.Y = s_current_y;
		ygo::mainGame->device->postEventFromUser(ldownEvent);
		ygo::mainGame->perfromHapticFeedback();
	}
	is_timer_set = false;
	timer_delete(long_press_tid);
}
} /* namespace android */
} /* namespace irr */
