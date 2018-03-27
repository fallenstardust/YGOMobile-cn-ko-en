/*
 * CustomShaderConstantSetCallBack.h
 *
 *  Created on: 2014年3月13日
 *      Author: mabin
 */

#ifndef CUSTOMSHADERCONSTANTSETCALLBACK_H_
#define CUSTOMSHADERCONSTANTSETCALLBACK_H_

#include "irrlicht.h"

namespace irr {
namespace android {

class CustomShaderConstantSetCallBack: public video::IShaderConstantSetCallBack {
public:
	CustomShaderConstantSetCallBack();
	virtual void OnSetConstants(video::IMaterialRendererServices* services, s32 userData);
private:
	s32 uWorldMatrixID;
	s32 uMvpMatrixID;
	s32 uTextureUsage0ID;
	s32 uTextureMatrix0ID;
	s32 uTextureMatrix1ID;
	s32 uTextureUnit0ID;

	bool firstUpdate;
};

} /* namespace android */
} /* namespace irr */
#endif /* CUSTOMSHADERCONSTANTSETCALLBACK_H_ */
