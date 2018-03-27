#include <wchar.h>
#include <ctype.h>
#include <memory.h>
#include <stdlib.h>
#include <stdio.h>
#include "xstring.h"
#include <string.h>
#include <assert.h>
#include <string>
#ifdef _WIN32
#include <windows.h>
#endif
#ifdef _USE_ICONV_
#include <iconv.h>
#pragma comment(lib,"libiconv.lib")
#endif

enum fmt_arg_type
{
    fat_none,
    fat_float,
    fat_interger,
    fat_pointer,
    fat_char,
    fat_string,
};

int xfind_fmt_tag(std::string& _fmt , const wchar_t *_buf , fmt_arg_type& _type)
{
     const wchar_t* _base = _buf;
     _type = fat_none;
     //prefix
     if(*_buf == '-' || *_buf == '+' || *_buf == ' ' || *_buf == '0' || *_buf == '#'  )
     {
         _fmt += (char)(*_buf);
         _buf ++;
     }

     //width
     if(*_buf <='9' && *_buf >= '0' )
     {
         _fmt += (char)(*_buf);
         _buf ++;
     }

     //.
     if(*_buf =='.')
     {
         _fmt += (char)(*_buf);
         _buf ++;
     }

     //precision 
     if(*_buf <='9' && *_buf >= '0' )
     {
         _fmt += (char)(*_buf);
         _buf ++;
     }

     //[{h | l | I64 | L}]
     if(*_buf == 'I' || *_buf == 'l' || *_buf == 'h' || *_buf == 'L' )
     {
         _fmt += (char)(*_buf);
         if(*++_buf == 's') {
        	 _type = fat_string;
         }
     }
     else if(*_buf == 'c' || *_buf == 'C')
     {
         _type = fat_char;
     }     
     else if(*_buf == 'd' || *_buf == 'i' || *_buf == 'o' || *_buf == 'u' || *_buf == 'x' || *_buf == 'X' || *_buf == 'e' || *_buf == 'E')
     {
         _type = fat_interger;
     }
     else if( *_buf == 'f' || *_buf == 'g' || *_buf == 'G' || *_buf == 'n' || *_buf == 'p'  )
     {
         _type = fat_float;
     }
     else if( *_buf == 'n' || *_buf == 'p'  )
     {
         _type = fat_pointer;
     }

     if(_type != fat_none)
     {
         _fmt += (char)(*_buf);
         _buf ++;
         return _buf - _base;
     }

     return 0;
     
}


//#if defined(_ANDROID_) || defined(_ANDROID)
#define GUARD_COUNT(idx)  {(idx) ++ ;  if((idx) >= ( (int)count - 1) ) { _out[(idx)] = 0 ; return (idx); } }
int vsnwprintf_x(wchar_t* _out , size_t count , const wchar_t * wcsfmtStr , va_list ap)
{
    int   idx              = 0;
    wchar_t* _ret          = _out;
    const wchar_t *_fmtStr = wcsfmtStr;
    while(*_fmtStr!=0)
    {
        if(*_fmtStr!='%')
        {
            *_ret = *_fmtStr;
            _ret ++;
            GUARD_COUNT(idx);
             _fmtStr++;
        }
        else
        {
            switch(*(++_fmtStr))
            {
//            case 's':
//            case 'S':
//                {
//                    const wchar_t* wcsValue = va_arg(ap , const wchar_t*);
//                    while( *wcsValue)
//                    {
//                        *_ret = *wcsValue;
//                        wcsValue ++;
//                        _ret ++;
//                        GUARD_COUNT(idx);
//                    }
//                     _fmtStr++;
//                }
//                break;
            default:
                {
                    std::string fmtStr = "%";
                    fmt_arg_type _type;
                    int nSkip = xfind_fmt_tag(fmtStr , _fmtStr , _type);
                    if(nSkip > 0)
                    {
                        char ansiBuf[128] = {0};
                        switch(_type)
                        {
                        case fat_char:
                            {
                                char n = (char)va_arg(ap , int);
                                sprintf(ansiBuf , fmtStr.c_str() , n ); 
                            }
                            break;
                        case fat_interger:
                            {
                                int n = va_arg(ap , int);
                                sprintf(ansiBuf , fmtStr.c_str() , n ); 
                            }
                            break;
                        case fat_float:
                            {
                                float n = va_arg(ap , double);
                                sprintf(ansiBuf , fmtStr.c_str() , n ); 
                            }
                            break;
                        case fat_pointer:
                            {
                                void* n = va_arg(ap , void*);
                                sprintf(ansiBuf , fmtStr.c_str() , n ); 
                            }
                            break;
                        case fat_string:
                        	{
                        		const wchar_t* wcsValue = va_arg(ap , const wchar_t*);
                        		while (*wcsValue) {
                        			*_ret = *wcsValue;
                        			wcsValue++;
                        			_ret++;
                        			GUARD_COUNT(idx);
                        		}
                        	}
                        }
                        int i = 0;
                        while(ansiBuf[i ])
                        {
                            *_ret = ansiBuf[i];
                            _ret ++; i ++;
                            GUARD_COUNT(idx);
                        }

                        _fmtStr += nSkip;
                    }
                    else
                    {
                        *_ret = *_fmtStr;
                        _ret ++;
                        _fmtStr++;
                        GUARD_COUNT(idx);
                    }
                }
            }
        }
       
    }
    _out[idx] = 0;
    return idx;
}

int        format_value (wchar_t *buffer,size_t count, const wchar_t *fmt , ... )
{
    va_list args;
    va_start(args, fmt);
    char _ansiBuf[512] = {0};
    assert(count < 512);
    std::string _fmt   = ::XW2A_S(fmt , _ansiBuf , 512);

    int ret = 0;
    ret = vsnprintf_x(_ansiBuf, count, _fmt.c_str() , args);
    va_end(args);

    memset(buffer , 0 , sizeof(wchar_t) * count);
    ::XEvol_LocaleToUnicode(_ansiBuf , buffer , count );
    return ret;
}
//#endif


int swprintf_x(wchar_t* _out , size_t count , const wchar_t *buf , ...)
{
    va_list ap;
    va_start(ap,buf);
    int _ret = vsnwprintf_x(_out , count , buf , ap);
    va_end(ap);
    return _ret;
}

wchar_t * wcpncpy_x(wchar_t *dest, const wchar_t *src , size_t n)
{
    wint_t c;
    wchar_t *const s = dest;

    if (n >= 4)
    {
        size_t n4 = n >> 2;

        for (;;)
        {
            c = *src++;
            *dest++ = c;
            if (c == L'\0')
                break;
            c = *src++;
            *dest++ = c;
            if (c == L'\0')
                break;
            c = *src++;
            *dest++ = c;
            if (c == L'\0')
                break;
            c = *src++;
            *dest++ = c;
            if (c == L'\0')
                break;
            if (--n4 == 0)
                goto last_chars;
        }
        n -= dest - s;
        goto zero_fill;
    }

last_chars:
    n &= 3;
    if (n == 0)
        return dest;

    for (;;)
    {
        c = *src++;
        --n;
        *dest++ = c;
        if (c == L'\0')
            break;
        if (n == 0)
            return dest;
    }

zero_fill:
    while (n-- > 0)
        dest[n] = L'\0';

    return dest - 1;
}

wchar_t * wcpcpy_x (wchar_t *dest, const wchar_t *src)
{
    wchar_t *wcp = (wchar_t *) dest - 1;
    wint_t c;
    const int off = (int)(src - dest + 1);
    do
    {
        c = wcp[off];
        *++wcp = c;
    }
    while (c != L'\0');
    return wcp;
}


int wcscasecmp_x (const wchar_t *s1, const wchar_t *s2)// LOCALE_PARAM)
//LOCALE_PARAM_DECL
{
    wint_t c1, c2;

    if (s1 == s2)
        return 0;

    do
    {
        c1 = tolower(*s1++);
        c2 = tolower(*s2++);
        if (c1 == L'\0')
            break;
    }
    while (c1 == c2);
    return c1 - c2;
}

wchar_t * wcschrnul_x (const wchar_t *wcs, const wchar_t wc)
{
    while (*wcs != L'\0')
        if (*wcs == wc)
            break;
        else
            ++wcs;
    return (wchar_t *) wcs;
}

int wcscmp_x(const wchar_t *s1, const wchar_t *s2)
{
    wint_t c1, c2;

    do
    {
        c1 = (wint_t) *s1++;
        c2 = (wint_t) *s2++;
        if (c1 == L'\0')
            return c1 - c2;
    }
    while (c1 == c2);

    return c1 - c2;
}



wchar_t *  wcschr_x (const wchar_t *wcs, const wchar_t wc)
{
    do
    if (*wcs == wc)
        return (wchar_t *) wcs;
    while (*wcs++ != L'\0');

    return NULL;
}

size_t  wcslen_x(const wchar_t *s)
{
    size_t len = 0;
    while (s[len] != L'\0')
    {
        if (s[++len] == L'\0')
            return len;
        if (s[++len] == L'\0')
            return len;
        if (s[++len] == L'\0')
            return len;
        ++len;
    }
    return len;
}

wchar_t * wcscat_x (wchar_t *dest, const wchar_t *src)
{
    register wchar_t *s1 = dest;
    register const wchar_t *s2 = src;
    wchar_t c;

    /* Find the end of the string.  */
    do
    {
        c = *s1++;
    }
    while (c != L'\0');

    /* Make S1 point before the next character, so we can increment
    it while memory is read (wins on pipelined cpus).	*/
    s1 -= 2;

    do
    {
        c = *s2++;
        *++s1 = c;
    }
    while (c != L'\0');

    return dest;
}

wchar_t * wcsdup_x (const wchar_t *s)
{
    size_t len = (wcslen_x (s) + 1) * sizeof (wchar_t);
    void * newStr = malloc (len);

    if (newStr == NULL)
        return NULL;
    return (wchar_t *) memcpy (newStr, (void *) s, len);
}

int wcsncasecmp_x (const wchar_t *s1, const wchar_t *s2, size_t n )//LOCALE_PARAM)
//LOCALE_PARAM_DECL
{
    wint_t c1, c2;

    if (s1 == s2 || n == 0)
        return 0;

    do
    {
        c1 = (wint_t) tolower (*s1++);
        c2 = (wint_t) tolower (*s2++);
        if (c1 == L'\0' || c1 != c2)
            return c1 - c2;
    } while (--n > 0);

    return c1 - c2;
}

wchar_t * wcsncat_x (wchar_t *dest, const wchar_t *src, size_t n)
{
    wchar_t c;
    wchar_t * const s = dest;

    /* Find the end of DEST.  */
    do
    c = *dest++;
    while (c != L'\0');

    /* Make DEST point before next character, so we can increment
    it while memory is read (wins on pipelined cpus).	*/
    dest -= 2;

    if (n >= 4)
    {
        size_t n4 = n >> 2;
        do
        {
            c = *src++;
            *++dest = c;
            if (c == L'\0')
                return s;
            c = *src++;
            *++dest = c;
            if (c == L'\0')
                return s;
            c = *src++;
            *++dest = c;
            if (c == L'\0')
                return s;
            c = *src++;
            *++dest = c;
            if (c == L'\0')
                return s;
        } while (--n4 > 0);
        n &= 3;
    }

    while (n > 0)
    {
        c = *src++;
        *++dest = c;
        if (c == L'\0')
            return s;
        n--;
    }

    if (c != L'\0')
        *++dest = L'\0';

    return s;
}


wchar_t * wcsncpy_x (wchar_t *dest, const wchar_t *src, size_t n)
{
    wint_t c;
    wchar_t *const s = dest;

    --dest;

    if (n >= 4)
    {
        size_t n4 = n >> 2;

        for (;;)
        {
            c = *src++;
            *++dest = c;
            if (c == L'\0')
                break;
            c = *src++;
            *++dest = c;
            if (c == L'\0')
                break;
            c = *src++;
            *++dest = c;
            if (c == L'\0')
                break;
            c = *src++;
            *++dest = c;
            if (c == L'\0')
                break;
            if (--n4 == 0)
                goto last_chars;
        }
        n = n - (dest - s) - 1;
        if (n == 0)
            return s;
        goto zero_fill;
    }

last_chars:
    n &= 3;
    if (n == 0)
        return s;

    do
    {
        c = *src++;
        *++dest = c;
        if (--n == 0)
            return s;
    }
    while (c != L'\0');

zero_fill:
    do
    *++dest = L'\0';
    while (--n > 0);

    return s;
}

size_t wcsnlen_x (const wchar_t *s, size_t maxlen)
{
    size_t len = 0;

    while (maxlen > 0 && s[len] != L'\0')
    {
        ++len;
        if (--maxlen == 0 || s[len] == L'\0')
            return len;
        ++len;
        if (--maxlen == 0 || s[len] == L'\0')
            return len;
        ++len;
        if (--maxlen == 0 || s[len] == L'\0')
            return len;
        ++len;
        --maxlen;
    }

    return len;
}

wchar_t * wcsstr_x (const wchar_t *haystack, const wchar_t *needle)
{
    wchar_t b, c;

    if ((b = *needle) != L'\0')
    {
        haystack--;				/* possible ANSI violation */
        do
        if ((c = *++haystack) == L'\0')
            goto ret0;
        while (c != b);

        if (!(c = *++needle))
            goto foundneedle;
        ++needle;
        goto jin;

        for (;;)
        {
            wchar_t a;
            const wchar_t *rhaystack, *rneedle;

            do
            {
                if (!(a = *++haystack))
                    goto ret0;
                if (a == b)
                    break;
                if ((a = *++haystack) == L'\0')
                    goto ret0;
shloop:	      ;
            }
            while (a != b);

jin:	  if (!(a = *++haystack))
              goto ret0;

          if (a != c)
              goto shloop;

          if (*(rhaystack = haystack-- + 1) == (a = *(rneedle = needle)))
              do
              {
                  if (a == L'\0')
                      goto foundneedle;
                  if (*++rhaystack != (a = *++needle))
                      break;
                  if (a == L'\0')
                      goto foundneedle;
              }
              while (*++rhaystack == (a = *++needle));

              needle = rneedle;		  /* took the register-poor approach */

              if (a == L'\0')
                  break;
        }
    }
foundneedle:
    return (wchar_t*) haystack;
ret0:
    return NULL;
}

size_t wcscspn_x(const wchar_t *wcs, const wchar_t *reject)
{
    register size_t count = 0;

    while (*wcs != L'\0')
        if (wcschr_x (reject, *wcs++) == NULL)
            ++count;
        else
            return count;

    return count;
}
void       tolower_x    (wchar_t * buffer)
{
    for(size_t i = 0 ; i < wcslen_x(buffer) ; i ++ )
    {
        tolower(buffer[i]);
    }
}

void       toupper_x    (wchar_t * buffer)				
{
    for(size_t i = 0 ; i < wcslen_x(buffer) ; i ++ )
    {
        toupper(buffer[i]);
    }
}


#ifdef _WIN32
float      wtof_x       (const wchar_t * buffer)
{
    return (float)_wtof(buffer);
}

int        wtoi_x       (const wchar_t * buffer)
{
    return _wtoi(buffer);
}
#else
float      wtof_x       (const wchar_t * buffer)
{
    char _buf[128] = {0};
    ::XW2A_S(buffer , _buf , 128);
    return (float)atof(_buf);
}

int        wtoi_x       (const wchar_t * buffer)
{
    char _buf[128] = {0};
    ::XW2A_S(buffer , _buf , 128);
    return (float)atoi(_buf);
}
#endif


#ifdef _USE_ICONV_
const char* XEvol_GetLocaleLang()
{
    return getenv("LANG");
}

const char* XEvol_GetLocaleCharSet()
{
    return NULL;//locale_charset();
}

xStringConverter::xStringConverter()
{                
    m_handle = (iconv_t)-1;
}                

xStringConverter::~xStringConverter()
{
    if(m_handle != (iconv_t)-1)
    {
        iconv_close(m_handle);
    }
}

bool  xStringConverter::setUTF82Locale()
{
    return setLanguge(XEvol_GetLocaleCharSet(),"UTF-8");
}

bool  xStringConverter::setLocale2UTF8()
{
    return setLanguge("UTF-8",XEvol_GetLocaleCharSet());
}

bool xStringConverter::setUCS4ToUCS2()
{
    return setLanguge(CHARSET_UNICODE32 , CHARSET_UNICODE16);
}

bool xStringConverter::setUCS2ToUCS4()
{
    return setLanguge(CHARSET_UTF16 , CHARSET_UTF32);
}

bool xStringConverter::setUTF82Unicode()
{
    if(sizeof(wchar_t) == 2)  return setLanguge(CHARSET_UNICODE16 , "UTF-8");
    if(sizeof(wchar_t) == 4)  return setLanguge(CHARSET_UNICODE32 , "UTF-8");
    return false;
}

bool xStringConverter::setUnicode2UTF8()
{
    if(sizeof(wchar_t) == 2)  return setLanguge("UTF-8" , CHARSET_UNICODE16);
    if(sizeof(wchar_t) == 4)  return setLanguge("UTF-8" , CHARSET_UNICODE32);
    return false;
}

bool  xStringConverter::setUnicode2Locale()
{
    if(sizeof(wchar_t) == 2)  return setLanguge(XEvol_GetLocaleCharSet(),CHARSET_UNICODE16);
    if(sizeof(wchar_t) == 4)  return setLanguge(XEvol_GetLocaleCharSet(),CHARSET_UNICODE32);
    return false;
}

bool  xStringConverter::setLocale2Unicode()
{
    if(sizeof(wchar_t) == 2)  return setLanguge(CHARSET_UNICODE16,XEvol_GetLocaleCharSet());
    if(sizeof(wchar_t) == 4)  return setLanguge(CHARSET_UNICODE32,XEvol_GetLocaleCharSet());
    return false;
}

bool  xStringConverter::setLanguge(const char* _to , const char* _from)
{
    if(m_handle != (iconv_t)-1 )
    {
        iconv_close(m_handle);
        m_handle = (iconv_t)-1;
    }
    m_handle = iconv_open(_to,_from);
    return m_handle != (iconv_t)-1;
}

bool xStringConverter::convert(const char* _in, char* _out,int inLen,int outlen)
{
    if(m_handle == (iconv_t)-1 )
        return false;
    size_t _bufInLen = inLen;
    size_t _bufOutLen = outlen;
#ifdef _WIN32
    bool ret =  iconv(m_handle , (char**)&_in,&_bufInLen,&_out,&_bufOutLen) == 0;
#else
    bool ret =  iconv(m_handle , (char**)&_in,&_bufInLen,(char**)&_out,&_bufOutLen) == 0;
#endif
    return ret;
}

bool xStringConverter::convert(const char* _in, char* _out,int outlen)
{
    if(m_handle == (iconv_t)-1 )
        return false;
    size_t _bufInLen = strlen(_in);
    return convert(_in , _out , (int)_bufInLen , (int)outlen);
}

bool xStringConverter::convert(const wchar_t* _in, char* _out,int outlen)
{
    if(m_handle == (iconv_t)-1 )
        return false;
    size_t _bufInLen = wcslen_x(_in) * 2;
    return convert((const char*)_in , _out , (int)_bufInLen , (int)outlen);
}
#endif

//////////////////////////////////////////////////////////////////////////
static const int halfShift  = 10; /* used for shifting by 10 bits */

static const UTF32 halfBase = 0x0010000UL;
static const UTF32 halfMask = 0x3FFUL;

#define UNI_SUR_HIGH_START  (UTF32)0xD800
#define UNI_SUR_HIGH_END    (UTF32)0xDBFF
#define UNI_SUR_LOW_START   (UTF32)0xDC00
#define UNI_SUR_LOW_END     (UTF32)0xDFFF
#define false	   0
#define true	    1

/* --------------------------------------------------------------------- */

ConversionResult XEvol_Utf32ToUtf16 ( const UTF32* sourceStart,   UTF16* targetStart, size_t outLen ,  ConversionFlags flags) 
{
    ConversionResult result = conversionOK;
    const UTF32* source = sourceStart;
    UTF16* target       = targetStart;
    UTF16* targetEnd    = targetStart + outLen/2;
    while (*source)
    {
        UTF32 ch;
        if (target >= targetEnd)
        {
            result = targetExhausted; break;
        }
        ch = *source++;
        if (ch <= UNI_MAX_BMP)
        { /* Target is a character <= 0xFFFF */
            /* UTF-16 surrogate values are illegal in UTF-32; 0xffff or 0xfffe are both reserved values */
            if (ch >= UNI_SUR_HIGH_START && ch <= UNI_SUR_LOW_END) 
            {
                if (flags == strictConversion)
                {
                    --source; /* return to the illegal value itself */
                    result = sourceIllegal;
                    break;
                } 
                else 
                {
                    *target++ = UNI_REPLACEMENT_CHAR;
                }
            }
            else 
            {
                *target++ = (UTF16)ch; /* normal case */
            }
        }
        else if (ch > UNI_MAX_LEGAL_UTF32)
        {
            if (flags == strictConversion)
            {
                result = sourceIllegal;
            }
            else
            {
                *target++ = UNI_REPLACEMENT_CHAR;
            }
        } 
        else
        {
            /* target is a character in range 0xFFFF - 0x10FFFF. */
            if (target + 1 >= targetEnd) 
            {
                --source; /* Back up source pointer! */
                result = targetExhausted; break;
            }
            ch -= halfBase;
            *target++ = (UTF16)((ch >> halfShift) + UNI_SUR_HIGH_START);
            *target++ = (UTF16)((ch & halfMask) + UNI_SUR_LOW_START);
        }
    }
    return result;
}

/* --------------------------------------------------------------------- */
size_t XEvol_UTF16Len(const UTF16 * text)
{
    const UTF16* source = text;
    size_t ret = 0;
    UTF32 ch, ch2;
    while (*source )
    {
        ch = *source++;
        ret ++;
        /* If we have a surrogate pair, convert to UTF32 first. */
        if (ch >= UNI_SUR_HIGH_START && ch <= UNI_SUR_HIGH_END)
        {
            /* If the 16 bits following the high surrogate are in the source buffer... */
            if ( *source ) 
            {
                ch2 = *source;
                /* If it's a low surrogate, convert to UTF32. */
                if (ch2 >= UNI_SUR_LOW_START && ch2 <= UNI_SUR_LOW_END) 
                {
                    ch = ((ch - UNI_SUR_HIGH_START) << halfShift) + (ch2 - UNI_SUR_LOW_START) + halfBase;
                    ++source;
                } 
                else
                { 
                    /* it's an unpaired high surrogate */
                    return ret;
                }
            }
            else
            { 
                /* We don't have the 16 bits following the high surrogate. */
                return ret;
            }
        }
        else 
        {
            /* UTF-16 surrogate values are illegal in UTF-32 */
            if (ch >= UNI_SUR_LOW_START && ch <= UNI_SUR_LOW_END)
            {
                return ret;
            }
        }
    }
    return ret;
}

ConversionResult XEvol_Utf16ToUtf32 (const UTF16* sourceStart, UTF32* targetStart, size_t outLen, ConversionFlags flags) 
{
    ConversionResult result = conversionOK;
    const UTF16* source = sourceStart;
    UTF32* target       = targetStart;
    UTF32* targetEnd    = targetStart + outLen/4;
    UTF32 ch, ch2;
    while (*source )
    {
        const UTF16* oldSource = source; /*  In case we have to back up because of target overflow. */
        ch = *source++;
        /* If we have a surrogate pair, convert to UTF32 first. */
        if (ch >= UNI_SUR_HIGH_START && ch <= UNI_SUR_HIGH_END)
        {
            /* If the 16 bits following the high surrogate are in the source buffer... */
            if ( *source ) 
            {
                ch2 = *source;
                /* If it's a low surrogate, convert to UTF32. */
                if (ch2 >= UNI_SUR_LOW_START && ch2 <= UNI_SUR_LOW_END) 
                {
                    ch = ((ch - UNI_SUR_HIGH_START) << halfShift)
                        + (ch2 - UNI_SUR_LOW_START) + halfBase;
                    ++source;
                } 
                else if (flags == strictConversion) 
                { /* it's an unpaired high surrogate */
                    --source; /* return to the illegal value itself */
                    result = sourceIllegal;
                    break;
                }
            }
            else
            { /* We don't have the 16 bits following the high surrogate. */
                --source; /* return to the high surrogate */
                result = sourceExhausted;
                break;
            }
        }
        else if (flags == strictConversion)
        {
            /* UTF-16 surrogate values are illegal in UTF-32 */
            if (ch >= UNI_SUR_LOW_START && ch <= UNI_SUR_LOW_END)
            {
                --source; /* return to the illegal value itself */
                result = sourceIllegal;
                break;
            }
        }
        if (target >= targetEnd)
        {
            source = oldSource; /* Back up source pointer! */
            result = targetExhausted; break;
        }
        *target++ = ch;
    }
#ifdef CVTUTF_DEBUG
    if (result == sourceIllegal)
    {
        fprintf(stderr, "XEvol_Utf16ToUtf32 illegal seq 0x%04x,%04x\n", ch, ch2);
        fflush(stderr);
    }
#endif
    return result;
}

/* --------------------------------------------------------------------- */

/*
* Index into the table below with the first byte of a UTF-8 sequence to
* get the number of trailing bytes that are supposed to follow it.
* Note that *legal* UTF-8 values can't have 4 or 5-bytes. The table is
* left as-is for anyone who may want to do such conversion, which was
* allowed in earlier algorithms.
*/
static const char trailingBytesForUTF8[256] =
{
    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
    1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1, 1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
    2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2, 3,3,3,3,3,3,3,3,4,4,4,4,5,5,5,5
};

/*
* Magic values subtracted from a buffer value during UTF8 conversion.
* This table contains as many values as there might be trailing bytes
* in a UTF-8 sequence.
*/
static const UTF32 offsetsFromUTF8[6] = { 0x00000000UL, 0x00003080UL, 0x000E2080UL,   0x03C82080UL, 0xFA082080UL, 0x82082080UL };

/*
* Once the bits are split out into bytes of UTF-8, this is a mask OR-ed
* into the first byte, depending on how many bytes follow.  There are
* as many entries in this table as there are UTF-8 sequence types.
* (I.e., one byte sequence, two byte... etc.). Remember that sequencs
* for *legal* UTF-8 will be 4 or fewer bytes total.
*/
static const UTF8 firstByteMark[7] = { 0x00, 0x00, 0xC0, 0xE0, 0xF0, 0xF8, 0xFC };

/* --------------------------------------------------------------------- */

/* The interface converts a whole buffer to avoid function-call overhead.
* Constants have been gathered. Loops & conditionals have been removed as
* much as possible for efficiency, in favor of drop-through switches.
* (See "Note A" at the bottom of the file for equivalent code.)
* If your compiler supports it, the "isLegalUTF8" call can be turned
* into an inline function.
*/

/* --------------------------------------------------------------------- */

ConversionResult XEvol_Utf16ToUtf8 (const UTF16* sourceStart, UTF8* targetStart, size_t outLen ,  ConversionFlags flags)
{
    ConversionResult result = conversionOK;
    const UTF16* source = sourceStart;
    UTF8* target        = targetStart;
    UTF8* targetEnd     = targetStart + outLen;
    while ( *source )
    {
        UTF32 ch;
        unsigned short bytesToWrite = 0;
        const UTF32 byteMask = 0xBF;
        const UTF32 byteMark = 0x80; 
        const UTF16* oldSource = source; /* In case we have to back up because of target overflow. */
        ch = *source++;
        /* If we have a surrogate pair, convert to UTF32 first. */
        if (ch >= UNI_SUR_HIGH_START && ch <= UNI_SUR_HIGH_END) 
        {
            /* If the 16 bits following the high surrogate are in the source buffer... */
            if ( *source )
            {
                UTF32 ch2 = *source;
                /* If it's a low surrogate, convert to UTF32. */
                if (ch2 >= UNI_SUR_LOW_START && ch2 <= UNI_SUR_LOW_END) 
                {
                    ch = ((ch - UNI_SUR_HIGH_START) << halfShift)
                        + (ch2 - UNI_SUR_LOW_START) + halfBase;
                    ++source;
                } 
                else if (flags == strictConversion) 
                { /* it's an unpaired high surrogate */
                    --source; /* return to the illegal value itself */
                    result = sourceIllegal;
                    break;
                }
            } 
            else 
            { /* We don't have the 16 bits following the high surrogate. */
                --source; /* return to the high surrogate */
                result = sourceExhausted;
                break;
            }
        } 
        else if (flags == strictConversion) 
        {
            /* UTF-16 surrogate values are illegal in UTF-32 */
            if (ch >= UNI_SUR_LOW_START && ch <= UNI_SUR_LOW_END)
            {
                --source; /* return to the illegal value itself */
                result = sourceIllegal;
                break;
            }
        }
        /* Figure out how many bytes the result will require */
        if (ch < (UTF32)0x80)
        {	     bytesToWrite = 1;
        } 
        else if (ch < (UTF32)0x800) 
        {     
            bytesToWrite = 2;
        }
        else if (ch < (UTF32)0x10000) 
        {  
            bytesToWrite = 3;
        } 
        else if (ch < (UTF32)0x110000)
        { 
            bytesToWrite = 4;
        }
        else
        {	
            bytesToWrite = 3;
            ch = UNI_REPLACEMENT_CHAR;
        }

        target += bytesToWrite;
        if (target > targetEnd)
        {
            source = oldSource; /* Back up source pointer! */
            target -= bytesToWrite; result = targetExhausted; break;
        }
        switch (bytesToWrite) 
        { /* note: everything falls through. */
        case 4: *--target = (UTF8)((ch | byteMark) & byteMask); ch >>= 6;
        case 3: *--target = (UTF8)((ch | byteMark) & byteMask); ch >>= 6;
        case 2: *--target = (UTF8)((ch | byteMark) & byteMask); ch >>= 6;
        case 1: *--target =  (UTF8)(ch | firstByteMark[bytesToWrite]);
        }
        target += bytesToWrite;
    }
    return result;
}

/* --------------------------------------------------------------------- */

/*
* Utility routine to tell whether a sequence of bytes is legal UTF-8.
* This must be called with the length pre-determined by the first byte.
* If not calling this from ConvertUTF8to*, then the length can be set by:
*  length = trailingBytesForUTF8[*source]+1;
* and the sequence is illegal right away if there aren't that many bytes
* available.
* If presented with a length > 4, this returns false.  The Unicode
* definition of UTF-8 goes up to 4-byte sequences.
*/

static bool isLegalUTF8(const UTF8 *source, int length)
{
    UTF8 a;
    const UTF8 *srcptr = source+length;
    switch (length) 
    {
    default: return false;
        /* Everything else falls through when "true"... */
    case 4: if ((a = (*--srcptr)) < 0x80 || a > 0xBF) return false;
    case 3: if ((a = (*--srcptr)) < 0x80 || a > 0xBF) return false;
    case 2: if ((a = (*--srcptr)) > 0xBF) return false;

        switch (*source)
        {
            /* no fall-through in this inner switch */
        case 0xE0: if (a < 0xA0) return false; break;
        case 0xED: if (a > 0x9F) return false; break;
        case 0xF0: if (a < 0x90) return false; break;
        case 0xF4: if (a > 0x8F) return false; break;
        default:   if (a < 0x80) return false;
        }

    case 1: if (*source >= 0x80 && *source < 0xC2) return false;
    }
    if (*source > 0xF4) return false;
    return true;
}

/* --------------------------------------------------------------------- */

/*
* Exported function to return whether a UTF-8 sequence is legal or not.
* This is not used here; it's just exported.
*/
bool XEvol_IsUtf8(const UTF8 *source, const UTF8 *sourceEnd)
{
    int length = trailingBytesForUTF8[*source]+1;
    if (source+length > sourceEnd)
    {
        return false;
    }
    return isLegalUTF8(source, length);
}

/* --------------------------------------------------------------------- */

ConversionResult XEvol_Utf8ToUtf16 (const UTF8* sourceStart, UTF16* targetStart, size_t outLen , ConversionFlags flags)
{
    ConversionResult result = conversionOK;
    const UTF8* source = sourceStart;
    UTF16* target      = targetStart;
    UTF16* targetEnd   = targetStart + outLen/2;
    const UTF8*  sourceEnd   = strlen( (const char*)sourceStart) + sourceStart ;
    while (*source)
    {
        UTF32 ch = 0;
        unsigned short extraBytesToRead = trailingBytesForUTF8[*source];
        if (source + extraBytesToRead >= sourceEnd)
        {
            result = sourceExhausted; break;
        }
        /* Do this check whether lenient or strict */
        if (! isLegalUTF8(source, extraBytesToRead+1))
        {
            result = sourceIllegal;
            break;
        }
        /*
        * The cases all fall through. See "Note A" below.
        */
        switch (extraBytesToRead) 
        {
        case 5: ch += *source++; ch <<= 6; /* remember, illegal UTF-8 */
        case 4: ch += *source++; ch <<= 6; /* remember, illegal UTF-8 */
        case 3: ch += *source++; ch <<= 6;
        case 2: ch += *source++; ch <<= 6;
        case 1: ch += *source++; ch <<= 6;
        case 0: ch += *source++;
        }
        ch -= offsetsFromUTF8[extraBytesToRead];

        if (target >= targetEnd) 
        {
            source -= (extraBytesToRead+1); /* Back up source pointer! */
            result = targetExhausted; break;
        }
        if (ch <= UNI_MAX_BMP)
        { /* Target is a character <= 0xFFFF */
            /* UTF-16 surrogate values are illegal in UTF-32 */
            if (ch >= UNI_SUR_HIGH_START && ch <= UNI_SUR_LOW_END)
            {
                if (flags == strictConversion)
                {
                    source -= (extraBytesToRead+1); /* return to the illegal value itself */
                    result = sourceIllegal;
                    break;
                } 
                else 
                {
                    *target++ = UNI_REPLACEMENT_CHAR;
                }
            } 
            else
            {
                *target++ = (UTF16)ch; /* normal case */
            }
        }
        else if (ch > UNI_MAX_UTF16)
        {
            if (flags == strictConversion) 
            {
                result = sourceIllegal;
                source -= (extraBytesToRead+1); /* return to the start */
                break; /* Bail out; shouldn't continue */
            } 
            else 
            {
                *target++ = UNI_REPLACEMENT_CHAR;
            }
        } 
        else 
        {
            /* target is a character in range 0xFFFF - 0x10FFFF. */
            if (target + 1 >= targetEnd) 
            {
                source -= (extraBytesToRead+1); /* Back up source pointer! */
                result = targetExhausted; break;
            }
            ch -= halfBase;
            *target++ = (UTF16)((ch >> halfShift) + UNI_SUR_HIGH_START);
            *target++ = (UTF16)((ch & halfMask) + UNI_SUR_LOW_START);
        }
    }
    return result;
}

/* --------------------------------------------------------------------- */

ConversionResult XEvol_Utf32ToUtf8 (const UTF32* sourceStart, UTF8* targetStart,  size_t outLen, ConversionFlags flags)
{
    ConversionResult result = conversionOK;
    const UTF32* source = sourceStart;
    UTF8* target        = targetStart;
    UTF8* targetEnd     = targetStart + outLen;
    while ( *source ) 
    {
        UTF32 ch;
        unsigned short bytesToWrite = 0;
        const UTF32 byteMask = 0xBF;
        const UTF32 byteMark = 0x80; 
        ch = *source++;
        if (flags == strictConversion )
        {
            /* UTF-16 surrogate values are illegal in UTF-32 */
            if (ch >= UNI_SUR_HIGH_START && ch <= UNI_SUR_LOW_END)
            {
                --source; /* return to the illegal value itself */
                result = sourceIllegal;
                break;
            }
        }
        /*
        * Figure out how many bytes the result will require. Turn any
        * illegally large UTF32 things (> Plane 17) into replacement chars.
        */
        if (ch < (UTF32)0x80)
        {	  
            bytesToWrite = 1;
        }
        else if (ch < (UTF32)0x800) 
        {    
            bytesToWrite = 2;
        } 
        else if (ch < (UTF32)0x10000) 
        {   
            bytesToWrite = 3;
        } 
        else if (ch <= UNI_MAX_LEGAL_UTF32) 
        {
            bytesToWrite = 4;
        }
        else
        {			    bytesToWrite = 3;
        ch = UNI_REPLACEMENT_CHAR;
        result = sourceIllegal;
        }

        target += bytesToWrite;
        if (target > targetEnd)
        {
            --source; /* Back up source pointer! */
            target -= bytesToWrite; result = targetExhausted; break;
        }
        switch (bytesToWrite)
        { /* note: everything falls through. */
        case 4: *--target = (UTF8)((ch | byteMark) & byteMask); ch >>= 6;
        case 3: *--target = (UTF8)((ch | byteMark) & byteMask); ch >>= 6;
        case 2: *--target = (UTF8)((ch | byteMark) & byteMask); ch >>= 6;
        case 1: *--target = (UTF8) (ch | firstByteMark[bytesToWrite]);
        }
        target += bytesToWrite;
    }

    return result;
}

/* --------------------------------------------------------------------- */

ConversionResult XEvol_Utf8ToUtf32 (const UTF8* sourceStart, UTF32* targetStart, size_t outLen, ConversionFlags flags)
{
    ConversionResult result = conversionOK;
    const UTF8* source = sourceStart;
    UTF32* target      = targetStart;
    UTF32* targetEnd   = targetStart + outLen/4;
    const UTF8* sourceEnd = sourceStart + strlen((const char*)sourceStart);
    while (source < sourceEnd)
    {
        UTF32 ch = 0;
        unsigned short extraBytesToRead = trailingBytesForUTF8[*source];
        if (source + extraBytesToRead >= sourceEnd)
        {
            result = sourceExhausted; break;
        }
        /* Do this check whether lenient or strict */
        if (! isLegalUTF8(source, extraBytesToRead+1)) 
        {
            result = sourceIllegal;
            break;
        }
        /*
        * The cases all fall through. See "Note A" below.
        */
        switch (extraBytesToRead)
        {
        case 5: ch += *source++; ch <<= 6;
        case 4: ch += *source++; ch <<= 6;
        case 3: ch += *source++; ch <<= 6;
        case 2: ch += *source++; ch <<= 6;
        case 1: ch += *source++; ch <<= 6;
        case 0: ch += *source++;
        }
        ch -= offsetsFromUTF8[extraBytesToRead];

        if (target >= targetEnd) 
        {
            source -= (extraBytesToRead+1); /* Back up the source pointer! */
            result = targetExhausted; break;
        }
        if (ch <= UNI_MAX_LEGAL_UTF32) 
        {
            /*
            * UTF-16 surrogate values are illegal in UTF-32, and anything
            * over Plane 17 (> 0x10FFFF) is illegal.
            */
            if (ch >= UNI_SUR_HIGH_START && ch <= UNI_SUR_LOW_END) 
            {
                if (flags == strictConversion) 
                {
                    source -= (extraBytesToRead+1); /* return to the illegal value itself */
                    result = sourceIllegal;
                    break;
                } 
                else 
                {
                    *target++ = UNI_REPLACEMENT_CHAR;
                }
            } 
            else
            {
                *target++ = ch;
            }
        }
        else
        { /* i.e., ch > UNI_MAX_LEGAL_UTF32 */
            result = sourceIllegal;
            *target++ = UNI_REPLACEMENT_CHAR;
        }
    }
    return result;
}


UTF32            XEvol_GetUtf32CodePoint(const UTF16 * text , size_t &c , UTF16 * strOut)
{
    unsigned int char_code = text[c];
    unsigned int ch1 = text[c];
    unsigned int ch2 = text[c + 1];
    c ++;
    if(strOut)
    {
        strOut[0] = ch1;
        strOut[1] = 0;
        strOut[2] = 0;
    }
    if (ch1 >= UNI_SUR_HIGH_START && ch1 <= UNI_SUR_HIGH_END)
    {
        if (ch2 >= UNI_SUR_LOW_START && ch2 <= UNI_SUR_LOW_END)
        {
            if(strOut)
            { 
                strOut[1] = ch2;
            }
            char_code = ((ch1 - UNI_SUR_HIGH_START) << halfShift) +  (ch2 - UNI_SUR_LOW_START) + halfBase;
            c++;
        } 
    }
    return char_code;
}


ConversionResult XEvol_Utf8ToUnicode(const char* _in, wchar_t* _out,size_t outlen)
{
#ifdef _WIN32
    int ret = MultiByteToWideChar(CP_UTF8 , 0 , _in , (int)strlen(_in) , _out , (int)outlen);
    return ret == 0 ? conversionFailed : conversionOK;
#endif

    if(sizeof(wchar_t) == 2)
    {
        return XEvol_Utf8ToUtf16( (const UTF8*)_in , (UTF16*)_out , outlen ) ; 
    }
    else
    {
        return XEvol_Utf8ToUtf32( (const UTF8*)_in , (UTF32*)_out , outlen ) ; 
    }
}

ConversionResult XEvol_UnicodeToUtf8(const wchar_t* _in, char* _out,size_t outlen)
{
#ifdef _WIN32
    int ret = WideCharToMultiByte(CP_UTF8 , 0 , _in , (int)wcslen_x(_in) , _out , (int)outlen , NULL , NULL);
    return ret == 0 ? conversionFailed : conversionOK;
#endif
    if(sizeof(wchar_t) == 2)
    {
        return XEvol_Utf16ToUtf8( (const UTF16*)_in , (UTF8*)_out , outlen ) ; 
    }                                           
    else                                        
    {                                           
        return XEvol_Utf32ToUtf8( (const UTF32*)_in , (UTF8*)_out , outlen ) ; 
    }
}


ConversionResult XEvol_LocaleToUtf8(const char* _in, char* _out,size_t outlen)
{
    wchar_t* _unicode =  new wchar_t [ strlen(_in) + 1] ;
    XEvol_LocaleToUnicode(_in , _unicode , (int)strlen(_in) + 1 );
    ConversionResult ret = XEvol_UnicodeToUtf8(_unicode , _out , outlen);
    delete [] _unicode;
    return ret;
}


ConversionResult XEvol_Utf8ToLocale(const char* _in, char* _out,size_t outlen)
{
    wchar_t* _unicode =  new wchar_t [ strlen(_in) + 1] ;
    XEvol_Utf8ToUnicode(_in , _unicode , (int)strlen(_in) + 1 );
    ConversionResult ret = XEvol_UnicodeToLocale(_unicode , _out , outlen);
    delete [] _unicode;
    return ret;
}

ConversionResult XEvol_UnicodeToFsEnc(const wchar_t* _in, char* _out,size_t outlen)
{
#ifdef _WIN32
    return XEvol_UnicodeToLocale(_in , _out , outlen);
#else
    return XEvol_UnicodeToUtf8(_in , _out , outlen);
#endif
}

 ConversionResult XEvol_FsEncToUnicode(const char* _in, wchar_t* _out,size_t outlen)
{
#ifdef _WIN32
    return XEvol_LocaleToUnicode(_in , _out , outlen);
#else		
    return XEvol_Utf8ToUnicode(_in , _out , outlen);
#endif
}

ConversionResult XEvol_UnicodeToLocale(const wchar_t* _in, char* _out,size_t outlen)
{

#ifdef _WIN32
    int ret = WideCharToMultiByte(CP_ACP , 0 , _in , (int)wcslen(_in) , _out , (int)outlen , NULL , NULL);
#else

#if defined(_ANDROID_) || defined(_ANDROID)
    int ret = 0;
    return XEvol_UnicodeToUtf8(_in , _out , outlen);
#else
    int ret = wcstombs (_out, _in, wcslen(_in));
#endif

#endif
    return ret == 0 ? conversionFailed : conversionOK;

}

ConversionResult XEvol_LocaleToUnicode(const char* _in, wchar_t* _out,size_t outlen)
{
#ifdef _WIN32
    int ret = MultiByteToWideChar(CP_ACP , 0 , _in , (int)strlen(_in) , _out , (int)outlen);
#else

#if defined(_ANDROID_) || defined(_ANDROID)  
    int ret = 0;
    return XEvol_Utf8ToUnicode(_in , _out , outlen);
#else
    int ret = mbstowcs(_out, _in, strlen(_in));
#endif

#endif
    return ret == 0 ? conversionFailed : conversionOK;
}



const wchar_t*  XEvol_LocaleToUnicode(const char* _in)
{
      int len = strlen(_in );
      static wchar_t buf [2048];
      assert(len < 2048);
      buf[ len ] = 0;
      XEvol_LocaleToUnicode(_in , buf , len + 1);
      return buf;
}

const char*   XEvol_UnicodeToLocale(const wchar_t* _in)
{
    int len = wcslen_x(_in );
    assert(len < 2048);
    static char buf[2048];
    buf[ len ] = 0;
    XEvol_UnicodeToLocale(_in , buf , len + 1);
    return buf;
}

const char*    XW2A_S(const wchar_t* _in, char* _out,size_t outlen)
{
    XEvol_UnicodeToLocale(_in , _out , outlen );
    return _out;
}

const wchar_t* XA2W_S(const char* _in, wchar_t* _out,size_t outlen)
{
    XEvol_LocaleToUnicode(_in , _out , outlen );
    return _out;
}

/* --------------------- */
