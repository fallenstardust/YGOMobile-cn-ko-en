package cn.garymb.ygomobile;

import android.view.Gravity;

import cn.garymb.ygomobile.lite.BuildConfig;

public interface Constants {
    boolean DEBUG = BuildConfig.DEBUG;
    String PREF_START = "game_pref_";
    String PREF_LAST_YDK = "pref_last_ydk";
    String PREF_DEF_LAST_YDK = "new";
    String PREF_GAME_PATH = "pref_key_game_res_path";
    String PREF_DEF_GAME_DIR = "ygocore";

    String PREF_GAME_VERSION = "game_version";

    String PREF_IMAGE_QUALITY = "pref_key_game_image_quality";
    int PREF_DEF_IMAGE_QUALITY = 1;
    String PREF_GAME_FONT = "pref_key_game_font_name";
    String PREF_USE_EXTRA_CARD_CARDS = "settings_game_diy_card_db";
    boolean PREF_DEF_USE_EXTRA_CARD_CARDS = false;
    String PREF_FONT_ANTIALIAS = "pref_key_game_font_antialias";
    boolean PREF_DEF_FONT_ANTIALIAS = true;
    String PREF_OPENGL_VERSION = "pref_key_game_ogles_config";
    int PREF_DEF_OPENGL_VERSION = 1;
    String PREF_PENDULUM_SCALE = "pref_key_game_lab_pendulum_scale";
    boolean PREF_DEF_PENDULUM_SCALE = false;
    String PREF_SOUND_EFFECT = "pref_key_game_sound_effect";
    boolean PREF_DEF_SOUND_EFFECT = true;
    String PREF_LOCK_SCREEN = "pref_key_game_screen_orientation";
    boolean PREF_DEF_LOCK_SCREEN = true;
    String PREF_IMMERSIVE_MODE = "pref_key_immersive_mode";
    boolean PREF_DEF_IMMERSIVE_MODE = false;
    String PREF_SENSOR_REFRESH = "pref_key_sensor_refresh";
    boolean PREF_DEF_SENSOR_REFRESH = true;
    String PREF_CHANGE_LOG = "pref_key_change_log";
    String PREF_CHECK_UPDATE = "pref_key_about_check_update";
    String PREF_LAST_ROOM_LIST = "pref_key_lastroom_list";
    int LAST_ROOM_MAX = 10;
    /***
     * 卡组编辑，长按删除对话框
     */
    String PREF_DECK_DELETE_DILAOG = "pref_key_deck_delete_dialog";
    boolean PREF_DEF_DECK_DELETE_DILAOG = true;

    String SETTINGS_COVER = "settings_game_diy_card_cover";
    String SETTINGS_CARD_BG = "settings_game_diy_card_bg";
    String ASSETS_PATH = "data/";
    String ASSET_SERVER_LIST = "serverlist.xml";
    String ASSET_LIMIT_PNG = ASSETS_PATH + "textures/lim.png";
    String DEFAULT_FONT_NAME = "ygo.ttf";
    String DATABASE_NAME = "cards.cdb";
    String WINDBOT_PATH="windbot";
    String FONT_DIRECTORY = "fonts";
    String CORE_STRING_PATH = "strings.conf";
    String CORE_LIMIT_PATH = "lflist.conf";
    String CORE_SYSTEM_PATH = "system.conf";
    String CORE_SKIN_PATH = "textures";
    String CORE_SKIN_PENDULUM_PATH = CORE_SKIN_PATH + "/extra";
    String CORE_DECK_PATH = "deck";
    String CORE_EXPANSIONS ="expansions";
    String CORE_SINGLE_PATH = "single";
    String CORE_IMAGE_PATH = "pics";
    String CORE_IMAGE_FIELD_PATH = "field";
    String CORE_SCRIPT_PATH = "script";
    String CORE_REPLAY_PATH = "replay";
    String CORE_SCRIPTS_ZIP = "scripts.zip";
    String CORE_PICS_ZIP = "pics.zip";
    String CORE_SKIN_COVER = "cover.jpg";
    String CORE_SKIN_BG = "bg.jpg";
    String UNKNOWN_IMAGE = "unknown.jpg";
    String YDK_FILE_EX = ".ydk";
    int[] CORE_SKIN_BG_SIZE = new int[]{1280, 720};
    int[] CORE_SKIN_CARD_COVER_SIZE = new int[]{177, 254};
    boolean SUPPORT_BPG = true;
    String BPG = ".bpg";
    int CARD_MAX_COUNT = 3;
    String[] IMAGE_EX = SUPPORT_BPG ? new String[]{".bpg", ".jpg", ".png"}
            : new String[]{".jpg", ".png"};

    String[] FILE_IMAGE_EX = new String[]{".bmp", ".jpg", ".png", ".gif"};

    String PREF_FONT_SIZE = "pref_settings_font_size";
    int DEF_PREF_FONT_SIZE = 14;


    String PREF_ONLY_GAME = "pref_settings_only_game";
    boolean DEF_PREF_ONLY_GAME = false;

    String PREF_READ_EX = "pref_settings_read_ex";
    boolean DEF_PREF_READ_EX = false;

    String PREF_DECK_MANAGER_V2 = "pref_settings_deck_manager_v2";
    boolean DEF_PREF_DECK_MANAGER_V2 = false;

    int REQUEST_CUT_IMG = 0x1000 + 0x10;
    int REQUEST_CHOOSE_FILE = 0x1000 + 0x20;
    int REQUEST_CHOOSE_IMG = 0x1000 + 0x21;
    int REQUEST_CHOOSE_FOLDER = 0x1000 + 0x22;
    int STRING_TYPE_START = 1050;

    int STRING_ATTRIBUTE_START = 1010;
    int STRING_RACE_START = 1020;
    int STRING_OT_START = 1239;

    int UNSORT_TIMES = 0x80;

    int CARD_SEARCH_GRAVITY = Gravity.RIGHT;
    int STRING_LIMIT_START = 1315;
    int STRING_CATEGORY_START = 1100;
    int DEFAULT_CARD_COUNT = 500;
    int DECK_WIDTH_MAX_COUNT = 15;
    int DECK_WIDTH_COUNT = 10;
    int DECK_MAIN_MAX = 60;
    int DECK_EXTRA_MAX = 15;
    int DECK_SIDE_MAX = 15;
    int DECK_EXTRA_COUNT = (DECK_SIDE_MAX / DECK_WIDTH_COUNT * DECK_WIDTH_COUNT < DECK_SIDE_MAX) ? DECK_WIDTH_COUNT * 2 : DECK_WIDTH_COUNT;
    int DECK_SIDE_COUNT = DECK_EXTRA_COUNT;
    String ALIPAY_URL = "HTTPS://QR.ALIPAY.COM/FKX06491UAXJMGIDTYVC0C";
    String DOWNLOAD_HOME = "http://pan.baidu.com/s/1o7RMcMA";
    String URL_HELP = "http://www.jianshu.com/p/a43f5d951a25";
    String WIKI_SEARCH_URL = "http://www.ourocg.cn/S.aspx?key=";

    String SERVER_FILE = "server_list.xml";
    String SHARE_FILE = ".share_deck.png";

    long LOG_TIME = 2 * 1000;
    /***
     * 如果是双击显示，则单击拖拽
     */
    boolean DECK_SINGLE_PRESS_DRAG = true;

    /***
     * 长按删除
     */
    long LONG_PRESS_DRAG = 800;
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
    String IMAGE_URL = "https://github.com/Ygoproco/Live-images/raw/master/pics/%s.jpg";
    String IMAGE_FIELD_URL = "https://github.com/Ygoproco/Live-images/raw/master/pics/field/%s.png";
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
    String SCHEME_APP = "ygomobile";
    String URI_HOST = "m.ygomobile.com";

    String QUERY_YDK = "ydk";
    String QUERY_NAME = "name";
    String QUERY_MAIN = "main";
    String QUERY_EXTRA = "extra";
    String QUERY_SIDE = "side";
    String PATH_ROOM = "/room";
    String QUERY_HOST = "host";
    String QUERY_PORT = "port";
    String QUERY_USER = "user";
    String QUERY_ROOM = "room";

    //额外的cdb
    boolean NETWORK_IMAGE = false;
    boolean SHOW_MYCARD = !"core".equals(BuildConfig.FLAVOR);
}
