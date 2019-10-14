// This file is part of the "Irrlicht Engine".
// For conditions of distribution and use, see copyright notice in irrlicht.h

#include "android_tools.h"
#include "../gframe/bufferio.h"

namespace irr {
namespace android {

inline static void ReadString(irr::io::path &path, char*& p) {
	u32 length = static_cast<u32>(BufferIO::ReadInt32(p));
	if (length != 0) {
		path.append(p, length);
		path[length] = '\0';
		p += length;
	}
}

InitOptions::InitOptions(void*data) :
		m_opengles_version(0),
		m_card_quality(0),
		m_font_aa_enabled(true),
		m_se_enabled(false) {
	if (data != NULL) {
		char* rawdata = (char*)data;
		int tmplength = 0;
		m_opengles_version = BufferIO::ReadInt32(rawdata);
		m_se_enabled = BufferIO::ReadInt32(rawdata) > 0;
		
		m_card_quality = BufferIO::ReadInt32(rawdata);
		m_font_aa_enabled = BufferIO::ReadInt32(rawdata) > 0;
		m_ps_enabled = BufferIO::ReadInt32(rawdata) > 0;
		
		//cache dir
		ReadString(m_work_dir, rawdata);
		//cdbs
		cdb_count = BufferIO::ReadInt32(rawdata);
		m_db_files = new io::path[cdb_count];
		
		for(int i = 0;i < cdb_count; i++){
			io::path tmp_path;
			ReadString(tmp_path, rawdata);
			m_db_files[i] = tmp_path;
		}
		//zips
		zip_count = BufferIO::ReadInt32(rawdata);
		m_archive_files = new io::path[zip_count];
		for(int i = 0 ;i < zip_count; i++){
			io::path tmp_path;
			ReadString(tmp_path, rawdata);
			m_archive_files[i] = tmp_path;
		}
		ReadString(m_font_path, rawdata);
		ReadString(m_res_path, rawdata);
		ReadString(m_image_path, rawdata);
	}
}

s32 handleInput(ANDROID_APP app, AInputEvent* androidEvent) {
	IrrlichtDevice* device = (IrrlichtDevice*) app->userData;
	s32 Status = 0;

	if (AInputEvent_getType(androidEvent) == AINPUT_EVENT_TYPE_MOTION) {
		SEvent Event;
		Event.EventType = EET_TOUCH_INPUT_EVENT;

		s32 EventAction = AMotionEvent_getAction(androidEvent);
		s32 EventType = EventAction & AMOTION_EVENT_ACTION_MASK;

		bool TouchReceived = true;

		switch (EventType) {
		case AMOTION_EVENT_ACTION_DOWN:
		case AMOTION_EVENT_ACTION_POINTER_DOWN:
			Event.TouchInput.Event = ETIE_PRESSED_DOWN;
			break;
		case AMOTION_EVENT_ACTION_MOVE:
			Event.TouchInput.Event = ETIE_MOVED;
			break;
		case AMOTION_EVENT_ACTION_UP:
		case AMOTION_EVENT_ACTION_POINTER_UP:
		case AMOTION_EVENT_ACTION_CANCEL:
			Event.TouchInput.Event = ETIE_LEFT_UP;
			break;
		default:
			TouchReceived = false;
			break;
		}

		if (TouchReceived) {
			// Process all touches for move action.
			if (Event.TouchInput.Event == ETIE_MOVED) {
				s32 PointerCount = AMotionEvent_getPointerCount(androidEvent);

				for (s32 i = 0; i < PointerCount; ++i) {
					Event.TouchInput.ID = AMotionEvent_getPointerId(
							androidEvent, i);
					Event.TouchInput.X = AMotionEvent_getX(androidEvent, i);
					Event.TouchInput.Y = AMotionEvent_getY(androidEvent, i);

					device->postEventFromUser(Event);
				}
			} else // Process one touch for other actions.
			{
				s32 PointerIndex = (EventAction
						& AMOTION_EVENT_ACTION_POINTER_INDEX_MASK)
						>> AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT;

				Event.TouchInput.ID = AMotionEvent_getPointerId(androidEvent,
						PointerIndex);
				Event.TouchInput.X = AMotionEvent_getX(androidEvent,
						PointerIndex);
				Event.TouchInput.Y = AMotionEvent_getY(androidEvent,
						PointerIndex);

				device->postEventFromUser(Event);
			}

			Status = 1;
		}
	} else if (AInputEvent_getType(androidEvent) == AINPUT_EVENT_TYPE_KEY) {
		s32 key = AKeyEvent_getKeyCode(androidEvent);
		if (key == AKEYCODE_BACK) {
			Status = 1;
		}
	}
	return Status;
}

JNIEnv *getJniEnv(ANDROID_APP app) {
    if (!app || !app->activity || !app->activity->vm)
        return NULL;
    JNIEnv *env;
    JavaVM *vm = app->activity->vm;
    vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_4);
    if (env == NULL) {
        vm->AttachCurrentThread(&env, NULL);
    }
    return env;
}

} // namespace android
} // namespace irr
