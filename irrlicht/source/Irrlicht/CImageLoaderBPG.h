#pragma once
#include "IrrCompileConfig.h"

#ifdef _IRR_COMPILE_WITH_BPG_LOADER_

#include "IImageLoader.h"

#include "IReadFile.h"

namespace irr
{
	namespace video
	{
		class CImageLoaderBPG : public IImageLoader
		{
		public:
			CImageLoaderBPG();
			~CImageLoaderBPG();
		
			virtual bool isALoadableFileExtension(const io::path& filename) const override;

			virtual bool isALoadableFileFormat(io::IReadFile* file) const override;

			virtual IImage* loadImage(io::IReadFile* file) const override;

		};
	}
}

#endif