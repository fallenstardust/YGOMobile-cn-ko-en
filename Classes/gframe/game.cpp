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
	return !mywcsncasecmp(filename + (flen - elen), extension, elen);
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
    ALOGD("cc game: stop bgm");
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
            ALOGD("cc game: APP_CMD_PAUSE");
            if(ygo::mainGame != nullptr){
                ygo::mainGame->stopBGM();
            }
            break;
        case APP_CMD_RESUME:
        	//第一次不一定调用
			ALOGD("cc game: APP_CMD_RESUME");
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

bool IsExtension(const char* filename, const char* extension) {
	auto flen = std::strlen(filename);
	auto elen = std::strlen(extension);
	if (!elen || flen < elen)
		return false;
	return !mystrncasecmp(filename + (flen - elen), extension, elen);
}

bool Game::Initialize(ANDROID_APP app, android::InitOptions *options) {
	this->appMain = app;
	srand(time(0));
	irr::SIrrlichtCreationParameters params{};
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
	if(!device) {
		ErrorLog("Failed to create Irrlicht Engine device!");
		return false;
	}
	if (!android::perfromTrick(app)) {
		return false;
	}
	irr::core::vector2di appPosition = android::initJavaBridge(app, device);
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

	ALOGD("cc game: xScale = %f, yScale = %f", xScale, yScale);

    SetCardS3DVertex();//reset cardfront cardback S3DVertex size
	//io::path databaseDir = options->getDBDir();
	io::path workingDir = options->getWorkDir();
    ALOGD("cc game: workingDir= %s", workingDir.c_str());
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
		    ALOGD("cc game: add arrchive ok:%s", zip_path.c_str());
	    }else{
			ALOGW("cc game: add arrchive fail:%s", zip_path.c_str());
		}
	}

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
	ALOGD("cc game: isNPOTSupported = %d", isNPOTSupported);
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
		    ALOGD("cc game: add cdb ok:%s", cdb_path.c_str());
	    }else{
			ALOGW("cc game: add cdb fail:%s", cdb_path.c_str());
		}
	}
	//if(!dataManager.LoadDB(workingDir.append("/cards.cdb").c_str()))
	//	return false;
	if(dataManager.LoadStrings((workingDir + path("/expansions/strings.conf")).c_str())){
		ALOGD("cc game: loadStrings expansions/strings.conf");
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
	  ALOGW("cc game: add font fail ");
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
	wMainMenu = env->addWindow(Resize(450, 40, 900, 600), false, L"");
	wMainMenu->getCloseButton()->setVisible(false);
	wMainMenu->setDrawBackground(false);
    //button Lan Mode
	btnLanMode = irr::gui::CGUIImageButton::addImageButton(env, Resize(15, 30, 350, 106), wMainMenu, BUTTON_LAN_MODE);
	btnLanMode->setImageSize(core::dimension2di(400 * yScale, 76 * yScale));
	btnLanMode->setDrawBorder(false);
	btnLanMode->setImage(imageManager.tTitleBar);
    textLanMode = env->addStaticText(dataManager.GetSysString(1200), Resize(15, 25, 350, 60), false, false, btnLanMode);
    textLanMode->setOverrideFont(titleFont);
    textLanMode->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    //version code
    wchar_t strbuf[256];
    myswprintf(strbuf, L"YGOPro Version:%X.0%X.%X", (PRO_VERSION & 0xf000U) >> 12, (PRO_VERSION & 0x0ff0U) >> 4, PRO_VERSION & 0x000fU);
	env->addStaticText(strbuf, Resize(55, 2, 280, 35), false, false, btnLanMode);
    //button Single Mode
	btnSingleMode = irr::gui::CGUIImageButton::addImageButton(env,  Resize(15, 110, 350, 186), wMainMenu, BUTTON_SINGLE_MODE);
	btnSingleMode->setImageSize(core::dimension2di(400 * yScale, 76 * yScale));
	btnSingleMode->setDrawBorder(false);
    btnSingleMode->setImage(imageManager.tTitleBar);
	textSingleMode = env->addStaticText(dataManager.GetSysString(1201), Resize(15, 25, 350, 60), false, false, btnSingleMode);
	textSingleMode->setOverrideFont(titleFont);
    textSingleMode->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    //button Replay Mode
	btnReplayMode = irr::gui::CGUIImageButton::addImageButton(env, Resize(15, 190, 350, 266), wMainMenu, BUTTON_REPLAY_MODE);
    btnReplayMode->setImageSize(core::dimension2di(400 * yScale, 76 * yScale));
    btnReplayMode->setDrawBorder(false);
    btnReplayMode->setImage(imageManager.tTitleBar);
	textReplayMode = env->addStaticText(dataManager.GetSysString(1202), Resize(15, 25, 350, 60), false, false, btnReplayMode);
	textReplayMode->setOverrideFont(titleFont);
	textReplayMode->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    //button Deck Edit
	btnDeckEdit = irr::gui::CGUIImageButton::addImageButton(env, Resize(15, 270, 350, 346), wMainMenu, BUTTON_DECK_EDIT);
    btnDeckEdit->setImageSize(core::dimension2di(400 * yScale, 76 * yScale));
    btnDeckEdit->setDrawBorder(false);
    btnDeckEdit->setImage(imageManager.tTitleBar);
	textDeckEdit = env->addStaticText(dataManager.GetSysString(1204), Resize(15, 25, 350, 60), false, false, btnDeckEdit);
	textDeckEdit->setOverrideFont(titleFont);
	textDeckEdit->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    //button Settings
    btnSettings = irr::gui::CGUIImageButton::addImageButton(env, Resize(15, 350, 350, 426), wMainMenu, BUTTON_SETTINGS);
    btnSettings->setImageSize(core::dimension2di(400 * yScale, 76 * yScale));
	btnSettings->setDrawBorder(false);
	btnSettings->setImage(imageManager.tTitleBar);
	textSettings = env->addStaticText(dataManager.GetSysString(1273), Resize(15, 25, 350, 60), false, false, btnSettings);
	textSettings->setOverrideFont(titleFont);
	textSettings->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    //button Exit
    btnModeExit = irr::gui::CGUIImageButton::addImageButton(env, Resize(15, 430, 350, 506), wMainMenu, BUTTON_MODE_EXIT);
    btnModeExit->setImageSize(core::dimension2di(400 * yScale, 76 * yScale));
	btnModeExit->setDrawBorder(false);
	btnModeExit->setImage(imageManager.tTitleBar);
	textModeExit = env->addStaticText(dataManager.GetSysString(1210), Resize(15, 25, 350, 65), false, false, btnModeExit);
	textModeExit->setOverrideFont(titleFont);
	textModeExit->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);

    //---------------------game windows---------------------
    //lan mode
	wLanWindow = env->addWindow(Resize(220, 100, 800, 520), false, dataManager.GetSysString(1200));
	wLanWindow->getCloseButton()->setVisible(false);
	wLanWindow->setVisible(false);
    ChangeToIGUIImageWindow(wLanWindow, &bgLanWindow, imageManager.tWindow);
	env->addStaticText(dataManager.GetSysString(1220), Resize(30, 30, 70, 70), false, false, wLanWindow);
	ebNickName = CAndroidGUIEditBox::addAndroidEditBox(gameConf.nickname, true, env, Resize(110, 25, 420, 65), wLanWindow);
	ebNickName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	lstHostList = env->addListBox(Resize(30, 75, 540, 240), wLanWindow, LISTBOX_LAN_HOST, true);
	lstHostList->setItemHeight(30 * yScale);
	btnLanRefresh = env->addButton(Resize(205, 250, 315, 290), wLanWindow, BUTTON_LAN_REFRESH, dataManager.GetSysString(1217));
		ChangeToIGUIImageButton(btnLanRefresh, imageManager.tButton_S, imageManager.tButton_S_pressed);
	env->addStaticText(dataManager.GetSysString(1221), Resize(30, 305, 100, 340), false, false, wLanWindow);
	ebJoinHost = CAndroidGUIEditBox::addAndroidEditBox(gameConf.lasthost, true, env, Resize(110, 300, 270, 340), wLanWindow);
	ebJoinHost->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	ebJoinPort = CAndroidGUIEditBox::addAndroidEditBox(gameConf.lastport, true, env, Resize(280, 300, 340, 340), wLanWindow);
	ebJoinPort->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1222), Resize(30, 355, 100, 390), false, false, wLanWindow);
	ebJoinPass = CAndroidGUIEditBox::addAndroidEditBox(gameConf.roompass, true, env, Resize(110, 350, 340, 390), wLanWindow);
	ebJoinPass->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnJoinHost = env->addButton(Resize(430, 300, 540, 340), wLanWindow, BUTTON_JOIN_HOST, dataManager.GetSysString(1223));
		ChangeToIGUIImageButton(btnJoinHost, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnJoinCancel = env->addButton(Resize(430, 350, 540, 390), wLanWindow, BUTTON_JOIN_CANCEL, dataManager.GetSysString(1212));
		ChangeToIGUIImageButton(btnJoinCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnCreateHost = env->addButton(Resize(430, 25, 540, 65), wLanWindow, BUTTON_CREATE_HOST, dataManager.GetSysString(1224));
		ChangeToIGUIImageButton(btnCreateHost, imageManager.tButton_S, imageManager.tButton_S_pressed);

	//create host
	wCreateHost = env->addWindow(Resize(220, 100, 800, 520), false, dataManager.GetSysString(1224));
	wCreateHost->getCloseButton()->setVisible(false);
	wCreateHost->setVisible(false);
        ChangeToIGUIImageWindow(wCreateHost, &bgCreateHost, imageManager.tWindow);
    env->addStaticText(dataManager.GetSysString(1226), Resize(20, 30, 90, 65), false, false, wCreateHost);
	cbHostLFlist = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(110, 25, 260, 65), wCreateHost);
	for(unsigned int i = 0; i < deckManager._lfList.size(); ++i)
		cbHostLFlist->addItem(deckManager._lfList[i].listName.c_str(), deckManager._lfList[i].hash);
	cbHostLFlist->setSelected(gameConf.use_lflist ? gameConf.default_lflist : cbHostLFlist->getItemCount() - 1);
	env->addStaticText(dataManager.GetSysString(1225), Resize(20, 75, 100, 110), false, false, wCreateHost);
	cbRule = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(110, 75, 260, 115), wCreateHost);
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
	env->addStaticText(dataManager.GetSysString(1227), Resize(20, 130, 100, 165), false, false, wCreateHost);
	cbMatchMode = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(110, 125, 260, 165), wCreateHost);
	cbMatchMode->addItem(dataManager.GetSysString(1244));
	cbMatchMode->addItem(dataManager.GetSysString(1245));
	cbMatchMode->addItem(dataManager.GetSysString(1246));
	env->addStaticText(dataManager.GetSysString(1237), Resize(20, 180, 100, 215), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 180);
	ebTimeLimit = CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, Resize(110, 175, 260, 215), wCreateHost);
	ebTimeLimit->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1236), Resize(270, 30, 350, 65), false, false, wCreateHost);
	cbDuelRule = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(360, 25, 510, 65), wCreateHost);
	cbDuelRule->addItem(dataManager.GetSysString(1260));
	cbDuelRule->addItem(dataManager.GetSysString(1261));
	cbDuelRule->addItem(dataManager.GetSysString(1262));
	cbDuelRule->addItem(dataManager.GetSysString(1263));
	cbDuelRule->addItem(dataManager.GetSysString(1264));
	cbDuelRule->setSelected(gameConf.default_rule - 1);
	chkNoCheckDeck = env->addCheckBox(false, Resize(250, 235, 350, 275), wCreateHost, -1, dataManager.GetSysString(1229));
	chkNoShuffleDeck = env->addCheckBox(false, Resize(360, 235, 460, 275), wCreateHost, -1, dataManager.GetSysString(1230));
	env->addStaticText(dataManager.GetSysString(1231), Resize(270, 80, 350, 310), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 8000);
	ebStartLP = CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, Resize(360, 75, 510, 115), wCreateHost);
	ebStartLP->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1232), Resize(270, 130, 350, 165), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 5);
	ebStartHand = CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, Resize(360, 125, 510, 165), wCreateHost);
	ebStartHand->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1233), Resize(270, 180, 350, 215), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 1);
	ebDrawCount = CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, Resize(360, 175, 510, 215), wCreateHost);
	ebDrawCount->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1234), Resize(20, 305, 100, 340), false, false, wCreateHost);
	ebServerName = CAndroidGUIEditBox::addAndroidEditBox(gameConf.gamename, true, env, Resize(110, 300, 340, 340), wCreateHost);
	ebServerName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1235), Resize(20, 355, 100, 390), false, false, wCreateHost);
	ebServerPass = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(110, 350, 340, 390), wCreateHost);
	ebServerPass->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnHostConfirm = env->addButton(Resize(430, 300, 540, 340), wCreateHost, BUTTON_HOST_CONFIRM, dataManager.GetSysString(1211));
		ChangeToIGUIImageButton(btnHostConfirm, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnHostCancel = env->addButton(Resize(430, 350, 540, 390), wCreateHost, BUTTON_HOST_CANCEL, dataManager.GetSysString(1212));
		ChangeToIGUIImageButton(btnHostCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);

	//host(single)
	wHostPrepare = env->addWindow(Resize(220, 100, 800, 520), false, dataManager.GetSysString(1250));
	wHostPrepare->setDraggable(false);
	wHostPrepare->getCloseButton()->setVisible(false);
	wHostPrepare->setVisible(false);
	    ChangeToIGUIImageWindow(wHostPrepare, &bgHostPrepare, imageManager.tWindow);
    btnHostPrepDuelist = env->addButton(Resize(10, 30, 120, 70), wHostPrepare, BUTTON_HP_DUELIST, dataManager.GetSysString(1251));
		ChangeToIGUIImageButton(btnHostPrepDuelist, imageManager.tButton_S, imageManager.tButton_S_pressed);
	for(int i = 0; i < 2; ++i) {
		stHostPrepDuelist[i] = env->addStaticText(L"", Resize(60, 80 + i * 45, 260, 120 + i * 45), true, false, wHostPrepare);
		stHostPrepDuelist[i]->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
		btnHostPrepKick[i] = irr::gui::CGUIImageButton::addImageButton(env, Resize(10, 80 + i * 45, 50, 120 + i * 45), wHostPrepare, BUTTON_HP_KICK);
        btnHostPrepKick[i]->setImageSize(core::dimension2di(40 * yScale, 40 * yScale));
        btnHostPrepKick[i]->setDrawBorder(false);
        btnHostPrepKick[i]->setImage(imageManager.tClose);
		chkHostPrepReady[i] = env->addCheckBox(false, Resize(270, 80 + i * 45, 310, 120 + i * 45), wHostPrepare, CHECKBOX_HP_READY, L"");
		chkHostPrepReady[i]->setEnabled(false);
	}
	for(int i = 2; i < 4; ++i) {
		stHostPrepDuelist[i] = env->addStaticText(L"", Resize(60, 135 + i * 45, 260, 175 + i * 45), true, false, wHostPrepare);
		stHostPrepDuelist[i]->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
        btnHostPrepKick[i] = irr::gui::CGUIImageButton::addImageButton(env,Resize(10, 135 + i * 45, 50, 175 + i * 45), wHostPrepare, BUTTON_HP_KICK);
        btnHostPrepKick[i]->setImageSize(core::dimension2di(40 * yScale, 40 * yScale));
        btnHostPrepKick[i]->setDrawBorder(false);
        btnHostPrepKick[i]->setImage(imageManager.tClose);
		chkHostPrepReady[i] = env->addCheckBox(false, Resize(270, 135 + i * 45, 310, 175 + i * 45), wHostPrepare, CHECKBOX_HP_READY, L"");
		chkHostPrepReady[i]->setEnabled(false);
	}
	btnHostPrepOB = env->addButton(Resize(10, 175, 120, 215), wHostPrepare, BUTTON_HP_OBSERVER, dataManager.GetSysString(1252));
		ChangeToIGUIImageButton(btnHostPrepOB, imageManager.tButton_S, imageManager.tButton_S_pressed);
	myswprintf(strbuf, L"%ls%d", dataManager.GetSysString(1253), 0);
	stHostPrepOB = env->addStaticText(strbuf, Resize(320, 310, 560, 350), false, false, wHostPrepare);
	stHostPrepRule = env->addStaticText(L"", Resize(320, 30, 560, 300), false, true, wHostPrepare);
	env->addStaticText(dataManager.GetSysString(1254), Resize(10, 320, 110, 350), false, false, wHostPrepare);
	cbCategorySelect = env->addComboBox(Resize(0, 0, 0, 0), wHostPrepare, COMBOBOX_HP_CATEGORY);
	cbDeckSelect = env->addComboBox(Resize(0, 0, 0, 0), wHostPrepare);
	btnHostDeckSelect = env->addButton(Resize(10, 350, 270, 390), wHostPrepare, BUTTON_HP_DECK_SELECT, L"");
	btnHostPrepReady = env->addButton(Resize(170, 175, 280, 215), wHostPrepare, BUTTON_HP_READY, dataManager.GetSysString(1218));
		ChangeToIGUIImageButton(btnHostPrepReady, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnHostPrepNotReady = env->addButton(Resize(170, 175, 280, 215), wHostPrepare, BUTTON_HP_NOTREADY, dataManager.GetSysString(1219));
		ChangeToIGUIImageButton(btnHostPrepNotReady, imageManager.tButton_S_pressed, imageManager.tButton_S);
	btnHostPrepNotReady->setVisible(false);
	btnHostPrepStart = env->addButton(Resize(320, 350, 430, 390), wHostPrepare, BUTTON_HP_START, dataManager.GetSysString(1215));
		ChangeToIGUIImageButton(btnHostPrepStart, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnHostPrepCancel = env->addButton(Resize(440, 350, 550, 390), wHostPrepare, BUTTON_HP_CANCEL, dataManager.GetSysString(1210));
		ChangeToIGUIImageButton(btnHostPrepCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);

	//img always use *yScale to keep proportion
	wCardImg = env->addStaticText(L"",Resize_Y(1, 1, 2 + CARD_IMG_WIDTH, 2 + CARD_IMG_HEIGHT), true, false, 0, -1, true);
    wCardImg->setBackgroundColor(0xc0c0c0c0);
	wCardImg->setVisible(false);
	imgCard = env->addImage(Resize_Y(2, 2, CARD_IMG_WIDTH, CARD_IMG_HEIGHT), wCardImg);
	imgCard->setImage(imageManager.tCover[0]);
	imgCard->setScaleImage(true);
	imgCard->setUseAlphaChannel(true);

	//phase
	wPhase = env->addStaticText(L"", Resize(480, 305, 895, 335));
	wPhase->setVisible(false);
	btnPhaseStatus = env->addButton(Resize(0, 0, 50, 30), wPhase, BUTTON_PHASE, L"");
	    ChangeToIGUIImageButton(btnPhaseStatus, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnPhaseStatus->setIsPushButton(true);
	btnPhaseStatus->setPressed(true);
	btnPhaseStatus->setVisible(false);
	btnBP = env->addButton(Resize(160, 0, 210, 30), wPhase, BUTTON_BP, L"\xff22\xff30");
        ChangeToIGUIImageButton(btnBP, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnBP->setVisible(false);
	btnM2 = env->addButton(Resize(160, 0, 210, 30), wPhase, BUTTON_M2, L"\xff2d\xff12");
        ChangeToIGUIImageButton(btnM2, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnM2->setVisible(false);
	btnEP = env->addButton(Resize(320, 0, 370, 30), wPhase, BUTTON_EP, L"\xff25\xff30");
        ChangeToIGUIImageButton(btnEP, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnEP->setVisible(false);

    //tab（changed）
	wInfos = env->addWindow(Resize(1, 3 + CARD_IMG_HEIGHT, 260, 639), false, L"");
	wInfos->getCloseButton()->setVisible(false);
	wInfos->setDraggable(false);
	wInfos->setVisible(false);
	    ChangeToIGUIImageWindow(wInfos, &bgInfos, imageManager.tWindow_V);
	//info
	stName = env->addStaticText(L"", Resize(10, 10, 250, 32), true, false, wInfos, -1, false);
	stName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	stInfo = env->addStaticText(L"", Resize(10, 37, 260, 60), false, true, wInfos, -1, false);
	stInfo->setOverrideColor(SColor(255, 149, 211, 137));//255, 0, 0, 255
	stDataInfo = env->addStaticText(L"", Resize(10, 60, 260, 83), false, true, wInfos, -1, false);
	stDataInfo->setOverrideColor(SColor(255, 222, 215, 100));//255, 0, 0, 255
	stSetName = env->addStaticText(L"", Resize(10, 83, 260, 106), false, true, wInfos, -1, false);
	stSetName->setOverrideColor(SColor(255, 255, 152, 42));//255, 0, 0, 255
	stText = env->addStaticText(L"", Resize(10, 106, 250, 340), false, true, wInfos, -1, false);
    stText->setOverrideFont(textFont);
	scrCardText = env->addScrollBar(false, Resize(250, 106, 258, 639), wInfos, SCROLL_CARDTEXT);
	scrCardText->setLargeStep(1);
	scrCardText->setSmallStep(1);
	scrCardText->setVisible(false);
    //imageButtons pallet
    wPallet = env->addWindow(Resize(262, 3 + CARD_IMG_HEIGHT, 307, 639), false, L"");
    wPallet->getCloseButton()->setVisible(false);
    wPallet->setDraggable(false);
    wPallet->setDrawTitlebar(false);
    wPallet->setDrawBackground(false);
    wPallet->setVisible(false);
    //Logs
    imgLog = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(0, 55, 45, 100), wPallet, BUTTON_SHOW_LOG);
	imgLog->setImageSize(core::dimension2di(28 * yScale, 28 * yScale));
	imgLog->setImage(imageManager.tLogs);
	imgLog->setIsPushButton(true);
	wLogs = env->addWindow(Resize(720, 1, 1020, 501), false, dataManager.GetSysString(1271));
    wLogs->getCloseButton()->setVisible(false);
    wLogs->setVisible(false);
        ChangeToIGUIImageWindow(wLogs, &bgLogs, imageManager.tDialog_S);
    lstLog = env->addListBox(Resize(10, 20, 290, 440), wLogs, LISTBOX_LOG, false);
    lstLog->setItemHeight(30 * yScale);
    btnClearLog = env->addButton(Resize(20, 450, 130, 490), wLogs, BUTTON_CLEAR_LOG, dataManager.GetSysString(1304));
        ChangeToIGUIImageButton(btnClearLog, imageManager.tButton_S, imageManager.tButton_S_pressed);
    btnCloseLog = env->addButton(Resize(170, 450, 280, 490), wLogs, BUTTON_CLOSE_LOG, dataManager.GetSysString(1211));
		ChangeToIGUIImageButton(btnCloseLog, imageManager.tButton_S, imageManager.tButton_S_pressed);
    //vol play/mute
	imgVol = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(0, 110, 45, 155), wPallet, BUTTON_BGM);
    imgVol->setImageSize(core::dimension2di(28 * yScale, 28 * yScale));
	if (gameConf.enable_music) {
		imgVol->setImage(imageManager.tPlay);
	} else {
		imgVol->setImage(imageManager.tMute);
	}
    //shift quick animation
    imgQuickAnimation = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(0, 165, 45, 210), wPallet, BUTTON_QUICK_ANIMIATION);
    imgQuickAnimation->setImageSize(core::dimension2di(28 * yScale, 28 * yScale));
    if (gameConf.quick_animation) {
        imgQuickAnimation->setImage(imageManager.tDoubleX);
    } else {
        imgQuickAnimation->setImage(imageManager.tOneX);
    }
    //Settings
	imgSettings = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(0, 0, 45, 45), wPallet, BUTTON_SETTINGS);
	imgSettings->setImageSize(core::dimension2di(28 * yScale, 28 * yScale));
	imgSettings->setImage(imageManager.tSettings);
	imgSettings->setIsPushButton(true);
    wSettings = env->addWindow(Resize(220, 80, 800, 540), false, dataManager.GetSysString(1273));
    wSettings->setRelativePosition(Resize(220, 80, 800, 540));
    wSettings->getCloseButton()->setVisible(false);
	wSettings->setVisible(false);
	    ChangeToIGUIImageWindow(wSettings, &bgSettings, imageManager.tWindow);
	int posX = 20;
	int posY = 40;
	chkMAutoPos = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, -1, dataManager.GetSysString(1274));
	chkMAutoPos->setChecked(gameConf.chkMAutoPos != 0);
	posY += 40;
	chkSTAutoPos = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, -1, dataManager.GetSysString(1278));
	chkSTAutoPos->setChecked(gameConf.chkSTAutoPos != 0);
	posY += 40;
	chkRandomPos = env->addCheckBox(false, Resize(posX + 20, posY, posX + 20 + 260, posY + 30), wSettings, -1, dataManager.GetSysString(1275));
	chkRandomPos->setChecked(gameConf.chkRandomPos != 0);
	posY += 40;
	chkAutoChain = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, -1, dataManager.GetSysString(1276));
	chkAutoChain->setChecked(gameConf.chkAutoChain != 0);
	posY += 40;
	chkWaitChain = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, -1, dataManager.GetSysString(1277));
	chkWaitChain->setChecked(gameConf.chkWaitChain != 0);
    posY += 40;
	chkDefaultShowChain = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, -1, dataManager.GetSysString(1354));
	chkDefaultShowChain->setChecked(gameConf.chkDefaultShowChain != 0);
	posY += 40;
    chkQuickAnimation = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, CHECKBOX_QUICK_ANIMATION, dataManager.GetSysString(1299));
	chkQuickAnimation->setChecked(gameConf.quick_animation != 0);
    posY += 40;
    chkDrawFieldSpell = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, CHECKBOX_DRAW_FIELD_SPELL, dataManager.GetSysString(1283));
    chkDrawFieldSpell->setChecked(gameConf.draw_field_spell != 0);
    posY += 40;
    chkDrawSingleChain = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, CHECKBOX_DRAW_SINGLE_CHAIN, dataManager.GetSysString(1287));
	chkDrawSingleChain->setChecked(gameConf.draw_single_chain != 0);
	posX = 250;//another Column
	posY = 40;
    chkLFlist = env->addCheckBox(false, Resize(posX, posY, posX + 100, posY + 30), wSettings, CHECKBOX_LFLIST, dataManager.GetSysString(1288));
    chkLFlist->setChecked(gameConf.use_lflist);
    cbLFlist = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(posX + 110, posY, posX + 230, posY + 30), wSettings, COMBOBOX_LFLIST);
    cbLFlist->setMaxSelectionRows(6);
    for(unsigned int i = 0; i < deckManager._lfList.size(); ++i)
        cbLFlist->addItem(deckManager._lfList[i].listName.c_str());
    cbLFlist->setEnabled(gameConf.use_lflist);
    cbLFlist->setSelected(gameConf.use_lflist ? gameConf.default_lflist : cbLFlist->getItemCount() - 1);
	posY += 40;
	chkIgnore1 = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, CHECKBOX_DISABLE_CHAT, dataManager.GetSysString(1290));
	chkIgnore1->setChecked(gameConf.chkIgnore1 != 0);
	posY += 40;
	chkIgnore2 = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, -1, dataManager.GetSysString(1291));
	chkIgnore2->setChecked(gameConf.chkIgnore2 != 0);
	posY += 40;
	chkHidePlayerName = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, CHECKBOX_HIDE_PLAYER_NAME, dataManager.GetSysString(1289));
	chkHidePlayerName->setChecked(gameConf.hide_player_name != 0);
	posY += 40;
	chkIgnoreDeckChanges = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, -1, dataManager.GetSysString(1357));
	chkIgnoreDeckChanges->setChecked(gameConf.chkIgnoreDeckChanges != 0);
	posY += 40;
	chkAutoSaveReplay = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, -1, dataManager.GetSysString(1366));
	chkAutoSaveReplay->setChecked(gameConf.auto_save_replay != 0);
    posY += 40;
    chkMusicMode = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, -1, dataManager.GetSysString(1281));
    chkMusicMode->setChecked(gameConf.music_mode != 0);
    posY += 40;
    chkEnableSound = env->addCheckBox(gameConf.enable_sound, Resize(posX, posY, posX + 100, posY + 30), wSettings, CHECKBOX_ENABLE_SOUND, dataManager.GetSysString(1279));
    chkEnableSound->setChecked(gameConf.enable_sound);
    scrSoundVolume = env->addScrollBar(true, Resize(posX + 110, posY, posX + 280, posY + 30), wSettings, SCROLL_VOLUME);
    scrSoundVolume->setMax(100);
    scrSoundVolume->setMin(0);
    scrSoundVolume->setPos(gameConf.sound_volume);
    scrSoundVolume->setLargeStep(1);
    scrSoundVolume->setSmallStep(1);
    posY += 40;
    chkEnableMusic = env->addCheckBox(gameConf.enable_music, Resize(posX, posY, posX + 100, posY + 30), wSettings, CHECKBOX_ENABLE_MUSIC, dataManager.GetSysString(1280));
    chkEnableMusic->setChecked(gameConf.enable_music);
    scrMusicVolume = env->addScrollBar(true, Resize(posX + 110, posY, posX + 280, posY + 30), wSettings, SCROLL_VOLUME);
    scrMusicVolume->setMax(100);
    scrMusicVolume->setMin(0);
    scrMusicVolume->setPos(gameConf.music_volume);
    scrMusicVolume->setLargeStep(1);
    scrMusicVolume->setSmallStep(1);
    elmTabSystemLast = chkEnableMusic;
	btnCloseSettings =irr::gui::CGUIImageButton::addImageButton(env,Resize(500, 30, 550, 80), wSettings, BUTTON_CLOSE_SETTINGS);
	btnCloseSettings->setImageSize(core::dimension2di(50 * yScale, 50 * yScale));
	btnCloseSettings->setDrawBorder(false);
    btnCloseSettings->setImage(imageManager.tClose);
    //
	wHand = env->addWindow(Resize(500, 450, 825, 605), false, L"");
	wHand->getCloseButton()->setVisible(false);
	wHand->setDraggable(false);
	wHand->setDrawTitlebar(false);
	wHand->setVisible(false);
	for(int i = 0; i < 3; ++i) {
		btnHand[i] = irr::gui::CGUIImageButton::addImageButton(env, Resize(10 + 105 * i, 10, 105 + 105 * i, 144), wHand, BUTTON_HAND1 + i);
		btnHand[i]->setImage(imageManager.tHand[i]);
		btnHand[i]->setImageScale(core::vector2df(xScale, yScale));
	}

	//first or second to go
	wFTSelect = env->addWindow(Resize(470, 180, 860, 360), false, L"");
	wFTSelect->getCloseButton()->setVisible(false);
	wFTSelect->setVisible(false);
	    ChangeToIGUIImageWindow(wFTSelect, &bgFTSelect, imageManager.tDialog_L);
	btnFirst = env->addButton(Resize(20, 35, 370, 85), wFTSelect, BUTTON_FIRST, dataManager.GetSysString(100));
        ChangeToIGUIImageButton(btnFirst, imageManager.tButton_L, imageManager.tButton_L_pressed, titleFont);
	btnSecond = env->addButton(Resize(20, 95, 370, 145), wFTSelect, BUTTON_SECOND, dataManager.GetSysString(101));
        ChangeToIGUIImageButton(btnSecond, imageManager.tButton_L, imageManager.tButton_L_pressed, titleFont);
	//message (370)
	wMessage = env->addWindow(Resize(470, 160, 860, 380), false, dataManager.GetSysString(1216));
	wMessage->getCloseButton()->setVisible(false);
	wMessage->setVisible(false);
	    ChangeToIGUIImageWindow(wMessage, &bgMessage, imageManager.tDialog_L);
	stMessage = env->addStaticText(L"", Resize(20, 20, 370, 120), false, true, wMessage, -1, false);
	stMessage->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnMsgOK = env->addButton(Resize(130, 150, 260, 200), wMessage, BUTTON_MSG_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnMsgOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//system message (370)
	wSysMessage = env->addWindow(Resize(315, 180, 705, 360), false, dataManager.GetSysString(1216));
	wSysMessage->getCloseButton()->setVisible(false);
	wSysMessage->setVisible(false);
		ChangeToIGUIImageWindow(wSysMessage, &bgSysMessage, imageManager.tDialog_L);
	stSysMessage = env->addStaticText(L"", Resize(20, 20, 370, 100), false, true, wSysMessage, -1, false);
	stSysMessage->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnSysMsgOK = env->addButton(Resize(130, 120, 260, 170), wSysMessage, BUTTON_SYS_MSG_OK, dataManager.GetSysString(1211));
    	ChangeToIGUIImageButton(btnSysMsgOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//auto fade message (370)
	wACMessage = env->addWindow(Resize(470, 240, 860, 300), false, L"");
	wACMessage->getCloseButton()->setVisible(false);
	wACMessage->setVisible(false);
	wACMessage->setDrawBackground(false);
	stACMessage = env->addStaticText(L"", Resize(0, 0, 350, 60), true, true, wACMessage, -1, true);
	stACMessage->setBackgroundColor(0x6011113d);
	stACMessage->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	//yes/no (370)
	wQuery = env->addWindow(Resize(470, 160, 860, 380), false, dataManager.GetSysString(560));
	wQuery->getCloseButton()->setVisible(false);
	wQuery->setVisible(false);
        ChangeToIGUIImageWindow(wQuery, &bgQuery, imageManager.tDialog_L);
	stQMessage =  env->addStaticText(L"", Resize(20, 20, 370, 140), false, true, wQuery, -1, false);
	stQMessage->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnYes = env->addButton(Resize(60, 150, 170, 200), wQuery, BUTTON_YES, dataManager.GetSysString(1213));
        ChangeToIGUIImageButton(btnYes, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnNo = env->addButton(Resize(210, 150, 320, 200), wQuery, BUTTON_NO, dataManager.GetSysString(1214));
        ChangeToIGUIImageButton(btnNo, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//surrender yes/no (310)
	wSurrender = env->addWindow(Resize(470, 180, 860, 360), false, dataManager.GetSysString(560));
	wSurrender->getCloseButton()->setVisible(false);
	wSurrender->setVisible(false);
        ChangeToIGUIImageWindow(wSurrender, &bgSurrender, imageManager.tDialog_L);
	stSurrenderMessage = env->addStaticText(dataManager.GetSysString(1359), Resize(20, 20, 350, 100), false, true, wSurrender, -1, false);
	stSurrenderMessage->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnSurrenderYes = env->addButton(Resize(60, 120, 170, 170), wSurrender, BUTTON_SURRENDER_YES, dataManager.GetSysString(1213));
        ChangeToIGUIImageButton(btnSurrenderYes, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSurrenderNo = env->addButton(Resize(200, 120, 310, 170), wSurrender, BUTTON_SURRENDER_NO, dataManager.GetSysString(1214));
        ChangeToIGUIImageButton(btnSurrenderNo, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//options (350)
	wOptions = env->addWindow(Resize(470, 180, 860, 390), false, L"");
	wOptions->getCloseButton()->setVisible(false);
	wOptions->setVisible(false);
    wOptions->setDrawBackground(false);
    bgOptions = env->addImage(Resize(0, 0, 390, 180), wOptions, -1, 0, true);
    bgOptions->setImage(imageManager.tDialog_L);
    bgOptions->setScaleImage(true);
	stOptions =  env->addStaticText(L"", Resize(20, 20, 370, 100), false, true, wOptions, -1, false);
	stOptions->setTextAlignment(irr::gui::EGUIA_UPPERLEFT, irr::gui::EGUIA_CENTER);
	btnOptionOK = env->addButton(Resize(140, 115, 250, 165), wOptions, BUTTON_OPTION_OK, dataManager.GetSysString(1211));
		ChangeToIGUIImageButton(btnOptionOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnOptionp = env->addButton(Resize(20, 115, 130, 165), wOptions, BUTTON_OPTION_PREV, L"<<<");
		ChangeToIGUIImageButton(btnOptionp, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnOptionn = env->addButton(Resize(260, 115, 370, 165), wOptions, BUTTON_OPTION_NEXT, L">>>");
		ChangeToIGUIImageButton(btnOptionn, imageManager.tButton_S, imageManager.tButton_S_pressed);
	for(int i = 0; i < 5; ++i) {
		btnOption[i] = env->addButton(Resize(20, 20 + 60 * i, 370, 70 + 60 * i), wOptions, BUTTON_OPTION_0 + i, L"");
			ChangeToIGUIImageButton(btnOption[i], imageManager.tButton_L, imageManager.tButton_L_pressed);
	}
	scrOption = env->addScrollBar(false, Resize(380, 20, 410, 310), wOptions, SCROLL_OPTION_SELECT);
	scrOption->setLargeStep(1);
	scrOption->setSmallStep(1);
	scrOption->setMin(0);
#endif
	//pos select
	wPosSelect = env->addWindow(recti(660 * xScale - 223 * yScale, 160 * yScale, 660 * xScale + 223 * yScale, (160 + 228) * yScale), false, dataManager.GetSysString(561));
	wPosSelect->getCloseButton()->setVisible(false);
	wPosSelect->setVisible(false);
        ChangeToIGUIImageWindow(wPosSelect, &bgPosSelect, imageManager.tDialog_L);
	btnPSAU = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(50, 30, 50 + 168, 30 + 168), wPosSelect, BUTTON_POS_AU);
	btnPSAU->setImageSize(core::dimension2di(CARD_IMG_WIDTH * 0.6f * yScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	btnPSAD = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(218 + 10, 30, 228 + 168, 30 + 168), wPosSelect, BUTTON_POS_AD);
	btnPSAD->setImageSize(core::dimension2di(CARD_IMG_WIDTH * 0.6f * yScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	btnPSAD->setImage(imageManager.tCover[0]);
	btnPSDU = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(50, 30, 50 + 168, 30 + 168), wPosSelect, BUTTON_POS_DU);
	btnPSDU->setImageSize(core::dimension2di(CARD_IMG_WIDTH * 0.6f * yScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	btnPSDU->setImageRotation(270);
	btnPSDD = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(218 + 10, 30, 228 + 168, 30 + 168), wPosSelect, BUTTON_POS_DD);
	btnPSDD->setImageSize(core::dimension2di(CARD_IMG_WIDTH * 0.6f * yScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	btnPSDD->setImageRotation(270);
	btnPSDD->setImage(imageManager.tCover[0]);
#ifdef _IRR_ANDROID_PLATFORM_
    //card select
	wCardSelect = env->addWindow(recti(660 * xScale - 340 * yScale, 55 * yScale, 660 * xScale + 340 * yScale, 400 * yScale), false, L"");
	wCardSelect->getCloseButton()->setVisible(false);
	wCardSelect->setVisible(false);
        ChangeToIGUIImageWindow(wCardSelect, &bgCardSelect, imageManager.tDialog_L);
    stCardSelect = env->addStaticText(L"", Resize_Y(20, 10, 660, 40), false, false, wCardSelect, -1, false);
	stCardSelect->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
    for(int i = 0; i < 5; ++i) {
		stCardPos[i] = env->addStaticText(L"", Resize_Y(30 + 125 * i, 40, 150 + 125 * i, 60), true, false, wCardSelect, -1, true);
		stCardPos[i]->setBackgroundColor(0xffffffff);
		stCardPos[i]->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
		btnCardSelect[i] = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(30 + 125 * i, 65, 150 + 125 * i, 235), wCardSelect, BUTTON_CARD_0 + i);
		btnCardSelect[i]->setImageSize(core::dimension2di(CARD_IMG_WIDTH * 0.6f * yScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	}
	scrCardList = env->addScrollBar(true, Resize_Y(30, 245, 650, 285), wCardSelect, SCROLL_CARD_SELECT);
	btnSelectOK = env->addButton(Resize_Y(340 - 55, 295, 340 + 55, 295 + 40), wCardSelect, BUTTON_CARD_SEL_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnSelectOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//card display
	wCardDisplay = env->addWindow(recti(660 * xScale - 340 * yScale, 55 * yScale, 660 * xScale + 340 * yScale, 400 * yScale), false, L"");
	wCardDisplay->getCloseButton()->setVisible(false);
	wCardDisplay->setVisible(false);
        ChangeToIGUIImageWindow(wCardDisplay, &bgCardDisplay, imageManager.tDialog_L);
    stCardDisplay = env->addStaticText(L"", Resize_Y(20, 10, 660, 40), false, false, wCardDisplay, -1, false);
    stCardDisplay->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
    for(int i = 0; i < 5; ++i) {
		stDisplayPos[i] = env->addStaticText(L"", Resize_Y(30 + 125 * i, 40, 150 + 125 * i, 60), true, false, wCardDisplay, -1, true);
		stDisplayPos[i]->setBackgroundColor(0xffffffff);
		stDisplayPos[i]->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
		btnCardDisplay[i] = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(30 + 125 * i, 65, 150 + 125 * i, 235), wCardDisplay, BUTTON_DISPLAY_0 + i);
		btnCardDisplay[i]->setImageSize(core::dimension2di(CARD_IMG_WIDTH * 0.6f * yScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	}
	scrDisplayList = env->addScrollBar(true, Resize_Y(30, 245, 30 + 620, 285), wCardDisplay, SCROLL_CARD_DISPLAY);
	btnDisplayOK = env->addButton(Resize_Y(340 - 55, 295, 340 + 55, 335), wCardDisplay, BUTTON_CARD_DISP_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnDisplayOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
#endif
	//announce number
	wANNumber = env->addWindow(Resize(500, 50, 800, 470), false, L"");
	wANNumber->getCloseButton()->setVisible(false);
	wANNumber->setVisible(false);
        ChangeToIGUIImageWindow(wANNumber, &bgANNumber, imageManager.tWindow_V);
	cbANNumber = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(30, 180, 270, 240), wANNumber, -1);
	cbANNumber->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    stANNumber = env->addStaticText(L"", Resize(20, 10, 280, 40), false, false, wANNumber, -1, false);
    stANNumber->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
	for(int i = 0; i < 12; ++i) {
		myswprintf(strbuf, L"%d", i + 1);
		btnANNumber[i] = env->addButton(Resize(50 + 70 * (i % 3), 60 + 70 * (i / 3), 110 + 70 * (i % 3), 120 + 70 * (i / 3)), wANNumber, BUTTON_ANNUMBER_1 + i, strbuf);
            ChangeToIGUIImageButton(btnANNumber[i], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
		btnANNumber[i]->setIsPushButton(true);
	}
	btnANNumberOK = env->addButton(Resize(95, 360, 205, 410), wANNumber, BUTTON_ANNUMBER_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnANNumberOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//announce card
	wANCard = env->addWindow(Resize(500, 50, 800, 550), false, L"");
	wANCard->getCloseButton()->setVisible(false);
	wANCard->setVisible(false);
        ChangeToIGUIImageWindow(wANCard, &bgANCard, imageManager.tDialog_S);
	stANCard = env->addStaticText(L"", Resize(20, 10, 280, 40), false, false, wANCard, -1, false);
	stANCard->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
    ebANCard = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(10, 50, 290, 90), wANCard, EDITBOX_ANCARD);
	ebANCard->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	lstANCard = env->addListBox(Resize(10, 100, 290, 430), wANCard, LISTBOX_ANCARD, true);
	btnANCardOK = env->addButton(Resize(95, 440, 205, 490), wANCard, BUTTON_ANCARD_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnANCardOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//announce attribute
	wANAttribute = env->addWindow(Resize(470, 160, 860, 380), false, dataManager.GetSysString(562));
	wANAttribute->getCloseButton()->setVisible(false);
	wANAttribute->setVisible(false);
	    ChangeToIGUIImageWindow(wANAttribute, &bgANAttribute, imageManager.tDialog_L);
	stANAttribute = env->addStaticText(L"", Resize(20, 10, 370, 40), false, false, wANAttribute, -1, false);
    stANAttribute->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
	for(int filter = 0x1, i = 0; i < 7; filter <<= 1, ++i)
		chkAttribute[i] = env->addCheckBox(false, Resize(50 + (i % 4) * 80, 60 + (i / 4) * 55, 130 + (i % 4) * 80, 90 + (i / 4) * 55),wANAttribute, CHECK_ATTRIBUTE, dataManager.FormatAttribute(filter).c_str());
	//announce race
	wANRace = env->addWindow(Resize(500, 40, 800, 560), false, dataManager.GetSysString(563));
	wANRace->getCloseButton()->setVisible(false);
	wANRace->setVisible(false);
	    ChangeToIGUIImageWindow(wANRace, &bgANRace, imageManager.tDialog_S);
    stANRace = env->addStaticText(L"", Resize(20, 10, 280, 40), false, false, wANRace, -1, false);
    stANRace->setTextAlignment(EGUIA_CENTER, EGUIA_CENTER);
	for(int filter = 0x1, i = 0; i < RACES_COUNT; filter <<= 1, ++i)
		chkRace[i] = env->addCheckBox(false, Resize(30 + (i % 3) * 90, 60 + (i / 3) * 50, 100 + (i % 3) * 90, 110 + (i / 3) * 50),
		                              wANRace, CHECK_RACE, dataManager.FormatRace(filter).c_str());
	//selection hint
	stHintMsg = env->addStaticText(L"", Resize(500, 90, 820, 120), true, false, 0, -1, false);
	stHintMsg->setBackgroundColor(0xee11113d);
	stHintMsg->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	stHintMsg->setVisible(false);
	stTip = env->addStaticText(L"", Resize(0, 0, 150, 150), false, true, 0, -1, true);
	stTip->setBackgroundColor(0x6011113d);
	stTip->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	stTip->setVisible(false);
	//cmd menu
	wCmdMenu = env->addWindow(Resize(0, 0, 150, 600), false, L"");
	wCmdMenu->setDrawTitlebar(false);
	wCmdMenu->setDrawBackground(false);
	wCmdMenu->setVisible(false);
	wCmdMenu->getCloseButton()->setVisible(false);
	btnActivate = env->addButton(Resize(0, 0, 150, 60), wCmdMenu, BUTTON_CMD_ACTIVATE, dataManager.GetSysString(1150));
        ChangeToIGUIImageButton(btnActivate, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSummon = env->addButton(Resize(0, 60, 150, 120), wCmdMenu, BUTTON_CMD_SUMMON, dataManager.GetSysString(1151));
        ChangeToIGUIImageButton(btnSummon, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSPSummon = env->addButton(Resize(0, 120, 150, 180), wCmdMenu, BUTTON_CMD_SPSUMMON, dataManager.GetSysString(1152));
        ChangeToIGUIImageButton(btnSPSummon, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnMSet = env->addButton(Resize(0, 180, 150, 240), wCmdMenu, BUTTON_CMD_MSET, dataManager.GetSysString(1153));
        ChangeToIGUIImageButton(btnMSet, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSSet = env->addButton(Resize(0, 240, 150, 300), wCmdMenu, BUTTON_CMD_SSET, dataManager.GetSysString(1153));
        ChangeToIGUIImageButton(btnSSet, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnRepos = env->addButton(Resize(0, 300, 150, 360), wCmdMenu, BUTTON_CMD_REPOS, dataManager.GetSysString(1154));
        ChangeToIGUIImageButton(btnRepos, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnAttack = env->addButton(Resize(0, 360, 150, 420), wCmdMenu, BUTTON_CMD_ATTACK, dataManager.GetSysString(1157));
        ChangeToIGUIImageButton(btnAttack, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnShowList = env->addButton(Resize(0, 420, 150, 480), wCmdMenu, BUTTON_CMD_SHOWLIST, dataManager.GetSysString(1158));
        ChangeToIGUIImageButton(btnShowList, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnOperation = env->addButton(Resize(0, 480, 150, 540), wCmdMenu, BUTTON_CMD_ACTIVATE, dataManager.GetSysString(1161));
        ChangeToIGUIImageButton(btnOperation, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnReset = env->addButton(Resize(0, 540 , 150, 600), wCmdMenu, BUTTON_CMD_RESET, dataManager.GetSysString(1162));
        ChangeToIGUIImageButton(btnReset, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//deck edit
	wDeckEdit = env->addWindow(Resize(310, 1, 600, 130), false, L"");
    wDeckEdit->getCloseButton()->setVisible(false);
    wDeckEdit->setDrawTitlebar(false);
	wDeckEdit->setVisible(false);
	    ChangeToIGUIImageWindow(wDeckEdit, &bgDeckEdit, imageManager.tDialog_L);
	btnManageDeck = env->addButton(Resize(10, 35, 220, 75), wDeckEdit, BUTTON_MANAGE_DECK, dataManager.GetSysString(1460));
    //deck manage
	wDeckManage = env->addWindow(Resize(530, 10, 920, 460), false, dataManager.GetSysString(1460), 0, WINDOW_DECK_MANAGE);
	wDeckManage->setVisible(false);
	wDeckManage->getCloseButton()->setVisible(false);
	    ChangeToIGUIImageWindow(wDeckManage, &bgDeckManage, imageManager.tWindow_V);
	lstCategories = env->addListBox(Resize(20, 20, 110, 430), wDeckManage, LISTBOX_CATEGORIES, true);
    lstCategories->setItemHeight(30 * yScale);
	lstDecks = env->addListBox(Resize(115, 20, 280, 430), wDeckManage, LISTBOX_DECKS, true);
	lstCategories->setItemHeight(30 * yScale);
	btnNewCategory = env->addButton(Resize(290, 20, 370, 60), wDeckManage, BUTTON_NEW_CATEGORY, dataManager.GetSysString(1461));
        ChangeToIGUIImageButton(btnNewCategory, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnRenameCategory = env->addButton(Resize(290, 65, 370, 105), wDeckManage, BUTTON_RENAME_CATEGORY, dataManager.GetSysString(1462));
        ChangeToIGUIImageButton(btnRenameCategory, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnDeleteCategory = env->addButton(Resize(290, 110, 370, 150), wDeckManage, BUTTON_DELETE_CATEGORY, dataManager.GetSysString(1463));
        ChangeToIGUIImageButton(btnDeleteCategory, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnNewDeck = env->addButton(Resize(290, 155, 370, 195), wDeckManage, BUTTON_NEW_DECK, dataManager.GetSysString(1464));
        ChangeToIGUIImageButton(btnNewDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnRenameDeck = env->addButton(Resize(290, 200, 370, 240), wDeckManage, BUTTON_RENAME_DECK, dataManager.GetSysString(1465));
        ChangeToIGUIImageButton(btnRenameDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnDMDeleteDeck = env->addButton(Resize(290, 245, 370, 285), wDeckManage, BUTTON_DELETE_DECK_DM, dataManager.GetSysString(1466));
    ChangeToIGUIImageButton(btnDMDeleteDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnMoveDeck = env->addButton(Resize(290, 290, 370, 330), wDeckManage, BUTTON_MOVE_DECK, dataManager.GetSysString(1467));
        ChangeToIGUIImageButton(btnMoveDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnCopyDeck = env->addButton(Resize(290, 335, 370, 375), wDeckManage, BUTTON_COPY_DECK, dataManager.GetSysString(1468));
        ChangeToIGUIImageButton(btnCopyDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnCloseDM = env->addButton(Resize(290, 390, 370, 430), wDeckManage, BUTTON_CLOSE_DECKMANAGER, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnCloseDM, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//deck manage query
	wDMQuery = env->addWindow(Resize(490, 180, 840, 340), false, dataManager.GetSysString(1460));
	wDMQuery->getCloseButton()->setVisible(false);
	wDMQuery->setVisible(false);
	wDMQuery->setDraggable(false);
	    ChangeToIGUIImageWindow(wDMQuery, &bgDMQuery, imageManager.tDialog_L);
	stDMMessage = env->addStaticText(L"", Resize(20, 25, 290, 45), false, false, wDMQuery);
	stDMMessage2 = env->addStaticText(L"", Resize(20, 50, 330, 90), false, false, wDMQuery, -1, true);
	stDMMessage2->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	ebDMName = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(20, 50, 330, 90), wDMQuery, -1);
	ebDMName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	cbDMCategory = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(20, 50, 330, 90), wDMQuery, -1);
	stDMMessage2->setVisible(false);
	ebDMName->setVisible(false);
	cbDMCategory->setVisible(false);
	cbDMCategory->setMaxSelectionRows(10);
	btnDMOK = env->addButton(Resize(70, 100, 160, 150), wDMQuery, BUTTON_DM_OK, dataManager.GetSysString(1211));
	    ChangeToIGUIImageButton(btnDMOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnDMCancel = env->addButton(Resize(180, 100, 270, 150), wDMQuery, BUTTON_DM_CANCEL, dataManager.GetSysString(1212));
	scrPackCards = env->addScrollBar(false, Resize(775, 161, 795, 629), 0, SCROLL_FILTER);
	scrPackCards->setLargeStep(1);
	scrPackCards->setSmallStep(1);
	scrPackCards->setVisible(false);
        ChangeToIGUIImageButton(btnDMCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);
	stDBCategory = env->addStaticText(dataManager.GetSysString(1300), Resize(10, 10, 60, 50), false, false, wDeckEdit);
	cbDBCategory = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(0, 0, 0, 0), wDeckEdit, COMBOBOX_DBCATEGORY);
	cbDBCategory->setMaxSelectionRows(15);
	cbDBDecks = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(0, 0, 0, 0), wDeckEdit, COMBOBOX_DBDECKS);
	cbDBDecks->setMaxSelectionRows(15);
	btnSaveDeck = env->addButton(Resize(225, 35, 280, 75), wDeckEdit, BUTTON_SAVE_DECK, dataManager.GetSysString(1302));
        ChangeToIGUIImageButton(btnSaveDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	ebDeckname = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(10, 80, 220, 120), wDeckEdit, -1);
	ebDeckname->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnSaveDeckAs = env->addButton(Resize(225, 80, 280, 120), wDeckEdit, BUTTON_SAVE_DECK_AS, dataManager.GetSysString(1303));
        ChangeToIGUIImageButton(btnSaveDeckAs, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnDeleteDeck = env->addButton(Resize_Y(3 + CARD_IMG_WIDTH, 245, 310, 245 + 40), 0, BUTTON_DELETE_DECK, dataManager.GetSysString(1308));
        ChangeToIGUIImageButton(btnDeleteDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnShuffleDeck = env->addButton(Resize_Y(3 + CARD_IMG_WIDTH, 70, 310, 70 + 40), 0, BUTTON_SHUFFLE_DECK, dataManager.GetSysString(1307));
        ChangeToIGUIImageButton(btnShuffleDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSortDeck = env->addButton(Resize_Y(3 + CARD_IMG_WIDTH, 115, 310, 115 + 40), 0, BUTTON_SORT_DECK, dataManager.GetSysString(1305));
        ChangeToIGUIImageButton(btnSortDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnClearDeck = env->addButton(Resize_Y(3 + CARD_IMG_WIDTH, 200, 310, 200 + 40), 0, BUTTON_CLEAR_DECK, dataManager.GetSysString(1304));
        ChangeToIGUIImageButton(btnClearDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
    btnDeleteDeck->setVisible(false);
    btnShuffleDeck->setVisible(false);
    btnSortDeck->setVisible(false);
    btnClearDeck->setVisible(false);
	btnSideOK = env->addButton(Resize(400, 40, 710, 80), 0, BUTTON_SIDE_OK, dataManager.GetSysString(1334));
        ChangeToIGUIImageButton(btnSideOK, imageManager.tButton_L, imageManager.tButton_L_pressed);
	btnSideOK->setVisible(false);
	btnSideShuffle = env->addButton(Resize(310, 100, 370, 130), 0, BUTTON_SHUFFLE_DECK, dataManager.GetSysString(1307));
        ChangeToIGUIImageButton(btnSideShuffle, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSideShuffle->setVisible(false);
        ChangeToIGUIImageButton(btnSideShuffle, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSideSort = env->addButton(Resize(375, 100, 435, 130), 0, BUTTON_SORT_DECK, dataManager.GetSysString(1305));
        ChangeToIGUIImageButton(btnSideSort, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSideSort->setVisible(false);
        ChangeToIGUIImageButton(btnSideSort, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSideReload = env->addButton(Resize(440, 100, 500, 130), 0, BUTTON_SIDE_RELOAD, dataManager.GetSysString(1309));
        ChangeToIGUIImageButton(btnSideReload, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSideReload->setVisible(false);
	//sort type
	wSort = env->addStaticText(L"", Resize(930, 132, 1020, 156), true, false, 0, -1, true);
	cbSortType = env->addComboBox(Resize(10, 2, 85, 22), wSort, COMBOBOX_SORTTYPE);
	cbSortType->setMaxSelectionRows(10);
	for(int i = 1370; i <= 1373; i++)
		cbSortType->addItem(dataManager.GetSysString(i));
	wSort->setVisible(false);
#ifdef _IRR_ANDROID_PLATFORM_
    //filters
	wFilter = env->addWindow(Resize(610, 1, 1020, 130), false, L"");
	wFilter->getCloseButton()->setVisible(false);
    wFilter->setDrawTitlebar(false);
	wFilter->setDraggable(false);
	wFilter->setVisible(false);
	    ChangeToIGUIImageWindow(wFilter, &bgFilter, imageManager.tDialog_L);
	env->addStaticText(dataManager.GetSysString(1311), Resize(10, 5, 70, 25), false, false, wFilter);
	cbCardType = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(60, 3, 120, 23), wFilter, COMBOBOX_MAINTYPE);
	cbCardType->addItem(dataManager.GetSysString(1310));
	cbCardType->addItem(dataManager.GetSysString(1312));
	cbCardType->addItem(dataManager.GetSysString(1313));
	cbCardType->addItem(dataManager.GetSysString(1314));
	cbCardType2 = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(125, 3, 200, 23), wFilter, COMBOBOX_SECONDTYPE);
	cbCardType2->addItem(dataManager.GetSysString(1310), 0);
	env->addStaticText(dataManager.GetSysString(1315), Resize(205, 5, 280, 25), false, false, wFilter);
	cbLimit = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(260, 3, 390, 23), wFilter, COMBOBOX_LIMIT);
	cbLimit->addItem(dataManager.GetSysString(1310));
	cbLimit->addItem(dataManager.GetSysString(1316));
	cbLimit->addItem(dataManager.GetSysString(1317));
	cbLimit->addItem(dataManager.GetSysString(1318));
	cbLimit->addItem(dataManager.GetSysString(1481));
	cbLimit->addItem(dataManager.GetSysString(1482));
	cbLimit->addItem(dataManager.GetSysString(1483));
	cbLimit->addItem(dataManager.GetSysString(1484));
	cbLimit->addItem(dataManager.GetSysString(1485));
	env->addStaticText(dataManager.GetSysString(1319), Resize(10, 28, 70, 48), false, false, wFilter);
	cbAttribute = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(60, 26, 190, 46), wFilter, COMBOBOX_ATTRIBUTE);
	cbAttribute->setMaxSelectionRows(10);
	cbAttribute->addItem(dataManager.GetSysString(1310), 0);
	for(int filter = 0x1; filter != 0x80; filter <<= 1)
		cbAttribute->addItem(dataManager.FormatAttribute(filter).c_str(), filter);
	env->addStaticText(dataManager.GetSysString(1321), Resize(10, 51, 70, 71), false, false, wFilter);
	cbRace = CAndroidGUIComboBox::addAndroidComboBox(env, Resize(60, 40 + 75 / 6, 190, 60 + 75 / 6), wFilter, COMBOBOX_RACE);
	cbRace->setMaxSelectionRows(10);
	cbRace->addItem(dataManager.GetSysString(1310), 0);
	for(int filter = 0x1; filter < (1 << RACES_COUNT); filter <<= 1)
		cbRace->addItem(dataManager.FormatRace(filter).c_str(), filter);
	env->addStaticText(dataManager.GetSysString(1322), Resize(205, 28, 280, 48), false, false, wFilter);
	ebAttack = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(260, 26, 340, 46), wFilter, EDITBOX_INPUTS);
	ebAttack->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1323), Resize(205, 51, 280, 71), false, false, wFilter);
	ebDefense = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(260, 49, 340, 69), wFilter, EDITBOX_INPUTS);
	ebDefense->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1324), Resize(10, 74, 80, 94), false, false, wFilter);
	ebStar = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(60, 60 + 100 / 6, 100, 80 + 100 / 6), wFilter, EDITBOX_INPUTS);
	ebStar->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1336), Resize(101, 60 + 100 / 6, 150 * xScale, 82 + 100 / 6), false, false, wFilter);
	ebScale = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(150, 60 + 100 / 6, 190, 80 + 100 / 6), wFilter, EDITBOX_INPUTS);
	ebScale->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1325), Resize(205, 60 + 100 / 6, 280, 82 + 100 / 6), false, false, wFilter);
	ebCardName = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(260, 72, 390, 92), wFilter, EDITBOX_KEYWORD);
	ebCardName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnEffectFilter = env->addButton(Resize(345, 28, 390, 69), wFilter, BUTTON_EFFECT_FILTER, dataManager.GetSysString(1326));
		ChangeToIGUIImageButton(btnEffectFilter, imageManager.tButton_C, imageManager.tButton_C_pressed);
	btnStartFilter = env->addButton(Resize(210, 96, 390, 118), wFilter, BUTTON_START_FILTER, dataManager.GetSysString(1327));
		ChangeToIGUIImageButton(btnStartFilter, imageManager.tButton_L, imageManager.tButton_L_pressed);
	if(gameConf.separate_clear_button) {
		btnStartFilter->setRelativePosition(Resize(260, 80 + 125 / 6l, 390, 100 + 125 / 6));
		btnClearFilter = env->addButton(Resize(205, 80 + 125 / 6, 255, 100 + 125 / 6), wFilter, BUTTON_CLEAR_FILTER, dataManager.GetSysString(1304));
			ChangeToIGUIImageButton(btnClearFilter, imageManager.tButton_S, imageManager.tButton_S_pressed);
	}
	wCategories = env->addWindow(Resize(600, 60, 1000, 440), false, L"");
	wCategories->getCloseButton()->setVisible(false);
	wCategories->setDrawTitlebar(false);
	wCategories->setDraggable(false);
	wCategories->setVisible(false);
	    ChangeToIGUIImageWindow(wCategories, &bgCategories, imageManager.tWindow_V);
	btnCategoryOK = env->addButton(Resize(135, 340, 235, 370), wCategories, BUTTON_CATEGORY_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnCategoryOK, imageManager.tButton_L, imageManager.tButton_L_pressed);
	for(int i = 0; i < 32; ++i)
		chkCategory[i] = env->addCheckBox(false, Resize(20 + (i % 4) * 90, 10 + (i / 4) * 40, 120 + (i % 4) * 90, 40 + (i / 4) * 40), wCategories, -1, dataManager.GetSysString(1100 + i));
	scrFilter = env->addScrollBar(false, Resize(980, 159, 1020, 629), 0, SCROLL_FILTER);
	scrFilter->setLargeStep(10);
	scrFilter->setSmallStep(1);
	scrFilter->setVisible(false);
#endif

#ifdef _IRR_ANDROID_PLATFORM_
    //LINK MARKER SEARCH
	btnMarksFilter = env->addButton(Resize(60, 80 + 125 / 6, 190, 100 + 125 / 6), wFilter, BUTTON_MARKS_FILTER, dataManager.GetSysString(1374));
 	   ChangeToIGUIImageButton(btnMarksFilter, imageManager.tButton_L, imageManager.tButton_L_pressed);
	wLinkMarks = env->addWindow(Resize(600, 30, 820, 250), false, L"");
	wLinkMarks->getCloseButton()->setVisible(false);
	wLinkMarks->setDrawTitlebar(false);
	wLinkMarks->setDraggable(false);
	wLinkMarks->setVisible(false);
	    ChangeToIGUIImageWindow(wLinkMarks, &bgLinkMarks, imageManager.tWindow_V);
	btnMarksOK = env->addButton(Resize(80, 80, 140, 140), wLinkMarks, BUTTON_MARKERS_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnMarksOK, imageManager.tButton_C, imageManager.tButton_C_pressed);
	btnMark[0] = env->addButton(Resize(10, 10, 70, 70), wLinkMarks, -1, L"\u2196");
        ChangeToIGUIImageButton(btnMark[0], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[1] = env->addButton(Resize(80, 10, 140, 70), wLinkMarks, -1, L"\u2191");
        ChangeToIGUIImageButton(btnMark[1], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[2] = env->addButton(Resize(150, 10, 210, 70), wLinkMarks, -1, L"\u2197");
        ChangeToIGUIImageButton(btnMark[2], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[3] = env->addButton(Resize(10, 80, 70, 140), wLinkMarks, -1, L"\u2190");
        ChangeToIGUIImageButton(btnMark[3], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[4] = env->addButton(Resize(150, 80, 210, 140), wLinkMarks, -1, L"\u2192");
        ChangeToIGUIImageButton(btnMark[4], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[5] = env->addButton(Resize(10, 150, 70, 210), wLinkMarks, -1, L"\u2199");
        ChangeToIGUIImageButton(btnMark[5], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[6] = env->addButton(Resize(80, 150, 140, 210), wLinkMarks, -1, L"\u2193");
        ChangeToIGUIImageButton(btnMark[6], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	btnMark[7] = env->addButton(Resize(150, 150, 210, 210), wLinkMarks, -1, L"\u2198");
        ChangeToIGUIImageButton(btnMark[7], imageManager.tButton_C, imageManager.tButton_C_pressed, titleFont);
	for(int i=0;i<8;i++)
		btnMark[i]->setIsPushButton(true);
	//replay window
	wReplay = env->addWindow(Resize(220, 100, 800, 520), false, dataManager.GetSysString(1202));
	wReplay->getCloseButton()->setVisible(false);
	wReplay->setDrawBackground(false);
	wReplay->setVisible(false);
	bgReplay = env->addImage(Resize(0, 0, 580, 420), wReplay, -1, 0, true);
	bgReplay->setImage(imageManager.tWindow);
    bgReplay->setScaleImage(true);
	lstReplayList = env->addListBox(Resize(20, 30, 310, 400), wReplay, LISTBOX_REPLAY_LIST, true);
	lstReplayList->setItemHeight(30 * yScale);
	btnLoadReplay = env->addButton(Resize(440, 310, 550, 350), wReplay, BUTTON_LOAD_REPLAY, dataManager.GetSysString(1348));
        ChangeToIGUIImageButton(btnLoadReplay, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnDeleteReplay = env->addButton(Resize(320, 310, 430, 350), wReplay, BUTTON_DELETE_REPLAY, dataManager.GetSysString(1361));
        ChangeToIGUIImageButton(btnDeleteReplay, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnRenameReplay = env->addButton(Resize(320, 360, 430, 400), wReplay, BUTTON_RENAME_REPLAY, dataManager.GetSysString(1362));
        ChangeToIGUIImageButton(btnRenameReplay, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnReplayCancel = env->addButton(Resize(440, 360, 550, 400), wReplay, BUTTON_CANCEL_REPLAY, dataManager.GetSysString(1347));
        ChangeToIGUIImageButton(btnReplayCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);
	env->addStaticText(dataManager.GetSysString(1349), Resize(320, 30, 550, 50), false, true, wReplay);
	stReplayInfo = env->addStaticText(L"", Resize(320, 60, 570, 315), false, true, wReplay);
	env->addStaticText(dataManager.GetSysString(1353), Resize(320, 180, 550, 200), false, true, wReplay);
	ebRepStartTurn = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(320, 210, 430, 250), wReplay, -1);
	ebRepStartTurn->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnExportDeck = env->addButton(Resize(440, 260, 550, 300), wReplay, BUTTON_EXPORT_DECK, dataManager.GetSysString(1369));
        ChangeToIGUIImageButton(btnExportDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnShareReplay = env->addButton(Resize(320, 260, 430, 300), wReplay, BUTTON_SHARE_REPLAY, dataManager.GetSysString(1368));
		ChangeToIGUIImageButton(btnShareReplay, imageManager.tButton_S, imageManager.tButton_S_pressed);
        //single play window
	wSinglePlay = env->addWindow(Resize(220, 100, 800, 520), false, dataManager.GetSysString(1201));
	wSinglePlay->getCloseButton()->setVisible(false);
	wSinglePlay->setDrawBackground(false);
	wSinglePlay->setVisible(false);
    bgSinglePlay = env->addImage(Resize(0, 0, 580, 420), wSinglePlay, -1, 0, true);
    bgSinglePlay->setImage(imageManager.tWindow);
    bgSinglePlay->setScaleImage(true);
    irr::gui::IGUITabControl* wSingle = env->addTabControl(Resize(20, 20, 570, 400), wSinglePlay, false, false);
	wSingle->setTabHeight(40 * yScale);
	//TEST BOT MODE
	if(gameConf.enable_bot_mode) {
		irr::gui::IGUITab* tabBot = wSingle->addTab(dataManager.GetSysString(1380));
		lstBotList = env->addListBox(Resize(0, 0, 300, 330), tabBot, LISTBOX_BOT_LIST, true);
		lstBotList->setItemHeight(30 * yScale);
		btnStartBot = env->addButton(Resize(420, 240, 530, 280), tabBot, BUTTON_BOT_START, dataManager.GetSysString(1211));
            ChangeToIGUIImageButton(btnStartBot, imageManager.tButton_S, imageManager.tButton_S_pressed);
		btnBotCancel = env->addButton(Resize(420, 290, 530, 330), tabBot, BUTTON_CANCEL_SINGLEPLAY, dataManager.GetSysString(1210));
            ChangeToIGUIImageButton(btnBotCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);
		env->addStaticText(dataManager.GetSysString(1382), Resize(310, 10, 500, 30), false, true, tabBot);
		stBotInfo = env->addStaticText(L"", Resize(310, 40, 560, 160), false, true, tabBot);
		cbBotDeckCategory =  env->addComboBox(Resize(0, 0, 0, 0), tabBot, COMBOBOX_BOT_DECKCATEGORY);
		cbBotDeckCategory->setVisible(false);
		cbBotDeck =  env->addComboBox(Resize(0, 0, 0, 0), tabBot);
		cbBotDeck->setVisible(false);
        btnBotDeckSelect = env->addButton(Resize(310, 110, 530, 150), tabBot, BUTTON_BOT_DECK_SELECT, L"");
		btnBotDeckSelect->setVisible(false);
		cbBotRule =  CAndroidGUIComboBox::addAndroidComboBox(env, Resize(310, 160, 530, 200), tabBot, COMBOBOX_BOT_RULE);
		cbBotRule->addItem(dataManager.GetSysString(1262));
		cbBotRule->addItem(dataManager.GetSysString(1263));
		cbBotRule->addItem(dataManager.GetSysString(1264));
		cbBotRule->setSelected(gameConf.default_rule - 3);
		chkBotHand = env->addCheckBox(false, Resize(310, 210, 410, 240), tabBot, -1, dataManager.GetSysString(1384));
		chkBotNoCheckDeck = env->addCheckBox(false, Resize(310, 250, 410, 280), tabBot, -1, dataManager.GetSysString(1229));
		chkBotNoShuffleDeck = env->addCheckBox(false, Resize(310, 290, 410, 320), tabBot, -1, dataManager.GetSysString(1230));
	} else { // avoid null object reference
		btnStartBot = env->addButton(Resize(0, 0, 0, 0), wSinglePlay);
		btnBotCancel = env->addButton(Resize(0, 0, 0, 0), wSinglePlay);
		btnStartBot->setVisible(false);
		btnBotCancel->setVisible(false);
	}
	//SINGLE MODE
	irr::gui::IGUITab* tabSingle = wSingle->addTab(dataManager.GetSysString(1381));
	lstSinglePlayList = env->addListBox(Resize(0, 0, 300, 330), tabSingle, LISTBOX_SINGLEPLAY_LIST, true);
	lstSinglePlayList->setItemHeight(30 * yScale);
	btnLoadSinglePlay = env->addButton(Resize(420, 240, 530, 280), tabSingle, BUTTON_LOAD_SINGLEPLAY, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnLoadSinglePlay, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSinglePlayCancel = env->addButton(Resize(420, 290, 530, 330),tabSingle, BUTTON_CANCEL_SINGLEPLAY, dataManager.GetSysString(1210));
        ChangeToIGUIImageButton(btnSinglePlayCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);
	env->addStaticText(dataManager.GetSysString(1352), Resize(310, 10, 500, 30), false, true, tabSingle);
	stSinglePlayInfo = env->addStaticText(L"", Resize(310, 40, 560, 80), false, true, tabSingle);
	chkSinglePlayReturnDeckTop = env->addCheckBox(false, Resize(310, 200, 560, 230), tabSingle, -1, dataManager.GetSysString(1238));

	//replay save
	wReplaySave = env->addWindow(Resize(470, 180, 860, 360), false, dataManager.GetSysString(1340));
	wReplaySave->getCloseButton()->setVisible(false);
	wReplaySave->setVisible(false);
        ChangeToIGUIImageWindow(wReplaySave, &bgReplaySave, imageManager.tDialog_L);
	env->addStaticText(dataManager.GetSysString(1342), Resize(20, 25, 290, 45), false, false, wReplaySave);
	ebRSName = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(20, 50, 370, 90), wReplaySave, -1);
	ebRSName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnRSYes = env->addButton(Resize(70, 120, 180, 170), wReplaySave, BUTTON_REPLAY_SAVE, dataManager.GetSysString(1341));
        ChangeToIGUIImageButton(btnRSYes, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnRSNo = env->addButton(Resize(210, 120, 320, 170), wReplaySave, BUTTON_REPLAY_CANCEL, dataManager.GetSysString(1212));
        ChangeToIGUIImageButton(btnRSNo, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//replay control
	wReplayControl = env->addWindow(Resize_Y(200, 5, 310, 270), false, L"");
	wReplayControl->getCloseButton()->setVisible(false);
	wReplayControl->setDrawBackground(false);
	wReplayControl->setDraggable(false);
	wReplayControl->setVisible(false);
	btnReplayStart = env->addButton(Resize_Y(0, 0, 110, 40), wReplayControl, BUTTON_REPLAY_START, dataManager.GetSysString(1343));
        ChangeToIGUIImageButton(btnReplayStart, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnReplayPause = env->addButton(Resize_Y(0, 40 + 5, 110, 45 + 40), wReplayControl, BUTTON_REPLAY_PAUSE, dataManager.GetSysString(1344));
        ChangeToIGUIImageButton(btnReplayPause, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnReplayStep = env->addButton(Resize_Y(0, 85 + 5, 110, 90 + 40), wReplayControl, BUTTON_REPLAY_STEP, dataManager.GetSysString(1345));
        ChangeToIGUIImageButton(btnReplayStep, imageManager.tButton_S, imageManager.tButton_S_pressed);
    btnReplayUndo = env->addButton(Resize_Y(0, 130 + 5, 110, 135 + 40), wReplayControl, BUTTON_REPLAY_UNDO, dataManager.GetSysString(1360));
        ChangeToIGUIImageButton(btnReplayUndo, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnReplaySwap = env->addButton(Resize_Y(0, 175 + 5, 110, 180 + 40), wReplayControl, BUTTON_REPLAY_SWAP, dataManager.GetSysString(1346));
        ChangeToIGUIImageButton(btnReplaySwap, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnReplayExit = env->addButton(Resize_Y(0, 220 + 5, 110, 225 + 40), wReplayControl, BUTTON_REPLAY_EXIT, dataManager.GetSysString(1347));
        ChangeToIGUIImageButton(btnReplayExit, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//chat
    imgChat = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(0, 300, 45, 300 + 45), wPallet, BUTTON_CHATTING);
    imgChat->setImageSize(core::dimension2di(28 * yScale, 28 * yScale));
    if (gameConf.chkIgnore1) {
        imgChat->setImage(imageManager.tShut);
    } else {
        imgChat->setImage(imageManager.tTalk);
    }
	wChat = env->addWindow(Resize(305, 605, 1020, 640), false, L"");
	wChat->getCloseButton()->setVisible(false);
	wChat->setDraggable(false);
	wChat->setDrawTitlebar(false);
	wChat->setVisible(false);
	ebChatInput = CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(3, 2, 710, 28), wChat, EDITBOX_CHAT);
	//swap
	btnSpectatorSwap = env->addButton(Resize_Y(3 + CARD_IMG_WIDTH, 70, 310, 70 + 40), 0, BUTTON_REPLAY_SWAP, dataManager.GetSysString(1346));
        ChangeToIGUIImageButton(btnSpectatorSwap, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnSpectatorSwap->setVisible(false);
	//chain buttons
	btnChainIgnore = env->addButton(Resize_Y(3 + CARD_IMG_WIDTH, 70, 310, 70 + 40), 0, BUTTON_CHAIN_IGNORE, dataManager.GetSysString(1292));
        ChangeToIGUIImageButton(btnChainIgnore, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnChainAlways = env->addButton(Resize_Y(3 + CARD_IMG_WIDTH, 115, 310, 115 + 40), 0, BUTTON_CHAIN_ALWAYS, dataManager.GetSysString(1293));
        ChangeToIGUIImageButton(btnChainAlways, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnChainWhenAvail = env->addButton(Resize_Y(3 + CARD_IMG_WIDTH, 160, 310, 160 + 40), 0, BUTTON_CHAIN_WHENAVAIL, dataManager.GetSysString(1294));
        ChangeToIGUIImageButton(btnChainWhenAvail, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnChainIgnore->setIsPushButton(true);
	btnChainAlways->setIsPushButton(true);
	btnChainWhenAvail->setIsPushButton(true);
	btnChainIgnore->setVisible(false);
	btnChainAlways->setVisible(false);
	btnChainWhenAvail->setVisible(false);
	//shuffle
	btnShuffle = env->addButton(Resize_Y(3 + CARD_IMG_WIDTH, 205, 310, 205 + 40), 0, BUTTON_CMD_SHUFFLE, dataManager.GetSysString(1297));
        ChangeToIGUIImageButton(btnShuffle, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnShuffle->setVisible(false);
	//cancel or finish
	btnCancelOrFinish = env->addButton(Resize_Y(3 + CARD_IMG_WIDTH, 205, 310, 255), 0, BUTTON_CANCEL_OR_FINISH, dataManager.GetSysString(1295));
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
	wBigCard = env->addWindow(Resize(0, 0, 0, 0), false, L"");
	wBigCard->getCloseButton()->setVisible(false);
	wBigCard->setDrawTitlebar(false);
	wBigCard->setDrawBackground(false);
	wBigCard->setVisible(false);
	imgBigCard = env->addImage(Resize(0, 0, 0, 0), wBigCard);
	imgBigCard->setScaleImage(false);
	imgBigCard->setUseAlphaChannel(true);
	btnBigCardOriginalSize = env->addButton(Resize_Y(200, 100, 305, 135), 0, BUTTON_BIG_CARD_ORIG_SIZE, dataManager.GetSysString(1443));
	btnBigCardZoomIn = env->addButton(Resize_Y(200, 140, 305, 175), 0, BUTTON_BIG_CARD_ZOOM_IN, dataManager.GetSysString(1441));
	btnBigCardZoomOut = env->addButton(Resize_Y(200, 180, 305, 215), 0, BUTTON_BIG_CARD_ZOOM_OUT, dataManager.GetSysString(1442));
	btnBigCardClose = env->addButton(Resize_Y(200, 220, 305, 275), 0, BUTTON_BIG_CARD_CLOSE, dataManager.GetSysString(1440));
	btnBigCardOriginalSize->setVisible(false);
	btnBigCardZoomIn->setVisible(false);
	btnBigCardZoomOut->setVisible(false);
	btnBigCardClose->setVisible(false);
	//leave/surrender/exit
	btnLeaveGame = env->addButton(Resize_Y(3 + CARD_IMG_WIDTH, 1, 310, 51), 0, BUTTON_LEAVE_GAME, L"");
        ChangeToIGUIImageButton(btnLeaveGame, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnLeaveGame->setVisible(false);
	//tip
	stTip = env->addStaticText(L"", Resize(0, 0, 150, 150), false, true, 0, -1, true);
	stTip->setBackgroundColor(0xc011113d);
	stTip->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	stTip->setVisible(false);
	//tip for cards in select / display list
	stCardListTip = env->addStaticText(L"", Resize(0, 0, 150, 150), false, true, wCardSelect, TEXT_CARD_LIST_TIP, true);
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
IGUIStaticText *text = env->addStaticText(L"", rect<s32>(1,1,100,45), false, false, 0, GUI_INFO_FPS);
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
    // get FPS
	IGUIElement *stat = device->getGUIEnvironment()->getRootGUIElement()->getElementFromId ( GUI_INFO_FPS );
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
			ALOGD("cc game: WARNING: Pixel shaders disabled "
					"because of missing driver/hardware support.");
			psFileName = "";
		}
		if (!driver->queryFeature(video::EVDF_VERTEX_SHADER_1_1) &&
				!driver->queryFeature(video::EVDF_ARB_VERTEX_PROGRAM_1))
		{
			ALOGD("cc game: WARNING: Vertex shaders disabled "
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
			ALOGD("cc game:ogles2Sold = %d", ogles2Solid);
			ALOGD("cc game:ogles2BlendTexture = %d", ogles2BlendTexture);
			ALOGD("cc game:ogles2TrasparentAlpha = %d", ogles2TrasparentAlpha);
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
		//ALOGV("cc game draw frame");
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
			driver->draw2DImage(imageManager.tBackGround, Resize(0, 0, GAME_WIDTH, GAME_HEIGHT), recti(0, 0, imageManager.tBackGround->getOriginalSize().Width, imageManager.tBackGround->getOriginalSize().Height));
		}
		if(imageManager.tBackGround_menu) {
			driver->draw2DImage(imageManager.tBackGround_menu, Resize(0, 0, GAME_WIDTH, GAME_HEIGHT), recti(0, 0, imageManager.tBackGround->getOriginalSize().Width, imageManager.tBackGround->getOriginalSize().Height));
		}
		if(imageManager.tBackGround_deck) {
			driver->draw2DImage(imageManager.tBackGround_deck, Resize(0, 0, GAME_WIDTH, GAME_HEIGHT), recti(0, 0, imageManager.tBackGround->getOriginalSize().Width, imageManager.tBackGround->getOriginalSize().Height));
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

	for(size_t i = 0; text[i] != 0 && i < std::wcslen(text); ++i) {
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
			if (IsExtension(fname, L".cdb")) {
				dataManager.LoadDB(fname);
				continue;
			}
			if (IsExtension(fname, L".conf")) {
				IReadFile* reader = DataManager::FileSystem->createAndOpenFile(uname);
				dataManager.LoadStrings(reader);
				continue;
			}
			if (!mywcsncasecmp(fname, L"pack/", 5) && IsExtension(fname, L".ydk")) {
				deckBuilder.expansionPacks.push_back(fname);
				continue;
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
			if(!std::wcscmp(cbCategory->getItem(i), gameConf.lastcategory)) {
				cbCategory->setSelected(i);
				break;
			}
		}
	}
	RefreshDeck(cbCategory, cbDeck);
	if(selectlastused) {
		for(size_t i = 0; i < cbDeck->getItemCount(); ++i) {
			if(!std::wcscmp(cbDeck->getItem(i), gameConf.lastdeck)) {
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
	if(!mywcsncasecmp(deckpath, L"./pack", 6)) {
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
	FILE* fp = std::fopen("bot.conf", "r");
	char linebuf[256]{};
	char strbuf[256]{};
	if(fp) {
		while(std::fgets(linebuf, 256, fp)) {
			if(linebuf[0] == '#')
				continue;
			if(linebuf[0] == '!') {
				BotInfo newinfo;
				if (std::sscanf(linebuf, "!%240[^\n]", strbuf) != 1)
					continue;
				BufferIO::DecodeUTF8(strbuf, newinfo.name);
				if (!std::fgets(linebuf, 256, fp))
					break;
				if (std::sscanf(linebuf, "%240[^\n]", strbuf) != 1)
					continue;
				BufferIO::DecodeUTF8(strbuf, newinfo.command);
				if (!std::fgets(linebuf, 256, fp))
					break;
				if (std::sscanf(linebuf, "%240[^\n]", strbuf) != 1)
					continue;
				BufferIO::DecodeUTF8(strbuf, newinfo.desc);
				if (!std::fgets(linebuf, 256, fp))
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
		std::fclose(fp);
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
		stSetName->setRelativePosition(Resize(10, 83, 250, 106));
		stText->setRelativePosition(Resize(10, 83 + offset, 251, 340));
		scrCardText->setRelativePosition(Resize(255, 83 + offset, 258, 340));
	}
	else {
		if (is_valid)
			myswprintf(formatBuffer, L"[%ls]", dataManager.FormatType(cit->second.type).c_str());
		else
			myswprintf(formatBuffer, L"[%ls]", dataManager.unknown_string);
		stInfo->setText(formatBuffer);
		stDataInfo->setText(L"");
		stSetName->setRelativePosition(Resize(10, 60, 250, 106));
		stText->setRelativePosition(Resize(10, 60 + offset, 251, 340));
		scrCardText->setRelativePosition(Resize(255, 60 + offset, 258, 340));
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
		std::snprintf(msgbuf, sizeof msgbuf, "[Script Error]: %s", msg);
		ErrorLog(msgbuf);
	}
}
void Game::ErrorLog(const char* msg) {
	FILE* fp = std::fopen("error.log", "at");
	if(!fp)
		return;
	time_t nowtime = std::time(nullptr);
	char timebuf[40];
	std::strftime(timebuf, sizeof timebuf, "%Y-%m-%d %H:%M:%S", std::localtime(&nowtime));
	std::fprintf(fp, "[%s]%s\n", timebuf, msg);
	std::fclose(fp);
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
	s32 x = 305;
	if(is_building) x = 802;
	wChat->setRelativePosition(Resize(x, GAME_HEIGHT - 35, GAME_WIDTH - 4, GAME_HEIGHT));
	ebChatInput->setRelativePosition(recti(3 * xScale, 2 * yScale, (GAME_WIDTH - 6) * xScale - wChat->getRelativePosition().UpperLeftCorner.X, 28 * yScale));
}
recti Game::Resize(s32 x, s32 y, s32 x2, s32 y2) {
	x = x * xScale;
	y = y * yScale;
	x2 = x2 * xScale;
	y2 = y2 * yScale;
	return recti(x, y, x2, y2);
}
recti Game::Resize(s32 x, s32 y, s32 x2, s32 y2, s32 dx, s32 dy, s32 dx2, s32 dy2) {
	x = x * xScale + dx;
	y = y * yScale + dy;
	x2 = x2 * xScale + dx2;
	y2 = y2 * yScale + dy2;
	return recti(x, y, x2, y2);
}
irr::core::vector2di Game::Resize(s32 x, s32 y) {
	x = x * xScale;
	y = y * yScale;
	return irr::core::vector2di(x, y);
}
irr::core::vector2di Game::ResizeReverse(s32 x, s32 y) {
	x = x / xScale;
	y = y / yScale;
	return irr::core::vector2di(x, y);
}
recti Game::ResizeWin(s32 x, s32 y, s32 x2, s32 y2) {
	s32 w = x2 - x;
	s32 h = y2 - y;
	x = (x + w / 2) * xScale - w / 2;
	y = (y + h / 2) * yScale - h / 2;
	x2 = w + x;
	y2 = h + y;
	return recti(x, y, x2, y2);
}
recti Game::ResizePhaseHint(s32 x, s32 y, s32 x2, s32 y2, s32 width) {
	x = x * xScale - width / 2;
	y = y * yScale;
	x2 = x2 * xScale;
	y2 = y2 * yScale;
	return recti(x, y, x2, y2);
}
recti Game::Resize_Y(s32 x, s32 y, s32 x2, s32 y2) {
    x = x * yScale;
    y = y * yScale;
    x2 = x2 * yScale;
    y2 = y2 * yScale;
	return recti(x, y, x2, y2);
}
irr::core::vector2di Game::Resize_Y(s32 x, s32 y) {
    x = x * yScale;
    y = y * yScale;
	return irr::core::vector2di(x, y);
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
