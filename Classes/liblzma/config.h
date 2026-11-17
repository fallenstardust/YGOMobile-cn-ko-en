/*
 * liblzma 的 Android/NDK 静态配置。
 *
 * xz-utils 官方构建由 configure/CMake 生成 config.h；本项目用 ndk-build，
 * 因此在这里固定一份适用于 bionic/NDK 的最小配置：
 *   - 启用 LZMA1/LZMA2/Delta/BCJ 过滤器与全部匹配查找器（录像需要 LZMA1EXT）
 *   - 只编译单线程版本（见同目录 Android.mk），故有意不定义 HAVE_PTHREAD
 *   - NDK 支持的 ABI 均为小端，故不定义 WORDS_BIGENDIAN
 */
#ifndef LIBLZMA_ANDROID_CONFIG_H
#define LIBLZMA_ANDROID_CONFIG_H

/* 解码器 */
#define HAVE_DECODERS 1
#define HAVE_DECODER_ARM 1
#define HAVE_DECODER_ARM64 1
#define HAVE_DECODER_ARMTHUMB 1
#define HAVE_DECODER_DELTA 1
#define HAVE_DECODER_IA64 1
#define HAVE_DECODER_LZMA1 1
#define HAVE_DECODER_LZMA1EXT 1
#define HAVE_DECODER_LZMA2 1
#define HAVE_DECODER_POWERPC 1
#define HAVE_DECODER_SPARC 1
#define HAVE_DECODER_X86 1

/* 编码器 */
#define HAVE_ENCODERS 1
#define HAVE_ENCODER_ARM 1
#define HAVE_ENCODER_ARM64 1
#define HAVE_ENCODER_ARMTHUMB 1
#define HAVE_ENCODER_DELTA 1
#define HAVE_ENCODER_IA64 1
#define HAVE_ENCODER_LZMA1 1
#define HAVE_ENCODER_LZMA1EXT 1
#define HAVE_ENCODER_LZMA2 1
#define HAVE_ENCODER_POWERPC 1
#define HAVE_ENCODER_SPARC 1
#define HAVE_ENCODER_X86 1

/* 过滤器 props 处理 */
#define HAVE_FILTER_DELTA 1
#define HAVE_FILTER_LZMA1 1
#define HAVE_FILTER_LZMA1EXT 1
#define HAVE_FILTER_LZMA2 1

/* LZMA 匹配查找器（lz/lz_encoder.c 的注册表） */
#define HAVE_MF_BT2 1
#define HAVE_MF_BT3 1
#define HAVE_MF_BT4 1
#define HAVE_MF_HC3 1
#define HAVE_MF_HC4 1

/* 完整性校验（xz 容器使用；raw 录像流不依赖，但保持 API 完整） */
#define HAVE_CHECK_CRC32 1
#define HAVE_CHECK_CRC64 1
#define HAVE_CHECK_SHA256 1

/* 标准头（bionic 全部提供） */
#define HAVE_ALLOCA_H 1
#define HAVE_ERRNO_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_LIMITS_H 1
#define HAVE_STDBOOL_H 1
#define HAVE_STDINT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_SYS_TIME_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_UNISTD_H 1

/* clang 提供的字节序内建（tuklib_integer.h） */
#define HAVE_BUILTIN_BSWAP16 1
#define HAVE_BUILTIN_BSWAP32 1
#define HAVE_BUILTIN_BSWAP64 1

#endif
