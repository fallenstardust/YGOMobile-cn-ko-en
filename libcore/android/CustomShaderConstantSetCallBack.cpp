/*
 * CustomShaderConstantSetCallBack.cpp
 *
 *  Created on: 2014年3月13日
 *      Author: mabin
 */

#include "CustomShaderConstantSetCallBack.h"

namespace irr {
namespace android {

} /* namespace android */
} /* namespace irr */

irr::android::CustomShaderConstantSetCallBack::CustomShaderConstantSetCallBack():
		uWorldMatrixID(-1), uMvpMatrixID(-1), uTextureUsage0ID(-1),
		uTextureMatrix0ID(-1), uTextureMatrix1ID(-1), uTextureUnit0ID(-1), firstUpdate(true){
}

void irr::android::CustomShaderConstantSetCallBack::OnSetConstants(
		 video::IMaterialRendererServices* services, s32 userData) {
	irr::video::IVideoDriver* driver = services->getVideoDriver();
	if (firstUpdate) {
		uWorldMatrixID = services->getVertexShaderConstantID("uWorldMatrix");
		uMvpMatrixID = services->getVertexShaderConstantID("uMvpMatrix");
		uTextureUsage0ID = services->getVertexShaderConstantID("uTextureUsage0");
		uTextureMatrix0ID = services->getVertexShaderConstantID("uTextureMatrix0");
		uTextureMatrix1ID = services->getVertexShaderConstantID("uTextureMatrix1");
		uTextureUnit0ID = services->getVertexShaderConstantID("uTextureUnit0");
		firstUpdate = false;
	}

	core::matrix4 world = driver->getTransform(video::ETS_WORLD);
	services->setPixelShaderConstant(uWorldMatrixID, world.pointer(), 16);

	core::matrix4 worldViewProj = driver->getTransform(video::ETS_PROJECTION);
	worldViewProj *= driver->getTransform(video::ETS_VIEW);
	worldViewProj *= driver->getTransform(video::ETS_WORLD);
	services->setPixelShaderConstant(uMvpMatrixID, worldViewProj.pointer(), 16);

	s32 TextureUsage0 = false;
	int textureCount = driver->getTextureCount();
	if (textureCount > 0) {
		TextureUsage0 = true;
	}
	services->setPixelShaderConstant(uTextureUsage0ID, &TextureUsage0, 1);

	core::matrix4 textureMatrix0 = driver->getTransform(video::ETS_TEXTURE_0);
	core::matrix4 textureMatrix1 = driver->getTransform(video::ETS_TEXTURE_0);

	services->setPixelShaderConstant(uTextureMatrix0ID, textureMatrix0.pointer(), 16);
	services->setPixelShaderConstant(uTextureMatrix1ID, textureMatrix1.pointer(), 16);

	s32 TextureUnit0 = 0;

	services->setPixelShaderConstant(uTextureUnit0ID, &TextureUnit0, 1);
}
