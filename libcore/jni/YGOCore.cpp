//
// Created by user on 2019/10/12.
//

#include <jni.h>
#include <unistd.h>
#include <pthread.h>
#include <android/log.h>
#include <Android/CIrrDeviceAndroid.h>
#include "irrlicht.h"
#include "../Classes/gframe/game.h"
#include "../Classes/gframe/os.h"
#include "../android/YGOGameOptions.h"
#include "../android/bufferio_android.h"
#include "../android/AndroidGameHost.h"

using namespace irr;
using namespace gui;

extern "C" {

JNIEXPORT void JNICALL Java_cn_garymb_ygomobile_core_YGOCore_nativeInsertText(
        JNIEnv* env, jobject, jint handle, jstring textString) {
    if (handle) {
        IrrlichtDevice *device = (IrrlichtDevice *) handle;
        IGUIEnvironment* irrenv = device->getGUIEnvironment();
        IGUIElement* element = irrenv->getFocus();
        if (element && element->getType() == EGUIET_EDIT_BOX) {
            IGUIEditBox* editbox = (IGUIEditBox*) element;
            const char* text = env->GetStringUTFChars(textString, NULL);
            wchar_t content[256];
            BufferIO::DecodeUTF8(text, content);
            editbox->setText(content);
            irrenv->removeFocus(editbox);
            irrenv->setFocus(editbox->getParent());
            SEvent changeEvent;
            changeEvent.EventType = EET_GUI_EVENT;
            changeEvent.GUIEvent.Caller = editbox;
            changeEvent.GUIEvent.Element = 0;
            changeEvent.GUIEvent.EventType = EGET_EDITBOX_CHANGED;
            editbox->getParent()->OnEvent(changeEvent);
            SEvent enterEvent;
            enterEvent.EventType = EET_GUI_EVENT;
            enterEvent.GUIEvent.Caller = editbox;
            enterEvent.GUIEvent.Element = 0;
            enterEvent.GUIEvent.EventType = EGET_EDITBOX_ENTER;
            editbox->getParent()->OnEvent(enterEvent);
            env->DeleteLocalRef(textString);
        }
    }
}

JNIEXPORT void JNICALL Java_cn_garymb_ygomobile_core_YGOCore_nativeSetComboBoxSelection(
        JNIEnv* env, jobject , jint handle, jint idx) {
    if (handle) {
        IrrlichtDevice *device = (IrrlichtDevice *) handle;
        IGUIEnvironment* irrenv = device->getGUIEnvironment();
        IGUIElement* element = irrenv->getFocus();
        if (element && element->getParent()->getType() == EGUIET_COMBO_BOX) {
            IGUIComboBox* combo = (IGUIComboBox*) (element->getParent());
            core::list<IGUIElement*> children = combo->getChildren();
            core::list<IGUIElement*>::Iterator current = children.begin();
            do {
                if ((*current)->getType() == EGUIET_LIST_BOX) {
                    break;
                }
                current++;
            } while (current != children.end());
            if (current == children.end()) {
                return;
            }
            IGUIListBox* list = (IGUIListBox*) *current;
            list->setSelected(idx);
            SEvent changeEvent;
            changeEvent.EventType = EET_GUI_EVENT;
            changeEvent.GUIEvent.Caller = list;
            changeEvent.GUIEvent.Element = 0;
            changeEvent.GUIEvent.EventType = EGET_LISTBOX_CHANGED;
            combo->OnEvent(changeEvent);
        }
    }
}

JNIEXPORT void JNICALL Java_cn_garymb_ygomobile_core_YGOCore_nativeSetCheckBoxesSelection(
        JNIEnv* env, jobject, jint handle, jint idx) {
    if (handle) {
        IrrlichtDevice *device = (IrrlichtDevice *) handle;
        IGUIEnvironment* irrenv = device->getGUIEnvironment();
        IGUIElement* element = irrenv->getFocus();
        if (element) {
            IGUIWindow* window = (IGUIWindow*) (element);
            core::list<IGUIElement*> children = window->getChildren();
            core::list<IGUIElement*>::Iterator current = children.begin();
            int i = 0;
            do {
                if ((*current)->getType() == EGUIET_CHECK_BOX && i++ == idx) {
                    break;
                }
                current++;
            } while (current != children.end());
            if (current == children.end()) {
                return;
            }
            IGUICheckBox* checkbox = (IGUICheckBox*) *current;
            checkbox->setChecked(true);
            SEvent e;
            e.EventType = EET_GUI_EVENT;
            e.GUIEvent.Caller = checkbox;
            e.GUIEvent.Element = 0;
            e.GUIEvent.EventType = EGET_CHECKBOX_CHANGED;
            window->OnEvent(e);
            irrenv->setFocus(window);
        }
    }
}

JNIEXPORT void JNICALL Java_cn_garymb_ygomobile_core_YGOCore_nativeRefreshTexture(
        JNIEnv* env, jobject, jint handle) {
    if (handle) {
        IrrlichtDevice *device = (IrrlichtDevice *) handle;
        if (device->isWindowFocused()) {
            irr::os::Printer::log("before send refresh event");
            SEvent event;
            event.EventType = EET_KEY_INPUT_EVENT;
            //just cause a right up event to refresh texture
            event.KeyInput.PressedDown = true;
            event.KeyInput.Shift = false;
            event.KeyInput.Control = false;
            event.KeyInput.Key = KEY_KEY_R;
            device->postEventFromUser(event);
        }
    }
}

JNIEXPORT void JNICALL Java_cn_garymb_ygomobile_core_YGOCore_nativeIgnoreChain(
        JNIEnv* env, jobject, jint handle, jboolean begin) {
    if (handle) {
        IrrlichtDevice *device = (IrrlichtDevice *) handle;
        if (device->isWindowFocused()) {
            irr::os::Printer::log("before send ignore chain");
            SEvent event;
            event.EventType = EET_KEY_INPUT_EVENT;
            //just cause a right up event to refresh texture
            event.KeyInput.PressedDown = begin;
            event.KeyInput.Shift = false;
            event.KeyInput.Control = false;
            event.KeyInput.Key = KEY_KEY_S;
            device->postEventFromUser(event);
        }
    }
}

JNIEXPORT void JNICALL Java_cn_garymb_ygomobile_core_YGOCore_nativeReactChain(
        JNIEnv* env, jobject, jint handle, jboolean begin) {
    if (handle) {
        IrrlichtDevice *device = (IrrlichtDevice *) handle;
        if (device->isWindowFocused()) {
            irr::os::Printer::log("before send react chain");
            SEvent event;
            event.EventType = EET_KEY_INPUT_EVENT;
            //just cause a right up event to refresh texture
            event.KeyInput.PressedDown = begin;
            event.KeyInput.Shift = false;
            event.KeyInput.Control = false;
            event.KeyInput.Key = KEY_KEY_A;
            device->postEventFromUser(event);
        }
    }
}

static void* cancel_chain_thread(void* param) {
    IrrlichtDevice* device = (IrrlichtDevice*) param;
    irr::os::Printer::log("before send cancel chain");
    SEvent downevent;
    downevent.EventType = EET_MOUSE_INPUT_EVENT;
    //just cause a right up event to refresh texture
    downevent.MouseInput.Event = EMIE_RMOUSE_PRESSED_DOWN;
    device->postEventFromUser(downevent);
    usleep(20 * 1000);
    SEvent upevent;
    upevent.EventType = EET_MOUSE_INPUT_EVENT;
    //just cause a right up event to refresh texture
    upevent.MouseInput.Event = EMIE_RMOUSE_LEFT_UP;
    device->postEventFromUser(upevent);
    return NULL;
}


JNIEXPORT void JNICALL Java_cn_garymb_ygomobile_core_YGOCore_nativeCancelChain(
        JNIEnv *env, jobject, jint handle) {
    if(!handle){
        return;
    }
    IrrlichtDevice *device = (IrrlichtDevice *) handle;
    if (device->isWindowFocused()) {
        pthread_t cencelThread;
        pthread_attr_t cancelAttr;
        pthread_attr_init(&cancelAttr);
        pthread_create(&cencelThread, &cancelAttr, cancel_chain_thread,
                       (void *) device);
        pthread_attr_destroy(&cancelAttr);
        pthread_detach(cencelThread);
    }
}
static void* s_init_param_buffer = NULL;

static void* join_game_thread(void* param) {
    ygo::mainGame->externalSignal.Wait();
    ygo::mainGame->gMutex.lock();
    if (ygo::mainGame->dInfo.isStarted) {
        ygo::mainGame->gMutex.unlock();
        return NULL;
    }
    irr::android::YGOGameOptions options = irr::android::YGOGameOptions(param);
    irr::SEvent event;

    wchar_t wbuff[256];
    char linelog[256];
    BufferIO::DecodeUTF8(options.getIPAddr(), wbuff);
    ygo::mainGame->ebJoinHost->setText(wbuff);

    myswprintf(wbuff, L"%d", options.getPort());
    BufferIO::EncodeUTF8(wbuff, linelog);
    ygo::mainGame->ebJoinPort->setText(wbuff);

    irr::os::Printer::log(options.getUserName());
    BufferIO::DecodeUTF8(options.getUserName(), wbuff);
    ygo::mainGame->ebNickName->setText(wbuff);

    wmemset(wbuff, 0, 256);

    bool bRoomCreate = options.formatGameParams(wbuff);
    if (bRoomCreate) {
        BufferIO::EncodeUTF8(wbuff, linelog);
        irr::os::Printer::log(linelog);
        ygo::mainGame->ebJoinPass->setText(wbuff);
    }

    event.EventType = irr::EET_GUI_EVENT;
    event.GUIEvent.EventType = irr::gui::EGET_BUTTON_CLICKED;
    event.GUIEvent.Caller = ygo::mainGame->btnLanMode;
    ygo::mainGame->device->postEventFromUser(event);
//	if (bRoomCreate) {
    // wait for wLanWindow show. if network connection faster than wLanWindow, wLanWindow will still show on duel scene.
    usleep(500);
    event.GUIEvent.Caller = ygo::mainGame->btnJoinHost;
    ygo::mainGame->device->postEventFromUser(event);
    //	}
    exit: ygo::mainGame->gMutex.unlock();

    free(param);
    return NULL;
}

JNIEXPORT void JNICALL Java_cn_garymb_ygomobile_core_YGOCore_nativeJoinGame(
        JNIEnv *env, jobject, jint handle, jobject buffer, jint length) {
    void *data = env->GetDirectBufferAddress(buffer);
    s_init_param_buffer = malloc(length);
    memcpy(s_init_param_buffer, data, length);
    pthread_t joinGameThread;
    pthread_attr_t joinGameAttr;
    pthread_attr_init(&joinGameAttr);
    pthread_create(&joinGameThread, &joinGameAttr, join_game_thread,
                   s_init_param_buffer);
    pthread_attr_destroy(&joinGameAttr);
    pthread_detach(joinGameThread);
}

JNIEXPORT jboolean JNICALL Java_cn_garymb_ygomobile_core_YGOCore_nativeSendTouch(
        JNIEnv* env, jobject, jint handle, jint action, jint x, jint y, jint id
        ){
    if(!handle){
        return false;
    }
    IrrlichtDevice *device = (IrrlichtDevice *) handle;
    SEvent Event;
    Event.EventType = EET_TOUCH_INPUT_EVENT;

    s32 EventAction = action;
    s32 EventType = EventAction & AMOTION_EVENT_ACTION_MASK;

    bool TouchReceived = true;

    switch (EventType) {
        case AMOTION_EVENT_ACTION_DOWN:
        case AMOTION_EVENT_ACTION_POINTER_DOWN:
            Event.TouchInput.Event = ETIE_PRESSED_DOWN;
            break;
        case AMOTION_EVENT_ACTION_MOVE:
            Event.TouchInput.Event = ETIE_MOVED;
            break;
        case AMOTION_EVENT_ACTION_UP:
        case AMOTION_EVENT_ACTION_POINTER_UP:
        case AMOTION_EVENT_ACTION_CANCEL:
            Event.TouchInput.Event = ETIE_LEFT_UP;
            break;
        default:
            TouchReceived = false;
            break;
    }

    if (TouchReceived) {
        // Process all touches for move action.
        Event.TouchInput.ID = id;
        Event.TouchInput.X = x;
        Event.TouchInput.Y = y;

        device->postEventFromUser(Event);
    }
    return TouchReceived?TRUE:FALSE;
}
};