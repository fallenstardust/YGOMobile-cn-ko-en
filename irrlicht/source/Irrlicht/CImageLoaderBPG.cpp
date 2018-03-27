// Copyright (C) 2002-2012 Nikolaus Gebhardt
// This file is part of the "Irrlicht Engine".
// For conditions of distribution and use, see copyright notice in irrlicht.h

#include "CImageLoaderBPG.h"

#ifdef _IRR_COMPILE_WITH_BPG_LOADER_

#include "IReadFile.h"
#include "CImage.h"
#include "os.h"
#include "irrString.h"
extern "C" {
#include "libbpg/libbpg.h"
}
namespace irr
{
	namespace video
	{

		//! constructor
		CImageLoaderBPG::CImageLoaderBPG()
		{
			#ifdef _DEBUG
			setDebugName("CImageLoaderBPG");
			#endif
		}

		//! destructor
		CImageLoaderBPG::~CImageLoaderBPG()
		{
		}


		//! returns true if the file maybe is able to be loaded by this class
		//! based on the file extension (e.g. ".tga")
		bool CImageLoaderBPG::isALoadableFileExtension(const io::path& filename) const
		{
			return core::hasFileExtension ( filename, "bpg");
		}
		
		bool CImageLoaderBPG::isALoadableFileFormat(io::IReadFile* file) const
		{
			if (!file || file->getSize() < 4)
				return false;
			u8 head[4];
			file->read(&head, 4);
			
			return head[0] == 0x42 && head[1] == 0x50 && head[2] == 0x47 && head[3] ==0xfb;
		}
		
		IImage* CImageLoaderBPG::loadImage(io::IReadFile* file) const
		{
			if (!file)
				return 0;
			BPGDecoderContext *img;
			BPGImageInfo img_info_s, *img_info = &img_info_s;
			int w, h, i, y,size, bufferIncrement, rowspan;
			//加载文件
			img = bpg_decoder_open();
			u8* input = new u8[file->getSize()];
			file->read(input, file->getSize());
			if (bpg_decoder_decode(img, input, file->getSize()) < 0) {
				return 0;
			}
			//解析信息
			bpg_decoder_get_info(img, img_info);
			w = img_info->width;
			h = img_info->height;
			size = w * y;
			//*rgb_line的长度 rgb=3,argb=4
			if (img_info->format == BPG_FORMAT_GRAY) 
				rowspan = 1 * w;
			else
				rowspan = 3 * w;
			u8* rgb_line = new u8[rowspan];
			//输出到output
			unsigned int outBufLen = rowspan * h;
			u8* output = new u8[outBufLen];
			bpg_decoder_start(img, BPG_OUTPUT_FORMAT_RGB24);
			bufferIncrement = 0;
			for (y = 0; y < h; y++) {
				bpg_decoder_get_line(img, rgb_line);
				for(i=0; i<rowspan;i++){
					output[bufferIncrement + i] = rgb_line[i];
				}
				bufferIncrement += rowspan;
			}
			bpg_decoder_close(img);
			delete [] rgb_line;
			delete [] input;
			return new CImage(ECF_R8G8B8, core::dimension2d<u32>(w, h), output);
		}

		//! creates a loader which is able to load jpeg images
		IImageLoader* createImageLoaderBPG()
		{
			return new CImageLoaderBPG();
		}

	} // end namespace video
} // end namespace irr

#endif

