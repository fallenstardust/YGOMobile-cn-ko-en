/*
 * ygo_engine_jni.cpp
 *
 * 薄 JNI 决斗引擎封装：仅暴露 ocgcore（ocgapi.h）的干净 C 接口子集给 Java，
 * 不链接 irrlicht / freetype / openal / openssl / libevent 等渲染或网络库。
 * 自带：
 *   - 卡片数据读取（sqlite3 直读 cards.cdb + 扩展 cdb，逻辑对齐 data_manager.cpp::ReadDB）
 *   - Lua 脚本读取（从资源根目录下的 script/ 、single/ 、expansions/script/ 读取）
 * 决斗消息循环由 Java 侧（ServerDuel）驱动，本层只做无状态转发。
 */
#include <jni.h>
#include <cstdio>
#include <cstring>
#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

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

const char SELECT_STMT[] =
    "SELECT datas.id, datas.alias, datas.setcode, datas.type, datas.atk, datas.def,"
    " datas.level, datas.race, datas.attribute FROM datas";

inline bool is_alternative(uint32_t code, uint32_t alias) {
    return alias && (alias < code + CARD_ARTWORK_VERSIONS_OFFSET)
           && (code < alias + CARD_ARTWORK_VERSIONS_OFFSET);
}

// 把一个 cdb 文件读入 g_cardData，后加载者覆盖先前同 code 项（扩展卡先、主库后，主库优先）
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

// 把 "./script/c1.lua" / "./single/x.lua" 解析为绝对路径并读取；依次尝试 root 与 root/expansions
byte* engineScriptReader(const char* scriptPath, int* slen) {
    if (!scriptPath || g_rootPath.empty())
        return nullptr;
    const char* rel = scriptPath;
    if (rel[0] == '.' && rel[1] == '/')
        rel += 2;
    char full[1024];
    // 1) root/rel
    std::snprintf(full, sizeof full, "%s/%s", g_rootPath.c_str(), rel);
    FILE* fp = std::fopen(full, "rb");
    if (!fp) {
        // 2) root/expansions/rel
        std::snprintf(full, sizeof full, "%s/expansions/%s", g_rootPath.c_str(), rel);
        fp = std::fopen(full, "rb");
    }
    if (!fp)
        return nullptr;
    size_t len = std::fread(g_scriptBuffer, 1, sizeof g_scriptBuffer, fp);
    std::fclose(fp);
    if (len >= sizeof g_scriptBuffer)
        return nullptr;
    *slen = static_cast<int>(len);
    return g_scriptBuffer;
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
 * rootPath 为资源根目录（含 script/、single/）；cdbPaths 为主库 + 扩展库（主库放最后覆盖）。
 */
JNIEXPORT jboolean JNICALL
Java_cn_garymb_ygomobile_engine_OcgDuelEngine_nativeInit(JNIEnv* env, jclass /*clazz*/,
                                                         jstring rootPath, jobjectArray cdbPaths) {
    if (rootPath == nullptr)
        return JNI_FALSE;
    const char* rp = env->GetStringUTFChars(rootPath, nullptr);
    g_rootPath = rp ? rp : "";
    if (rp) env->ReleaseStringUTFChars(rootPath, rp);

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
    std::vector<byte> tmp(len > 0 ? len : 1, 0);
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
