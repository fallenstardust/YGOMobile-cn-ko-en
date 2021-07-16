#include "config.h"
#include "game.h"
#include "data_manager.h"
#include <event2/thread.h>
#include <memory>

int enable_log = 0;
bool exit_on_return = false;
bool bot_mode = false;

void ClickButton(irr::gui::IGUIElement* btn) {
	irr::SEvent event;
	event.EventType = irr::EET_GUI_EVENT;
	event.GUIEvent.EventType = irr::gui::EGET_BUTTON_CLICKED;
	event.GUIEvent.Caller = btn;
	ygo::mainGame->device->postEventFromUser(event);
}

#ifdef _IRR_ANDROID_PLATFORM_
int GetListBoxIndex(IGUIListBox* listbox, const wchar_t* target){
	int count = listbox->getItemCount();
	for(int i = 0; i < count; i++){
		auto item = listbox->getListItem(i);
		if(wcscmp(item, target)){
			return i;
		}
	}
	return 0;
}
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
    bool keep_on_return = false;
	bool open_file = false;
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
		} else if(!strcmp(arv, "-k")) { // Keep on return
			exit_on_return = false;
			keep_on_return = true;
		} else if(!strcmp(arg, "-c")) { // Create host
		    exit_on_return = !keep_on_return;
			ygo::mainGame->HideElement(ygo::mainGame->wMainMenu);
			ClickButton(ygo::mainGame->btnJoinHost);
			break;
		} else if(!strcmp(arg, "-j")) { // Join host
		    exit_on_return = !keep_on_return;
			ygo::mainGame->HideElement(ygo::mainGame->wMainMenu);
			ClickButton(ygo::mainGame->btnJoinHost);
			break;
		} else if(!strcmp(arg, "-r")) { // Replay
		    exit_on_return = !keep_on_return;
		    int index = 0;
			if((i+1) < argc){//下一个参数是录像名
#ifdef _IRR_ANDROID_PLATFORM_
		        char* name = argv[i+1].c_str();
#else
                char* name = argv[i+1];
#endif
			    wchar_t fname[1024];
			    BufferIO::DecodeUTF8(name, fname);
				open_file = true;

                index = GetListBoxIndex(ygo::mainGame->lstReplayList, fname);
			}
			ClickButton(ygo::mainGame->btnReplayMode);
			if(open_file){
				ygo::mainGame->lstReplayList->setSelected(index);
			    ClickButton(ygo::mainGame->btnLoadReplay);
			}
			break;//只播放一个
		} else if(!strcmp(arg, "-s")) { // Single
		    exit_on_return = !keep_on_return;

			int index = 0;
			if((i+1) < argc){//下一个参数是文件名
#ifdef _IRR_ANDROID_PLATFORM_
		        char* name = argv[i+1].c_str();
#else
                char* name = argv[i+1];
#endif
			    wchar_t fname[1024];
			    BufferIO::DecodeUTF8(name, fname);
				open_file = true;

                index = GetListBoxIndex(ygo::mainGame->lstReplayList, fname);
			}
			ClickButton(ygo::mainGame->btnSingleMode);
			if(open_file){
				ygo::mainGame->lstSinglePlayList->setSelected(index);
			    ClickButton(ygo::mainGame->btnLoadSinglePlay);
			}
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
