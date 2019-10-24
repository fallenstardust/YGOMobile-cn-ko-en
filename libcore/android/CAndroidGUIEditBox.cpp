/*
 * CAndroidGUIEditBox.cpp
 *
 *  Created on: 2014年2月24日
 *      Author: mabin
 */

#include "CAndroidGUIEditBox.h"
#include "android_tools.h"
#include "../gframe/game.h"

namespace irr {
namespace gui {

CAndroidGUIEditBox::CAndroidGUIEditBox(const wchar_t* text, bool border,
		IGUIEnvironment* environment, IGUIElement* parent, s32 id,
		const core::rect<s32>& rectangle) :
		CGUIEditBox(text, border, environment, parent, id, rectangle) {
}

CAndroidGUIEditBox::~CAndroidGUIEditBox() {
	// TODO Auto-generated destructor stub
}

CAndroidGUIEditBox* CAndroidGUIEditBox::addAndroidEditBox(const wchar_t* text,
		bool border, IGUIEnvironment *env, const core::rect<s32>& rectangle,
		IGUIElement* parent, s32 id) {
	CAndroidGUIEditBox* editbox = new CAndroidGUIEditBox(text, border, env,
			parent ? parent : 0, id, rectangle);
	editbox->drop();
	return editbox;
}

bool CAndroidGUIEditBox::OnEvent(const SEvent& event) {
	if (event.GUIEvent.EventType == EGET_ELEMENT_FOCUSED && event.GUIEvent.Caller == this) {
		toggleIME(true);
	}
	return CGUIEditBox::OnEvent(event);
}

inline void CAndroidGUIEditBox::toggleIME(bool pShow) {
	char hint[256];
	BufferIO::EncodeUTF8(getText(), hint);
	ygo::mainGame->toggleIME(pShow, hint);
}

} /* namespace gui */
} /* namespace irr */
