#include "config.h"
#include "game.h"
#include "myfilesystem.h"
#include "image_manager.h"
#include "data_manager.h"
#include "deck_manager.h"
#include "replay.h"
#include "materials.h"
#include "duelclient.h"
#include "netserver.h"
#include "single_mode.h"
#include <thread>
#ifdef _IRR_ANDROID_PLATFORM_
#include <android/CAndroidGUIEditBox.h>
#include <android/CAndroidGUIComboBox.h>
#include <android/CAndroidGUISkin.h>
#include <Android/CIrrDeviceAndroid.h>
#include <COGLES2ExtensionHandler.h>
#include <COGLESExtensionHandler.h>
#include <COGLES2Driver.h>
#include <COGLESDriver.h>
#endif

const unsigned short PRO_VERSION = 0x1361;

namespace ygo {

Game* mainGame;

void DuelInfo::Clear() {
	isStarted = false;
	isInDuel = false;
	isFinished = false;
	isReplay = false;
	isReplaySkiping = false;
	isFirst = false;
	isTag = false;
	isSingleMode = false;
	is_shuffling = false;
	tag_player[0] = false;
	tag_player[1] = false;
	isReplaySwapped = false;
	lp[0] = 0;
	lp[1] = 0;
	start_lp = 0;
	duel_rule = 0;
	turn = 0;
	curMsg = 0;
	hostname[0] = 0;
	clientname[0] = 0;
	hostname_tag[0] = 0;
	clientname_tag[0] = 0;
	strLP[0][0] = 0;
	strLP[1][0] = 0;
	player_type = 0;
	time_player = 0;
	time_limit = 0;
	time_left[0] = 0;
	time_left[1] = 0;
}

bool IsExtension(const wchar_t* filename, const wchar_t* extension) {
	auto flen = std::wcslen(filename);
	auto elen = std::wcslen(extension);
	if (!elen || flen < elen)
		return false;
	return !wcsncasecmp(filename + (flen - elen), extension, elen);
}

void Game::process(irr::SEvent &event) {
	if (event.EventType == EET_MOUSE_INPUT_EVENT) {
		s32 x = event.MouseInput.X;
		s32 y = event.MouseInput.Y;
		event.MouseInput.X = optX(x);
		event.MouseInput.Y = optY(y);
	}
}

void Game::stopBGM() {
    ALOGD("stop bgm");
	gMutex.lock();
	soundManager->StopBGM();
	gMutex.unlock();
}

void Game::playBGM() {
 //   ALOGV("play bgm dInfo.isStarted=%d, is_building=%d", dInfo.isStarted, is_building);
	gMutex.lock();
	if(dInfo.isStarted) {
		if(dInfo.isFinished && showcardcode == 1)
			soundManager->PlayBGM(SoundManager::BGM::WIN);
		else if(dInfo.isFinished && (showcardcode == 2 || showcardcode == 3))
			soundManager->PlayBGM(SoundManager::BGM::LOSE);
		else if(dInfo.lp[0] > 0 && dInfo.lp[0] <= dInfo.lp[1] / 2)
			soundManager->PlayBGM(SoundManager::BGM::DISADVANTAGE);
		else if(dInfo.lp[0] > 0 && dInfo.lp[0] >= dInfo.lp[1] * 2)
			soundManager->PlayBGM(SoundManager::BGM::ADVANTAGE);
		else
			soundManager->PlayBGM(SoundManager::BGM::DUEL);
	} else if(is_building) {
		soundManager->PlayBGM(SoundManager::BGM::DECK);
	} else {
		soundManager->PlayBGM(SoundManager::BGM::MENU);
	}
	gMutex.unlock();
}

void Game::onHandleAndroidCommand(ANDROID_APP app, int32_t cmd){
    switch (cmd)
    {
        case APP_CMD_PAUSE:
            ALOGD("APP_CMD_PAUSE");
            if(ygo::mainGame != nullptr){
                ygo::mainGame->stopBGM();
            }
            break;
        case APP_CMD_RESUME:
        	//第一次不一定调用
			ALOGD("APP_CMD_RESUME");
			if(ygo::mainGame != nullptr){
                ygo::mainGame->playBGM();
			}
            break;
		case APP_CMD_STOP:
        case APP_CMD_INIT_WINDOW:
        case APP_CMD_SAVE_STATE:
        case APP_CMD_TERM_WINDOW:
        case APP_CMD_GAINED_FOCUS:
        case APP_CMD_LOST_FOCUS:
        case APP_CMD_DESTROY:
        case APP_CMD_WINDOW_RESIZED:
        default:
            break;
    }
}
bool Game::Initialize(ANDROID_APP app, android::InitOptions *options) {
	this->appMain = app;
	srand(time(0));
	irr::SIrrlichtCreationParameters params = irr::SIrrlichtCreationParameters();

#ifdef _IRR_ANDROID_PLATFORM_
	glversion = options->getOpenglVersion();
	if (glversion == 0) {
		params.DriverType = irr::video::EDT_OGLES1;
	} else{
		params.DriverType = irr::video::EDT_OGLES2;
	}
	params.PrivateData = app;
	params.Bits = 24;
	params.ZBufferBits = 16;
	params.AntiAlias  = 0;
	params.WindowSize = irr::core::dimension2d<u32>(0, 0);

	device = irr::createDeviceEx(params);
	if(!device)
		return false;
	if (!android::perfromTrick(app)) {
		return false;
	}
	core::position2di appPosition = android::initJavaBridge(app, device);
	setPositionFix(appPosition);
	device->setProcessReceiver(this);

	app->onInputEvent = android::handleInput;
	ILogger* logger = device->getLogger();
//	logger->setLogLevel(ELL_WARNING);
	isPSEnabled = options->isPendulumScaleEnabled();

	dataManager.FileSystem = device->getFileSystem();
    ((CIrrDeviceAndroid*)device)->onAppCmd = onHandleAndroidCommand;

	xScale = android::getXScale(app);
	yScale = android::getYScale(app);

	ALOGD("xScale = %f, yScale = %f", xScale, yScale);
	//io::path databaseDir = options->getDBDir();
	io::path workingDir = options->getWorkDir();
    ALOGD("workingDir= %s", workingDir.c_str());
	dataManager.FileSystem->changeWorkingDirectoryTo(workingDir);

	/* Your media must be somewhere inside the assets folder. The assets folder is the root for the file system.
	 This example copies the media in the Android.mk makefile. */
	stringc mediaPath = "media/";

	// The Android assets file-system does not know which sub-directories it has (blame google).
	// So we have to add all sub-directories in assets manually. Otherwise we could still open the files,
	// but existFile checks will fail (which are for example needed by getFont).
	for ( u32 i=0; i < dataManager.FileSystem->getFileArchiveCount(); ++i )
	{
		IFileArchive* archive = dataManager.FileSystem->getFileArchive(i);
		if ( archive->getType() == EFAT_ANDROID_ASSET )
		{
			archive->addDirectoryToFileList(mediaPath);
			break;
		}
	}
	//pics.zip, scripts.zip, ...zip
	io::path* zips = options->getArchiveFiles();
	int len = options->getArchiveCount();
	for(int i=0;i<len;i++){
		io::path zip_path = zips[i];
		if(dataManager.FileSystem->addFileArchive(zip_path.c_str(), false, false, EFAT_ZIP)) {
		    ALOGD("add arrchive ok:%s", zip_path.c_str());
	    }else{
			ALOGW("add arrchive fail:%s", zip_path.c_str());
		}
	}
#endif
	LoadConfig();
	linePatternD3D = 0;
	linePatternGL = 0x0f0f;
	waitFrame = 0;
	signalFrame = 0;
	showcard = 0;
	is_attacking = false;
	lpframe = 0;
	always_chain = false;
	ignore_chain = false;
	chain_when_avail = false;
	is_building = false;
	menuHandler.prev_operation = 0;
	menuHandler.prev_sel = -1;
	deckManager.LoadLFList(options);
	driver = device->getVideoDriver();
#ifdef _IRR_ANDROID_PLATFORM_
	int quality = options->getCardQualityOp();
	if (driver->getDriverType() == EDT_OGLES2) {
		isNPOTSupported = ((COGLES2Driver *) driver)->queryOpenGLFeature(COGLES2ExtensionHandler::IRR_OES_texture_npot);
	} else {
		isNPOTSupported = ((COGLES1Driver *) driver)->queryOpenGLFeature(COGLES1ExtensionHandler::IRR_OES_texture_npot);
	}
	ALOGD("isNPOTSupported = %d", isNPOTSupported);
	if (isNPOTSupported) {
		if (quality == 1) {
			driver->setTextureCreationFlag(irr::video::ETCF_CREATE_MIP_MAPS, false);
		} else {
			driver->setTextureCreationFlag(irr::video::ETCF_CREATE_MIP_MAPS, true);
		}
	} else {
		driver->setTextureCreationFlag(irr::video::ETCF_ALLOW_NON_POWER_2, true);
		driver->setTextureCreationFlag(irr::video::ETCF_CREATE_MIP_MAPS, false);
	}
#endif
	driver->setTextureCreationFlag(irr::video::ETCF_OPTIMIZED_FOR_QUALITY, true);

	imageManager.SetDevice(device);
	imageManager.ClearTexture();
	if(!imageManager.Initial(workingDir)) {
		ErrorLog("Failed to load textures!");
		return false;
	}
	// LoadExpansions only load zips, the other cdb databases are still loaded by getDBFiles
	io::path* cdbs = options->getDBFiles();
	len = options->getDbCount();
	//os::Printer::log("load cdbs count %d", len);
	for(int i=0;i<len;i++){
		io::path cdb_path = cdbs[i];
		wchar_t wpath[1024];
		BufferIO::DecodeUTF8(cdb_path.c_str(), wpath);
		if(dataManager.LoadDB(wpath)) {
		    ALOGD("add cdb ok:%s", cdb_path.c_str());
	    }else{
			ALOGW("add cdb fail:%s", cdb_path.c_str());
		}
	}
	//if(!dataManager.LoadDB(workingDir.append("/cards.cdb").c_str()))
	//	return false;
	if(dataManager.LoadStrings((workingDir + path("/expansions/strings.conf")).c_str())){
		ALOGD("loadStrings expansions/strings.conf");
	}
	if(!dataManager.LoadStrings((workingDir + path("/strings.conf")).c_str())) {
		ErrorLog("Failed to load strings!");
		return false;
	}
	LoadExpansions();
	env = device->getGUIEnvironment();
	bool isAntialias = options->isFontAntiAliasEnabled();
	numFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.numfont, 18 * yScale, isAntialias, false);
	adFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.numfont, 12 * yScale, isAntialias, false);
	lpcFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.numfont, 48 * yScale, isAntialias, true);
	guiFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.textfont, 18 * yScale, isAntialias, true);
    titleFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.textfont, 32 * yScale, isAntialias, true);
	textFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.textfont, (int)gameConf.textfontsize * yScale, isAntialias, true);
	if(!numFont || !guiFont) {
	  ALOGW("add font fail ");
	}
	smgr = device->getSceneManager();
	device->setWindowCaption(L"[---]");
	device->setResizable(false);
	gui::IGUISkin* newskin = CAndroidGUISkin::createAndroidSkin(gui::EGST_BURNING_SKIN, driver, env, xScale, yScale);
	newskin->setFont(guiFont);
	env->setSkin(newskin);
	newskin->drop();
#ifdef _IRR_ANDROID_PLATFORM_
	//main menu
	wchar_t strbuf[256];
	myswprintf(strbuf, L"YGOPro Version:%X.0%X.%X", (PRO_VERSION & 0xf000U) >> 12, (PRO_VERSION & 0x0ff0U) >> 4, PRO_VERSION & 0x000fU);
	wMainMenu = env->addWindow(rect<s32>(450 * xScale, 40 * yScale, 900 * xScale, 600 * yScale), false, strbuf);
	wMainMenu->getCloseButton()->setVisible(false);
	wMainMenu->setDrawBackground(false);
	btnLanMode = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(15 * xScale, 30 * yScale, 350 * xScale, 106 * yScale), wMainMenu, BUTTON_LAN_MODE);
	btnLanMode->setImageSize(core::dimension2di(400 * yScale, 76 * yScale));
	btnLanMode->setDrawBorder(false);
	btnLanMode->setImage(imageManager.tTitleBar);
	env->addStaticText(strbuf, rect<s32>(55 * xScale, 2 * yScale, 280 * xScale, 35 * yScale), false, false, btnLanMode);
	textLanMode = env->addStaticText(dataManager.GetSysString(1200), rect<s32>(115 * xScale, 25 * yScale, 300 * xScale, 65 * yScale), false, false, btnLanMode);
	textLanMode->setOverrideFont(titleFont);
	btnSingleMode = irr::gui::CGUIImageButton::addImageButton(env,  rect<s32>(15 * xScale, 110 * yScale, 350 * xScale, 186 * yScale), wMainMenu, BUTTON_SINGLE_MODE);
	btnSingleMode->setImageSize(core::dimension2di(400 * yScale, 76 * yScale));
	btnSingleMode->setDrawBorder(false);
    btnSingleMode->setImage(imageManager.tTitleBar);
	textSingleMode = env->addStaticText(dataManager.GetSysString(1201), rect<s32>(115 * xScale, 25 * yScale, 300 * xScale, 65 * yScale), false, false, btnSingleMode);
	textSingleMode->setOverrideFont(titleFont);
	btnReplayMode = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(15 * xScale, 190 * yScale, 350 * xScale, 266 * yScale), wMainMenu, BUTTON_REPLAY_MODE);
    btnReplayMode->setImageSize(core::dimension2di(400 * yScale, 76 * yScale));
    btnReplayMode->setDrawBorder(false);
    btnReplayMode->setImage(imageManager.tTitleBar);
	textReplayMode = env->addStaticText(dataManager.GetSysString(1202), rect<s32>(115 * xScale, 25 * yScale, 300 * xScale, 65 * yScale), false, false, btnReplayMode);
	textReplayMode->setOverrideFont(titleFont);
	btnDeckEdit = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(15 * xScale, 270 * yScale, 350 * xScale, 346 * yScale), wMainMenu, BUTTON_DECK_EDIT);
    btnDeckEdit->setImageSize(core::dimension2di(400 * yScale, 76 * yScale));
    btnDeckEdit->setDrawBorder(false);
    btnDeckEdit->setImage(imageManager.tTitleBar);
	textDeckEdit = env->addStaticText(dataManager.GetSysString(1204), rect<s32>(115 * xScale, 25 * yScale, 300 * xScale, 65 * yScale), false, false, btnDeckEdit);
	textDeckEdit->setOverrideFont(titleFont);
    btnSettings = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(15 * xScale, 350 * yScale, 350 * xScale, 426 * yScale), wMainMenu, BUTTON_SETTINGS);
    btnSettings->setImageSize(core::dimension2di(400 * yScale, 76 * yScale));
	btnSettings->setDrawBorder(false);
	btnSettings->setImage(imageManager.tTitleBar);
	textSettings = env->addStaticText(dataManager.GetSysString(1273), rect<s32>(115 * xScale, 25 * yScale, 300 * xScale, 65 * yScale), false, false, btnSettings);
	textSettings->setOverrideFont(titleFont);
    btnModeExit = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(15 * xScale, 430 * yScale, 350 * xScale, 506 * yScale), wMainMenu, BUTTON_MODE_EXIT);
    btnModeExit->setImageSize(core::dimension2di(400 * yScale, 76 * yScale));
	btnModeExit->setDrawBorder(false);
	btnModeExit->setImage(imageManager.tTitleBar);
	textModeExit = env->addStaticText(dataManager.GetSysString(1210), rect<s32>(145 * xScale, 25 * yScale, 300 * xScale, 65 * yScale), false, false, btnModeExit);
	textModeExit->setOverrideFont(titleFont);
    //lan mode
	wLanWindow = env->addWindow(rect<s32>(220 * xScale, 100 * yScale, 800 * xScale, 520 * yScale), false, dataManager.GetSysString(1200));
	wLanWindow->getCloseButton()->setVisible(false);
	wLanWindow->setVisible(false);
    ChangeToIGUIImageWindow(wLanWindow, &bgLanWindow, imageManager.tWindow);
	env->addStaticText(dataManager.GetSysString(1220), rect<s32>(30 * xScale, 30 * yScale, 70 * xScale, 70 * yScale), false, false, wLanWindow);
	ebNickName = CAndroidGUIEditBox::addAndroidEditBox(gameConf.nickname, true, env, rect<s32>(110 * xScale, 25 * yScale, 420 * xScale, 65 * yScale), wLanWindow);
	ebNickName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	lstHostList = env->addListBox(rect<s32>(30 * xScale, 75 * yScale, 540 * xScale, 240 * yScale), wLanWindow, LISTBOX_LAN_HOST, true);
	lstHostList->setItemHeight(30 * yScale);
	btnLanRefresh = env->addButton(rect<s32>(205 * xScale, 250 * yScale, 315 * xScale, 290 * yScale), wLanWindow, BUTTON_LAN_REFRESH, dataManager.GetSysString(1217));
		ChangeToIGUIImageButton(btnLanRefresh, imageManager.tButton_S, imageManager.tButton_S_pressed);
	env->addStaticText(dataManager.GetSysString(1221), rect<s32>(30 * xScale, 305 * yScale, 100 * xScale, 340 * yScale), false, false, wLanWindow);
	ebJoinHost = CAndroidGUIEditBox::addAndroidEditBox(gameConf.lasthost, true, env, rect<s32>(110 * xScale, 300 * yScale, 270 * xScale, 340 * yScale), wLanWindow);
	ebJoinHost->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	ebJoinPort = CAndroidGUIEditBox::addAndroidEditBox(gameConf.lastport, true, env, rect<s32>(280 * xScale, 300 * yScale, 340 * xScale, 340 * yScale), wLanWindow);
	ebJoinPort->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1222), rect<s32>(30 * xScale, 355 * yScale, 100 * xScale, 390 * yScale), false, false, wLanWindow);
	ebJoinPass = CAndroidGUIEditBox::addAndroidEditBox(gameConf.roompass, true, env, rect<s32>(110 * xScale, 350 * yScale, 340 * xScale, 390 * yScale), wLanWindow);
	ebJoinPass->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnJoinHost = env->addButton(rect<s32>(430 * xScale, 300 * yScale, 540 * xScale, 340 * yScale), wLanWindow, BUTTON_JOIN_HOST, dataManager.GetSysString(1223));
		ChangeToIGUIImageButton(btnJoinHost, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnJoinCancel = env->addButton(rect<s32>(430 * xScale, 350 * yScale, 540 * xScale, 390 * yScale), wLanWindow, BUTTON_JOIN_CANCEL, dataManager.GetSysString(1212));
		ChangeToIGUIImageButton(btnJoinCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnCreateHost = env->addButton(rect<s32>(430 * xScale, 25 * yScale, 540 * xScale, 65 * yScale), wLanWindow, BUTTON_CREATE_HOST, dataManager.GetSysString(1224));
		ChangeToIGUIImageButton(btnCreateHost, imageManager.tButton_S, imageManager.tButton_S_pressed);

	//create host
	wCreateHost = env->addWindow(rect<s32>(220 * xScale, 100 * yScale, 800 * xScale, 520 * yScale), false, dataManager.GetSysString(1224));
	wCreateHost->getCloseButton()->setVisible(false);
	wCreateHost->setVisible(false);
        ChangeToIGUIImageWindow(wCreateHost, &bgCreateHost, imageManager.tWindow);
    env->addStaticText(dataManager.GetSysString(1226), rect<s32>(20 * xScale, 30 * yScale, 90 * xScale, 65 * yScale), false, false, wCreateHost);
	cbHostLFlist = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(110 * xScale, 25 * yScale, 260 * xScale, 65 * yScale), wCreateHost);
	for(unsigned int i = 0; i < deckManager._lfList.size(); ++i)
		cbHostLFlist->addItem(deckManager._lfList[i].listName.c_str(), deckManager._lfList[i].hash);
	cbHostLFlist->setSelected(gameConf.use_lflist ? gameConf.default_lflist : cbHostLFlist->getItemCount() - 1);
	env->addStaticText(dataManager.GetSysString(1225), rect<s32>(20 * xScale, 75 * yScale, 100 * xScale, 110 * yScale), false, false, wCreateHost);
	cbRule = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(110 * xScale, 75 * yScale, 260 * xScale, 115 * yScale), wCreateHost);
	cbRule->setMaxSelectionRows(10);
	cbRule->addItem(dataManager.GetSysString(1481));
	cbRule->addItem(dataManager.GetSysString(1482));
	cbRule->addItem(dataManager.GetSysString(1483));
	cbRule->addItem(dataManager.GetSysString(1484));
	cbRule->addItem(dataManager.GetSysString(1485));
	cbRule->addItem(dataManager.GetSysString(1486));
	switch(gameConf.defaultOT) {
	case 1:
		cbRule->setSelected(0);
		break;
	case 2:
		cbRule->setSelected(1);
		break;
	case 4:
		cbRule->setSelected(3);
		break;
	case 8:
		cbRule->setSelected(2);
		break;
	default:
		cbRule->setSelected(5);
		break;
	}	
	env->addStaticText(dataManager.GetSysString(1227), rect<s32>(20 * xScale, 130 * yScale, 100 * xScale, 165 * yScale), false, false, wCreateHost);
	cbMatchMode = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(110 * xScale, 125 * yScale, 260 * xScale, 165 * yScale), wCreateHost);
	cbMatchMode->addItem(dataManager.GetSysString(1244));
	cbMatchMode->addItem(dataManager.GetSysString(1245));
	cbMatchMode->addItem(dataManager.GetSysString(1246));
	env->addStaticText(dataManager.GetSysString(1237), rect<s32>(20 * xScale, 180 * yScale, 100 * xScale, 215 * yScale), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 180);
	ebTimeLimit = CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, rect<s32>(110 * xScale, 175 * yScale, 260 * xScale, 215 * yScale), wCreateHost);
	ebTimeLimit->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1236), rect<s32>(270 * xScale, 30 * yScale, 350 * xScale, 65 * yScale), false, false, wCreateHost);
	cbDuelRule = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(360 * xScale, 25 * yScale, 510 * xScale, 65 * yScale), wCreateHost);
	cbDuelRule->addItem(dataManager.GetSysString(1260));
	cbDuelRule->addItem(dataManager.GetSysString(1261));
	cbDuelRule->addItem(dataManager.GetSysString(1262));
	cbDuelRule->addItem(dataManager.GetSysString(1263));
	cbDuelRule->addItem(dataManager.GetSysString(1264));
	cbDuelRule->setSelected(gameConf.default_rule - 1);
	chkNoCheckDeck = env->addCheckBox(false, rect<s32>(250 * xScale, 235 * yScale, 350 * xScale, 275 * yScale), wCreateHost, -1, dataManager.GetSysString(1229));
	chkNoShuffleDeck = env->addCheckBox(false, rect<s32>(360 * xScale, 235 * yScale, 460 * xScale, 275 * yScale), wCreateHost, -1, dataManager.GetSysString(1230));
	env->addStaticText(dataManager.GetSysString(1231), rect<s32>(270 * xScale, 80 * yScale, 350 * xScale, 310 * yScale), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 8000);
	ebStartLP = CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, rect<s32>(360 * xScale, 75 * yScale, 510 * xScale, 115 * yScale), wCreateHost);
	ebStartLP->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1232), rect<s32>(270 * xScale, 130 * yScale, 350 * xScale, 165 * yScale), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 5);
	ebStartHand = CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, rect<s32>(360 * xScale, 125 * yScale, 510 * xScale, 165 * yScale), wCreateHost);
	ebStartHand->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1233), rect<s32>(270 * xScale, 180 * yScale, 350 * xScale, 215 * yScale), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 1);
	ebDrawCount = CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, rect<s32>(360 * xScale, 175 * yScale, 510 * xScale, 215 * yScale), wCreateHost);
	ebDrawCount->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1234), rect<s32>(20 * xScale, 305 * yScale, 100 * xScale, 340 * yScale), false, false, wCreateHost);
	ebServerName = CAndroidGUIEditBox::addAndroidEditBox(gameConf.gamename, true, env, rect<s32>(110 * xScale, 300 * yScale, 340 * xScale, 340 * yScale), wCreateHost);
	ebServerName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1235), rect<s32>(20 * xScale, 355 * yScale, 100 * xScale, 390 * yScale), false, false, wCreateHost);
	ebServerPass = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(110 * xScale, 350 * yScale, 340 * xScale, 390 * yScale), wCreateHost);
	ebServerPass->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnHostConfirm = env->addButton(rect<s32>(430 * xScale, 300 * yScale, 540 * xScale, 340 * yScale), wCreateHost, BUTTON_HOST_CONFIRM, dataManager.GetSysString(1211));
		ChangeToIGUIImageButton(btnHostConfirm, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnHostCancel = env->addButton(rect<s32>(430 * xScale, 350 * yScale, 540 * xScale, 390 * yScale), wCreateHost, BUTTON_HOST_CANCEL, dataManager.GetSysString(1212));
		ChangeToIGUIImageButton(btnHostCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);

	//host(single)
	wHostPrepare = env->addWindow(rect<s32>(220 * xScale, 100 * yScale, 800 * xScale, 520 * yScale), false, dataManager.GetSysString(1250));
	wHostPrepare->setDraggable(false);
	wHostPrepare->getCloseButton()->setVisible(false);
	wHostPrepare->setVisible(false);
	    ChangeToIGUIImageWindow(wHostPrepare, &bgHostPrepare, imageManager.tWindow);
    btnHostPrepDuelist = env->addButton(rect<s32>(10 * xScale, 30 * yScale, 120 * xScale, 70 * yScale), wHostPrepare, BUTTON_HP_DUELIST, dataManager.GetSysString(1251));
		ChangeToIGUIImageButton(btnHostPrepDuelist, imageManager.tButton_S, imageManager.tButton_S_pressed);
	for(int i = 0; i < 2; ++i) {
		stHostPrepDuelist[i] = env->addStaticText(L"", rect<s32>(60 * xScale, (80 + i * 45) * yScale, 260 * xScale, (120 + i * 45) * yScale), true, false, wHostPrepare);
		stHostPrepDuelist[i]->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
		btnHostPrepKick[i] = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(10 * xScale, (80 + i * 45) * yScale, 50 * xScale, (120 + i * 45) * yScale), wHostPrepare, BUTTON_HP_KICK);
        btnHostPrepKick[i]->setImageSize(core::dimension2di(40 * yScale, 40 * yScale));
        btnHostPrepKick[i]->setDrawBorder(false);
        btnHostPrepKick[i]->setImage(imageManager.tClose);
		chkHostPrepReady[i] = env->addCheckBox(false, rect<s32>(270 * xScale, (80 + i * 45) * yScale, 310 * xScale, (120 + i * 45) * yScale), wHostPrepare, CHECKBOX_HP_READY, L"");
		chkHostPrepReady[i]->setEnabled(false);
	}
	for(int i = 2; i < 4; ++i) {
		stHostPrepDuelist[i] = env->addStaticText(L"", rect<s32>(60 * xScale, (135 + i * 45) * yScale, 260 * xScale, (175 + i * 45) * yScale), true, false, wHostPrepare);
		stHostPrepDuelist[i]->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
        btnHostPrepKick[i] = irr::gui::CGUIImageButton::addImageButton(env,rect<s32>(10 * xScale, (135 + i * 45) * yScale, 50 * xScale, (175 + i * 45) * yScale), wHostPrepare, BUTTON_HP_KICK);
        btnHostPrepKick[i]->setImageSize(core::dimension2di(40 * yScale, 40 * yScale));
        btnHostPrepKick[i]->setDrawBorder(false);
        btnHostPrepKick[i]->setImage(imageManager.tClose);
		chkHostPrepReady[i] = env->addCheckBox(false, rect<s32>(270 * xScale, (135 + i * 45) * yScale, 310 * xScale, (175 + i * 45) * yScale), wHostPrepare, CHECKBOX_HP_READY, L"");
		chkHostPrepReady[i]->setEnabled(false);
	}
	btnHostPrepOB = env->addButton(rect<s32>(10 * xScale, 175 * yScale, 120 * xScale, 215 * yScale), wHostPrepare, BUTTON_HP_OBSERVER, dataManager.GetSysString(1252));
		ChangeToIGUIImageButton(btnHostPrepOB, imageManager.tButton_S, imageManager.tButton_S_pressed);
	myswprintf(strbuf, L"%ls%d", dataManager.GetSysString(1253), 0);
	stHostPrepOB = env->addStaticText(strbuf, rect<s32>(320 * xScale, 310 * yScale, 560 * xScale, 350 * yScale), false, false, wHostPrepare);
	stHostPrepRule = env->addStaticText(L"", rect<s32>(320 * xScale, 30 * yScale, 560 * xScale, 300 * yScale), false, true, wHostPrepare);
	env->addStaticText(dataManager.GetSysString(1254), rect<s32>(10 * xScale, 320 * yScale, 110 * xScale, 350 * yScale), false, false, wHostPrepare);
	cbCategorySelect = env->addComboBox(rect<s32>(0, 0, 0, 0), wHostPrepare, COMBOBOX_HP_CATEGORY);
	cbDeckSelect = env->addComboBox(rect<s32>(0, 0, 0, 0), wHostPrepare);
	btnHostDeckSelect = env->addButton(rect<s32>(10 * xScale, 350 * yScale, 270 * xScale, 390 * yScale), wHostPrepare, BUTTON_HP_DECK_SELECT, L"");
	btnHostPrepReady = env->addButton(rect<s32>(170 * xScale, 175 * yScale, 280 * xScale, 215 * yScale), wHostPrepare, BUTTON_HP_READY, dataManager.GetSysString(1218));
		ChangeToIGUIImageButton(btnHostPrepReady, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnHostPrepNotReady = env->addButton(rect<s32>(170 * xScale, 175 * yScale, 280 * xScale, 215 * yScale), wHostPrepare, BUTTON_HP_NOTREADY, dataManager.GetSysString(1219));
		ChangeToIGUIImageButton(btnHostPrepNotReady, imageManager.tButton_S_pressed, imageManager.tButton_S);
	btnHostPrepNotReady->setVisible(false);
	btnHostPrepStart = env->addButton(rect<s32>(320 * xScale, 350 * yScale, 430 * xScale, 390 * yScale), wHostPrepare, BUTTON_HP_START, dataManager.GetSysString(1215));
		ChangeToIGUIImageButton(btnHostPrepStart, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnHostPrepCancel = env->addButton(rect<s32>(440 * xScale, 350 * yScale, 550 * xScale, 390 * yScale), wHostPrepare, BUTTON_HP_CANCEL, dataManager.GetSysString(1210));
		ChangeToIGUIImageButton(btnHostPrepCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);

	//img always use *yScale to keep proportion
	wCardImg = env->addStaticText(L"",rect<s32>(1 * yScale, 1 * yScale, ( 2 + CARD_IMG_WIDTH) * yScale, (2 + CARD_IMG_HEIGHT) * yScale), true, false, 0, -1, true);
    wCardImg->setBackgroundColor(0xc0c0c0c0);
	wCardImg->setVisible(false);
	imgCard = env->addImage(rect<s32>(2 * yScale, 2 * yScale, CARD_IMG_WIDTH * yScale, CARD_IMG_HEIGHT * yScale), wCardImg);
	imgCard->setImage(imageManager.tCover[0]);
	imgCard->setScaleImage(true);
	imgCard->setUseAlphaChannel(true);

	//phase
	wPhase = env->addStaticText(L"", rect<s32>(480 * xScale, 305 * yScale, 895 * xScale, 335 * yScale));
	wPhase->setVisible(false);
	btnPhaseStatus = env->addButton(rect<s32>(0 * xScale, 0 * yScale, 50 * xScale, 30 * yScale), wPhase, BUTTON_PHASE, L"");
	    ChangeToIGUIImageButton(btnPhaseStatus, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnPhaseStatus->setIsPushButton(true);
	btnPhaseStatus->setPressed(true);
	btnPhaseStatus->setVisible(false);
	btnBP = env->addButton(rect<s32>(160 * xScale, 0 * yScale, 210 * xScale, 30 * yScale), wPhase, BUTTON_BP, L"\xff22\xff30");
        ChangeToIGUIImageButton(btnBP, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnBP->setVisible(false);
	btnM2 = env->addButton(rect<s32>(160 * xScale, 0 * yScale, 210 * xScale, 30 * yScale), wPhase, BUTTON_M2, L"\xff2d\xff12");
        ChangeToIGUIImageButton(btnM2, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnM2->setVisible(false);
	btnEP = env->addButton(rect<s32>(320 * xScale, 0 * yScale, 370 * xScale, 30 * yScale), wPhase, BUTTON_EP, L"\xff25\xff30");
        ChangeToIGUIImageButton(btnEP, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnEP->setVisible(false);

    //tab（changed）
	wInfos = env->addWindow(rect<s32>(1 * xScale, (3 + CARD_IMG_HEIGHT) * yScale, 260 * xScale, 639 * yScale), false, L"");
	wInfos->getCloseButton()->setVisible(false);
	wInfos->setDraggable(false);
	wInfos->setVisible(false);
	    ChangeToIGUIImageWindow(wInfos, &bgInfos, imageManager.tWindow_V);
	//info
	stName = env->addStaticText(L"", rect<s32>(10 * xScale, 10 * yScale, 250 * xScale, 32 * yScale), true, false, wInfos, -1, false);
	stName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	stInfo = env->addStaticText(L"", rect<s32>(10 * xScale, 37 * yScale, 260 * xScale, 60 * yScale), false, true, wInfos, -1, false);
	stInfo->setOverrideColor(SColor(255, 149, 211, 137));//255, 0, 0, 255
	stDataInfo = env->addStaticText(L"", rect<s32>(10 * xScale, 60 * yScale, 260 * xScale, 83 * yScale), false, true, wInfos, -1, false);
	stDataInfo->setOverrideColor(SColor(255, 222, 215, 100));//255, 0, 0, 255
	stSetName = env->addStaticText(L"", rect<s32>(10 * xScale, 83 * yScale, 260 * xScale, 106 * yScale), false, true, wInfos, -1, false);
	stSetName->setOverrideColor(SColor(255, 255, 152, 42));//255, 0, 0, 255
	stText = env->addStaticText(L"", rect<s32>(10 * xScale, 106 * yScale, 250 * xScale, 340 * yScale), false, true, wInfos, -1, false);
    stText->setOverrideFont(textFont);
	scrCardText = env->addScrollBar(false, rect<s32>(250 * xScale, 106 * yScale, 258 * xScale, 639 * yScale), wInfos, SCROLL_CARDTEXT);
	scrCardText->setLargeStep(1);
	scrCardText->setSmallStep(1);
	scrCardText->setVisible(false);
    //imageButtons pallet
    wPallet = env->addWindow(rect<s32>(262 * xScale, (3 + CARD_IMG_HEIGHT) * yScale, 307 * xScale, 639 * yScale), false, L"");
    wPallet->getCloseButton()->setVisible(false);
    wPallet->setDraggable(false);
    wPallet->setDrawTitlebar(false);
    wPallet->setDrawBackground(false);
    wPallet->setVisible(false);
    //Logs
    imgLog = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(0 * yScale, 55 * yScale, 45 * yScale, 100 * yScale), wPallet, BUTTON_SHOW_LOG);
	imgLog->setImageSize(core::dimension2di(28 * yScale, 28 * yScale));
	imgLog->setImage(imageManager.tLogs);
	imgLog->setIsPushButton(true);
	wLogs = env->addWindow(rect<s32>(720 * xScale, 1 * yScale, 1020 * xScale, 501 * yScale), false, dataManager.GetSysString(1271));
    wLogs->getCloseButton()->setVisible(false);
    wLogs->setVisible(false);
        ChangeToIGUIImageWindow(wLogs, &bgLogs, imageManager.tDialog_S);
    lstLog = env->addListBox(rect<s32>(10 * xScale, 20 * yScale, 290 * xScale, 440 * yScale), wLogs, LISTBOX_LOG, false);
    lstLog->setItemHeight(30 * yScale);
    btnClearLog = env->addButton(rect<s32>(20 * xScale, 450 * yScale, 130 * xScale, 490 * yScale), wLogs, BUTTON_CLEAR_LOG, dataManager.GetSysString(1304));
        ChangeToIGUIImageButton(btnClearLog, imageManager.tButton_S, imageManager.tButton_S_pressed);
    btnCloseLog = env->addButton(rect<s32>(170 * xScale, 450 * yScale, 280 * xScale, 490 * yScale), wLogs, BUTTON_CLOSE_LOG, dataManager.GetSysString(1211));
		ChangeToIGUIImageButton(btnCloseLog, imageManager.tButton_S, imageManager.tButton_S_pressed);
    //vol play/mute
	imgVol = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(0 * yScale, 110 * yScale, 45 * yScale, 155 * yScale), wPallet, BUTTON_BGM);
    imgVol->setImageSize(core::dimension2di(28 * yScale, 28 * yScale));
	if (gameConf.enable_music) {
		imgVol->setImage(imageManager.tPlay);
	} else {
		imgVol->setImage(imageManager.tMute);
	}
    //shift quick animation
    imgQuickAnimation = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(0 * yScale, 165 * yScale, 45 * yScale, 210 * yScale), wPallet, BUTTON_QUICK_ANIMIATION);
    imgQuickAnimation->setImageSize(core::dimension2di(28 * yScale, 28 * yScale));
    if (gameConf.quick_animation) {
        imgQuickAnimation->setImage(imageManager.tDoubleX);
    } else {
        imgQuickAnimation->setImage(imageManager.tOneX);
    }
    //Settings
	imgSettings = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(0 * yScale, 0 * yScale, 45 * yScale, 45 * yScale), wPallet, BUTTON_SETTINGS);
	imgSettings->setImageSize(core::dimension2di(28 * yScale, 28 * yScale));
	imgSettings->setImage(imageManager.tSettings);
	imgSettings->setIsPushButton(true);
    wSettings = env->addWindow(rect<s32>(220 * xScale, 80 * yScale, 800 * xScale, 540 * yScale), false, dataManager.GetSysString(1273));
    wSettings->setRelativePosition(recti(220 * xScale, 80 * yScale, 800 * xScale, 540 * yScale));
    wSettings->getCloseButton()->setVisible(false);
	wSettings->setVisible(false);
	    ChangeToIGUIImageWindow(wSettings, &bgSettings, imageManager.tWindow);
	int posX = 20 * xScale;
	int posY = 40 * yScale;
	chkMAutoPos = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 30 * yScale), wSettings, -1, dataManager.GetSysString(1274));
	chkMAutoPos->setChecked(gameConf.chkMAutoPos != 0);
	posY += 40 * yScale;
	chkSTAutoPos = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 30 * yScale), wSettings, -1, dataManager.GetSysString(1278));
	chkSTAutoPos->setChecked(gameConf.chkSTAutoPos != 0);
	posY += 40 * yScale;
	chkRandomPos = env->addCheckBox(false, rect<s32>(posX + 20 * xScale, posY, posX + (20 + 260) * xScale, posY + 30 * yScale), wSettings, -1, dataManager.GetSysString(1275));
	chkRandomPos->setChecked(gameConf.chkRandomPos != 0);
	posY += 40 * yScale;
	chkAutoChain = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 30 * yScale), wSettings, -1, dataManager.GetSysString(1276));
	chkAutoChain->setChecked(gameConf.chkAutoChain != 0);
	posY += 40 * yScale;
	chkWaitChain = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 30 * yScale), wSettings, -1, dataManager.GetSysString(1277));
	chkWaitChain->setChecked(gameConf.chkWaitChain != 0);
    posY += 40 * yScale;
	chkDefaultShowChain = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 30 * yScale), wSettings, -1, dataManager.GetSysString(1354));
	chkDefaultShowChain->setChecked(gameConf.chkDefaultShowChain != 0);
	posY += 40 * yScale;
    chkQuickAnimation = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 30 * yScale), wSettings, CHECKBOX_QUICK_ANIMATION, dataManager.GetSysString(1299));
	chkQuickAnimation->setChecked(gameConf.quick_animation != 0);
    posY += 40 * yScale;
    chkDrawFieldSpell = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 30 * yScale), wSettings, CHECKBOX_DRAW_FIELD_SPELL, dataManager.GetSysString(1283));
    chkDrawFieldSpell->setChecked(gameConf.draw_field_spell != 0);
    posY += 40 * yScale;
    chkDrawSingleChain = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 30 * yScale), wSettings, CHECKBOX_DRAW_SINGLE_CHAIN, dataManager.GetSysString(1287));
	chkDrawSingleChain->setChecked(gameConf.draw_single_chain != 0);
	posX = 250 * xScale;//another Column
	posY = 40 * yScale;
    chkLFlist = env->addCheckBox(false, rect<s32>(posX, posY, posX + 100 * xScale, posY + 30 * yScale), wSettings, CHECKBOX_LFLIST, dataManager.GetSysString(1288));
    chkLFlist->setChecked(gameConf.use_lflist);
    cbLFlist = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(posX + 110 * xScale, posY, posX + 230 * xScale, posY + 30 * yScale), wSettings, COMBOBOX_LFLIST);
    cbLFlist->setMaxSelectionRows(6);
    for(unsigned int i = 0; i < deckManager._lfList.size(); ++i)
        cbLFlist->addItem(deckManager._lfList[i].listName.c_str());
    cbLFlist->setEnabled(gameConf.use_lflist);
    cbLFlist->setSelected(gameConf.use_lflist ? gameConf.default_lflist : cbLFlist->getItemCount() - 1);
	posY += 40 * yScale;
	chkIgnore1 = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260, posY + 30 * yScale), wSettings, CHECKBOX_DISABLE_CHAT, dataManager.GetSysString(1290));
	chkIgnore1->setChecked(gameConf.chkIgnore1 != 0);
	posY += 40 * yScale;
	chkIgnore2 = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260, posY + 30 * yScale), wSettings, -1, dataManager.GetSysString(1291));
	chkIgnore2->setChecked(gameConf.chkIgnore2 != 0);
	posY += 40 * yScale;
	chkHidePlayerName = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 30 * yScale), wSettings, CHECKBOX_HIDE_PLAYER_NAME, dataManager.GetSysString(1289));
	chkHidePlayerName->setChecked(gameConf.hide_player_name != 0);
	posY += 40 * yScale;
	chkIgnoreDeckChanges = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 30 * yScale), wSettings, -1, dataManager.GetSysString(1357));
	chkIgnoreDeckChanges->setChecked(gameConf.chkIgnoreDeckChanges != 0);
	posY += 40 * yScale;
	chkAutoSaveReplay = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 30 * yScale), wSettings, -1, dataManager.GetSysString(1366));
	chkAutoSaveReplay->setChecked(gameConf.auto_save_replay != 0);
    posY += 40 * yScale;
    chkMusicMode = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 30 * yScale), wSettings, -1, dataManager.GetSysString(1281));
    chkMusicMode->setChecked(gameConf.music_mode != 0);
    posY += 40 * yScale;
    chkEnableSound = env->addCheckBox(gameConf.enable_sound, rect<s32>(posX, posY, posX + 100 * xScale, posY + 30 * yScale), wSettings, CHECKBOX_ENABLE_SOUND, dataManager.GetSysString(1279));
    chkEnableSound->setChecked(gameConf.enable_sound);
    scrSoundVolume = env->addScrollBar(true, rect<s32>(posX + 110 * xScale, posY, posX + 280 * xScale, posY + 30 * yScale), wSettings, SCROLL_VOLUME);
    scrSoundVolume->setMax(100);
    scrSoundVolume->setMin(0);
    scrSoundVolume->setPos(gameConf.sound_volume);
    scrSoundVolume->setLargeStep(1);
    scrSoundVolume->setSmallStep(1);
    posY += 40 * yScale;
    chkEnableMusic = env->addCheckBox(gameConf.enable_music, rect<s32>(posX, posY, posX + 100 * xScale, posY + 30 * yScale), wSettings, CHECKBOX_ENABLE_MUSIC, dataManager.GetSysString(1280));
    chkEnableMusic->setChecked(gameConf.enable_music);
    scrMusicVolume = env->addScrollBar(true, rect<s32>(posX + 110 * xScale, posY, posX + 280 * xScale, posY + 30 * yScale), wSettings, SCROLL_VOLUME);
    scrMusicVolume->setMax(100);
    scrMusicVolume->setMin(0);
    scrMusicVolume->setPos(gameConf.music_volume);
    scrMusicVolume->setLargeStep(1);
    scrMusicVolume->setSmallStep(1);
    elmTabSystemLast = chkEnableMusic;
	btnCloseSettings =irr::gui::CGUIImageButton::addImageButton(env,rect<s32>(500 * xScale, 30 * yScale, 550 * xScale, 80 * yScale), wSettings, BUTTON_CLOSE_SETTINGS);
	btnCloseSettings->setImageSize(core::dimension2di(50 * yScale, 50 * yScale));
	btnCloseSettings->setDrawBorder(false);
    btnCloseSettings->setImage(imageManager.tClose);
    //
	wHand = env->addWindow(rect<s32>(500 * xScale, 450 * yScale, 825 * xScale, 605 * yScale), false, L"");
	wHand->getCloseButton()->setVisible(false);
	wHand->setDraggable(false);
	wHand->setDrawTitlebar(false);
	wHand->setVisible(false);
	for(int i = 0; i < 3; ++i) {
		btnHand[i] = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>((10 + 105 * i) * xScale, 10 * yScale, (105 + 105 * i)  * xScale, 144 * yScale), wHand, BUTTON_HAND1 + i);
		btnHand[i]->setImage(imageManager.tHand[i]);
		btnHand[i]->setImageScale(core::vector2df(xScale, yScale));
	}

	//first or second to go
	wFTSelect = env->addWindow(rect<s32>(470 * xScale, 180 * yScale, 860 * xScale, 360 * yScale), false, L"");
	wFTSelect->getCloseButton()->setVisible(false);
	wFTSelect->setVisible(false);
	    ChangeToIGUIImageWindow(wFTSelect, &bgFTSelect, imageManager.tDialog_L);
	btnFirst = env->addButton(rect<s32>(20 * xScale, 35 * yScale, 370 * xScale, 85 * yScale), wFTSelect, BUTTON_FIRST, dataManager.GetSysString(100));
        ChangeToIGUIImageButton(btnFirst, imageManager.tButton_L, imageManager.tButton_L_pressed, titleFont);
	btnSecond = env->addButton(rect<s32>(20 * xScale, 95 * yScale, 370 * xScale, 145 * yScale), wFTSelect, BUTTON_SECOND, dataManager.GetSysString(101));
        ChangeToIGUIImageButton(btnSecond, imageManager.tButton_L, imageManager.tButton_L_pressed, titleFont);
	//message (370)
	wMessage = env->addWindow(rect<s32>(470 * xScale, 160 * yScale, 860 * xScale, 380 * yScale), false, dataManager.GetSysString(1216));
	wMessage->getCloseButton()->setVisible(false);
	wMessage->setVisible(false);
	    ChangeToIGUIImageWindow(wMessage, &bgMessage, imageManager.tDialog_L);
	stMessage = env->addStaticText(L"", rect<s32>(20 * xScale, 20 * yScale, 370 * xScale, 120 * yScale), false, true, wMessage, -1, false);
	stMessage->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnMsgOK = env->addButton(rect<s32>(130 * xScale, 150 * yScale, 260 * xScale, 200 * yScale), wMessage, BUTTON_MSG_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnMsgOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//system message (370)
	wSysMessage = env->addWindow(rect<s32>(315 * xScale, 180 * yScale, 705 * xScale, 360 * yScale), false, dataManager.GetSysString(1216));
	wSysMessage->getCloseButton()->setVisible(false);
	wSysMessage->setVisible(false);
		ChangeToIGUIImageWindow(wSysMessage, &bgSysMessage, imageManager.tDialog_L);
	stSysMessage = env->addStaticText(L"", rect<s32>(20 * xScale, 20 * yScale, 370 * xScale, 100 * yScale), false, true, wSysMessage, -1, false);
	stSysMessage->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnSysMsgOK = env->addButton(rect<s32>(130 * xScale, 120 * yScale, 260 * xScale, 170 * yScale), wSysMessage, BUTTON_SYS_MSG_OK, dataManager.GetSysString(1211));
    	ChangeToIGUIImageButton(btnSysMsgOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//auto fade message (370)
	wACMessage = env->addWindow(rect<s32>(470 * xScale, 240 * yScale, 860 * xScale, 300 * yScale), false, L"");
	wACMessage->getCloseButton()->setVisible(false);
	wACMessage->setVisible(false);
	wACMessage->setDrawBackground(false);
	stACMessage = env->addStaticText(L"", rect<s32>(0 * xScale, 0 * yScale, 350 * xScale, 60 * yScale), true, true, wACMessage, -1, true);
	stACMessage->setBackgroundColor(0x6011113d);
	stACMessage->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	//yes/no (370)
	wQuery = env->addWindow(rect<s32>(470 * xScale, 160 * yScale, 860 * xScale, 380 * yScale), false, dataManager.GetSysString(560));
	wQuery->getCloseButton()->setVisible(false);
	wQuery->setVisible(false);
        ChangeToIGUIImageWindow(wQuery, &bgQuery, imageManager.tDialog_L);
	stQMessage =  env->addStaticText(L"", rect<s32>(20 * xScale, 20 * yScale, 370 * xScale, 140 * yScale), false, true, wQuery, -1, false);
	stQMessage->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnYes = env->addButton(rect<s32>(60 * xScale, 150 * yScale, 170 * xScale, 200 * yScale), wQuery, BUTTON_YES, dataManager.GetSysString(1213));
        ChangeToIGUIImageButton(btnYes, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnNo = env->addButton(rect<s32>(210 * xScale, 150 * yScale, 320 * xScale, 200 * yScale), wQuery, BUTTON_NO, dataManager.GetSysString(1214));
        ChangeToIGUIImageButton(btnNo, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//surrender yes/no (310)
	wSurrender = env->addWindow(rect<s32>(470 * xScale, 180 * yScale, 860 * xScale, 360 * yScale), false, dataManager.GetSysString(560));
	wSurrender->getCloseButton()->setVisible(false);
	wSurrender->setVisible(false);
        ChangeToIGUIImageWindow(wSurrender, &bgSurrender, imageManager.tDialog_L);
	stSurrenderMessage = env->addStaticText(dataManager.GetSysString(1359), rect<s32>(20 * xScale, 20 * yScale, 350 * xScale, 100 * yScale), false, true, wSurrender, -1, false);
	stSurrenderMessage->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnSurrenderYes = env->addButton(rect<s32>(60 * xScale, 120 * yScale, 170 * xScale, 170 * yScale), wSurrender, BUTTON_SURRENDER_YES, dataManager.GetSysString(1213));
        ChangeToIGUIImageButton(btnSurrenderYes, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSurrenderNo = env->addButton(rect<s32>(200 * xScale, 120 * yScale, 310 * xScale, 170 * yScale), wSurrender, BUTTON_SURRENDER_NO, dataManager.GetSysString(1214));
        ChangeToIGUIImageButton(btnSurrenderNo, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//options (350)
	wOptions = env->addWindow(rect<s32>(470 * xScale, 180 * yScale, 860 * xScale, 360 * yScale), false, L"");
	wOptions->getCloseButton()->setVisible(false);
	wOptions->setVisible(false);
    wOptions->setDrawBackground(false);
    bgOptions = env->addImage(rect<s32>(0, 0, 390 * xScale, 180 * yScale), wOptions, -1, 0, true);
    bgOptions->setImage(imageManager.tDialog_L);
    bgOptions->setScaleImage(true);
	stOptions =  env->addStaticText(L"", rect<s32>(20 * xScale, 20 * yScale, 370 * xScale, 100 * yScale), false, true, wOptions, -1, false);
	stOptions->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnOptionOK = env->addButton(rect<s32>(140 * xScale, 115 * yScale, 250 * xScale, 165 * yScale), wOptions, BUTTON_OPTION_OK, dataManager.GetSysString(1211));
		ChangeToIGUIImageButton(btnOptionOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnOptionp = env->addButton(rect<s32>(20 * xScale, 115 * yScale, 130 * xScale, 165 * yScale), wOptions, BUTTON_OPTION_PREV, L"<<<");
		ChangeToIGUIImageButton(btnOptionp, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnOptionn = env->addButton(rect<s32>(260 * xScale, 115 * yScale, 370 * xScale, 165 * yScale), wOptions, BUTTON_OPTION_NEXT, L">>>");
		ChangeToIGUIImageButton(btnOptionn, imageManager.tButton_S, imageManager.tButton_S_pressed);
	for(int i = 0; i < 5; ++i) {
		btnOption[i] = env->addButton(rect<s32>(20 * xScale, (20 + 60 * i) * yScale, 370 * xScale, (70 + 60 * i) * yScale), wOptions, BUTTON_OPTION_0 + i, L"");
			ChangeToIGUIImageButton(btnOption[i], imageManager.tButton_L, imageManager.tButton_L_pressed);
	}
	scrOption = env->addScrollBar(false, rect<s32>(0 * xScale, 0 * yScale, 0 * xScale, 0 * yScale), wOptions, SCROLL_OPTION_SELECT);
	scrOption->setLargeStep(1);
	scrOption->setSmallStep(1);
	scrOption->setMin(0);
#endif
	//pos
	wPosSelect = env->addWindow(rect<s32>(470 * xScale, 160 * yScale, 860 * xScale, 380 * yScale), false, dataManager.GetSysString(561));
	wPosSelect->getCloseButton()->setVisible(false);
	wPosSelect->setVisible(false);
        ChangeToIGUIImageWindow(wPosSelect, &bgPosSelect, imageManager.tDialog_L);
	btnPSAU = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(50 * xScale, 25 * yScale, 190 * xScale, 165 * yScale), wPosSelect, BUTTON_POS_AU);
	btnPSAU->setImageSize(core::dimension2di(CARD_IMG_WIDTH * 0.5f * yScale, CARD_IMG_HEIGHT * 0.5f * yScale));
	btnPSAD = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(200 * xScale, 25 * yScale, 340 * xScale, 165 * yScale), wPosSelect, BUTTON_POS_AD);
	btnPSAD->setImageSize(core::dimension2di(CARD_IMG_WIDTH * 0.5f * yScale, CARD_IMG_HEIGHT * 0.5f * yScale));
	btnPSAD->setImage(imageManager.tCover[0]);
	btnPSDU = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(50 * xScale, 25 * yScale, 190 * xScale, 165 * yScale), wPosSelect, BUTTON_POS_DU);
	btnPSDU->setImageSize(core::dimension2di(CARD_IMG_WIDTH * 0.5f * yScale, CARD_IMG_HEIGHT * 0.5f * yScale));
	btnPSDU->setImageRotation(270);
	btnPSDD = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(200 * xScale, 25 * yScale, 340 * xScale, 165 * yScale), wPosSelect, BUTTON_POS_DD);
	btnPSDD->setImageSize(core::dimension2di(CARD_IMG_WIDTH * 0.5f * yScale, CARD_IMG_HEIGHT * 0.5f * yScale));
	btnPSDD->setImageRotation(270);
	btnPSDD->setImage(imageManager.tCover[0]);
#ifdef _IRR_ANDROID_PLATFORM_
    //card select
	wCardSelect = env->addWindow(rect<s32>(320 * xScale, 55 * yScale, 1000 * xScale, 400 * yScale), false, L"");
	wCardSelect->getCloseButton()->setVisible(false);
	wCardSelect->setVisible(false);
        ChangeToIGUIImageWindow(wCardSelect, &bgCardSelect, imageManager.tDialog_L);
    stCardSelect = env->addStaticText(L"", rect<s32>(20 * xScale, 10 * yScale, 660 * xScale, 40 * yScale), false, false, wCardSelect, -1, false);
	stCardSelect->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
    for(int i = 0; i < 5; ++i) {
		stCardPos[i] = env->addStaticText(L"", rect<s32>((40 + 125 * i) * xScale, 40 * yScale, (139 + 125 * i) * xScale, 60 * yScale), true, false, wCardSelect, -1, true);
		stCardPos[i]->setBackgroundColor(0xffffffff);
		stCardPos[i]->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
		btnCardSelect[i] = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>((30 + 125 * i)  * xScale, 65 * yScale, (150 + 125 * i) * xScale, 235 * yScale), wCardSelect, BUTTON_CARD_0 + i);
		btnCardSelect[i]->setImageSize(core::dimension2di(CARD_IMG_WIDTH * 0.6f * xScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	}
	scrCardList = env->addScrollBar(true, rect<s32>(30 * xScale, 245 * yScale, 650 * xScale, 285 * yScale), wCardSelect, SCROLL_CARD_SELECT);
	btnSelectOK = env->addButton(rect<s32>(285 * xScale, 295 * yScale, 395 * xScale, 335 * yScale), wCardSelect, BUTTON_CARD_SEL_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnSelectOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//card display
	wCardDisplay = env->addWindow(rect<s32>(320 * xScale, 55 * yScale, 1000 * xScale, 400 * yScale), false, L"");
	wCardDisplay->getCloseButton()->setVisible(false);
	wCardDisplay->setVisible(false);
        ChangeToIGUIImageWindow(wCardDisplay, &bgCardDisplay, imageManager.tDialog_L);
    stCardDisplay = env->addStaticText(L"", rect<s32>(20 * xScale, 10 * yScale, 660 * xScale, 40 * yScale), false, false, wCardDisplay, -1, false);
    stCardDisplay->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
    for(int i = 0; i < 5; ++i) {
		stDisplayPos[i] = env->addStaticText(L"", rect<s32>((30 + 125 * i) *xScale, 40 * yScale, (150 + 125 * i) * xScale, 60 * yScale), true, false, wCardDisplay, -1, true);
		stDisplayPos[i]->setBackgroundColor(0xffffffff);
		stDisplayPos[i]->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
		btnCardDisplay[i] = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>((30 + 125 * i) * xScale, 65 * yScale, (150 + 125 * i) * xScale, 235 * yScale), wCardDisplay, BUTTON_DISPLAY_0 + i);
		btnCardDisplay[i]->setImageSize(core::dimension2di(CARD_IMG_WIDTH * 0.6f * xScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	}
	scrDisplayList = env->addScrollBar(true, rect<s32>(30 * xScale, 245 * yScale, 650 * xScale, 285 * yScale), wCardDisplay, SCROLL_CARD_DISPLAY);
	btnDisplayOK = env->addButton(rect<s32>(285 * xScale, 295 * yScale, 395 * xScale, 335 * yScale), wCardDisplay, BUTTON_CARD_DISP_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnDisplayOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
#endif
	//announce number
	wANNumber = env->addWindow(rect<s32>(500 * xScale, 50 * yScale, 800 * xScale, 470 * yScale), false, L"");
	wANNumber->getCloseButton()->setVisible(false);
	wANNumber->setVisible(false);
        ChangeToIGUIImageWindow(wANNumber, &bgANNumber, imageManager.tWindow_V);
	cbANNumber = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(30 * xScale, 180 * yScale, 270 * xScale, 240 * yScale), wANNumber, -1);
	cbANNumber->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    stANNumber = env->addStaticText(L"", rect<s32>(20 * xScale, 10 * yScale, 280 * xScale, 40 * yScale), false, false, wANNumber, -1, false);
    stANNumber->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
	for(int i = 0; i < 12; ++i) {
		myswprintf(strbuf, L"%d", i + 1);
		btnANNumber[i] = env->addButton(rect<s32>((50 + 70 * (i % 3)) * xScale, (60 + 70 * (i / 3)) * yScale, (110 + 70 * (i % 3)) * xScale, (120 + 70 * (i / 3)) * yScale), wANNumber, BUTTON_ANNUMBER_1 + i, strbuf);
            ChangeToIGUIImageButton(btnANNumber[i], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
		btnANNumber[i]->setIsPushButton(true);
	}
	btnANNumberOK = env->addButton(rect<s32>(95 * xScale, 360 * yScale, 205 * xScale, 410 * yScale), wANNumber, BUTTON_ANNUMBER_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnANNumberOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//announce card
	wANCard = env->addWindow(rect<s32>(500 * xScale, 50 * yScale, 800 * xScale, 550 * yScale), false, L"");
	wANCard->getCloseButton()->setVisible(false);
	wANCard->setVisible(false);
        ChangeToIGUIImageWindow(wANCard, &bgANCard, imageManager.tDialog_S);
	stANCard = env->addStaticText(L"", rect<s32>(20 * xScale, 10 * yScale, 280 * xScale, 40 * yScale), false, false, wANCard, -1, false);
	stANCard->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
    ebANCard = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(10 * xScale, 50 * yScale, 290 * xScale, 90 * yScale), wANCard, EDITBOX_ANCARD);
	ebANCard->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	lstANCard = env->addListBox(rect<s32>(10 * xScale, 100 * yScale, 290 * xScale, 430 * yScale), wANCard, LISTBOX_ANCARD, true);
	btnANCardOK = env->addButton(rect<s32>(95 * xScale, 440 * yScale, 205 * xScale, 490 * yScale), wANCard, BUTTON_ANCARD_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnANCardOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//announce attribute
	wANAttribute = env->addWindow(rect<s32>(470 * xScale, 160 * yScale, 860 * xScale, 380 * yScale), false, dataManager.GetSysString(562));
	wANAttribute->getCloseButton()->setVisible(false);
	wANAttribute->setVisible(false);
	    ChangeToIGUIImageWindow(wANAttribute, &bgANAttribute, imageManager.tDialog_L);
	stANAttribute = env->addStaticText(L"", rect<s32>(20 * xScale, 10 * yScale, 370 * xScale, 40 * yScale), false, false, wANAttribute, -1, false);
    stANAttribute->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
	for(int filter = 0x1, i = 0; i < 7; filter <<= 1, ++i)
		chkAttribute[i] = env->addCheckBox(false, rect<s32>((50 + (i % 4) * 80) * xScale, (60 + (i / 4) * 55) * yScale, (130 + (i % 4) * 80) * xScale, (90 + (i / 4) * 55) * yScale),
		                                   wANAttribute, CHECK_ATTRIBUTE, dataManager.FormatAttribute(filter).c_str());
	//announce race
	wANRace = env->addWindow(rect<s32>(500 * xScale, 40 * yScale, 800 * xScale, 560 * yScale), false, dataManager.GetSysString(563));
	wANRace->getCloseButton()->setVisible(false);
	wANRace->setVisible(false);
	    ChangeToIGUIImageWindow(wANRace, &bgANRace, imageManager.tDialog_S);
    stANRace = env->addStaticText(L"", rect<s32>(20 * xScale, 10 * yScale, 280 * xScale, 40 * yScale), false, false, wANRace, -1, false);
    stANRace->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
	for(int filter = 0x1, i = 0; i < RACES_COUNT; filter <<= 1, ++i)
		chkRace[i] = env->addCheckBox(false, rect<s32>((30 + (i % 3) * 90) * xScale, (60 + (i / 3) * 50) * yScale, (100 + (i % 3) * 90) * xScale, (110 + (i / 3) * 50) * yScale),
		                              wANRace, CHECK_RACE, dataManager.FormatRace(filter).c_str());
	//selection hint
	stHintMsg = env->addStaticText(L"", rect<s32>(500 * xScale, 90 * yScale, 820 * xScale, 120 * yScale), true, false, 0, -1, false);
	stHintMsg->setBackgroundColor(0xee11113d);
	stHintMsg->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	stHintMsg->setVisible(false);
	stTip = env->addStaticText(L"", rect<s32>(0 * xScale, 0 * yScale, 150 * xScale, 150 * yScale), false, true, 0, -1, true);
	stTip->setBackgroundColor(0x6011113d);
	stTip->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	stTip->setVisible(false);
	//cmd menu
	wCmdMenu = env->addWindow(rect<s32>(0, 0, 150 * xScale, 600 * yScale), false, L"");
	wCmdMenu->setDrawTitlebar(false);
	wCmdMenu->setDrawBackground(false);
	wCmdMenu->setVisible(false);
	wCmdMenu->getCloseButton()->setVisible(false);
	btnActivate = env->addButton(rect<s32>(0, 0, 150 * xScale, 60 * yScale), wCmdMenu, BUTTON_CMD_ACTIVATE, dataManager.GetSysString(1150));
        ChangeToIGUIImageButton(btnActivate, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSummon = env->addButton(rect<s32>(0, 60 * yScale, 150 * xScale, 120 * yScale), wCmdMenu, BUTTON_CMD_SUMMON, dataManager.GetSysString(1151));
        ChangeToIGUIImageButton(btnSummon, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSPSummon = env->addButton(rect<s32>(0, 120 * yScale, 150  * xScale, 180 * yScale), wCmdMenu, BUTTON_CMD_SPSUMMON, dataManager.GetSysString(1152));
        ChangeToIGUIImageButton(btnSPSummon, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnMSet = env->addButton(rect<s32>(0, 180 * yScale, 150 * xScale, 240 * yScale), wCmdMenu, BUTTON_CMD_MSET, dataManager.GetSysString(1153));
        ChangeToIGUIImageButton(btnMSet, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSSet = env->addButton(rect<s32>(0, 240 * yScale, 150 * xScale, 300 * yScale), wCmdMenu, BUTTON_CMD_SSET, dataManager.GetSysString(1153));
        ChangeToIGUIImageButton(btnSSet, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnRepos = env->addButton(rect<s32>(0, 300 * yScale, 150 * xScale, 360 * yScale), wCmdMenu, BUTTON_CMD_REPOS, dataManager.GetSysString(1154));
        ChangeToIGUIImageButton(btnRepos, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnAttack = env->addButton(rect<s32>(0, 360 * yScale, 150 * xScale, 420 * yScale), wCmdMenu, BUTTON_CMD_ATTACK, dataManager.GetSysString(1157));
        ChangeToIGUIImageButton(btnAttack, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnShowList = env->addButton(rect<s32>(0, 420 * yScale, 150 * xScale, 480 * yScale), wCmdMenu, BUTTON_CMD_SHOWLIST, dataManager.GetSysString(1158));
        ChangeToIGUIImageButton(btnShowList, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnOperation = env->addButton(rect<s32>(0, 480 * yScale, 150 * xScale, 540 * yScale), wCmdMenu, BUTTON_CMD_ACTIVATE, dataManager.GetSysString(1161));
        ChangeToIGUIImageButton(btnOperation, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnReset = env->addButton(rect<s32>(0, 540 * yScale , 150 * xScale, 600 * yScale), wCmdMenu, BUTTON_CMD_RESET, dataManager.GetSysString(1162));
        ChangeToIGUIImageButton(btnReset, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//deck edit
	wDeckEdit = env->addWindow(rect<s32>(310 * xScale, 1 * yScale, 600 * xScale, 130 * yScale), false, L"");
    wDeckEdit->getCloseButton()->setVisible(false);
    wDeckEdit->setDrawTitlebar(false);
	wDeckEdit->setVisible(false);
	    ChangeToIGUIImageWindow(wDeckEdit, &bgDeckEdit, imageManager.tDialog_L);
	btnManageDeck = env->addButton(rect<s32>(10 * xScale, 35 * yScale, 220 * xScale, 75 * yScale), wDeckEdit, BUTTON_MANAGE_DECK, dataManager.GetSysString(1460));
    //deck manage
	wDeckManage = env->addWindow(rect<s32>(530 * xScale, 10 * yScale, 920 * xScale, 460 * yScale), false, dataManager.GetSysString(1460), 0, WINDOW_DECK_MANAGE);
	wDeckManage->setVisible(false);
	wDeckManage->getCloseButton()->setVisible(false);
	    ChangeToIGUIImageWindow(wDeckManage, &bgDeckManage, imageManager.tWindow_V);
	lstCategories = env->addListBox(rect<s32>(20 * xScale, 20 * yScale, 110 * xScale, 430 * yScale), wDeckManage, LISTBOX_CATEGORIES, true);
    lstCategories->setItemHeight(30 * yScale);
	lstDecks = env->addListBox(rect<s32>(115 * xScale, 20 * yScale, 280 * xScale, 430 * yScale), wDeckManage, LISTBOX_DECKS, true);
	lstCategories->setItemHeight(30 * yScale);
	btnNewCategory = env->addButton(rect<s32>(290 * xScale, 20 * yScale, 370 * xScale, 60 * yScale), wDeckManage, BUTTON_NEW_CATEGORY, dataManager.GetSysString(1461));
        ChangeToIGUIImageButton(btnNewCategory, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnRenameCategory = env->addButton(rect<s32>(290 * xScale, 65 * yScale, 370 * xScale, 105 * yScale), wDeckManage, BUTTON_RENAME_CATEGORY, dataManager.GetSysString(1462));
        ChangeToIGUIImageButton(btnRenameCategory, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnDeleteCategory = env->addButton(rect<s32>(290 * xScale, 110 * yScale, 370 * xScale, 150 * yScale), wDeckManage, BUTTON_DELETE_CATEGORY, dataManager.GetSysString(1463));
        ChangeToIGUIImageButton(btnDeleteCategory, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnNewDeck = env->addButton(rect<s32>(290 * xScale, 155 * yScale, 370 * xScale, 195 * yScale), wDeckManage, BUTTON_NEW_DECK, dataManager.GetSysString(1464));
        ChangeToIGUIImageButton(btnNewDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnRenameDeck = env->addButton(rect<s32>(290 * xScale, 200 * yScale, 370 * xScale, 240 * yScale), wDeckManage, BUTTON_RENAME_DECK, dataManager.GetSysString(1465));
        ChangeToIGUIImageButton(btnRenameDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnDMDeleteDeck = env->addButton(rect<s32>(290 * xScale, 245 * yScale, 370 * xScale, 285 * yScale), wDeckManage, BUTTON_DELETE_DECK_DM, dataManager.GetSysString(1466));
    ChangeToIGUIImageButton(btnDMDeleteDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnMoveDeck = env->addButton(rect<s32>(290 * xScale, 290 * yScale, 370 * xScale, 330 * yScale), wDeckManage, BUTTON_MOVE_DECK, dataManager.GetSysString(1467));
        ChangeToIGUIImageButton(btnMoveDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnCopyDeck = env->addButton(rect<s32>(290 * xScale, 335 * yScale, 370 * xScale, 375 * yScale), wDeckManage, BUTTON_COPY_DECK, dataManager.GetSysString(1468));
        ChangeToIGUIImageButton(btnCopyDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnCloseDM = env->addButton(rect<s32>(290 * xScale, 390 * yScale, 370 * xScale, 430 * yScale), wDeckManage, BUTTON_CLOSE_DECKMANAGER, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnCloseDM, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//deck manage query
	wDMQuery = env->addWindow(rect<s32>(490 * xScale, 180 * yScale, 840 * xScale, 340 * yScale), false, dataManager.GetSysString(1460));
	wDMQuery->getCloseButton()->setVisible(false);
	wDMQuery->setVisible(false);
	wDMQuery->setDraggable(false);
	    ChangeToIGUIImageWindow(wDMQuery, &bgDMQuery, imageManager.tDialog_L);
	stDMMessage = env->addStaticText(L"", rect<s32>(20 * xScale, 25 * yScale, 290 * xScale, 45 * yScale), false, false, wDMQuery);
	stDMMessage2 = env->addStaticText(L"", rect<s32>(20 * xScale, 50 * yScale, 330 * xScale, 90 * yScale), false, false, wDMQuery, -1, true);
	stDMMessage2->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	ebDMName = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(20 * xScale, 50 * yScale, 330 * xScale, 90 * yScale), wDMQuery, -1);
	ebDMName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	cbDMCategory = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(20 * xScale, 50 * yScale, 330 * xScale, 90 * yScale), wDMQuery, -1);
	stDMMessage2->setVisible(false);
	ebDMName->setVisible(false);
	cbDMCategory->setVisible(false);
	cbDMCategory->setMaxSelectionRows(10);
	btnDMOK = env->addButton(rect<s32>(70 * xScale, 100 * yScale, 160 * xScale, 150 * yScale), wDMQuery, BUTTON_DM_OK, dataManager.GetSysString(1211));
	    ChangeToIGUIImageButton(btnDMOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnDMCancel = env->addButton(rect<s32>(180 * xScale, 100 * yScale, 270 * xScale, 150 * yScale), wDMQuery, BUTTON_DM_CANCEL, dataManager.GetSysString(1212));
	scrPackCards = env->addScrollBar(false, recti(775 * xScale, 161 * yScale, 795 * xScale, 629 * yScale), 0, SCROLL_FILTER);
	scrPackCards->setLargeStep(1);
	scrPackCards->setSmallStep(1);
	scrPackCards->setVisible(false);
        ChangeToIGUIImageButton(btnDMCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);
	stDBCategory = env->addStaticText(dataManager.GetSysString(1300), rect<s32>(10 * xScale, 10 * yScale, 60 * xScale, 50 * yScale), false, false, wDeckEdit);
	cbDBCategory = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(0, 0, 0, 0), wDeckEdit, COMBOBOX_DBCATEGORY);
	cbDBCategory->setMaxSelectionRows(15);
	cbDBDecks = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(0, 0, 0, 0), wDeckEdit, COMBOBOX_DBDECKS);
	cbDBDecks->setMaxSelectionRows(15);
	btnSaveDeck = env->addButton(rect<s32>(225 * xScale, 35 * yScale, 280 * xScale, 75 * yScale), wDeckEdit, BUTTON_SAVE_DECK, dataManager.GetSysString(1302));
        ChangeToIGUIImageButton(btnSaveDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	ebDeckname = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(10 * xScale, 80 * yScale, 220 * xScale, 120 * yScale), wDeckEdit, -1);
	ebDeckname->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnSaveDeckAs = env->addButton(rect<s32>(225 * xScale, 80 * yScale, 280 * xScale, 120 * yScale), wDeckEdit, BUTTON_SAVE_DECK_AS, dataManager.GetSysString(1303));
        ChangeToIGUIImageButton(btnSaveDeckAs, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnDeleteDeck = env->addButton(rect<s32>((3 + CARD_IMG_WIDTH) * yScale, 245 * yScale, 310 * yScale, 285 * yScale), 0, BUTTON_DELETE_DECK, dataManager.GetSysString(1308));
        ChangeToIGUIImageButton(btnDeleteDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnShuffleDeck = env->addButton(rect<s32>((3 + CARD_IMG_WIDTH) * yScale, 70 * yScale, 310 * yScale, 110 * yScale), 0, BUTTON_SHUFFLE_DECK, dataManager.GetSysString(1307));
        ChangeToIGUIImageButton(btnShuffleDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSortDeck = env->addButton(rect<s32>((3 + CARD_IMG_WIDTH) * yScale, 115 * yScale, 310 * yScale, 155 * yScale), 0, BUTTON_SORT_DECK, dataManager.GetSysString(1305));
        ChangeToIGUIImageButton(btnSortDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnClearDeck = env->addButton(rect<s32>((3 + CARD_IMG_WIDTH) * yScale, 200 * yScale, 310 * yScale, 240 * yScale), 0, BUTTON_CLEAR_DECK, dataManager.GetSysString(1304));
        ChangeToIGUIImageButton(btnClearDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
    btnDeleteDeck->setVisible(false);
    btnShuffleDeck->setVisible(false);
    btnSortDeck->setVisible(false);
    btnClearDeck->setVisible(false);
	btnSideOK = env->addButton(rect<s32>(400 * xScale, 40 * yScale, 710 * xScale, 80 * yScale), 0, BUTTON_SIDE_OK, dataManager.GetSysString(1334));
        ChangeToIGUIImageButton(btnSideOK, imageManager.tButton_L, imageManager.tButton_L_pressed);
	btnSideOK->setVisible(false);
	btnSideShuffle = env->addButton(rect<s32>(310 * xScale, 100 * yScale, 370 * xScale, 130 * yScale), 0, BUTTON_SHUFFLE_DECK, dataManager.GetSysString(1307));
        ChangeToIGUIImageButton(btnSideShuffle, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSideShuffle->setVisible(false);
        ChangeToIGUIImageButton(btnSideShuffle, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSideSort = env->addButton(rect<s32>(375 * xScale, 100 * yScale, 435 * xScale, 130 * yScale), 0, BUTTON_SORT_DECK, dataManager.GetSysString(1305));
        ChangeToIGUIImageButton(btnSideSort, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSideSort->setVisible(false);
        ChangeToIGUIImageButton(btnSideSort, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSideReload = env->addButton(rect<s32>(440 * xScale, 100 * yScale, 500 * xScale, 130 * yScale), 0, BUTTON_SIDE_RELOAD, dataManager.GetSysString(1309));
        ChangeToIGUIImageButton(btnSideReload, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSideReload->setVisible(false);
	//sort type
	wSort = env->addStaticText(L"", rect<s32>(930 * xScale, 132 * yScale, 1020 * xScale, 156 * yScale), true, false, 0, -1, true);
	cbSortType = env->addComboBox(rect<s32>(10 * xScale, 2 * yScale, 85 * xScale, 22 * yScale), wSort, COMBOBOX_SORTTYPE);
	cbSortType->setMaxSelectionRows(10);
	for(int i = 1370; i <= 1373; i++)
		cbSortType->addItem(dataManager.GetSysString(i));
	wSort->setVisible(false);
#ifdef _IRR_ANDROID_PLATFORM_
    //filters
	wFilter = env->addWindow(rect<s32>(610 * xScale, 1 * yScale, 1020 * xScale, 130 * yScale), false, L"");
	wFilter->getCloseButton()->setVisible(false);
    wFilter->setDrawTitlebar(false);
	wFilter->setDraggable(false);
	wFilter->setVisible(false);
	    ChangeToIGUIImageWindow(wFilter, &bgFilter, imageManager.tDialog_L);
	env->addStaticText(dataManager.GetSysString(1311), rect<s32>(10 * xScale, 5 * yScale, 70 * xScale, 25 * yScale), false, false, wFilter);
	cbCardType = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(60 * xScale, 3 * yScale, 120 * xScale, 23 * yScale), wFilter, COMBOBOX_MAINTYPE);
	cbCardType->addItem(dataManager.GetSysString(1310));
	cbCardType->addItem(dataManager.GetSysString(1312));
	cbCardType->addItem(dataManager.GetSysString(1313));
	cbCardType->addItem(dataManager.GetSysString(1314));
	cbCardType2 = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(125 * xScale, 3 * yScale, 200 * xScale, 23 * yScale), wFilter, COMBOBOX_SECONDTYPE);
	cbCardType2->addItem(dataManager.GetSysString(1310), 0);
	env->addStaticText(dataManager.GetSysString(1315), rect<s32>(205 * xScale, 5 * yScale, 280 * xScale, 25 * yScale), false, false, wFilter);
	cbLimit = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(260 * xScale, 3 * yScale, 390 * xScale, 23 * yScale), wFilter, COMBOBOX_LIMIT);
	cbLimit->addItem(dataManager.GetSysString(1310));
	cbLimit->addItem(dataManager.GetSysString(1316));
	cbLimit->addItem(dataManager.GetSysString(1317));
	cbLimit->addItem(dataManager.GetSysString(1318));
	cbLimit->addItem(dataManager.GetSysString(1481));
	cbLimit->addItem(dataManager.GetSysString(1482));
	cbLimit->addItem(dataManager.GetSysString(1483));
	cbLimit->addItem(dataManager.GetSysString(1484));
	cbLimit->addItem(dataManager.GetSysString(1485));
	env->addStaticText(dataManager.GetSysString(1319), rect<s32>(10 * xScale, 28 * yScale, 70 * xScale, 48 * yScale), false, false, wFilter);
	cbAttribute = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(60 * xScale, 26 * yScale, 190 * xScale, 46 * yScale), wFilter, COMBOBOX_ATTRIBUTE);
	cbAttribute->setMaxSelectionRows(10);
	cbAttribute->addItem(dataManager.GetSysString(1310), 0);
	for(int filter = 0x1; filter != 0x80; filter <<= 1)
		cbAttribute->addItem(dataManager.FormatAttribute(filter).c_str(), filter);
	env->addStaticText(dataManager.GetSysString(1321), rect<s32>(10 * xScale, 51 * yScale, 70 * xScale, 71 * yScale), false, false, wFilter);
	cbRace = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(60 * xScale, (40 + 75 / 6) * yScale, 190 * xScale, (60 + 75 / 6) * yScale), wFilter, COMBOBOX_RACE);
	cbRace->setMaxSelectionRows(10);
	cbRace->addItem(dataManager.GetSysString(1310), 0);
	for(int filter = 0x1; filter < (1 << RACES_COUNT); filter <<= 1)
		cbRace->addItem(dataManager.FormatRace(filter).c_str(), filter);
	env->addStaticText(dataManager.GetSysString(1322), rect<s32>(205 * xScale, 28 * yScale, 280 * xScale, 48 * yScale), false, false, wFilter);
	ebAttack = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(260 * xScale, 26 * yScale, 340 * xScale, 46 * yScale), wFilter, EDITBOX_INPUTS);
	ebAttack->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1323), rect<s32>(205 * xScale, 51 * yScale, 280 * xScale, 71 * yScale), false, false, wFilter);
	ebDefense = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(260 * xScale, 49 * yScale, 340 * xScale, 69 * yScale), wFilter, EDITBOX_INPUTS);
	ebDefense->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1324), rect<s32>(10 * xScale, 74 * yScale, 80 * xScale, 94 * yScale), false, false, wFilter);
	ebStar = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(60 * xScale, (60 + 100 / 6) * yScale, 100 * xScale, (80 + 100 / 6) * yScale), wFilter, EDITBOX_INPUTS);
	ebStar->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1336), rect<s32>(101 * xScale, (60 + 100 / 6) * yScale, 150 * xScale, (82 + 100 / 6) * yScale), false, false, wFilter);
	ebScale = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(150 * xScale, (60 + 100 / 6)  * yScale, 190 * xScale, (80 + 100 / 6) * yScale), wFilter, EDITBOX_INPUTS);
	ebScale->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1325), rect<s32>(205 * xScale, (60 + 100 / 6) * yScale, 280 * xScale, (82 + 100 / 6) * yScale), false, false, wFilter);
	ebCardName = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(260 * xScale, 72 * yScale, 390 * xScale, 92 * yScale), wFilter, EDITBOX_KEYWORD);
	ebCardName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnEffectFilter = env->addButton(rect<s32>(345 * xScale, 28 * yScale, 390 * xScale, 69 * yScale), wFilter, BUTTON_EFFECT_FILTER, dataManager.GetSysString(1326));
		ChangeToIGUIImageButton(btnEffectFilter, imageManager.tButton_C, imageManager.tButton_C_pressed);
	btnStartFilter = env->addButton(rect<s32>(210 * xScale, 96 * yScale, 390 * xScale, 118 * yScale), wFilter, BUTTON_START_FILTER, dataManager.GetSysString(1327));
		ChangeToIGUIImageButton(btnStartFilter, imageManager.tButton_L, imageManager.tButton_L_pressed);
	if(gameConf.separate_clear_button) {
		btnStartFilter->setRelativePosition(rect<s32>(260 * xScale, (80 + 125 / 6) * yScale, 390 * xScale, (100 + 125 / 6) * yScale));
		btnClearFilter = env->addButton(rect<s32>(205 * xScale, (80 + 125 / 6) * yScale, 255 * xScale, (100 + 125 / 6) * yScale), wFilter, BUTTON_CLEAR_FILTER, dataManager.GetSysString(1304));
			ChangeToIGUIImageButton(btnClearFilter, imageManager.tButton_S, imageManager.tButton_S_pressed);
	}
	wCategories = env->addWindow(rect<s32>(600 * xScale, 60 * yScale, 1000 * xScale, 440 * yScale), false, L"");
	wCategories->getCloseButton()->setVisible(false);
	wCategories->setDrawTitlebar(false);
	wCategories->setDraggable(false);
	wCategories->setVisible(false);
	    ChangeToIGUIImageWindow(wCategories, &bgCategories, imageManager.tWindow_V);
	btnCategoryOK = env->addButton(rect<s32>(135 * xScale, 340 * yScale, 235 * xScale, 370 * yScale), wCategories, BUTTON_CATEGORY_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnCategoryOK, imageManager.tButton_L, imageManager.tButton_L_pressed);
    for(int i = 0; i < 32; ++i)
		chkCategory[i] = env->addCheckBox(false, recti((20 + (i % 4) * 90)  * xScale, (10 + (i / 4) * 40) * yScale, (120 + (i % 4) * 90) * xScale, (40 + (i / 4) * 40) * yScale), wCategories, -1, dataManager.GetSysString(1100 + i));
	scrFilter = env->addScrollBar(false, recti(980 * xScale, 159 * yScale, 1020 * xScale, 629 * yScale), 0, SCROLL_FILTER);
	scrFilter->setLargeStep(10);
	scrFilter->setSmallStep(1);
	scrFilter->setVisible(false);
#endif

#ifdef _IRR_ANDROID_PLATFORM_
    //LINK MARKER SEARCH
	btnMarksFilter = env->addButton(rect<s32>(60 * xScale, (80 + 125 / 6) * yScale, 190 * xScale, (100 + 125 / 6) * yScale), wFilter, BUTTON_MARKS_FILTER, dataManager.GetSysString(1374));
 	   ChangeToIGUIImageButton(btnMarksFilter, imageManager.tButton_L, imageManager.tButton_L_pressed);
	wLinkMarks = env->addWindow(rect<s32>(600 * xScale, 30 * yScale, 820 * xScale, 250 * yScale), false, L"");
	wLinkMarks->getCloseButton()->setVisible(false);
	wLinkMarks->setDrawTitlebar(false);
	wLinkMarks->setDraggable(false);
	wLinkMarks->setVisible(false);
	    ChangeToIGUIImageWindow(wLinkMarks, &bgLinkMarks, imageManager.tWindow_V);
	btnMarksOK = env->addButton(recti(80 * xScale, 80 * yScale, 140 * xScale, 140 * yScale), wLinkMarks, BUTTON_MARKERS_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnMarksOK, imageManager.tButton_C, imageManager.tButton_C_pressed);
	btnMark[0] = env->addButton(recti(10 * xScale, 10 * yScale, 70 * xScale, 70 * yScale), wLinkMarks, -1, L"\u2196");
        ChangeToIGUIImageButton(btnMark[0], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[1] = env->addButton(recti(80 * xScale, 10 * yScale, 140 * xScale, 70 * yScale), wLinkMarks, -1, L"\u2191");
        ChangeToIGUIImageButton(btnMark[1], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[2] = env->addButton(recti(150 * xScale, 10 * yScale, 210 * xScale, 70 * yScale), wLinkMarks, -1, L"\u2197");
        ChangeToIGUIImageButton(btnMark[2], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[3] = env->addButton(recti(10 * xScale, 80 * yScale, 70 * xScale, 140 * yScale), wLinkMarks, -1, L"\u2190");
        ChangeToIGUIImageButton(btnMark[3], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[4] = env->addButton(recti(150 * xScale, 80 * yScale, 210 * xScale, 140 * yScale), wLinkMarks, -1, L"\u2192");
        ChangeToIGUIImageButton(btnMark[4], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[5] = env->addButton(recti(10 * xScale, 150 * yScale, 70 * xScale, 210 * yScale), wLinkMarks, -1, L"\u2199");
        ChangeToIGUIImageButton(btnMark[5], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[6] = env->addButton(recti(80 * xScale, 150 * yScale, 140 * xScale, 210 * yScale), wLinkMarks, -1, L"\u2193");
        ChangeToIGUIImageButton(btnMark[6], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[7] = env->addButton(recti(150 * xScale, 150 * yScale, 210 * xScale, 210 * yScale), wLinkMarks, -1, L"\u2198");
        ChangeToIGUIImageButton(btnMark[7], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	for(int i=0;i<8;i++)
		btnMark[i]->setIsPushButton(true);
	//replay window
	wReplay = env->addWindow(rect<s32>(220 * xScale, 100 * yScale, 800 * xScale, 520 * yScale), false, dataManager.GetSysString(1202));
	wReplay->getCloseButton()->setVisible(false);
	wReplay->setDrawBackground(false);
	wReplay->setVisible(false);
	bgReplay = env->addImage(rect<s32>(0, 0, 580 * xScale, 420 * yScale), wReplay, -1, 0, true);
	bgReplay->setImage(imageManager.tWindow);
    bgReplay->setScaleImage(true);
	lstReplayList = env->addListBox(rect<s32>(20 * xScale, 30 * yScale, 310 * xScale, 400 * yScale), wReplay, LISTBOX_REPLAY_LIST, true);
	lstReplayList->setItemHeight(30 * yScale);
	btnLoadReplay = env->addButton(rect<s32>(440 * xScale, 310 * yScale, 550 * xScale, 350 * yScale), wReplay, BUTTON_LOAD_REPLAY, dataManager.GetSysString(1348));
        ChangeToIGUIImageButton(btnLoadReplay, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnDeleteReplay = env->addButton(rect<s32>(320 * xScale, 310 * yScale, 430 * xScale, 350 * yScale), wReplay, BUTTON_DELETE_REPLAY, dataManager.GetSysString(1361));
        ChangeToIGUIImageButton(btnDeleteReplay, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnRenameReplay = env->addButton(rect<s32>(320 * xScale, 360 * yScale, 430 * xScale, 400 * yScale), wReplay, BUTTON_RENAME_REPLAY, dataManager.GetSysString(1362));
        ChangeToIGUIImageButton(btnRenameReplay, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnReplayCancel = env->addButton(rect<s32>(440 * xScale, 360 * yScale, 550 * xScale, 400 * yScale), wReplay, BUTTON_CANCEL_REPLAY, dataManager.GetSysString(1347));
        ChangeToIGUIImageButton(btnReplayCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);
	env->addStaticText(dataManager.GetSysString(1349), rect<s32>(320 * xScale, 30 * yScale, 550 * xScale, 50 * yScale), false, true, wReplay);
	stReplayInfo = env->addStaticText(L"", rect<s32>(320 * xScale, 60 * yScale, 570 * xScale, 315 * yScale), false, true, wReplay);
	env->addStaticText(dataManager.GetSysString(1353), rect<s32>(320 * xScale, 180 * yScale, 550 * xScale, 200 * yScale), false, true, wReplay);
	ebRepStartTurn = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(320 * xScale, 210 * yScale, 430 * xScale, 250 * yScale), wReplay, -1);
	ebRepStartTurn->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnExportDeck = env->addButton(rect<s32>(440 * xScale, 260 * yScale, 550 * xScale, 300 * yScale), wReplay, BUTTON_EXPORT_DECK, dataManager.GetSysString(1369));
        ChangeToIGUIImageButton(btnExportDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnShareReplay = env->addButton(rect<s32>(320 * xScale, 260 * yScale, 430 * xScale, 300 * yScale), wReplay, BUTTON_SHARE_REPLAY, dataManager.GetSysString(1368));
		ChangeToIGUIImageButton(btnShareReplay, imageManager.tButton_S, imageManager.tButton_S_pressed);
        //single play window
	wSinglePlay = env->addWindow(rect<s32>(220 * xScale, 100 * yScale, 800 * xScale, 520 * yScale), false, dataManager.GetSysString(1201));
	wSinglePlay->getCloseButton()->setVisible(false);
	wSinglePlay->setDrawBackground(false);
	wSinglePlay->setVisible(false);
    bgSinglePlay = env->addImage(rect<s32>(0, 0, 580 * xScale, 420 * yScale), wSinglePlay, -1, 0, true);
    bgSinglePlay->setImage(imageManager.tWindow);
    bgSinglePlay->setScaleImage(true);
    irr::gui::IGUITabControl* wSingle = env->addTabControl(rect<s32>(20 * xScale, 20 * yScale, 570 * xScale, 400 * yScale), wSinglePlay, false, false);
	wSingle->setTabHeight(40 * yScale);
	//TEST BOT MODE
	if(gameConf.enable_bot_mode) {
		irr::gui::IGUITab* tabBot = wSingle->addTab(dataManager.GetSysString(1380));
		lstBotList = env->addListBox(rect<s32>(0, 0, 300 * xScale, 330 * yScale), tabBot, LISTBOX_BOT_LIST, true);
		lstBotList->setItemHeight(30 * yScale);
		btnStartBot = env->addButton(rect<s32>(420 * xScale, 240 * yScale, 530 * xScale, 280 * yScale), tabBot, BUTTON_BOT_START, dataManager.GetSysString(1211));
            ChangeToIGUIImageButton(btnStartBot, imageManager.tButton_S, imageManager.tButton_S_pressed);
		btnBotCancel = env->addButton(rect<s32>(420 * xScale, 290 * yScale, 530 * xScale, 330 * yScale), tabBot, BUTTON_CANCEL_SINGLEPLAY, dataManager.GetSysString(1210));
            ChangeToIGUIImageButton(btnBotCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);
		env->addStaticText(dataManager.GetSysString(1382), rect<s32>(310 * xScale, 10 * yScale, 500 * xScale, 30 * yScale), false, true, tabBot);
		stBotInfo = env->addStaticText(L"", rect<s32>(310 * xScale, 40 * yScale, 560 * xScale, 160 * yScale), false, true, tabBot);
		cbBotDeckCategory =  env->addComboBox(rect<s32>(0, 0, 0, 0), tabBot, COMBOBOX_BOT_DECKCATEGORY);
		cbBotDeckCategory->setVisible(false);
		cbBotDeck =  env->addComboBox(rect<s32>(0, 0, 0, 0), tabBot);
		cbBotDeck->setVisible(false);
        btnBotDeckSelect = env->addButton(rect<s32>(310 * xScale, 110 * yScale, 530 * xScale, 150 * yScale), tabBot, BUTTON_BOT_DECK_SELECT, L"");
		btnBotDeckSelect->setVisible(false);
		cbBotRule =  CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(310 * xScale, 160 * yScale, 530 * xScale, 200 * yScale), tabBot, COMBOBOX_BOT_RULE);
		cbBotRule->addItem(dataManager.GetSysString(1262));
		cbBotRule->addItem(dataManager.GetSysString(1263));
		cbBotRule->addItem(dataManager.GetSysString(1264));
		cbBotRule->setSelected(gameConf.default_rule - 3);
		chkBotHand = env->addCheckBox(false, rect<s32>(310 * xScale, 210 * yScale, 410 * xScale, 240 * yScale), tabBot, -1, dataManager.GetSysString(1384));
		chkBotNoCheckDeck = env->addCheckBox(false, rect<s32>(310 * xScale, 250 * yScale, 410 * xScale, 280 * yScale), tabBot, -1, dataManager.GetSysString(1229));
		chkBotNoShuffleDeck = env->addCheckBox(false, rect<s32>(310 * xScale, 290 * yScale, 410 * xScale, 320 * yScale), tabBot, -1, dataManager.GetSysString(1230));
	} else { // avoid null object reference
		btnStartBot = env->addButton(rect<s32>(0, 0, 0, 0), wSinglePlay);
		btnBotCancel = env->addButton(rect<s32>(0, 0, 0, 0), wSinglePlay);
		btnStartBot->setVisible(false);
		btnBotCancel->setVisible(false);
	}
	//SINGLE MODE
	irr::gui::IGUITab* tabSingle = wSingle->addTab(dataManager.GetSysString(1381));
	lstSinglePlayList = env->addListBox(rect<s32>(0, 0, 300 * xScale, 330 * yScale), tabSingle, LISTBOX_SINGLEPLAY_LIST, true);
	lstSinglePlayList->setItemHeight(30 * yScale);
	btnLoadSinglePlay = env->addButton(rect<s32>(420 * xScale, 240 * yScale, 530 * xScale, 280 * yScale), tabSingle, BUTTON_LOAD_SINGLEPLAY, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnLoadSinglePlay, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSinglePlayCancel = env->addButton(rect<s32>(420 * xScale, 290 * yScale, 530 * xScale, 330 * yScale),tabSingle, BUTTON_CANCEL_SINGLEPLAY, dataManager.GetSysString(1210));
        ChangeToIGUIImageButton(btnSinglePlayCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);
	env->addStaticText(dataManager.GetSysString(1352), rect<s32>(310 * xScale, 10 * yScale, 500 * xScale, 30 * yScale), false, true, tabSingle);
	stSinglePlayInfo = env->addStaticText(L"", rect<s32>(310 * xScale, 40 * yScale, 560 * xScale, 80 * yScale), false, true, tabSingle);
	chkSinglePlayReturnDeckTop = env->addCheckBox(false, rect<s32>(310 * xScale, 200 * yScale, 560 * xScale, 230 * yScale), tabSingle, -1, dataManager.GetSysString(1238));

	//replay save
	wReplaySave = env->addWindow(rect<s32>(470 * xScale, 180 * yScale, 860 * xScale, 360 * yScale), false, dataManager.GetSysString(1340));
	wReplaySave->getCloseButton()->setVisible(false);
	wReplaySave->setVisible(false);
        ChangeToIGUIImageWindow(wReplaySave, &bgReplaySave, imageManager.tDialog_L);
	env->addStaticText(dataManager.GetSysString(1342), rect<s32>(20 * xScale, 25 * yScale, 290 * xScale, 45 * yScale), false, false, wReplaySave);
	ebRSName = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(20 * xScale, 50 * yScale, 370 * xScale, 90 * yScale), wReplaySave, -1);
	ebRSName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnRSYes = env->addButton(rect<s32>(70 * xScale, 120 * yScale, 180 * xScale, 170 * yScale), wReplaySave, BUTTON_REPLAY_SAVE, dataManager.GetSysString(1341));
        ChangeToIGUIImageButton(btnRSYes, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnRSNo = env->addButton(rect<s32>(210 * xScale, 120 * yScale, 320 * xScale, 170 * yScale), wReplaySave, BUTTON_REPLAY_CANCEL, dataManager.GetSysString(1212));
        ChangeToIGUIImageButton(btnRSNo, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//replay control
	wReplayControl = env->addWindow(rect<s32>(200 * yScale, 5 * yScale, 310 * yScale, 270 * yScale), false, L"");
	wReplayControl->getCloseButton()->setVisible(false);
	wReplayControl->setDrawBackground(false);
	wReplayControl->setDraggable(false);
	wReplayControl->setVisible(false);
	btnReplayStart = env->addButton(rect<s32>(0, 0, 110 * yScale, 40 * yScale), wReplayControl, BUTTON_REPLAY_START, dataManager.GetSysString(1343));
        ChangeToIGUIImageButton(btnReplayStart, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnReplayPause = env->addButton(rect<s32>(0, 45 * yScale, 110 * yScale, 85 * yScale), wReplayControl, BUTTON_REPLAY_PAUSE, dataManager.GetSysString(1344));
        ChangeToIGUIImageButton(btnReplayPause, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnReplayStep = env->addButton(rect<s32>(0, 90 * yScale, 110 * yScale, 130 * yScale), wReplayControl, BUTTON_REPLAY_STEP, dataManager.GetSysString(1345));
        ChangeToIGUIImageButton(btnReplayStep, imageManager.tButton_S, imageManager.tButton_S_pressed);
    btnReplayUndo = env->addButton(rect<s32>(0, 135 * yScale, 110 * yScale, 175 * yScale), wReplayControl, BUTTON_REPLAY_UNDO, dataManager.GetSysString(1360));
        ChangeToIGUIImageButton(btnReplayUndo, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnReplaySwap = env->addButton(rect<s32>(0, 180 * yScale, 110 * yScale, 220 * yScale), wReplayControl, BUTTON_REPLAY_SWAP, dataManager.GetSysString(1346));
        ChangeToIGUIImageButton(btnReplaySwap, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnReplayExit = env->addButton(rect<s32>(0, 225 * yScale, 110 * yScale, 265 * yScale), wReplayControl, BUTTON_REPLAY_EXIT, dataManager.GetSysString(1347));
        ChangeToIGUIImageButton(btnReplayExit, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//chat
    imgChat = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(0 * yScale, 300 * yScale, 45 * yScale, 345 * yScale), wPallet, BUTTON_CHATTING);
    imgChat->setImageSize(core::dimension2di(28 * yScale, 28 * yScale));
    if (gameConf.chkIgnore1) {
        imgChat->setImage(imageManager.tShut);
    } else {
        imgChat->setImage(imageManager.tTalk);
    }
	wChat = env->addWindow(rect<s32>(305 * xScale, 605 * yScale, 1020 * xScale, 640 * yScale), false, L"");
	wChat->getCloseButton()->setVisible(false);
	wChat->setDraggable(false);
	wChat->setDrawTitlebar(false);
	wChat->setVisible(false);
	ebChatInput = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(3 * xScale, 2 * yScale, 710 * xScale, 28 * yScale), wChat, EDITBOX_CHAT);
	//swap
	btnSpectatorSwap = env->addButton(rect<s32>((3 + CARD_IMG_WIDTH) * yScale, 70 * yScale, 310 * yScale, 110 * yScale), 0, BUTTON_REPLAY_SWAP, dataManager.GetSysString(1346));
        ChangeToIGUIImageButton(btnSpectatorSwap, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSpectatorSwap->setVisible(false);
	//chain buttons
	btnChainIgnore = env->addButton(rect<s32>((3 + CARD_IMG_WIDTH) * yScale, 70 * yScale, 310 * yScale, 110 * yScale), 0, BUTTON_CHAIN_IGNORE, dataManager.GetSysString(1292));
        ChangeToIGUIImageButton(btnChainIgnore, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnChainAlways = env->addButton(rect<s32>((3 + CARD_IMG_WIDTH) * yScale, 115 * yScale, 310 * yScale, 155 * yScale), 0, BUTTON_CHAIN_ALWAYS, dataManager.GetSysString(1293));
        ChangeToIGUIImageButton(btnChainAlways, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnChainWhenAvail = env->addButton(rect<s32>((3 + CARD_IMG_WIDTH) * yScale, 160 * yScale, 310 * yScale, 200 * yScale), 0, BUTTON_CHAIN_WHENAVAIL, dataManager.GetSysString(1294));
        ChangeToIGUIImageButton(btnChainWhenAvail, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnChainIgnore->setIsPushButton(true);
	btnChainAlways->setIsPushButton(true);
	btnChainWhenAvail->setIsPushButton(true);
	btnChainIgnore->setVisible(false);
	btnChainAlways->setVisible(false);
	btnChainWhenAvail->setVisible(false);
	//shuffle
	btnShuffle = env->addButton(rect<s32>((3 + CARD_IMG_WIDTH) * yScale, 205 * yScale, 310 * yScale, 245 * yScale), 0, BUTTON_CMD_SHUFFLE, dataManager.GetSysString(1297));
        ChangeToIGUIImageButton(btnShuffle, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnShuffle->setVisible(false);
	//cancel or finish
	btnCancelOrFinish = env->addButton(rect<s32>((3 + CARD_IMG_WIDTH) * yScale, 205 * yScale, 310 * yScale, 255 * yScale), 0, BUTTON_CANCEL_OR_FINISH, dataManager.GetSysString(1295));
        ChangeToIGUIImageButton(btnCancelOrFinish, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnCancelOrFinish->setVisible(false);
	soundManager = Utils::make_unique<SoundManager>();
	if(!soundManager->Init((double)gameConf.sound_volume / 100, (double)gameConf.music_volume / 100, gameConf.enable_sound, gameConf.enable_music, nullptr)) {
		chkEnableSound->setChecked(false);
		chkEnableSound->setEnabled(false);
		chkEnableSound->setVisible(false);
		chkEnableMusic->setChecked(false);
		chkEnableMusic->setEnabled(false);
		chkEnableMusic->setVisible(false);
		scrSoundVolume->setVisible(false);
		scrMusicVolume->setVisible(false);
		chkMusicMode->setEnabled(false);
		chkMusicMode->setVisible(false);
	}
#endif
	//big picture
	wBigCard = env->addWindow(rect<s32>(0, 0, 0, 0), false, L"");
	wBigCard->getCloseButton()->setVisible(false);
	wBigCard->setDrawTitlebar(false);
	wBigCard->setDrawBackground(false);
	wBigCard->setVisible(false);
	imgBigCard = env->addImage(rect<s32>(0, 0, 0, 0), wBigCard);
	imgBigCard->setScaleImage(false);
	imgBigCard->setUseAlphaChannel(true);
	btnBigCardOriginalSize = env->addButton(rect<s32>(200 * yScale, 100 * yScale, 305 * yScale, 135 * yScale), 0, BUTTON_BIG_CARD_ORIG_SIZE, dataManager.GetSysString(1443));
	btnBigCardZoomIn = env->addButton(rect<s32>(200 * yScale, 140 * yScale, 305 * yScale, 175 * yScale), 0, BUTTON_BIG_CARD_ZOOM_IN, dataManager.GetSysString(1441));
	btnBigCardZoomOut = env->addButton(rect<s32>(200 * yScale, 180 * yScale, 305 * yScale, 215 * yScale), 0, BUTTON_BIG_CARD_ZOOM_OUT, dataManager.GetSysString(1442));
	btnBigCardClose = env->addButton(rect<s32>(200 * yScale, 220 * yScale, 305 * yScale, 275 * yScale), 0, BUTTON_BIG_CARD_CLOSE, dataManager.GetSysString(1440));
	btnBigCardOriginalSize->setVisible(false);
	btnBigCardZoomIn->setVisible(false);
	btnBigCardZoomOut->setVisible(false);
	btnBigCardClose->setVisible(false);
	//leave/surrender/exit
	btnLeaveGame = env->addButton(rect<s32>((3 + CARD_IMG_WIDTH) * yScale, 1 * yScale, 310 * yScale, 51 * yScale), 0, BUTTON_LEAVE_GAME, L"");
        ChangeToIGUIImageButton(btnLeaveGame, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnLeaveGame->setVisible(false);
	//tip
	stTip = env->addStaticText(L"", rect<s32>(0, 0, 150 * xScale, 150 * yScale), false, true, 0, -1, true);
	stTip->setBackgroundColor(0xc011113d);
	stTip->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	stTip->setVisible(false);
	//tip for cards in select / display list
	stCardListTip = env->addStaticText(L"", rect<s32>(0, 0, 150 * xScale, 150 * yScale), false, true, wCardSelect, TEXT_CARD_LIST_TIP, true);
 	stCardListTip->setBackgroundColor(0x6011113d);
	stCardListTip->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	stCardListTip->setVisible(false);
	device->setEventReceiver(&menuHandler);
	LoadConfig();
	env->getSkin()->setFont(guiFont);
	env->getSkin()->setSize(EGDS_CHECK_BOX_WIDTH, (gameConf.textfontsize + 10) * yScale);
	env->setFocus(wMainMenu);
	for (u32 i = 0; i < EGDC_COUNT; ++i) {
		SColor col = env->getSkin()->getColor((EGUI_DEFAULT_COLOR)i);
		col.setAlpha(200);
		env->getSkin()->setColor((EGUI_DEFAULT_COLOR)i, col);
	}
#ifdef _IRR_ANDROID_PLATFORM_
IGUIStaticText *text = env->addStaticText(L"",
		rect<s32>(1,1,100,45), false, false, 0, GUI_INFO_FPS );
#endif
	hideChat = false;
	hideChatTimer = 0;
	
	return true;
}//bool Game::Initialize
void Game::MainLoop() {
	wchar_t cap[256];
	camera = smgr->addCameraSceneNode(0);
	irr::core::matrix4 mProjection;
	BuildProjectionMatrix(mProjection, -0.90f, 0.45f, -0.42f, 0.42f, 1.0f, 100.0f);
	camera->setProjectionMatrix(mProjection);

	mProjection.buildCameraLookAtMatrixLH(vector3df(4.2f, 8.0f, 7.8f), vector3df(4.2f, 0, 0), vector3df(0, 0, 1));
	camera->setViewMatrixAffector(mProjection);
	smgr->setAmbientLight(SColorf(1.0f, 1.0f, 1.0f));
	float atkframe = 0.1f;
	irr::ITimer* timer = device->getTimer();
	timer->setTime(0);
#ifdef _IRR_ANDROID_PLATFORM_
	IGUIElement *stat = device->getGUIEnvironment()->getRootGUIElement()->getElementFromId ( GUI_INFO_FPS );
#endif
	int fps = 0;
	int cur_time = 0;
#if defined(_IRR_ANDROID_PLATFORM_)
	ogles2Solid = 0;
	ogles2TrasparentAlpha = 0;
	ogles2BlendTexture = 0;
	if (glversion == 0 || glversion == 2) {
		ogles2Solid = video::EMT_SOLID;
		ogles2TrasparentAlpha = video::EMT_TRANSPARENT_ALPHA_CHANNEL;
		ogles2BlendTexture = video::EMT_ONETEXTURE_BLEND;
	} else {
		io::path solidvsFileName = "media/ogles2customsolid.frag";
		io::path TACvsFileName = "media/ogles2customTAC.frag";
		io::path blendvsFileName = "media/ogles2customblend.frag";
		io::path psFileName = "media/ogles2custom.vert";
		if (!driver->queryFeature(video::EVDF_PIXEL_SHADER_1_1) &&
				!driver->queryFeature(video::EVDF_ARB_FRAGMENT_PROGRAM_1))
		{
			ALOGD("WARNING: Pixel shaders disabled "
					"because of missing driver/hardware support.");
			psFileName = "";
		}
		if (!driver->queryFeature(video::EVDF_VERTEX_SHADER_1_1) &&
				!driver->queryFeature(video::EVDF_ARB_VERTEX_PROGRAM_1))
		{
			ALOGD("WARNING: Vertex shaders disabled "
					"because of missing driver/hardware support.");
			solidvsFileName = "";
			TACvsFileName = "";
			blendvsFileName = "";
		}
		video::IGPUProgrammingServices* gpu = driver->getGPUProgrammingServices();
		if (gpu) {
			const video::E_GPU_SHADING_LANGUAGE shadingLanguage = video::EGSL_DEFAULT;
			ogles2Solid = gpu->addHighLevelShaderMaterialFromFiles(
					psFileName, "vertexMain", video::EVST_VS_1_1,
					solidvsFileName, "pixelMain", video::EPST_PS_1_1,
					&customShadersCallback, video::EMT_SOLID, 0, shadingLanguage);
			ogles2TrasparentAlpha = gpu->addHighLevelShaderMaterialFromFiles(
					psFileName, "vertexMain", video::EVST_VS_1_1,
					TACvsFileName, "pixelMain", video::EPST_PS_1_1,
					&customShadersCallback, video::EMT_TRANSPARENT_ALPHA_CHANNEL, 0 , shadingLanguage);
			ogles2BlendTexture = gpu->addHighLevelShaderMaterialFromFiles(
					psFileName, "vertexMain", video::EVST_VS_1_1,
					blendvsFileName, "pixelMain", video::EPST_PS_1_1,
					&customShadersCallback, video::EMT_ONETEXTURE_BLEND, 0 , shadingLanguage);
			ALOGD("ogles2Sold = %d", ogles2Solid);
			ALOGD("ogles2BlendTexture = %d", ogles2BlendTexture);
			ALOGD("ogles2TrasparentAlpha = %d", ogles2TrasparentAlpha);
		}
	}
	matManager.mCard.MaterialType = (video::E_MATERIAL_TYPE)ogles2BlendTexture;
	matManager.mTexture.MaterialType = (video::E_MATERIAL_TYPE)ogles2TrasparentAlpha;
	matManager.mBackLine.MaterialType = (video::E_MATERIAL_TYPE)ogles2BlendTexture;
	matManager.mSelField.MaterialType = (video::E_MATERIAL_TYPE)ogles2BlendTexture;
	matManager.mOutLine.MaterialType = (video::E_MATERIAL_TYPE)ogles2Solid;
	matManager.mTRTexture.MaterialType = (video::E_MATERIAL_TYPE)ogles2TrasparentAlpha;
	matManager.mATK.MaterialType = (video::E_MATERIAL_TYPE)ogles2BlendTexture;
	if (!isNPOTSupported) {
		matManager.mCard.TextureLayer[0].TextureWrapU = ETC_CLAMP_TO_EDGE;
		matManager.mCard.TextureLayer[0].TextureWrapV = ETC_CLAMP_TO_EDGE;
		matManager.mTexture.TextureLayer[0].TextureWrapU = ETC_CLAMP_TO_EDGE;
		matManager.mTexture.TextureLayer[0].TextureWrapV = ETC_CLAMP_TO_EDGE;
		matManager.mBackLine.TextureLayer[0].TextureWrapU = ETC_CLAMP_TO_EDGE;
		matManager.mBackLine.TextureLayer[0].TextureWrapV = ETC_CLAMP_TO_EDGE;
		matManager.mSelField.TextureLayer[0].TextureWrapU = ETC_CLAMP_TO_EDGE;
		matManager.mSelField.TextureLayer[0].TextureWrapV = ETC_CLAMP_TO_EDGE;
		matManager.mOutLine.TextureLayer[0].TextureWrapU = ETC_CLAMP_TO_EDGE;
		matManager.mOutLine.TextureLayer[0].TextureWrapV = ETC_CLAMP_TO_EDGE;
		matManager.mTRTexture.TextureLayer[0].TextureWrapU = ETC_CLAMP_TO_EDGE;
		matManager.mTRTexture.TextureLayer[0].TextureWrapV = ETC_CLAMP_TO_EDGE;
		matManager.mATK.TextureLayer[0].TextureWrapU = ETC_CLAMP_TO_EDGE;
		matManager.mATK.TextureLayer[0].TextureWrapV = ETC_CLAMP_TO_EDGE;
	}
#endif
	while(device->run()) {
		//ALOGV("game draw frame");
		linePatternD3D = (linePatternD3D + 1) % 30;
		linePatternGL = (linePatternGL << 1) | (linePatternGL >> 15);
		atkframe += 0.1f;
		atkdy = (float)sin(atkframe);
		driver->beginScene(true, true, SColor(0, 0, 0, 0));
#ifdef _IRR_ANDROID_PLATFORM_
		driver->getMaterial2D().MaterialType = (video::E_MATERIAL_TYPE)ogles2Solid;
		if (!isNPOTSupported) {
			driver->getMaterial2D().TextureLayer[0].TextureWrapU = ETC_CLAMP_TO_EDGE;
			driver->getMaterial2D().TextureLayer[0].TextureWrapV = ETC_CLAMP_TO_EDGE;
		}
		driver->enableMaterial2D(true);
		driver->getMaterial2D().ZBuffer = ECFN_NEVER;
		if(imageManager.tBackGround) {
			driver->draw2DImage(imageManager.tBackGround, recti(0 * xScale, 0 * yScale, 1920 * xScale, 1080 * yScale), recti(0, 0, imageManager.tBackGround->getOriginalSize().Width, imageManager.tBackGround->getOriginalSize().Height));
		}
		if(imageManager.tBackGround_menu) {
			driver->draw2DImage(imageManager.tBackGround_menu, recti(0 * xScale, 0 * yScale, 1920 * xScale, 1080 * yScale), recti(0, 0, imageManager.tBackGround->getOriginalSize().Width, imageManager.tBackGround->getOriginalSize().Height));
		}
		if(imageManager.tBackGround_deck) {
			driver->draw2DImage(imageManager.tBackGround_deck, recti(0 * xScale, 0 * yScale, 1920 * xScale, 1080 * yScale), recti(0, 0, imageManager.tBackGround->getOriginalSize().Width, imageManager.tBackGround->getOriginalSize().Height));
		}
		driver->enableMaterial2D(false);
#endif
		gMutex.lock();
		if(dInfo.isStarted) {
			DrawBackImage(imageManager.tBackGround);
			DrawBackGround();
			DrawCards();
			DrawMisc();
			smgr->drawAll();
			driver->setMaterial(irr::video::IdentityMaterial);
			driver->clearZBuffer();
		} else if(is_building) {
			DrawBackImage(imageManager.tBackGround_deck);
#ifdef _IRR_ANDROID_PLATFORM_
			driver->enableMaterial2D(true);
			DrawDeckBd();
			driver->enableMaterial2D(false);
		} else {
			DrawBackImage(imageManager.tBackGround_menu);
		}
		driver->enableMaterial2D(true);
		DrawGUI();
		DrawSpec();
		driver->enableMaterial2D(false);
#endif
		gMutex.unlock();
#ifdef _IRR_ANDROID_PLATFORM_
		playBGM();
#endif
		if(signalFrame > 0) {
			signalFrame--;
			if(!signalFrame)
				frameSignal.Set();
		}
		if(waitFrame >= 0) {
			waitFrame++;
			if(waitFrame % 90 == 0) {
				stHintMsg->setText(dataManager.GetSysString(1390));
			} else if(waitFrame % 90 == 30) {
				stHintMsg->setText(dataManager.GetSysString(1391));
			} else if(waitFrame % 90 == 60) {
				stHintMsg->setText(dataManager.GetSysString(1392));
			}
		}
		driver->endScene();
		if(closeSignal.Wait(1))
			CloseDuelWindow();
		fps++;
		cur_time = timer->getTime();
		if(cur_time < fps * 17 - 20)
			std::this_thread::sleep_for(std::chrono::milliseconds(20));
		if(cur_time >= 1000) {

#ifdef _IRR_ANDROID_PLATFORM_
			if ( stat ) {
				stringw str = L"FPS: ";
				str += (s32)device->getVideoDriver()->getFPS();
				stat->setText ( str.c_str() );
			}
#endif
			fps = 0;
			cur_time -= 1000;
			timer->setTime(0);
			if(dInfo.time_player == 0 || dInfo.time_player == 1)
				if(dInfo.time_left[dInfo.time_player]) {
					dInfo.time_left[dInfo.time_player]--;
					RefreshTimeDisplay();
				}
		}
#ifdef _IRR_ANDROID_PLATFORM_
		device->yield(); // probably nicer to the battery
#endif
}
	DuelClient::StopClient(true);
	if(dInfo.isSingleMode)
		SingleMode::StopPlay(true);

	usleep(500000);
	SaveConfig();
	usleep(500000);
	device->drop();
}
void Game::RefreshTimeDisplay() {
	for(int i = 0; i < 2; ++i) {
		if(dInfo.time_left[i] && dInfo.time_limit) {
			if(dInfo.time_left[i] >= dInfo.time_limit / 2)
				dInfo.time_color[i] = 0xff00ff00;
			else if(dInfo.time_left[i] >= dInfo.time_limit / 3)
				dInfo.time_color[i] = 0xffffff00;
			else if(dInfo.time_left[i] >= dInfo.time_limit / 6)
				dInfo.time_color[i] = 0xffff7f00;
			else
				dInfo.time_color[i] = 0xffff0000;
		} else
			dInfo.time_color[i] = 0xffffffff;
	}
	myswprintf(dInfo.str_time_left[0], L"%d", dInfo.time_left[0]);
	myswprintf(dInfo.str_time_left[1], L"%d", dInfo.time_left[1]);
}
void Game::BuildProjectionMatrix(irr::core::matrix4& mProjection, f32 left, f32 right, f32 bottom, f32 top, f32 znear, f32 zfar) {
	for(int i = 0; i < 16; ++i)
		mProjection[i] = 0;
	mProjection[0] = 2.0f * znear / (right - left);
	mProjection[5] = 2.0f * znear / (top - bottom);
	mProjection[8] = (left + right) / (left - right);
	mProjection[9] = (top + bottom) / (bottom - top);
	mProjection[10] = zfar / (zfar - znear);
	mProjection[11] = 1.0f;
	mProjection[14] = znear * zfar / (znear - zfar);
}
void Game::InitStaticText(irr::gui::IGUIStaticText* pControl, u32 cWidth, u32 cHeight, irr::gui::CGUITTFont* font, const wchar_t* text) {
	std::wstring format_text;
	format_text = SetStaticText(pControl, cWidth, font, text);
	if(font->getDimension(format_text.c_str()).Height <= cHeight) {
		scrCardText->setVisible(false);
		if(env->hasFocus(scrCardText))
			env->removeFocus(scrCardText);
		return;
	}
	format_text = SetStaticText(pControl, cWidth, font, text);
	u32 fontheight = font->getDimension(L"A").Height + font->getKerningHeight();
	u32 step = (font->getDimension(format_text.c_str()).Height - cHeight) / fontheight + 1;
	scrCardText->setVisible(true);
	scrCardText->setMin(0);
	scrCardText->setMax(step);
	scrCardText->setPos(0);
}
std::wstring Game::SetStaticText(irr::gui::IGUIStaticText* pControl, u32 cWidth, irr::gui::CGUITTFont* font, const wchar_t* text, u32 pos) {
	int pbuffer = 0;
	u32 _width = 0, _height = 0;
	wchar_t prev = 0;
	wchar_t strBuffer[4096];
	std::wstring ret;

	for(size_t i = 0; text[i] != 0 && i < wcslen(text); ++i) {
		wchar_t c = text[i];
		u32 w = font->getCharDimension(c).Width + font->getKerningWidth(c, prev);
		prev = c;
		if(text[i] == L'\r') {
			continue;
		} else if(text[i] == L'\n') {
			strBuffer[pbuffer++] = L'\n';
			_width = 0;
			_height++;
			prev = 0;
			if(_height == pos)
				pbuffer = 0;
			continue;
		} else if(_width > 0 && _width + w > cWidth) {
			strBuffer[pbuffer++] = L'\n';
			_width = 0;
			_height++;
			prev = 0;
			if(_height == pos)
				pbuffer = 0;
		}
		_width += w;
		strBuffer[pbuffer++] = c;
	}
	strBuffer[pbuffer] = 0;
	if(pControl) pControl->setText(strBuffer);
	ret.assign(strBuffer);
	return ret;
}
void Game::LoadExpansions() {
	// TODO: get isUseExtraCards
#ifdef _IRR_ANDROID_PLATFORM_
	DIR * dir;
	struct dirent * dirp;
	if((dir = opendir("./expansions/")) == NULL)
		return;
	while((dirp = readdir(dir)) != NULL) {
		size_t len = strlen(dirp->d_name);
		if(len < 5 || strcasecmp(dirp->d_name + len - 4, ".zip") != 0 ||strcasecmp(dirp->d_name + len - 4, ".ypk") != 0)
			continue;
		char upath[1024];
		sprintf(upath, "./expansions/%s", dirp->d_name);
		dataManager.FileSystem->addFileArchive(upath, true, false, EFAT_ZIP);
	}
	closedir(dir);
#endif
	for(u32 i = 0; i < DataManager::FileSystem->getFileArchiveCount(); ++i) {
		const IFileList* archive = DataManager::FileSystem->getFileArchive(i)->getFileList();
		for(u32 j = 0; j < archive->getFileCount(); ++j) {
            wchar_t fname[1024];
			const char* uname = archive->getFullFileName(j).c_str();
			BufferIO::DecodeUTF8(uname, fname);
			if(wcsrchr(fname, '.') && !wcsncasecmp(wcsrchr(fname, '.'), L".cdb", 4))
				dataManager.LoadDB(fname);
			if(wcsrchr(fname, '.') && !wcsncasecmp(wcsrchr(fname, '.'), L".conf", 5)) {
				IReadFile* reader = DataManager::FileSystem->createAndOpenFile(uname);
				dataManager.LoadStrings(reader);
			}
			if(wcsrchr(fname, '.') && !wcsncasecmp(wcsrchr(fname, '.'), L".ydk", 4)) {
				deckBuilder.expansionPacks.push_back(fname);
			}
		}
	}
}
void Game::RefreshCategoryDeck(irr::gui::IGUIComboBox* cbCategory, irr::gui::IGUIComboBox* cbDeck, bool selectlastused) {
	cbCategory->clear();
	if (cbCategory == mainGame->cbDBCategory) {
		cbCategory->addItem(dataManager.GetSysString(1450));
	}
	cbCategory->addItem(dataManager.GetSysString(1451));
	cbCategory->addItem(dataManager.GetSysString(1452));
	cbCategory->addItem(dataManager.GetSysString(1453));
	FileSystem::TraversalDir(L"./deck", [cbCategory](const wchar_t* name, bool isdir) {
		if(isdir) {
			cbCategory->addItem(name);
		}
	});
    if (cbCategory == mainGame->cbDBCategory) {
        cbCategory->setSelected(2);
    } else {
        cbCategory->setSelected(1);
    }
	if(selectlastused) {
		for(size_t i = 0; i < cbCategory->getItemCount(); ++i) {
			if(!wcscmp(cbCategory->getItem(i), gameConf.lastcategory)) {
				cbCategory->setSelected(i);
				break;
			}
		}
	}
	RefreshDeck(cbCategory, cbDeck);
	if(selectlastused) {
		for(size_t i = 0; i < cbDeck->getItemCount(); ++i) {
			if(!wcscmp(cbDeck->getItem(i), gameConf.lastdeck)) {
				cbDeck->setSelected(i);
				break;
			}
		}
	}
}
void Game::RefreshDeck(irr::gui::IGUIComboBox* cbCategory, irr::gui::IGUIComboBox* cbDeck) {
    if (cbCategory == mainGame->cbDBCategory) {
        if(cbCategory != cbDBCategory && cbCategory->getSelected() == 0) {
            // can't use pack list in duel
            cbDeck->clear();
            return;
        }
    }
	wchar_t catepath[256];
	deckManager.GetCategoryPath(catepath, cbCategory->getSelected(), cbCategory->getText(), cbCategory == mainGame->cbDBCategory);
	cbDeck->clear();
	RefreshDeck(catepath, [cbDeck](const wchar_t* item) { cbDeck->addItem(item); });
}
void Game::RefreshDeck(const wchar_t* deckpath, const std::function<void(const wchar_t*)>& additem) {
	if(!wcsncasecmp(deckpath, L"./pack", 6)) {
		for(auto& pack : deckBuilder.expansionPacks) {
			// add pack/xxx.ydk
			additem(pack.substr(5, pack.size() - 9).c_str());
		}
	}
	FileSystem::TraversalDir(deckpath, [additem](const wchar_t* name, bool isdir) {
		if (!isdir && IsExtension(name, L".ydk")) {
			size_t len = std::wcslen(name);
			wchar_t deckname[256]{};
			size_t count = std::min(len - 4, sizeof deckname / sizeof deckname[0] - 1);
			std::wcsncpy(deckname, name, count);
			deckname[count] = 0;
			additem(deckname);
		}
	});
}
void Game::RefreshReplay() {
	lstReplayList->clear();
	FileSystem::TraversalDir(L"./replay", [this](const wchar_t* name, bool isdir) {
		if (!isdir && IsExtension(name, L".yrp") && Replay::CheckReplay(name))
			lstReplayList->addItem(name);
	});
}
void Game::RefreshSingleplay() {
	lstSinglePlayList->clear();
	stSinglePlayInfo->setText(L"");
	FileSystem::TraversalDir(L"./single", [this](const wchar_t* name, bool isdir) {
		if(!isdir && IsExtension(name, L".lua"))
			lstSinglePlayList->addItem(name);
	});
}
void Game::RefreshBot() {
	if(!gameConf.enable_bot_mode)
		return;
	botInfo.clear();
	FILE* fp = fopen("bot.conf", "r");
	char linebuf[256]{};
	char strbuf[256]{};
	if(fp) {
		while(fgets(linebuf, 256, fp)) {
			if(linebuf[0] == '#')
				continue;
			if(linebuf[0] == '!') {
				BotInfo newinfo;
				if (sscanf(linebuf, "!%240[^\n]", strbuf) != 1)
					continue;
				BufferIO::DecodeUTF8(strbuf, newinfo.name);
				if (!fgets(linebuf, 256, fp))
					break;
				if (sscanf(linebuf, "%240[^\n]", strbuf) != 1)
					continue;
				BufferIO::DecodeUTF8(strbuf, newinfo.command);
				if (!fgets(linebuf, 256, fp))
					break;
				if (sscanf(linebuf, "%240[^\n]", strbuf) != 1)
					continue;
				BufferIO::DecodeUTF8(strbuf, newinfo.desc);
				if (!fgets(linebuf, 256, fp))
					break;
				newinfo.support_master_rule_3 = !!std::strstr(linebuf, "SUPPORT_MASTER_RULE_3");
				newinfo.support_new_master_rule = !!std::strstr(linebuf, "SUPPORT_NEW_MASTER_RULE");
				newinfo.support_master_rule_2020 = !!std::strstr(linebuf, "SUPPORT_MASTER_RULE_2020");
				newinfo.select_deckfile = !!std::strstr(linebuf, "SELECT_DECKFILE");
				int rule = cbBotRule->getSelected() + 3;
				if((rule == 3 && newinfo.support_master_rule_3)
					|| (rule == 4 && newinfo.support_new_master_rule)
					|| (rule == 5 && newinfo.support_master_rule_2020))
					botInfo.push_back(newinfo);
				continue;
			}
		}
		fclose(fp);
	}
	lstBotList->clear();
	stBotInfo->setText(L"");
	cbBotDeckCategory->setVisible(false);
	cbBotDeck->setVisible(false);
	for(unsigned int i = 0; i < botInfo.size(); ++i) {
		lstBotList->addItem(botInfo[i].name);
	}
	if(botInfo.size() == 0) {
		SetStaticText(stBotInfo, 200, guiFont, dataManager.GetSysString(1385));
	}
	else {
		RefreshCategoryDeck(cbBotDeckCategory, cbBotDeck);
        deckBuilder.prev_category = mainGame->cbBotDeckCategory->getSelected();
        deckBuilder.prev_deck = mainGame->cbBotDeck->getSelected();
		wchar_t cate[256];
		wchar_t cate_deck[256];
		myswprintf(cate, L"%ls%ls", (cbBotDeckCategory->getSelected())==1 ? L"" : cbBotDeckCategory->getItem(cbBotDeckCategory->getSelected()), (cbBotDeckCategory->getSelected())==1 ? L"" : L"|");
		if (cbBotDeck->getItemCount() != 0) {
			myswprintf(cate_deck, L"%ls%ls", cate, cbBotDeck->getItem(cbBotDeck->getSelected()));
		} else {
			myswprintf(cate_deck, L"%ls%ls", cate, dataManager.GetSysString(1301));
		}
		mainGame->btnBotDeckSelect->setText(cate_deck);
	}
}
void Game::LoadConfig() {
	wchar_t wstr[256];
	gameConf.antialias = 1;
	gameConf.serverport = 7911;
	gameConf.textfontsize = android::getIntSetting(appMain, "textfontsize", 18);;
	gameConf.nickname[0] = 0;
	gameConf.gamename[0] = 0;
    BufferIO::DecodeUTF8(android::getLastCategory(appMain).c_str(), wstr);;
    BufferIO::CopyWStr(wstr, gameConf.lastcategory, 64);

	BufferIO::DecodeUTF8(android::getLastDeck(appMain).c_str(), wstr);
	BufferIO::CopyWStr(wstr, gameConf.lastdeck, 64);

	BufferIO::DecodeUTF8(android::getFontPath(appMain).c_str(), wstr);
	BufferIO::CopyWStr(wstr, gameConf.numfont, 256);
	BufferIO::CopyWStr(wstr, gameConf.textfont, 256);
	gameConf.lasthost[0] = 0;
	gameConf.lastport[0] = 0;
	gameConf.roompass[0] = 0;
	//helper
	gameConf.chkMAutoPos = android::getIntSetting(appMain, "chkMAutoPos", 0);
	gameConf.chkSTAutoPos = android::getIntSetting(appMain, "chkSTAutoPos", 0);
	gameConf.chkRandomPos = android::getIntSetting(appMain, "chkRandomPos", 0);
	gameConf.chkAutoChain = android::getIntSetting(appMain, "chkAutoChain", 0);
	gameConf.chkWaitChain = android::getIntSetting(appMain, "chkWaitChain", 0);
	//system
	gameConf.chkIgnore1 = android::getIntSetting(appMain, "chkIgnore1", 0);
	gameConf.chkIgnore2 = android::getIntSetting(appMain, "chkIgnore2", 0);
	gameConf.control_mode = android::getIntSetting(appMain, "control_mode", 0);
	gameConf.draw_field_spell = android::getIntSetting(appMain, "draw_field_spell", 1);
	gameConf.chkIgnoreDeckChanges = android::getIntSetting(appMain, "chkIgnoreDeckChanges", 0);
	gameConf.auto_save_replay = android::getIntSetting(appMain, "auto_save_replay", 0);
	gameConf.quick_animation = android::getIntSetting(appMain, "quick_animation", 0);
	gameConf.draw_single_chain = android::getIntSetting(appMain, "draw_single_chain", 0);
	gameConf.enable_sound = android::getIntSetting(appMain, "enable_sound", 1);
	gameConf.sound_volume = android::getIntSetting(appMain, "sound_volume", 50);
	gameConf.enable_music = android::getIntSetting(appMain, "enable_music", 1);
	gameConf.music_volume = android::getIntSetting(appMain, "music_volume", 50);
	gameConf.music_mode = android::getIntSetting(appMain, "music_mode", 1);
	gameConf.use_lflist = android::getIntSetting(appMain, "use_lflist", 1);
	gameConf.chkDefaultShowChain = android::getIntSetting(appMain, "chkDefaultShowChain", 0);
	gameConf.hide_player_name = android::getIntSetting(appMain, "chkHidePlayerName", 0);
	//defult Setting without checked
	gameConf.default_rule = DEFAULT_DUEL_RULE;
    gameConf.hide_setname = 0;
	gameConf.separate_clear_button = 1;
	gameConf.search_multiple_keywords = 1;
	gameConf.defaultOT = 1;
	gameConf.auto_search_limit = 1;
	//enable errorLog
	enable_log = 3;
	//TEST BOT MODE
	gameConf.enable_bot_mode = 1;
}

void Game::SaveConfig() {
    android::saveIntSetting(appMain, "textfontsize", gameConf.textfontsize);
	//helper
	gameConf.chkMAutoPos = chkMAutoPos->isChecked() ? 1 : 0;
	    android::saveIntSetting(appMain, "chkMAutoPos", gameConf.chkMAutoPos);
	gameConf.chkSTAutoPos = chkSTAutoPos->isChecked() ? 1 : 0;
		android::saveIntSetting(appMain, "chkSTAutoPos", gameConf.chkSTAutoPos);
	gameConf.chkRandomPos = chkRandomPos->isChecked() ? 1 : 0;
		android::saveIntSetting(appMain, "chkRandomPos", gameConf.chkRandomPos);
	gameConf.chkAutoChain = chkAutoChain->isChecked() ? 1 : 0;
		android::saveIntSetting(appMain, "chkAutoChain", gameConf.chkAutoChain);
    gameConf.chkWaitChain = chkWaitChain->isChecked() ? 1 : 0;
    	android::saveIntSetting(appMain, "chkWaitChain", gameConf.chkWaitChain);

	//system
	gameConf.chkIgnore1 = chkIgnore1->isChecked() ? 1 : 0;
		android::saveIntSetting(appMain, "chkIgnore1", gameConf.chkIgnore1);
	gameConf.chkIgnore2 = chkIgnore2->isChecked() ? 1 : 0;
		android::saveIntSetting(appMain, "chkIgnore2", gameConf.chkIgnore2);
	gameConf.chkIgnoreDeckChanges = chkIgnoreDeckChanges->isChecked() ? 1 : 0;
		android::saveIntSetting(appMain, "chkIgnoreDeckChanges", gameConf.chkIgnoreDeckChanges);
	gameConf.auto_save_replay = chkAutoSaveReplay->isChecked() ? 1 : 0;
	    android::saveIntSetting(appMain, "auto_save_replay", gameConf.auto_save_replay);
	gameConf.draw_field_spell = chkDrawFieldSpell->isChecked() ? 1 : 0;
        android::saveIntSetting(appMain, "draw_field_spell", gameConf.draw_field_spell);
    gameConf.quick_animation = chkQuickAnimation->isChecked() ? 1 : 0;
        android::saveIntSetting(appMain, "quick_animation", gameConf.quick_animation);
	gameConf.draw_single_chain = chkDrawSingleChain->isChecked() ? 1 : 0;
	    android::saveIntSetting(appMain, "draw_single_chain", gameConf.draw_single_chain);
	gameConf.enable_sound = chkEnableSound->isChecked() ? 1 : 0;
	    android::saveIntSetting(appMain, "enable_sound", gameConf.enable_sound);
	gameConf.enable_music = chkEnableMusic->isChecked() ? 1 : 0;
	    android::saveIntSetting(appMain, "enable_music", gameConf.enable_music);
	gameConf.music_mode = chkMusicMode->isChecked() ? 1 : 0;
	    android::saveIntSetting(appMain, "music_mode", gameConf.music_mode);
	gameConf.sound_volume = (double)scrSoundVolume->getPos();
	    android::saveIntSetting(appMain, "sound_volume", gameConf.sound_volume);
	gameConf.music_volume = (double)scrMusicVolume->getPos();
	    android::saveIntSetting(appMain, "music_volume", gameConf.music_volume);
	gameConf.use_lflist = chkLFlist->isChecked() ? 1 : 0;
	    android::saveIntSetting(appMain, "use_lflist", gameConf.use_lflist);
	gameConf.chkDefaultShowChain = chkDefaultShowChain->isChecked() ? 1 : 0;
	    android::saveIntSetting(appMain, "chkDefaultShowChain", gameConf.chkDefaultShowChain);
	gameConf.hide_player_name  = chkHidePlayerName->isChecked() ? 1 : 0;
		android::saveIntSetting(appMain, "chkHidePlayerName", gameConf.hide_player_name);
//gameConf.control_mode = control_mode->isChecked()?1:0;
//	  android::saveIntSetting(appMain, "control_mode", gameConf.control_mode);
}

void Game::ShowCardInfo(int code) {
	wchar_t formatBuffer[256];
	auto cit = dataManager.GetCodePointer(code);
	bool is_valid = (cit != dataManager.datas_end());
	imgCard->setImage(imageManager.GetTexture(code));
	imgCard->setScaleImage(true);
	if (is_valid) {
		auto& cd = cit->second;
		if (cd.is_alternative())
			myswprintf(formatBuffer, L"%ls[%08d]", dataManager.GetName(cd.alias), cd.alias);
		else
			myswprintf(formatBuffer, L"%ls[%08d]", dataManager.GetName(code), code);
	}
	else {
		myswprintf(formatBuffer, L"%ls[%08d]", dataManager.GetName(code), code);
	}
	stName->setText(formatBuffer);
	int offset = 0;
	if (is_valid && !gameConf.hide_setname) {
		auto& cd = cit->second;
		auto target = cit;
		if (cd.alias && dataManager.GetCodePointer(cd.alias) != dataManager.datas_end()) {
			target = dataManager.GetCodePointer(cd.alias);
		}
		if (target->second.setcode[0]) {
			offset = 23;// *yScale;
			myswprintf(formatBuffer, L"%ls%ls", dataManager.GetSysString(1329), dataManager.FormatSetName(target->second.setcode).c_str());
			stSetName->setText(formatBuffer);
		}
		else
			stSetName->setText(L"");
	}
	else {
		stSetName->setText(L"");
	}
	if(is_valid && cit->second.type & TYPE_MONSTER) {
		auto& cd = cit->second;
		myswprintf(formatBuffer, L"[%ls] %ls/%ls", dataManager.FormatType(cd.type).c_str(), dataManager.FormatRace(cd.race).c_str(), dataManager.FormatAttribute(cd.attribute).c_str());
		stInfo->setText(formatBuffer);
		const wchar_t* form = L"\u2605";
		wchar_t adBuffer[64]{};
		wchar_t scaleBuffer[16]{};
		if(!(cd.type & TYPE_LINK)) {
			if(cd.type & TYPE_XYZ)
				form = L"\u2606";
			if(cd.attack < 0 && cd.defense < 0)
				myswprintf(adBuffer, L"?/?");
			else if(cd.attack < 0)
				myswprintf(adBuffer, L"?/%d", cd.defense);
			else if(cd.defense < 0)
				myswprintf(adBuffer, L"%d/?", cd.attack);
			else
				myswprintf(adBuffer, L"%d/%d", cd.attack, cd.defense);
		} else {
			form = L"LINK-";
			if(cd.attack < 0)
				myswprintf(adBuffer, L"?/-   %ls", dataManager.FormatLinkMarker(cd.link_marker).c_str());
			else
				myswprintf(adBuffer, L"%d/-   %ls", cd.attack, dataManager.FormatLinkMarker(cd.link_marker).c_str());
		}
		if(cd.type & TYPE_PENDULUM) {
			myswprintf(scaleBuffer, L"   %d/%d", cd.lscale, cd.rscale);
		}
		myswprintf(formatBuffer, L"[%ls%d] %ls%ls", form, cd.level, adBuffer, scaleBuffer);
		stDataInfo->setText(formatBuffer);
		stSetName->setRelativePosition(rect<s32>(10 * xScale, 83 * yScale, 250 * xScale, 106 * yScale));
		stText->setRelativePosition(rect<s32>(10 * xScale, (83 + offset) * yScale, 251 * xScale, 340 * yScale));
		scrCardText->setRelativePosition(rect<s32>(255 * xScale, (83 + offset) * yScale, 258 * xScale, 340 * yScale));
	}
	else {
		if (is_valid)
			myswprintf(formatBuffer, L"[%ls]", dataManager.FormatType(cit->second.type).c_str());
		else
			myswprintf(formatBuffer, L"[%ls]", dataManager.unknown_string);
		stInfo->setText(formatBuffer);
		stDataInfo->setText(L"");
		stSetName->setRelativePosition(rect<s32>(10 * xScale, 60 * yScale, 250 * xScale, 106 * yScale));
		stText->setRelativePosition(rect<s32>(10 * xScale, (60 + offset) * yScale, 251 * xScale, 340 * yScale));
		scrCardText->setRelativePosition(rect<s32>(255 * xScale, (60 + offset) * yScale, 258 * xScale, 340 * yScale));
	}
	showingtext = dataManager.GetText(code);
	const auto& tsize = stText->getRelativePosition();
	InitStaticText(stText, tsize.getWidth(), tsize.getHeight(), textFont, showingtext);
}
void Game::ClearCardInfo(int player) {
	imgCard->setImage(imageManager.tCover[player]);
	stName->setText(L"");
	stInfo->setText(L"");
	stDataInfo->setText(L"");
	stSetName->setText(L"");
	stText->setText(L"");
	scrCardText->setVisible(false);
}
void Game::AddLog(const wchar_t* msg, int param) {
	logParam.push_back(param);
	lstLog->addItem(msg);
	if(!env->hasFocus(lstLog)) {
		lstLog->setSelected(-1);
	}
}
void Game::AddChatMsg(const wchar_t* msg, int player, bool play_sound) {
	for(int i = 7; i > 0; --i) {
		chatMsg[i] = chatMsg[i - 1];
		chatTiming[i] = chatTiming[i - 1];
		chatType[i] = chatType[i - 1];
	}
	chatMsg[0].clear();
	chatTiming[0] = 1200;
	chatType[0] = player;
	if(gameConf.hide_player_name && player < 4)
		player = 10;
	if(play_sound)
		soundManager->PlaySoundEffect(SoundManager::SFX::CHAT);
	switch(player) {
	case 0: //from host
		chatMsg[0].append(dInfo.hostname);
		chatMsg[0].append(L": ");
		break;
	case 1: //from client
		chatMsg[0].append(dInfo.clientname);
		chatMsg[0].append(L": ");
		break;
	case 2: //host tag
		chatMsg[0].append(dInfo.hostname_tag);
		chatMsg[0].append(L": ");
		break;
	case 3: //client tag
		chatMsg[0].append(dInfo.clientname_tag);
		chatMsg[0].append(L": ");
		break;
	case 7: //local name
		chatMsg[0].append(ebNickName->getText());
		chatMsg[0].append(L": ");
		break;
	case 8: //system custom message, no prefix.
		chatMsg[0].append(L"[System]: ");
		break;
	case 9: //error message
		chatMsg[0].append(L"[Script Error]: ");
		break;
	case 10: //hidden name
		chatMsg[0].append(L"[********]: ");
		break;
	default: //from watcher or unknown
		if(player < 11 || player > 19)
			chatMsg[0].append(L"[---]: ");
	}
	chatMsg[0].append(msg);
}
void Game::ClearChatMsg() {
	for(int i = 7; i >= 0; --i) {
		chatTiming[i] = 0;
	}
}
void Game::AddDebugMsg(const char* msg) {
	if (enable_log & 0x1) {
		wchar_t wbuf[1024];
		BufferIO::DecodeUTF8(msg, wbuf);
		AddChatMsg(wbuf, 9);
	}
	if (enable_log & 0x2) {
		char msgbuf[1040];
		snprintf(msgbuf, sizeof msgbuf, "[Script Error]: %s", msg);
		ErrorLog(msgbuf);
	}
}
void Game::ErrorLog(const char* msg) {
	FILE* fp = fopen("error.log", "at");
	if(!fp)
		return;
	time_t nowtime = time(NULL);
	tm* localedtime = localtime(&nowtime);
	char timebuf[40];
	strftime(timebuf, 40, "%Y-%m-%d %H:%M:%S", localedtime);
	fprintf(fp, "[%s]%s\n", timebuf, msg);
	fclose(fp);
}
void Game::addMessageBox(const wchar_t* caption, const wchar_t* text) {
	SetStaticText(stSysMessage, 370 * xScale, guiFont, text);
	wSysMessage->setVisible(true);
	wSysMessage->getParent()->bringToFront(wSysMessage);
	//env->setFocus(wSysMessage);
}
void Game::ClearTextures() {
	matManager.mCard.setTexture(0, 0);
	ClearCardInfo(0);
	imgCard->setImage(imageManager.tCover[0]);
	scrCardText->setVisible(false);
	imgCard->setScaleImage(true);
	btnPSAU->setImage();
	btnPSDU->setImage();
	for(int i=0; i<=4; ++i) {
		btnCardSelect[i]->setImage();
		btnCardDisplay[i]->setImage();
	}
	imageManager.ClearTexture();
}
void Game::CloseGameButtons() {
	btnChainIgnore->setVisible(false);
	btnChainAlways->setVisible(false);
	btnChainWhenAvail->setVisible(false);
	btnCancelOrFinish->setVisible(false);
	btnSpectatorSwap->setVisible(false);
	btnShuffle->setVisible(false);
	wSurrender->setVisible(false);
}
void Game::CloseGameWindow() {
	CloseGameButtons();
	for(auto wit = fadingList.begin(); wit != fadingList.end(); ++wit) {
		if(wit->isFadein)
			wit->autoFadeoutFrame = 1;
	}
	wACMessage->setVisible(false);
	wANAttribute->setVisible(false);
	wANCard->setVisible(false);
	wANNumber->setVisible(false);
	wANRace->setVisible(false);
	wCardSelect->setVisible(false);
	wCardDisplay->setVisible(false);
	wCmdMenu->setVisible(false);
	wFTSelect->setVisible(false);
	wHand->setVisible(false);
	wMessage->setVisible(false);
	wOptions->setVisible(false);
	wPhase->setVisible(false);
	wPosSelect->setVisible(false);
	wQuery->setVisible(false);
	wReplayControl->setVisible(false);
	wReplaySave->setVisible(false);
	stHintMsg->setVisible(false);
	stTip->setVisible(false);
}
void Game::CloseDuelWindow() {
	CloseGameWindow();
	wCardImg->setVisible(false);
	wInfos->setVisible(false);
	wChat->setVisible(false);
	wPallet->setVisible(false);
	imgChat->setVisible(true);
	wSettings->setVisible(false);
	wLogs->setVisible(false);
	btnSideOK->setVisible(false);
	btnSideShuffle->setVisible(false);
	btnSideSort->setVisible(false);
	btnSideReload->setVisible(false);
	btnLeaveGame->setVisible(false);
	btnSpectatorSwap->setVisible(false);
	lstLog->clear();
	logParam.clear();
	lstHostList->clear();
	DuelClient::hosts.clear();
	ClearTextures();
	ResizeChatInputWindow();
	closeDoneSignal.Set();
}
int Game::LocalPlayer(int player) const {
	int pid = player ? 1 : 0;
	return dInfo.isFirst ? pid : 1 - pid;
}
int Game::OppositePlayer(int player) {
	auto player_side_bit = dInfo.isTag ? 0x2 : 0x1;
	return player ^ player_side_bit;
}
int Game::ChatLocalPlayer(int player) {
	if(player > 3)
		return player;
	bool is_self;
	if(dInfo.isStarted || is_siding) {
		if(dInfo.isInDuel)
			// when in duel
			player = mainGame->dInfo.isFirst ? player : OppositePlayer(player);
		else {
			// when changing side or waiting tp result
			auto selftype_boundary = dInfo.isTag ? 2 : 1;
			if(DuelClient::selftype >= selftype_boundary && DuelClient::selftype < 4)
				player = OppositePlayer(player);
		}
		if (DuelClient::selftype >= 4) {
			is_self = false;
		} else if (dInfo.isTag) {
			is_self =  (player & 0x2) == 0 && (player & 0x1) == (DuelClient::selftype & 0x1);
		} else {
			is_self = player == 0;
		}
	} else {
		// when in lobby
		is_self = player == DuelClient::selftype;
	}
	if(dInfo.isTag && (player == 1 || player == 2)) {
		player = 3 - player;
	}
	return player | (is_self ? 0x10 : 0);
}
const wchar_t* Game::LocalName(int local_player) {
	return local_player == 0 ? dInfo.hostname : dInfo.clientname;
}
void Game::ResizeChatInputWindow() {
	s32 x = 305 * xScale;
	if(is_building) x = 802 * xScale;
	wChat->setRelativePosition(recti(x, (GAME_HEIGHT - 35) * yScale, (GAME_WIDTH - 4) * xScale, GAME_HEIGHT * yScale));
	ebChatInput->setRelativePosition(recti(3 * xScale, 2 * yScale, (GAME_WIDTH - 6) * xScale - wChat->getRelativePosition().UpperLeftCorner.X, 28 * xScale));
}
void Game::ChangeToIGUIImageWindow(irr::gui::IGUIWindow* window, irr::gui::IGUIImage** pWindowBackground, irr::video::ITexture* image) {
    window->setDrawBackground(false);
    recti pos = window->getRelativePosition();
	*pWindowBackground = env->addImage(rect<s32>(0, 0, pos.getWidth(), pos.getHeight()), window, -1, 0, true);
	irr::gui::IGUIImage* bgwindow = *pWindowBackground;
	bgwindow->setImage(image);
	bgwindow->setScaleImage(true);
}
void Game::ChangeToIGUIImageButton(irr::gui::IGUIButton* button, irr::video::ITexture* image, irr::video::ITexture* pressedImage, irr::gui::CGUITTFont* font) {
    button->setDrawBorder(false);
    button->setUseAlphaChannel(true);
    button->setImage(image);
    button->setPressedImage(pressedImage);
    button->setScaleImage(true);
    button->setOverrideFont(font);
}

void Game::OnGameClose() {
	android::onGameExit(appMain);
    this->device->closeDevice();
}
}
