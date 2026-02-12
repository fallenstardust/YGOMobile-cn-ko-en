#ifndef IMAGEMANAGER_H
#define IMAGEMANAGER_H

#include "config.h"
#include "data_manager.h"
#include <unordered_map>
#include <queue>
#include <mutex>

namespace ygo {

class ImageManager {
private:
	void resizeImage(irr::video::IImage* src, irr::video::IImage* dest, bool use_threading);
	irr::video::ITexture* addTexture(const char* name, irr::video::IImage* srcimg, irr::s32 width, irr::s32 height);
public:
	bool Initial(const irr::io::path dir);
	void SetDevice(irr::IrrlichtDevice* dev);
	void ClearTexture();
	void ResizeTexture(const irr::io::path dir);
	irr::video::ITexture* GetTextureFromFile(const char* file, irr::s32 width, irr::s32 height);
	irr::video::IImage* GetImage(int code);
	irr::video::ITexture* GetTexture(int code, irr::s32 width, irr::s32 height);
	irr::video::ITexture* GetTexture(int code, bool fit = false);
	irr::video::ITexture* GetBigPicture(int code, float zoom);
	irr::video::ITexture* GetTextureThumb(int code);
	irr::video::ITexture* GetTextureField(int code);
	static int LoadThumbThread();

	std::unordered_map<int, irr::video::ITexture*> tMap[2];
	std::unordered_map<int, irr::video::ITexture*> tThumb;
	std::unordered_map<int, irr::video::ITexture*> tFields;
	std::unordered_map<int, irr::video::IImage*> tThumbLoading;
	std::queue<int> tThumbLoadingCodes;
	std::mutex tThumbLoadingMutex;
	bool tThumbLoadingThreadRunning;
	irr::IrrlichtDevice* device;
	irr::video::IVideoDriver* driver;
	irr::video::ITexture* tCover[4];
	irr::video::ITexture* tUnknown;
	irr::video::ITexture* tUnknownFit;
	irr::video::ITexture* tUnknownThumb;
	irr::video::ITexture* tBigPicture;
	irr::video::ITexture* tLoading;
	irr::video::ITexture* tAct;
	irr::video::ITexture* tAttack;
	irr::video::ITexture* tNegated;
	irr::video::ITexture* tChain;
	irr::video::ITexture* tNumber;
	irr::video::ITexture* tLPFrame;
	irr::video::ITexture* tLPBar;
	irr::video::ITexture* tMask;
	irr::video::ITexture* tEquip;
	irr::video::ITexture* tTarget;
	irr::video::ITexture* tChainTarget;
	irr::video::ITexture* tLim;
	irr::video::ITexture* tOT;
	irr::video::ITexture* tHand[3];
	irr::video::ITexture* tBackGround;
	irr::video::ITexture* tBackGround_menu;
	irr::video::ITexture* tBackGround_deck;
	irr::video::ITexture* tField[2];
	irr::video::ITexture* tFieldTransparent[2];
	path image_work_path;
    std::unordered_map<std::wstring, irr::core::recti> emoticonRects;  // 存储每个表情的位置
    std::unordered_map<std::wstring, irr::video::ITexture*> emoticons;
    irr::video::ITexture* GetEmoticon(const std::wstring& emoticonName);
    // 添加全局数组
    const wchar_t* emoticonCodes[16] = {
            L"&laugh",      // 大笑
            L"&ridiculous", // 滑稽
            L"&stick_tongue",// 吐舌
            L"&reluctant",  // 勉强
            L"&sweat",     // 流汗
            L"&confused",   // 疑问
            L"&surprised",  // 惊讶
            L"&bawl",      // 大哭
            L"&angry",     // 生气
            L"&rage",      // 发怒
            L"&sneaky",    // 阴险
            L"&obedient",  // 喷水
            L"&good",      // 点赞
            L"&cool",      // 酷
            L"&despise",   // 鄙视
            L"&shy"        // 害羞
    };
    void ClearEmoticons();
    irr::video::ITexture* tEmoticons;
    irr::video::ITexture* tEmoticon;
	irr::video::ITexture* tTotalAtk;
	irr::video::ITexture* tSelField;
	irr::video::ITexture* tSelFieldLinkArrows[10];
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
};

extern ImageManager imageManager;

}

#endif // IMAGEMANAGER_H
