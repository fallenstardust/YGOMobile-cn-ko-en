#ifndef __XEVOL_STRING_H__
#define __XEVOL_STRING_H__
#include <wchar.h>
#include <stdarg.h>

//#define _USE_ICONV_
//#ifdef _UNIX
//#define wcscspn     wcscspn_x   
//#define wcpncpy		wcpncpy_x
//#define wcpcpy		wcpcpy_x
//#define wcscasecmp	wcscasecmp_x
//#define wcschrnul	wcschrnul_x
//#define wcscmp		wcscmp_x
//#define wcschr		wcschr_x
//#define wcslen_x		wcslen_x
//#define wcscat		wcscat_x
//#define wcsdup		wcsdup_x
//#define wcsncasecmp	wcsncasecmp_x
//#define wcsncat		wcsncat_x
//#define wcsncpy		wcsncpy_x
//#define wcsnlen		wcsnlen_x
//#define wcsstr		wcsstr_x
//#endif

//////////////////////////////////////////////////////////////////////////
size_t     wcscspn_x    (const wchar_t *wcs, const wchar_t *reject);
wchar_t *  wcpncpy_x    (wchar_t *dest, const wchar_t *src , size_t n);
wchar_t *  wcpcpy_x     (wchar_t *dest, const wchar_t *src);
int        wcscasecmp_x (const wchar_t *s1, const wchar_t *s2);
wchar_t *  wcschrnul_x  (const wchar_t *wcs, const wchar_t wc);
int        wcscmp_x     (const wchar_t *s1, const wchar_t *s2);
wchar_t *  wcschr_x     (const wchar_t *wcs, const wchar_t wc);
size_t     wcslen_x     (const wchar_t *s);
wchar_t *  wcscat_x     (wchar_t *dest, const wchar_t *src);
wchar_t *  wcsdup_x     (const wchar_t *s);
int        wcsncasecmp_x(const wchar_t *s1, const wchar_t *s2, size_t n );
wchar_t *  wcsncat_x    (wchar_t *dest, const wchar_t *src, size_t n);
wchar_t *  wcsncpy_x    (wchar_t *dest, const wchar_t *src, size_t n);
size_t     wcsnlen_x    (const wchar_t *s, size_t maxlen);
wchar_t *  wcsstr_x     (const wchar_t *haystack, const wchar_t *needle);
int        swprintf_x   (wchar_t *buffer,size_t count, const wchar_t *fmt , ... );

void       tolower_x    (wchar_t * buffer);
void       toupper_x    (wchar_t * buffer);

float      wtof_x       (const wchar_t * buffer);
int        wtoi_x       (const wchar_t * buffer);

//////////////////////////////////////////////////////////////////////////

#ifdef _WIN32
#define swscanf_x    swscanf_s
#define vsnwprintf_x _vsnwprintf
#define vsnprintf_x  _vsnprintf
#else
#define swscanf_x    swscanf
#define vsnprintf_x  vsnprintf
#endif


//////////////////////////////////////////////////////////////////////////////////////
//Android swprintf and swscanf.
//#if defined(_ANDROID_) || defined(_ANDROID)
#undef swscanf_x
#undef vsnwprintf_x

int        format_value (wchar_t *buffer,size_t count, const wchar_t *fmt , ... );
int        vsnwprintf_x(wchar_t* _out , size_t count , const wchar_t *buf , va_list ap);

#define  swscanf_x(str , strFmt , ...) \
do \
{\
    char  _buf[1024] = {0};\
    std::string ansiStr = XW2A_S(str   , _buf , 1024);\
    std::string ansiFmt = XW2A_S(strFmt, _buf , 1024);\
    sscanf(ansiStr.c_str() , ansiFmt.c_str() , __VA_ARGS__ );\
} while (0);\

#define  scan_value_x(str , strFmt , ...) \
do \
{\
    char  _buf[128] = {0};\
    std::string ansiStr = XW2A_S(str   , _buf , 128);\
    std::string ansiFmt = XW2A_S(strFmt, _buf , 128);\
    sscanf(ansiStr.c_str() , ansiFmt.c_str() , __VA_ARGS__ );\
} while (0);\

//////////////////////////////////////////////////////////////////////////////////////
//#else
//#define format_value   swprintf_x
//#endif
//#if defined(_ANDROID_) || defined(_ANDROID)
///////////////////////////////////////////////////////////////////////////////////////////////


//iconv's wrapper
#ifdef _USE_ICONV_
#define CHARSET_UNICODE16   "UCS-2-INTERNAL"
#define CHARSET_UNICODE32   "UCS-4-INTERNAL"
#define CHARSET_UTF8        "UTF-8"
#define CHARSET_UTF16       "UTF-16"
#define CHARSET_UTF32       "UTF-32"
class xStringConverter
{
    void*       m_handle;
public:
    xStringConverter();
    ~xStringConverter();
    bool           setLanguge(const char* _to , const char* _from);

    bool           setUTF82Locale();
    bool           setLocale2UTF8();

    bool           setUTF82Unicode();
    bool           setUnicode2UTF8();

    bool           setUnicode2Locale();
    bool           setLocale2Unicode();

    bool           setUCS2ToUCS4();
    bool           setUCS4ToUCS2();


    bool           convert(const char* _in, char* _out,int inLen,int outlen);
    bool           convert(const char* _in, char* _out,int outlen);
    bool           convert(const wchar_t* _in, char* _out,int outlen);
};
#endif


template<int _WCharWidth> class TWCharTrait
{
public:
    typedef wchar_t        CharType ;
#if defined(__i386__) || defined(__x86_64__)
    typedef char32_t        UTF32;
#else
    typedef wchar_t        UTF32;
#endif
    typedef unsigned short UTF16;
    typedef unsigned char  UTF8;
    enum { WCharWidth = _WCharWidth , }; 

public:
    wchar_t  GetCodePoint(const wchar_t* _text , size_t& index , wchar_t* _out);
    wchar_t  GetCodePoint(const wchar_t* _text , size_t& index );
    size_t   strlen(const wchar_t* _text);
};




template<> class TWCharTrait<2>
{
public:
    typedef unsigned int   CharType ;
    typedef wchar_t        UTF16;
    typedef int32_t        UTF32;
    typedef unsigned char  UTF8;

    enum { WCharWidth = 2 , } ; 
public:
    UTF32  GetCodePoint(const wchar_t* _text , size_t& index , wchar_t* _out);
    UTF32  GetCodePoint(const wchar_t* _text , size_t& index );
    size_t strlen(const wchar_t* _text);
};




typedef TWCharTrait<sizeof(wchar_t)>           xWCharTrait;
typedef xWCharTrait::CharType                  xWCharType; 

typedef xWCharTrait::UTF32  UTF32;	/* at least 32 bits */
typedef xWCharTrait::UTF16  UTF16;	/* at least 16 bits */
typedef xWCharTrait::UTF8   UTF8;	/* typically 8 bits */

/* Some fundamental constants */
#define UNI_REPLACEMENT_CHAR (UTF32)0x0000FFFD
#define UNI_MAX_BMP (UTF32)0x0000FFFF
#define UNI_MAX_UTF16 (UTF32)0x0010FFFF
#define UNI_MAX_UTF32 (UTF32)0x7FFFFFFF
#define UNI_MAX_LEGAL_UTF32 (UTF32)0x0010FFFF

typedef enum 
{
    conversionOK, 		/* conversion successful */
    sourceExhausted,	/* partial character in source, but hit end */
    targetExhausted,	/* insuff. room in target for conversion */
    sourceIllegal,		/* source sequence is illegal/malformed */
    conversionFailed,
} ConversionResult;

typedef enum 
{
    strictConversion = 0,
    lenientConversion
} ConversionFlags;


ConversionResult XEvol_Utf8ToUtf16 (const UTF8*  sourceStart,  UTF16* targetStart , size_t outlen , ConversionFlags flags = lenientConversion);
ConversionResult XEvol_Utf16ToUtf8 (const UTF16* sourceStart,  UTF8* targetStart  , size_t outlen , ConversionFlags flags = lenientConversion);
ConversionResult XEvol_Utf8ToUtf32 (const UTF8*  sourceStart,  UTF32* targetStart , size_t outlen , ConversionFlags flags = lenientConversion);
ConversionResult XEvol_Utf32ToUtf8 (const UTF32* sourceStart,  UTF8* targetStart  , size_t outlen , ConversionFlags flags = lenientConversion);
ConversionResult XEvol_Utf16ToUtf32(const UTF16* sourceStart,  UTF32* targetStart , size_t outlen , ConversionFlags flags = lenientConversion);
ConversionResult XEvol_Utf32ToUtf16(const UTF32* sourceStart,  UTF16* targetStart , size_t outlen , ConversionFlags flags = lenientConversion);
ConversionResult XEvol_UnicodeToFsEnc(const wchar_t* _in, char* _out,size_t outlen);
ConversionResult XEvol_FsEncToUnicode(const char* _in, wchar_t* _out,size_t outlen);
ConversionResult XEvol_Utf8ToUnicode(const char* _in, wchar_t* _out,size_t outlen);
ConversionResult XEvol_UnicodeToUtf8(const wchar_t* _in, char* _out,size_t outlen);
ConversionResult XEvol_LocaleToUtf8(const char* _in, char* _out,size_t outlen);
ConversionResult XEvol_Utf8ToLocale(const char* _in, char* _out,size_t outlen);
ConversionResult XEvol_UnicodeToLocale(const wchar_t* _in, char* _out,size_t outlen);
ConversionResult XEvol_LocaleToUnicode(const char* _in, wchar_t* _out,size_t outlen);
UTF32            XEvol_GetUtf32CodePoint(const UTF16 * text , size_t &c , UTF16 * strOut);
bool             XEvol_IsUtf8(const UTF8 * source, const UTF8 *sourceEnd);
size_t           XEvol_UTF16Len(const UTF16 * text);
const wchar_t*   XEvol_LocaleToUnicode(const char* _in);
const char*      XEvol_UnicodeToLocale(const wchar_t* _in);

const wchar_t*   XEvol_LocaleToUnicode(const char* _in);
const char*      XEvol_UnicodeToLocale(const wchar_t* _in);

#define XA2W(x) XEvol_LocaleToUnicode(x)
#define XW2A(x) XEvol_UnicodeToLocale(x)

const char*    XW2A_S(const wchar_t* _in, char* _out,size_t outlen);
const wchar_t* XA2W_S(const char* _in, wchar_t* _out,size_t outlen);


template <int _WCharWidth> wchar_t  TWCharTrait<_WCharWidth>::GetCodePoint(const wchar_t* _text , size_t& index , wchar_t* _out)
{
    wchar_t _ret = _text[index];
    _out[0] = _ret;
    _out[1] = 0;
    index ++;
    return  _ret;
}

template <int _WCharWidth> wchar_t  TWCharTrait<_WCharWidth>::GetCodePoint(const wchar_t* _text , size_t& index )
{
    wchar_t _ret = _text[index];
    index ++;
    return  _ret;
}

template <int _WCharWidth> size_t TWCharTrait<_WCharWidth>::strlen(const wchar_t* _text)
{
    return wcslen_x(_text);
}


inline TWCharTrait<2>::UTF32  TWCharTrait<2>::GetCodePoint(const wchar_t* _text , size_t& index , wchar_t* _out)
{
    return XEvol_GetUtf32CodePoint((const xWCharTrait::UTF16 *)_text , index , (xWCharTrait::UTF16*)_out );
}

inline TWCharTrait<2>::UTF32  TWCharTrait<2>::GetCodePoint(const wchar_t* _text , size_t& index )
{
    return XEvol_GetUtf32CodePoint((const xWCharTrait::UTF16*)_text , index , NULL );
}

inline size_t TWCharTrait<2>::strlen(const wchar_t* _text)
{
    return wcslen_x(_text);
}


#undef swscanf_x
#define  swscanf_x(str , strFmt , ...) \
do \
{\
    char  _buf[1024] = {0};\
    std::string ansiStr = XW2A_S(str   , _buf , 1024);\
    std::string ansiFmt = XW2A_S(strFmt, _buf , 1024);\
    sscanf(ansiStr.c_str() , ansiFmt.c_str() , __VA_ARGS__ );\
} while (0);\

#define  scan_value_x(str , strFmt , ...) \
do \
{\
    char  _buf[128] = {0};\
    std::string ansiStr = XW2A_S(str   , _buf , 128);\
    std::string ansiFmt = XW2A_S(strFmt, _buf , 128);\
    sscanf(ansiStr.c_str() , ansiFmt.c_str() , __VA_ARGS__ );\
} while (0);\


#endif

