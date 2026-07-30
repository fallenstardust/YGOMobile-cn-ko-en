#ifndef YGOPRO_CONFIG_H
#define YGOPRO_CONFIG_H

#define IRR_COMPILE_WITH_DX9_DEV_PACK

#include <cerrno>
#include <cstdio>
#include <string>

#include "../ocgcore/ocgapi.h"

#define _IRR_ANDROID_PLATFORM_

#ifdef _IRR_ANDROID_PLATFORM_
#include <android_native_app_glue.h>
#include <android/android_tools.h>
#include <android/xstring.h>
#include <strings.h>

#define mywcscat wcscat_x
#define mywcsncasecmp wcsncasecmp_x
#define mystrncasecmp strncasecmp
#include "os.h"
#include <android/bufferio_android.h>
#include <android/CustomShaderConstantSetCallBack.h>
#endif

#ifndef TEXT
#ifdef UNICODE
#define TEXT(x) L##x
#else
#define TEXT(x) x
#endif // UNICODE
#endif

template<size_t N, typename... TR>
inline int myswprintf(wchar_t(&buf)[N], const wchar_t* fmt, TR... args) {
	return std::swprintf(buf, N, fmt, args...);
}
template<size_t N, typename... TR>
inline int mysnprintf(char(&buf)[N], const char* fmt, TR... args) {
	return std::snprintf(buf, N, fmt, args...);
}
template<typename T>
inline T myclamp(T v, T lo, T hi) {
	return (v < lo) ? lo : (hi < v) ? hi : v;
}

#include <irrlicht.h>
using namespace irr::io;
using namespace irr::os;

constexpr uint16_t PRO_VERSION = 0x1362;

#endif // YGOPRO_CONFIG_H
