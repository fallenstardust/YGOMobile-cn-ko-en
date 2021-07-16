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
    android::InitOptions *options = android::getInitOptions(app);
	if(!ygo::mainGame->Initialize(app, options)){
		delete options;
		return;
	}
	int argc = options->getArgc();
	irr::io::path* argv = options->getArgv();
#endif
/*command line args:
 * -j: join host (host info from system.conf)
 * -d: deck edit
 * -r: replay
 */
#ifdef _IRR_ANDROID_PLATFORM_
    //android
    for(int i = 0; i < argc; ++i) {
		char* arg = argv[i].c_str();
#else
    //pc的第一个是exe的路径
    for(int i = 1; i < argc; ++i) {
        char* arg = argv[i];
#endif
		if(arg[0] == '-' && arg[1] == 'e') {
			ygo::dataManager.LoadDB(&arg[2]);
		} else if(!strcmp(arg, "-j") || !strcmp(arg, "-d") || !strcmp(arg, "-r") || !strcmp(arg, "-s")) {
			exit_on_return = true;
			irr::SEvent event;
			event.EventType = irr::EET_GUI_EVENT;
			event.GUIEvent.EventType = irr::gui::EGET_BUTTON_CLICKED;
		} else if(!strcmp(arg, "-c")) { // Create host
			ygo::mainGame->HideElement(ygo::mainGame->wMainMenu);
			event.GUIEvent.Caller = ygo::mainGame->btnJoinHost;
			ygo::mainGame->device->postEventFromUser(event);
			break;
		} else if(!strcmp(arg, "-j")) { // Join host
			event.GUIEvent.Caller = ygo::mainGame->btnJoinHost;
			ygo::mainGame->HideElement(ygo::mainGame->wMainMenu);
			ygo::mainGame->device->postEventFromUser(event);
			break;
		} else if(!strcmp(arg, "-r")) { // Replay
			char* name = NULL;
			if((i+1) < argc){//下一个参数是录像名
#ifdef _IRR_ANDROID_PLATFORM_
		        name = argv[i+1].c_str();
#else
                name = argv[i+1];
#endif
			}
			event.GUIEvent.Caller = ygo::mainGame->btnReplayMode;
			ygo::mainGame->device->postEventFromUser(event);
			if(name != NULL){
				//TODO may be error?
				ygo::mainGame->lstReplayList->setSelected(name);
			} else {
				ygo::mainGame->lstReplayList->setSelected(0);
			}
			event.GUIEvent.Caller = ygo::mainGame->btnLoadReplay;
			ygo::mainGame->device->postEventFromUser(event);
			break;//只播放一个
		} else if(!strcmp(arg, "-s")) { // Single
			event.GUIEvent.Caller = ygo::mainGame->btnSingleMode;
			ygo::mainGame->device->postEventFromUser(event);
			ygo::mainGame->lstSinglePlayList->setSelected(0);
			event.GUIEvent.Caller = ygo::mainGame->btnLoadSinglePlay;
			ygo::mainGame->device->postEventFromUser(event);
			break;
		}
	}
#ifdef _IRR_ANDROID_PLATFORM_
	delete options;
#endif
	ygo::mainGame->externalSignal.Set();
	ygo::mainGame->externalSignal.SetNoWait(true);
	ygo::mainGame->MainLoop();
	return;
}
