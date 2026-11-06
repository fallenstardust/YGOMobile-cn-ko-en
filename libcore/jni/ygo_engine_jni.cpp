/*
 * ygo_engine_jni.cpp
 *
 * 薄 JNI 决斗引擎封装：仅暴露 ocgcore（ocgapi.h）的干净 C 接口子集给 Java，
 * 不链接 irrlicht / freetype / openal / openssl / libevent 等渲染或网络库。
 * 自带：
 *   - 卡片数据读取（sqlite3 直读 cards.cdb + 扩展 cdb，逻辑对齐 data_manager.cpp::ReadDB；
 *     cdbPaths 按优先级从低到高传入：主库在前、扩展卡包/散cdb在后，后加载覆盖）
 *   - Lua 脚本读取（优先级对齐 data_manager.cpp::ScriptReaderEx prefer_expansion_script=1：
 *     卡片脚本先查 <root>/expansions/script/（expansions 散文件与卡包物化区），
 *     再查 <root>/scripts.zip（主脚本档案，中央目录索引 + pread/inflate 就地取，不再落地
 *     解压出 1.35 万个 lua），最后才回落 <root>/script/（老安装残留或手工调试）；
 *     非卡片脚本（single/ 等）保持主目录优先）
 * 决斗消息循环由 Java 侧（ServerDuel）驱动，本层只做无状态转发。
 */
#include <jni.h>
#include <cstdio>
#include <cstring>
#include <cstdint>
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

// 把一个 cdb 文件读入 g_cardData，后加载者覆盖先前同 code 项
//（调用方按优先级从低到高排列：主库在前、扩展在后，扩展覆盖主库——对齐 C++
// game.cpp 先 LoadDB(mDbList) 后 LoadExpansions() 的净效果）
bool loadCdb(const char* file) {
    sqlite3* db = nullptr;
    if (sqlite3_open_v2(file, &db, SQLITE_OPEN_READONLY, nullptr) != SQLITE_OK) {
        if (db) sqlite3_close(db);
        return false;
    }
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db, SELECT_STMT, -1, &stmt, nullptr) != SQLITE_OK) {
        sqlite3_finalize(stmt);
        sqlite3_close(db);
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
    sqlite3_close(db);
    return true;
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

// scripts.zip 条目的定位信息（数据偏移、压缩/原始长度、方法）
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

/**
 * scripts.zip 直读：扫一遍中央目录建“文件名 -> 条目”索引，之后 cXXXX.lua 用 pread 就地取
 *（stored 直接拷贝，deflate 走 zlib raw inflate），取代“把 1.35 万个 lua 解压落地到
 * <root>/script/”。被省掉的正是每条一次的 open/creat+close（实测占脚本落地耗时的主体，
 * inflate 只占零头），并且档案是唯一事实源：开局、撤回重建、旧格式回放与 MSG_RETRY 重派
 * 都经同一个 reader 入口，脚本内容不可能分叉。
 */
class ScriptsArchive {
public:
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
        std::unordered_map<std::string, ZipEntryRef> index;
        if (buildIndex(fd, st.st_size, index)) {
            m_fd = fd;
            m_path = path;
            m_size = st.st_size;
            m_mtime = st.st_mtime;
            m_index = std::move(index);
            __android_log_print(ANDROID_LOG_INFO, ENGINE_TAG, "scripts.zip 索引 %zu 条：%s",
                                m_index.size(), path.c_str());
            return;
        }
        ::close(fd);
    }

    const ZipEntryRef* find(const std::string& bareName) const {
        auto it = m_index.find(bareName);
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
    }

    /**
     * 解析 EOCD（含 zip64 入口）与中央目录。条目名两种布局都兼容：裸文件名（现仓库/CI
     * 产出）与带 script/ 前缀（历史包）；其它目录（.git/ 等）与不支持的方法一律不入索引。
     */
    bool buildIndex(int fd, off_t size,
            std::unordered_map<std::string, ZipEntryRef>& out) {
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
            if (!name.empty() && name.back() == '/')
                continue;     // 目录条目
            for (char& c : name) {
                if (c == '\\')
                    c = '/';
            }
            std::string key;
            const size_t slash = name.find('/');
            if (slash == std::string::npos) {
                key = name;
            } else if (name.size() > 7 && name.compare(0, 7, "script/") == 0
                    && name.find('/', 7) == std::string::npos) {
                key = name.substr(7);
            } else {
                continue;     // 其它目录布局不属于主脚本区
            }
            if (key.empty() || key == ".gitignore" || key == ".gitattributes")
                continue;
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
            out[key] = ref;
        }
        return !out.empty();
    }

    int m_fd = -1;
    std::string m_path;
    off_t m_size = 0;
    time_t m_mtime = 0;
    std::unordered_map<std::string, ZipEntryRef> m_index;
};

ScriptsArchive g_scriptsZip;
std::string g_scriptsZipPath;
// g_scriptBuffer 与档案索引是共享资源：多个决斗/回放线程同时读脚本时整体串行
std::mutex g_scriptMutex;

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
// 优先级对齐 data_manager.cpp::ScriptReaderEx（prefer_expansion_script=1）——
// 卡片脚本：① <root>/expansions/（散调试文件 + Java 引导按包名字典序升序覆写物化的卡包
// 脚本，即靠后的包优先）② <root>/scripts.zip（主脚本档案直读）③ <root>/script/
//（老安装残留或手工放置）；非卡片脚本（single/ 等）仍旧是主目录优先、expansions 兜底。
uint8_t* engineScriptReader(const char* scriptPath, int* slen) {
    if (!scriptPath || g_rootPath.empty())
        return nullptr;
    const char* rel = scriptPath;
    if (rel[0] == '.' && rel[1] == '/')
        rel += 2;
    const bool cardScript = std::strncmp(rel, "script/", 7) == 0;
    char full[1024];
    // g_scriptBuffer 与档案索引都是共享资源，多个决斗/回放线程同时读时整体串行
    std::lock_guard<std::mutex> guard(g_scriptMutex);
    if (cardScript) {
        // ① expansions 散文件 / 卡包物化区（最高，与旧行为一致）
        std::snprintf(full, sizeof full, "%s/expansions/%s", g_rootPath.c_str(), rel);
        if (readFileInto(full, slen))
            return g_scriptBuffer;
        // ② 主脚本档案：中央目录索引 + pread/inflate 就地取，不再依赖落地解压的 lua
        g_scriptsZip.ensureOpen(g_scriptsZipPath);
        const ZipEntryRef* ref = g_scriptsZip.find(std::string(rel + 7));
        if (ref != nullptr) {
            const int n = g_scriptsZip.read(*ref, g_scriptBuffer, sizeof g_scriptBuffer);
            if (n >= 0) {
                *slen = n;
                return g_scriptBuffer;
            }
        }
    }
    // ③ 卡片脚本回落实体目录；非卡片脚本保持主目录优先、expansions 兜底
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
 * rootPath 为资源根目录（含 script/、single/）；cdbPaths 按优先级从低到高排列
 * （主库在前，扩展卡包物化 cdb、expansions 散 cdb 在后，后加载覆盖同名卡）。
 */
JNIEXPORT jboolean JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeInit(JNIEnv* env, jclass /*clazz*/,
                                                         jstring rootPath, jobjectArray cdbPaths) {
    if (rootPath == nullptr)
        return JNI_FALSE;
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
    resolveRuleCodes();
    if (g_cardData.empty())
        return JNI_FALSE;

    set_script_reader(engineScriptReader);
    set_card_reader(engineCardReader);
    set_message_handler(engineMessageHandler);
    return JNI_TRUE;
}

/* 重新装载卡片数据（卡组编辑/扩展变更后调用），沿用已设置的 rootPath */
JNIEXPORT void JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeReloadCards(JNIEnv* env, jclass /*clazz*/,
                                                                 jobjectArray cdbPaths) {
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
