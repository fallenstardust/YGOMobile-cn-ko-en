#include "config.h"
#include "game.h"
#include "data_manager.h"
#include <event2/thread.h>
#include "android/AndroidGameHost.h"
#include "android/AndroidGameUI.h"
#include "android/YGOGameOptions.h"

int enable_log = 0;
bool exit_on_return = false;
bool bot_mode = false;

#ifdef _IRR_ANDROID_PLATFORM_


void android_main(ANDROID_APP app){
#else
int main(int argc, char* argv[]) {
#endif
    ygo::Game* game = new ygo::Game;
    ygo::mainGame = game;
#ifdef _WIN32
	WORD wVersionRequested;
	WSADATA wsaData;
	wVersionRequested = MAKEWORD(2, 2);
	WSAStartup(wVersionRequested, &wsaData);
	evthread_use_windows_threads();
#else
	evthread_use_pthreads();
#endif //_WIN32
#ifdef _IRR_ANDROID_PLATFORM_
	LOGI("start Initialize");
	if(!game->Initialize(app))
		return;
#else
	if(!ygo::mainGame->Initialize())
		return 0;
#endif
#ifndef _IRR_ANDROID_PLATFORM_
    for (int i = 1; i < argc; ++i) {
        /*command line args:
         * -j: join host (host info from system.conf)
         * -d: deck edit
         * -r: replay */
        if (argv[i][0] == '-' && argv[i][1] == 'e') {
            ygo::dataManager.LoadDB(&argv[i][2]);
        } else if (!strcmp(argv[i], "-j") || !strcmp(argv[i], "-d") || !strcmp(argv[i], "-r") ||
                   !strcmp(argv[i], "-s")) {
            exit_on_return = true;
            irr::SEvent event;
            event.EventType = irr::EET_GUI_EVENT;
            event.GUIEvent.EventType = irr::gui::EGET_BUTTON_CLICKED;
        } else if (!strcmp(argv[i], "-c")) { // Create host
            ygo::mainGame->HideElement(ygo::mainGame->wMainMenu);
            event.GUIEvent.Caller = ygo::mainGame->btnJoinHost;
            ygo::mainGame->device->postEventFromUser(event);
        } else if (!strcmp(argv[i], "-d")) { // Join host
            event.GUIEvent.Caller = ygo::mainGame->btnDeckEdit;
            //		ygo::mainGame->HideElement(ygo::mainGame->wMainMenu);
            ygo::mainGame->device->postEventFromUser(event);
        } else if (!strcmp(argv[i], "-r")) { // Replay
            event.GUIEvent.Caller = ygo::mainGame->btnReplayMode;
            ygo::mainGame->device->postEventFromUser(event);
            ygo::mainGame->lstReplayList->setSelected(0);
            event.GUIEvent.Caller = ygo::mainGame->btnLoadReplay;
            ygo::mainGame->device->postEventFromUser(event);
        } else if (!strcmp(argv[i], "-s")) { // Single
            event.GUIEvent.Caller = ygo::mainGame->btnSingleMode;
            ygo::mainGame->device->postEventFromUser(event);
            ygo::mainGame->lstSinglePlayList->setSelected(0);
            event.GUIEvent.Caller = ygo::mainGame->btnLoadSinglePlay;
            ygo::mainGame->device->postEventFromUser(event);
        }
    }
#else
    JNIEnv *env = android::getJniEnv(app);
    irr::android::YGOGameOptions* options = ygo::mainGame->gameUI->getJoinOptions(env);
    if(options != NULL){
        irr::SEvent event;
        wchar_t wbuff[256];
        BufferIO::DecodeUTF8(options->getIPAddr(), wbuff);
        game->ebJoinHost->setText(wbuff);

        myswprintf(wbuff, L"%d", options->getPort());
        game->ebJoinPort->setText(wbuff);

        BufferIO::DecodeUTF8(options->getUserName(), wbuff);
        game->ebNickName->setText(wbuff);

        wmemset(wbuff, 0, 256);

        bool bRoomCreate = options->formatGameParams(wbuff);
        if (bRoomCreate) {
            ygo::mainGame->ebJoinPass->setText(wbuff);
        }

        event.EventType = irr::EET_GUI_EVENT;
        event.GUIEvent.EventType = irr::gui::EGET_BUTTON_CLICKED;
        event.GUIEvent.Caller = ygo::mainGame->btnLanMode;
        game->device->postEventFromUser(event);
        usleep(500);
        event.GUIEvent.Caller = ygo::mainGame->btnJoinHost;
        game->device->postEventFromUser(event);
    }
	game->externalSignal.Set();
	game->externalSignal.SetNoWait(true);
#endif
	game->MainLoop();
	delete game;
	game = NULL;
	LOGI("end game");
#ifdef _WIN32
	WSACleanup();
#else

#endif //_WIN32
#ifdef _IRR_ANDROID_PLATFORM_
	return;
#else
	return EXIT_SUCCESS;
#endif
}
