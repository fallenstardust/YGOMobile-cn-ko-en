#include "config.h"
#include "game.h"
#include "data_manager.h"
#include <event2/thread.h>
#include <locale.h>
#include <memory>

unsigned int enable_log = 0x3;
bool exit_on_return = false;
bool bot_mode = false;
bool open_file = false;
wchar_t open_file_name[256] = L"";

void ClickButton(irr::gui::IGUIElement* btn) {
	irr::SEvent event;
	event.EventType = irr::EET_GUI_EVENT;
	event.GUIEvent.EventType = irr::gui::EGET_BUTTON_CLICKED;
	event.GUIEvent.Caller = btn;
	ygo::mainGame->device->postEventFromUser(event);
}
char* sub_string(const char* str, int start, int count = -1){
	char* tmp = new char[1024];
	int len = strlen(str);
	int index = 0;
	if(count < 0) {
		count = len - start;
	}
	for (int j = start; j < len && count > 0; count--, j++) {
		tmp[index++] = str[j];
	}
	tmp[index] = '\0';
	return tmp;
}
#ifdef _IRR_ANDROID_PLATFORM_
int GetListBoxIndex(IGUIListBox* listbox, const wchar_t * target){
	int count = listbox->getItemCount();
    ALOGD("open deck file:for count=%d",count);
	for(int i = 0; i < count; i++){
		auto item = listbox->getListItem(i);
        ALOGD("open deck file:for name=%ls,name=%ls", item,target);
		if(std::wcscmp(item, target) == 0){
			return i;
		}
	}
	return -1;
}
void android_main(ANDROID_APP app) {
	app->inputPollSource.process = android::process_input;
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
 * -s：single
 */

    bool keep_on_return = false;
	bool deckCategorySpecified = false;
#ifdef _IRR_ANDROID_PLATFORM_
	ALOGD("cc gframe: handle args %d", argc);
	int wargc = argc;
	auto wargv = std::make_unique<wchar_t[][256]>(wargc);
    //android
    for(int i = 0; i < argc; ++i) {
		const char* arg = argv[i].c_str();
		BufferIO::DecodeUTF8(arg, wargv[i]);
#else
		int wargc;
	std::unique_ptr<wchar_t*[], void(*)(wchar_t**)> wargv(CommandLineToArgvW(GetCommandLineW(), &wargc), [](wchar_t** wargv) {
		LocalFree(wargv);
	});
    //pc的第一个是exe的路径
    for(int i = 1; i < argc; ++i) {
        char* arg = argv[i];
#endif

        if (arg[0] == '-' && arg[1] == 'e') {
			wchar_t fname[1024];
			char* tmp = sub_string(arg, 2);
			BufferIO::DecodeUTF8(tmp, fname);
			ygo::dataManager.LoadDB(fname);
			delete tmp;
		} else if(!strcmp(arg, "-k")) { // Keep on return
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
			//显示录像窗口
			ygo::mainGame->HideElement(ygo::mainGame->wMainMenu);
			ygo::mainGame->ShowElement(ygo::mainGame->wReplay);
			ygo::mainGame->ebRepStartTurn->setText(L"1");
			ygo::mainGame->stReplayInfo->setText(L"");
			ygo::mainGame->RefreshReplay();
		    int index = -1;
			if((i+1) < argc){//下一个参数是录像名
#ifdef _IRR_ANDROID_PLATFORM_
		        const char* name = argv[i+1].c_str();
#else
                char* name = argv[i+1];
#endif

                wchar_t fname[1024];
                BufferIO::DecodeUTF8(name, fname);
                index = GetListBoxIndex(ygo::mainGame->lstReplayList, fname);
				ALOGD("cc gframe: open replay file:index=%d, name=%s", index, name);
			}
            ygo::mainGame->HideElement(ygo::mainGame->wMainMenu);
            ClickButton(ygo::mainGame->btnReplayMode);
			if (index >= 0) {
                ygo::mainGame->lstReplayList->setSelected(index);
                ClickButton(ygo::mainGame->btnLoadReplay);
            }

            break;//只播放一个
		} else if(!strcmp(arg, "-s")) { // Single
		    exit_on_return = !keep_on_return;
			//显示残局窗口
			ygo::mainGame->HideElement(ygo::mainGame->wMainMenu);
			ygo::mainGame->ShowElement(ygo::mainGame->wSinglePlay);
			ygo::mainGame->RefreshSingleplay();
			ygo::mainGame->RefreshBot();
			int index = -1;
			if((i+1) < argc){//下一个参数是文件名
#ifdef _IRR_ANDROID_PLATFORM_
		        const char* name = argv[i+1].c_str();
#else
                char* name = argv[i+1];
#endif
                wchar_t fname[1024];
                BufferIO::DecodeUTF8(name, fname);
                index = GetListBoxIndex(ygo::mainGame->lstSinglePlayList, fname);
				ALOGD("cc gframe: open single file:index=%d, name=%s", index, name);
			}
			if(index >= 0){
				ygo::mainGame->lstSinglePlayList->setSelected(index);
			    ClickButton(ygo::mainGame->btnLoadSinglePlay);
			}
			break;
		} else if(!strcmp(arg, "--deck-category")) {
			++i;
			if(i < argc) {
				deckCategorySpecified = true;
				BufferIO::DecodeUTF8(argv[i].c_str(), wargv[i]);
				wcscpy(ygo::mainGame->gameConf.lastcategory, wargv[i]);
				ALOGD("open deck file:selct lastcategory=%ls", wargv[i]);
			}
		}else if(!strcmp(arg, "-d")) { // Deck
			ALOGD("open deck file:index=%d size=%d", i,wargc);

			if(!deckCategorySpecified)
				ygo::mainGame->gameConf.lastcategory[0] = 1;
			if(i + 1 < wargc) { // select deck
                BufferIO::DecodeUTF8(argv[i+1].c_str(), wargv[i+1]);
				wcscpy(ygo::mainGame->gameConf.lastdeck, wargv[i+1]);
				ALOGD("open deck file:selct name=%ls,cate=%ls", wargv[i],wargv[i+1]);
				ClickButton(ygo::mainGame->btnDeckEdit);
				break;
			} else { // open deck
				exit_on_return = !keep_on_return;
				if(i < wargc) {
					open_file = true;
					if(deckCategorySpecified) {
#ifdef WIN32
						myswprintf(open_file_name, L"%ls\\%ls", ygo::mainGame->gameConf.lastcategory, wargv[i]);
#else
						myswprintf(open_file_name, L"%ls/%ls", ygo::mainGame->gameConf.lastcategory, wargv[i]);
#endif
						ALOGD("open deck file:open name=%ls", open_file_name);
					} else {
						wcscpy(open_file_name, wargv[i]);
						ALOGD("open deck file:open name1=%ls", wargv[i]);
					}
				}
				ClickButton(ygo::mainGame->btnDeckEdit);
				break;
			}
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
