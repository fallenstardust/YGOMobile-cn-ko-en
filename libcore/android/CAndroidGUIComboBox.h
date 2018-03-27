/*
 * CAndroidGUIComboBox.h
 *
 *  Created on: 2014年3月2日
 *      Author: mabin
 */

#ifndef CANDROIDGUICOMBOBOX_H_
#define CANDROIDGUICOMBOBOX_H_

#include "../gframe/CGUIComboBox.h"

namespace irr {
namespace gui {

class CAndroidGUIComboBox: public irr::gui::CGUIComboBox {
public:
	CAndroidGUIComboBox(IGUIEnvironment* environment,
					IGUIElement* parent, s32 id, const core::rect<s32>& rectangle);
	virtual ~CAndroidGUIComboBox();
	virtual bool OnEvent(const SEvent& event);

	static IGUIComboBox* addAndroidComboBox(IGUIEnvironment *env, const core::rect<s32>& rectangle, IGUIElement* parent, s32 id = 0);
};

} /* namespace gui */
} /* namespace irr */

#endif /* CANDROIDGUICOMBOBOX_H_ */
