#include "config.h"
#include "game.h"
#include "data_manager.h"
#include <event2/thread.h>
#include <memory>

int enable_log = 0;
bool exit_on_return = false;
bool bot_mode = false;

#ifdef _IRR_ANDROID_PLATFORM_
void android_main(ANDROID_APP app) {
	app->inputPollSource.process = android::process_input;
	app_dummy();
#else
int main(int argc, char* argv[]) {
#endif
	evthread_use_pthreads();
	ygo::Game _game;
	ygo::mainGame = &_game;
#ifdef _IRR_ANDROID_PLATFORM_
	if(!ygo::mainGame->Initialize(app))
		return;
#endif
#ifndef _IRR_ANDROID_PLATFORM_
	for(int i = 1; i < argc; ++i) {
		/*command line args:
		 * -j: join host (host info from system.conf)
		 * -d: deck edit
		 * -r: replay */
		if(argv[i][0] == '-' && argv[i][1] == 'e') {
#ifdef _IRR_ANDROID_PLATFORM_
			wchar_t fname[260];
			MultiByteToWideChar(CP_ACP, 0, &argv[i][2], -1, fname, 260);
			char fname2[260];
			BufferIO::EncodeUTF8(fname, fname2);
			if(ygo::dataManager.LoadDB(fname2)){
				os::Printer::log("add cdb ok ", fname2);
			}else{
				os::Printer::log("add cdb fail ", fname2);
			}
#else
			ygo::dataManager.LoadDB(&argv[i][2]);
#endif
		} else if(!strcmp(argv[i], "-j") || !strcmp(argv[i], "-d") || !strcmp(argv[i], "-r") || !strcmp(argv[i], "-s")) {
			exit_on_return = true;
			irr::SEvent event;
			event.EventType = irr::EET_GUI_EVENT;
			event.GUIEvent.EventType = irr::gui::EGET_BUTTON_CLICKED;
		} else if(!strcmp(argv[i], "-c")) { // Create host
				ygo::mainGame->HideElement(ygo::mainGame->wMainMenu);
				event.GUIEvent.Caller = ygo::mainGame->btnJoinHost;
				ygo::mainGame->device->postEventFromUser(event);
		} else if(!strcmp(argv[i], "-j")) { // Join host
		        event.GUIEvent.Caller = ygo::mainGame->btnJoinHost;
				ygo::mainGame->HideElement(ygo::mainGame->wMainMenu);
				ygo::mainGame->device->postEventFromUser(event);
		} else if(!strcmp(argv[i], "-r")) { // Replay
				event.GUIEvent.Caller = ygo::mainGame->btnReplayMode;
				ygo::mainGame->device->postEventFromUser(event);
				ygo::mainGame->lstReplayList->setSelected(0);
				event.GUIEvent.Caller = ygo::mainGame->btnLoadReplay;
				ygo::mainGame->device->postEventFromUser(event);
		} else if(!strcmp(argv[i], "-s")) { // Single
				event.GUIEvent.Caller = ygo::mainGame->btnSingleMode;
				ygo::mainGame->device->postEventFromUser(event);
				ygo::mainGame->lstSinglePlayList->setSelected(0);
				event.GUIEvent.Caller = ygo::mainGame->btnLoadSinglePlay;
				ygo::mainGame->device->postEventFromUser(event);
			}

		}
	}
#endif
	ygo::mainGame->externalSignal.Set();
	ygo::mainGame->externalSignal.SetNoWait(true);
	ygo::mainGame->MainLoop();
	return;
}
