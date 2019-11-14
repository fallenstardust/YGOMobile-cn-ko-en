LOCAL_PATH	:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES	:= $(LOCAL_PATH)/include		\
					$(LOCAL_PATH)/src				\
					$(LOCAL_PATH)/libFLAC/include	\
					$(LOCAL_PATH)/libvorbis			\

LOCAL_MODULE		:= sndfile
LOCAL_CFLAGS		:= -DHAVE_CONFIG_H -w -DHOST_TRIPLET=\"$(LLVM_TRIPLE)\"
LOCAL_CFLAGS		+= -DFLAC__HAS_OGG -DFLAC__INTEGER_ONLY_LIBRARY

#LOCAL_ALLOW_UNDEFINED_SYMBOLS := true

FLAC_SRC := \
	libFLAC/bitmath.c \
	libFLAC/bitreader.c \
	libFLAC/bitwriter.c \
	libFLAC/cpu.c \
	libFLAC/crc.c \
	libFLAC/fixed.c \
	libFLAC/fixed_intrin_sse2.c \
	libFLAC/fixed_intrin_ssse3.c \
	libFLAC/float.c \
	libFLAC/format.c \
	libFLAC/lpc.c \
	libFLAC/lpc_intrin_sse.c \
	libFLAC/lpc_intrin_sse2.c \
	libFLAC/lpc_intrin_sse41.c \
	libFLAC/lpc_intrin_avx2.c \
	libFLAC/md5.c \
	libFLAC/memory.c \
	libFLAC/metadata_iterators.c \
	libFLAC/metadata_object.c \
	libFLAC/ogg_decoder_aspect.c \
	libFLAC/ogg_encoder_aspect.c \
	libFLAC/ogg_helper.c \
	libFLAC/ogg_mapping.c \
	libFLAC/stream_decoder.c \
	libFLAC/stream_encoder.c \
	libFLAC/stream_encoder_intrin_sse2.c \
	libFLAC/stream_encoder_intrin_ssse3.c \
	libFLAC/stream_encoder_intrin_avx2.c \
	libFLAC/stream_encoder_framing.c \
	libFLAC/window.c \

OGG_SRC := \
	libogg/bitwise.c \
	libogg/framing.c \

VORBIS_SRC := \
	libvorbis/analysis.c \
	libvorbis/bitrate.c \
	libvorbis/block.c \
	libvorbis/codebook.c \
	libvorbis/envelope.c \
	libvorbis/floor0.c \
	libvorbis/floor1.c \
	libvorbis/info.c \
	libvorbis/lookup.c \
	libvorbis/lpc.c \
	libvorbis/lsp.c \
	libvorbis/mapping0.c \
	libvorbis/mdct.c \
	libvorbis/psy.c \
	libvorbis/registry.c \
	libvorbis/res0.c \
	libvorbis/sharedbook.c \
	libvorbis/smallft.c \
	libvorbis/synthesis.c \
	libvorbis/vorbisenc.c \
	libvorbis/vorbisfile.c \
	libvorbis/window.c \

SNDFILE_SRC		:= \
	src/sndfile.c \
	src/aiff.c \
	src/au.c \
	src/avr.c \
	src/caf.c \
	src/dwd.c \
	src/flac.c \
	src/g72x.c \
	src/htk.c \
	src/ircam.c \
	src/macos.c \
	src/mat4.c \
	src/mat5.c \
	src/nist.c \
	src/paf.c \
	src/pvf.c \
	src/raw.c \
	src/rx2.c \
	src/sd2.c \
	src/sds.c \
	src/svx.c \
	src/txw.c \
	src/voc.c \
	src/wve.c \
	src/w64.c \
	src/wavlike.c \
	src/wav.c \
	src/xi.c \
	src/mpc2k.c \
	src/rf64.c \
	src/ogg_vorbis.c \
	src/ogg_speex.c \
	src/ogg_pcm.c \
	src/ogg_opus.c \
	src/common.c \
	src/file_io.c \
	src/command.c \
	src/pcm.c \
	src/ulaw.c \
	src/alaw.c \
	src/float32.c \
	src/double64.c \
	src/ima_adpcm.c \
	src/ms_adpcm.c \
	src/gsm610.c \
	src/dwvw.c \
	src/vox_adpcm.c \
	src/interleave.c \
	src/strings.c \
	src/dither.c \
	src/cart.c \
	src/broadcast.c \
	src/audio_detect.c \
	src/ima_oki_adpcm.c \
	src/alac.c \
	src/chunk.c \
	src/ogg.c \
	src/chanmap.c \
	src/windows.c \
	src/id3.c \
	\
	src/GSM610/add.c \
	src/GSM610/code.c \
	src/GSM610/decode.c \
	src/GSM610/gsm_create.c \
	src/GSM610/gsm_decode.c \
	src/GSM610/gsm_destroy.c \
	src/GSM610/gsm_encode.c \
	src/GSM610/gsm_option.c \
	src/GSM610/long_term.c \
	src/GSM610/lpc.c \
	src/GSM610/preprocess.c \
	src/GSM610/rpe.c \
	src/GSM610/short_term.c \
	src/GSM610/table.c \
	\
	src/G72x/g721.c \
	src/G72x/g723_16.c \
	src/G72x/g723_24.c \
	src/G72x/g723_40.c \
	src/G72x/g72x.c \
	\
	src/ALAC/ALACBitUtilities.c \
	src/ALAC/ag_dec.c \
	src/ALAC/ag_enc.c \
	src/ALAC/dp_dec.c \
	src/ALAC/dp_enc.c \
	src/ALAC/matrix_dec.c \
	src/ALAC/matrix_enc.c \
	src/ALAC/alac_decoder.c \
	src/ALAC/alac_encoder.c \

LOCAL_SRC_FILES		:= \
	$(FLAC_SRC) \
	$(OGG_SRC) \
	$(VORBIS_SRC) \
	$(SNDFILE_SRC) \

include $(BUILD_STATIC_LIBRARY)
