/*
 * ygo_engine_jni.cpp
 *
 * 薄 JNI 决斗引擎封装：仅暴露 ocgcore（ocgapi.h）的干净 C 接口子集给 Java，
 * 不链接 irrlicht / freetype / openal / openssl / libevent 等渲染或网络库。
 * 自带：
 *   - 卡片数据读取（sqlite3 直读，逻辑对齐 data_manager.cpp::ReadDB；装载次序按优先级
 *     从低到高、后加载覆盖：Java 传入的主库等“主资源 cdb” → 卡包 zip/ypk 内 *.cdb
 *    （按包名字典序升序，靠后的包覆盖靠前的包）→ <root>/expansions 散 cdb（最高），
 *     净效果对齐 game.cpp 先 LoadDB(mDbList) 后 LoadExpansions()）
 *   - Lua 脚本读取（优先级对齐 data_manager.cpp::ScriptReaderEx 在
 *     prefer_expansion_script=1 时的“expansions 散文件 ＞ 卡包档案 ＞ 主脚本档案 ＞
 *     主散目录”语义）：卡片脚本先查 <root>/expansions/script/（手工调试散文件），再按
 *     包名字典序**降序**就地查各卡包 zip/ypk 里 script 目录下的 lua（靠后的包优先），再查
 *     <root>/scripts.zip（主脚本档案），最后回落 <root>/script/；非卡片脚本（single/ 等）
 *     保持主目录优先、expansions 兜底。所有档案都只扫中央目录建索引 + pread/inflate
 *     就地取，不需要任何落地解压。
 * 决斗消息循环由 Java 侧（ServerDuel）驱动，本层只做无状态转发。
 */
#include <jni.h>
#include <cstdio>
#include <cstring>
#include <cstdint>
#include <algorithm>
#include <cctype>
#include <dirent.h>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <zlib.h>
#include <android/log.h>

#include "ocgapi.h"
#include "card_data.h"
#include "common.h"
#include "sqlite3.h"

#define ENGINE_TAG "YgoEngineJNI"

namespace {

// 卡片数据表：code -> card_data（对齐 data_manager 的 _datas，仅保留引擎所需字段）
std::unordered_map<uint32_t, card_data> g_cardData;
// 资源根目录（含 script/、single/、expansions/），不带结尾斜杠
std::string g_rootPath;
// 脚本读取缓冲（脚本一般 < 数百 KB）
unsigned char g_scriptBuffer[0x100000]{};

constexpr int CARD_ARTWORK_VERSIONS_OFFSET = 20;
constexpr uint32_t RULE_CODE_SPECIAL_ID = 5405695u;
// ZIP 各结构体签名（小端），只用于 scripts.zip 直读索引
constexpr uint32_t EOCD_SIG = 0x06054b50u;         // 末尾目录结束记录
constexpr uint32_t EOCD64_SIG = 0x06064b50u;       // zip64 目录结束记录
constexpr uint32_t EOCD64_LOC_SIG = 0x07064b50u;   // zip64 目录结束定位记录
constexpr uint32_t CD_SIG = 0x02014b50u;           // 中央目录条目
constexpr uint32_t LF_SIG = 0x04034b50u;           // 本地文件头
constexpr uint16_t ZIP_STORED = 0;
constexpr uint16_t ZIP_DEFLATED = 8;

inline uint16_t rd16(const unsigned char* p) {
    return static_cast<uint16_t>(p[0] | (p[1] << 8));
}

inline uint32_t rd32(const unsigned char* p) {
    return static_cast<uint32_t>(p[0]) | (static_cast<uint32_t>(p[1]) << 8)
           | (static_cast<uint32_t>(p[2]) << 16) | (static_cast<uint32_t>(p[3]) << 24);
}

inline uint64_t rd64(const unsigned char* p) {
    return static_cast<uint64_t>(rd32(p)) | (static_cast<uint64_t>(rd32(p + 4)) << 32);
}

const char SELECT_STMT[] =
    "SELECT datas.id, datas.alias, datas.setcode, datas.type, datas.atk, datas.def,"
    " datas.level, datas.race, datas.attribute FROM datas";

inline bool is_alternative(uint32_t code, uint32_t alias) {
    return alias && (alias < code + CARD_ARTWORK_VERSIONS_OFFSET)
           && (code < alias + CARD_ARTWORK_VERSIONS_OFFSET);
}

// 把一个已打开的 sqlite 句子里的 datas 表读进 g_cardData（对齐 ReadDB 的主循环）；
// 后加载者覆盖先前同 code 项，调用方按优先级从低到高排列（主库在前、卡包与散 cdb
// 在后，扩展覆盖主库——对齐 game.cpp 先 LoadDB(mDbList) 后 LoadExpansions() 的净效果）
bool readDbRows(sqlite3* db) {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db, SELECT_STMT, -1, &stmt, nullptr) != SQLITE_OK) {
        sqlite3_finalize(stmt);
        return false;
    }
    while (sqlite3_step(stmt) == SQLITE_ROW) {
        uint32_t code = static_cast<uint32_t>(sqlite3_column_int64(stmt, 0));
        card_data cd;
        cd.code = code;
        cd.alias = static_cast<uint32_t>(sqlite3_column_int(stmt, 1));
        uint64_t setcode = static_cast<uint64_t>(sqlite3_column_int64(stmt, 2));
        write_setcode(cd.setcode, setcode);
        cd.type = static_cast<uint32_t>(sqlite3_column_int64(stmt, 3));
        cd.attack = sqlite3_column_int(stmt, 4);
        cd.defense = sqlite3_column_int(stmt, 5);
        if (cd.type & TYPE_LINK) {
            cd.link_marker = static_cast<uint32_t>(cd.defense);
            cd.defense = 0;
        }
        uint32_t level = static_cast<uint32_t>(sqlite3_column_int64(stmt, 6));
        cd.level = level & 0xff;
        cd.lscale = (level >> 24) & 0xff;
        cd.rscale = (level >> 16) & 0xff;
        cd.race = static_cast<uint32_t>(sqlite3_column_int64(stmt, 7));
        cd.attribute = static_cast<uint32_t>(sqlite3_column_int64(stmt, 8));
        // rule_code（对齐 ReadDB）
        if (cd.code == RULE_CODE_SPECIAL_ID) {
            cd.rule_code = cd.alias;
            cd.alias = 0;
        } else if (cd.alias && !(cd.type & TYPE_TOKEN) && !is_alternative(cd.code, cd.alias)) {
            cd.rule_code = cd.alias;
            cd.alias = 0;
        }
        g_cardData[code] = cd;
    }
    sqlite3_finalize(stmt);
    return true;
}

// 从一个实体 cdb 文件读入 g_cardData
bool loadCdb(const char* file) {
    sqlite3* db = nullptr;
    if (sqlite3_open_v2(file, &db, SQLITE_OPEN_READONLY, nullptr) != SQLITE_OK) {
        if (db) sqlite3_close(db);
        return false;
    }
    const bool ok = readDbRows(db);
    sqlite3_close(db);
    return ok;
}

// 第二轮：为仍带 alias 的卡继承原始卡的 rule_code（对齐 ReadDB 收尾循环）
void resolveRuleCodes() {
    for (auto& entry : g_cardData) {
        card_data& cd = entry.second;
        if (cd.rule_code || !cd.alias || (cd.type & TYPE_TOKEN))
            continue;
        auto it = g_cardData.find(cd.alias);
        if (it == g_cardData.end())
            continue;
        cd.rule_code = it->second.rule_code;
    }
}

// —— ocgcore 回调：卡片读取 ——
uint32_t engineCardReader(uint32_t code, card_data* data) {
    auto it = g_cardData.find(code);
    if (it == g_cardData.end()) {
        data->clear();
        return 0;
    }
    std::memcpy(data, &it->second, sizeof(card_data));
    return 1;
}

// ZIP 条目的定位信息（数据偏移、压缩/原始长度、方法）
struct ZipEntryRef {
    uint64_t dataOffset = 0;
    uint32_t compSize = 0;
    uint32_t rawSize = 0;
    uint16_t method = 0;
};

// 连续 pread 满 n 字节；截断或出错返回 false
bool preadAll(int fd, void* buf, size_t n, off_t off) {
    unsigned char* p = static_cast<unsigned char*>(buf);
    while (n > 0) {
        const ssize_t got = ::pread(fd, p, n, off);
        if (got <= 0)
            return false;
        p += got;
        off += got;
        n -= static_cast<size_t>(got);
    }
    return true;
}

// 条目名归一：反斜杠转正斜杠、去掉开头的 "./"（打包工具两种布局都见过）
std::string normalizeEntryName(std::string name) {
    for (char& c : name) {
        if (c == '\\')
            c = '/';
    }
    while (name.compare(0, 2, "./") == 0)
        name.erase(0, 2);
    return name;
}

// 大小写不敏感后缀比较（卡包与卡密扩展名在设备上大小写不一）
bool endsWithI(const std::string& s, const char* suffix) {
    const size_t n = std::strlen(suffix);
    if (s.size() < n)
        return false;
    for (size_t i = 0; i < n; ++i) {
        if (std::tolower(static_cast<unsigned char>(s[s.size() - n + i]))
                != std::tolower(static_cast<unsigned char>(suffix[i])))
            return false;
    }
    return true;
}

std::string lowerCopy(const std::string& s) {
    std::string out(s);
    for (char& c : out)
        c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return out;
}

// 档案内路径里是否存在名为 script 的目录段（兼容 script/、Script/、./script/、任意前缀 script/）
bool hasScriptDir(const std::string& path) {
    const std::string lower = lowerCopy(path);
    size_t p = 0;
    while ((p = lower.find("script/", p)) != std::string::npos) {
        if (p == 0 || lower[p - 1] == '/')
            return true;
        p += 7;
    }
    return false;
}

/**
 * ZIP 档案直读（主脚本档案 scripts.zip 与各卡包 zip/ypk 共用；ypk 只是换了扩展名的 zip）：
 * 扫一遍中央目录建索引，之后 *.lua 与 *.cdb 都用 pread 就地取（stored 直接拷贝，
 * deflate 走 zlib raw inflate），全程不落盘解压。索引只收决斗用得上的两类条目：
 * .lua（按“档案内全路径 / script 下裸名 / 顶层裸名”三种键）与 .cdb（按中央目录序列表）。
 * 档案是唯一事实源：开局、撒回重建、旧格式回放与 MSG_RETRY 重派都经同一个 reader  入口，
 * 脚本与卡数据不可能分叉。
 */
class ZipArchive {
public:
    ZipArchive() = default;
    ~ZipArchive() { reset(); }
    ZipArchive(const ZipArchive&) = delete;
    ZipArchive& operator=(const ZipArchive&) = delete;

    /**
     * 按 size+mtime 自查后保证索引就绪：档案换了（升级覆盖、首装时拷贝晚于引擎 init）就
     * 重建；档案还不存在则清空索引，让读取回落到实体目录。不缓存“失败”，代价只是一次
     * stat（微秒级）。
     */
    void ensureOpen(const std::string& path) {
        struct stat st{};
        if (path.empty() || ::stat(path.c_str(), &st) != 0 || !S_ISREG(st.st_mode)) {
            reset();
            return;
        }
        if (m_fd >= 0 && m_path == path && m_size == st.st_size && m_mtime == st.st_mtime)
            return;
        reset();
        const int fd = ::open(path.c_str(), O_RDONLY | O_CLOEXEC);
        if (fd < 0)
            return;
        if (buildIndex(fd, st.st_size)) {
            m_fd = fd;
            m_path = path;
            m_size = st.st_size;
            m_mtime = st.st_mtime;
            __android_log_print(ANDROID_LOG_INFO, ENGINE_TAG, "档案索引 lua %zu 条、cdb %zu 条：%s",
                                m_index.size(), m_cdbs.size(), path.c_str());
            return;
        }
        reset();
        ::close(fd);
    }

    bool isOpen() const { return m_fd >= 0; }

    const std::vector<std::pair<std::string, ZipEntryRef>>& cdbs() const { return m_cdbs; }

    /**
     * 查卡片脚本 cXXX.lua：先按档案内全路径键 "script/cXXX.lua"（对齐 irrlicht 以
     * ignorePaths=false 挂载时的逐字匹配），再按裸名键（顶层散 lua 与老式无目录段打包）。
     */
    const ZipEntryRef* findScript(const std::string& bareName) const {
        auto it = m_index.find("script/" + bareName);
        if (it != m_index.end())
            return &it->second;
        it = m_index.find(bareName);
        return it == m_index.end() ? nullptr : &it->second;
    }

    /** 就地解出条目到 out（容量 cap），返回字节数；失败返回 -1（调用方回落实体目录） */
    int read(const ZipEntryRef& ref, unsigned char* out, size_t cap) const {
        if (m_fd < 0 || ref.rawSize >= cap)
            return -1;
        if (ref.method == ZIP_STORED) {
            // stored：档案里就是原始字节，一次 pread 搞定，连 inflate 都没有
            return preadAll(m_fd, out, ref.compSize, static_cast<off_t>(ref.dataOffset))
                    ? static_cast<int>(ref.compSize) : -1;
        }
        if (ref.method != ZIP_DEFLATED)
            return -1;
        std::vector<unsigned char> comp(ref.compSize);
        if (!preadAll(m_fd, comp.data(), comp.size(), static_cast<off_t>(ref.dataOffset)))
            return -1;
        z_stream zs{};
        if (inflateInit2(&zs, -MAX_WBITS) != Z_OK)    // raw deflate（zip 条目无 zlib 头）
            return -1;
        zs.next_in = comp.data();
        zs.avail_in = static_cast<uInt>(comp.size());
        zs.next_out = out;
        zs.avail_out = static_cast<uInt>(cap);
        const int ret = inflate(&zs, Z_FINISH);
        const int got = static_cast<int>(zs.total_out);
        inflateEnd(&zs);
        return ret == Z_STREAM_END ? got : -1;
    }

private:
    void reset() {
        if (m_fd >= 0)
            ::close(m_fd);
        m_fd = -1;
        m_path.clear();
        m_size = 0;
        m_mtime = 0;
        m_index.clear();
        m_cdbs.clear();
    }

    /**
     * 解析 EOCD（含 zip64 入口）与中央目录，只把 .lua 进 m_index、.cdb 进 m_cdbs。
     * 条目名布局全兼容：裸文件名、script/ 前缀、./script/ 前缀；pics/ 与 pack/ 等其
     * 它条目不进决斗索引（卡图由 Java 侧查包，卡组列表另有入口）。
     */
    bool buildIndex(int fd, off_t size) {
        const size_t tailLen = size < 0x11000 ? static_cast<size_t>(size) : 0x11000u;
        std::vector<unsigned char> tail(tailLen);
        if (!preadAll(fd, tail.data(), tailLen, size - static_cast<off_t>(tailLen)))
            return false;
        const unsigned char* eocd = nullptr;
        for (size_t i = tail.size(); i >= 22; --i) {
            const unsigned char* p = tail.data() + (i - 22);
            if (rd32(p) == EOCD_SIG) {
                eocd = p;
                break;
            }
        }
        if (eocd == nullptr)
            return false;
        uint64_t entries = rd16(eocd + 10);
        uint64_t cdSize = rd32(eocd + 12);
        uint64_t cdOffset = rd32(eocd + 16);
        if (entries == 0xFFFF || cdSize == 0xFFFFFFFFu || cdOffset == 0xFFFFFFFFu) {
            // zip64：EOCD 前 20 字节的 locator 指到 zip64 EOCD 记录
            if (cdOffset < 20)
                return false;
            unsigned char loc[20];
            if (!preadAll(fd, loc, sizeof loc, static_cast<off_t>(cdOffset) - 20))
                return false;
            if (rd32(loc) != EOCD64_LOC_SIG)
                return false;
            const uint64_t z64Off = rd64(loc + 8);
            unsigned char z64[56];
            if (!preadAll(fd, z64, sizeof z64, static_cast<off_t>(z64Off)))
                return false;
            if (rd32(z64) != EOCD64_SIG)
                return false;
            entries = rd64(z64 + 32);
            cdSize = rd64(z64 + 40);
            cdOffset = rd64(z64 + 48);
        }
        if (entries == 0 || cdSize == 0
                || cdOffset + cdSize > static_cast<uint64_t>(size))
            return false;
        std::vector<unsigned char> cd(static_cast<size_t>(cdSize));
        if (!preadAll(fd, cd.data(), cd.size(), static_cast<off_t>(cdOffset)))
            return false;
        size_t pos = 0;
        for (uint64_t i = 0; i < entries; ++i) {
            //中央目录里碰到意外结构时“用已解出的部分”而不是整体作废：打包工具
            //写错条目数、或夹了不支持的扩展都不应该让整本档案失效
            if (pos + 46 > cd.size() || rd32(cd.data() + pos) != CD_SIG)
                break;
            const unsigned char* h = cd.data() + pos;
            const uint16_t method = rd16(h + 10);
            const uint32_t compSize = rd32(h + 20);
            const uint32_t rawSize = rd32(h + 24);
            const uint16_t nameLen = rd16(h + 28);
            const uint16_t extraLen = rd16(h + 30);
            const uint16_t commentLen = rd16(h + 32);
            const uint32_t localOff = rd32(h + 42);
            if (pos + 46u + nameLen > cd.size())
                break;
            std::string name(reinterpret_cast<const char*>(h + 46), nameLen);
            pos += 46u + nameLen + extraLen + commentLen;
            name = normalizeEntryName(name);
            if (name.empty() || name.back() == '/')
                continue;     // 目录条目
            const size_t slash = name.rfind('/');
            const std::string bare = slash == std::string::npos
                    ? name : name.substr(slash + 1);
            if (bare.empty() || bare == ".gitignore" || bare == ".gitattributes"
                    || bare == "desktop.ini" || bare == "Thumbs.db")
                continue;
            const bool luaEntry = endsWithI(bare, ".lua");
            const bool cdbEntry = endsWithI(bare, ".cdb");
            if (!luaEntry && !cdbEntry)
                continue;     // 卡图/卡组/conf 等不进决斗索引
            if (method != ZIP_STORED && method != ZIP_DEFLATED)
                continue;     // 不支持的方法：交给实体目录兜底
            unsigned char lh[30];
            if (!preadAll(fd, lh, sizeof lh, static_cast<off_t>(localOff))
                    || rd32(lh) != LF_SIG)
                continue;
            // 数据起点按本地头的 name/extra 长度算（流式打包时两者可与中央目录不同）
            const uint64_t dataOff = static_cast<uint64_t>(localOff) + 30
                    + rd16(lh + 26) + rd16(lh + 28);
            if (dataOff + compSize > static_cast<uint64_t>(size))
                continue;
            ZipEntryRef ref;
            ref.dataOffset = dataOff;
            ref.compSize = compSize;
            ref.rawSize = rawSize;
            ref.method = method;
            if (cdbEntry) {
                // cdb 按中央目录序列表保留（一个包可能多个 cdb 目录/文件），由调用方按序加载
                m_cdbs.emplace_back(name, ref);
                continue;
            }
            m_index[name] = ref;                        // 档案内全路径键
            const bool underScript = hasScriptDir(name);
            if (slash == std::string::npos || underScript)
                m_index.emplace(bare, ref);             // 裸名键：已有则不覆盖
            if (underScript)
                m_index["script/" + bare] = ref;
        }
        return !m_index.empty() || !m_cdbs.empty();
    }

    int m_fd = -1;
    std::string m_path;
    off_t m_size = 0;
    time_t m_mtime = 0;
    std::unordered_map<std::string, ZipEntryRef> m_index;
    std::vector<std::pair<std::string, ZipEntryRef>> m_cdbs;
};

ZipArchive g_scriptsZip;
std::string g_scriptsZipPath;
// g_scriptBuffer 与档案索引是共享资源：多个决斗/回放线程同时读脚本时整体串行
std::mutex g_scriptMutex;

/**
 * 卡包档案登记表（ygocore 根目录与 expansions/ 下的 zip/ypk，二者都是 ZIP 容器）：
 * 不再解压物化任何 lua/cdb，全部按中央目录索引就地读。列表按包名字典序**升序**保存：
 *  - 脚本查询反向遍历（靠后的包优先，对齐 irrlicht “倒序挂载、先挂载者优先”）；
 *  - cdb 顺序加载（后加载覆盖同名卡，净效果同样是靠后的包优先）。
 */
struct PackArchive {
    std::string path;
    std::unique_ptr<ZipArchive> zip;
};
std::vector<PackArchive> g_packs;
bool g_packsScanned = false;
time_t g_packsStamp = 0;
// 用户“读取扩展卡包”开关：false 时完全不扫卡包、不读包内 cdb 与 expansions 散 cdb
bool g_readExpansions = true;

time_t dirStamp(const std::string& dir) {
    struct stat st{};
    return ::stat(dir.c_str(), &st) == 0 ? st.st_mtime : 0;
}

// 收集目录下的卡包档案：只认 zip/ypk，并排除主资源档案 pics.zip / scripts.zip（另有专档入口）
void collectPacksIn(const std::string& dir, std::vector<std::string>& out) {
    DIR* d = ::opendir(dir.c_str());
    if (d == nullptr)
        return;
    struct dirent* de;
    while ((de = ::readdir(d)) != nullptr) {
        const std::string name = de->d_name;
        if (name[0] == '.' || name.size() < 5)
            continue;                       // 隐藏/临时档案
        if (!endsWithI(name, ".zip") && !endsWithI(name, ".ypk"))
            continue;
        const std::string lower = lowerCopy(name);
        if (lower == "pics.zip" || lower == "scripts.zip")
            continue;                       // 主资源档案走自己的通道，不能当卡包抢主线脚本
        const std::string full = dir + "/" + name;
        struct stat st{};
        if (::stat(full.c_str(), &st) == 0 && S_ISREG(st.st_mode))
            out.push_back(full);
    }
    ::closedir(d);
}

/**
 * 重扫卡包清单。force=false 用于脚本读取路径：只看两个目录的 mtime（一次 stat），
 * 档案被新增/删除才重扫；单个包被就地覆写则由 ZipArchive::ensureOpen 的 size+mtime
 * 自查兜住。force=true 用于 init/reloadCards：包集与包内容都可能刚变，重建列表。
 */
void ensurePacksScanned(bool force) {
    if (!g_readExpansions) {
        g_packs.clear();
        g_packsScanned = true;
        g_packsStamp = 0;
        return;
    }
    const time_t stamp = dirStamp(g_rootPath) + 7 * dirStamp(g_rootPath + "/expansions");
    if (!force && g_packsScanned && stamp == g_packsStamp)
        return;
    g_packsScanned = true;
    g_packsStamp = stamp;
    std::vector<std::string> paths;
    collectPacksIn(g_rootPath, paths);
    collectPacksIn(g_rootPath + "/expansions", paths);
    std::sort(paths.begin(), paths.end(), [](const std::string& a, const std::string& b) {
        const std::string na = lowerCopy(a.substr(a.rfind('/') + 1));
        const std::string nb = lowerCopy(b.substr(b.rfind('/') + 1));
        return na != nb ? na < nb : lowerCopy(a) < lowerCopy(b);
    });
    g_packs.clear();
    for (const auto& p : paths) {
        PackArchive pack;
        pack.path = p;
        g_packs.push_back(std::move(pack));
    }
}

// 懒开档案并按 size+mtime 保证索引就绪；不可用（被删/损坏）返回 nullptr
ZipArchive* packZip(PackArchive& pack) {
    if (pack.zip == nullptr)
        pack.zip = std::make_unique<ZipArchive>();
    pack.zip->ensureOpen(pack.path);
    return pack.zip->isOpen() ? pack.zip.get() : nullptr;
}

// 卡包档案里查卡片脚本：包名字典序降序遍历，靠后的包优先
const ZipEntryRef* findPackScript(const std::string& bareName, ZipArchive** owner) {
    for (size_t i = g_packs.size(); i-- > 0; ) {
        ZipArchive* zip = packZip(g_packs[i]);
        if (zip == nullptr)
            continue;
        const ZipEntryRef* ref = zip->findScript(bareName);
        if (ref != nullptr) {
            *owner = zip;
            return ref;
        }
    }
    return nullptr;
}

/**
 * 从档案条目就地读 cdb：整段解进 sqlite 堆缓冲后 sqlite3_deserialize 成只读内存库，
 * 逐步对齐 data_manager.cpp::LoadDB（含强制 rollback-journal 的头 18/19 字节改写），
 * 因此不需要把包里的 cdb 解压落地成实体文件。缓冲由 sqlite3_free 分配：成功时交
 * FREEONCLOSE 释放，失败时自行释放（deserialize 失败路径除外）。
 */
bool loadCdbFromEntry(const ZipArchive& zip, const ZipEntryRef& ref) {
    if (ref.rawSize == 0 || ref.rawSize > 0x20000000u)    // 上限 512MB：异常条目直接跳过
        return false;
    unsigned char* buf = static_cast<unsigned char*>(sqlite3_malloc64(ref.rawSize + 1));
    if (buf == nullptr)
        return false;
    const int n = zip.read(ref, buf, static_cast<size_t>(ref.rawSize) + 1);
    if (n <= 0) {
        sqlite3_free(buf);
        return false;
    }
    if (n >= 20 && buf[18] == 0x02) {   // WAL 强制改成 rollback-journal（对齐 LoadDB）
        buf[18] = 0x01;
        buf[19] = 0x01;
    }
    sqlite3* db = nullptr;
    if (sqlite3_open(":memory:", &db) != SQLITE_OK) {
        sqlite3_free(buf);
        if (db) sqlite3_close(db);
        return false;
    }
    if (sqlite3_deserialize(db, nullptr, buf, n, n,
            SQLITE_DESERIALIZE_FREEONCLOSE | SQLITE_DESERIALIZE_READONLY) != SQLITE_OK) {
        sqlite3_close(db);   // FREEONCLOSE 已接管该缓冲，不能再自行 free
        return false;
    }
    const bool ok = readDbRows(db);
    sqlite3_close(db);
    return ok;
}

// 卡包档案内 cdb：按包名字典序升序加载（靠后的包后加载、覆盖同名卡）
size_t loadPackCdbs() {
    ensurePacksScanned(true);
    size_t loaded = 0;
    for (auto& pack : g_packs) {
        ZipArchive* zip = packZip(pack);
        if (zip == nullptr)
            continue;
        for (const auto& cdb : zip->cdbs())
            if (loadCdbFromEntry(*zip, cdb.second))
                ++loaded;
    }
    return loaded;
}

// <root>/expansions/*.cdb 散文件：调试/覆写用的最高优先级档，按名升序后加载者覆盖
size_t loadLooseExpansionCdbs() {
    if (!g_readExpansions)
        return 0;
    const std::string dir = g_rootPath + "/expansions";
    std::vector<std::string> files;
    DIR* d = ::opendir(dir.c_str());
    if (d == nullptr)
        return 0;
    struct dirent* de;
    while ((de = ::readdir(d)) != nullptr) {
        const std::string name = de->d_name;
        if (name[0] == '.' || !endsWithI(name, ".cdb"))
            continue;
        const std::string full = dir + "/" + name;
        struct stat st{};
        if (::stat(full.c_str(), &st) == 0 && S_ISREG(st.st_mode))
            files.push_back(full);
    }
    ::closedir(d);
    std::sort(files.begin(), files.end(), [](const std::string& a, const std::string& b) {
        return lowerCopy(a) < lowerCopy(b);
    });
    size_t loaded = 0;
    for (const auto& f : files)
        if (loadCdb(f.c_str()))
            ++loaded;
    return loaded;
}

// 扩展卡数据装载（低→高：卡包档案 cdb → expansions 散 cdb），均在主库之后调用；
// g_packs 与脚本读取共用，故与 engineScriptReader 同一把锁串行
void loadExpansionCardData() {
    std::lock_guard<std::mutex> guard(g_scriptMutex);
    const size_t packs = g_packs.size();
    const size_t fromPacks = loadPackCdbs();
    const size_t loose = loadLooseExpansionCdbs();
    __android_log_print(ANDROID_LOG_INFO, ENGINE_TAG,
                        "扩展卡数据直读档案（readExpansions=%d）：卡包 %zu 个（包内 cdb %zu 本）、"
                        "expansions 散 cdb %zu 本",
                        g_readExpansions ? 1 : 0, packs, fromPacks, loose);
}

// 读实体文件到共享脚本缓冲；容量不足（异常大的脚本）视为失败
bool readFileInto(const char* path, int* slen) {
    FILE* fp = std::fopen(path, "rb");
    if (fp == nullptr)
        return false;
    const size_t len = std::fread(g_scriptBuffer, 1, sizeof g_scriptBuffer, fp);
    std::fclose(fp);
    if (len >= sizeof g_scriptBuffer)
        return false;
    *slen = static_cast<int>(len);
    return true;
}

// 把 "./script/c1.lua" / "./single/x.lua" 解析为可读来源。
// 优先级对齐 data_manager.cpp::ScriptReaderEx 在 prefer_expansion_script=1 时的
// “expansions 散文件 ＞ irrlicht 档案区（卡包倒序→pics.zip→scripts.zip）＞ 主散目录”：
// 卡片脚本：① <root>/expansions/script/（手工调试散文件）② 卡包 zip/ypk 档案内
// script/*.lua（包名字典序降序=靠后的包优先）③ <root>/scripts.zip（主脚本档案）
// ④ <root>/script/ 实体目录（老安装残留/手工放置）；非卡片脚本（single/ 等）仍旧是
// 主目录优先、expansions 兜底。所有档案均就地读，不再解压落地。
uint8_t* engineScriptReader(const char* scriptPath, int* slen) {
    if (!scriptPath || g_rootPath.empty())
        return nullptr;
    const char* rel = scriptPath;
    if (rel[0] == '.' && rel[1] == '/')
        rel += 2;
    const bool cardScript = std::strncmp(rel, "script/", 7) == 0;
    char full[1024];
    // g_scriptBuffer 与各档案索引都是共享资源，多个决斗/回放线程同时读时整体串行
    std::lock_guard<std::mutex> guard(g_scriptMutex);
    if (cardScript) {
        const std::string bare(rel + 7);
        // ① expansions 散调试文件（实体，最高）
        std::snprintf(full, sizeof full, "%s/expansions/%s", g_rootPath.c_str(), rel);
        if (readFileInto(full, slen))
            return g_scriptBuffer;
        ensurePacksScanned(false);
        // ② 卡包档案：包名字典序降序就地取 script/*.lua（靠后的包优先，无需解压物化）
        ZipArchive* owner = nullptr;
        const ZipEntryRef* ref = findPackScript(bare, &owner);
        if (ref != nullptr) {
            const int n = owner->read(*ref, g_scriptBuffer, sizeof g_scriptBuffer);
            if (n >= 0) {
                *slen = n;
                return g_scriptBuffer;
            }
        }
        // ③ 主脚本档案 scripts.zip
        g_scriptsZip.ensureOpen(g_scriptsZipPath);
        ref = g_scriptsZip.findScript(bare);
        if (ref != nullptr) {
            const int n = g_scriptsZip.read(*ref, g_scriptBuffer, sizeof g_scriptBuffer);
            if (n >= 0) {
                *slen = n;
                return g_scriptBuffer;
            }
        }
    }
    // ④ 卡片脚本回落实体目录；非卡片脚本保持主目录优先、expansions 兜底
    for (int pass = 0; pass < 2; ++pass) {
        const bool useExpansions = cardScript ? (pass == 1) : (pass == 0);
        if (useExpansions)
            std::snprintf(full, sizeof full, "%s/expansions/%s", g_rootPath.c_str(), rel);
        else
            std::snprintf(full, sizeof full, "%s/%s", g_rootPath.c_str(), rel);
        if (readFileInto(full, slen))
            return g_scriptBuffer;
    }
    return nullptr;
}

// 引擎消息信号回调：本层由 Java 主动 get_message 拉取，无需处理，返回 0 即可
uint32_t engineMessageHandler(intptr_t /*pduel*/, uint32_t /*type*/) {
    return 0;
}

} // namespace

extern "C" {

/*
 * class     cn.garymb.ygomobile.engine.OcgDuelEngine
 * 初始化引擎：装载卡片数据并挂上脚本/卡片读取回调。
 * rootPath 为资源根目录（其下含 single/、scripts.zip、可选 expansions/ 与卡包 zip/ypk）；
 * cdbPaths 只传“主资源档”（主 cards.cdb 等）且按优先级从低到高；扩展卡数据（卡包档案内
 * cdb 与 expansions 散 cdb）由本层自己扫档案就地加载，均在 cdbPaths 之后装载，因此
 * 卡包/散 cdb 优先级恒高于主库（对齐 game.cpp 先 LoadDB(mDbList) 后 LoadExpansions()）。
 * readExpansions=false 时一律不碰卡包档案与 expansions 扩展档。
 */
JNIEXPORT jboolean JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeInit(JNIEnv* env, jclass /*clazz*/,
                                                         jstring rootPath, jobjectArray cdbPaths,
                                                         jboolean readExpansions) {
    if (rootPath == nullptr)
        return JNI_FALSE;
    g_readExpansions = readExpansions != JNI_FALSE;
    const char* rp = env->GetStringUTFChars(rootPath, nullptr);
    g_rootPath = rp ? rp : "";
    if (rp) env->ReleaseStringUTFChars(rootPath, rp);
    // 主脚本档案路径与 Java 侧 Constants.CORE_SCRIPTS_ZIP 一致；索引由首次读脚本时
    // 懒建（ensureOpen 按 size+mtime 自查），所以拷贝晚于本次 init 也不会错过新档案
    g_scriptsZipPath = g_rootPath.empty() ? std::string() : g_rootPath + "/scripts.zip";

    g_cardData.clear();
    jsize n = cdbPaths ? env->GetArrayLength(cdbPaths) : 0;
    for (jsize i = 0; i < n; ++i) {
        auto pathStr = (jstring) env->GetObjectArrayElement(cdbPaths, i);
        const char* path = env->GetStringUTFChars(pathStr, nullptr);
        if (path) {
            loadCdb(path);
            env->ReleaseStringUTFChars(pathStr, path);
        }
        env->DeleteLocalRef(pathStr);
    }
    // 扩展卡数据：卡包 zip/ypk 档案内 cdb → expansions 散 cdb（均不解压落地）
    loadExpansionCardData();
    resolveRuleCodes();
    if (g_cardData.empty())
        return JNI_FALSE;

    set_script_reader(engineScriptReader);
    set_card_reader(engineCardReader);
    set_message_handler(engineMessageHandler);
    return JNI_TRUE;
}

/* 重新装载卡片数据（卡组编辑/卡包增删变更后调用），沿用已设置的 rootPath；
 * 本入口会强制重扫卡包档案，因此包的新增/删除/覆写无需重启应用即可生效 */
JNIEXPORT void JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeReloadCards(JNIEnv* env, jclass /*clazz*/,
                                                                 jobjectArray cdbPaths,
                                                                 jboolean readExpansions) {
    g_readExpansions = readExpansions != JNI_FALSE;
    g_cardData.clear();
    jsize n = cdbPaths ? env->GetArrayLength(cdbPaths) : 0;
    for (jsize i = 0; i < n; ++i) {
        auto pathStr = (jstring) env->GetObjectArrayElement(cdbPaths, i);
        const char* path = env->GetStringUTFChars(pathStr, nullptr);
        if (path) {
            loadCdb(path);
            env->ReleaseStringUTFChars(pathStr, path);
        }
        env->DeleteLocalRef(pathStr);
    }
    loadExpansionCardData();
    resolveRuleCodes();
}

JNIEXPORT jlong JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeCreateDuelV2(JNIEnv* env, jclass /*clazz*/,
                                                                  jintArray seedSequence) {
    uint32_t seq[SEED_COUNT]{};
    jsize len = seedSequence ? env->GetArrayLength(seedSequence) : 0;
    if (len > SEED_COUNT) len = SEED_COUNT;
    if (len > 0) {
        jint* body = env->GetIntArrayElements(seedSequence, nullptr);
        for (jsize i = 0; i < len; ++i)
            seq[i] = static_cast<uint32_t>(body[i]);
        env->ReleaseIntArrayElements(seedSequence, body, JNI_ABORT);
    }
    return static_cast<jlong>(create_duel_v2(seq));
}

JNIEXPORT jlong JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeCreateDuel(JNIEnv* /*env*/, jclass /*clazz*/,
                                                                jint seed) {
    return static_cast<jlong>(create_duel(static_cast<uint_fast32_t>(seed)));
}

JNIEXPORT void JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeSetPlayerInfo(JNIEnv* /*env*/, jclass /*clazz*/,
                                                                   jlong duel, jint playerid,
                                                                   jint lp, jint startCount,
                                                                   jint drawCount) {
    set_player_info(duel, playerid, lp, startCount, drawCount);
}

JNIEXPORT void JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeNewCard(JNIEnv* /*env*/, jclass /*clazz*/,
                                                            jlong duel, jint code, jint owner,
                                                            jint controller, jint location,
                                                            jint sequence, jint position) {
    new_card(duel, static_cast<uint32_t>(code), static_cast<uint8_t>(owner),
             static_cast<uint8_t>(controller), static_cast<uint8_t>(location),
             static_cast<uint8_t>(sequence), static_cast<uint8_t>(position));
}

JNIEXPORT void JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeNewTagCard(JNIEnv* /*env*/, jclass /*clazz*/,
                                                                jlong duel, jint code, jint owner,
                                                                jint location) {
    new_tag_card(duel, static_cast<uint32_t>(code), static_cast<uint8_t>(owner),
                 static_cast<uint8_t>(location));
}

JNIEXPORT void JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeStartDuel(JNIEnv* /*env*/, jclass /*clazz*/,
                                                              jlong duel, jint options) {
    start_duel(duel, static_cast<uint32_t>(options));
}

JNIEXPORT void JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeEndDuel(JNIEnv* /*env*/, jclass /*clazz*/,
                                                            jlong duel) {
    end_duel(duel);
}

JNIEXPORT jint JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeProcess(JNIEnv* /*env*/, jclass /*clazz*/,
                                                            jlong duel) {
    return static_cast<jint>(process(duel));
}

/* 取出一条引擎消息；无消息返回长度为 0 的数组 */
JNIEXPORT jbyteArray JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeGetMessage(JNIEnv* env, jclass /*clazz*/,
                                                               jlong duel) {
    static thread_local unsigned char buf[0x20000];
    int32_t len = get_message(duel, buf);
    if (len <= 0)
        len = 0;
    jbyteArray arr = env->NewByteArray(len);
    if (arr && len > 0)
        env->SetByteArrayRegion(arr, 0, len, reinterpret_cast<const jbyte*>(buf));
    return arr;
}

JNIEXPORT void JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeSetResponseI(JNIEnv* /*env*/, jclass /*clazz*/,
                                                                  jlong duel, jint value) {
    set_responsei(duel, value);
}

JNIEXPORT void JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeSetResponseB(JNIEnv* env, jclass /*clazz*/,
                                                                 jlong duel, jbyteArray resp) {
    if (resp == nullptr)
        return;
    jsize len = env->GetArrayLength(resp);
    std::vector<uint8_t> tmp(len > 0 ? len : 1, 0);
    env->GetByteArrayRegion(resp, 0, len, reinterpret_cast<jbyte*>(tmp.data()));
    set_responseb(duel, tmp.data());
}

JNIEXPORT jint JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeQueryFieldCount(JNIEnv* /*env*/, jclass /*clazz*/,
                                                                    jlong duel, jint player,
                                                                    jint location) {
    return query_field_count(duel, static_cast<uint8_t>(player), static_cast<uint8_t>(location));
}

JNIEXPORT jbyteArray JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeQueryFieldCard(JNIEnv* env, jclass /*clazz*/,
                                                                    jlong duel, jint player,
                                                                    jint location, jint flag,
                                                                    jint useCache) {
    static thread_local unsigned char buf[0x20000];
    int32_t len = query_field_card(duel, static_cast<uint8_t>(player), static_cast<uint8_t>(location),
                                   static_cast<uint32_t>(flag), buf, static_cast<int>(useCache));
    if (len < 0) len = 0;
    if (len > (int)sizeof buf) len = (int)sizeof buf;
    jbyteArray arr = env->NewByteArray(len);
    if (arr && len > 0)
        env->SetByteArrayRegion(arr, 0, len, reinterpret_cast<const jbyte*>(buf));
    return arr;
}

JNIEXPORT jbyteArray JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeQueryCard(JNIEnv* env, jclass /*clazz*/,
                                                              jlong duel, jint player, jint location,
                                                              jint sequence, jint flag, jint useCache) {
    static thread_local unsigned char buf[0x10000];
    int32_t len = query_card(duel, static_cast<uint8_t>(player), static_cast<uint8_t>(location),
                             static_cast<uint8_t>(sequence), static_cast<uint32_t>(flag), buf, static_cast<int>(useCache));
    if (len < 0) len = 0;
    if (len > (int)sizeof buf) len = (int)sizeof buf;
    jbyteArray arr = env->NewByteArray(len);
    if (arr && len > 0)
        env->SetByteArrayRegion(arr, 0, len, reinterpret_cast<const jbyte*>(buf));
    return arr;
}

JNIEXPORT jbyteArray JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeQueryFieldInfo(JNIEnv* env, jclass /*clazz*/,
                                                                    jlong duel) {
    static thread_local unsigned char buf[0x10000];
    int32_t len = query_field_info(duel, buf);
    if (len < 0) len = 0;
    if (len > (int)sizeof buf) len = (int)sizeof buf;
    jbyteArray arr = env->NewByteArray(len);
    if (arr && len > 0)
        env->SetByteArrayRegion(arr, 0, len, reinterpret_cast<const jbyte*>(buf));
    return arr;
}

/* 残局模式：预加载指定脚本（如 "./single/xxx.lua"），返回是否成功 */
JNIEXPORT jint JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativePreloadScript(JNIEnv* env, jclass /*clazz*/,
                                                                   jlong duel, jstring scriptName) {
    if (scriptName == nullptr)
        return 0;
    const char* name = env->GetStringUTFChars(scriptName, nullptr);
    int32_t res = name ? preload_script(duel, name) : 0;
    if (name) env->ReleaseStringUTFChars(scriptName, name);
    return res;
}

} // extern "C"
