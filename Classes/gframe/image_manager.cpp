#include "image_manager.h"
#include "image_resizer.h"
#include "game.h"
#include <thread>

namespace ygo {

ImageManager imageManager;

bool ImageManager::Initial(const path dir) {
	tCover[0] = nullptr;
	tCover[1] = nullptr;
	tCover[2] = GetTextureFromFile((dir + path("/textures/cover.jpg")).c_str(), CARD_IMG_WIDTH, CARD_IMG_HEIGHT);
	tCover[3] = GetTextureFromFile((dir + path("/textures/cover2.jpg")).c_str(), CARD_IMG_WIDTH, CARD_IMG_HEIGHT);
	if(!tCover[3])
		tCover[3] = tCover[2];
	tUnknown = nullptr;
	tUnknownFit = nullptr;
	tUnknownThumb = nullptr;
	tBigPicture = nullptr;
	tLoading = nullptr;
	tThumbLoadingThreadRunning = false;
	tAct = driver->getTexture((dir + path("/textures/act.png")).c_str());
	tAttack = driver->getTexture((dir + path("/textures/attack.png")).c_str());
	tChain = driver->getTexture((dir + path("/textures/chain.png")).c_str());
	tNegated = driver->getTexture((dir + path("/textures/negated.png")).c_str());
	tNumber = driver->getTexture((dir + path("/textures/number.png")).c_str());
	tLPBar = driver->getTexture((dir + path("/textures/lp3.png")).c_str());
	tLPFrame = driver->getTexture((dir + path("/textures/lpf.png")).c_str());
	tMask = driver->getTexture((dir + path("/textures/mask.png")).c_str());
	tEquip = driver->getTexture((dir + path("/textures/equip.png")).c_str());
	tTarget = driver->getTexture((dir + path("/textures/target.png")).c_str());
	tChainTarget = driver->getTexture((dir + path("/textures/chaintarget.png")).c_str());
	tLim = driver->getTexture((dir + path("/textures/icon_lim.png")).c_str());
	tOT = driver->getTexture((dir + path("/textures/ot.png")).c_str());
	tHand[0] = driver->getTexture((dir + path("/textures/f1.jpg")).c_str());
	tHand[1] = driver->getTexture((dir + path("/textures/f2.jpg")).c_str());
	tHand[2] = driver->getTexture((dir + path("/textures/f3.jpg")).c_str());
	tBackGround = nullptr;
	tBackGround_menu = nullptr;
	tBackGround_deck = nullptr;
	tField[0] = driver->getTexture((dir + path("/textures/field2.png")).c_str());
	tFieldTransparent[0] = driver->getTexture((dir + path("/textures/field-transparent2.png")).c_str());
	tField[1] = driver->getTexture((dir + path("/textures/field3.png")).c_str());
	tFieldTransparent[1] = driver->getTexture((dir + path("/textures/field-transparent3.png")).c_str());
	ResizeTexture(dir);
	
	tTotalAtk = driver->getTexture((dir + path("/textures/totalAtk.png")).c_str());
	tSelField = driver->getTexture((dir + path("/textures/selfield.png")).c_str());
	tSelFieldLinkArrows[1] = driver->getTexture((dir + path("/textures/link_marker_on_1.png")).c_str());
	tSelFieldLinkArrows[2] = driver->getTexture((dir + path("/textures/link_marker_on_2.png")).c_str());
	tSelFieldLinkArrows[3] = driver->getTexture((dir + path("/textures/link_marker_on_3.png")).c_str());
	tSelFieldLinkArrows[4] = driver->getTexture((dir + path("/textures/link_marker_on_4.png")).c_str());
	tSelFieldLinkArrows[6] = driver->getTexture((dir + path("/textures/link_marker_on_6.png")).c_str());
	tSelFieldLinkArrows[7] = driver->getTexture((dir + path("/textures/link_marker_on_7.png")).c_str());
	tSelFieldLinkArrows[8] = driver->getTexture((dir + path("/textures/link_marker_on_8.png")).c_str());
	tSelFieldLinkArrows[9] = driver->getTexture((dir + path("/textures/link_marker_on_9.png")).c_str());
	tCardType = driver->getTexture((dir + path("/textures/cardtype.png")).c_str());
	tAvatar[0] = driver->getTexture((dir + path("/textures/me.jpg")).c_str());
	tAvatar[1] = driver->getTexture((dir + path("/textures/opponent.jpg")).c_str());
	tLPBarFrame = driver->getTexture((dir + path("/textures/lpbarf.png")).c_str());
	tSettings = driver->getTexture((dir + path("/textures/extra/tsettings.png")).c_str());
	tLogs = driver->getTexture((dir + path("/textures/extra/tlogs.png")).c_str());
	tMute = driver->getTexture((dir + path("/textures/extra/tmute.png")).c_str());
	tPlay = driver->getTexture((dir + path("/textures/extra/tplay.png")).c_str());
	tTalk = driver->getTexture((dir + path("/textures/extra/ttalk.png")).c_str());
	tOneX = driver->getTexture((dir + path("/textures/extra/tonex.png")).c_str());
	tDoubleX = driver->getTexture((dir + path("/textures/extra/tdoublex.png")).c_str());
	tShut = driver->getTexture((dir + path("/textures/extra/tshut.png")).c_str());
	tClose = driver->getTexture((dir + path("/textures/extra/tclose.png")).c_str());
    tTitleBar = GetTextureFromFile((dir + path("/textures/extra/stitlebar.png")).c_str(), 451 * mainGame->xScale, 100 * mainGame->yScale);
    tWindow = GetTextureFromFile((dir + path("/textures/extra/sWindow.png")).c_str(), 580 * mainGame->xScale, 420 * mainGame->yScale);
    tWindow_V = GetTextureFromFile((dir + path("/textures/extra/sWindow_V.png")).c_str(), 300 * mainGame->xScale, 420 * mainGame->yScale);
	tDialog_S = GetTextureFromFile((dir + path("/textures/extra/sDialog_S.png")).c_str(), 300 * mainGame->xScale, 500 * mainGame->yScale);
	tDialog_L = GetTextureFromFile((dir + path("/textures/extra/sDialog_L.png")).c_str(), 600 * mainGame->xScale, 200 * mainGame->yScale);
	tButton_L = GetTextureFromFile((dir + path("/textures/extra/sButton_L.png")).c_str(), 560 * mainGame->xScale, 80 * mainGame->yScale);
	tButton_L_pressed = GetTextureFromFile((dir + path("/textures/extra/sButton_L_pressed.png")).c_str(), 560 * mainGame->xScale, 80 * mainGame->yScale);
	tButton_S = GetTextureFromFile((dir + path("/textures/extra/sButton_S.png")).c_str(), 200 * mainGame->xScale, 80 * mainGame->yScale);
	tButton_S_pressed = GetTextureFromFile((dir + path("/textures/extra/sButton_S_pressed.png")).c_str(), 200 * mainGame->xScale, 80 * mainGame->yScale);
	tButton_C = GetTextureFromFile((dir + path("/textures/extra/sButton_C.png")).c_str(), 80 * mainGame->xScale, 80 * mainGame->yScale);
	tButton_C_pressed = GetTextureFromFile((dir + path("/textures/extra/sButton_C_pressed.png")).c_str(), 80 * mainGame->xScale, 80 * mainGame->yScale);
    // 加载各种表情图片
    tEmoticons = GetTextureFromFile((dir + path("/textures/extra/emoticons.png")).c_str(), 320 * mainGame->yScale, 320 * mainGame->yScale);
    tEmoticon = driver->getTexture((dir + path("/textures/extra/temoticon.png")).c_str());
    if(tEmoticons) {
        // &laugh 在左上角 (0,0) - (80,80)
        emoticonRects[L"&laugh"] = irr::core::recti(0, 0, 80 * mainGame->yScale, 80 * mainGame->yScale);
        // &ridiculous 在第一行第二个 (80,0) - (160,80)
        emoticonRects[L"&ridiculous"] = irr::core::recti(80 * mainGame->yScale, 0, 160 * mainGame->yScale, 80 * mainGame->yScale);
        // &stick_tongue 在第一行第三个 (160,0) - (240,80)
        emoticonRects[L"&stick_tongue"] = irr::core::recti(160 * mainGame->yScale, 0, 240 * mainGame->yScale, 80 * mainGame->yScale);
        // &reluctant 在第一行第四个 (240,0) - (320,80)
        emoticonRects[L"&reluctant"] = irr::core::recti(240 * mainGame->yScale, 0, 320 * mainGame->yScale, 80 * mainGame->yScale);
        // &sweat 在第二行第一个 (0,80) - (80,160)
        emoticonRects[L"&sweat"] = irr::core::recti(0, 80 * mainGame->yScale, 80 * mainGame->yScale, 160 * mainGame->yScale);
        // &confused 在第二行第二个 (80,80) - (160,160)
        emoticonRects[L"&confused"] = irr::core::recti(80 * mainGame->yScale, 80 * mainGame->yScale, 160 * mainGame->yScale, 160 * mainGame->yScale);
        // &surprised 在第二行第三个 (160,80) - (240,160)
        emoticonRects[L"&surprised"] = irr::core::recti(160 * mainGame->yScale, 80 * mainGame->yScale, 240 * mainGame->yScale, 160 * mainGame->yScale);
        // &bawl 在第二行第四个 (240,80) - (320,160)
        emoticonRects[L"&bawl"] = irr::core::recti(240 * mainGame->yScale, 80 * mainGame->yScale, 320 * mainGame->yScale, 160 * mainGame->yScale);
        // &angry 在第三行第一个 (0,160) - (80,240)
        emoticonRects[L"&angry"] = irr::core::recti(0, 160 * mainGame->yScale, 80 * mainGame->yScale, 240 * mainGame->yScale);
        // &rage 在第三行第二个 (80,160) - (160,240)
        emoticonRects[L"&rage"] = irr::core::recti(80 * mainGame->yScale, 160 * mainGame->yScale, 160 * mainGame->yScale, 240 * mainGame->yScale);
        // &sneaky 在第三行第三个 (160,160) - (240,240)
        emoticonRects[L"&sneaky"] = irr::core::recti(160 * mainGame->yScale, 160 * mainGame->yScale, 240 * mainGame->yScale, 240 * mainGame->yScale);
        // &obedient 在第三行第四个 (240,160) - (320,240)
        emoticonRects[L"&obedient"] = irr::core::recti(240 * mainGame->yScale, 160 * mainGame->yScale, 320 * mainGame->yScale, 240 * mainGame->yScale);
        // &good 在第四行第一个 (0,240) - (80,320)
        emoticonRects[L"&good"] = irr::core::recti(0, 240 * mainGame->yScale, 80 * mainGame->yScale, 320 * mainGame->yScale);
        // &yeah 在第四行第二个 (80,240) - (160,320)
        emoticonRects[L"&cool"] = irr::core::recti(80 * mainGame->yScale, 240 * mainGame->yScale, 160 * mainGame->yScale, 320 * mainGame->yScale);
        // &gotcha 在第四行第三个 (160,240) - (240,320)
        emoticonRects[L"&despise"] = irr::core::recti(160 * mainGame->yScale, 240 * mainGame->yScale, 240 * mainGame->yScale, 320 * mainGame->yScale);
        // &love 在第四行第四个 (240,240) - (320,320)
        emoticonRects[L"&shy"] = irr::core::recti(240 * mainGame->yScale, 240 * mainGame->yScale, 320 * mainGame->yScale, 320 * mainGame->yScale);
    }
    // 裁剪并创建独立的表情纹理
    for (const auto& pair : emoticonRects) {
        // 获取源纹理的图像数据
        irr::video::IImage* sourceImage = driver->createImageFromData(tEmoticons->getColorFormat(),tEmoticons->getSize(),tEmoticons->lock(),false);
        if (sourceImage) {
            tEmoticons->unlock(); // 解锁纹理
            // 创建目标图像（裁剪区域大小）
            irr::core::dimension2d<irr::u32> cropSize(pair.second.getWidth(),pair.second.getHeight());
            irr::video::IImage* targetImage = driver->createImage(sourceImage->getColorFormat(),cropSize);

            if (targetImage) {
                // 从源图像的指定区域复制到目标图像
                sourceImage->copyTo(targetImage,irr::core::position2d<irr::s32>(0, 0),pair.second);
                // 创建唯一纹理名称
                std::wstring textureName = L"emoticon_" + pair.first;
                // 添加纹理到驱动程序
                irr::video::ITexture* emoticonTexture = driver->addTexture(textureName.c_str(),targetImage);
                if (emoticonTexture) {
                    emoticons[pair.first] = emoticonTexture;
                }
                targetImage->drop(); // 释放目标图像
            }
            sourceImage->drop(); // 释放源图像
        }
    }
	int i = 0;
	char buff[100];
	for (; i < 14; i++) {
		snprintf(buff, 100, "/textures/extra/rscale_%d.png", i);
		tRScale[i] = driver->getTexture((dir + path(buff)).c_str());
	}
	for (i = 0; i < 14; i++) {
		snprintf(buff, 100, "/textures/extra/lscale_%d.png", i);
		tLScale[i] = driver->getTexture((dir + path(buff)).c_str());
	}
    tGSC = driver->getTexture((dir + path("/textures/extra/gsc.png")).c_str());
	tClock = driver->getTexture((dir + path("/textures/tiktok.png")).c_str());
	image_work_path = dir;
	return true;
}
void ImageManager::SetDevice(irr::IrrlichtDevice* dev) {
	device = dev;
	driver = dev->getVideoDriver();
}
void ImageManager::ClearTexture() {
	for(auto tit = tMap[0].begin(); tit != tMap[0].end(); ++tit) {
		if(tit->second)
			driver->removeTexture(tit->second);
	}
	for(auto tit = tMap[1].begin(); tit != tMap[1].end(); ++tit) {
		if(tit->second)
			driver->removeTexture(tit->second);
	}
	for(auto tit = tThumb.begin(); tit != tThumb.end(); ++tit) {
		if(tit->second && tit->second != tLoading)
			driver->removeTexture(tit->second);
	}
	for(auto tit = tFields.begin(); tit != tFields.end(); ++tit) {
		if(tit->second)
			driver->removeTexture(tit->second);
	}
	if(tBigPicture != nullptr) {
		driver->removeTexture(tBigPicture);
		tBigPicture = nullptr;
	}
	tMap[0].clear();
	tMap[1].clear();
	tThumb.clear();
	tFields.clear();
	tThumbLoadingMutex.lock();
	tThumbLoading.clear();
	while(!tThumbLoadingCodes.empty())
		tThumbLoadingCodes.pop();
	tThumbLoadingThreadRunning = false;
	tThumbLoadingMutex.unlock();
	ClearEmoticons();
}
void ImageManager::ResizeTexture(const path dir) {
	irr::s32 imgWidth = CARD_IMG_WIDTH * mainGame->xScale;
	irr::s32 imgHeight = CARD_IMG_HEIGHT * mainGame->yScale;
	irr::s32 imgWidthThumb = CARD_THUMB_WIDTH * mainGame->xScale;
	irr::s32 imgHeightThumb = CARD_THUMB_HEIGHT * mainGame->yScale;
	float mul = (mainGame->xScale > mainGame->yScale) ? mainGame->yScale : mainGame->xScale;
	irr::s32 imgWidthFit = CARD_IMG_WIDTH * mul;
	irr::s32 imgHeightFit = CARD_IMG_HEIGHT * mul;
	irr::s32 bgWidth = 1024 * mainGame->xScale;
	irr::s32 bgHeight = 640 * mainGame->yScale;
	driver->removeTexture(tCover[0]);
	driver->removeTexture(tCover[1]);
	tCover[0] = GetTextureFromFile((dir + path("/textures/cover.jpg")).c_str(), imgWidth, imgHeight);
	tCover[1] = GetTextureFromFile((dir + path("/textures/cover2.jpg")).c_str(), imgWidth, imgHeight);
	if(!tCover[1])
		tCover[1] = tCover[0];
	driver->removeTexture(tUnknown);
	driver->removeTexture(tUnknownFit);
	driver->removeTexture(tUnknownThumb);
	driver->removeTexture(tLoading);
	tLoading = GetTextureFromFile((dir + path("/textures/cover.jpg")).c_str(), imgWidthThumb, imgHeightThumb);
	tUnknown = GetTextureFromFile((dir + path("/textures/unknown.jpg")).c_str(), CARD_IMG_WIDTH, CARD_IMG_HEIGHT);
	tUnknownFit = GetTextureFromFile((dir + path("/textures/unknown.jpg")).c_str(), imgWidthFit, imgHeightFit);
	tUnknownThumb = GetTextureFromFile((dir + path("/textures/unknown.jpg")).c_str(), imgWidthThumb, imgHeightThumb);
	driver->removeTexture(tBackGround);
	tBackGround = GetTextureFromFile((dir + path("/textures/bg.jpg")).c_str(), bgWidth, bgHeight);
	driver->removeTexture(tBackGround_menu);
	tBackGround_menu = GetTextureFromFile((dir + path("/textures/bg_menu.jpg")).c_str(), bgWidth, bgHeight);
	if(!tBackGround_menu)
		tBackGround_menu = tBackGround;
	driver->removeTexture(tBackGround_deck);
	tBackGround_deck = GetTextureFromFile((dir + path("/textures/bg_deck.jpg")).c_str(), bgWidth, bgHeight);
	if(!tBackGround_deck)
		tBackGround_deck = tBackGround;
}
void ImageManager::resizeImage(irr::video::IImage* src, irr::video::IImage* dest, bool use_threading) {
	imageResizer.resize(src, dest, use_threading);
}
/**
 * Convert image to texture, resizing if needed.
 * @param name Texture name (Irrlicht texture key).
 * @param srcimg Source image; will be dropped by this function.
 * @return Texture pointer. Remove via `driver->removeTexture` (do not `drop`).
 */
irr::video::ITexture* ImageManager::addTexture(const char* name, irr::video::IImage* srcimg, irr::s32 width, irr::s32 height) {
	if(srcimg == nullptr)
		return nullptr;
	irr::video::ITexture* texture;
	if(srcimg->getDimension() == irr::core::dimension2d<irr::u32>(width, height)) {
		texture = driver->addTexture(name, srcimg);
	} else {
		irr::video::IImage* destimg = driver->createImage(srcimg->getColorFormat(), irr::core::dimension2d<irr::u32>(width, height));
		resizeImage(srcimg, destimg, mainGame->gameConf.use_image_scale_multi_thread);
		texture = driver->addTexture(name, destimg);
		destimg->drop();
	}
	srcimg->drop();
	return texture;
}
/**
 * Load image from file and convert to texture.
 * @return Texture pointer. Remove via `driver->removeTexture` (do not `drop`).
 */
irr::video::ITexture* ImageManager::GetTextureFromFile(const char* file, irr::s32 width, irr::s32 height) {
	irr::video::IImage* img = driver->createImageFromFile(file);
	if(img == nullptr) {
		return nullptr;
	}
	char name[256];
	mysnprintf(name, "%s/%d_%d", file, width, height);
	return addTexture(name, img, width, height);
}
/**
 * Load card picture from `expansions` or `pics` folder.
 * Files in the expansions directory have priority, allowing custom pictures to be loaded without modifying the original files.
 * @return Image pointer. Must be dropped after use.
 */
irr::video::IImage* ImageManager::GetImage(int code) {
	char file[256];
	mysnprintf(file, "expansions/pics/%d.jpg", code);
	irr::video::IImage* img = driver->createImageFromFile(file);
	if(img == nullptr) {
		mysnprintf(file, "pics/%d.jpg", code);
		img = driver->createImageFromFile(file);
	}
	return img;
}
/**
 * Load card picture.
 * @return Texture pointer. Remove via `driver->removeTexture` (do not `drop`).
 */
irr::video::ITexture* ImageManager::GetTexture(int code, irr::s32 width, irr::s32 height) {
	irr::video::IImage* img = GetImage(code);
	if(img == nullptr) {
		return nullptr;
	}
	char name[256];
	mysnprintf(name, "pics/%d/%d_%d", code, width, height);
	return addTexture(name, img, width, height);
}
/**
 * Load managed card picture texture.
 * @param fit Resize to fit scale if true.
 * @return Texture pointer. Should NOT be removed nor dropped.
 */
irr::video::ITexture* ImageManager::GetTexture(int code, bool fit) {
	if(code == 0)
		return fit ? tUnknownFit : tUnknown;
	int width = CARD_IMG_WIDTH;
	int height = CARD_IMG_HEIGHT;
	if(fit) {
		float mul = mainGame->xScale;
		if(mainGame->xScale > mainGame->yScale)
			mul = mainGame->yScale;
		width = width * mul;
		height = height * mul;
	}
	auto tit = tMap[fit ? 1 : 0].find(code);
	if(tit == tMap[fit ? 1 : 0].end()) {
		irr::video::ITexture* texture = GetTexture(code, width, height);
		tMap[fit ? 1 : 0][code] = texture;
		return (texture == nullptr) ? (fit ? tUnknownFit : tUnknown) : texture;
	}
	if(tit->second)
		return tit->second;
	else
		return fit ? tUnknownFit : tUnknown;
}
/**
 * Load managed card picture texture with zoom.
 * @return Texture pointer. Should NOT be removed nor dropped.
 */
irr::video::ITexture* ImageManager::GetBigPicture(int code, float zoom) {
	if(code == 0)
		return tUnknownFit;
	if(tBigPicture != nullptr) {
		driver->removeTexture(tBigPicture);
		tBigPicture = nullptr;
	}
	irr::video::IImage* img = GetImage(code);
	if(img == nullptr) {
		return tUnknownFit;
	}
	char name[256];
	mysnprintf(name, "pics/%d/big", code);
	auto origsize = img->getDimension();
	tBigPicture = addTexture(name, img, origsize.Width * zoom, origsize.Height * zoom);
	return tBigPicture;
}
int ImageManager::LoadThumbThread() {
	while(true) {
		imageManager.tThumbLoadingMutex.lock();
		imageManager.tThumbLoadingThreadRunning = !imageManager.tThumbLoadingCodes.empty();
		if(!imageManager.tThumbLoadingThreadRunning) {
			imageManager.tThumbLoadingMutex.unlock();
			break;
		}
		int code = imageManager.tThumbLoadingCodes.front();
		imageManager.tThumbLoadingCodes.pop();
		imageManager.tThumbLoadingMutex.unlock();
		irr::video::IImage* img = imageManager.GetImage(code);
		if(img != nullptr) {
			int width = CARD_THUMB_WIDTH * mainGame->xScale;
			int height = CARD_THUMB_HEIGHT * mainGame->yScale;
			if(img->getDimension() == irr::core::dimension2d<irr::u32>(width, height)) {
				imageManager.tThumbLoadingMutex.lock();
				if(imageManager.tThumbLoadingThreadRunning)
					imageManager.tThumbLoading[code] = img;
				else
					img->drop();
				imageManager.tThumbLoadingMutex.unlock();
			} else {
				irr::video::IImage *destimg = imageManager.driver->createImage(img->getColorFormat(), irr::core::dimension2d<irr::u32>(width, height));
				imageManager.resizeImage(img, destimg, mainGame->gameConf.use_image_scale_multi_thread);
				img->drop();
				imageManager.tThumbLoadingMutex.lock();
				if(imageManager.tThumbLoadingThreadRunning)
					imageManager.tThumbLoading[code] = destimg;
				else
					destimg->drop();
				imageManager.tThumbLoadingMutex.unlock();
			}
		} else {
			imageManager.tThumbLoadingMutex.lock();
			if(imageManager.tThumbLoadingThreadRunning)
				imageManager.tThumbLoading[code] = nullptr;
			imageManager.tThumbLoadingMutex.unlock();
		}
	}
	return 0;
}
/**
 * Load managed card thumbnail texture.
 * @return Texture pointer. Should NOT be removed nor dropped.
 */
irr::video::ITexture* ImageManager::GetTextureThumb(int code) {
	if(code == 0)
		return tUnknownThumb;
	auto tit = tThumb.find(code);
	if(tit == tThumb.end() && !mainGame->gameConf.use_image_load_background_thread) {
		int width = CARD_THUMB_WIDTH * mainGame->xScale;
		int height = CARD_THUMB_HEIGHT * mainGame->yScale;
		irr::video::ITexture* texture = GetTexture(code, width, height);
		tThumb[code] = texture;
		return (texture == nullptr) ? tUnknownThumb : texture;
	}
	if(tit == tThumb.end() || tit->second == tLoading) {
		imageManager.tThumbLoadingMutex.lock();
		auto lit = tThumbLoading.find(code);
		if(lit != tThumbLoading.end()) {
			if(lit->second != nullptr) {
				char textureName[256];
				mysnprintf(textureName, "pics/%d/thumbnail", code);
				irr::video::ITexture* texture = driver->addTexture(textureName, lit->second); // textures must be added in the main thread due to OpenGL
				lit->second->drop();
				tThumb[code] = texture;
			} else {
				tThumb[code] = nullptr;
			}
			tThumbLoading.erase(lit);
		}
		imageManager.tThumbLoadingMutex.unlock();
		tit = tThumb.find(code);
	}
	if(tit == tThumb.end()) {
		tThumb[code] = tLoading;
		imageManager.tThumbLoadingMutex.lock();
		tThumbLoadingCodes.push(code);
		if(!tThumbLoadingThreadRunning) {
			tThumbLoadingThreadRunning = true;
			std::thread(LoadThumbThread).detach();
		}
		imageManager.tThumbLoadingMutex.unlock();
		return tLoading;
	}
	if(tit->second)
		return tit->second;
	else
		return tUnknownThumb;
}
/**
 * Load managed duel field texture.
 * @return Texture pointer. Should NOT be removed nor dropped.
 */
irr::video::ITexture* ImageManager::GetTextureField(int code) {
	if(code == 0)
		return nullptr;
	auto tit = tFields.find(code);
	if(tit == tFields.end()) {
		irr::s32 width = 512 * mainGame->xScale;
		irr::s32 height = 512 * mainGame->yScale;
		char file[256];
		mysnprintf(file, "expansions/pics/field/%d.png", code);
		irr::video::ITexture* img = GetTextureFromFile(file, width, height);
		if(img == nullptr) {
			mysnprintf(file, "expansions/pics/field/%d.jpg", code);
			img = GetTextureFromFile(file, width, height);
		}
		if(img == nullptr) {
			mysnprintf(file, "pics/field/%d.png", code);
			img = GetTextureFromFile(file, width, height);
		}
		if(img == nullptr) {
			mysnprintf(file, "pics/field/%d.jpg", code);
			img = GetTextureFromFile(file, width, height);
			if(img == nullptr) {
				tFields[code] = nullptr;
				return nullptr;
			} else {
				tFields[code] = img;
				return img;
			}
		} else {
			tFields[code] = img;
			return img;
		}
	}
	if(tit->second)
		return tit->second;
	else
		return nullptr;
}
// 添加获取表情图的函数
irr::video::ITexture* ImageManager::GetEmoticon(const std::wstring& emoticonName) {
    auto it = emoticons.find(emoticonName);
    if (it != emoticons.end()) {
        return it->second;
    }
    return nullptr;
}

// 添加清理表情图的函数
void ImageManager::ClearEmoticons() {
    for (auto& pair : emoticons) {
        if (pair.second) {
            driver->removeTexture(pair.second);
        }
    }
    emoticons.clear();
}
}
