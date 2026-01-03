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
#include <string>
#include <regex>

#include <android/CAndroidGUIEditBox.h>
#include <android/CAndroidGUIComboBox.h>
#include <android/CAndroidGUISkin.h>
#include <Android/CIrrDeviceAndroid.h>
#include <COGLES2ExtensionHandler.h>
#include <COGLESExtensionHandler.h>
#include <COGLES2Driver.h>
#include <COGLESDriver.h>

namespace ygo {

Game* mainGame;

/**
 * @brief 清空决斗信息结构体中的所有数据
 *
 * 该函数将DuelInfo结构体中的所有成员变量重置为初始状态，
 * 包括决斗状态标志、玩家生命值、回合信息、时间限制等。
 *
 * @param this 指向DuelInfo对象的指针
 * @return 无返回值
 */
void DuelInfo::Clear() {
	// 重置决斗状态标志
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

	// 重置生命值相关数据
	lp[0] = 0;
	lp[1] = 0;
	start_lp = 0;

	// 重置决斗规则和回合信息
	duel_rule = 0;
	turn = 0;
	curMsg = 0;

	// 清空主机名和客户端名称
	hostname[0] = 0;
	clientname[0] = 0;
	hostname_tag[0] = 0;
	clientname_tag[0] = 0;
	strLP[0][0] = 0;
	strLP[1][0] = 0;

	// 重置玩家类型和时间限制
	player_type = 0;
	time_player = 0;
	time_limit = 0;
	time_left[0] = 0;
	time_left[1] = 0;
}

/**
 * @brief 处理游戏事件
 * @param event 输入事件引用，包含鼠标或键盘等输入信息
 *
 * 该函数主要用于处理鼠标输入事件，对鼠标的坐标进行优化处理。
 * 当事件类型为鼠标输入时，会获取当前鼠标坐标并调用optX和optY函数
 * 对坐标进行转换或优化，然后更新鼠标输入事件中的坐标值。
 */
void Game::process(irr::SEvent &event) {
	// 检查事件类型是否为鼠标输入事件
	if (event.EventType == irr::EET_MOUSE_INPUT_EVENT) {
        irr::s32 x = event.MouseInput.X;
        irr::s32 y = event.MouseInput.Y;
		// 对鼠标坐标进行优化处理并更新事件中的坐标值
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

bool Game::Initialize(ANDROID_APP app, irr::android::InitOptions *options) {
	// 保存应用程序句柄
	this->appMain = app;
	// 初始化随机数种子
	srand(time(0));
	// 创建Irrlicht引擎参数结构体
	irr::SIrrlichtCreationParameters params{};
	// 获取OpenGL版本设置
	glversion = options->getOpenglVersion();
	// 根据OpenGL版本选择渲染驱动类型
	if (glversion == 0) {
		params.DriverType = irr::video::EDT_OGLES1;
	} else{
		params.DriverType = irr::video::EDT_OGLES2;
	}
	// 设置应用程序私有数据
	params.PrivateData = app;
	// 设置颜色深度为24位
	params.Bits = 24;
	// 设置Z缓冲区深度为16位
	params.ZBufferBits = 16;
	// 关闭抗锯齿
	params.AntiAlias  = 1;
	// 设置窗口大小为自适应
	params.WindowSize = irr::core::dimension2d<irr::u32>(0, 0);

	// 创建Irrlicht设备实例
	device = irr::createDeviceEx(params);
	// 检查设备创建是否成功
	if(!device) {
		ErrorLog("Failed to create Irrlicht Engine device!");
		return false;
	}
	// 执行Android平台特定的初始化技巧
	if (!irr::android::perfromTrick(app)) {
		return false;
	}
	// 初始化Java桥接并获取应用位置信息
	irr::core::vector2di appPosition = irr::android::initJavaBridge(app, device);
	// 设置位置修正参数
	setPositionFix(appPosition);
	// 设置进程接收器
	device->setProcessReceiver(this);

	// 设置输入事件处理函数
	app->onInputEvent = irr::android::handleInput;
    // 获取日志记录器
    irr::ILogger* logger = device->getLogger();
//	logger->setLogLevel(ELL_WARNING);
	// 检查是否启用钟摆刻度显示
	isPSEnabled = options->isPendulumScaleEnabled();

	// 获取文件系统并设置给数据管理器
	dataManager.FileSystem = device->getFileSystem();
    // 设置应用命令回调函数
    ((irr::CIrrDeviceAndroid*)device)->onAppCmd = onHandleAndroidCommand;

	// 获取屏幕缩放比例
	xScale = irr::android::getXScale(app);
	yScale = irr::android::getYScale(app);

	ALOGD("cc game: xScale = %f, yScale = %f", xScale, yScale);

    // 重置卡片顶点数据大小
    SetCardS3DVertex();
	// 获取工作目录路径
    irr::io::path workingDir = options->getWorkDir();
    ALOGD("cc game: workingDir= %s", workingDir.c_str());
	// 更改工作目录
	dataManager.FileSystem->changeWorkingDirectoryTo(workingDir);

	/* Your media must be somewhere inside the assets folder. The assets folder is the root for the file system.
	 This example copies the media in the Android.mk makefile. */
    // 定义媒体资源路径
    irr::core::stringc mediaPath = "media/";

	// The Android assets file-system does not know which sub-directories it has (blame google).
	// So we have to add all sub-directories in assets manually. Otherwise we could still open the files,
	// but existFile checks will fail (which are for example needed by getFont).
	// 遍历文件档案，为Android资产文件系统添加目录列表
	for ( irr::u32 i=0; i < dataManager.FileSystem->getFileArchiveCount(); ++i )
	{
		IFileArchive* archive = dataManager.FileSystem->getFileArchive(i);
		if ( archive->getType() == EFAT_ANDROID_ASSET )
		{
			archive->addDirectoryToFileList(mediaPath);
			break;
		}
	}
	// 加载ZIP压缩包资源
	irr::io::path* zips = options->getArchiveFiles();
	int len = options->getArchiveCount();
	for(int i=0;i<len;i++){
		irr::io::path zip_path = zips[i];
		// 添加文件档案
		if(dataManager.FileSystem->addFileArchive(zip_path.c_str(), false, false, EFAT_ZIP)) {
		    ALOGD("cc game: add arrchive ok:%s", zip_path.c_str());
	    }else{
			ALOGW("cc game: add arrchive fail:%s", zip_path.c_str());
		}
	}
	// 初始化各种游戏状态变量
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
	// 获取视频驱动和场景管理器
	driver = device->getVideoDriver();

	// 获取卡片质量设置
	int quality = options->getCardQualityOp();
	// 检查是否支持非2次幂纹理
	if (driver->getDriverType() == irr::video::EDT_OGLES2) {
		isNPOTSupported = ((irr::video::COGLES2Driver *) driver)->queryOpenGLFeature(irr::video::COGLES2ExtensionHandler::IRR_OES_texture_npot);
	} else {
		isNPOTSupported = ((irr::video::COGLES1Driver *) driver)->queryOpenGLFeature(irr::video::COGLES1ExtensionHandler::IRR_OES_texture_npot);
	}
	ALOGD("cc game: isNPOTSupported = %d", isNPOTSupported);
	// 根据纹理支持情况设置纹理创建标志
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

	// 设置纹理优化质量标志
	driver->setTextureCreationFlag(irr::video::ETCF_OPTIMIZED_FOR_QUALITY, true);
	// 初始化图像管理器
	imageManager.SetDevice(device);
	imageManager.ClearTexture();
	// 初始化图像资源
	if(!imageManager.Initial(workingDir)) {
		ErrorLog("Failed to load textures!");
		return false;
	}
	// 加载数据库文件
	irr::io::path* cdbs = options->getDBFiles();
	len = options->getDbCount();
	for(int i=0;i<len;i++){
		irr::io::path cdb_path = cdbs[i];
		wchar_t wpath[1024];
		// 解码UTF8路径
		BufferIO::DecodeUTF8(cdb_path.c_str(), wpath);
		// 加载数据库
		if(dataManager.LoadDB(wpath)) {
		    ALOGD("cc game: add cdb ok:%s", cdb_path.c_str());
	    }else{
			ALOGW("cc game: add cdb fail:%s", cdb_path.c_str());
		}
	}
	// 加载字符串配置文件
	if(dataManager.LoadStrings((workingDir + path("/expansions/strings.conf")).c_str())){
		ALOGD("cc game: loadStrings expansions/strings.conf");
	}
	if(!dataManager.LoadStrings((workingDir + path("/strings.conf")).c_str())) {
		ErrorLog("Failed to load strings!");
		return false;
	}
    // 加载配置文件
    LoadConfig();
    // 加载扩展卡包
    LoadExpansions();
    // 加载禁限卡表
    deckManager.LoadLFList(options);
	// 获取GUI环境
	env = device->getGUIEnvironment();
	// 检查是否启用字体抗锯齿
	bool isAntialias = options->isFontAntiAliasEnabled();
	// 创建各种字体
	numFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.numfont, 18 * yScale, isAntialias, false);
	adFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.numfont, 12 * yScale, isAntialias, false);
	lpcFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.numfont, 48 * yScale, isAntialias, true);
	guiFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.textfont, 18 * yScale, isAntialias, true);
    titleFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.textfont, 32 * yScale, isAntialias, true);
	textFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.textfont, (int)gameConf.textfontsize * yScale, isAntialias, true);
	miniFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.textfont, 8 * yScale, isAntialias, true);//最小的文字，用于genesys点数图标
    icFont = irr::gui::CGUITTFont::createTTFont(env, gameConf.textfont, 14 * yScale, isAntialias, true);// 图标数字，用于禁限①②的图标
	// 检查字体创建是否成功
	if(!numFont || !guiFont) {
	  ALOGW("cc game: add font fail ");
	}
	// 获取场景管理器
	smgr = device->getSceneManager();
	// 设置窗口标题
	device->setWindowCaption(L"[---]");
	// 禁止窗口大小调整
	device->setResizable(false);
	// 创建并设置新的GUI皮肤
	irr::gui::IGUISkin* newskin = irr::gui::CAndroidGUISkin::createAndroidSkin(irr::gui::EGST_BURNING_SKIN, driver, env, xScale, yScale);
	newskin->setFont(guiFont);
	env->setSkin(newskin);
	// 释放皮肤资源
	newskin->drop();

	//main menu
	wMainMenu = env->addWindow(Resize(450, 40, 900, 600), false, L"");
	wMainMenu->getCloseButton()->setVisible(false);
	wMainMenu->setDrawBackground(false);
    //button Lan Mode
	btnLanMode = irr::gui::CGUIImageButton::addImageButton(env, Resize(15, 30, 350, 106), wMainMenu, BUTTON_LAN_MODE);
	btnLanMode->setImageSize(irr::core::dimension2di(400 * yScale, 76 * yScale));
	btnLanMode->setDrawBorder(false);
	btnLanMode->setImage(imageManager.tTitleBar);
    textLanMode = env->addStaticText(dataManager.GetSysString(1200)/*本地联机*/, Resize(15, 25, 350, 60), false, false, btnLanMode);
    textLanMode->setOverrideFont(titleFont);
    textLanMode->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    //version code
	wchar_t strbuf[256];
	myswprintf(strbuf, L"YGOPro Version:%X.0%X.%X", (PRO_VERSION & 0xf000U) >> 12, (PRO_VERSION & 0x0ff0U) >> 4, PRO_VERSION & 0x000fU);
	env->addStaticText(strbuf, Resize(55, 2, 280, 35), false, false, btnLanMode);
    //button Single Mode
	btnSingleMode = irr::gui::CGUIImageButton::addImageButton(env,  Resize(15, 110, 350, 186), wMainMenu, BUTTON_SINGLE_MODE);
	btnSingleMode->setImageSize(irr::core::dimension2di(400 * yScale, 76 * yScale));
	btnSingleMode->setDrawBorder(false);
    btnSingleMode->setImage(imageManager.tTitleBar);
	textSingleMode = env->addStaticText(dataManager.GetSysString(1201)/*单人游戏*/, Resize(15, 25, 350, 60), false, false, btnSingleMode);
	textSingleMode->setOverrideFont(titleFont);
    textSingleMode->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    //button Replay Mode
	btnReplayMode = irr::gui::CGUIImageButton::addImageButton(env, Resize(15, 190, 350, 266), wMainMenu, BUTTON_REPLAY_MODE);
    btnReplayMode->setImageSize(irr::core::dimension2di(400 * yScale, 76 * yScale));
    btnReplayMode->setDrawBorder(false);
    btnReplayMode->setImage(imageManager.tTitleBar);
	textReplayMode = env->addStaticText(dataManager.GetSysString(1202)/*观看录像*/, Resize(15, 25, 350, 60), false, false, btnReplayMode);
	textReplayMode->setOverrideFont(titleFont);
	textReplayMode->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    //button Deck Edit
	btnDeckEdit = irr::gui::CGUIImageButton::addImageButton(env, Resize(15, 270, 350, 346), wMainMenu, BUTTON_DECK_EDIT);
    btnDeckEdit->setImageSize(irr::core::dimension2di(400 * yScale, 76 * yScale));
    btnDeckEdit->setDrawBorder(false);
    btnDeckEdit->setImage(imageManager.tTitleBar);
	textDeckEdit = env->addStaticText(dataManager.GetSysString(1204)/*编辑卡组*/, Resize(15, 25, 350, 60), false, false, btnDeckEdit);
	textDeckEdit->setOverrideFont(titleFont);
	textDeckEdit->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    //button Settings
    btnSettings = irr::gui::CGUIImageButton::addImageButton(env, Resize(15, 350, 350, 426), wMainMenu, BUTTON_SETTINGS);
    btnSettings->setImageSize(irr::core::dimension2di(400 * yScale, 76 * yScale));
	btnSettings->setDrawBorder(false);
	btnSettings->setImage(imageManager.tTitleBar);
	textSettings = env->addStaticText(dataManager.GetSysString(1273)/*系统设定*/, Resize(15, 25, 350, 60), false, false, btnSettings);
	textSettings->setOverrideFont(titleFont);
	textSettings->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    //button Exit
    btnModeExit = irr::gui::CGUIImageButton::addImageButton(env, Resize(15, 430, 350, 506), wMainMenu, BUTTON_MODE_EXIT);
    btnModeExit->setImageSize(irr::core::dimension2di(400 * yScale, 76 * yScale));
	btnModeExit->setDrawBorder(false);
	btnModeExit->setImage(imageManager.tTitleBar);
	textModeExit = env->addStaticText(dataManager.GetSysString(1210)/*退出*/, Resize(15, 25, 350, 65), false, false, btnModeExit);
	textModeExit->setOverrideFont(titleFont);
	textModeExit->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);

    //---------------------game windows---------------------
    //lan mode
	wLanWindow = env->addWindow(Resize(220, 100, 800, 520), false, dataManager.GetSysString(1200)/*本地联机*/);
	wLanWindow->getCloseButton()->setVisible(false);
	wLanWindow->setVisible(false);
    ChangeToIGUIImageWindow(wLanWindow, &bgLanWindow, imageManager.tWindow);
	env->addStaticText(dataManager.GetSysString(1220)/*昵称：*/, Resize(30, 30, 70, 70), false, false, wLanWindow);
	ebNickName = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(gameConf.nickname, true, env, Resize(110, 25, 420, 65), wLanWindow);
	ebNickName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	lstHostList = env->addListBox(Resize(30, 75, 540, 240), wLanWindow, LISTBOX_LAN_HOST, true);
	lstHostList->setItemHeight(30 * yScale);
	btnLanRefresh = env->addButton(Resize(205, 250, 315, 290), wLanWindow, BUTTON_LAN_REFRESH, dataManager.GetSysString(1217));
		ChangeToIGUIImageButton(btnLanRefresh, imageManager.tButton_S, imageManager.tButton_S_pressed);
	env->addStaticText(dataManager.GetSysString(1221)/*主机信息：*/, Resize(30, 305, 100, 340), false, false, wLanWindow);
	ebJoinHost = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(gameConf.lasthost, true, env, Resize(110, 300, 270, 340), wLanWindow);
	ebJoinHost->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	ebJoinPort = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(gameConf.lastport, true, env, Resize(280, 300, 340, 340), wLanWindow);
	ebJoinPort->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1222)/*房间密码：*/, Resize(30, 355, 100, 390), false, false, wLanWindow);
	ebJoinPass = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(gameConf.roompass, true, env, Resize(110, 350, 340, 390), wLanWindow);
	ebJoinPass->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnJoinHost = env->addButton(Resize(430, 300, 540, 340), wLanWindow, BUTTON_JOIN_HOST, dataManager.GetSysString(1223)/*加入游戏*/);
		ChangeToIGUIImageButton(btnJoinHost, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnJoinCancel = env->addButton(Resize(430, 350, 540, 390), wLanWindow, BUTTON_JOIN_CANCEL, dataManager.GetSysString(1212)/*取消*/);
		ChangeToIGUIImageButton(btnJoinCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnCreateHost = env->addButton(Resize(430, 25, 540, 65), wLanWindow, BUTTON_CREATE_HOST, dataManager.GetSysString(1224)/*局域网建主*/);
		ChangeToIGUIImageButton(btnCreateHost, imageManager.tButton_S, imageManager.tButton_S_pressed);

	//create host
	wCreateHost = env->addWindow(Resize(220, 100, 800, 520), false, dataManager.GetSysString(1224)/*局域网建主*/);
	wCreateHost->getCloseButton()->setVisible(false);
	wCreateHost->setVisible(false);
        ChangeToIGUIImageWindow(wCreateHost, &bgCreateHost, imageManager.tWindow);
    env->addStaticText(dataManager.GetSysString(1226)/*禁限卡表：*/, Resize(20, 30, 90, 65), false, false, wCreateHost);
    // 局域网建主的禁卡表选择combobox
    cbHostLFlist = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(110, 25, 260, 65), wCreateHost);
	for(unsigned int i = 0; i < deckManager._lfList.size(); ++i) {
        cbHostLFlist->addItem(deckManager._lfList[i].listName.c_str(), deckManager._lfList[i].hash);
        if(!wcscmp(deckManager._lfList[i].listName.c_str(), gameConf.last_limit_list_name)) {//找到名称相同时找到对应的index值作为默认值
            gameConf.default_lflist = i;
        }
    }
    cbHostLFlist->setVisible(!gameConf.enable_genesys_mode);
    cbHostLFlist->setSelected(gameConf.use_lflist ? gameConf.default_lflist : cbHostLFlist->getItemCount() - 1);// 设置默认选中的禁限卡表
    // 局域网建主的genesys禁卡表选择combobox
    cbHostGenesysLFlist = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(110, 25, 260, 65), wCreateHost);
	for(unsigned int i = 0; i < deckManager._genesys_lfList.size(); ++i) {
        cbHostGenesysLFlist->addItem(deckManager._genesys_lfList[i].listName.c_str(), deckManager._genesys_lfList[i].hash);
        if(!wcscmp(deckManager._genesys_lfList[i].listName.c_str(), gameConf.last_genesys_limit_list_name)) {//找到名称相同时找到对应的index值作为默认值
            gameConf.default_genesys_lflist = i;
        }
    }
    cbHostGenesysLFlist->setVisible(gameConf.enable_genesys_mode);
    cbHostGenesysLFlist->setSelected(gameConf.use_genesys_lflist ? gameConf.default_genesys_lflist : cbHostGenesysLFlist->getItemCount() - 1);// 设置默认选中的禁限卡表
    // 局域网建主的卡片允许的选择combobox
	env->addStaticText(dataManager.GetSysString(1225)/*卡片允许：*/, Resize(20, 75, 100, 110), false, false, wCreateHost);
	cbRule = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(110, 75, 260, 115), wCreateHost);
	cbRule->setMaxSelectionRows(10);
	cbRule->addItem(dataManager.GetSysString(1481));// ＯＣＧ
	cbRule->addItem(dataManager.GetSysString(1482));// ＴＣＧ
	cbRule->addItem(dataManager.GetSysString(1483));// 简体中文
	cbRule->addItem(dataManager.GetSysString(1484));// 自定义卡片
	cbRule->addItem(dataManager.GetSysString(1485));// 无独有卡
	cbRule->addItem(dataManager.GetSysString(1486));// 所有卡片
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
    // 局域网建主的决斗模式的选择combobox
	env->addStaticText(dataManager.GetSysString(1227)/*决斗模式：*/, Resize(20, 130, 100, 165), false, false, wCreateHost);
	cbMatchMode = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(110, 125, 260, 165), wCreateHost);
	cbMatchMode->addItem(dataManager.GetSysString(1244));// 单局模式
	cbMatchMode->addItem(dataManager.GetSysString(1245));// 比赛模式
	cbMatchMode->addItem(dataManager.GetSysString(1246));// TAG
	env->addStaticText(dataManager.GetSysString(1237)/*每回合时间：*/, Resize(20, 180, 100, 215), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 180);
	ebTimeLimit = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, Resize(110, 175, 260, 215), wCreateHost);
	ebTimeLimit->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1236)/*规则：*/, Resize(270, 30, 350, 65), false, false, wCreateHost);
	cbDuelRule = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(360, 25, 510, 65), wCreateHost);
	cbDuelRule->addItem(dataManager.GetSysString(1260));// 大师规则
	cbDuelRule->addItem(dataManager.GetSysString(1261));// 大师规则２
	cbDuelRule->addItem(dataManager.GetSysString(1262));// 大师规则３
	cbDuelRule->addItem(dataManager.GetSysString(1263));// 新大师规则（2017）
	cbDuelRule->addItem(dataManager.GetSysString(1264));// 大师规则（2020）
	cbDuelRule->setSelected(gameConf.default_rule - 1);
	chkNoCheckDeck = env->addCheckBox(false, Resize(250, 235, 350, 275), wCreateHost, -1, dataManager.GetSysString(1229));
	chkNoShuffleDeck = env->addCheckBox(false, Resize(360, 235, 460, 275), wCreateHost, -1, dataManager.GetSysString(1230));
	env->addStaticText(dataManager.GetSysString(1231), Resize(270, 80, 350, 310), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 8000);
	ebStartLP = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, Resize(360, 75, 510, 115), wCreateHost);
	ebStartLP->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1232), Resize(270, 130, 350, 165), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 5);
	ebStartHand = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, Resize(360, 125, 510, 165), wCreateHost);
	ebStartHand->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1233), Resize(270, 180, 350, 215), false, false, wCreateHost);
	myswprintf(strbuf, L"%d", 1);
	ebDrawCount = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(strbuf, true, env, Resize(360, 175, 510, 215), wCreateHost);
	ebDrawCount->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1234), Resize(20, 305, 100, 340), false, false, wCreateHost);
	ebServerName = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(gameConf.gamename, true, env, Resize(110, 300, 340, 340), wCreateHost);
	ebServerName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	env->addStaticText(dataManager.GetSysString(1235), Resize(20, 355, 100, 390), false, false, wCreateHost);
	ebServerPass = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(110, 350, 340, 390), wCreateHost);
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
		stHostPrepDuelist[i]->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
		btnHostPrepKick[i] = irr::gui::CGUIImageButton::addImageButton(env, Resize(10, 80 + i * 45, 50, 120 + i * 45), wHostPrepare, BUTTON_HP_KICK);
        btnHostPrepKick[i]->setImageSize(irr::core::dimension2di(40 * yScale, 40 * yScale));
        btnHostPrepKick[i]->setDrawBorder(false);
        btnHostPrepKick[i]->setImage(imageManager.tClose);
		chkHostPrepReady[i] = env->addCheckBox(false, Resize(270, 80 + i * 45, 310, 120 + i * 45), wHostPrepare, CHECKBOX_HP_READY, L"");
		chkHostPrepReady[i]->setEnabled(false);
	}
	for(int i = 2; i < 4; ++i) {
		stHostPrepDuelist[i] = env->addStaticText(L"", Resize(60, 135 + i * 45, 260, 175 + i * 45), true, false, wHostPrepare);
		stHostPrepDuelist[i]->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
        btnHostPrepKick[i] = irr::gui::CGUIImageButton::addImageButton(env,Resize(10, 135 + i * 45, 50, 175 + i * 45), wHostPrepare, BUTTON_HP_KICK);
        btnHostPrepKick[i]->setImageSize(irr::core::dimension2di(40 * yScale, 40 * yScale));
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
	stInfo->setOverrideColor(irr::video::SColor(255, 149, 211, 137));//255, 0, 0, 255
	stDataInfo = env->addStaticText(L"", Resize(10, 60, 260, 83), false, true, wInfos, -1, false);
	stDataInfo->setOverrideColor(irr::video::SColor(255, 222, 215, 100));//255, 0, 0, 255
	stSetName = env->addStaticText(L"", Resize(10, 83, 260, 106), false, true, wInfos, -1, false);
	stSetName->setOverrideColor(irr::video::SColor(255, 255, 152, 42));//255, 0, 0, 255
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
	imgLog->setImageSize(irr::core::dimension2di(28 * yScale, 28 * yScale));
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
    imgVol->setImageSize(irr::core::dimension2di(28 * yScale, 28 * yScale));
	if (gameConf.enable_music) {
		imgVol->setImage(imageManager.tPlay);
	} else {
		imgVol->setImage(imageManager.tMute);
	}
    //shift quick animation
    imgQuickAnimation = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(0, 165, 45, 210), wPallet, BUTTON_QUICK_ANIMIATION);
    imgQuickAnimation->setImageSize(irr::core::dimension2di(28 * yScale, 28 * yScale));
    if (gameConf.quick_animation) {
        imgQuickAnimation->setImage(imageManager.tDoubleX);
    } else {
        imgQuickAnimation->setImage(imageManager.tOneX);
    }
    //Settings
	imgSettings = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(0, 0, 45, 45), wPallet, BUTTON_SETTINGS);
	imgSettings->setImageSize(irr::core::dimension2di(28 * yScale, 28 * yScale));
	imgSettings->setImage(imageManager.tSettings);
	imgSettings->setIsPushButton(true);
    wSettings = env->addWindow(Resize(220, 80, 800, 540), false, dataManager.GetSysString(1273));
    wSettings->setRelativePosition(Resize(220, 80, 800, 540));
    wSettings->getCloseButton()->setVisible(false);
	wSettings->setVisible(false);
	    ChangeToIGUIImageWindow(wSettings, &bgSettings, imageManager.tWindow);
	int posX = 20;
	int posY = 80;
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
    chkHidePlayerName = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, CHECKBOX_HIDE_PLAYER_NAME, dataManager.GetSysString(1289));
    chkHidePlayerName->setChecked(gameConf.hide_player_name != 0);
	posY += 0;
    chkQuickAnimation = env->addCheckBox(false, Resize(0, 0, 0, 0), wSettings, CHECKBOX_QUICK_ANIMATION, dataManager.GetSysString(1299));
	chkQuickAnimation->setChecked(gameConf.quick_animation != 0);
    posY += 40;
    chkDrawFieldSpell = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, CHECKBOX_DRAW_FIELD_SPELL, dataManager.GetSysString(1283));
    chkDrawFieldSpell->setChecked(gameConf.draw_field_spell != 0);
    posY += 40;
    chkDrawSingleChain = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, CHECKBOX_DRAW_SINGLE_CHAIN, dataManager.GetSysString(1287));
	chkDrawSingleChain->setChecked(gameConf.draw_single_chain != 0);
	posX = 250;//重启一列显示
	posY = 80;//重新设定posY的初始值为80，作为新一行的初始位置
    chkEnableGenesysMode = env->addCheckBox(false, Resize(posX, posY, posX + 230, posY + 30), wSettings, CHECKBOX_ENABLE_GENESYS_MODE, dataManager.GetSysString(1698));
    chkEnableGenesysMode->setChecked(gameConf.enable_genesys_mode);
    posY += 40;
    // 勾选启用禁卡表
    chkLFlist = env->addCheckBox(false, Resize(posX, posY, posX + 100, posY + 30), wSettings, CHECKBOX_LFLIST, dataManager.GetSysString(1288));
    chkLFlist->setChecked(gameConf.use_lflist);
    chkLFlist->setVisible(!gameConf.enable_genesys_mode);
    // 启用禁卡表的combobox
    cbLFlist = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(posX + 110, posY, posX + 280, posY + 30), wSettings, COMBOBOX_LFLIST);
    cbLFlist->setMaxSelectionRows(6);
    for(unsigned int i = 0; i < deckManager._lfList.size(); ++i) {
        cbLFlist->addItem(deckManager._lfList[i].listName.c_str());
        if(!wcscmp(deckManager._lfList[i].listName.c_str(), gameConf.last_limit_list_name)) {//找到名称相同时找到对应的index值作为默认值
            gameConf.default_lflist = i;
        }
    }
    cbLFlist->setVisible(!gameConf.enable_genesys_mode);
    cbLFlist->setEnabled(gameConf.use_lflist);
    cbLFlist->setSelected(gameConf.use_lflist ? gameConf.default_lflist : cbLFlist->getItemCount() - 1);
    // 勾选启用genesys禁卡表
    chkGenesysLFlist = env->addCheckBox(false, Resize(posX, posY, posX + 100, posY + 30), wSettings, CHECKBOX_GENESYS_LFLIST, dataManager.GetSysString(1288));
    chkGenesysLFlist->setChecked(gameConf.use_genesys_lflist);
    chkGenesysLFlist->setVisible(gameConf.enable_genesys_mode);
    // 启用genesys禁卡表的combobox
    cbGenesysLFlist = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(posX + 110, posY, posX + 280, posY + 30), wSettings, COMBOBOX_GENESYS_LFLIST);
    cbGenesysLFlist->setMaxSelectionRows(6);
    for(unsigned int i = 0; i < deckManager._genesys_lfList.size(); ++i) {
        cbGenesysLFlist->addItem(deckManager._genesys_lfList[i].listName.c_str());
        if(!wcscmp(deckManager._genesys_lfList[i].listName.c_str(), gameConf.last_genesys_limit_list_name)) {//找到名称相同时找到对应的index值作为默认值
            gameConf.default_genesys_lflist = i;
        }
    }
    cbGenesysLFlist->setVisible(gameConf.enable_genesys_mode);
    cbGenesysLFlist->setEnabled(gameConf.use_genesys_lflist);
    cbGenesysLFlist->setSelected(gameConf.use_genesys_lflist ? gameConf.default_genesys_lflist : cbGenesysLFlist->getItemCount() - 1);
	posY += 0;//隐藏此布局，因为决斗界面已经有快捷按钮
	chkIgnore1 = env->addCheckBox(false, Resize(0, 0, 0, 0), wSettings, CHECKBOX_DISABLE_CHAT, dataManager.GetSysString(1290));
	chkIgnore1->setChecked(gameConf.chkIgnore1 != 0);
	posY += 40;
	chkIgnore2 = env->addCheckBox(false, Resize(posX, posY, posX + 260, posY + 30), wSettings, -1, dataManager.GetSysString(1291));
	chkIgnore2->setChecked(gameConf.chkIgnore2 != 0);

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
	btnCloseSettings->setImageSize(irr::core::dimension2di(50 * yScale, 50 * yScale));
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
		btnHand[i]->setImageScale(irr::core::vector2df(xScale, yScale));
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

	//pos select
	wPosSelect = env->addWindow(irr::core::recti(660 * xScale - 223 * yScale, 160 * yScale, 660 * xScale + 223 * yScale, (160 + 228) * yScale), false, dataManager.GetSysString(561));
	wPosSelect->getCloseButton()->setVisible(false);
	wPosSelect->setVisible(false);
        ChangeToIGUIImageWindow(wPosSelect, &bgPosSelect, imageManager.tDialog_L);
	btnPSAU = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(50, 30, 50 + 168, 30 + 168), wPosSelect, BUTTON_POS_AU);
	btnPSAU->setImageSize(irr::core::dimension2di(CARD_IMG_WIDTH * 0.6f * yScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	btnPSAD = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(218 + 10, 30, 228 + 168, 30 + 168), wPosSelect, BUTTON_POS_AD);
	btnPSAD->setImageSize(irr::core::dimension2di(CARD_IMG_WIDTH * 0.6f * yScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	btnPSAD->setImage(imageManager.tCover[0]);//show cover of player1
	btnPSDU = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(50, 30, 50 + 168, 30 + 168), wPosSelect, BUTTON_POS_DU);
	btnPSDU->setImageSize(irr::core::dimension2di(CARD_IMG_WIDTH * 0.6f * yScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	btnPSDU->setImageRotation(270);
	btnPSDD = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(218 + 10, 30, 228 + 168, 30 + 168), wPosSelect, BUTTON_POS_DD);
	btnPSDD->setImageSize(irr::core::dimension2di(CARD_IMG_WIDTH * 0.6f * yScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	btnPSDD->setImageRotation(270);
	btnPSDD->setImage(imageManager.tCover[0]);//show cover of player1

	//card select
	wCardSelect = env->addWindow(irr::core::recti(660 * xScale - 340 * yScale, 55 * yScale, 660 * xScale + 340 * yScale, 400 * yScale), false, L"");
	wCardSelect->getCloseButton()->setVisible(false);
	wCardSelect->setVisible(false);
        ChangeToIGUIImageWindow(wCardSelect, &bgCardSelect, imageManager.tDialog_L);
    stCardSelect = env->addStaticText(L"", Resize_Y(20, 10, 660, 40), false, false, wCardSelect, -1, false);
	stCardSelect->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	for(int i = 0; i < 5; ++i) {
		stCardPos[i] = env->addStaticText(L"", Resize_Y(30 + 125 * i, 40, 150 + 125 * i, 60), true, false, wCardSelect, -1, true);
		stCardPos[i]->setBackgroundColor(0xffffffff);
		stCardPos[i]->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
		btnCardSelect[i] = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(30 + 125 * i, 65, 150 + 125 * i, 235), wCardSelect, BUTTON_CARD_0 + i);
		btnCardSelect[i]->setImageSize(irr::core::dimension2di(CARD_IMG_WIDTH * 0.6f * yScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	}
	scrCardList = env->addScrollBar(true, Resize_Y(30, 245, 650, 285), wCardSelect, SCROLL_CARD_SELECT);
	btnSelectOK = env->addButton(Resize_Y(340 - 55, 295, 340 + 55, 295 + 40), wCardSelect, BUTTON_CARD_SEL_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnSelectOK, imageManager.tButton_S, imageManager.tButton_S_pressed);
	//card display
	wCardDisplay = env->addWindow(irr::core::recti(660 * xScale - 340 * yScale, 55 * yScale, 660 * xScale + 340 * yScale, 400 * yScale), false, L"");
	wCardDisplay->getCloseButton()->setVisible(false);
	wCardDisplay->setVisible(false);
        ChangeToIGUIImageWindow(wCardDisplay, &bgCardDisplay, imageManager.tDialog_L);
    stCardDisplay = env->addStaticText(L"", Resize_Y(20, 10, 660, 40), false, false, wCardDisplay, -1, false);
    stCardDisplay->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	for(int i = 0; i < 5; ++i) {
		stDisplayPos[i] = env->addStaticText(L"", Resize_Y(30 + 125 * i, 40, 150 + 125 * i, 60), true, false, wCardDisplay, -1, true);
		stDisplayPos[i]->setBackgroundColor(0xffffffff);
		stDisplayPos[i]->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
		btnCardDisplay[i] = irr::gui::CGUIImageButton::addImageButton(env, Resize_Y(30 + 125 * i, 65, 150 + 125 * i, 235), wCardDisplay, BUTTON_DISPLAY_0 + i);
		btnCardDisplay[i]->setImageSize(irr::core::dimension2di(CARD_IMG_WIDTH * 0.6f * yScale, CARD_IMG_HEIGHT * 0.6f * yScale));
	}
	scrDisplayList = env->addScrollBar(true, Resize_Y(30, 245, 30 + 620, 285), wCardDisplay, SCROLL_CARD_DISPLAY);
	btnDisplayOK = env->addButton(Resize_Y(340 - 55, 295, 340 + 55, 335), wCardDisplay, BUTTON_CARD_DISP_OK, dataManager.GetSysString(1211));
        ChangeToIGUIImageButton(btnDisplayOK, imageManager.tButton_S, imageManager.tButton_S_pressed);

	//announce number
	wANNumber = env->addWindow(Resize(500, 50, 800, 470), false, L"");
	wANNumber->getCloseButton()->setVisible(false);
	wANNumber->setVisible(false);
        ChangeToIGUIImageWindow(wANNumber, &bgANNumber, imageManager.tWindow_V);
	cbANNumber = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(30, 180, 270, 240), wANNumber, -1);
	cbANNumber->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    stANNumber = env->addStaticText(L"", Resize(20, 10, 280, 40), false, false, wANNumber, -1, false);
    stANNumber->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
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
	stANCard->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    ebANCard = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(10, 50, 290, 90), wANCard, EDITBOX_ANCARD);
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
    stANAttribute->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	for (int i = 0; i < ATTRIBUTES_COUNT; ++i)
		chkAttribute[i] = env->addCheckBox(false, Resize(50 + (i % 4) * 80, 60 + (i / 4) * 55, 130 + (i % 4) * 80, 90 + (i / 4) * 55),
			wANAttribute, CHECK_ATTRIBUTE, dataManager.GetSysString(DataManager::STRING_ID_ATTRIBUTE + i));
	//announce race
	wANRace = env->addWindow(Resize(500, 40, 800, 560), false, dataManager.GetSysString(563));
	wANRace->getCloseButton()->setVisible(false);
	wANRace->setVisible(false);
	    ChangeToIGUIImageWindow(wANRace, &bgANRace, imageManager.tDialog_S);
    stANRace = env->addStaticText(L"", Resize(20, 10, 280, 40), false, false, wANRace, -1, false);
    stANRace->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	for (int i = 0; i < RACES_COUNT; ++i)
		chkRace[i] = env->addCheckBox(false, Resize(30 + (i % 3) * 90, 60 + (i / 3) * 50, 100 + (i % 3) * 90, 110 + (i / 3) * 50),
			wANRace, CHECK_RACE, dataManager.GetSysString(DataManager::STRING_ID_RACE + i));
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
    wDeckEdit->setDraggable(false);
    wDeckEdit->setDrawTitlebar(false);
	wDeckEdit->setVisible(false);
	    ChangeToIGUIImageWindow(wDeckEdit, &bgDeckEdit, imageManager.tDialog_L);
	btnManageDeck = env->addButton(Resize(10, 35, 220, 75), wDeckEdit, BUTTON_MANAGE_DECK, dataManager.GetSysString(1460)/*卡组管理（其实它实际会显示分类名\n卡组名）*/);

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
	ebDMName = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(20, 50, 330, 90), wDMQuery, -1);
	ebDMName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	cbDMCategory = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(20, 50, 330, 90), wDMQuery, -1);
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
	cbDBCategory = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(0, 0, 0, 0), wDeckEdit, COMBOBOX_DBCATEGORY);
	cbDBCategory->setMaxSelectionRows(15);
	cbDBDecks = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(0, 0, 0, 0), wDeckEdit, COMBOBOX_DBDECKS);
	cbDBDecks->setMaxSelectionRows(15);
	btnSaveDeck = env->addButton(Resize(225, 35, 280, 75), wDeckEdit, BUTTON_SAVE_DECK, dataManager.GetSysString(1302));
        ChangeToIGUIImageButton(btnSaveDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	ebDeckname = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(10, 80, 220, 120), wDeckEdit, -1);
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

	//filters
	wFilter = env->addWindow(Resize(610, 1, 1020, 130), false, L"");
	wFilter->getCloseButton()->setVisible(false);
    wFilter->setDrawTitlebar(false);
	wFilter->setDraggable(false);
	wFilter->setVisible(false);
	    ChangeToIGUIImageWindow(wFilter, &bgFilter, imageManager.tDialog_L);
	env->addStaticText(dataManager.GetSysString(1311), Resize(10, 5, 70, 25), false, false, wFilter);
	cbCardType = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(60, 3, 120, 23), wFilter, COMBOBOX_MAINTYPE);
	cbCardType->addItem(dataManager.GetSysString(1310));
	cbCardType->addItem(dataManager.GetSysString(1312));
	cbCardType->addItem(dataManager.GetSysString(1313));
	cbCardType->addItem(dataManager.GetSysString(1314));
	cbCardType2 = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(125, 3, 200, 23), wFilter, COMBOBOX_SECONDTYPE);
	cbCardType2->addItem(dataManager.GetSysString(1310), 0);
	env->addStaticText(dataManager.GetSysString(1315), Resize(205, 5, 280, 25), false, false, wFilter);
	// 筛选卡片的条件：禁限类型、卡片允许情况
    cbLimit = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(260, 3, 390, 23), wFilter, COMBOBOX_LIMIT);
	cbLimit->addItem(dataManager.GetSysString(1310));//（无）
	cbLimit->addItem(dataManager.GetSysString(1316));// 禁止
	cbLimit->addItem(dataManager.GetSysString(1317));// 限制
	cbLimit->addItem(dataManager.GetSysString(1318));// 准限制
	cbLimit->addItem(dataManager.GetSysString(1699));// 点数
	cbLimit->addItem(dataManager.GetSysString(1481));// ＯＣＧ
	cbLimit->addItem(dataManager.GetSysString(1482));// ＴＣＧ
	cbLimit->addItem(dataManager.GetSysString(1483));// 简体中文
	cbLimit->addItem(dataManager.GetSysString(1484));// 自定义卡片
	cbLimit->addItem(dataManager.GetSysString(1485));// 无独有卡
    // 筛选卡片的条件：属性
	stAttribute = env->addStaticText(dataManager.GetSysString(1319)/*属性：*/, Resize(10, 28, 70, 48), false, false, wFilter);
	cbAttribute = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(60, 26, 190, 46), wFilter, COMBOBOX_ATTRIBUTE);
	cbAttribute->setMaxSelectionRows(10);
	cbAttribute->addItem(dataManager.GetSysString(1310)/*（无）*/, 0);
	for (int i = 0; i < ATTRIBUTES_COUNT; ++i)
		cbAttribute->addItem(dataManager.GetSysString(DataManager::STRING_ID_ATTRIBUTE + i), 0x1U << i);
    // 筛选卡片的条件：种族
	env->addStaticText(dataManager.GetSysString(1321)/*种族：*/, Resize(10, 51, 70, 71), false, false, wFilter);
	cbRace = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(60, 40 + 75 / 6, 190, 60 + 75 / 6), wFilter, COMBOBOX_RACE);
	cbRace->setMaxSelectionRows(10);
	cbRace->addItem(dataManager.GetSysString(1310)/*（无）*/, 0);
	for (int i = 0; i < RACES_COUNT; ++i)
		cbRace->addItem(dataManager.GetSysString(DataManager::STRING_ID_RACE + i), 0x1U << i);
	// 筛选卡片的条件：攻击力
    env->addStaticText(dataManager.GetSysString(1322)/*攻击：*/, Resize(205, 28, 280, 48), false, false, wFilter);
	ebAttack = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(260, 26, 340, 46), wFilter, EDITBOX_INPUTS);
	ebAttack->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	// 筛选卡片的条件：防御力
    env->addStaticText(dataManager.GetSysString(1323)/*守备：*/, Resize(205, 51, 280, 71), false, false, wFilter);
	ebDefense = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(260, 49, 340, 69), wFilter, EDITBOX_INPUTS);
	ebDefense->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	// 筛选卡片的条件：星数
    env->addStaticText(dataManager.GetSysString(1324)/*星数：*/, Resize(10, 74, 80, 94), false, false, wFilter);
	ebStar = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(60, 60 + 100 / 6, 100, 80 + 100 / 6), wFilter, EDITBOX_INPUTS);
	ebStar->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	// 筛选卡片的条件：刻度
    env->addStaticText(dataManager.GetSysString(1336)/*刻度：*/, Resize(101, 60 + 100 / 6, 150 * xScale, 82 + 100 / 6), false, false, wFilter);
	ebScale = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(150, 60 + 100 / 6, 190, 80 + 100 / 6), wFilter, EDITBOX_INPUTS);
	ebScale->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	// 筛选卡片的条件：关键字
    env->addStaticText(dataManager.GetSysString(1325)/*关键字：*/, Resize(205, 60 + 100 / 6, 280, 82 + 100 / 6), false, false, wFilter);
	ebCardName = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(260, 72, 390, 92), wFilter, EDITBOX_KEYWORD);
	ebCardName->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
    // 筛选卡片的条件：效果
	btnEffectFilter = env->addButton(Resize(345, 28, 390, 69), wFilter, BUTTON_EFFECT_FILTER, dataManager.GetSysString(1326)/*效果*/);
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

    //LINK MARKER SEARCH
	btnMarksFilter = env->addButton(Resize(60, 80 + 125 / 6, 190, 100 + 125 / 6), wFilter, BUTTON_MARKS_FILTER, dataManager.GetSysString(1374)/*连接标记*/);
 	   ChangeToIGUIImageButton(btnMarksFilter, imageManager.tButton_L, imageManager.tButton_L_pressed);
	wLinkMarks = env->addWindow(Resize(600, 30, 820, 250), false, L"");
	wLinkMarks->getCloseButton()->setVisible(false);
	wLinkMarks->setDrawTitlebar(false);
	wLinkMarks->setDraggable(false);
	wLinkMarks->setVisible(false);
	    ChangeToIGUIImageWindow(wLinkMarks, &bgLinkMarks, imageManager.tWindow_V);
	btnMarksOK = env->addButton(Resize(80, 80, 140, 140), wLinkMarks, BUTTON_MARKERS_OK, dataManager.GetSysString(1211)/*确定*/);
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
	wReplay = env->addWindow(Resize(220, 100, 800, 520), false, dataManager.GetSysString(1202)/*观看录像*/);
	wReplay->getCloseButton()->setVisible(false);
	wReplay->setDrawBackground(false);
	wReplay->setVisible(false);
	bgReplay = env->addImage(Resize(0, 0, 580, 420), wReplay, -1, 0, true);
	bgReplay->setImage(imageManager.tWindow);
    bgReplay->setScaleImage(true);
	lstReplayList = env->addListBox(Resize(20, 30, 310, 400), wReplay, LISTBOX_REPLAY_LIST, true);
	lstReplayList->setItemHeight(30 * yScale);
	btnLoadReplay = env->addButton(Resize(440, 310, 550, 350), wReplay, BUTTON_LOAD_REPLAY, dataManager.GetSysString(1348)/*载入录像*/);
        ChangeToIGUIImageButton(btnLoadReplay, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnDeleteReplay = env->addButton(Resize(320, 310, 430, 350), wReplay, BUTTON_DELETE_REPLAY, dataManager.GetSysString(1361)/*删除录像*/);
        ChangeToIGUIImageButton(btnDeleteReplay, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnRenameReplay = env->addButton(Resize(320, 360, 430, 400), wReplay, BUTTON_RENAME_REPLAY, dataManager.GetSysString(1362)/*重命名*/);
        ChangeToIGUIImageButton(btnRenameReplay, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnReplayCancel = env->addButton(Resize(440, 360, 550, 400), wReplay, BUTTON_CANCEL_REPLAY, dataManager.GetSysString(1347)/*退出*/);
        ChangeToIGUIImageButton(btnReplayCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);
	env->addStaticText(dataManager.GetSysString(1349), Resize(320, 30, 550, 50), false, true, wReplay);
	stReplayInfo = env->addStaticText(L"", Resize(320, 60, 570, 315), false, true, wReplay);
	env->addStaticText(dataManager.GetSysString(1353), Resize(320, 180, 550, 200), false, true, wReplay);
	ebRepStartTurn = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(320, 210, 430, 250), wReplay, -1);
	ebRepStartTurn->setTextAlignment(irr::gui::EGUIA_CENTER, irr::gui::EGUIA_CENTER);
	btnExportDeck = env->addButton(Resize(440, 260, 550, 300), wReplay, BUTTON_EXPORT_DECK, dataManager.GetSysString(1369)/*提取卡组*/);
        ChangeToIGUIImageButton(btnExportDeck, imageManager.tButton_S, imageManager.tButton_S_pressed);
	btnShareReplay = env->addButton(Resize(320, 260, 430, 300), wReplay, BUTTON_SHARE_REPLAY, dataManager.GetSysString(1368)/*分享录像*/);
		ChangeToIGUIImageButton(btnShareReplay, imageManager.tButton_S, imageManager.tButton_S_pressed);
        //single play window
	wSinglePlay = env->addWindow(Resize(220, 100, 800, 520), false, dataManager.GetSysString(1201)/*单人游戏*/);
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
		irr::gui::IGUITab* tabBot = wSingle->addTab(dataManager.GetSysString(1380)/*人机模式（双方无禁）*/);
		lstBotList = env->addListBox(Resize(0, 0, 300, 330), tabBot, LISTBOX_BOT_LIST, true);
		lstBotList->setItemHeight(30 * yScale);
		btnStartBot = env->addButton(Resize(420, 240, 530, 280), tabBot, BUTTON_BOT_START, dataManager.GetSysString(1211)/*确定*/);
            ChangeToIGUIImageButton(btnStartBot, imageManager.tButton_S, imageManager.tButton_S_pressed);
		btnBotCancel = env->addButton(Resize(420, 290, 530, 330), tabBot, BUTTON_CANCEL_SINGLEPLAY, dataManager.GetSysString(1210)/*退出*/);
            ChangeToIGUIImageButton(btnBotCancel, imageManager.tButton_S, imageManager.tButton_S_pressed);
		env->addStaticText(dataManager.GetSysString(1382), Resize(310, 10, 500, 30), false, true, tabBot);
		stBotInfo = env->addStaticText(L"", Resize(310, 40, 560, 160), false, true, tabBot);
		cbBotDeckCategory =  env->addComboBox(Resize(0, 0, 0, 0), tabBot, COMBOBOX_BOT_DECKCATEGORY);
		cbBotDeckCategory->setVisible(false);
		cbBotDeck =  env->addComboBox(Resize(0, 0, 0, 0), tabBot);
		cbBotDeck->setVisible(false);
        btnBotDeckSelect = env->addButton(Resize(310, 110, 530, 150), tabBot, BUTTON_BOT_DECK_SELECT, L"");
		btnBotDeckSelect->setVisible(false);
		cbBotRule = irr::gui::CAndroidGUIComboBox::addAndroidComboBox(env, Resize(310, 160, 530, 200), tabBot, COMBOBOX_BOT_RULE);
		cbBotRule->addItem(dataManager.GetSysString(1262));// 大师规则３
		cbBotRule->addItem(dataManager.GetSysString(1263));// 新大师规则（2017）
		cbBotRule->addItem(dataManager.GetSysString(1264));// 大师规则（2020）
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
	ebRSName = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(20, 50, 370, 90), wReplaySave, -1);
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
    imgChat->setImageSize(irr::core::dimension2di(28 * yScale, 28 * yScale));
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
	ebChatInput = irr::gui::CAndroidGUIEditBox::addAndroidEditBox(L"", true, env, Resize(3, 2, 710, 28), wChat, EDITBOX_CHAT);
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

	env->getSkin()->setFont(guiFont);
	env->getSkin()->setSize(irr::gui::EGDS_CHECK_BOX_WIDTH, (gameConf.textfontsize + 10) * yScale);
	env->setFocus(wMainMenu);
	for (int i = 0; i < irr::gui::EGDC_COUNT; ++i) {
		auto col = env->getSkin()->getColor((irr::gui::EGUI_DEFAULT_COLOR)i);
		col.setAlpha(200);
		env->getSkin()->setColor((irr::gui::EGUI_DEFAULT_COLOR)i, col);
	}
    //左上角FPS数字
    irr::gui::IGUIStaticText *text = env->addStaticText(L"", Resize(1,1,100,45), false, false, 0, GUI_INFO_FPS);

	hideChat = false;
	hideChatTimer = 0;
	
	return true;
}//bool Game::Initialize
/**
 * @brief 游戏主循环函数，负责渲染游戏画面、处理输入事件以及更新逻辑状态。
 *
 * 此函数初始化摄像机、设置投影矩阵与视图矩阵，并根据平台加载对应的着色器材质。
 * 在主循环中进行场景绘制、界面刷新、音频播放等操作，并控制帧率和时间显示。
 * 同时支持多线程同步机制（如互斥锁）以确保数据安全访问。
 */
void Game::MainLoop() {
	wchar_t cap[256]; // 字符缓冲区，用于临时存储宽字符文本

	// 添加一个摄像机节点到场景管理器
	camera = smgr->addCameraSceneNode(0);

	// 构建并设置自定义的投影矩阵
	irr::core::matrix4 mProjection;
	BuildProjectionMatrix(mProjection, -0.90f, 0.45f, -0.42f, 0.42f, 1.0f, 100.0f);
	camera->setProjectionMatrix(mProjection);

	// 设置摄像机视角变换矩阵（LookAt）
	mProjection.buildCameraLookAtMatrixLH(
		irr::core::vector3df(4.2f, 8.0f, 7.8f),   // 摄像机位置
		irr::core::vector3df(4.2f, 0, 0),         // 观察目标点
		irr::core::vector3df(0, 0, 1)             // 上方向向量
	);
	camera->setViewMatrixAffector(mProjection);

	// 设置环境光颜色为白色
	smgr->setAmbientLight(irr::video::SColorf(1.0f, 1.0f, 1.0f));

	float atkframe = 0.1f; // 动画帧计数器初始值

	// 获取设备定时器并重置时间为0
	irr::ITimer* timer = device->getTimer();
	timer->setTime(0);

	// 获取GUI中的FPS信息控件
	irr::gui::IGUIElement *stat = device->getGUIEnvironment()->getRootGUIElement()->getElementFromId(GUI_INFO_FPS);
	int fps = 0;       // 当前秒内已渲染帧数
	int cur_time = 0;  // 定时器当前时间戳

	// 初始化OpenGL ES 2.0材质类型变量
	ogles2Solid = 0;
	ogles2TrasparentAlpha = 0;
	ogles2BlendTexture = 0;

	// 根据GL版本选择是否使用内置或外部着色器文件
	if (glversion == 0 || glversion == 2) {
		ogles2Solid = irr::video::EMT_SOLID;
		ogles2TrasparentAlpha = irr::video::EMT_TRANSPARENT_ALPHA_CHANNEL;
		ogles2BlendTexture = irr::video::EMT_ONETEXTURE_BLEND;
	} else {
		// 着色器文件路径配置
		irr::io::path solidvsFileName = "media/ogles2customsolid.frag";
		irr::io::path TACvsFileName = "media/ogles2customTAC.frag";
		irr::io::path blendvsFileName = "media/ogles2customblend.frag";
		irr::io::path psFileName = "media/ogles2custom.vert";

		// 检查硬件是否支持像素着色器
		if (!driver->queryFeature(irr::video::EVDF_PIXEL_SHADER_1_1) &&
		    !driver->queryFeature(irr::video::EVDF_ARB_FRAGMENT_PROGRAM_1)) {
			ALOGD("cc game: WARNING: Pixel shaders disabled because of missing driver/hardware support.");
			psFileName = "";
		}

		// 检查硬件是否支持顶点着色器
		if (!driver->queryFeature(irr::video::EVDF_VERTEX_SHADER_1_1) &&
		    !driver->queryFeature(irr::video::EVDF_ARB_VERTEX_PROGRAM_1)) {
			ALOGD("cc game: WARNING: Vertex shaders disabled because of missing driver/hardware support.");
			solidvsFileName = "";
			TACvsFileName = "";
			blendvsFileName = "";
		}

		// 加载高级着色器程序
		irr::video::IGPUProgrammingServices* gpu = driver->getGPUProgrammingServices();
		if (gpu) {
			const irr::video::E_GPU_SHADING_LANGUAGE shadingLanguage = irr::video::EGSL_DEFAULT;

			ogles2Solid = gpu->addHighLevelShaderMaterialFromFiles(
				psFileName, "vertexMain", irr::video::EVST_VS_1_1,
				solidvsFileName, "pixelMain", irr::video::EPST_PS_1_1,
				&customShadersCallback, irr::video::EMT_SOLID, 0, shadingLanguage);

			ogles2TrasparentAlpha = gpu->addHighLevelShaderMaterialFromFiles(
				psFileName, "vertexMain", irr::video::EVST_VS_1_1,
				TACvsFileName, "pixelMain", irr::video::EPST_PS_1_1,
				&customShadersCallback, irr::video::EMT_TRANSPARENT_ALPHA_CHANNEL, 0, shadingLanguage);

			ogles2BlendTexture = gpu->addHighLevelShaderMaterialFromFiles(
				psFileName, "vertexMain", irr::video::EVST_VS_1_1,
				blendvsFileName, "pixelMain", irr::video::EPST_PS_1_1,
				&customShadersCallback, irr::video::EMT_ONETEXTURE_BLEND, 0, shadingLanguage);

			ALOGD("cc game:ogles2Sold = %d", ogles2Solid);
			ALOGD("cc game:ogles2BlendTexture = %d", ogles2BlendTexture);
			ALOGD("cc game:ogles2TrasparentAlpha = %d", ogles2TrasparentAlpha);
		}
	}

	// 将着色器材质应用至各个材质对象
	// 设置卡片正面材质类型为混合纹理着色器
    matManager.mCard.MaterialType = (irr::video::E_MATERIAL_TYPE)ogles2BlendTexture;
    // 设置纹理材质类型为透明alpha通道着色器
    matManager.mTexture.MaterialType = (irr::video::E_MATERIAL_TYPE)ogles2TrasparentAlpha;
    // 设置背景线条材质类型为混合纹理着色器
    matManager.mBackLine.MaterialType = (irr::video::E_MATERIAL_TYPE)ogles2BlendTexture;
    // 设置选择区域材质类型为混合纹理着色器
    matManager.mSelField.MaterialType = (irr::video::E_MATERIAL_TYPE)ogles2BlendTexture;
    // 设置轮廓线材质类型为纯色着色器
    matManager.mOutLine.MaterialType = (irr::video::E_MATERIAL_TYPE)ogles2Solid;
    // 设置透明纹理材质类型为透明alpha通道着色器
    matManager.mTRTexture.MaterialType = (irr::video::E_MATERIAL_TYPE)ogles2TrasparentAlpha;
    // 设置攻击指示器材质类型为混合纹理着色器
    matManager.mATK.MaterialType = (irr::video::E_MATERIAL_TYPE)ogles2BlendTexture;

	// 若不支持非幂次纹理尺寸，则设置纹理环绕模式为边缘夹紧
	if (!isNPOTSupported) {
        // 当不支持非2次幂纹理时，设置各个材质的纹理环绕模式为边缘夹紧(CLAMP_TO_EDGE)
        // 卡片正面材质的U轴和V轴纹理环绕模式设为边缘夹紧
        matManager.mCard.TextureLayer[0].TextureWrapU = irr::video::ETC_CLAMP_TO_EDGE;
        matManager.mCard.TextureLayer[0].TextureWrapV = irr::video::ETC_CLAMP_TO_EDGE;
        // 纹理材质的U轴和V轴纹理环绕模式设为边缘夹紧
        matManager.mTexture.TextureLayer[0].TextureWrapU = irr::video::ETC_CLAMP_TO_EDGE;
        matManager.mTexture.TextureLayer[0].TextureWrapV = irr::video::ETC_CLAMP_TO_EDGE;
        // 背景线条材质的U轴和V轴纹理环绕模式设为边缘夹紧
        matManager.mBackLine.TextureLayer[0].TextureWrapU = irr::video::ETC_CLAMP_TO_EDGE;
        matManager.mBackLine.TextureLayer[0].TextureWrapV = irr::video::ETC_CLAMP_TO_EDGE;
        // 选择区域材质的U轴和V轴纹理环绕模式设为边缘夹紧
        matManager.mSelField.TextureLayer[0].TextureWrapU = irr::video::ETC_CLAMP_TO_EDGE;
        matManager.mSelField.TextureLayer[0].TextureWrapV = irr::video::ETC_CLAMP_TO_EDGE;
        // 轮廓线材质的U轴和V轴纹理环绕模式设为边缘夹紧
        matManager.mOutLine.TextureLayer[0].TextureWrapU = irr::video::ETC_CLAMP_TO_EDGE;
        matManager.mOutLine.TextureLayer[0].TextureWrapV = irr::video::ETC_CLAMP_TO_EDGE;
        // 透明纹理材质的U轴和V轴纹理环绕模式设为边缘夹紧
        matManager.mTRTexture.TextureLayer[0].TextureWrapU = irr::video::ETC_CLAMP_TO_EDGE;
        matManager.mTRTexture.TextureLayer[0].TextureWrapV = irr::video::ETC_CLAMP_TO_EDGE;
        // 攻击指示器材质的U轴和V轴纹理环绕模式设为边缘夹紧
        matManager.mATK.TextureLayer[0].TextureWrapU = irr::video::ETC_CLAMP_TO_EDGE;
        matManager.mATK.TextureLayer[0].TextureWrapV = irr::video::ETC_CLAMP_TO_EDGE;
	}

	// 主循环开始：持续运行直到窗口关闭
	while(device->run()) {
		linePatternD3D = (linePatternD3D + 1) % 30;     // 更新线条图案索引（Direct3D风格）
		linePatternGL = (linePatternGL << 1) | (linePatternGL >> 15); // 更新线条图案掩码（OpenGL风格）

		atkframe += 0.1f;                               // 增加动画帧计数
		atkdy = (float)sin(atkframe);                   // 计算攻击动作偏移量

		// 开始渲染场景
		driver->beginScene(true, true, irr::video::SColor(0, 0, 0, 0));

		// 设置2D材质并启用2D渲染
		driver->getMaterial2D().MaterialType = (irr::video::E_MATERIAL_TYPE)ogles2Solid;
		if (!isNPOTSupported) {
			driver->getMaterial2D().TextureLayer[0].TextureWrapU = irr::video::ETC_CLAMP_TO_EDGE;
			driver->getMaterial2D().TextureLayer[0].TextureWrapV = irr::video::ETC_CLAMP_TO_EDGE;
		}
		driver->enableMaterial2D(true);
		driver->getMaterial2D().ZBuffer = irr::video::ECFN_NEVER;

		// 绘制背景图像
		if(imageManager.tBackGround) {
			driver->draw2DImage(imageManager.tBackGround, Resize(0, 0, GAME_WIDTH, GAME_HEIGHT),
			                    irr::core::recti(0, 0, imageManager.tBackGround->getOriginalSize().Width,
			                                     imageManager.tBackGround->getOriginalSize().Height));
		}
		if(imageManager.tBackGround_menu) {
			driver->draw2DImage(imageManager.tBackGround_menu, Resize(0, 0, GAME_WIDTH, GAME_HEIGHT),
			                    irr::core::recti(0, 0, imageManager.tBackGround->getOriginalSize().Width,
			                                     imageManager.tBackGround->getOriginalSize().Height));
		}
		if(imageManager.tBackGround_deck) {
			driver->draw2DImage(imageManager.tBackGround_deck, Resize(0, 0, GAME_WIDTH, GAME_HEIGHT),
			                    irr::core::recti(0, 0, imageManager.tBackGround->getOriginalSize().Width,
			                                     imageManager.tBackGround->getOriginalSize().Height));
		}
		driver->enableMaterial2D(false);

		// 多线程保护：锁定互斥锁防止并发修改共享资源
		gMutex.lock();

		// 根据不同状态绘制对应内容
		if(dInfo.isStarted) {
			DrawBackImage(imageManager.tBackGround); // 背景图片
			DrawBackGround();                        // 场地背景
			DrawCards();                             // 所有卡牌
			DrawMisc();                              // 其他元素
			smgr->drawAll();                         // 渲染所有3D场景节点
			driver->setMaterial(irr::video::IdentityMaterial);
			driver->clearZBuffer();                  // 清除深度缓存
		} else if(is_building) {
			DrawBackImage(imageManager.tBackGround_deck); // 牌组编辑界面背景
			driver->enableMaterial2D(true);
			DrawDeckBd();                            // 绘制牌组边框
			driver->enableMaterial2D(false);
		} else {
			DrawBackImage(imageManager.tBackGround_menu); // 菜单界面背景
		}

		// 绘制用户界面及特殊效果
		driver->enableMaterial2D(true);
		DrawGUI();                                   // UI组件
		DrawSpec();                                  // 特效相关
		driver->enableMaterial2D(false);

		gMutex.unlock();                             // 解锁互斥锁

		playBGM();                                   // 播放背景音乐

		// 控制信号帧倒计时
		if(signalFrame > 0) {
			signalFrame--;
			if(!signalFrame)
				frameSignal.Set();                     // 发送信号通知其他线程
		}

		// 显示等待提示消息
		if(waitFrame >= 0) {
			waitFrame++;
			if(waitFrame % 90 == 0) {
				stHintMsg->setText(dataManager.GetSysString(1390)); // "等待行动中..."
			} else if(waitFrame % 90 == 30) {
				stHintMsg->setText(dataManager.GetSysString(1391)); // "等待行动中...."
			} else if(waitFrame % 90 == 60) {
				stHintMsg->setText(dataManager.GetSysString(1392)); // "等待行动中....."
			}
		}

		driver->endScene();                          // 结束渲染过程

		// 检查是否需要退出对战窗口
		if(closeSignal.Wait(1))
			CloseDuelWindow();

		fps++;                                       // 帧计数增加
		cur_time = timer->getTime();                 // 获取当前时间

		// 控制帧率为约60FPS（每帧最大延迟20毫秒）
		if(cur_time < fps * 17 - 20)
			std::this_thread::sleep_for(std::chrono::milliseconds(20));

		// 每秒钟更新一次FPS显示和剩余时间
		if(cur_time >= 1000) {
			if(stat) {
				irr::core::stringw str = L"FPS: ";
				str += (irr::s32)device->getVideoDriver()->getFPS();
				stat->setText(str.c_str());
			}
			fps = 0;
			cur_time -= 1000;
			timer->setTime(0);

			// 更新玩家剩余时间
			if(dInfo.time_player == 0 || dInfo.time_player == 1)
				if(dInfo.time_left[dInfo.time_player]) {
					dInfo.time_left[dInfo.time_player]--;
					RefreshTimeDisplay();
				}
		}

		device->yield(); // 礼让CPU，节省电池电量
	}

	// 游戏结束后的清理工作
	DuelClient::StopClient(true);
	if(dInfo.isSingleMode)
		SingleMode::StopPlay(true);

	usleep(500000);      // 等待半秒后保存配置
	SaveConfig();
	usleep(500000);      // 再等待半秒后释放设备资源
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
void Game::BuildProjectionMatrix(irr::core::matrix4& mProjection, irr::f32 left, irr::f32 right, irr::f32 bottom, irr::f32 top, irr::f32 znear, irr::f32 zfar) {
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
void Game::InitStaticText(irr::gui::IGUIStaticText* pControl, irr::u32 cWidth, irr::u32 cHeight, irr::gui::CGUITTFont* font, const wchar_t* text) {
	std::wstring format_text;
	format_text = SetStaticText(pControl, cWidth, font, text);
	if(font->getDimension(format_text.c_str()).Height <= cHeight) {
		scrCardText->setVisible(false);
		if(env->hasFocus(scrCardText))
			env->removeFocus(scrCardText);
		return;
	}
	format_text = SetStaticText(pControl, cWidth, font, text);
	irr::u32 fontheight = font->getDimension(L"A").Height + font->getKerningHeight();
	irr::u32 step = (font->getDimension(format_text.c_str()).Height - cHeight) / fontheight + 1;
	scrCardText->setVisible(true);
	scrCardText->setMin(0);
	scrCardText->setMax(step);
	scrCardText->setPos(0);
}
std::wstring Game::SetStaticText(irr::gui::IGUIStaticText* pControl, irr::u32 cWidth, irr::gui::CGUITTFont* font, const wchar_t* text, irr::u32 pos) {
	size_t pbuffer = 0;
	irr::u32 _width = 0, _height = 0;
	wchar_t prev = 0;
	wchar_t strBuffer[4096]{};
	constexpr size_t buffer_len = sizeof strBuffer / sizeof strBuffer[0] - 1;
	const size_t text_len = std::wcslen(text);

	for(size_t i = 0; i < text_len ; ++i) {
		if (pbuffer >= buffer_len)
			break;
		wchar_t c = text[i];
		irr::u32 w = font->getCharDimension(c).Width + font->getKerningWidth(c, prev);
		prev = c;
		if (c == L'\r') {
			continue;
		}
		if (c == L'\n') {
			strBuffer[pbuffer++] = L'\n';
			_width = 0;
			_height++;
			prev = 0;
			if (_height == pos)
				pbuffer = 0;
			continue;
		}
		if (_width > 0 && _width + w > cWidth) {
			strBuffer[pbuffer++] = L'\n';
			_width = 0;
			_height++;
			prev = 0;
			if (_height == pos)
				pbuffer = 0;
		}
		if (pbuffer >= buffer_len)
			break;
		_width += w;
		strBuffer[pbuffer++] = c;
	}
	strBuffer[pbuffer] = 0;
	if (pControl)
		pControl->setText(strBuffer);
	return std::wstring(strBuffer);
}
/**
 * @brief 加载游戏扩展包（如卡牌数据库、配置文件等）。
 *
 * 此函数会扫描 ./expansions/ 目录下的 .zip 或 .ypk 扩展包文件，
 * 将其作为文件档案加载到文件系统中，并进一步解析其中的数据库文件 (.cdb)、
 * 配置文件 (.conf) 和卡组文件 (.ydk)，用于初始化游戏数据。
 *
 */
void Game::LoadExpansions() {
	// TODO: get isUseExtraCards

	// 打开 expansions 目录以查找扩展包文件
	DIR * dir;
	struct dirent * dirp;
	if((dir = opendir("./expansions/")) == NULL)
		return;

	// 遍历目录中的所有文件，筛选出 .zip 和 .ypk 文件并加入文件系统
	while((dirp = readdir(dir)) != NULL) {
		size_t len = strlen(dirp->d_name);
		// 检查文件名长度是否足够以及后缀是否为 .zip 或 .ypk（忽略大小写）
		if(len > 4 && (strcasecmp(dirp->d_name + len - 4, ".zip") == 0 || strcasecmp(dirp->d_name + len - 4, ".ypk") == 0)) {
			char upath[1024];
			sprintf(upath, "./expansions/%s", dirp->d_name);
			ALOGW("扩展卡文件: %s", upath);
			dataManager.FileSystem->addFileArchive(upath, true, false, EFAT_ZIP);
		} else if (len > 11 && strcasecmp(dirp->d_name + len - 11, "lflist.conf") == 0) {
			char upath[1024];
			sprintf(upath, "./expansions/%s", dirp->d_name);
			ALOGW("拓展禁卡表文件: %s", upath);
			deckManager.LoadLFListSingle((const char*)upath);
		}
	}
	closedir(dir);

	// 遍历已加载的所有文件档案，处理其中的内容
	for(irr::u32 i = 0; i < dataManager.FileSystem->getFileArchiveCount(); ++i) {
		auto archive = dataManager.FileSystem->getFileArchive(i)->getFileList();

		// 遍历当前档案中的每一个文件
		for(irr::u32 j = 0; j < archive->getFileCount(); ++j) {
			wchar_t fname[1024];
			const char* uname = archive->getFullFileName(j).c_str();
			BufferIO::DecodeUTF8(uname, fname);

			// 如果是数据库文件 (.cdb)，则加载至数据管理器
			if (IsExtension(fname, L".cdb")) {
				dataManager.LoadDB(fname);
				continue;
			}

			// 如果是配置文件 (.conf)，根据文件名决定加载方式
			if (IsExtension(fname, L".conf")) {
                auto reader = dataManager.FileSystem->createAndOpenFile(uname);
                // 若文件名为 lflist 类型，则加载限卡表；否则加载字符串资源
                if (std::wcsstr(fname, L"lflist") != nullptr) {
                    ALOGD("uname=%s",uname);
                    deckManager.LoadLFListSingle(reader);
                } else {
                    dataManager.LoadStrings(reader);
                }
				continue;
			}

			// 如果路径前缀为 pack/ 并且是 ydk 卡组文件，则记录在扩展包列表中
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
	cbCategory->addItem(dataManager.GetSysString(1451));// 人机卡组
	cbCategory->addItem(dataManager.GetSysString(1452));// 未分类卡组
	cbCategory->addItem(dataManager.GetSysString(1453));// --------（分割线）
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
	DeckManager::GetCategoryPath(catepath, cbCategory->getSelected(), cbCategory->getText(), cbCategory == mainGame->cbDBCategory);
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
			wchar_t deckname[256]{};
			BufferIO::CopyWideString(name, deckname, std::wcslen(name) - 4);
			additem(deckname);
		}
	});
}
void Game::RefreshReplay() {
	lstReplayList->clear();
	FileSystem::TraversalDir(L"./replay", [this](const wchar_t* name, bool isdir) {
		if (!isdir && IsExtension(name, L".yrp"))
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
	FILE* fp = myfopen("bot.conf", "r");
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
		SetStaticText(stBotInfo, 200, guiFont, dataManager.GetSysString(1385)); // 此模式人机尚未实装
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
			myswprintf(cate_deck, L"%ls%ls", cate, dataManager.GetSysString(1301));// 未选择卡组！
		}
		mainGame->btnBotDeckSelect->setText(cate_deck);
	}
}
void Game::LoadConfig() {
	wchar_t wstr[256];
	gameConf.antialias = 1;
	gameConf.serverport = 7911;
	gameConf.textfontsize = irr::android::getIntSetting(appMain, "textfontsize", 18);;
	gameConf.nickname[0] = 0;
	gameConf.gamename[0] = 0;
    // 获取 enable_genesys_mode 值并存储到 gameConf.enable_genesys_mode
    gameConf.enable_genesys_mode = irr::android::getIntSetting(appMain, "enable_genesys_mode", 0);;
    // 获取 lastLimit 值并存储到 gameConf.last_limit_list_name
    BufferIO::DecodeUTF8(irr::android::getLastLimit(appMain).c_str(), wstr);
    BufferIO::CopyWStr(wstr, gameConf.last_limit_list_name, 64);
    // 获取 lastGenesysLimit 值并存储到 gameConf.last_genesys_limit_list_name
    BufferIO::DecodeUTF8(irr::android::getLastGenesysLimit(appMain).c_str(), wstr);
    BufferIO::CopyWStr(wstr, gameConf.last_genesys_limit_list_name, 64);
    ALOGW("cc game: lastLimit: %ls", wstr);
    // 获取 lastCategory 值并存储到 gameConf.lastcategory
    BufferIO::DecodeUTF8(irr::android::getLastCategory(appMain).c_str(), wstr);;
    BufferIO::CopyWStr(wstr, gameConf.lastcategory, 64);
    // 获取 lastDeck 值并存储到 gameConf.lastdeck
	BufferIO::DecodeUTF8(irr::android::getLastDeck(appMain).c_str(), wstr);
	BufferIO::CopyWStr(wstr, gameConf.lastdeck, 64);
    // 获取 fontPath 值并存储到 gameConf.numfont 和 gameConf.textfont
	BufferIO::DecodeUTF8(irr::android::getFontPath(appMain).c_str(), wstr);
	BufferIO::CopyWStr(wstr, gameConf.numfont, 256);
	BufferIO::CopyWStr(wstr, gameConf.textfont, 256);
	gameConf.lasthost[0] = 0;
	gameConf.lastport[0] = 0;
	gameConf.roompass[0] = 0;
	//helper
	gameConf.chkMAutoPos = irr::android::getIntSetting(appMain, "chkMAutoPos", 0);
	gameConf.chkSTAutoPos = irr::android::getIntSetting(appMain, "chkSTAutoPos", 0);
	gameConf.chkRandomPos = irr::android::getIntSetting(appMain, "chkRandomPos", 0);
	gameConf.chkAutoChain = irr::android::getIntSetting(appMain, "chkAutoChain", 0);
	gameConf.chkWaitChain = irr::android::getIntSetting(appMain, "chkWaitChain", 0);
	//system
	gameConf.chkIgnore1 = irr::android::getIntSetting(appMain, "chkIgnore1", 0);
	gameConf.chkIgnore2 = irr::android::getIntSetting(appMain, "chkIgnore2", 0);
	gameConf.control_mode = irr::android::getIntSetting(appMain, "control_mode", 0);
	gameConf.draw_field_spell = irr::android::getIntSetting(appMain, "draw_field_spell", 1);
	gameConf.chkIgnoreDeckChanges = irr::android::getIntSetting(appMain, "chkIgnoreDeckChanges", 0);
	gameConf.auto_save_replay = irr::android::getIntSetting(appMain, "auto_save_replay", 0);
	gameConf.quick_animation = irr::android::getIntSetting(appMain, "quick_animation", 0);
	gameConf.draw_single_chain = irr::android::getIntSetting(appMain, "draw_single_chain", 0);
	gameConf.enable_sound = irr::android::getIntSetting(appMain, "enable_sound", 1);
	gameConf.sound_volume = irr::android::getIntSetting(appMain, "sound_volume", 50);
	gameConf.enable_music = irr::android::getIntSetting(appMain, "enable_music", 1);
	gameConf.music_volume = irr::android::getIntSetting(appMain, "music_volume", 50);
	gameConf.music_mode = irr::android::getIntSetting(appMain, "music_mode", 1);
    // 加载是否启用常规禁限卡表的开关值
	gameConf.use_lflist = irr::android::getIntSetting(appMain, "use_lflist", 1);
    // 加载是否启用genesys禁限卡表的开关值
	gameConf.use_genesys_lflist = irr::android::getIntSetting(appMain, "use_genesys_lflist", 1);

	gameConf.chkDefaultShowChain = irr::android::getIntSetting(appMain, "chkDefaultShowChain", 0);
	gameConf.hide_player_name = irr::android::getIntSetting(appMain, "chkHidePlayerName", 0);
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
    irr::android::saveIntSetting(appMain, "textfontsize", gameConf.textfontsize);
	//helper
	gameConf.chkMAutoPos = chkMAutoPos->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "chkMAutoPos", gameConf.chkMAutoPos);
	gameConf.chkSTAutoPos = chkSTAutoPos->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "chkSTAutoPos", gameConf.chkSTAutoPos);
	gameConf.chkRandomPos = chkRandomPos->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "chkRandomPos", gameConf.chkRandomPos);
	gameConf.chkAutoChain = chkAutoChain->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "chkAutoChain", gameConf.chkAutoChain);
    gameConf.chkWaitChain = chkWaitChain->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "chkWaitChain", gameConf.chkWaitChain);

	//system
    //保存启用起源点数模式的勾选值
    gameConf.enable_genesys_mode = chkEnableGenesysMode->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "enable_genesys_mode", gameConf.enable_genesys_mode);

    gameConf.chkIgnore1 = chkIgnore1->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "chkIgnore1", gameConf.chkIgnore1);
	gameConf.chkIgnore2 = chkIgnore2->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "chkIgnore2", gameConf.chkIgnore2);
	gameConf.chkIgnoreDeckChanges = chkIgnoreDeckChanges->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "chkIgnoreDeckChanges", gameConf.chkIgnoreDeckChanges);
	gameConf.auto_save_replay = chkAutoSaveReplay->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "auto_save_replay", gameConf.auto_save_replay);
	gameConf.draw_field_spell = chkDrawFieldSpell->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "draw_field_spell", gameConf.draw_field_spell);
    gameConf.quick_animation = chkQuickAnimation->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "quick_animation", gameConf.quick_animation);
	gameConf.draw_single_chain = chkDrawSingleChain->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "draw_single_chain", gameConf.draw_single_chain);
	gameConf.enable_sound = chkEnableSound->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "enable_sound", gameConf.enable_sound);
	gameConf.enable_music = chkEnableMusic->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "enable_music", gameConf.enable_music);
	gameConf.music_mode = chkMusicMode->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "music_mode", gameConf.music_mode);
	gameConf.sound_volume = (double)scrSoundVolume->getPos();
    irr::android::saveIntSetting(appMain, "sound_volume", gameConf.sound_volume);
	gameConf.music_volume = (double)scrMusicVolume->getPos();
    irr::android::saveIntSetting(appMain, "music_volume", gameConf.music_volume);
    // 保存启用LFLIST的勾选值
	gameConf.use_lflist = chkLFlist->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "use_lflist", gameConf.use_lflist);
    // 保存启用Genesys LFLIST的勾选值
	gameConf.use_genesys_lflist = chkGenesysLFlist->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "use_genesys_lflist", gameConf.use_genesys_lflist);

    gameConf.chkDefaultShowChain = chkDefaultShowChain->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "chkDefaultShowChain", gameConf.chkDefaultShowChain);
	gameConf.hide_player_name  = chkHidePlayerName->isChecked() ? 1 : 0;
    irr::android::saveIntSetting(appMain, "chkHidePlayerName", gameConf.hide_player_name);
//  gameConf.control_mode = control_mode->isChecked()?1:0;
//	android::saveIntSetting(appMain, "control_mode", gameConf.control_mode);
}

/**
 * @brief 显示指定卡牌的信息。
 *
 * 此函数用于在游戏界面中显示一张卡牌的详细信息，包括图像、名称、类型、种族、属性、攻击力/守备力等，
 * 并根据卡牌是否有效（存在于数据表中）以及其具体类型（怪兽卡、魔法卡、陷阱卡等）进行不同的处理。
 * 同时会设置文本区域以展示卡牌描述，并调整控件位置与缩放。
 *
 * @param code 卡牌编号，用作查找卡牌数据的关键字。
 */
void Game::ShowCardInfo(int code) {
    // 定义格式化缓冲区和获取卡牌数据表引用
    wchar_t formatBuffer[256];
    auto& _datas = dataManager.GetDataTable();
    auto cit = _datas.find(code);
    bool is_valid = (cit != _datas.end());

    // 设置卡片图片并启用自动缩放
    imgCard->setImage(imageManager.GetTexture(code));
    imgCard->setScaleImage(true);

    // 根据卡牌是否存在决定如何显示名称：若存在且是替代卡则使用别名
    if (is_valid) {
        auto& cd = cit->second;
        if (is_alternative(cd.code, cd.alias))
            myswprintf(formatBuffer, L"%ls[%08d]", dataManager.GetName(cd.alias), cd.alias);
        else
            myswprintf(formatBuffer, L"%ls[%08d]", dataManager.GetName(code), code);
    }
    else {
        myswprintf(formatBuffer, L"%ls[%08d]", dataManager.GetName(code), code);
    }

    // 设置名称标签文本及提示工具文本（当文字超出宽度时）
    stName->setText(formatBuffer);
    if ((int)guiFont->getDimension(formatBuffer).Width > stName->getRelativePosition().getWidth() - gameConf.textfontsize)
        stName->setToolTipText(formatBuffer);
    else
        stName->setToolTipText(nullptr);

    // 字段名显示逻辑
    int offset = 0;
    if (is_valid && !gameConf.hide_setname) {
        auto& cd = cit->second;
        auto target = cit;
        // 若当前卡有同名卡并且该同名卡存在于数据库中，则使用该同名卡对应的数据
        if (cd.alias && _datas.find(cd.alias) != _datas.end()) {
            target = _datas.find(cd.alias);
        }
        // 如果目标卡具有字段码，则构造并显示字段名
        if (target->second.setcode[0]) {
            offset = 23; // 固定偏移量用于布局调整
            const auto& setname = dataManager.FormatSetName(target->second.setcode);
            myswprintf(formatBuffer, L"%ls%ls", dataManager.GetSysString(1329), setname.c_str()); // 字段：
            stSetName->setText(formatBuffer);
        }
        else
            stSetName->setText(L"");
    }
    else {
        stSetName->setText(L"");
    }

    // 怪物卡信息处理分支
    if (is_valid && cit->second.type & TYPE_MONSTER) {
        auto& cd = cit->second;

        // 构造并显示怪物卡的基本信息（类型、种族、属性）
        const auto& type = dataManager.FormatType(cd.type);
        const auto& race = dataManager.FormatRace(cd.race);
        const auto& attribute = dataManager.FormatAttribute(cd.attribute);
        myswprintf(formatBuffer, L"[%ls] %ls/%ls", type.c_str(), race.c_str(), attribute.c_str());
        stInfo->setText(formatBuffer);

        // 判断文本长度是否需要换行偏移
        int offset_info = 0;
        irr::core::dimension2d<unsigned int> dtxt = guiFont->getDimension(formatBuffer);
        if (dtxt.Width > (300 * xScale - 13) - 15)
            offset_info = 15;

        // 准备等级符号和攻防数值字符串
        const wchar_t* form = L"\u2605"; // 默认星数标记
        wchar_t adBuffer[64]{};
        wchar_t scaleBuffer[16]{};

        // 非连接卡处理
        if (!(cd.type & TYPE_LINK)) {
            if (cd.type & TYPE_XYZ)
                form = L"\u2606"; // XYZ卡使用不同标记

            // 攻击力/防御力未知情况下的特殊处理
            if (cd.attack < 0 && cd.defense < 0)
                myswprintf(adBuffer, L"?/?");
            else if (cd.attack < 0)
                myswprintf(adBuffer, L"?/%d", cd.defense);
            else if (cd.defense < 0)
                myswprintf(adBuffer, L"%d/?", cd.attack);
            else
                myswprintf(adBuffer, L"%d/%d", cd.attack, cd.defense);
        }
        // 连接卡处理
        else {
            form = L"LINK-";
            const auto& link_marker = dataManager.FormatLinkMarker(cd.link_marker);
            if (cd.attack < 0)
                myswprintf(adBuffer, L"?/-   %ls", link_marker.c_str());
            else
                myswprintf(adBuffer, L"%d/-   %ls", cd.attack, link_marker.c_str());
        }

        // 摆钟卡额外显示左右刻度
        if (cd.type & TYPE_PENDULUM) {
            myswprintf(scaleBuffer, L"   %d/%d", cd.lscale, cd.rscale);
        }

        // 组合最终数据信息并设置到界面上
        myswprintf(formatBuffer, L"[%ls%d] %ls%ls", form, cd.level, adBuffer, scaleBuffer);
        stDataInfo->setText(formatBuffer);

        // 调整控件的位置
        stSetName->setRelativePosition(Resize(10, 83, 250, 106));
        stText->setRelativePosition(Resize(10, 83 + offset, 251, 340));
        scrCardText->setRelativePosition(Resize(255, 83 + offset, 258, 340));
    }else {// 非怪物卡或无效卡处理
        if (is_valid) {
            const auto& type = dataManager.FormatType(cit->second.type);
            myswprintf(formatBuffer, L"[%ls]", type.c_str());
        }
        else
            myswprintf(formatBuffer, L"[%ls]", dataManager.unknown_string);

        stInfo->setText(formatBuffer);
        stDataInfo->setText(L"");

        // 调整控件位置适应非怪物卡布局
        stSetName->setRelativePosition(Resize(10, 60, 250, 106));
        stText->setRelativePosition(Resize(10, 60 + offset, 251, 340));
        scrCardText->setRelativePosition(Resize(255, 60 + offset, 258, 340));
    }

    // 获取并初始化卡牌描述文本
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

std::string WCharToUTF8(const wchar_t* input) {
    std::string output;
    if(input == nullptr) return output;
    char buffer[1024];
    wcstombs(buffer, input, 1024);
    output = buffer;
    return output;
}

void Game::AddDebugMsg(const char* msg) {
    std::string message(msg);
    unsigned int cardID = 0;
    std::regex cardIdPattern(R"((\d{3,9}))");
    auto words_begin = std::sregex_iterator(message.begin(), message.end(), cardIdPattern);
    auto words_end = std::sregex_iterator();
    for (std::sregex_iterator i = words_begin; i != words_end; ++i) {
        std::smatch match = *i;
        cardID = std::stoul(match.str());
        break;
    }
    std::string cardName = "Unknown";
    if(cardID != 0) {
        cardName = WCharToUTF8(dataManager.GetName(cardID));
    }
    std::string fullMsg;
    if(cardID != 0 && cardName != "???") {
        fullMsg += cardName;
        fullMsg += ":";
    } else {
        fullMsg += "";
    }
    fullMsg += message;
	if (enable_log & 0x1) {
		wchar_t wbuf[1024];
        BufferIO::DecodeUTF8(fullMsg.c_str(), wbuf);
		AddChatMsg(wbuf, 9);
	}
	if (enable_log & 0x2) {
		char msgbuf[1040];
        mysnprintf(msgbuf, "[Script Error]: %s", fullMsg.c_str());
		ErrorLog(msgbuf);
	}
}
void Game::ErrorLog(const char* msg) {
	std::fprintf(stderr, "%s\n", msg);
	FILE* fp = myfopen("error.log", "a");
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
	irr::s32 x = 305;
	if(is_building) x = 802;
	wChat->setRelativePosition(Resize(x, GAME_HEIGHT - 35, GAME_WIDTH - 4, GAME_HEIGHT));
	ebChatInput->setRelativePosition(irr::core::recti(3 * xScale, 2 * yScale, (GAME_WIDTH - 6) * xScale - wChat->getRelativePosition().UpperLeftCorner.X, 28 * yScale));
}
irr::core::recti Game::Resize(irr::s32 x, irr::s32 y, irr::s32 x2, irr::s32 y2) {
	x = x * xScale;
	y = y * yScale;
	x2 = x2 * xScale;
	y2 = y2 * yScale;
	return irr::core::recti(x, y, x2, y2);
}
irr::core::recti Game::Resize(irr::s32 x, irr::s32 y, irr::s32 x2, irr::s32 y2, irr::s32 dx, irr::s32 dy, irr::s32 dx2, irr::s32 dy2) {
	x = x * xScale + dx;
	y = y * yScale + dy;
	x2 = x2 * xScale + dx2;
	y2 = y2 * yScale + dy2;
	return irr::core::recti(x, y, x2, y2);
}
irr::core::vector2di Game::Resize(irr::s32 x, irr::s32 y) {
	x = x * xScale;
	y = y * yScale;
	return irr::core::vector2di(x, y);
}
irr::core::vector2di Game::ResizeReverse(irr::s32 x, irr::s32 y) {
	x = x / xScale;
	y = y / yScale;
	return irr::core::vector2di(x, y);
}
irr::core::recti Game::ResizeWin(irr::s32 x, irr::s32 y, irr::s32 x2, irr::s32 y2) {
	irr::s32 w = x2 - x;
	irr::s32 h = y2 - y;
	x = (x + w / 2) * xScale - w / 2;
	y = (y + h / 2) * yScale - h / 2;
	x2 = w + x;
	y2 = h + y;
	return irr::core::recti(x, y, x2, y2);
}
irr::core::recti Game::ResizePhaseHint(irr::s32 x, irr::s32 y, irr::s32 x2, irr::s32 y2, irr::s32 width) {
	x = x * xScale - width / 2;
	y = y * yScale;
	x2 = x2 * xScale;
	y2 = y2 * yScale;
	return irr::core::recti(x, y, x2, y2);
}
irr::core::recti Game::Resize_Y(irr::s32 x, irr::s32 y, irr::s32 x2, irr::s32 y2) {
    x = x * yScale;
	y = y * yScale;
    x2 = x2 * yScale;
    y2 = y2 * yScale;
	return irr::core::recti(x, y, x2, y2);
}
irr::core::recti Game::Resize_X_Y(irr::s32 x, irr::s32 y, irr::s32 x2, irr::s32 y2) {
    irr::s32 w = x2 - x;
    x = x * xScale;
    y = y * yScale;
    x2 = x + w * yScale;
    y2 = y2 * yScale;
    return irr::core::recti(x, y, x2, y2);
}
irr::core::vector2di Game::Resize_Y(irr::s32 x, irr::s32 y) {
    x = x * yScale;
    y = y * yScale;
	return irr::core::vector2di(x, y);
}
void Game::ChangeToIGUIImageWindow(irr::gui::IGUIWindow* window, irr::gui::IGUIImage** pWindowBackground, irr::video::ITexture* image) {
    window->setDrawBackground(false);
    irr::core::recti pos = window->getRelativePosition();
	*pWindowBackground = env->addImage(irr::core::rect<irr::s32>(0, 0, pos.getWidth(), pos.getHeight()), window, -1, 0, true);
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
	irr::android::onGameExit(appMain);
    this->device->closeDevice();
}
}
