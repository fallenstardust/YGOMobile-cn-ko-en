package cn.garymb.ygomobile;

import android.os.Environment;
import android.view.Gravity;

import cn.garymb.ygomobile.lite.BuildConfig;

public interface Constants {
    boolean DEBUG = BuildConfig.DEBUG;
    String PREF_START = "game_pref_";
    String PREF_LAST_LIMIT = "pref_last_limit";
    String PREF_LAST_GENESYS_MODE = "pref_last_genesys_mode";
    int PREF_DEF_LAST_GENESYS_MODE = 0;//0代表传统禁限模式，1代表Genesys模式
    String PREF_DEF_LAST_LIMIT = "";
    String PREF_LAST_GENESYS_LIMIT = "pref_last_genesys_limit";
    String PREF_DEF_LAST_GENESYS_LIMIT = "";
    String PREF_LAST_DECK_PATH = "pref_last_deck_path";
    String PREF_LAST_YDK = "pref_last_ydk";
    String PREF_DEF_LAST_YDK = "new";
    String PREF_LAST_CATEGORY = "pref_last_category";
    String PREF_DEF_LAST_CATEGORY = "newcate";
    String PREF_GAME_PATH = "pref_key_game_res_path";
    String PREF_DEF_GAME_DIR = "ygocore";

    String PREF_GAME_VERSION = "game_version";

    String PREF_IMAGE_QUALITY = "pref_key_game_image_quality";
    int PREF_DEF_IMAGE_QUALITY = 1;
    String PREF_DATA_LANGUAGE = "pref_key_game_data_language";
    int PREF_DEF_DATA_LANGUAGE = -1;
    String PREF_KEY_WORDS_SPLIT = "pref_key_words_split";
    int PREF_DEF_KEY_WORDS_SPLIT = 1;
    String PREF_GAME_FONT = "pref_key_game_font_name";
    String PREF_USE_EXTRA_CARD_CARDS = "settings_game_diy_card_db";
    boolean PREF_DEF_USE_EXTRA_CARD_CARDS = true;
    String PREF_FONT_ANTIALIAS = "pref_key_game_font_antialias";
    boolean PREF_DEF_FONT_ANTIALIAS = true;
    String PREF_OPENGL_VERSION = "pref_key_game_ogles_config";
    int PREF_DEF_OPENGL_VERSION = 2;
    String PREF_PENDULUM_SCALE = "pref_key_game_lab_pendulum_scale";
    boolean PREF_DEF_PENDULUM_SCALE = true;
    String PREF_START_SERVICEDUELASSISTANT = "pref_key_start_serviceduelassistant";
    boolean PREF_DEF_START_SERVICEDUELASSISTANT = true;
    String PREF_LOCK_SCREEN = "pref_key_game_screen_orientation";
    boolean PREF_DEF_LOCK_SCREEN = false;
    String PREF_IMMERSIVE_MODE = "pref_key_immersive_mode";
    boolean PREF_DEF_IMMERSIVE_MODE = false;
    String PREF_SENSOR_REFRESH = "pref_key_sensor_refresh";
    boolean PREF_DEF_SENSOR_REFRESH = true;
    String PREF_CHANGE_LOG = "pref_key_change_log";
    String PREF_CHECK_UPDATE = "pref_key_about_check_update";
    String PREF_RESET_GAME_RES = "pref_key_reset_game_res";
    String PREF_USER_PRIVACY_POLICY = "pref_user_privacy_policy";
    String PREF_JOIN_QQ = "pref_key_join_qq";
    String PREF_DEL_EX = "pref_key_settings_delete_ex";
    String PREF_LAST_ROOM_LIST = "pref_key_lastroom_list";
    String PERF_TEST_REPLACE_KERNEL = "pref_key_test_replace_kernel";
    int LAST_ROOM_MAX = 10;
    /***
     * 卡组编辑，长按删除对话框
     */
    String PREF_DECK_DELETE_DILAOG = "pref_key_deck_delete_dialog";
    boolean PREF_DEF_DECK_DELETE_DILAOG = false;

    String SETTINGS_COVER = "settings_game_diy_card_cover";
    String SETTINGS_AVATAR = "settings_game_avatar";
    String SETTINGS_CARD_BG = "settings_game_diy_card_bg";
    String ASSETS_EN = "en/";
    String ASSETS_KOR = "kor/";
    String ASSETS_ES = "es/";
    String ASSETS_JP = "jp/";
    String ASSETS_PT = "pt/";
    String ASSETS_PATH = "data/";
    String ASSET_SERVER_LIST = "serverlist.xml";
    String ASSET_LIMIT_PNG = ASSETS_PATH + "textures/lim.png";
    String ASSET_GENESYS_LIMIT_PNG = ASSETS_PATH + "textures/lim_credit.png";
    String DEFAULT_FONT_NAME = "ygo.ttf";
    String DATABASE_NAME = "cards.cdb";
    String BOT_CONF = "bot.conf";
    String WINDBOT_PATH = "windbot";
    String WINDBOT_DECK_PATH = "Decks";
    String FONT_DIRECTORY = "fonts";
    String CORE_STRING_PATH = "strings.conf";
    String CORE_LIMIT_PATH = "lflist.conf";
    String CORE_GENESYS_LIMIT_PATH = "genesys_official_lflist.conf";
    String CORE_CUSTOM_LIMIT_PATH = "expansions/lflist.conf";
    String CORE_CUSTOM_STRING_PATH = "pre-strings.conf";
    String CORE_SYSTEM_PATH = "system.conf";
    String CORE_BOT_CONF_PATH = "bot.conf";
    String CORE_SOUND_PATH = "sound";
    String CORE_SKIN_PATH = "textures";
    String CORE_SKIN_PENDULUM_PATH = CORE_SKIN_PATH + "/extra";
    String CORE_AVATAR_PATH = CORE_SKIN_PATH + "/extra/avatars";
    String CORE_COVER_PATH = CORE_SKIN_PATH + "/extra/covers";
    String CORE_BG_PATH = CORE_SKIN_PATH + "/extra/bgs";
    String CORE_DECK_PATH = "deck";
    String CORE_PACK_PATH = "pack";
    String CORE_EXPANSIONS = "expansions";
    String CORE_SINGLE_PATH = "single";
    String CORE_IMAGE_PATH = "pics";
    String MOBILE_LOG = "log";
    String MOBILE_DECK_SHARE = "deckShare";
    String CORE_EXPANSIONS_IMAGE_PATH = "expansions/pics";
    String CORE_IMAGE_FIELD_PATH = "field";
    String CORE_SCRIPT_PATH = "script";
    String CORE_REPLAY_PATH = "replay";
    String CORE_SCRIPTS_ZIP = "scripts.zip";
    String CORE_PICS_ZIP = "pics.zip";
    String CORE_SKIN_COVER = "cover.jpg";
    String CORE_SKIN_COVER2 = "cover2.jpg";
    String CORE_SKIN_BG = "bg.jpg";
    String CORE_SKIN_BG_MENU = "bg_menu.jpg";
    String CORE_SKIN_BG_DECK = "bg_deck.jpg";
    String CORE_SKIN_AVATAR_ME = "me.jpg";
    String CORE_SKIN_AVATAR_OPPONENT = "opponent.jpg";
    String UNKNOWN_IMAGE = "unknown.jpg";
    String YDK_FILE_EX = ".ydk";
    String YRP_FILE_EX = ".yrp";
    String YPK_FILE_EX = ".ypk";
    String LUA_FILE_EX = ".lua";
    int[] CORE_SKIN_BG_SIZE = new int[]{1920, 1080};

    int[] CORE_SKIN_CARD_MINI_SIZE = new int[]{44, 64};
    int[] CORE_SKIN_CARD_SMALL_SIZE = new int[]{177, 254};
    //原图
    int[] CORE_SKIN_CARD_MIDDLE_SIZE = new int[]{397, 578};

    int[] CORE_SKIN_CARD_COVER_SIZE = new int[]{200, 290};
    int[] CORE_SKIN_AVATAR_SIZE = new int[]{128, 128};
    boolean SUPPORT_BPG = true;
    String BPG = ".bpg";
    int CARD_MAX_COUNT = 3;
    String[] IMAGE_EX = SUPPORT_BPG ? new String[]{".bpg", ".jpg", ".png"}
            : new String[]{".jpg", ".png"};

    String[] FILE_IMAGE_EX = new String[]{".bmp", ".jpg", ".png", ".gif"};

    String PREF_FONT_SIZE = "pref_settings_font_size";
    int DEF_PREF_FONT_SIZE = 14;

    String PREF_NOTCH_HEIGHT = "pref_notch_height";
    int DEF_PREF_NOTCH_HEIGHT = 0;

    String PREF_ONLY_GAME = "pref_settings_only_game";
    boolean DEF_PREF_ONLY_GAME = false;

    String PREF_READ_EX = "pref_settings_read_ex";
    boolean DEF_PREF_READ_EX = true;

    String PREF_KEEP_SCALE = "pref_settings_keep_scale";
    boolean DEF_PREF_KEEP_SCALE = false;

    //dp单位，游戏高度减少，留空白
    String PREF_WINDOW_TOP_BOTTOM = "pref_settings_window_top_bottom";
    int DEF_PREF_WINDOW_TOP_BOTTOM = 0;

    int REQUEST_CUT_IMG = 0x1000 + 0x10;
    int REQUEST_CHOOSE_FILE = 0x1000 + 0x20;
    int REQUEST_CHOOSE_IMG = 0x1000 + 0x21;
    int REQUEST_CHOOSE_FOLDER = 0x1000 + 0x22;
    int REQUEST_SETTINGS_CODE = 0x1000 + 0x23;

    int UNSORT_TIMES = 0x80;

    int CARD_RESULT_GRAVITY = Gravity.LEFT;
    int CARD_SEARCH_GRAVITY = Gravity.RIGHT;
    int DEFAULT_CARD_COUNT = 500;
    int DECK_WIDTH_MAX_COUNT = 15;
    int DECK_WIDTH_COUNT = 10;
    int DECK_MAIN_MAX = 60;
    int DECK_EXTRA_MAX = 15;
    int DECK_SIDE_MAX = 15;
    int DECK_EXTRA_COUNT = (DECK_SIDE_MAX / DECK_WIDTH_COUNT * DECK_WIDTH_COUNT < DECK_SIDE_MAX) ? DECK_WIDTH_COUNT * 2 : DECK_WIDTH_COUNT;
    int DECK_SIDE_COUNT = DECK_EXTRA_COUNT;
    String URL_HELP = "https://ygom.top/tutorial.html";
    String URL_DONATE = "https://afdian.com/@ygomobile";
    String URL_MASTER_RULE_CN = "https://ocg-rule.readthedocs.io/";
    String WIKI_SEARCH_URL = "https://ygocdb.com/card/";
    String URL_HOME_VERSION = "https://ygom.top/ver_code.txt";
    String URL_HOME_VERSION_ALT = "https://cdn02.moecube.com:444/ygom-site/ver_code.txt";
    String URL_BILIBILI_DYNAMIC = "https://m.bilibili.com/space/16033444/";
    String ID1 = "[versionname]";
    String ID2 = "[download_link]";
    String ID3 = "#pre_release_code";
    String URL_GENESYS_LFLIST_DOWNLOAD_LINK = "https://cdntx.moecube.com/ygopro-genesys/lflist.conf";
    String URL_YGO233_ADVANCE = "";//"https://ygo233.com/pre#pre_release_cards";//关闭233先行卡服务器，但不要删除该字段，许多未调用的遗留代码使用该contant
    String URL_CN_DATAVER = "https://cdn02.moecube.com:444/ygopro-super-pre/data/version.txt";
    String URL_PRE_CARD = "https://cdn02.moecube.com:444/ygopro-super-pre/data/test-release.json";
    String URL_SUPERPRE_CN_FILE = "https://cdn02.moecube.com:444/ygopro-super-pre/archive/ygopro-super-pre.ypk";
    String URL_SUPERPRE_CN_FILE_ALT = "https://cdn02.moecube.com:444/ygopro-super-pre/archive/ygopro-super-pre.ypk";
    String URL_YGO233_BUG_REPORT = "https://ygo233.com/pre#faq";
    int PORT_Mycard_Super_Pre_Server = 888;
    String URL_Mycard_Super_Pre_Server = "mygo.superpre.pro";
    String URL_Mycard_Super_Pre_Server_2 = "mygo2.superpre.pro";
    String PlayerName = "Knight of Hanoi";

    String SERVER_FILE = "server_list.xml";
    String SHARE_FILE = ".share_deck.png";

    //原目录文件路径
    String ORI_DECK = Environment.getExternalStorageDirectory() + "/" + Constants.PREF_DEF_GAME_DIR + "/" + Constants.CORE_DECK_PATH;
    String ORI_REPLAY = Environment.getExternalStorageDirectory() + "/" + Constants.PREF_DEF_GAME_DIR + "/" + Constants.CORE_REPLAY_PATH;
    String ORI_TEXTURES = Environment.getExternalStorageDirectory() + "/" + Constants.PREF_DEF_GAME_DIR + "/" + Constants.CORE_SKIN_PATH;
    String ORI_PICS = Environment.getExternalStorageDirectory() + "/" + Constants.PREF_DEF_GAME_DIR + "/" + Constants.CORE_IMAGE_PATH;

    long LOG_TIME = 2 * 1000;
    /***
     * 如果是双击显示，则单击拖拽
     */
    boolean DECK_SINGLE_PRESS_DRAG = true;

    /***
     * 长按删除
     */
    long LONG_PRESS_DRAG = 500;
    /***
     * adb shell am start -n cn.garymb.ygomobile/cn.garymb.ygomobile.ui.home.MainActivity -a ygomobile.intent.action.DECK --es android.intent.extra.TEXT 青眼白龙.ydk
     * <p>
     * adb shell am start -n cn.garymb.ygomobile/cn.garymb.ygomobile.ui.home.MainActivity -a ygomobile.intent.action.DECK --es android.intent.extra.TEXT /sdcard/ygocore/deck/青眼白龙.ydk
     */
    String ACTION_OPEN_DECK = "ygomobile.intent.action.DECK";
    /***
     * Intent intent1=new Intent("ygomobile.intent.action.GAME");
     * intent1.putExtra("host", "127.0.0.1");
     * intent1.putExtra("port", 233);
     * intent1.putExtra("user", "player");
     * intent1.putExtra("room", "room$123");
     * intent1.setPackage("cn.garymb.ygomobile");
     * startActivity(intent1);
     */
    String ACTION_OPEN_GAME = "ygomobile.intent.action.GAME";
    String ACTION_RELOAD = "ygomobile.intent.action.RELOAD";
    String IMAGE_URL = "https://github.com/fallenstardust/YGOMobile-pics/master/pics/%s.jpg";
    String IMAGE_FIELD_URL = "https://github.com/fallenstardust/YGOMobile-pics/master/pics/field/%s.jpg";
    String IMAGE_URL_EX = ".jpg";
    String IMAGE_FIELD_URL_EX = ".png";
    /**
     * https://m.ygomobile.com/deck?ydk=卡组名&main=124563789,12456487&extra=123,145&side=4564,4546
     * ygomobile://m.ygomobile.com/deck?ydk=卡组名&main=124563789,12456487&extra=123,145&side=4564,4546
     * <p>
     * https://m.ygomobile.com/game?host=127.0.0.1&port=233&user=player&room=1235$123
     * ygomobile://m.ygomobile.com/game?host=127.0.0.1&port=233&user=player&room=1235$123
     */
    String PATH_DECK = "/deck";
    String SCHEME_HTTP = "http";
    String SCHEME_HTTPS = "https";
    String SCHEME_APP = "http";
    String URI_HOST = "deck.ourygo.top";
    String URI_DECK = "deck";
    String URI_ROOM = "room";

    String QUERY_YDK = "ydk";
    String QUERY_NAME = "name";
    /**
     * 打开卡组的路径，参数可以是以下两种形式:
     * <br/>1、青眼白龙.ydk
     * <br/> 2、/sdcard/ygocore/deck/青眼白龙.ydk
     */
    String ARG_OPEN_DECK_PATH = "openDeckPath";
    String PATH_ROOM = "/room";
    String QUERY_HOST = "host";
    String QUERY_PORT = "port";
    String QUERY_USER = "user";
    String QUERY_ROOM = "room";

    //额外的cdb
    boolean NETWORK_IMAGE = false;
    boolean SHOW_MYCARD = !"core".equals(BuildConfig.FLAVOR);

    //打开ydk，是否复制到文件夹
    boolean COPY_YDK_FILE = false;

    String TAG = "ygo-java";

    String DEF_ENCODING = "utf-8";



    public final String officialExCardPackageName = "ygopro-super-pre";//官方扩展卡包ypk文件的名称
    public final String mercuryExCardPackageName = "ygo233.com-pre-release";//23333扩展卡包ypk文件的名称
    public final String cacheExCardPackageName = "内测先行卡";//临时扩展卡包ypk文件的名称
}
