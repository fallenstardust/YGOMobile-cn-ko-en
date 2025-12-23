#ifndef IMAGEMANAGER_H
#define IMAGEMANAGER_H

#include "config.h"
#include "data_manager.h"
#include <unordered_map>
#include <queue>
#include <mutex>
#include <list>
#include <string>

namespace ygo {

class ImageManager {
public:
	bool Initial(const irr::io::path dir);
	void SetDevice(irr::IrrlichtDevice* dev);
	void ClearTexture();
	void RemoveTexture(int code);
//	irr::video::ITexture* GetTextureFromFile(char* file, s32 width, s32 height);
	irr::video::ITexture* GetTexture(int code);
	irr::video::ITexture* GetBigPicture(int code, float zoom);
	irr::video::ITexture* GetTextureThumb(int code);
	irr::video::ITexture* GetTextureField(int code);
	path image_work_path;

	std::unordered_map<int, irr::video::ITexture*> tMap;
	std::unordered_map<int, irr::video::ITexture*> tThumb;
	std::unordered_map<int, irr::video::ITexture*> tFields;
	irr::IrrlichtDevice* device;
	irr::video::IVideoDriver* driver;
	irr::video::ITexture* tCover[4];
	irr::video::ITexture* tBigPicture;
	irr::video::ITexture* tUnknown;
	irr::video::ITexture* tAct;
	irr::video::ITexture* tAttack;
	irr::video::ITexture* tTotalAtk;
	irr::video::ITexture* tNegated;
	irr::video::ITexture* tChain;
	irr::video::ITexture* tSelField;
	irr::video::ITexture* tSelFieldLinkArrows[10];
	irr::video::ITexture* tNumber;
	irr::video::ITexture* tLPFrame;
	irr::video::ITexture* tLPBar;
	irr::video::ITexture* tMask;
	irr::video::ITexture* tEquip;
	irr::video::ITexture* tTarget;
	irr::video::ITexture* tChainTarget;
	irr::video::ITexture* tLim;
    irr::video::ITexture* tLimCredit;
	irr::video::ITexture* tOT;
	irr::video::ITexture* tHand[3];
	irr::video::ITexture* tBackGround;
	irr::video::ITexture* tBackGround_menu;
	irr::video::ITexture* tBackGround_deck;
	irr::video::ITexture* tField[2];
	irr::video::ITexture* tFieldTransparent[2];
	irr::video::ITexture* tRScale[14];
	irr::video::ITexture* tLScale[14];
	irr::video::ITexture* tGSC;
	irr::video::ITexture* tClock;
	irr::video::ITexture* tCardType;
	irr::video::ITexture* tAvatar[2];
	irr::video::ITexture* tLPBarFrame;
	irr::video::ITexture* tSettings;
    irr::video::ITexture* tLogs;
    irr::video::ITexture* tMute;
    irr::video::ITexture* tPlay;
	irr::video::ITexture* tTalk;
	irr::video::ITexture* tOneX;
	irr::video::ITexture* tDoubleX;
	irr::video::ITexture* tShut;
	irr::video::ITexture* tClose;
    irr::video::ITexture* tTitleBar;
	irr::video::ITexture* tWindow;
	irr::video::ITexture* tWindow_V;
	irr::video::ITexture* tDialog_L;
	irr::video::ITexture* tDialog_S;
	irr::video::ITexture* tButton_L;
	irr::video::ITexture* tButton_L_pressed;
	irr::video::ITexture* tButton_S;
	irr::video::ITexture* tButton_S_pressed;
	irr::video::ITexture* tButton_C;
	irr::video::ITexture* tButton_C_pressed;
    std::list<std::string> support_types;
};

extern ImageManager imageManager;

}

#endif // IMAGEMANAGER_H
