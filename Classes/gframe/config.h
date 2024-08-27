#ifndef YGOPRO_CONFIG_H
#define YGOPRO_CONFIG_H

#define _IRR_STATIC_LIB_
#define IRR_COMPILE_WITH_DX9_DEV_PACK
#define _IRR_ANDROID_PLATFORM_

#ifdef _IRR_ANDROID_PLATFORM_
#include <android_native_app_glue.h>
#include <android/android_tools.h>
#endif
#ifdef _WIN32

#define NOMINMAX
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

#ifdef _IRR_ANDROID_PLATFORM_
#include <android/xstring.h>
#define myswprintf(buf, fmt, ...) swprintf_x(buf, 4096, fmt, ##__VA_ARGS__)
#define mywcscat wcscat_x
#endif

#include <wchar.h>
template<size_t N, typename... TR>
inline int swprintf(wchar_t(&buf)[N], const wchar_t* fmt, TR... args) {
	return swprintf(buf, N, fmt, args...);
}

#include <stdio.h>
#include <stdlib.h>
#include <iostream>
#include <algorithm>
#include <string>

#ifdef _IRR_ANDROID_PLATFORM_
#include "os.h"
#include <android/bufferio_android.h>
#endif

#include "myfilesystem.h"
#include "mysignal.h"
#include "../ocgcore/ocgapi.h"
#include "../ocgcore/common.h"

#if defined(_IRR_ANDROID_PLATFORM_)
#include <android/CustomShaderConstantSetCallBack.h>
#endif

#ifndef TEXT
#ifdef UNICODE
#define TEXT(x) L##x
#else
#define TEXT(x) x
#endif // UNICODE
#endif

#include <irrlicht.h>
using namespace irr;
using namespace core;
using namespace scene;
using namespace video;
using namespace io;
using namespace gui;
using namespace os;

extern const unsigned short PRO_VERSION;
extern unsigned int enable_log;
extern bool exit_on_return;
extern bool bot_mode;
#endif
#endif
