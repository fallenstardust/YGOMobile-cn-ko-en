#ifndef UTILS_H
#define UTILS_H

#include <irrlicht.h>
#include <string>
#include <vector>
#include <functional>
#include <fstream>
#include <map>
#ifndef _WIN32
#include <dirent.h>
#include <sys/stat.h>
#endif

using path_string = std::basic_string<irr::fschar_t>;

namespace ygo {
	class Utils {
	public:
		class IrrArchiveHelper {
		public:
			irr::io::IFileArchive* archive;
			std::map<path_string/*folder name*/, std::pair<std::pair<int/*begin folder offset*/, int/*end folder offset*/>, std::pair<int/*begin file offset*/, int/*end file offset*/>>> folderindexes;
			IrrArchiveHelper(irr::io::IFileArchive* archive) { ParseList(archive); };
			void ParseList(irr::io::IFileArchive* archive);
		};
		static bool Makedirectory(const path_string& path);
		static bool Movefile(const path_string& source, const path_string& destination);
		static path_string ParseFilename(const std::wstring& input);
		static path_string ParseFilename(const std::string& input);
		static std::string ToUTF8IfNeeded(const path_string& input);
		static std::wstring ToUnicodeIfNeeded(const path_string& input);
		static bool Deletefile(const path_string& source);
		static bool ClearDirectory(const path_string& path);
		static bool Deletedirectory(const path_string& source);
		static void CreateResourceFolders();
		static void takeScreenshot(irr::IrrlichtDevice* device);
		static void ToggleFullscreen();
		static void changeCursor(irr::gui::ECURSOR_ICON icon);
		static void FindfolderFiles(const path_string& path, const std::function<void(path_string, bool, void*)>& cb, void* payload = nullptr);
		static std::vector<path_string> FindfolderFiles(const path_string& path, std::vector<path_string> extensions, int subdirectorylayers = 0);
		static void FindfolderFiles(IrrArchiveHelper& archive, const path_string& path, const std::function<bool(int, path_string, bool, void*)>& cb, void* payload = nullptr);
		static std::vector<int> FindfolderFiles(IrrArchiveHelper& archive, const path_string& path, std::vector<path_string> extensions, int subdirectorylayers = 0);
		static irr::io::IReadFile* FindandOpenFileFromArchives(const path_string& path, const path_string& name);
		static std::wstring NormalizePath(std::wstring path, bool trailing_slash = true);
		static std::wstring GetFileExtension(std::wstring file);
		static std::wstring GetFilePath(std::wstring file);
		static std::wstring GetFileName(std::wstring file);
		static std::string NormalizePath(std::string path, bool trailing_slash = true);
		static std::string GetFileExtension(std::string file);
		static std::string GetFilePath(std::string file);
		static std::string GetFileName(std::string file);

		template<typename T, typename... Ts>
		static std::unique_ptr<T> make_unique(Ts&&... params)
		{
			return std::unique_ptr<T>(new T(std::forward<Ts>(params)...));
		}

		// UTF-8 to UTF-16/UTF-32
		static std::string EncodeUTF8s(const std::wstring& source) {
			std::string res;
			res.reserve(source.size() * 3);
			for(size_t i = 0; i < source.size(); i++) {
				auto c = source[i];
				if (c < 0x80) {
					res += ((char)c);
				} else if (c < 0x800) {
					res += ((char)(((c >> 6) & 0x1f) | 0xc0));
					res += ((char)((c & 0x3f) | 0x80));
				} else if (c < 0x10000 && (c < 0xd800 || c > 0xdfff)) {
					res += ((char)(((c >> 12) & 0xf) | 0xe0));
					res += ((char)(((c >> 6) & 0x3f) | 0x80));
					res += ((char)(((c) & 0x3f) | 0x80));
				} else {
#ifdef _WIN32
					unsigned unicode = 0;
					unicode |= (c & 0x3ff) << 10;
					c = source[++i];
					unicode |= c & 0x3ff;
					unicode += 0x10000;
					res += ((char)(((unicode >> 18) & 0x7) | 0xf0));
					res += ((char)(((unicode >> 12) & 0x3f) | 0x80));
					res += ((char)(((unicode >> 6) & 0x3f) | 0x80));
					res += ((char)(((unicode) & 0x3f) | 0x80));
#else
					res += ((char)(((c >> 18) & 0x7) | 0xf0));
					res += ((char)(((c >> 12) & 0x3f) | 0x80));
					res += ((char)(((c >> 6) & 0x3f) | 0x80));
					res += ((char)(((c) & 0x3f) | 0x80));
#endif
				}
			}
			res.shrink_to_fit();
			return res;
		}
		// UTF-8 to UTF-16/UTF-32
		static std::wstring DecodeUTF8s(const std::string& source) {
			std::wstring res;
			res.reserve(source.size());
			for (size_t i = 0; i < source.size();) {
				auto c = source[i];
				if ((c & 0x80) == 0) {
					res += ((wchar_t)c);
					i++;
				} else if ((c & 0xe0) == 0xc0) {
					res += ((wchar_t)((((unsigned)c & 0x1f) << 6) | ((unsigned)source[i + 1] & 0x3f)));
					i += 2;
				} else if ((c & 0xf0) == 0xe0) {
					res += ((wchar_t)((((unsigned)c & 0xf) << 12) | (((unsigned)source[i + 1] & 0x3f) << 6) | ((unsigned)source[i + 2] & 0x3f)));
					i += 3;
				} else if ((c & 0xf8) == 0xf0) {
#ifdef _WIN32
					unsigned unicode = (((unsigned)c & 0x7) << 18) | (((unsigned)source[i + 1] & 0x3f) << 12) | (((unsigned)source[i + 2] & 0x3f) << 6) | ((unsigned)source[i + 3] & 0x3f);
					unicode -= 0x10000;
					res += ((wchar_t)((unicode >> 10) | 0xd800));
					res += ((wchar_t)((unicode & 0x3ff) | 0xdc00));
#else
					res += ((wchar_t)((((unsigned)c & 0x7) << 18) | (((unsigned)source[i + 1] & 0x3f) << 12) | (((unsigned)source[i + 2] & 0x3f) << 6) | ((unsigned)source[i + 3] & 0x3f)));
#endif // _WIN32
					i += 4;
				} else {
					i++;
				}
			}
			res.shrink_to_fit();
			return res;
		}
	};
}

#endif //UTILS_H
