/*
 * CAndroidGUIComboBox.cpp
 *
 *  Created on: 2014年3月2日
 *      Author: mabin
 */

#include "CAndroidGUIComboBox.h"
#include "android_tools.h"
#include "../gframe/game.h"

namespace irr {
namespace gui {

CAndroidGUIComboBox::CAndroidGUIComboBox(IGUIEnvironment* environment,
		IGUIElement* parent, s32 id, const core::rect<s32>& rectangle) :CGUIComboBox(environment, parent,id,rectangle) {
}

CAndroidGUIComboBox::~CAndroidGUIComboBox() {
	// TODO Auto-generated destructor stub
}

bool CAndroidGUIComboBox::OnEvent(const SEvent& event) {
	bool result = CGUIComboBox::OnEvent(event);
	if (getItemCount() != 0 && isEnabled() && result && (event.GUIEvent.EventType == EGET_BUTTON_CLICKED ||
			event.MouseInput.Event == EMIE_LMOUSE_LEFT_UP )) {
		char* content;
		char** contents;
		char* label;
		int count = getItemCount();
		contents = (char **)malloc(count * sizeof(char *));
		for (int i = 0; i < count; i++) {
			content = (char *)malloc(256 * 4);
			BufferIO::EncodeUTF8(getItem(i), content);
			*(contents + i) = content;
		}
		ygo::mainGame->showAndroidComboBoxCompat(true, contents, count);
		for (int i = 0; i < count; i++) {
			free(*(contents + i));
		}
		free(contents);
	}
	return result;
}

IGUIComboBox* irr::gui::CAndroidGUIComboBox::addAndroidComboBox(
		IGUIEnvironment* env, const core::rect<s32>& rectangle,
		IGUIElement* parent, s32 id) {
	CAndroidGUIComboBox* cbox = new CAndroidGUIComboBox(env, parent ? parent : 0, id, rectangle);
	cbox->drop();
	return cbox;
}

} /* namespace gui */
} /* namespace irr */

