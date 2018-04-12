#include "config.h"
#include "game.h"
#include "image_manager.h"
#include "data_manager.h"
#include "deck_manager.h"
#include "replay.h"
#include "materials.h"
#include "duelclient.h"
#include "netserver.h"
#include "single_mode.h"

#ifndef _WIN32
#include <sys/types.h>
#include <dirent.h>
#endif
#ifdef _IRR_ANDROID_PLATFORM_
#include <android/AndroidSoundEffectPlayer.h>
#include <android/CAndroidGUIEditBox.h>
#include <android/CAndroidGUIComboBox.h>
#include <android/CAndroidGUIListBox.h>
#include <android/CAndroidGUISkin.h>
#include <COGLES2ExtensionHandler.h>
#include <COGLESExtensionHandler.h>
#include <COGLES2Driver.h>
#include <COGLESDriver.h>
#endif

unsigned short PRO_VERSION = DEF_PRO_VERSION;

namespace ygo {

Game* mainGame;

#ifdef _IRR_ANDROID_PLATFORM_
bool Game::Initialize(ANDROID_APP app) {
	this->appMain = app;
#else
bool Game::Initialize() {
#endif
	srand(time(0));
	irr::SIrrlichtCreationParameters params = irr::SIrrlichtCreationParameters();
#ifdef _IRR_ANDROID_PLATFORM_
    PRO_VERSION = android::getIntSetting(app, "game_version", PRO_VERSION);
	android::InitOptions *options = android::getInitOptions(app);
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
#else
	if(gameConf.use_d3d)
		params.DriverType = irr::video::EDT_DIRECT3D9;
	else
		params.DriverType = irr::video::EDT_OPENGL;
	params.WindowSize = irr::core::dimension2d<u32>(1280, 720);
#endif
	device = irr::createDeviceEx(params);
	if(!device)
		return false;
#ifdef _IRR_ANDROID_PLATFORM_
	if (!android::perfromTrick(app)) {
		return false;
	}
	android::initJavaBridge(app, device);
	soundEffectPlayer = new AndroidSoundEffectPlayer(app);
	soundEffectPlayer->setSEEnabled(options->isSoundEffectEnabled());
	app->onInputEvent = android::handleInput;
	ILogger* logger = device->getLogger();
//	logger->setLogLevel(ELL_WARNING);
	isPSEnabled = options->isPendulumScaleEnabled();
	IFileSystem * fs = device->getFileSystem();
	xScale = android::getScreenHeight(app) / 1024.0;
	yScale = android::getScreenWidth(app) / 640.0;
/*	if (xScale < yScale) {
*		xScale = android::getScreenWidth(app) / 1024.0;
*		yScale = android::getScreenHeight(app) / 640.0;
*	}//start ygocore when mobile is in landscape mode, or using Android tablets or TV.*/
	char log_scale[256] = {0};
	sprintf(log_scale, "xScale = %f, yScale = %f", xScale, yScale);
	Printer::log(log_scale);
	//io::path databaseDir = options->getDBDir();
	io::path workingDir = options->getWorkDir();
	char log_working[256] = {0};
	sprintf(log_working, "workingDir= %s", workingDir.c_str());
	Printer::log(log_working);
	fs->changeWorkingDirectoryTo(workingDir);

	/* Your media must be somewhere inside the assets folder. The assets folder is the root for the file system.
	 This example copies the media in the Android.mk makefile. */
	stringc mediaPath = "media/";

	// The Android assets file-system does not know which sub-directories it has (blame google).
	// So we have to add all sub-directories in assets manually. Otherwise we could still open the files,
	// but existFile checks will fail (which are for example needed by getFont).
	for ( u32 i=0; i < fs->getFileArchiveCount(); ++i )
	{
		IFileArchive* archive = fs->getFileArchive(i);
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
		if(fs->addFileArchive(zip_path.c_str(), false, false, EFAT_ZIP)) {
		    os::Printer::log("add arrchive ok ", zip_path.c_str());
	    }else{
			os::Printer::log("add arrchive fail ", zip_path.c_str());
		}
	}
	
#else
	xScale = 1.0;
	yScale = 1.0;
#endif
	LoadConfig();
	linePatternD3D = 0;
	linePatternGL = 0x0f0f;
	waitFrame = 0;
	signalFrame = 0;
	showcard = 0;
	is_attacking = false;
	lpframe = 0;
	lpcstring = 0;
	always_chain = false;
	ignore_chain = false;
	chain_when_avail = false;
	is_building = false;
	menuHandler.prev_operation = 0;
	menuHandler.prev_sel = -1;
	memset(&dInfo, 0, sizeof(DuelInfo));
	memset(chatTiming, 0, sizeof(chatTiming));
	deckManager.LoadLFList((workingDir + path("/lflist.conf")).c_str());
	driver = device->getVideoDriver();
#ifdef _IRR_ANDROID_PLATFORM_
	int quality = options->getCardQualityOp();
	if (driver->getDriverType() == EDT_OGLES2) {
		isNPOTSupported = ((COGLES2Driver *) driver)->queryOpenGLFeature(COGLES2ExtensionHandler::IRR_OES_texture_npot);
	} else {
		isNPOTSupported = ((COGLES1Driver *) driver)->queryOpenGLFeature(COGLES1ExtensionHandler::IRR_OES_texture_npot);
	}
	char log_npot[256];
	sprintf(log_npot, "isNPOTSupported = %d", isNPOTSupported);
	Printer::log(log_npot);
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
#else
	driver->setTextureCreationFlag(irr::video::ETCF_CREATE_MIP_MAPS, false);
#endif
	driver->setTextureCreationFlag(irr::video::ETCF_OPTIMIZED_FOR_QUALITY, true);

	imageManager.SetDevice(device);
	if(!imageManager.Initial(workingDir))
		return false;
	io::path* cdbs = options->getDBFiles();
	len = options->getDbCount();
	//os::Printer::log("load cdbs count %d", len);
	for(int i=0;i<len;i++){
		io::path cdb_path = cdbs[i];
		if(dataManager.LoadDB(cdb_path.c_str())) {
		    os::Printer::log("add cdb ok ", cdb_path.c_str());
	    }else{
			os::Printer::log("add cdb fail ", cdb_path.c_str());
		}
	}
	//if(!dataManager.LoadDB(workingDir.append("/cards.cdb").c_str()))
	//	return false;
	if(dataManager.LoadStrings((workingDir + path("/expansions/strings.conf")).c_str())){
		os::Printer::log("loadStrings expansions/strings.conf");
	}
	if(!dataManager.LoadStrings((workingDir + path("/strings.conf")).c_str()))
		return false;
	env = device->getGUIEnvironment();
	bool isAntialias = options->isFontAntiAliasEnabled();
	numFont = irr::gui::CGUITTFont::createTTFont(driver, fs, gameConf.numfont, (int)16 * yScale, isAntialias, false);
	adFont = irr::gui::CGUITTFont::createTTFont(driver, fs, gameConf.numfont, (int)12 * yScale, isAntialias, false);
	lpcFont = irr::gui::CGUITTFont::createTTFont(driver, fs, gameConf.numfont, (int)48 * yScale, isAntialias, true);
	guiFont = irr::gui::CGUITTFont::createTTFont(driver, fs, gameConf.textfont, (int)gameConf.textfontsize * yScale, isAntialias, true);
	textFont = guiFont;
	smgr = device->getSceneManager();
	device->setWindowCaption(L"[---]");
	device->setResizable(false);
	gui::IGUISkin* newskin = CAndroidGUISkin::createAndroidSkin(gui::EGST_BURNING_SKIN, driver, env, xScale, yScale);
	newskin->setFont(textFont);
	env->setSkin(newskin);
	newskin->drop();
	//main menu
	wchar_t strbuf[256];
	myswprintf(strbuf, L"YGOPro Version:%X.0%X.%X", PRO_VERSION >> 12, (PRO_VERSION >> 4) & 0xff, PRO_VERSION & 0xf);
#ifdef _IRR_ANDROID_PLATFORM_
	wMainMenu = env->addWindow(rect<s32>(370 * xScale, 150 * yScale, 650 * xScale, 465 * yScale), false, strbuf);
	wMainMenu->getCloseButton()->setVisible(false);
	btnLanMode = env->addButton(rect<s32>(15 * xScale, 30 * yScale, 265 * xScale, 80 * yScale), wMainMenu, BUTTON_LAN_MODE, dataManager.GetSysString(1200));
	btnSingleMode = env->addButton(rect<s32>(15 * xScale, 85 * yScale, 265 * xScale, 135 * yScale), wMainMenu, BUTTON_SINGLE_MODE, dataManager.GetSysString(1201));
	btnReplayMode = env->addButton(rect<s32>(15 * xScale, 140 * yScale, 265 * xScale, 190 * yScale), wMainMenu, BUTTON_REPLAY_MODE, dataManager.GetSysString(1202));
	btnDeckEdit = env->addButton(rect<s32>(15 * xScale, 195 * yScale, 265 * xScale, 245 * yScale), wMainMenu, BUTTON_DECK_EDIT, dataManager.GetSysString(1204));
	btnModeExit = env->addButton(rect<s32>(15 * xScale, 250 * yScale, 265 * xScale, 300 * yScale), wMainMenu, BUTTON_MODE_EXIT, dataManager.GetSysString(1210));

	//lan mode
	wLanWindow = env->addWindow(rect<s32>(200 * xScale, 80 * yScale, 820 * xScale, 590 * yScale), false, dataManager.GetSysString(1200));
	wLanWindow->getCloseButton()->setVisible(false);
	wLanWindow->setVisible(false);
	env->addStaticText(dataManager.GetSysString(1220), rect<s32>(35 * xScale, 40 * yScale, 220 * xScale, 75 * yScale), false, false, wLanWindow);
	ebNickName = CAndroidGUIEditBox::addAndroidEditBox(gameConf.nickname, true, env, rect<s32>(110 * xScale, 25 * yScale, 450 * xScale, 65 * yScale), wLanWindow);
	ebNickName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	lstHostList = CAndroidGUIListBox::addAndroidGUIListBox(env, rect<s32>(20 * xScale, 75 * yScale, 600 * xScale, 320 * yScale), wLanWindow, LISTBOX_LAN_HOST, true, 40 * xScale);
	lstHostList->setItemHeight(18 * yScale);
	btnLanRefresh = env->addButton(rect<s32>(250 * xScale, 330 * yScale, 350 * xScale, 370 * yScale), wLanWindow, BUTTON_LAN_REFRESH, dataManager.GetSysString(1217));
	env->addStaticText(dataManager.GetSysString(1221), rect<s32>(35 * xScale, 390 * yScale, 220 * xScale, 410 * yScale), false, false, wLanWindow);
	ebJoinHost = CAndroidGUIEditBox::addAndroidEditBox(gameConf.lasthost, true, env, rect<s32>(110 * xScale, 380 * yScale, 270 * xScale, 420 * yScale), wLanWindow);
	ebJoinHost->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	ebJoinPort = CAndroidGUIEditBox::addAndroidEditBox(gameConf.lastport, true, env, rect<s32>(280 * xScale, 380 * yScale, 340 * xScale, 420 * yScale), wLanWindow);
	ebJoinPort->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1222), rect<s32>(35 * xScale, 440 * yScale, 220 * xScale, 460 * yScale), false, false, wLanWindow);
	ebJoinPass = CAndroidGUIEditBox::addAndroidEditBox(gameConf.roompass, true, env, rect<s32>(110 * xScale, 430 * yScale, 250 * xScale, 470 * yScale), wLanWindow);
	ebJoinPass->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnJoinHost = env->addButton(rect<s32>(460 * xScale, 380 * yScale, 590 * xScale, 420 * yScale), wLanWindow, BUTTON_JOIN_HOST, dataManager.GetSysString(1223));
	btnJoinCancel = env->addButton(rect<s32>(460 * xScale, 430 * yScale, 590 * xScale, 470 * yScale), wLanWindow, BUTTON_JOIN_CANCEL, dataManager.GetSysString(1212));
	btnCreateHost = env->addButton(rect<s32>(460 * xScale, 25 * yScale, 590 * xScale, 65 * yScale), wLanWindow, BUTTON_CREATE_HOST, dataManager.GetSysString(1224));
#else
	wMainMenu = env->addWindow(rect<s32>(370 * xScale, 200 * yScale, 650 * xScale, 415 * yScale), false, strbuf);
	wMainMenu->getCloseButton()->setVisible(false);
	btnLanMode = env->addButton(rect<s32>(10 * xScale, 30 * yScale, 270 * xScale, 60 * yScale), wMainMenu, BUTTON_LAN_MODE, dataManager.GetSysString(1200));
	btnSingleMode = env->addButton(rect<s32>(10 * xScale, 65 * yScale, 270 * xScale, 95 * yScale), wMainMenu, BUTTON_SINGLE_MODE, dataManager.GetSysString(1201));
	btnReplayMode = env->addButton(rect<s32>(10 * xScale, 100 * yScale, 270 * xScale, 130 * yScale), wMainMenu, BUTTON_REPLAY_MODE, dataManager.GetSysString(1202));
//	btnTestMode = env->addButton(rect<s32>(10, 135, 270, 165), wMainMenu, BUTTON_TEST_MODE, dataManager.GetSysString(1203));
	btnDeckEdit = env->addButton(rect<s32>(10 * xScale, 135 * yScale, 270 * xScale, 165 * yScale), wMainMenu, BUTTON_DECK_EDIT, dataManager.GetSysString(1204));
	btnModeExit = env->addButton(rect<s32>(10 * xScale, 170 * yScale, 270 * xScale, 200 * yScale), wMainMenu, BUTTON_MODE_EXIT, dataManager.GetSysString(1210));
	//lan mode
	wLanWindow = env->addWindow(rect<s32>(220 * xScale, 100 * yScale, 800 * xScale, 520 * yScale), false, dataManager.GetSysString(1200));
	wLanWindow->getCloseButton()->setVisible(false);
	wLanWindow->setVisible(false);
	env->addStaticText(dataManager.GetSysString(1220), rect<s32>(10 * xScale, 30 * yScale, 220 * xScale, 50 * yScale), false, false, wLanWindow);
	ebNickName = env->addEditBox(gameConf.nickname, rect<s32>(110 * xScale, 25 * yScale, 450 * xScale, 50 * yScale), true, wLanWindow);
	ebNickName->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	lstHostList = env->addListBox(rect<s32>(10 * xScale, 60 * yScale, 570 * xScale, 320 * yScale), wLanWindow, LISTBOX_LAN_HOST, true);
	lstHostList->setItemHeight(18 * yScale);
	btnLanRefresh = env->addButton(rect<s32>(240 * xScale, 325 * yScale, 340 * xScale, 350 * yScale), wLanWindow, BUTTON_LAN_REFRESH, dataManager.GetSysString(1217));
	env->addStaticText(dataManager.GetSysString(1221), rect<s32>(10 * xScale, 360 * yScale, 220 * xScale, 380 * yScale), false, false, wLanWindow);
	ebJoinHost = env->addEditBox(gameConf.lasthost, rect<s32>(110 * xScale, 355 * yScale, 350 * xScale, 380 * yScale), true, wLanWindow);
	ebJoinHost->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	ebJoinPort = env->addEditBox(gameConf.lastport, rect<s32>(360 * xScale, 355 * yScale, 420 * xScale, 380 * yScale), true, wLanWindow);
	ebJoinPort->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1222), rect<s32>(10 * xScale, 390 * yScale, 220 * xScale, 410 * yScale), false, false, wLanWindow);
	ebJoinPass = env->addEditBox(gameConf.roompass, rect<s32>(110 * xScale, 385 * yScale, 420 * xScale, 410 * yScale), true, wLanWindow);
	ebJoinPass->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnJoinHost = env->addButton(rect<s32>(460 * xScale, 355 * yScale, 570 * xScale, 380 * yScale), wLanWindow, BUTTON_JOIN_HOST, dataManager.GetSysString(1223));
	btnJoinCancel = env->addButton(rect<s32>(460 * xScale, 385 * yScale, 570 * xScale, 410 * yScale), wLanWindow, BUTTON_JOIN_CANCEL, dataManager.GetSysString(1212));
	btnCreateHost = env->addButton(rect<s32>(460 * xScale, 25 * yScale, 570 * xScale, 50 * yScale), wLanWindow, BUTTON_CREATE_HOST, dataManager.GetSysString(1224));
#endif
	//create host
	wCreateHost = env->addWindow(rect<s32>(320 * xScale, 100 * yScale, 700 * xScale, 520 * yScale), false, dataManager.GetSysString(1224));
	wCreateHost->getCloseButton()->setVisible(false);
	wCreateHost->setVisible(false);
	env->addStaticText(dataManager.GetSysString(1226), rect<s32>(20 * xScale, 30 * yScale, 220 * xScale, 50 * yScale), false, false, wCreateHost);
#ifdef _IRR_ANDROID_PLATFORM_
	cbLFlist = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(140 * xScale, 25 * yScale, 300 * xScale, 50 * yScale), wCreateHost);
#else
	cbLFlist = env->addComboBox(rect<s32>(140 * xScale, 25 * yScale, 300 * xScale, 50 * yScale), wCreateHost);
#endif
	std::vector<LFList>::iterator iter;
	for (iter = deckManager._lfList.begin(); iter != deckManager._lfList.end(); iter++) {
		cbLFlist->addItem((*iter).listName, (*iter).hash);
	}
	env->addStaticText(dataManager.GetSysString(1225), rect<s32>(20 * xScale, 60 * yScale, 220 * xScale, 80 * yScale), false, false, wCreateHost);
#ifdef _IRR_ANDROID_PLATFORM_
	cbRule = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(140 * xScale, 55 * yScale, 300 * xScale, 80 * yScale), wCreateHost);
#else
	cbRule = env->addComboBox(rect<s32>(140 * xScale, 55 * yScale, 300 * xScale, 80 * yScale), wCreateHost);
#endif
	cbRule->addItem(dataManager.GetSysString(1240));
	cbRule->addItem(dataManager.GetSysString(1241));
	cbRule->addItem(dataManager.GetSysString(1242));
	cbRule->addItem(dataManager.GetSysString(1243));
	cbRule->setSelected(gameConf.defaultOT - 1);
	env->addStaticText(dataManager.GetSysString(1227), rect<s32>(20 * xScale, 90 * yScale, 220 * xScale, 110 * yScale), false, false, wCreateHost);
#ifdef _IRR_ANDROID_PLATFORM_
	cbMatchMode = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(140 * xScale, 85 * yScale, 300 * xScale, 110 * yScale), wCreateHost);
#else
	cbMatchMode = env->addComboBox(rect<s32>(140 * xScale, 85 * yScale, 300 * xScale, 110 * yScale), wCreateHost);
#endif
	cbMatchMode->addItem(dataManager.GetSysString(1244));
	cbMatchMode->addItem(dataManager.GetSysString(1245));
	cbMatchMode->addItem(dataManager.GetSysString(1246));
	env->addStaticText(dataManager.GetSysString(1237), rect<s32>(20 * xScale, 120 * yScale, 320 * xScale, 140 * yScale), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 180);
#ifdef _IRR_ANDROID_PLATFORM_
	ebTimeLimit = CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, rect<s32>(140 * xScale, 115 * yScale, 220 * xScale, 140 * yScale), wCreateHost);
#else
	ebTimeLimit = env->addEditBox(strbuf, rect<s32>(140 * xScale, 115 * yScale, 220 * xScale, 140 * yScale), true, wCreateHost);
#endif
	ebTimeLimit->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1228), rect<s32>(20 * xScale, 150 * yScale, 320 * xScale, 170 * yScale), false, false, wCreateHost);
	env->addStaticText(dataManager.GetSysString(1236), rect<s32>(20 * xScale, 180 * yScale, 220 * xScale, 200 * yScale), false, false, wCreateHost);
	cbDuelRule = env->addComboBox(rect<s32>(140 * xScale, 175 * yScale, 300 * xScale, 200 * yScale), wCreateHost);
	cbDuelRule->addItem(dataManager.GetSysString(1260));
	cbDuelRule->addItem(dataManager.GetSysString(1261));
	cbDuelRule->addItem(dataManager.GetSysString(1262));
	cbDuelRule->addItem(dataManager.GetSysString(1263));
	cbDuelRule->setSelected(DEFAULT_DUEL_RULE - 1);
	chkNoCheckDeck = env->addCheckBox(false, rect<s32>(20 * xScale, 210 * yScale, 170 * xScale, 230 * yScale), wCreateHost, -1, dataManager.GetSysString(1229));
	chkNoShuffleDeck = env->addCheckBox(false, rect<s32>(180 * xScale, 210 * yScale, 360 * xScale, 230 * yScale), wCreateHost, -1, dataManager.GetSysString(1230));
	env->addStaticText(dataManager.GetSysString(1231), rect<s32>(20 * xScale, 240 * yScale, 320 * xScale, 260 * yScale), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 8000);
#ifdef _IRR_ANDROID_PLATFORM_
	ebStartLP = CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, rect<s32>(140 * xScale, 235 * yScale, 220 * xScale, 260 * yScale), wCreateHost);
#else
	ebStartLP = env->addEditBox(strbuf, rect<s32>(140 * xScale, 235 * yScale, 220 * xScale, 260 * yScale), true, wCreateHost);
#endif
	ebStartLP->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1232), rect<s32>(20 * xScale, 270 * yScale, 320 * xScale, 290 * yScale), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 5);
#ifdef _IRR_ANDROID_PLATFORM_
	ebStartHand = CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, rect<s32>(140 * xScale, 265 * yScale, 220 * xScale, 290 * yScale), wCreateHost);
#else
	ebStartHand = env->addEditBox(strbuf, rect<s32>(140 * xScale, 265 * yScale, 220 * xScale, 290 * yScale), true, wCreateHost);
#endif
	ebStartHand->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1233), rect<s32>(20 * xScale, 300 * yScale, 320 * xScale, 320 * yScale), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 1);
#ifdef _IRR_ANDROID_PLATFORM_
	ebDrawCount = CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, rect<s32>(140 * xScale, 295 * yScale, 220 * xScale, 320 * yScale), wCreateHost);
#else
	ebDrawCount = env->addEditBox(strbuf, rect<s32>(140 * xScale, 295 * yScale, 220 * xScale, 320 * yScale), true, wCreateHost);
#endif
	ebDrawCount->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1234), rect<s32>(10 * xScale, 360 * yScale, 220 * xScale, 380 * yScale), false, false, wCreateHost);
#ifdef _IRR_ANDROID_PLATFORM_
	ebServerName = CAndroidGUIEditBox::addAndroidEditBox(gameConf.gamename, true, env, rect<s32>(110 * xScale, 355 * yScale, 250 * xScale, 380 * yScale), wCreateHost);
#else
	ebServerName = env->addEditBox(gameConf.gamename, rect<s32>(110 * xScale, 355 * yScale, 250 * xScale, 380 * yScale), true, wCreateHost);
#endif
	ebServerName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1235), rect<s32>(10 * xScale, 390 * yScale, 220 * xScale, 410 * yScale), false, false, wCreateHost);
#ifdef _IRR_ANDROID_PLATFORM_
	ebServerPass = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(110 * xScale, 385 * yScale, 250 * xScale, 410 * yScale), wCreateHost);
#else
	ebServerPass = env->addEditBox(L"", rect<s32>(110 * xScale, 385 * yScale, 250 * xScale, 410 * yScale), true, wCreateHost);
#endif
	ebServerPass->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnHostConfirm = env->addButton(rect<s32>(260 * xScale, 355 * yScale, 370 * xScale, 380 * yScale), wCreateHost, BUTTON_HOST_CONFIRM, dataManager.GetSysString(1211));
	btnHostCancel = env->addButton(rect<s32>(260 * xScale, 385 * yScale, 370 * xScale, 410 * yScale), wCreateHost, BUTTON_HOST_CANCEL, dataManager.GetSysString(1212));
	//host(single)
#ifdef _IRR_ANDROID_PLATFORM_
	wHostPrepare = env->addWindow(rect<s32>(250 * xScale, 30 * yScale, 780 * xScale, 550 * yScale), false, dataManager.GetSysString(1250));
	wHostPrepare->setDraggable(false);
	wHostPrepare->getCloseButton()->setVisible(false);
	wHostPrepare->setVisible(false);
	btnHostPrepDuelist = env->addButton(rect<s32>(10 * xScale, 30 * yScale, 110 * xScale, 55 * yScale), wHostPrepare, BUTTON_HP_DUELIST, dataManager.GetSysString(1251));
	for (int i = 0; i < 2; ++i) {
		stHostPrepDuelist[i] = env->addStaticText(L"",
				rect<s32>(60 * xScale, (65 + i * 45) * yScale, 260 * xScale,
						(105 + i * 45) * yScale), true, false, wHostPrepare);
		stHostPrepDuelist[i]->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
		btnHostPrepKick[i] = env->addButton(
				rect<s32>(10 * xScale, (65 + i * 45) * yScale, 50 * xScale,
						(105 + i * 45) * yScale), wHostPrepare, BUTTON_HP_KICK,
				L"X");
		chkHostPrepReady[i] = env->addCheckBox(false,
				rect<s32>(270 * xScale, (65 + i * 45) * yScale, 310 * xScale,
						(105 + i * 45) * yScale), wHostPrepare,
				CHECKBOX_HP_READY, L"");
		chkHostPrepReady[i]->setEnabled(false);
	}
	for (int i = 2; i < 4; ++i) {
		stHostPrepDuelist[i] = env->addStaticText(L"",
				rect<s32>(60 * xScale, (145 + i * 45) * yScale, 260 * xScale,
						(185 + i * 45) * yScale), true, false, wHostPrepare);
		stHostPrepDuelist[i]->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
		btnHostPrepKick[i] = env->addButton(
				rect<s32>(10 * xScale, (145 + i * 45) * yScale, 50 * xScale,
						(185 + i * 45) * yScale), wHostPrepare, BUTTON_HP_KICK,
				L"X");
		chkHostPrepReady[i] = env->addCheckBox(false,
				rect<s32>(270 * xScale, (145 + i * 45) * yScale, 310 * xScale,
						(185 + i * 45) * yScale), wHostPrepare,
				CHECKBOX_HP_READY, L"");
		chkHostPrepReady[i]->setEnabled(false);
	}
	btnHostPrepOB = env->addButton(
			rect<s32>(10 * xScale, 180 * yScale, 110 * xScale, 205 * yScale),
			wHostPrepare, BUTTON_HP_OBSERVER, dataManager.GetSysString(1252));
	myswprintf(dataManager.strBuffer, L"%ls%d", dataManager.GetSysString(1253),
			0);
	stHostPrepOB = env->addStaticText(dataManager.strBuffer,
			rect<s32>(10 * xScale, 210 * yScale, 270 * xScale, 230 * yScale),
			false, false, wHostPrepare);
	stHostPrepRule = env->addStaticText(L"",
			rect<s32>(300 * xScale, 30 * yScale, 460 * xScale, 230 * yScale),
			false, true, wHostPrepare);
	env->addStaticText(dataManager.GetSysString(1254),
			rect<s32>(10 * xScale, 385 * yScale, 110 * xScale, 410 * yScale),
			false, false, wHostPrepare);
	cbDeckSelect = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(120 * xScale, 380 * yScale, 270 * xScale, 405 * yScale), wHostPrepare);
	btnHostPrepReady = env->addButton(
            rect<s32>(170 * xScale, 180 * yScale, 280 * xScale, 205 * yScale),
			wHostPrepare, BUTTON_HP_READY, dataManager.GetSysString(1218));
	btnHostPrepNotReady = env->addButton(
	        rect<s32>(170 * xScale, 180 * yScale, 280 * xScale, 205 * yScale),
			wHostPrepare, BUTTON_HP_NOTREADY, dataManager.GetSysString(1219));
	btnHostPrepNotReady->setVisible(false);
	btnHostPrepStart = env->addButton(
			rect<s32>(280 * xScale, 380 * yScale, 390 * xScale, 405 * yScale),
			wHostPrepare, BUTTON_HP_START, dataManager.GetSysString(1215));
	btnHostPrepCancel = env->addButton(
			rect<s32>(400 * xScale, 380 * yScale, 510 * xScale, 405 * yScale),
			wHostPrepare, BUTTON_HP_CANCEL, dataManager.GetSysString(1210));
#else
	wHostPrepare = env->addWindow(rect<s32>(270, 120, 750, 440), false, dataManager.GetSysString(1250));
	wHostPrepare->getCloseButton()->setVisible(false);
	wHostPrepare->setVisible(false);
	btnHostPrepDuelist = env->addButton(rect<s32>(10, 30, 110, 55), wHostPrepare, BUTTON_HP_DUELIST, dataManager.GetSysString(1251));
	for(int i = 0; i < 2; ++i) {
		stHostPrepDuelist[i] = env->addStaticText(L"", rect<s32>(40, 65 + i * 25, 240, 85 + i * 25), true, false, wHostPrepare);
		btnHostPrepKick[i] = env->addButton(rect<s32>(10, 65 + i * 25, 30, 85 + i * 25), wHostPrepare, BUTTON_HP_KICK, L"X");
		chkHostPrepReady[i] = env->addCheckBox(false, rect<s32>(250, 65 + i * 25, 270, 85 + i * 25), wHostPrepare, CHECKBOX_HP_READY, L"");
		chkHostPrepReady[i]->setEnabled(false);
	}
	for(int i = 2; i < 4; ++i) {
		stHostPrepDuelist[i] = env->addStaticText(L"", rect<s32>(40, 75 + i * 25, 240, 95 + i * 25), true, false, wHostPrepare);
		btnHostPrepKick[i] = env->addButton(rect<s32>(10, 75 + i * 25, 30, 95 + i * 25), wHostPrepare, BUTTON_HP_KICK, L"X");
		chkHostPrepReady[i] = env->addCheckBox(false, rect<s32>(250, 75 + i * 25, 270, 95 + i * 25), wHostPrepare, CHECKBOX_HP_READY, L"");
		chkHostPrepReady[i]->setEnabled(false);
	}
	btnHostPrepOB = env->addButton(rect<s32>(10, 180, 110, 205), wHostPrepare, BUTTON_HP_OBSERVER, dataManager.GetSysString(1252));
	myswprintf(dataManager.strBuffer, L"%ls%d", dataManager.GetSysString(1253), 0);
	stHostPrepOB = env->addStaticText(dataManager.strBuffer, rect<s32>(10, 210, 270, 230), false, false, wHostPrepare);
	stHostPrepRule = env->addStaticText(L"", rect<s32>(280, 30, 460, 230), false, true, wHostPrepare);
	env->addStaticText(dataManager.GetSysString(1254), rect<s32>(10, 235, 110, 255), false, false, wHostPrepare);
	cbDeckSelect = env->addComboBox(rect<s32>(120, 230, 270, 255), wHostPrepare);
	cbDeckSelect->setMaxSelectionRows(10);
	btnHostPrepReady = env->addButton(rect<s32>(170, 180, 270, 205), wHostPrepare, BUTTON_HP_READY, dataManager.GetSysString(1218));
	btnHostPrepNotReady = env->addButton(rect<s32>(170, 180, 270, 205), wHostPrepare, BUTTON_HP_NOTREADY, dataManager.GetSysString(1219));
	btnHostPrepNotReady->setVisible(false);
	btnHostPrepStart = env->addButton(rect<s32>(230, 280, 340, 305), wHostPrepare, BUTTON_HP_START, dataManager.GetSysString(1215));
	btnHostPrepCancel = env->addButton(rect<s32>(350, 280, 460, 305), wHostPrepare, BUTTON_HP_CANCEL, dataManager.GetSysString(1210));
#endif

	//img
	wCardImg = env->addStaticText(L"", rect<s32>(1 * xScale, 1 * yScale, ( 1 + CARD_IMG_WIDTH + 20) * xScale, (1 + CARD_IMG_HEIGHT + 18) * yScale), true, false, 0, -1, true);
	wCardImg->setBackgroundColor(0x6011113d);
	wCardImg->setVisible(false);
	imgCard = env->addImage(rect<s32>(10 * xScale, 9 * yScale, (10 + CARD_IMG_WIDTH) * xScale, (9 + CARD_IMG_HEIGHT) * yScale), wCardImg);
	imgCard->setImage(imageManager.tCover[0]);
	imgCard->setScaleImage(true);
	imgCard->setUseAlphaChannel(true);
#ifdef _IRR_ANDROID_PLATFORM_
	//phase
	wPhase = env->addStaticText(L"", rect<s32>(480 * xScale, 305 * yScale, 895 * xScale, 335 * yScale));
	wPhase->setVisible(false);
	btnPhaseStatus = env->addButton(rect<s32>(0 * xScale, 0 * yScale, 50 * xScale, 30 * yScale), wPhase, BUTTON_PHASE, L"");
	btnPhaseStatus->setIsPushButton(true);
	btnPhaseStatus->setPressed(true);
	btnPhaseStatus->setVisible(false);
	btnBP = env->addButton(rect<s32>(160 * xScale, 0 * yScale, 210 * xScale, 30 * yScale), wPhase, BUTTON_BP, L"\xff22\xff30");
	btnBP->setVisible(false);
	btnM2 = env->addButton(rect<s32>(160 * xScale, 0 * yScale, 210 * xScale, 30 * yScale), wPhase, BUTTON_M2, L"\xff2d\xff12");
	btnM2->setVisible(false);
	btnEP = env->addButton(rect<s32>(320 * xScale, 0 * yScale, 370 * xScale, 30 * yScale), wPhase, BUTTON_EP, L"\xff25\xff30");
	btnEP->setVisible(false);
#else
    //phase
	wPhase = env->addStaticText(L"", rect<s32>(480, 310, 855, 330));
	wPhase->setVisible(false);
	btnPhaseStatus = env->addButton(rect<s32>(0, 0, 50, 20), wPhase, BUTTON_PHASE, L"");
	btnPhaseStatus->setIsPushButton(true);
	btnPhaseStatus->setPressed(true);
	btnPhaseStatus->setVisible(false);
	btnBP = env->addButton(rect<s32>(160, 0, 210, 20), wPhase, BUTTON_BP, L"\xff22\xff30");
	btnBP->setVisible(false);
	btnM2 = env->addButton(rect<s32>(160, 0, 210, 20), wPhase, BUTTON_M2, L"\xff2d\xff12");
	btnM2->setVisible(false);
	btnEP = env->addButton(rect<s32>(320, 0, 370, 20), wPhase, BUTTON_EP, L"\xff25\xff30");
	btnEP->setVisible(false);
#endif
	//tab
	wInfos = env->addTabControl(rect<s32>(1 * xScale, 275 * yScale, 301 * xScale, 639 * yScale), 0, true);
	wInfos->setTabExtraWidth(16 * xScale);
	wInfos->setTabHeight(35 * yScale);
	wInfos->setVisible(false);
	//info
	irr::gui::IGUITab* tabInfo = wInfos->addTab(dataManager.GetSysString(1270));
	stName = env->addStaticText(L"", rect<s32>(10 * xScale, 10 * yScale, 287 * xScale, 32 * yScale), true, false, tabInfo, -1, false);
	stName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	stInfo = env->addStaticText(L"", rect<s32>(15 * xScale, 37 * yScale, 296 * xScale, 60 * yScale), false, true, tabInfo, -1, false);
	stInfo->setOverrideColor(SColor(255, 149, 211, 137));//255, 0, 0, 255
	stDataInfo = env->addStaticText(L"", rect<s32>(15 * xScale, 60 * yScale, 296 * xScale, 83 * yScale), false, true, tabInfo, -1, false);
	stDataInfo->setOverrideColor(SColor(255, 222, 215, 100));//255, 0, 0, 255
	stSetName = env->addStaticText(L"", rect<s32>(15 * xScale, 83 * yScale, 296 * xScale, 106 * yScale), false, true, tabInfo, -1, false);
	stSetName->setOverrideColor(SColor(255, 255, 152, 42));//255, 0, 0, 255
	stText = env->addStaticText(L"", rect<s32>(15 * xScale, 106 * yScale, 287 * xScale, 324 * yScale), false, true, tabInfo, -1, false);
#ifdef _IRR_ANDROID_PLATFORM_
	scrCardText = env->addScrollBar(false, rect<s32>(425 * xScale, 106 * yScale, 495 * xScale, 580 * yScale), tabInfo, SCROLL_CARDTEXT);
#else
	scrCardText = env->addScrollBar(false, rect<s32>(267 * xScale, 106 * yScale, 287 * xScale, 324 * yScale), tabInfo, SCROLL_CARDTEXT);
#endif
	scrCardText->setLargeStep(1);
	scrCardText->setSmallStep(1);
	scrCardText->setVisible(false);
	//log
	irr::gui::IGUITab* tabLog =  wInfos->addTab(dataManager.GetSysString(1271));
#ifdef _IRR_ANDROID_PLATFORM_
	lstLog = CAndroidGUIListBox::addAndroidGUIListBox(env, rect<s32>(10 * xScale, 10 * yScale, 290 * xScale, 290 * yScale), tabLog, LISTBOX_LOG, false, 40 * xScale);
#else
	lstLog = env->addListBox(rect<s32>(10 * xScale, 10 * yScale, 290 * xScale, 290 * yScale), tabLog, LISTBOX_LOG, false);
#endif
	lstLog->setItemHeight(18 * yScale);
	btnClearLog = env->addButton(rect<s32>(160 * xScale, 300 * yScale, 260 * xScale, 325 * yScale), tabLog, BUTTON_CLEAR_LOG, dataManager.GetSysString(1272));
	//helper
	irr::gui::IGUITab* tabHelper = wInfos->addTab(dataManager.GetSysString(1298));
	int posX = 20 * xScale;
	int posY = 20 * yScale;
	chkMAutoPos = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 25 * yScale), tabHelper, -1, dataManager.GetSysString(1274));
	chkMAutoPos->setChecked(gameConf.chkMAutoPos != 0);
	posY += 60;
	chkSTAutoPos = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 25 * yScale), tabHelper, -1, dataManager.GetSysString(1278));
	chkSTAutoPos->setChecked(gameConf.chkSTAutoPos != 0);
	posY += 60;
	chkRandomPos = env->addCheckBox(false, rect<s32>(posX + 20 * xScale, posY, posX + (20 + 260) * xScale, posY + 25 * yScale), tabHelper, -1, dataManager.GetSysString(1275));
	chkRandomPos->setChecked(gameConf.chkRandomPos != 0);
	posY += 60;
	chkAutoChain = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 25 * yScale), tabHelper, -1, dataManager.GetSysString(1276));
	chkAutoChain->setChecked(gameConf.chkAutoChain != 0);
	posY += 60;
	chkWaitChain = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 25 * yScale), tabHelper, -1, dataManager.GetSysString(1277));
	chkWaitChain->setChecked(gameConf.chkWaitChain != 0);
	//system
	irr::gui::IGUITab* tabSystem = wInfos->addTab(dataManager.GetSysString(1273));
	posY = 20 * xScale;
	chkIgnore1 = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260, posY + 25 * yScale), tabSystem, -1, dataManager.GetSysString(1290));
	chkIgnore1->setChecked(gameConf.chkIgnore1 != 0);
	posY += 60;
	chkIgnore2 = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260, posY + 25 * yScale), tabSystem, -1, dataManager.GetSysString(1291));
	chkIgnore2->setChecked(gameConf.chkIgnore2 != 0);
	posY += 60;
	chkHideSetname = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260, posY + 25 * yScale), tabSystem, -1, dataManager.GetSysString(1354));
	chkHideSetname->setChecked(gameConf.chkHideSetname != 0);
	posY += 60;
	chkIgnoreDeckChanges = env->addCheckBox(false, rect<s32>(posX, posY, posX + 260 * xScale, posY + 25 * yScale), tabSystem, -1, dataManager.GetSysString(1357));
	chkIgnoreDeckChanges->setChecked(gameConf.chkIgnoreDeckChanges != 0);
	posY += 60;
	chkAutoSearch = env->addCheckBox(false, rect<s32>(posX, posY, posX + 225 * xScale, posY + 25 * yScale), tabSystem, CHECKBOX_AUTO_SEARCH, dataManager.GetSysString(1358));
	chkAutoSearch->setChecked(gameConf.auto_search_limit >= 0);
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
#ifdef _IRR_ANDROID_PLATFORM_
	//first or second to go
	wFTSelect = env->addWindow(rect<s32>(530 * xScale, 220 * yScale, 800 * xScale, 380 * yScale), false, L"");
	wFTSelect->getCloseButton()->setVisible(false);
	wFTSelect->setVisible(false);
	btnFirst = env->addButton(rect<s32>(10 * xScale, 30 * yScale, 260 * xScale, 75 * yScale), wFTSelect, BUTTON_FIRST, dataManager.GetSysString(100));
	btnSecond = env->addButton(rect<s32>(10 * xScale, 85 * yScale, 260 * xScale, 130 * yScale), wFTSelect, BUTTON_SECOND, dataManager.GetSysString(101));
	//message (310)
	wMessage = env->addWindow(rect<s32>(470 * xScale, 180 * yScale, 860 * xScale, 360 * yScale), false, dataManager.GetSysString(1216));
	wMessage->getCloseButton()->setVisible(false);
	wMessage->setVisible(false);
	stMessage = env->addStaticText(L"", rect<s32>(20 * xScale, 20 * yScale, 390 * xScale, 100 * yScale), false, true, wMessage, -1, false);
	stMessage->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnMsgOK = env->addButton(rect<s32>(130 * xScale, 115 * yScale, 260 * xScale, 165 * yScale), wMessage, BUTTON_MSG_OK, dataManager.GetSysString(1211));
	//auto fade message (310)
	wACMessage = env->addWindow(rect<s32>(490 * xScale, 240 * yScale, 840 * xScale, 300 * yScale), false, L"");
	wACMessage->getCloseButton()->setVisible(false);
	wACMessage->setVisible(false);
	wACMessage->setDrawBackground(false);
	stACMessage = env->addStaticText(L"", rect<s32>(0 * xScale, 0 * yScale, 350 * xScale, 60 * yScale), true, true, wACMessage, -1, true);
	stACMessage->setBackgroundColor(0x6011113d);
	stACMessage->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	//yes/no (310)
	wQuery = env->addWindow(rect<s32>(470 * xScale, 180 * yScale, 860 * xScale, 360 * yScale), false, dataManager.GetSysString(560));
	wQuery->getCloseButton()->setVisible(false);
	wQuery->setVisible(false);
	stQMessage =  env->addStaticText(L"", rect<s32>(20 * xScale, 20 * yScale, 390 * xScale, 100 * yScale), false, true, wQuery, -1, false);
	stQMessage->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnYes = env->addButton(rect<s32>(80 * xScale, 115 * yScale, 170 * xScale, 165 * yScale), wQuery, BUTTON_YES, dataManager.GetSysString(1213));
	btnNo = env->addButton(rect<s32>(200 * xScale, 115 * yScale, 290 * xScale, 165 * yScale), wQuery, BUTTON_NO, dataManager.GetSysString(1214));
	//options (310)
	wOptions = env->addWindow(rect<s32>(470 * xScale, 180 * yScale, 860 * xScale, 360 * yScale), false, L"");
	wOptions->getCloseButton()->setVisible(false);
	wOptions->setVisible(false);
	stOptions =  env->addStaticText(L"", rect<s32>(20 * xScale, 20 * yScale, 390 * xScale, 100 * yScale), false, true, wOptions, -1, false);
	stOptions->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnOptionOK = env->addButton(rect<s32>(130 * xScale, 115 * yScale, 260 * xScale, 165 * yScale), wOptions, BUTTON_OPTION_OK, dataManager.GetSysString(1211));
	btnOptionp = env->addButton(rect<s32>(20 * xScale, 115 * yScale, 100 * xScale, 165 * yScale), wOptions, BUTTON_OPTION_PREV, L"<<<");
	btnOptionn = env->addButton(rect<s32>(290 * xScale, 115 * yScale, 370 * xScale, 165 * yScale), wOptions, BUTTON_OPTION_NEXT, L">>>");
#else
	wFTSelect = env->addWindow(rect<s32>(550 * xScale, 240 * yScale, 780 * xScale, 340 * yScale), false, L"");
	wFTSelect->getCloseButton()->setVisible(false);
	wFTSelect->setVisible(false);
	btnFirst = env->addButton(rect<s32>(10 * xScale, 30 * yScale, 220 * xScale, 55 * yScale), wFTSelect, BUTTON_FIRST, dataManager.GetSysString(100));
	btnSecond = env->addButton(rect<s32>(10 * xScale, 60 * yScale, 220 * xScale, 85 * yScale), wFTSelect, BUTTON_SECOND, dataManager.GetSysString(101));

	wMessage = env->addWindow(rect<s32>(490 * xScale, 200 * yScale, 840 * xScale, 340 * yScale), false, dataManager.GetSysString(1216));
	wMessage->getCloseButton()->setVisible(false);
	wMessage->setVisible(false);
	stMessage =  env->addStaticText(L"", rect<s32>(20 * xScale, 20 * yScale, 350 * xScale, 100 * yScale), false, true, wMessage, -1, false);
	stMessage->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnMsgOK = env->addButton(rect<s32>(130 * xScale, 105 * yScale, 220 * xScale, 130 * yScale), wMessage, BUTTON_MSG_OK, dataManager.GetSysString(1211));
	//auto fade message (310)
	wACMessage = env->addWindow(rect<s32>(490 * xScale, 240 * yScale, 840 * xScale, 300 * yScale), false, L"");
	wACMessage->getCloseButton()->setVisible(false);
	wACMessage->setVisible(false);
	wACMessage->setDrawBackground(false);
	stACMessage = env->addStaticText(L"", rect<s32>(0 * xScale, 0 * yScale, 350 * xScale, 60 * yScale), true, true, wACMessage, -1, true);
	stACMessage->setBackgroundColor(0x6011113d);
	stACMessage->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	//yes/no (310)
	wQuery = env->addWindow(rect<s32>(490 * xScale, 200 * yScale, 840 * xScale, 340 * yScale), false, dataManager.GetSysString(560));
	wQuery->getCloseButton()->setVisible(false);
	wQuery->setVisible(false);
	stQMessage =  env->addStaticText(L"", rect<s32>(20 * xScale, 20 * yScale, 350 * xScale, 100 * yScale), false, true, wQuery, -1, false);
	stQMessage->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnYes = env->addButton(rect<s32>(100 * xScale, 105 * yScale, 150 * xScale, 130 * yScale), wQuery, BUTTON_YES, dataManager.GetSysString(1213));
	btnNo = env->addButton(rect<s32>(200 * xScale, 105 * yScale, 250 * xScale, 130 * yScale), wQuery, BUTTON_NO, dataManager.GetSysString(1214));

	//options (310)
	wOptions = env->addWindow(rect<s32>(490 * xScale, 200 * yScale, 840 * xScale, 340 * yScale), false, L"");
	wOptions->getCloseButton()->setVisible(false);
	wOptions->setVisible(false);
	stOptions =  env->addStaticText(L"", rect<s32>(20 * xScale, 20 * yScale, 350 * xScale, 100 * yScale), false, true, wOptions, -1, false);
	stOptions->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnOptionOK = env->addButton(rect<s32>(130 * xScale, 105 * yScale, 220 * xScale, 130 * yScale), wOptions, BUTTON_OPTION_OK, dataManager.GetSysString(1211));
	btnOptionp = env->addButton(rect<s32>(20 * xScale, 105 * yScale, 60 * xScale, 130 * yScale), wOptions, BUTTON_OPTION_PREV, L"<<<");
	btnOptionn = env->addButton(rect<s32>(290 * xScale, 105 * yScale, 330 * xScale, 130 * yScale), wOptions, BUTTON_OPTION_NEXT, L">>>");
#endif
	//pos select
	wPosSelect = env->addWindow(rect<s32>(340 * xScale, 200 * yScale, 935 * xScale, 410 * yScale), false, dataManager.GetSysString(561));
	wPosSelect->getCloseButton()->setVisible(false);
	wPosSelect->setVisible(false);
	btnPSAU = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(10 * xScale, 45 * yScale, 150 * xScale, 185 * yScale), wPosSelect, BUTTON_POS_AU);
	btnPSAU->setImageScale(core::vector2df(0.5, 0.5));
	btnPSAD = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(155 * xScale, 45 * yScale, 295 * xScale, 185 * yScale), wPosSelect, BUTTON_POS_AD);
	btnPSAD->setImageScale(core::vector2df(0.5, 0.5));
	btnPSAD->setImage(imageManager.tCover[0], rect<s32>(0, 0, CARD_IMG_WIDTH * xScale, CARD_IMG_HEIGHT * yScale));
	btnPSDU = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(300 * xScale, 45 * yScale, 440 * xScale, 185 * yScale), wPosSelect, BUTTON_POS_DU);
	btnPSDU->setImageScale(core::vector2df(0.5, 0.5));
	btnPSDU->setImageRotation(270);
	btnPSDD = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>(445 * xScale, 45 * yScale, 585 * xScale, 185 * yScale), wPosSelect, BUTTON_POS_DD);
	btnPSDD->setImageScale(core::vector2df(0.5, 0.5));
	btnPSDD->setImageRotation(270);
	btnPSDD->setImage(imageManager.tCover[0], rect<s32>(0 * xScale, 0 * yScale, CARD_IMG_WIDTH * xScale, CARD_IMG_HEIGHT * yScale));
	//card select
#ifdef _IRR_ANDROID_PLATFORM_
	wCardSelect = env->addWindow(rect<s32>(320 * xScale, 100 * yScale, 1000 * xScale, 430 * yScale), false, L"");
#else
	wCardSelect = env->addWindow(rect<s32>(320 * xScale, 100 * yScale, 1000 * xScale, 400 * yScale), false, L"");
#endif
	wCardSelect->getCloseButton()->setVisible(false);
	wCardSelect->setVisible(false);
	for(int i = 0; i < 5; ++i) {
		stCardPos[i] = env->addStaticText(L"", rect<s32>((40 + 125 * i) * xScale, 30 * yScale, (139 + 125 * i) * xScale, 50 * yScale), true, false, wCardSelect, -1, true);
		stCardPos[i]->setBackgroundColor(0xffffffff);
		stCardPos[i]->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
		btnCardSelect[i] = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>((30 + 125 * i)  * xScale, 55 * yScale, (150 + 125 * i) * xScale, 225 * yScale), wCardSelect, BUTTON_CARD_0 + i);
		btnCardSelect[i]->setImageScale(core::vector2df(0.6f * xScale, 0.6f  * yScale));
	}
#ifdef _IRR_ANDROID_PLATFORM_
	scrCardList = env->addScrollBar(true, rect<s32>(30 * xScale, 235 * yScale, 650 * xScale, 275 * yScale), wCardSelect, SCROLL_CARD_SELECT);
	btnSelectOK = env->addButton(rect<s32>(300 * xScale, 285 * yScale, 380 * xScale, 325 * yScale), wCardSelect, BUTTON_CARD_SEL_OK, dataManager.GetSysString(1211));
	//card display
	wCardDisplay = env->addWindow(rect<s32>(320 * xScale, 100 * yScale, 1000 * xScale, 400 * yScale), false, L"");
	wCardDisplay->getCloseButton()->setVisible(false);
	wCardDisplay->setVisible(false);
	for(int i = 0; i < 5; ++i) {
		stDisplayPos[i] = env->addStaticText(L"", rect<s32>((30 + 125 * i) *xScale, 30 * yScale, (150 + 125 * i) * xScale, 50 * yScale), true, false, wCardDisplay, -1, true);
		stDisplayPos[i]->setBackgroundColor(0xffffffff);
		stDisplayPos[i]->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
		btnCardDisplay[i] = irr::gui::CGUIImageButton::addImageButton(env, rect<s32>((30 + 125 * i) * xScale, 55 * yScale, (150 + 125 * i) * xScale, 225 * yScale), wCardDisplay, BUTTON_DISPLAY_0 + i);
		btnCardDisplay[i]->setImageScale(core::vector2df(0.6f * xScale, 0.6f * yScale));
	}
	scrDisplayList = env->addScrollBar(true, rect<s32>(30 * xScale, 235 * yScale, 650 * xScale, 255 * yScale), wCardDisplay, SCROLL_CARD_DISPLAY);
	btnDisplayOK = env->addButton(rect<s32>(300 * xScale, 265 * yScale, 380 * xScale, 290 * yScale), wCardDisplay, BUTTON_CARD_DISP_OK, dataManager.GetSysString(1211));
#else
	scrCardList = env->addScrollBar(true, rect<s32>(30 * xScale, 235 * yScale, 650 * xScale, 255 * yScale), wCardSelect, SCROLL_CARD_SELECT);
	btnSelectOK = env->addButton(rect<s32>(300 * xScale, 265 * yScale, 380 * xScale, 290 * yScale), wCardSelect, BUTTON_CARD_SEL_OK, dataManager.GetSysString(1211));
#endif
	//announce number
	wANNumber = env->addWindow(rect<s32>(550 * xScale, 200 * yScale, 780 * xScale, 295 * yScale), false, L"");
	wANNumber->getCloseButton()->setVisible(false);
	wANNumber->setVisible(false);
#ifdef _IRR_ANDROID_PLATFORM_
	cbANNumber = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(40 * xScale, 30 * yScale, 190 * xScale, 50 * yScale), wANNumber, -1);
#else
	cbANNumber =  env->addComboBox(rect<s32>(40 * xScale, 30 * yScale, 190 * xScale, 50 * yScale), wANNumber, -1);
#endif
	cbANNumber->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnANNumberOK = env->addButton(rect<s32>(80 * xScale, 60 * yScale, 150 * xScale, 85 * yScale), wANNumber, BUTTON_ANNUMBER_OK, dataManager.GetSysString(1211));
	//announce card
	wANCard = env->addWindow(rect<s32>(400 * xScale, 100 * yScale, 800 * xScale, 400 * yScale), false, L"");
	wANCard->getCloseButton()->setVisible(false);
	wANCard->setVisible(false);
#ifdef _IRR_ANDROID_PLATFORM_
	ebANCard = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(20 * xScale, 25 * yScale, 380 * xScale, 55 * yScale), wANCard, EDITBOX_ANCARD);
	lstANCard = CAndroidGUIListBox::addAndroidGUIListBox(env, rect<s32>(20 * xScale, 55 * yScale, 380 * xScale, 260 * yScale), wANCard, LISTBOX_ANCARD, true, 40 * xScale);
#else
	ebANCard = env->addEditBox(L"", rect<s32>(20, 25, 190, 45), true, wANCard, EDITBOX_ANCARD);
	lstANCard = env->addListBox(rect<s32>(20, 50, 190, 160), wANCard, LISTBOX_ANCARD, true);
#endif
	ebANCard->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
//dont merge
	btnANCardOK = env->addButton(rect<s32>(140 * xScale, 260 * yScale, 270 * xScale, 295 * yScale), wANCard, BUTTON_ANCARD_OK, dataManager.GetSysString(1211));
	//announce attribute
	wANAttribute = env->addWindow(rect<s32>(500 * xScale, 200 * yScale, 830 * xScale, 295 * yScale), false, dataManager.GetSysString(562));
	wANAttribute->getCloseButton()->setVisible(false);
	wANAttribute->setVisible(false);
	for(int filter = 0x1, i = 0; i < 7; filter <<= 1, ++i)
		chkAttribute[i] = env->addCheckBox(false, rect<s32>((10 + (i % 4) * 80) * xScale, (25 + (i / 4) * 25) * yScale, (90 + (i % 4) * 80) * xScale, (50 + (i / 4) * 25) * yScale),
		                                   wANAttribute, CHECK_ATTRIBUTE, dataManager.FormatAttribute(filter));
	//announce race
	wANRace = env->addWindow(rect<s32>(480 * xScale, 200 * yScale, 850 * xScale, 430 * yScale), false, dataManager.GetSysString(563));
	wANRace->getCloseButton()->setVisible(false);
	wANRace->setVisible(false);
	for(int filter = 0x1, i = 0; i < 25; filter <<= 1, ++i)
		chkRace[i] = env->addCheckBox(false, rect<s32>((10 + (i % 4) * 90) * xScale, (25 + (i / 4) * 25) * yScale, (100 + (i % 4) * 90) * xScale, (50 + (i / 4) * 25) * yScale),
		                              wANRace, CHECK_RACE, dataManager.FormatRace(filter));
	//selection hint
	stHintMsg = env->addStaticText(L"", rect<s32>(500 * xScale, 60 * yScale, 820 * xScale, 90 * yScale), true, false, 0, -1, false);
	stHintMsg->setBackgroundColor(0x6011113d);
	stHintMsg->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	stHintMsg->setVisible(false);
	stTip = env->addStaticText(L"", rect<s32>(0 * xScale, 0 * yScale, 150 * xScale, 150 * yScale), false, true, 0, -1, true);
	stTip->setBackgroundColor(0x6011113d);
	stTip->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	stTip->setVisible(false);
	//cmd menu
	wCmdMenu = env->addWindow(rect<s32>(10 * xScale, 10 * yScale, 110 * xScale, 179 * yScale), false, L"");
	wCmdMenu->setDrawTitlebar(false);
	wCmdMenu->setVisible(false);
	wCmdMenu->getCloseButton()->setVisible(false);
#ifdef _IRR_ANDROID_PLATFORM_
	btnActivate = env->addButton(rect<s32>(1 * xScale, 1 * yScale, 99 * xScale, 40 * yScale), wCmdMenu, BUTTON_CMD_ACTIVATE, dataManager.GetSysString(1150));
	btnSummon = env->addButton(rect<s32>(1 * xScale, 41 * yScale, 99 * xScale, 81 * yScale), wCmdMenu, BUTTON_CMD_SUMMON, dataManager.GetSysString(1151));
	btnSPSummon = env->addButton(rect<s32>(1 * xScale, 82 * yScale, 99 * xScale, 122 * yScale), wCmdMenu, BUTTON_CMD_SPSUMMON, dataManager.GetSysString(1152));
	btnMSet = env->addButton(rect<s32>(1 * xScale, 123 * yScale, 99 * xScale, 164 * yScale), wCmdMenu, BUTTON_CMD_MSET, dataManager.GetSysString(1153));
	btnSSet = env->addButton(rect<s32>(1 * xScale, 165 * yScale, 99 * xScale, 205 * yScale), wCmdMenu, BUTTON_CMD_SSET, dataManager.GetSysString(1153));
	btnRepos = env->addButton(rect<s32>(1 * xScale, 206 * yScale, 99 * xScale, 246 * yScale), wCmdMenu, BUTTON_CMD_REPOS, dataManager.GetSysString(1154));
	btnAttack = env->addButton(rect<s32>(1 * xScale, 247 * yScale, 99 * xScale, 288 * yScale), wCmdMenu, BUTTON_CMD_ATTACK, dataManager.GetSysString(1157));
	btnShowList = env->addButton(rect<s32>(1 * xScale, 289 * yScale, 99 * xScale, 329 * yScale), wCmdMenu, BUTTON_CMD_SHOWLIST, dataManager.GetSysString(1158));
	btnOperation = env->addButton(rect<s32>(1 * xScale, 169 * yScale, 100 * xScale, 209 * yScale), wCmdMenu, BUTTON_CMD_ACTIVATE, dataManager.GetSysString(1161));
	btnReset = env->addButton(rect<s32>(1 * xScale, 190 * yScale , 99 * xScale, 210 * yScale), wCmdMenu, BUTTON_CMD_RESET, dataManager.GetSysString(1162));
#else
	btnActivate = env->addButton(rect<s32>(1, 1, 99, 21), wCmdMenu, BUTTON_CMD_ACTIVATE, dataManager.GetSysString(1150));
	btnSummon = env->addButton(rect<s32>(1, 22, 99, 42), wCmdMenu, BUTTON_CMD_SUMMON, dataManager.GetSysString(1151));
	btnSPSummon = env->addButton(rect<s32>(1, 43, 99, 63), wCmdMenu, BUTTON_CMD_SPSUMMON, dataManager.GetSysString(1152));
	btnMSet = env->addButton(rect<s32>(1, 64, 99, 84), wCmdMenu, BUTTON_CMD_MSET, dataManager.GetSysString(1153));
	btnSSet = env->addButton(rect<s32>(1, 85, 99, 105), wCmdMenu, BUTTON_CMD_SSET, dataManager.GetSysString(1153));
	btnRepos = env->addButton(rect<s32>(1, 106, 99, 126), wCmdMenu, BUTTON_CMD_REPOS, dataManager.GetSysString(1154));
	btnAttack = env->addButton(rect<s32>(1, 127, 99, 147), wCmdMenu, BUTTON_CMD_ATTACK, dataManager.GetSysString(1157));
	btnShowList = env->addButton(rect<s32>(1, 148, 99, 168), wCmdMenu, BUTTON_CMD_SHOWLIST, dataManager.GetSysString(1158));
	btnOperation = env->addButton(rect<s32>(1, 169, 99, 189), wCmdMenu, BUTTON_CMD_ACTIVATE, dataManager.GetSysString(1161));
	btnReset = env->addButton(rect<s32>(1, 190, 99, 210), wCmdMenu, BUTTON_CMD_RESET, dataManager.GetSysString(1162));
#endif
	//deck edit
	wDeckEdit = env->addStaticText(L"", rect<s32>(309 * xScale, 8 * yScale, 605 * xScale, 130 * yScale), true, false, 0, -1, true);
	wDeckEdit->setVisible(false);
	env->addStaticText(dataManager.GetSysString(1300), rect<s32>(10 * xScale, 9 * yScale, 100 * xScale, 29 * yScale), false, false, wDeckEdit);
#ifdef _IRR_ANDROID_PLATFORM_
	cbDBLFList = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(80 * xScale, 5 * yScale, 220 * xScale, 30 * yScale), wDeckEdit, COMBOBOX_DBLFLIST);
#else
	cbDBLFList = env->addComboBox(rect<s32>(80 * xScale, 5 * yScale, 220 * xScale, 30 * yScale), wDeckEdit, COMBOBOX_DBLFLIST);
#endif
	env->addStaticText(dataManager.GetSysString(1301), rect<s32>(10 * xScale, 39 * yScale, 100 * xScale, 59 * yScale), false, false, wDeckEdit);
#ifdef _IRR_ANDROID_PLATFORM_
	cbDBDecks = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(80 * xScale, 35 * yScale, 220 * xScale, 60 * yScale), wDeckEdit, COMBOBOX_DBDECKS);
#else
	cbDBDecks = env->addComboBox(rect<s32>(80 * xScale, 35 * yScale, 220 * xScale, 60 * yScale), wDeckEdit, COMBOBOX_DBDECKS);
#endif
	for(unsigned int i = 0; i < deckManager._lfList.size(); ++i)
		cbDBLFList->addItem(deckManager._lfList[i].listName);
	btnSaveDeck = env->addButton(rect<s32>(225 * xScale, 35 * yScale, 290 * xScale, 60 * yScale), wDeckEdit, BUTTON_SAVE_DECK, dataManager.GetSysString(1302));
#ifdef _IRR_ANDROID_PLATFORM_
	ebDeckname = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(80 * xScale, 65 * yScale, 220 * xScale, 90 * yScale), wDeckEdit, -1);
#else
	ebDeckname = env->addEditBox(L"", rect<s32>(80 * xScale, 65 * yScale, 220 * xScale, 90 * yScale), true, wDeckEdit, -1);
#endif
	ebDeckname->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnSaveDeckAs = env->addButton(rect<s32>(225 * xScale, 65 * yScale, 290 * xScale, 90 * yScale), wDeckEdit, BUTTON_SAVE_DECK_AS, dataManager.GetSysString(1303));
	btnDeleteDeck = env->addButton(rect<s32>(10 * xScale, 95 * yScale, 75 * xScale, 116 * yScale), wDeckEdit, BUTTON_DELETE_DECK, dataManager.GetSysString(1308));
	btnShuffleDeck = env->addButton(rect<s32>(130 * xScale, 95 * yScale, 180 * xScale, 116 * yScale), wDeckEdit, BUTTON_SHUFFLE_DECK, dataManager.GetSysString(1307));
	btnSortDeck = env->addButton(rect<s32>(185 * xScale, 95 * yScale, 235 * xScale, 116 * yScale), wDeckEdit, BUTTON_SORT_DECK, dataManager.GetSysString(1305));
	btnClearDeck = env->addButton(rect<s32>(240 * xScale, 95 * yScale, 290 * xScale, 116 * yScale), wDeckEdit, BUTTON_CLEAR_DECK, dataManager.GetSysString(1304));
	btnSideOK = env->addButton(rect<s32>(510 * xScale, 40 * yScale, 820 * xScale, 80 * yScale), 0, BUTTON_SIDE_OK, dataManager.GetSysString(1334));
	btnSideOK->setVisible(false);
	btnSideShuffle = env->addButton(rect<s32>(310 * xScale, 100 * yScale, 370 * xScale, 130 * yScale), 0, BUTTON_SHUFFLE_DECK, dataManager.GetSysString(1307));
	btnSideShuffle->setVisible(false);
	btnSideSort = env->addButton(rect<s32>(375 * xScale, 100 * yScale, 435 * xScale, 130 * yScale), 0, BUTTON_SORT_DECK, dataManager.GetSysString(1305));
	btnSideSort->setVisible(false);
	btnSideReload = env->addButton(rect<s32>(440 * xScale, 100 * yScale, 500 * xScale, 130 * yScale), 0, BUTTON_SIDE_RELOAD, dataManager.GetSysString(1309));
	btnSideReload->setVisible(false);
	//
	scrFilter = env->addScrollBar(false, recti(999 * xScale, 161 * yScale, 1019 * xScale, 629 * yScale), 0, SCROLL_FILTER);
	scrFilter->setLargeStep(10);
	scrFilter->setSmallStep(1);
	scrFilter->setVisible(false);
	//sort type
	wSort = env->addStaticText(L"", rect<s32>(930 * xScale, 132 * yScale, 1020 * xScale, 156 * yScale), true, false, 0, -1, true);
	cbSortType = env->addComboBox(rect<s32>(10 * xScale, 2 * yScale, 85 * xScale, 22 * yScale), wSort, COMBOBOX_SORTTYPE);
	cbSortType->setMaxSelectionRows(10);
	for(int i = 1370; i <= 1373; i++)
		cbSortType->addItem(dataManager.GetSysString(i));
	wSort->setVisible(false);
	//filters
	wFilter = env->addStaticText(L"", rect<s32>(610 * xScale, 8 * yScale, 1020 * xScale, 130 * yScale), true, false, 0, -1, true);
	wFilter->setVisible(false);
	env->addStaticText(dataManager.GetSysString(1311), rect<s32>(10 * xScale, 5 * yScale, 70 * xScale, 25 * yScale), false, false, wFilter);
#ifdef _IRR_ANDROID_PLATFORM_
	cbCardType = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(60 * xScale, 3 * yScale, 120 * xScale, 23 * yScale), wFilter, COMBOBOX_MAINTYPE);
#else
	cbCardType = env->addComboBox(rect<s32>(60 * xScale, 3 * yScale, 120 * xScale, 23 * yScale), wFilter, COMBOBOX_MAINTYPE);
#endif
	cbCardType->addItem(dataManager.GetSysString(1310));
	cbCardType->addItem(dataManager.GetSysString(1312));
	cbCardType->addItem(dataManager.GetSysString(1313));
	cbCardType->addItem(dataManager.GetSysString(1314));
#ifdef _IRR_ANDROID_PLATFORM_
	cbCardType2 = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(125 * xScale, 3 * yScale, 200 * xScale, 23 * yScale), wFilter, COMBOBOX_SECONDTYPE);
#else
	cbCardType2 = env->addComboBox(rect<s32>(125 * xScale, 3 * yScale, 200 * xScale, 23 * yScale), wFilter, COMBOBOX_SECONDTYPE);
#endif
	cbCardType2->addItem(dataManager.GetSysString(1310), 0);
	env->addStaticText(dataManager.GetSysString(1315), rect<s32>(205 * xScale, 5 * yScale, 280 * xScale, 25 * yScale), false, false, wFilter);
#ifdef _IRR_ANDROID_PLATFORM_
	cbLimit = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(260 * xScale, 3 * yScale, 390 * xScale, 23 * yScale), wFilter, COMBOBOX_LIMIT);
#else
	cbLimit = env->addComboBox(rect<s32>(260 * xScale, 3 * yScale, 390 * xScale, 23 * yScale), wFilter, COMBOBOX_LIMIT);
#endif
	cbLimit->addItem(dataManager.GetSysString(1310));
	cbLimit->addItem(dataManager.GetSysString(1316));
	cbLimit->addItem(dataManager.GetSysString(1317));
	cbLimit->addItem(dataManager.GetSysString(1318));
	cbLimit->addItem(dataManager.GetSysString(1240));
	cbLimit->addItem(dataManager.GetSysString(1241));
	cbLimit->addItem(dataManager.GetSysString(1242));
	cbLimit->addItem(dataManager.GetSysString(1243));
	env->addStaticText(dataManager.GetSysString(1319), rect<s32>(10 * xScale, 28 * yScale, 70 * xScale, 48 * yScale), false, false, wFilter);
#ifdef _IRR_ANDROID_PLATFORM_
	cbAttribute = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(60 * xScale, 26 * yScale, 190 * xScale, 46 * yScale), wFilter, COMBOBOX_ATTRIBUTE);
#else
	cbAttribute = env->addComboBox(rect<s32>(60 * xScale, 26 * yScale, 190 * xScale, 46 * yScale), wFilter, COMBOBOX_ATTRIBUTE);
#endif
	cbAttribute->addItem(dataManager.GetSysString(1310), 0);
	for(int filter = 0x1; filter != 0x80; filter <<= 1)
		cbAttribute->addItem(dataManager.FormatAttribute(filter), filter);
	env->addStaticText(dataManager.GetSysString(1321), rect<s32>(10 * xScale, 51 * yScale, 70 * xScale, 71 * yScale), false, false, wFilter);
#ifdef _IRR_ANDROID_PLATFORM_
	cbRace = CAndroidGUIComboBox::addAndroidComboBox(env, rect<s32>(60 * xScale, (40 + 75 / 6) * yScale, 190 * xScale, (60 + 75 / 6) * yScale), wFilter, COMBOBOX_RACE);
#else
	cbRace = env->addComboBox(rect<s32>(60, 40 + 75 / 6, 190, 60 + 75 / 6), wFilter, COMBOBOX_RACE);
#endif
	cbRace->addItem(dataManager.GetSysString(1310), 0);
	for(int filter = 0x1; filter != 0x2000000; filter <<= 1)
		cbRace->addItem(dataManager.FormatRace(filter), filter);
	env->addStaticText(dataManager.GetSysString(1322), rect<s32>(205 * xScale, 28 * yScale, 280 * xScale, 48 * yScale), false, false, wFilter);
#ifdef _IRR_ANDROID_PLATFORM_
	ebAttack = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(260 * xScale, 26 * yScale, 340 * xScale, 46 * yScale), wFilter);
#else
	ebAttack = env->addEditBox(L"", rect<s32>(260 * xScale, 26 * yScale, 340 * xScale, 46 * yScale), true, wFilter);
#endif
	ebAttack->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1323), rect<s32>(205 * xScale, 51 * yScale, 280 * xScale, 71 * yScale), false, false, wFilter);
#ifdef _IRR_ANDROID_PLATFORM_
	ebDefense = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(260 * xScale, 49 * yScale, 340 * xScale, 69 * yScale), wFilter);
#else
	ebDefense = env->addEditBox(L"", rect<s32>(260 * xScale, 49 * yScale, 340 * xScale, 69 * yScale), true, wFilter);
#endif
	ebDefense->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1324), rect<s32>(10 * xScale, 74 * yScale, 80 * xScale, 94 * yScale), false, false, wFilter);
#ifdef _IRR_ANDROID_PLATFORM_
	ebStar = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(60 * xScale, (60 + 100 / 6) * yScale, 100 * xScale, (80 + 100 / 6) * yScale), wFilter);
#else
	ebStar = env->addEditBox(L"", rect<s32>(60 * xScale, (60 + 100 / 6) * yScale, 100 * xScale, (80 + 100 / 6) * yScale), true, wFilter);
#endif
	ebStar->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1336), rect<s32>(101 * xScale, (62 + 100 / 6) * yScale, 150 * xScale, (82 + 100 / 6) * yScale), false, false, wFilter);
#ifdef _IRR_ANDROID_PLATFORM_
	ebScale = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(150 * xScale, (60 + 100 / 6)  * yScale, 190 * xScale, (80 + 100 / 6) * yScale), wFilter);
#else
	ebScale = env->addEditBox(L"", rect<s32>(150, 60 + 100 / 6, 190, 80 + 100 / 6), true, wFilter);
#endif
	ebScale->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1325), rect<s32>(205 * xScale, (62 + 100 / 6) * yScale, 280 * xScale, (82 + 100 / 6) * yScale), false, false, wFilter);
#ifdef _IRR_ANDROID_PLATFORM_
	ebCardName = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(260 * xScale, 72 * yScale, 390 * xScale, 92 * yScale), wFilter, EDITBOX_KEYWORD);
#else
	ebCardName = env->addEditBox(L"", rect<s32>(260 * xScale, 72 * yScale, 390 * xScale, 92 * yScale), true, wFilter, EDITBOX_KEYWORD);
#endif
	ebCardName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnEffectFilter = env->addButton(rect<s32>(345 * xScale, 28 * yScale, 390 * xScale, 69 * yScale), wFilter, BUTTON_EFFECT_FILTER, dataManager.GetSysString(1326));
	btnStartFilter = env->addButton(rect<s32>(210 * xScale, 96 * yScale, 390 * xScale, 118 * yScale), wFilter, BUTTON_START_FILTER, dataManager.GetSysString(1327));
	if(gameConf.separate_clear_button) {
		btnStartFilter->setRelativePosition(rect<s32>(260 * xScale, (80 + 125 / 6) * yScale, 390 * xScale, (100 + 125 / 6) * yScale));
		btnClearFilter = env->addButton(rect<s32>(205 * xScale, (80 + 125 / 6) * yScale, 255 * xScale, (100 + 125 / 6) * yScale), wFilter, BUTTON_CLEAR_FILTER, dataManager.GetSysString(1304));
	}
	wCategories = env->addWindow(rect<s32>(630 * xScale, 60 * yScale, 1000 * xScale, 270 * yScale), false, dataManager.strBuffer);
	wCategories->getCloseButton()->setVisible(false);
	wCategories->setDrawTitlebar(false);
	wCategories->setDraggable(false);
	wCategories->setVisible(false);
	btnCategoryOK = env->addButton(rect<s32>(135 * xScale, 175 * yScale, 235 * xScale, 200 * yScale), wCategories, BUTTON_CATEGORY_OK, dataManager.GetSysString(1211));
	for(int i = 0; i < 32; ++i)
		chkCategory[i] = env->addCheckBox(false, recti((10 + (i % 4) * 90)  * xScale, (10 + (i / 4) * 20) * yScale, (100 + (i % 4) * 90) * xScale, (40 + (i / 4) * 20) * yScale), wCategories, -1, dataManager.GetSysString(1100 + i));
#ifdef _IRR_ANDROID_PLATFORM_
	scrFilter = env->addScrollBar(false, recti(810 * xScale, 161 * yScale, 850 * xScale, 629 * yScale), 0, SCROLL_FILTER);
#else
	scrFilter = env->addScrollBar(false, recti(999 * xScale, 161 * yScale, 1019 * xScale, 629 * yScale), 0, SCROLL_FILTER);
#endif
	scrFilter->setLargeStep(10);
	scrFilter->setSmallStep(1);
	scrFilter->setVisible(false);

#ifdef _IRR_ANDROID_PLATFORM_
     //LINK MARKER SEARCH
	 btnMarksFilter = env->addButton(rect<s32>(60 * xScale, (80 + 125 / 6) * yScale, 190 * xScale, (100 + 125 / 6) * yScale), wFilter, BUTTON_MARKS_FILTER, dataManager.GetSysString(1374));
 	wLinkMarks = env->addWindow(rect<s32>(700 * xScale, 30 * yScale, 820 * xScale, 150 * yScale), false, dataManager.strBuffer);
	wLinkMarks->getCloseButton()->setVisible(false);
	wLinkMarks->setDrawTitlebar(false);
	wLinkMarks->setDraggable(false);
	wLinkMarks->setVisible(false);
	btnMarksOK = env->addButton(recti(45 * xScale, 45 * yScale, 75 * xScale, 75 * yScale), wLinkMarks, BUTTON_MARKERS_OK, dataManager.GetSysString(1211));
	btnMark[0] = env->addButton(recti(10 * xScale, 10 * yScale, 40 * xScale, 40 * yScale), wLinkMarks, -1, L"\u2196");
	btnMark[1] = env->addButton(recti(45 * xScale, 10 * yScale, 75 * xScale, 40 * yScale), wLinkMarks, -1, L"\u2191");
	btnMark[2] = env->addButton(recti(80 * xScale, 10 * yScale, 110 * xScale, 40 * yScale), wLinkMarks, -1, L"\u2197");
	btnMark[3] = env->addButton(recti(10 * xScale, 45 * yScale, 40 * xScale, 75 * yScale), wLinkMarks, -1, L"\u2190");
	btnMark[4] = env->addButton(recti(80 * xScale, 45 * yScale, 110 * xScale, 75 * yScale), wLinkMarks, -1, L"\u2192");
	btnMark[5] = env->addButton(recti(10 * xScale, 80 * yScale, 40 * xScale, 110 * yScale), wLinkMarks, -1, L"\u2199");
	btnMark[6] = env->addButton(recti(45 * xScale, 80 * yScale, 75 * xScale, 110 * yScale), wLinkMarks, -1, L"\u2193");
	btnMark[7] = env->addButton(recti(80 * xScale, 80 * yScale, 110 * xScale, 110 * yScale), wLinkMarks, -1, L"\u2198");
	for(int i=0;i<8;i++)
		btnMark[i]->setIsPushButton(true);
	//replay window
	wReplay = env->addWindow(rect<s32>(220 * xScale, 100 * yScale, 800 * xScale, 520 * yScale), false, dataManager.GetSysString(1202));
	wReplay->getCloseButton()->setVisible(false);
	wReplay->setVisible(false);
	lstReplayList = CAndroidGUIListBox::addAndroidGUIListBox(env, rect<s32>(10 * xScale, 30 * yScale, 350 * xScale, 400 * yScale), wReplay, LISTBOX_REPLAY_LIST, true, 40 * xScale);
	lstReplayList->setItemHeight(18 * yScale);
	btnLoadReplay = env->addButton(rect<s32>(470 * xScale, 320 * yScale, 570 * xScale, 360 * yScale), wReplay, BUTTON_LOAD_REPLAY, dataManager.GetSysString(1348));
	btnDeleteReplay = env->addButton(rect<s32>(360 * xScale, 320 * yScale, 460 * xScale, 360 * yScale), wReplay, BUTTON_DELETE_REPLAY, dataManager.GetSysString(1361));
	btnRenameReplay = env->addButton(rect<s32>(360 * xScale, 370 * yScale, 460 * xScale, 410 * yScale), wReplay, BUTTON_RENAME_REPLAY, dataManager.GetSysString(1362));
	btnReplayCancel = env->addButton(rect<s32>(470 * xScale, 370 * yScale, 570 * xScale, 410 * yScale), wReplay, BUTTON_CANCEL_REPLAY, dataManager.GetSysString(1347));
	env->addStaticText(dataManager.GetSysString(1349), rect<s32>(360 * xScale, 30 * yScale, 570 * xScale, 50 * yScale), false, true, wReplay);
	stReplayInfo = env->addStaticText(L"", rect<s32>(360 * xScale, 60 * yScale, 570 * xScale, 315 * yScale), false, true, wReplay);
	env->addStaticText(dataManager.GetSysString(1353), rect<s32>(360 * xScale, 240 * yScale, 570 * xScale, 260 * yScale), false, true, wReplay);

	ebRepStartTurn = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(360 * xScale, 275 * yScale, 460 * xScale, 295 * yScale), wReplay, -1);
	ebRepStartTurn->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	//single play window
	wSinglePlay = env->addWindow(rect<s32>(220 * xScale, 100 * yScale, 800 * xScale, 520 * yScale), false, dataManager.GetSysString(1201));
	wSinglePlay->getCloseButton()->setVisible(false);
	wSinglePlay->setVisible(false);
	irr::gui::IGUITabControl* wSingle = env->addTabControl(rect<s32>(0 * xScale, 20 * yScale, 579 * xScale, 419 * yScale), wSinglePlay, true);
	wSingle->setTabHeight(35 * yScale);
	//TEST BOT MODE
	if(gameConf.enable_bot_mode) {
		irr::gui::IGUITab* tabBot = wSingle->addTab(dataManager.GetSysString(1380));
		lstBotList = CAndroidGUIListBox::addAndroidGUIListBox(env, rect<s32>(10 * xScale, 10 * yScale, 350 * xScale, 350 * yScale), tabBot, LISTBOX_BOT_LIST, true, 40 * xScale);
		lstBotList->setItemHeight(25 * yScale);
		btnStartBot = env->addButton(rect<s32>(460 * xScale, 270 * yScale, 570 * xScale, 310 * yScale), tabBot, BUTTON_BOT_START, dataManager.GetSysString(1211));
		btnBotCancel = env->addButton(rect<s32>(460 * xScale, 320 * yScale, 570 * xScale, 360 * yScale), tabBot, BUTTON_CANCEL_SINGLEPLAY, dataManager.GetSysString(1210));
		env->addStaticText(dataManager.GetSysString(1382), rect<s32>(360 * xScale, 10 * yScale, 550 * xScale, 30 * yScale), false, true, tabBot);
		stBotInfo = env->addStaticText(L"", rect<s32>(360 * xScale, 40 * yScale, 560 * xScale, 160 * yScale), false, true, tabBot);
		chkBotOldRule = env->addCheckBox(false, rect<s32>(360 * xScale, 140 * yScale, 560 * xScale, 160 * yScale), tabBot, CHECKBOX_BOT_OLD_RULE, dataManager.GetSysString(1383));
		chkBotHand = env->addCheckBox(false, rect<s32>(360 * xScale, 170 * yScale, 560 * xScale, 190 * yScale), tabBot, -1, dataManager.GetSysString(1384));
		chkBotNoCheckDeck = env->addCheckBox(false, rect<s32>(360 * xScale, 200 * yScale, 560 * xScale, 220 * yScale), tabBot, -1, dataManager.GetSysString(1229));
		chkBotNoShuffleDeck = env->addCheckBox(false, rect<s32>(360 * xScale, 230 * yScale, 560 * xScale, 250 * yScale), tabBot, -1, dataManager.GetSysString(1230));
	} else { // avoid null pointer
		btnStartBot = env->addButton(rect<s32>(0, 0, 0, 0), wSinglePlay);
		btnBotCancel = env->addButton(rect<s32>(0, 0, 0, 0), wSinglePlay);
		btnStartBot->setVisible(false);
		btnBotCancel->setVisible(false);
	}
	
	irr::gui::IGUITab* tabSingle = wSingle->addTab(dataManager.GetSysString(1381));
	lstSinglePlayList = CAndroidGUIListBox::addAndroidGUIListBox(env, rect<s32>(10 * xScale, 10 * yScale, 350 * xScale, 350 * yScale), tabSingle, LISTBOX_SINGLEPLAY_LIST, true, 40 * xScale);
	lstSinglePlayList->setItemHeight(25 * yScale);
	btnLoadSinglePlay = env->addButton(rect<s32>(460 * xScale, 270 * yScale, 570 * xScale, 310 * yScale), tabSingle, BUTTON_LOAD_SINGLEPLAY, dataManager.GetSysString(1211));
	btnSinglePlayCancel = env->addButton(rect<s32>(460 * xScale, 320 * yScale, 570 * xScale, 360 * yScale),tabSingle, BUTTON_CANCEL_SINGLEPLAY, dataManager.GetSysString(1210));
	env->addStaticText(dataManager.GetSysString(1352), rect<s32>(360 * xScale, 30 * yScale, 570 * xScale, 50 * yScale), false, true, tabSingle);
	stSinglePlayInfo = env->addStaticText(L"", rect<s32>(360 * xScale, 60 * yScale, 570 * xScale, 295 * yScale), false, true, tabSingle);
	//replay save
	wReplaySave = env->addWindow(rect<s32>(490 * xScale, 180 * yScale, 840 * xScale, 340 * yScale), false, dataManager.GetSysString(1340));
	wReplaySave->getCloseButton()->setVisible(false);
	wReplaySave->setVisible(false);
	env->addStaticText(dataManager.GetSysString(1342), rect<s32>(20 * xScale, 25 * yScale, 290 * xScale, 45 * yScale), false, false, wReplaySave);
	ebRSName = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(20 * xScale, 50 * yScale, 330 * xScale, 90 * yScale), wReplaySave, -1);
	ebRSName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnRSYes = env->addButton(rect<s32>(70 * xScale, 100 * yScale, 160 * xScale, 150 * yScale), wReplaySave, BUTTON_REPLAY_SAVE, dataManager.GetSysString(1341));
	btnRSNo = env->addButton(rect<s32>(180 * xScale, 100 * yScale, 270 * xScale, 150 * yScale), wReplaySave, BUTTON_REPLAY_CANCEL, dataManager.GetSysString(1212));
	//replay control
	wReplayControl = env->addStaticText(L"", rect<s32>(205 * xScale, 48 * yScale, 295 * xScale, 273 * yScale), true, false, 0, -1, true);
	wReplayControl->setVisible(false);
	btnReplayStart = env->addButton(rect<s32>(5 * xScale, 5 * yScale, 85 * xScale, 45 * yScale), wReplayControl, BUTTON_REPLAY_START, dataManager.GetSysString(1343));
	btnReplayPause = env->addButton(rect<s32>(5 * xScale, 50 * yScale, 85 * xScale, 90 * yScale), wReplayControl, BUTTON_REPLAY_PAUSE, dataManager.GetSysString(1344));
	btnReplayStep = env->addButton(rect<s32>(5 * xScale, 95 * yScale, 85 * xScale, 135 * yScale), wReplayControl, BUTTON_REPLAY_STEP, dataManager.GetSysString(1345));
	btnReplayUndo = env->addButton(rect<s32>(5 * xScale, 50 * yScale, 85 * xScale, 90 * yScale), wReplayControl, BUTTON_REPLAY_UNDO, dataManager.GetSysString(1360));
	btnReplaySwap = env->addButton(rect<s32>(5 * xScale, 140 * yScale, 85 * xScale, 180 * yScale), wReplayControl, BUTTON_REPLAY_SWAP, dataManager.GetSysString(1346));
	btnReplayExit = env->addButton(rect<s32>(5 * xScale, 185 * yScale, 85 * xScale, 225 * yScale), wReplayControl, BUTTON_REPLAY_EXIT, dataManager.GetSysString(1347));
	//chat
	wChat = env->addWindow(rect<s32>(305 * xScale, 610 * yScale, 1020 * xScale, 640 * yScale), false, L"");
	wChat->getCloseButton()->setVisible(false);
	wChat->setDraggable(false);
	wChat->setDrawTitlebar(false);
	wChat->setVisible(false);
	//swap
	btnSpectatorSwap = env->addButton(rect<s32>(205 * xScale, 100 * yScale, 305 * xScale, 135 * yScale), 0, BUTTON_REPLAY_SWAP, dataManager.GetSysString(1346));
	btnSpectatorSwap->setVisible(false);
	//chain buttons
		btnChainIgnore = env->addButton(rect<s32>(205 * xScale, 100 * yScale, 305 * xScale, 135 * yScale), 0, BUTTON_CHAIN_IGNORE, dataManager.GetSysString(1292));
		btnChainAlways = env->addButton(rect<s32>(205 * xScale, 140 * yScale, 305 * xScale, 175 * yScale), 0, BUTTON_CHAIN_ALWAYS, dataManager.GetSysString(1293));
		btnChainWhenAvail = env->addButton(rect<s32>(205 * xScale, 180 * yScale, 305 * xScale, 215 * yScale), 0, BUTTON_CHAIN_WHENAVAIL, dataManager.GetSysString(1294));
		btnChainIgnore->setIsPushButton(true);
		btnChainAlways->setIsPushButton(true);
		btnChainWhenAvail->setIsPushButton(true);
		btnChainIgnore->setVisible(false);
		btnChainAlways->setVisible(false);
		btnChainWhenAvail->setVisible(false);
	//shuffle
	btnShuffle = env->addButton(rect<s32>(205 * xScale, 220 * yScale, 305 * xScale, 255 * yScale), 0, BUTTON_CMD_SHUFFLE, dataManager.GetSysString(1297));
	btnShuffle->setVisible(false);
	//cancel or finish
	btnCancelOrFinish = env->addButton(rect<s32>(205 * xScale, 220 * yScale, 305 * xScale, 275 * yScale), 0, BUTTON_CANCEL_OR_FINISH, dataManager.GetSysString(1295));
	btnCancelOrFinish->setVisible(false);
#else
	//replay window
	wReplay = env->addWindow(rect<s32>(220, 100, 800, 520), false, dataManager.GetSysString(1202));
	wReplay->getCloseButton()->setVisible(false);
	wReplay->setVisible(false);
	lstReplayList = env->addListBox(rect<s32>(10, 30, 350, 400), wReplay, LISTBOX_REPLAY_LIST, true);
	lstReplayList->setItemHeight(18);
	btnLoadReplay = env->addButton(rect<s32>(470, 355, 570, 380), wReplay, BUTTON_LOAD_REPLAY, dataManager.GetSysString(1348));
	btnDeleteReplay = env->addButton(rect<s32>(360, 355, 460, 380), wReplay, BUTTON_DELETE_REPLAY, dataManager.GetSysString(1361));
	btnRenameReplay = env->addButton(rect<s32>(360, 385, 460, 410), wReplay, BUTTON_RENAME_REPLAY, dataManager.GetSysString(1362));
	btnReplayCancel = env->addButton(rect<s32>(470, 385, 570, 410), wReplay, BUTTON_CANCEL_REPLAY, dataManager.GetSysString(1347));
	env->addStaticText(dataManager.GetSysString(1349), rect<s32>(360, 30, 570, 50), false, true, wReplay);
	stReplayInfo = env->addStaticText(L"", rect<s32>(360, 60, 570, 350), false, true, wReplay);
	env->addStaticText(dataManager.GetSysString(1353), rect<s32>(360, 275, 570, 295), false, true, wReplay);
	ebRepStartTurn = env->addEditBox(L"", rect<s32>(360, 300, 460, 320), true, wReplay, -1);
	ebRepStartTurn->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	//single play window
	wSinglePlay = env->addWindow(rect<s32>(220, 100, 800, 520), false, dataManager.GetSysString(1201));
	wSinglePlay->getCloseButton()->setVisible(false);
	wSinglePlay->setVisible(false);
	irr::gui::IGUITabControl* wSingle = env->addTabControl(rect<s32>(0, 20, 579, 419), wSinglePlay, true);
    //TEST BOT MODE
	if(gameConf.enable_bot_mode) {
		irr::gui::IGUITab* tabBot = wSingle->addTab(dataManager.GetSysString(1380));
		lstBotList = env->addListBox(rect<s32>(10, 10, 350, 350), tabBot, LISTBOX_BOT_LIST, true);
		lstBotList->setItemHeight(18);
		btnStartBot = env->addButton(rect<s32>(459, 301, 569, 326), tabBot, BUTTON_BOT_START, dataManager.GetSysString(1211));
		btnBotCancel = env->addButton(rect<s32>(459, 331, 569, 356), tabBot, BUTTON_CANCEL_SINGLEPLAY, dataManager.GetSysString(1210));
		env->addStaticText(dataManager.GetSysString(1382), rect<s32>(360, 10, 550, 30), false, true, tabBot);
		stBotInfo = env->addStaticText(L"", rect<s32>(360, 40, 560, 160), false, true, tabBot);
		chkBotOldRule = env->addCheckBox(false, rect<s32>(360, 170, 560, 190), tabBot, CHECKBOX_BOT_OLD_RULE, dataManager.GetSysString(1383));
		chkBotHand = env->addCheckBox(false, rect<s32>(360, 200, 560, 220), tabBot, -1, dataManager.GetSysString(1384));
		chkBotNoCheckDeck = env->addCheckBox(false, rect<s32>(360, 230, 560, 250), tabBot, -1, dataManager.GetSysString(1229));
		chkBotNoShuffleDeck = env->addCheckBox(false, rect<s32>(360, 260, 560, 280), tabBot, -1, dataManager.GetSysString(1230));
	} else { // avoid null pointer
		btnStartBot = env->addButton(rect<s32>(0, 0, 0, 0), wSinglePlay);
		btnBotCancel = env->addButton(rect<s32>(0, 0, 0, 0), wSinglePlay);
		btnStartBot->setVisible(false);
		btnBotCancel->setVisible(false);
	}
	
	irr::gui::IGUITab* tabSingle = wSingle->addTab(dataManager.GetSysString(1381));
	lstSinglePlayList = env->addListBox(rect<s32>(10, 10, 350, 350), tabSingle, LISTBOX_SINGLEPLAY_LIST, true);
	lstSinglePlayList->setItemHeight(18);
	btnLoadSinglePlay = env->addButton(rect<s32>(459, 301, 569, 326), tabSingle, BUTTON_LOAD_SINGLEPLAY, dataManager.GetSysString(1211));
	btnSinglePlayCancel = env->addButton(rect<s32>(459, 331, 569, 356), tabSingle, BUTTON_CANCEL_SINGLEPLAY, dataManager.GetSysString(1210));
	env->addStaticText(dataManager.GetSysString(1352), rect<s32>(360, 10, 550, 30), false, true, tabSingle);
	stSinglePlayInfo = env->addStaticText(L"", rect<s32>(360, 40, 550, 280), false, true, tabSingle);
	//replay save
	wReplaySave = env->addWindow(rect<s32>(510, 200, 820, 320), false, dataManager.GetSysString(1340));
	wReplaySave->getCloseButton()->setVisible(false);
	wReplaySave->setVisible(false);
	env->addStaticText(dataManager.GetSysString(1342), rect<s32>(20, 25, 290, 45), false, false, wReplaySave);
	ebRSName =  env->addEditBox(L"", rect<s32>(20, 50, 290, 70), true, wReplaySave, -1);
	ebRSName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnRSYes = env->addButton(rect<s32>(70, 80, 140, 105), wReplaySave, BUTTON_REPLAY_SAVE, dataManager.GetSysString(1341));
	btnRSNo = env->addButton(rect<s32>(170, 80, 240, 105), wReplaySave, BUTTON_REPLAY_CANCEL, dataManager.GetSysString(1212));
	//replay control
	wReplayControl = env->addStaticText(L"", rect<s32>(205, 118, 295, 273), true, false, 0, -1, true);
	wReplayControl->setVisible(false);
	btnReplayStart = env->addButton(rect<s32>(5, 5, 85, 25), wReplayControl, BUTTON_REPLAY_START, dataManager.GetSysString(1343));
	btnReplayPause = env->addButton(rect<s32>(5, 30, 85, 50), wReplayControl, BUTTON_REPLAY_PAUSE, dataManager.GetSysString(1344));
	btnReplayStep = env->addButton(rect<s32>(5, 55, 85, 75), wReplayControl, BUTTON_REPLAY_STEP, dataManager.GetSysString(1345));
	btnReplayUndo = env->addButton(rect<s32>(5, 80, 85, 100), wReplayControl, BUTTON_REPLAY_UNDO, dataManager.GetSysString(1360));
	btnReplaySwap = env->addButton(rect<s32>(5, 105, 85, 125), wReplayControl, BUTTON_REPLAY_SWAP, dataManager.GetSysString(1346));
	btnReplayExit = env->addButton(rect<s32>(5, 130, 85, 150), wReplayControl, BUTTON_REPLAY_EXIT, dataManager.GetSysString(1347));
	//chat
	wChat = env->addWindow(rect<s32>(305, 615, 1020, 640), false, L"");
	wChat->getCloseButton()->setVisible(false);
	wChat->setDraggable(false);
	wChat->setDrawTitlebar(false);
	wChat->setVisible(false);
	ebChatInput = env->addEditBox(L"", rect<s32>(3, 2, 710, 22), true, wChat, EDITBOX_CHAT);
	//swap
	btnSpectatorSwap = env->addButton(rect<s32>(205, 100, 295, 135), 0, BUTTON_REPLAY_SWAP, dataManager.GetSysString(1346));
	btnSpectatorSwap->setVisible(false);
	//chain buttons
	btnChainIgnore = env->addButton(rect<s32>(205, 100, 295, 135), 0, BUTTON_CHAIN_IGNORE, dataManager.GetSysString(1292));
	btnChainAlways = env->addButton(rect<s32>(205, 140, 295, 175), 0, BUTTON_CHAIN_ALWAYS, dataManager.GetSysString(1293));
	btnChainWhenAvail = env->addButton(rect<s32>(205, 180, 295, 215), 0, BUTTON_CHAIN_WHENAVAIL, dataManager.GetSysString(1294));
	btnChainIgnore->setIsPushButton(true);
	btnChainAlways->setIsPushButton(true);
	btnChainWhenAvail->setIsPushButton(true);
	btnChainIgnore->setVisible(false);
	btnChainAlways->setVisible(false);
	btnChainWhenAvail->setVisible(false);
	//shuffle
	btnShuffle = env->addButton(rect<s32>(205, 230, 295, 265), 0, BUTTON_CMD_SHUFFLE, dataManager.GetSysString(1297));
	btnShuffle->setVisible(false);
	//cancel or finish
	btnCancelOrFinish = env->addButton(rect<s32>(205, 230, 295, 265), 0, BUTTON_CANCEL_OR_FINISH, dataManager.GetSysString(1295));
	btnCancelOrFinish->setVisible(false);
	//leave/surrender/exit
#endif
#ifdef _IRR_ANDROID_PLATFORM_
	ebChatInput = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, rect<s32>(3 * xScale, 2 * yScale, 710 * xScale, 22 * yScale), wChat, EDITBOX_CHAT);
#else
	ebChatInput = env->addEditBox(L"", rect<s32>(3, 2, 710, 22), true, wChat, EDITBOX_CHAT);
#endif
	//
	btnLeaveGame = env->addButton(rect<s32>(205 * xScale, 1 * yScale, 305 * xScale, 80 * yScale), 0, BUTTON_LEAVE_GAME, L"");
	btnLeaveGame->setVisible(false);
	//tip
	stTip = env->addStaticText(L"", rect<s32>(0 * xScale, 0 * yScale, 150 * xScale, 150 * yScale), false, true, 0, -1, true);
	stTip->setBackgroundColor(0xc011113d);
	stTip->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	stTip->setVisible(false);
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
		rect<s32>(15,15,100,60), false, false, 0, GUI_INFO_FPS );
#endif
	hideChat = false;
	hideChatTimer = 0;
	delete options;
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
			Printer::log("WARNING: Pixel shaders disabled "
					"because of missing driver/hardware support.");
			psFileName = "";
		}
		if (!driver->queryFeature(video::EVDF_VERTEX_SHADER_1_1) &&
				!driver->queryFeature(video::EVDF_ARB_VERTEX_PROGRAM_1))
		{
			Printer::log("WARNING: Vertex shaders disabled "
					"because of missing driver/hardware support.");
			solidvsFileName = "";
			TACvsFileName = "";
			blendvsFileName = "";
		}
		video::IGPUProgrammingServices* gpu = driver->getGPUProgrammingServices();
		if (gpu) {
			char log_custom_shader[1024];
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
			sprintf(log_custom_shader, "ogles2Sold = %d", ogles2Solid);
			Printer::log(log_custom_shader);
			sprintf(log_custom_shader, "ogles2BlendTexture = %d", ogles2BlendTexture);
			Printer::log(log_custom_shader);
			sprintf(log_custom_shader, "ogles2TrasparentAlpha = %d", ogles2TrasparentAlpha);
			Printer::log(log_custom_shader);
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
			driver->draw2DImage(imageManager.tBackGround, recti(0 * xScale, 0 * yScale, 1280 * xScale, 720 * yScale), recti(0, 0, imageManager.tBackGround->getOriginalSize().Width, imageManager.tBackGround->getOriginalSize().Height));
		}
		if(imageManager.tBackGround_menu) {
			driver->draw2DImage(imageManager.tBackGround_menu, recti(0 * xScale, 0 * yScale, 1280 * xScale, 720 * yScale), recti(0, 0, imageManager.tBackGround->getOriginalSize().Width, imageManager.tBackGround->getOriginalSize().Height));
		}
		if(imageManager.tBackGround_deck) {
			driver->draw2DImage(imageManager.tBackGround_deck, recti(0 * xScale, 0 * yScale, 1280 * xScale, 720 * yScale), recti(0, 0, imageManager.tBackGround->getOriginalSize().Width, imageManager.tBackGround->getOriginalSize().Height));
		}
		driver->enableMaterial2D(false);
#else
		if(imageManager.tBackGround)
			driver->draw2DImage(imageManager.tBackGround, recti(0 * xScale, 0 * yScale, 1280 * xScale, 720 * yScale), recti(0, 0, imageManager.tBackGround->getOriginalSize().Width, imageManager.tBackGround->getOriginalSize().Height));
#endif
		gMutex.Lock();
		if(dInfo.isStarted || dInfo.isReplaySkiping) {
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
#else
		} else {
			DrawBackImage(imageManager.tBackGround_menu);
		}
		DrawGUI();
		DrawSpec();
#endif
		gMutex.Unlock();
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
		if(closeSignal.Wait(0))
			CloseDuelWindow();
		if(!device->isWindowActive())
			ignore_chain = false;
		fps++;
		cur_time = timer->getTime();
		if(cur_time >= 1000) {

#ifdef _IRR_ANDROID_PLATFORM_
			if ( stat ) {
				stringw str = L"FPS: ";
				str += (s32)device->getVideoDriver()->getFPS();
				stat->setText ( str.c_str() );
			}
#else
	       if(cur_time < fps * 17 - 20)
#ifdef _WIN32
				Sleep(20);
#else
				usleep(20000);
#endif
			myswprintf(cap, L"FPS: %d", fps);
			device->setWindowCaption(cap);
#endif
			fps = 0;
			cur_time -= 1000;
			timer->setTime(0);
			if(dInfo.time_player == 0 || dInfo.time_player == 1)
				if(dInfo.time_left[dInfo.time_player])
					dInfo.time_left[dInfo.time_player]--;
		}
#ifdef _IRR_ANDROID_PLATFORM_
		device->yield(); // probably nicer to the battery
#endif
}
	DuelClient::StopClient(true);
	if(dInfo.isSingleMode)
		SingleMode::StopPlay(true);
#ifdef _WIN32
	Sleep(500);
#else
	usleep(500000);
#endif
	SaveConfig();
	delete soundEffectPlayer;
	usleep(500000);
//	device->drop();
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
	SetStaticText(pControl, cWidth, font, text);
	if(font->getDimension(dataManager.strBuffer).Height <= cHeight) {
		scrCardText->setVisible(false);
		if(env->hasFocus(scrCardText))
			env->removeFocus(scrCardText);
		return;
	}
	SetStaticText(pControl, cWidth - int(25 * xScale), font, text);
	u32 fontheight = font->getDimension(L"A").Height + font->getKerningHeight();
	u32 step = (font->getDimension(dataManager.strBuffer).Height - cHeight) / fontheight + 1;
	scrCardText->setVisible(true);
	scrCardText->setMin(0);
	scrCardText->setMax(step);
	scrCardText->setPos(0);
}
void Game::SetStaticText(irr::gui::IGUIStaticText* pControl, u32 cWidth, irr::gui::CGUITTFont* font, const wchar_t* text, u32 pos) {
	int pbuffer = 0;
	u32 _width = 0, _height = 0;
    //dont merge here
	for(size_t i = 0; text[i] != 0 && i < wcslen(text); ++i) {
		u32 w = font->getCharDimension(text[i]).Width;
		if(text[i] == L'\n') {
			dataManager.strBuffer[pbuffer++] = L'\n';
			_width = 0;
			_height++;
			if(_height == pos)
				pbuffer = 0;
			continue;
		} else if(_width > 0 && _width + w > cWidth) {
			dataManager.strBuffer[pbuffer++] = L'\n';
			_width = 0;
			_height++;
			if(_height == pos)
				pbuffer = 0;
		}
		_width += w;
		dataManager.strBuffer[pbuffer++] = text[i];
	}
	dataManager.strBuffer[pbuffer] = 0;
	pControl->setText(dataManager.strBuffer);
}
void Game::RefreshDeck(irr::gui::IGUIComboBox* cbDeck) {
	cbDeck->clear();
#ifdef _WIN32
	WIN32_FIND_DATAW fdataw;
	HANDLE fh = FindFirstFileW(L"./deck/*.ydk", &fdataw);
	if(fh == INVALID_HANDLE_VALUE)
		return;
	do {
		if(!(fdataw.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) {
			wchar_t* pf = fdataw.cFileName;
			while(*pf) pf++;
			while(*pf != L'.') pf--;
			*pf = 0;
			cbDeck->addItem(fdataw.cFileName);
		}
	} while(FindNextFileW(fh, &fdataw));
	FindClose(fh);
#else
	DIR * dir;
	struct dirent * dirp;
	if((dir = opendir("./deck/")) == NULL)
		return;
	while((dirp = readdir(dir)) != NULL) {
		size_t len = strlen(dirp->d_name);
		if(len < 5 || strcasecmp(dirp->d_name + len - 4, ".ydk") != 0)
			continue;
		dirp->d_name[len - 4] = 0;
		wchar_t wname[256];
		BufferIO::DecodeUTF8(dirp->d_name, wname);
		cbDeck->addItem(wname);
	}
	closedir(dir);
#endif
	for(size_t i = 0; i < cbDeck->getItemCount(); ++i) {
		if(!wcscmp(cbDeck->getItem(i), gameConf.lastdeck)) {
			cbDeck->setSelected(i);
			break;
		}
	}
}
void Game::RefreshReplay() {
	lstReplayList->clear();
#ifdef _WIN32
	WIN32_FIND_DATAW fdataw;
	HANDLE fh = FindFirstFileW(L"./replay/*.yrp", &fdataw);
	if(fh == INVALID_HANDLE_VALUE)
		return;
	do {
		if(!(fdataw.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) && Replay::CheckReplay(fdataw.cFileName)) {
			lstReplayList->addItem(fdataw.cFileName);
		}
	} while(FindNextFileW(fh, &fdataw));
	FindClose(fh);
#else
	DIR * dir;
	struct dirent * dirp;
	if((dir = opendir("./replay/")) == NULL)
		return;
	while((dirp = readdir(dir)) != NULL) {
		size_t len = strlen(dirp->d_name);
		if(len < 5 || strcasecmp(dirp->d_name + len - 4, ".yrp") != 0)
			continue;
		wchar_t wname[256];
		BufferIO::DecodeUTF8(dirp->d_name, wname);
		if(Replay::CheckReplay(wname))
			lstReplayList->addItem(wname);
	}
	closedir(dir);
#endif
}
void Game::RefreshSingleplay() {
	lstSinglePlayList->clear();
#ifdef _WIN32
	WIN32_FIND_DATAW fdataw;
	HANDLE fh = FindFirstFileW(L"./single/*.lua", &fdataw);
	if(fh == INVALID_HANDLE_VALUE)
		return;
	do {
		if(!(fdataw.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY))
			lstSinglePlayList->addItem(fdataw.cFileName);
	} while(FindNextFileW(fh, &fdataw));
	FindClose(fh);
#else
	DIR * dir;
	struct dirent * dirp;
	if((dir = opendir("./single/")) == NULL)
		return;
	while((dirp = readdir(dir)) != NULL) {
		size_t len = strlen(dirp->d_name);
		if(len < 5 || strcasecmp(dirp->d_name + len - 4, ".lua") != 0)
			continue;
		wchar_t wname[256];
		BufferIO::DecodeUTF8(dirp->d_name, wname);
		lstSinglePlayList->addItem(wname);
	}
	closedir(dir);
#endif
}
void Game::RefreshBot() {
	if(!gameConf.enable_bot_mode)
		return;
	botInfo.clear();
	FILE* fp = fopen("bot.conf", "r");
	char linebuf[256];
	char strbuf[256];
	if(fp) {
		while(fgets(linebuf, 256, fp)) {
			if(linebuf[0] == '#')
				continue;
			if(linebuf[0] == '!') {
				BotInfo newinfo;
				sscanf(linebuf, "!%240[^\n]", strbuf);
				BufferIO::DecodeUTF8(strbuf, newinfo.name);
				fgets(linebuf, 256, fp);
				sscanf(linebuf, "%240[^\n]", strbuf);
				BufferIO::DecodeUTF8(strbuf, newinfo.command);
				fgets(linebuf, 256, fp);
				sscanf(linebuf, "%240[^\n]", strbuf);
				BufferIO::DecodeUTF8(strbuf, newinfo.desc);
				fgets(linebuf, 256, fp);
				newinfo.support_master_rule_3 = !!strstr(linebuf, "SUPPORT_MASTER_RULE_3");
				newinfo.support_new_master_rule = !!strstr(linebuf, "SUPPORT_NEW_MASTER_RULE");
				if((chkBotOldRule->isChecked() && newinfo.support_master_rule_3)
					|| (!chkBotOldRule->isChecked() && newinfo.support_new_master_rule))
					botInfo.push_back(newinfo);
				continue;
			}
		}
		fclose(fp);
	}
	lstBotList->clear();
	stBotInfo->setText(L"");
	for(unsigned int i = 0; i < botInfo.size(); ++i) {
		lstBotList->addItem(botInfo[i].name);
	}
	if(botInfo.size() == 0)
		SetStaticText(stBotInfo, 200, guiFont, dataManager.GetSysString(1385));
}
void Game::LoadConfig() {
	wchar_t wstr[256];
	if(gameConf._init)return;
	PRO_VERSION = android::getIntSetting(appMain, "game_version", PRO_VERSION);
	gameConf._init = TRUE;
	gameConf.antialias = 1;
	gameConf.serverport = 7911;
	gameConf.textfontsize = 16;
	gameConf.nickname[0] = 0;
	gameConf.gamename[0] = 0;
	BufferIO::DecodeUTF8(android::getLastDeck(appMain).c_str(), wstr);
	BufferIO::CopyWStr(wstr, gameConf.lastdeck, 64);
	irr:os::Printer::log(android::getFontPath(appMain).c_str());
	BufferIO::DecodeUTF8(android::getFontPath(appMain).c_str(), wstr);
	BufferIO::CopyWStr(wstr, gameConf.numfont, 256);
	BufferIO::CopyWStr(wstr, gameConf.textfont, 256);
	gameConf.lasthost[0] = 0;
	gameConf.lastport[0] = 0;
	gameConf.roompass[0] = 0;
	//settings
	gameConf.chkMAutoPos = android::getIntSetting(appMain, "chkMAutoPos", 0);
	gameConf.chkSTAutoPos = android::getIntSetting(appMain, "chkSTAutoPos", 0);
	gameConf.chkRandomPos = android::getIntSetting(appMain, "chkRandomPos", 0);
	gameConf.chkAutoChain = android::getIntSetting(appMain, "chkAutoChain", 0);
	gameConf.chkWaitChain = android::getIntSetting(appMain, "chkWaitChain", 0);
	gameConf.chkIgnore1 = android::getIntSetting(appMain, "chkIgnore1", 0);
	gameConf.chkIgnore2 = android::getIntSetting(appMain, "chkIgnore2", 0);
	gameConf.chkHideSetname = android::getIntSetting(appMain, "chkHideSetname", 0);
	gameConf.control_mode = android::getIntSetting(appMain, "control_mode", 0);
	gameConf.draw_field_spell = android::getIntSetting(appMain, "draw_field_spell", 1);
	gameConf.separate_clear_button = android::getIntSetting(appMain, "separate_clear_button", 1);
	gameConf.auto_search_limit = android::getIntSetting(appMain, "auto_search_limit", 1);
	gameConf.chkIgnoreDeckChanges = android::getIntSetting(appMain, "chkIgnoreDeckChanges", 0);
	gameConf.defaultOT = android::getIntSetting(appMain, "defaultOT", 1);
	//TEST BOT MODE
	gameConf.enable_bot_mode = 0;
}

void Game::SaveConfig() {
	//char linebuf[256];	
	//BufferIO::EncodeUTF8(gameConf.lastdeck, linebuf);
	//android::setLastDeck(appMain, linebuf);
	int cur;
	cur = chkMAutoPos->isChecked()?1:0;
	if(cur != gameConf.chkMAutoPos){
		gameConf.chkMAutoPos = cur;
		 android::saveIntSetting(appMain, "chkMAutoPos", gameConf.chkMAutoPos);
	}
	
	cur = chkSTAutoPos->isChecked()?1:0;
	if(cur != gameConf.chkSTAutoPos){
		gameConf.chkSTAutoPos = cur;
		 android::saveIntSetting(appMain, "chkSTAutoPos", gameConf.chkSTAutoPos);
	}
	
	
	cur = chkRandomPos->isChecked()?1:0;
	if(cur != gameConf.chkRandomPos){
		gameConf.chkRandomPos = cur;
		 android::saveIntSetting(appMain, "chkRandomPos", gameConf.chkRandomPos);
	}
	
	cur = chkAutoChain->isChecked()?1:0;
	if(cur != gameConf.chkAutoChain){
		gameConf.chkAutoChain = cur;
		 android::saveIntSetting(appMain, "chkAutoChain", gameConf.chkAutoChain);
	}

	cur = chkIgnore1->isChecked()?1:0;
	if(cur != gameConf.chkIgnore1){
		gameConf.chkIgnore1 = cur;
		 android::saveIntSetting(appMain, "chkIgnore1", gameConf.chkIgnore1);
	}
	
	cur = chkIgnore2->isChecked()?1:0;
	if(cur != gameConf.chkIgnore2){
		gameConf.chkIgnore2 = cur;
		 android::saveIntSetting(appMain, "chkIgnore2", gameConf.chkIgnore2);
	}
	
	cur = chkHideSetname->isChecked()?1:0;
	if(cur != gameConf.chkHideSetname){
		gameConf.chkHideSetname = cur;
		 android::saveIntSetting(appMain, "chkHideSetname", gameConf.chkHideSetname);
	}

	cur = chkIgnoreDeckChanges->isChecked()?1:0;
	if(cur != gameConf.chkIgnoreDeckChanges){
		gameConf.chkIgnoreDeckChanges = cur;
		 android::saveIntSetting(appMain, "chkIgnoreDeckChanges", gameConf.chkIgnoreDeckChanges);
	}
	
	cur = chkAutoSearch->isChecked()?0:-1;
	if(cur != gameConf.auto_search_limit){
		gameConf.auto_search_limit = cur;
		 android::saveIntSetting(appMain, "auto_search_limit", gameConf.auto_search_limit);
	}

//gameConf.defaultOT = defaultOT->isChecked()?1:0;
//    android::saveIntSetting(appMain, "defaultOT", gameConf.defaultOT);
//gameConf.control_mode = control_mode->isChecked()?1:0;
//	  android::saveIntSetting(appMain, "control_mode", gameConf.control_mode);
//gameConf.draw_field_spell = draw_field_spell->isChecked()?1:0;
//	  android::saveIntSetting(appMain, "draw_field_spell", gameConf.draw_field_spell);
//gameConf.separate_clear_button = separate_clear_button->isChecked()?1:0;
//	  android::saveIntSetting(appMain, "separate_clear_button", gameConf.separate_clear_button);
//TEST BOT MODE
//gameConf.enable_bot_mode = enable_bot_mode->isChecked()?1:0;
//	  android::saveIntSetting(appMain, "enable_bot_mode", gameConf.enable_bot_mode);
}

void Game::ShowCardInfo(int code) {
	CardData cd;
	wchar_t formatBuffer[256];
	if(!dataManager.GetData(code, &cd))
		memset(&cd, 0, sizeof(CardData));
	imgCard->setImage(imageManager.GetTexture(code));
	imgCard->setScaleImage(true);
	if(cd.alias != 0 && (cd.alias - code < 10 || code - cd.alias < 10))
		myswprintf(formatBuffer, L"%ls[%08d]", dataManager.GetName(cd.alias), cd.alias);
	else myswprintf(formatBuffer, L"%ls[%08d]", dataManager.GetName(code), code);
	stName->setText(formatBuffer);
	int offset = 0;
	if(!chkHideSetname->isChecked()) {
		unsigned long long sc = cd.setcode;
		if(cd.alias) {
			auto aptr = dataManager._datas.find(cd.alias);
			if(aptr != dataManager._datas.end())
				sc = aptr->second.setcode;
		}
		if(sc) {
			offset = 23;
			myswprintf(formatBuffer, L"%ls%ls", dataManager.GetSysString(1329), dataManager.FormatSetName(sc));
			stSetName->setText(formatBuffer);
		} else
			stSetName->setText(L"");
	} else {
		stSetName->setText(L"");
	}
	if(cd.type & TYPE_MONSTER) {
		myswprintf(formatBuffer, L"[%ls] %ls/%ls", dataManager.FormatType(cd.type), dataManager.FormatRace(cd.race), dataManager.FormatAttribute(cd.attribute));
		stInfo->setText(formatBuffer);
		if(!(cd.type & TYPE_LINK)) {
			wchar_t* form = L"\u2605";
			if(cd.type & TYPE_XYZ) form = L"\u2606";
			myswprintf(formatBuffer, L"[%ls%d] ", form, cd.level);
			wchar_t adBuffer[16];
			if(cd.attack < 0 && cd.defense < 0)
				myswprintf(adBuffer, L"?/?");
			else if(cd.attack < 0)
				myswprintf(adBuffer, L"?/%d", cd.defense);
			else if(cd.defense < 0)
				myswprintf(adBuffer, L"%d/?", cd.attack);
			else
				myswprintf(adBuffer, L"%d/%d", cd.attack, cd.defense);
			wcscat(formatBuffer, adBuffer);
		} else {
			myswprintf(formatBuffer, L"[LINK-%d] ", cd.level);
			wchar_t adBuffer[16];
			if(cd.attack < 0)
				myswprintf(adBuffer, L"?/-   ");
			else
				myswprintf(adBuffer, L"%d/-   ", cd.attack);
			wcscat(formatBuffer, adBuffer);
			wcscat(formatBuffer, dataManager.FormatLinkMarker(cd.link_marker));
		}
		if(cd.type & TYPE_PENDULUM) {
			wchar_t scaleBuffer[16];
			myswprintf(scaleBuffer, L"   %d/%d", cd.lscale, cd.rscale);
			wcscat(formatBuffer, scaleBuffer);
		}
		stDataInfo->setText(formatBuffer);
		stSetName->setRelativePosition(rect<s32>(15 * xScale, 83 * yScale, 296 * xScale, 106 * yScale));
		stText->setRelativePosition(rect<s32>(15 * xScale, (83 + offset) * yScale, 287 * xScale, 324 * yScale));
		scrCardText->setRelativePosition(rect<s32>(267 * xScale, (83 + offset) * yScale, 287 * xScale, 324 * yScale));
	} else {
		myswprintf(formatBuffer, L"[%ls]", dataManager.FormatType(cd.type));
		stInfo->setText(formatBuffer);
		stDataInfo->setText(L"");
		stSetName->setRelativePosition(rect<s32>(15 * xScale, 60 * yScale, 296 * xScale, 83 * yScale));
		stText->setRelativePosition(rect<s32>(15 * xScale, (60 + offset) * yScale, 287 * xScale, 324 * yScale));
		scrCardText->setRelativePosition(rect<s32>(267 * xScale, (60 + offset) * yScale, 287 * xScale, 324 * yScale));
	}
	showingtext = dataManager.GetText(code);
	const auto& tsize = stText->getRelativePosition();
	InitStaticText(stText, tsize.getWidth(), tsize.getHeight(), textFont, showingtext);
}
void Game::AddChatMsg(wchar_t* msg, int player) {
	for(int i = 7; i > 0; --i) {
		chatMsg[i] = chatMsg[i - 1];
		chatTiming[i] = chatTiming[i - 1];
		chatType[i] = chatType[i - 1];
	}
	chatMsg[0].clear();
	chatTiming[0] = 1200;
	chatType[0] = player;
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
	default: //from watcher or unknown
		if(player < 11 || player > 19)
			chatMsg[0].append(L"[---]: ");
	}
	chatMsg[0].append(msg);
}
void Game::AddDebugMsg(char* msg)
{
	if (enable_log & 0x1) {
		wchar_t wbuf[1024];
		BufferIO::DecodeUTF8(msg, wbuf);
		AddChatMsg(wbuf, 9);
	}
	if (enable_log & 0x2) {
		FILE* fp = fopen("error.log", "at");
		if (!fp)
			return;
		time_t nowtime = time(NULL);
		struct tm *localedtime = localtime(&nowtime);
		char timebuf[40];
		strftime(timebuf, 40, "%Y-%m-%d %H:%M:%S", localedtime);
		fprintf(fp, "[%s][Script Error]: %s\n", timebuf, msg);
		fclose(fp);
	}
}
void Game::ClearTextures() {
	matManager.mCard.setTexture(0, 0);
	imgCard->setImage(imageManager.tCover[0]);
	btnPSAU->setImage();
	btnPSDU->setImage();
	for(int i=0; i<=4; ++i) {
		btnCardSelect[i]->setImage();
		btnCardDisplay[i]->setImage();
	}
	imageManager.ClearTexture();
}
void Game::CloseDuelWindow() {
	for(auto wit = fadingList.begin(); wit != fadingList.end(); ++wit) {
		if(wit->isFadein)
			wit->autoFadeoutFrame = 1;
	}
	wACMessage->setVisible(false);
	wANAttribute->setVisible(false);
	wANCard->setVisible(false);
	wANNumber->setVisible(false);
	wANRace->setVisible(false);
	wCardImg->setVisible(false);
	wCardSelect->setVisible(false);
	wCardDisplay->setVisible(false);
	wCmdMenu->setVisible(false);
	wFTSelect->setVisible(false);
	wHand->setVisible(false);
	wInfos->setVisible(false);
	wMessage->setVisible(false);
	wOptions->setVisible(false);
	wPhase->setVisible(false);
	wPosSelect->setVisible(false);
	wQuery->setVisible(false);
	wReplayControl->setVisible(false);
	wReplaySave->setVisible(false);
	stHintMsg->setVisible(false);
	btnSideOK->setVisible(false);
	btnLeaveGame->setVisible(false);
	btnSpectatorSwap->setVisible(false);
	btnChainIgnore->setVisible(false);
	btnChainAlways->setVisible(false);
	btnChainWhenAvail->setVisible(false);
	btnCancelOrFinish->setVisible(false);
	btnShuffle->setVisible(false);
	wChat->setVisible(false);
	lstLog->clear();
	logParam.clear();
	lstHostList->clear();
	DuelClient::hosts.clear();
	ClearTextures();
	closeDoneSignal.Set();
}
int Game::LocalPlayer(int player) {
	return dInfo.isFirst ? player : 1 - player;
}
const wchar_t* Game::LocalName(int local_player) {
	return local_player == 0 ? dInfo.hostname : dInfo.clientname;
}

}
