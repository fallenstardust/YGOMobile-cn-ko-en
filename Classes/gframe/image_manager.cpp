#include "image_manager.h"
#include "game.h"

namespace ygo {

ImageManager imageManager;

bool ImageManager::Initial(const path dir) {
	tCover[0] = driver->getTexture((dir + path("/textures/cover.jpg")).c_str());
	tCover[1] = driver->getTexture((dir + path("/textures/cover2.jpg")).c_str());
	if(!tCover[1])
		tCover[1] = tCover[0];
	tUnknown = driver->getTexture((dir + path("/textures/unknown.jpg")).c_str());
	tAct = driver->getTexture((dir + path("/textures/act.png")).c_str());
	tAttack = driver->getTexture((dir + path("/textures/attack.png")).c_str());
	tChain = driver->getTexture((dir + path("/textures/chain.png")).c_str());
	tNegated = driver->getTexture((dir + path("/textures/negated.png")).c_str());
	tNumber = driver->getTexture((dir + path("/textures/number.png")).c_str());
	tLPBar = driver->getTexture((dir + path("/textures/lp2.png")).c_str());
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
	for(auto tit = tMap.begin(); tit != tMap.end(); ++tit) {
		if(tit->second)
			driver->removeTexture(tit->second);
	}
	for(auto tit = tThumb.begin(); tit != tThumb.end(); ++tit) {
		if(tit->second)
			driver->removeTexture(tit->second);
	}
	tMap.clear();
	tThumb.clear();
}
void ImageManager::RemoveTexture(int code) {
	auto tit = tMap.find(code);
	if(tit != tMap.end()) {
		if(tit->second)
			driver->removeTexture(tit->second);
		tMap.erase(tit);
	}
}/*
// function by Warr1024, from https://github.com/minetest/minetest/issues/2419 , modified
void imageScaleNNAA(irr::video::IImage *src, irr::video::IImage *dest) {
	double sx, sy, minsx, maxsx, minsy, maxsy, area, ra, ga, ba, aa, pw, ph, pa;
	u32 dy, dx;
	irr::video::SColor pxl;

	// Cache rectsngle boundaries.
	double sw = src->getDimension().Width * 1.0;
	double sh = src->getDimension().Height * 1.0;

	// Walk each destination image pixel.
	// Note: loop y around x for better cache locality.
	irr::core::dimension2d<u32> dim = dest->getDimension();
	for(dy = 0; dy < dim.Height; dy++)
		for(dx = 0; dx < dim.Width; dx++) {

			// Calculate floating-point source rectangle bounds.
			minsx = dx * sw / dim.Width;
			maxsx = minsx + sw / dim.Width;
			minsy = dy * sh / dim.Height;
			maxsy = minsy + sh / dim.Height;

			// Total area, and integral of r, g, b values over that area,
			// initialized to zero, to be summed up in next loops.
			area = 0;
			ra = 0;
			ga = 0;
			ba = 0;
			aa = 0;

			// Loop over the integral pixel positions described by those bounds.
			for(sy = floor(minsy); sy < maxsy; sy++)
				for(sx = floor(minsx); sx < maxsx; sx++) {

					// Calculate width, height, then area of dest pixel
					// that's covered by this source pixel.
					pw = 1;
					if(minsx > sx)
						pw += sx - minsx;
					if(maxsx < (sx + 1))
						pw += maxsx - sx - 1;
					ph = 1;
					if(minsy > sy)
						ph += sy - minsy;
					if(maxsy < (sy + 1))
						ph += maxsy - sy - 1;
					pa = pw * ph;

					// Get source pixel and add it to totals, weighted
					// by covered area and alpha.
					pxl = src->getPixel((u32)sx, (u32)sy);
					area += pa;
					ra += pa * pxl.getRed();
					ga += pa * pxl.getGreen();
					ba += pa * pxl.getBlue();
					aa += pa * pxl.getAlpha();
				}

			// Set the destination image pixel to the average color.
			if(area > 0) {
				pxl.setRed(ra / area + 0.5);
				pxl.setGreen(ga / area + 0.5);
				pxl.setBlue(ba / area + 0.5);
				pxl.setAlpha(aa / area + 0.5);
			} else {
				pxl.setRed(0);
				pxl.setGreen(0);
				pxl.setBlue(0);
				pxl.setAlpha(0);
			}
			dest->setPixel(dx, dy, pxl);
		}
}
irr::video::ITexture* ImageManager::GetTextureFromFile(char* file, s32 width, s32 height) {
	//if(mainGame->gameConf.use_image_scale) {
		irr::video::ITexture* texture;
		irr::video::IImage* srcimg = driver->createImageFromFile(file);
		if(srcimg == NULL)
			return NULL;
		if(srcimg->getDimension() == irr::core::dimension2d<u32>(width, height)) {
			texture = driver->addTexture(file, srcimg);
		} else {
			video::IImage *destimg = driver->createImage(srcimg->getColorFormat(), irr::core::dimension2d<u32>(width, height));
			imageScaleNNAA(srcimg, destimg);
			texture = driver->addTexture(file, destimg);
			destimg->drop();
		}
		srcimg->drop();
		return texture;
	//} else {
	//	return driver->getTexture(file);
	//}
}*/
irr::video::ITexture* ImageManager::GetTexture(int code) {
	if(code == 0)
		return tUnknown;
//	int width = CARD_IMG_WIDTH;
//	int height = CARD_IMG_HEIGHT;
	auto tit = tMap.find(code);
	if(tit == tMap.end()) {
		char file[256];
//		char file_img[256];
		sprintf(file, "expansions/pics/%d.jpg", code);
		irr::video::ITexture* img = NULL;
		std::list<std::string>::iterator iter;
		for (iter = support_types.begin(); iter != support_types.end(); ++iter) {	
			sprintf(file, "/expansions/pics/%d.%s", code, iter->c_str());
			img = driver->getTexture(image_work_path + path(file));
//			sprintf(file_img, "%s", (image_work_path + path(file)).c_str());
//			img = GetTextureFromFile(file_img, width, height);
			if (img != NULL) {
				break;
			}
		}
		if(img == NULL) {
			for (iter = support_types.begin(); iter != support_types.end(); ++iter) {
				sprintf(file, "%s/%d.%s", irr::android::getCardImagePath(mainGame->appMain).c_str(), code, iter->c_str());
				img = driver->getTexture(file);
//				img = GetTextureFromFile(file, width, height);
				if (img != NULL) {
					break;
				}
			}
		}
		if(img == NULL){//sdcard first, then zip
			for (iter = support_types.begin(); iter != support_types.end(); ++iter) {
				sprintf(file, "pics/%d.%s", code, iter->c_str());
				//load image in zip
				irr::io::IReadFile* in_zip_file = device->getFileSystem()->createAndOpenFile(file);
				if (in_zip_file && in_zip_file->getSize() > 0) {
					img = driver->getTexture(in_zip_file);
					if (img != NULL) {
						break;
					}
				}
			}
		}
		if(img == NULL) {
			tMap[code] = NULL;
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
irr::video::ITexture* ImageManager::GetTextureThumb(int code) {
	return tUnknown;
/*
	if(code == 0)
		return tUnknown;
	auto tit = tThumb.find(code);
	if(tit == tThumb.end()) {
		char file[256];
		sprintf(file, "%s/%d.jpg", irr::android::getCardImagePath(mainGame->appMain).c_str(), code);
		irr::video::ITexture* img = driver->getTexture(file);
		if(img == NULL) {
			tThumb[code] = NULL;
			return tUnknown;
		} else {
			tThumb[code] = img;
			return img;
		}
	}
	if(tit->second)
		return tit->second;
	else
		return tUnknown;
*/
}
irr::video::ITexture* ImageManager::GetTextureField(int code) {
	if(code == 0)
		return NULL;
	auto tit = tFields.find(code);
	if(tit == tFields.end()) {
		char file[256];
		sprintf(file, "field/%s/%d.jpg", irr::android::getCardImagePath(mainGame->appMain).c_str(), code);
		irr::video::ITexture* img = driver->getTexture(file);
//		irr::video::ITexture* img = GetTextureFromFile(file, 512, 512);
		if(img == NULL) {
			sprintf(file, "field/%s/%d.jpg", irr::android::getCardImagePath(mainGame->appMain).c_str(), code);
			img = driver->getTexture(file);
//			img = GetTextureFromFile(file, 512, 512);
		}
		if(img == NULL) {
			sprintf(file,  "field/%s/%d.png", irr::android::getCardImagePath(mainGame->appMain).c_str(), code);
			img = driver->getTexture(file);
//			img = GetTextureFromFile(file, 512, 512);
		}
		if(img == NULL) {
			sprintf(file, "pics/field/%d.jpg", code);
			img = driver->getTexture(file);
//			img = GetTextureFromFile(file, 512, 512);
			if(img == NULL) {
				tFields[code] = NULL;
				return NULL;
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
		return NULL;
}
}
