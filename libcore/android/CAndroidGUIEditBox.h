/*
 * CAndroidGUIEditBox.h
 *
 *  Created on: 2014年2月24日
 *      Author: mabin
 */

#ifndef CANDROIDGUIEDITBOX_H_
#define CANDROIDGUIEDITBOX_H_

#include "../gframe/CGUIEditBox.h"
#include <IGUIEnvironment.h>

namespace irr {
namespace gui {

class CAndroidGUIEditBox: public irr::gui::CGUIEditBox {
public:
	CAndroidGUIEditBox();
	CAndroidGUIEditBox(const wchar_t* text, bool border, IGUIEnvironment* environment,
				IGUIElement* parent, s32 id, const core::rect<s32>& rectangle);
	virtual ~CAndroidGUIEditBox();
	static CAndroidGUIEditBox* addAndroidEditBox(const wchar_t* text, bool border, IGUIEnvironment *env, const core::rect<s32>& rectangle, IGUIElement* parent, s32 id = 0);
	virtual bool OnEvent(const SEvent& event);
private:
	void toggleIME(bool pShow);
};

} /* namespace gui */
} /* namespace irr */
#endif /* CANDROIDGUIEDITBOX_H_ */
