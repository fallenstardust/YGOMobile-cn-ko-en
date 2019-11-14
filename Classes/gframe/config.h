#ifndef __CONFIG_H
#define __CONFIG_H

#pragma once
#ifndef __GAME_CONFIG
#define _IRR_STATIC_LIB_
#define IRR_COMPILE_WITH_DX9_DEV_PACK
#define _IRR_ANDROID_PLATFORM_

#ifdef _IRR_ANDROID_PLATFORM_

#include <android_native_app_glue.h>
#include <android/android_tools.h>
#endif
#ifdef _WIN32

#include <WinSock2.h>
#include <windows.h>
#include <ws2tcpip.h>

#ifdef _MSC_VER
#define myswprintf _swprintf
#define mywcsncasecmp _wcsnicmp
#define mystrncasecmp _strnicmp
#else
#define myswprintf swprintf
#define mywcsncasecmp wcsncasecmp
#define mystrncasecmp strncasecmp
#endif

#define socklen_t int

#else //_WIN32

#include <errno.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <unistd.h>

#define SD_BOTH 2
#define SOCKET int
#define closesocket close
#define INVALID_SOCKET -1
#define SOCKET_ERROR -1
#define SOCKADDR_IN sockaddr_in
#define SOCKADDR sockaddr
#define SOCKET_ERRNO() (errno)

#include <wchar.h>
#ifdef _IRR_ANDROID_PLATFORM_
#include <android/xstring.h>
#define myswprintf(buf, fmt, ...) swprintf_x(buf, 4096, fmt, ##__VA_ARGS__)
#define _wtoi wtoi_x
#define mywcscat wcscat_x
#else
#define myswprintf(buf, fmt, ...) swprintf(buf, 4096, fmt, ##__VA_ARGS__)
#define mywcsncasecmp wcsncasecmp
#define mystrncasecmp strncasecmp
inline int _wtoi(const wchar_t * s) {
	wchar_t * endptr;
	return (int)wcstol(s, &endptr, 10);
}
#endif
#endif

#ifndef TEXT
#ifdef UNICODE
#define TEXT(x) L##x
#else
#define TEXT(x) x
#endif // UNICODE
#endif

#include <irrlicht.h>
#ifdef _IRR_ANDROID_PLATFORM_
#include <GLES/gl.h>
#include <GLES/glext.h>
#include <GLES/glplatform.h>
#else
#include <GL/gl.h>
#include <GL/glu.h>
#endif
#include "CGUITTFont.h"
#include "CGUIImageButton.h"
#include <iostream>
#include <stdio.h>
#include <stdlib.h>
#include <memory.h>
#include <time.h>
#include <thread>
#include <mutex>
#ifdef _IRR_ANDROID_PLATFORM_
#include <android/bufferio_android.h>
#else
#include "bufferio.h"
#endif
#include "myfilesystem.h"
#include "mysignal.h"
#include "../ocgcore/ocgapi.h"
#include "../ocgcore/common.h"

#ifdef _IRR_ANDROID_PLATFORM_
#include "os.h"
#endif

#if defined(_IRR_ANDROID_PLATFORM_)
#include <android/CustomShaderConstantSetCallBack.h>
#endif

using namespace irr;
using namespace core;
using namespace scene;
using namespace video;
using namespace io;
using namespace gui;
using namespace os;

extern const unsigned short PRO_VERSION;
extern int enable_log;
extern bool exit_on_return;
extern bool bot_mode;
#endif
#endif
