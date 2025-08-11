#include "image_manager.h"
#include "game.h"
#include <thread>

namespace ygo {

ImageManager imageManager;

bool ImageManager::Initial(const path dir) {
	tCover[0] = driver->getTexture((dir + path("/textures/cover.jpg")).c_str());
	tCover[1] = driver->getTexture((dir + path("/textures/cover2.jpg")).c_str());
	if(!tCover[1])
		tCover[1] = tCover[0];
	tUnknown = driver->getTexture((dir + path("/textures/unknown.jpg")).c_str());
	tBigPicture = nullptr;
	tAct = driver->getTexture((dir + path("/textures/act.png")).c_str());
	tAttack = driver->getTexture((dir + path("/textures/attack.png")).c_str());
	tTotalAtk = driver->getTexture((dir + path("/textures/totalAtk.png")).c_str());
	tChain = driver->getTexture((dir + path("/textures/chain.png")).c_str());
	tNegated = driver->getTexture((dir + path("/textures/negated.png")).c_str());
	tSelField = driver->getTexture((dir + path("/textures/selfield.png")).c_str());
	tSelFieldLinkArrows[1] = driver->getTexture((dir + path("/textures/link_marker_on_1.png")).c_str());
	tSelFieldLinkArrows[2] = driver->getTexture((dir + path("/textures/link_marker_on_2.png")).c_str());
	tSelFieldLinkArrows[3] = driver->getTexture((dir + path("/textures/link_marker_on_3.png")).c_str());
	tSelFieldLinkArrows[4] = driver->getTexture((dir + path("/textures/link_marker_on_4.png")).c_str());
	tSelFieldLinkArrows[6] = driver->getTexture((dir + path("/textures/link_marker_on_6.png")).c_str());
	tSelFieldLinkArrows[7] = driver->getTexture((dir + path("/textures/link_marker_on_7.png")).c_str());
	tSelFieldLinkArrows[8] = driver->getTexture((dir + path("/textures/link_marker_on_8.png")).c_str());
	tSelFieldLinkArrows[9] = driver->getTexture((dir + path("/textures/link_marker_on_9.png")).c_str());
	tNumber = driver->getTexture((dir + path("/textures/number.png")).c_str());
	tLPBar = driver->getTexture((dir + path("/textures/lp3.png")).c_str());
	tLPFrame = driver->getTexture((dir + path("/textures/lpf.png")).c_str());
	tMask = driver->getTexture((dir + path("/textures/mask.png")).c_str());
	tEquip = driver->getTexture((dir + path("/textures/equip.png")).c_str());
	tTarget = driver->getTexture((dir + path("/textures/target.png")).c_str());
	tChainTarget = driver->getTexture((dir + path("/textures/chaintarget.png")).c_str());
	tLim = driver->getTexture((dir + path("/textures/lim.png")).c_str());
	tOT = driver->getTexture((dir + path("/textures/ot.png")).c_str());
	tHand[0] = driver->getTexture((dir + path("/textures/f1.jpg")).c_str());
	tHand[1] = driver->getTexture((dir + path("/textures/f2.jpg")).c_str());
	tHand[2] = driver->getTexture((dir + path("/textures/f3.jpg")).c_str());
	tBackGround = driver->getTexture((dir + path("/textures/bg.jpg")).c_str());
	tBackGround_menu = driver->getTexture((dir + path("/textures/bg_menu.jpg")).c_str());
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
    tTitleBar = driver->getTexture((dir + path("/textures/extra/stitlebar.png")).c_str());
    tWindow = driver->getTexture((dir + path("/textures/extra/sWindow.png")).c_str());
    tWindow_V = driver->getTexture((dir + path("/textures/extra/sWindow_V.png")).c_str());
	tDialog_S = driver->getTexture((dir + path("/textures/extra/sDialog_S.png")).c_str());
	tDialog_L = driver->getTexture((dir + path("/textures/extra/sDialog_L.png")).c_str());
	tButton_L = driver->getTexture((dir + path("/textures/extra/sButton_L.png")).c_str());
	tButton_L_pressed = driver->getTexture((dir + path("/textures/extra/sButton_L_pressed.png")).c_str());
	tButton_S = driver->getTexture((dir + path("/textures/extra/sButton_S.png")).c_str());
	tButton_S_pressed = driver->getTexture((dir + path("/textures/extra/sButton_S_pressed.png")).c_str());
	tButton_C = driver->getTexture((dir + path("/textures/extra/sButton_C.png")).c_str());
	tButton_C_pressed = driver->getTexture((dir + path("/textures/extra/sButton_C_pressed.png")).c_str());

    if(!tBackGround_menu)
		tBackGround_menu = tBackGround;
	tBackGround_deck = driver->getTexture((dir + path("/textures/bg_deck.jpg")).c_str());
	if(!tBackGround_deck)
		tBackGround_deck = tBackGround;
	tField[0] = driver->getTexture((dir + path("/textures/field2.png")).c_str());
	tFieldTransparent[0] = driver->getTexture((dir + path("/textures/field-transparent2.png")).c_str());
	tField[1] = driver->getTexture((dir + path("/textures/field3.png")).c_str());
	tFieldTransparent[1] = driver->getTexture((dir + path("/textures/field-transparent3.png")).c_str());
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
	tClock = driver->getTexture((dir + path("/textures/tiktok.png")).c_str());
	support_types.push_back(std::string("jpg"));
	support_types.push_back(std::string("png"));
	support_types.push_back(std::string("bpg"));
	image_work_path = dir;
	return true;
}
void ImageManager::SetDevice(irr::IrrlichtDevice* dev) {
	device = dev;
	driver = dev->getVideoDriver();
}
void ImageManager::ClearTexture() {
	for(auto & tit : tMap) {
		if(tit.second)
			driver->removeTexture(tit.second);
	}
	for(auto & tit : tThumb) {
		if(tit.second)
			driver->removeTexture(tit.second);
	}
	for(auto & field : tFields) {
		if(field.second)
			driver->removeTexture(field.second);
	}
	tMap.clear();
	tThumb.clear();
	tFields.clear();
	if(tBigPicture != nullptr) {
		driver->removeTexture(tBigPicture);
		tBigPicture = nullptr;
	}
}
void ImageManager::RemoveTexture(int code) {
	auto tit = tMap.find(code);
	if(tit != tMap.end()) {
		if(tit->second)
			driver->removeTexture(tit->second);
		tMap.erase(tit);
	}
}
irr::video::ITexture* ImageManager::GetTexture(int code) {
	if(code == 0)
		return tUnknown;
//	int width = CARD_IMG_WIDTH;
//	int height = CARD_IMG_HEIGHT;
	auto tit = tMap.find(code);
	if(tit == tMap.end()) {
		char file[256];
//		char file_img[256];
		snprintf(file, sizeof file, "expansions/pics/%d.jpg", code);
		irr::video::ITexture* img = nullptr;
		std::list<std::string>::iterator iter;
		for (iter = support_types.begin(); iter != support_types.end(); ++iter) {	
			snprintf(file, sizeof file, "/expansions/pics/%d.%s", code, iter->c_str());
			img = driver->getTexture(image_work_path + path(file));
//			sprintf(file_img, "%s", (image_work_path + path(file)).c_str());
//			img = GetTextureFromFile(file_img, width, height);
			if (img != nullptr) {
				break;
			}
		}
		if(img == nullptr) {
			for (iter = support_types.begin(); iter != support_types.end(); ++iter) {
				snprintf(file, sizeof file, "%s/%d.%s", irr::android::getCardImagePath(mainGame->appMain).c_str(), code, iter->c_str());
				img = driver->getTexture(file);
//				img = GetTextureFromFile(file, width, height);
				if (img != nullptr) {
					break;
				}
			}
		}
		if(img == nullptr){//sdcard first, then zip
			for (iter = support_types.begin(); iter != support_types.end(); ++iter) {
				snprintf(file, sizeof file, "pics/%d.%s", code, iter->c_str());
				//load image in zip
				irr::io::IReadFile* in_zip_file = device->getFileSystem()->createAndOpenFile(file);
				if (in_zip_file && in_zip_file->getSize() > 0) {
					img = driver->getTexture(in_zip_file);
					if (img != nullptr) {
						break;
					}
				}
			}
		}
		if(img == nullptr) {
			tMap[code] = nullptr;
			return GetTextureThumb(code);
		} else {
			tMap[code] = img;
			return img;
		}
	}
	if(tit->second)
		return tit->second;
	else
		return GetTextureThumb(code);
}
irr::video::ITexture* ImageManager::GetBigPicture(int code, float zoom) {
	if(code == 0)
		return tUnknown;
	if(tBigPicture != nullptr) {
		driver->removeTexture(tBigPicture);
		tBigPicture = nullptr;
	}
	irr::video::ITexture* texture;
	char file[256];
	mysnprintf(file, "expansions/pics/%d.jpg", code);
	irr::video::IImage* srcimg = driver->createImageFromFile(file);
	if(srcimg == nullptr) {
		mysnprintf(file, "pics/%d.jpg", code);
		srcimg = driver->createImageFromFile(file);
	}
	if(srcimg == nullptr) {
		return tUnknown;
	}
	if(zoom == 1) {
		texture = driver->addTexture(file, srcimg);
	} else {
		auto origsize = srcimg->getDimension();
		irr::video::IImage* destimg = driver->createImage(srcimg->getColorFormat(), irr::core::dimension2d<irr::u32>(origsize.Width * zoom, origsize.Height * zoom));
		//imageScaleNNAA(srcimg, destimg);
		texture = driver->addTexture(file, destimg);
		destimg->drop();
	}
	srcimg->drop();
	tBigPicture = texture;
	return texture;
}
irr::video::ITexture* ImageManager::GetTextureThumb(int code) {
	return tUnknown;
}
irr::video::ITexture* ImageManager::GetTextureField(int code) {
	if(code == 0)
		return nullptr;
	auto tit = tFields.find(code);
	if(tit == tFields.end()) {
		char file[256];
		mysnprintf(file, "field/%s/%d.jpg", irr::android::getCardImagePath(mainGame->appMain).c_str(), code);
		irr::video::ITexture* img = driver->getTexture(file);
		if(img == nullptr) {
			mysnprintf(file, "field/%s/%d.jpg", irr::android::getCardImagePath(mainGame->appMain).c_str(), code);
			img = driver->getTexture(file);
		}
		if(img == nullptr) {
			mysnprintf(file, "field/%s/%d.png", irr::android::getCardImagePath(mainGame->appMain).c_str(), code);
			img = driver->getTexture(file);
		}
		if(img == nullptr) {
			mysnprintf(file, "pics/field/%d.jpg", code);
			img = driver->getTexture(file);
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
}
