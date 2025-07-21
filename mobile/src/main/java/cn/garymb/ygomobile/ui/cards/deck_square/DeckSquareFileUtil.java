package cn.garymb.ygomobile.ui.cards.deck_square;

import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.ui.cards.deck_square.bo.MyDeckItem;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.LogUtil;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.data.Card;

public class DeckSquareFileUtil {
    //
    private static final String TAG = "DeckSquareFileUtil";

    //将MyOnlineDeckDetail转MyDeckItem类型list，有时候会需要用到
    public static List<MyDeckItem> toDeckItemList(List<MyOnlineDeckDetail> serverDecks) {
        List<MyDeckItem> myOnlineDecks = new ArrayList<>();
        for (MyOnlineDeckDetail detail : serverDecks) {
            MyDeckItem item = new MyDeckItem();
            item.setDeckName(detail.getDeckName());
            item.setDeckId(detail.getDeckId());
            item.setUserId(detail.getUserId());
            item.setDeckCoverCard1(detail.getDeckCoverCard1());
            item.setUpdateTimestamp(detail.getDeckUpdateDate());
            item.setPublic(detail.isPublic());
            myOnlineDecks.add(item);
        }
        return myOnlineDecks;
    }

    //读取file指定的ydk文件，返回其内包含的deckId。如果不包含deckId，返回null
    public static String getId(File file) {
        String deckId = null;
        Integer userId = null;
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);

            InputStreamReader in = null;
            in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(in);

            String line = null;
            while ((line = reader.readLine()) != null) {
               // LogUtil.i(TAG, line);
                if (line.startsWith("###")) {//注意，先判断###，后判断##。因为###会包括##的情况
                    try {
                        String data = line.replace("#", "");
                        if (!data.isEmpty()) {
                            userId = Integer.parseInt(data);
                        }
                        // userId = Integer.parseInt(line.replaceAll("###", ""));
                    } catch (NumberFormatException e) {
                        LogUtil.e(TAG, "getId(77): integer" + line + "parse error" + e.toString());
                    }

                } else if (line.startsWith("##")) {
                    line = line.replace("#", "");
                    deckId = line;

                }

            }

        } catch (IOException e) {

            LogUtil.e(TAG, "read 1", e);
        } finally {

            IOUtils.close(inputStream);
        }
        return deckId;
    }

    /**
     * 查询卡组目录下的所有ydk文件（包含子文件夹）
     *
     * @return 包含所有ydk文件的File数组
     */
    public static File[] getAllYdk() {
        File dir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_DECK_PATH);
        if (!dir.exists() || !dir.isDirectory()) {
            return new File[0];
        }
        // 使用ArrayList存储结果，方便动态添加
        ArrayList<File> ydkFiles = new ArrayList<>();
        // 递归遍历目录和子目录
        findYdkFiles(dir, ydkFiles);
        // 将ArrayList转换为File数组
        return ydkFiles.toArray(new File[0]);
    }

    /**
     * 递归查找指定目录下的所有YDK文件
     *
     * @param dir      当前查找的目录
     * @param ydkFiles 存储找到的YDK文件
     */
    private static void findYdkFiles(File dir, ArrayList<File> ydkFiles) {
        // 获取目录下的所有文件和子目录
        File[] files = dir.listFiles();
        if (files == null) {
            return; // 目录不可访问或为空
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 如果是子目录，递归查找
                findYdkFiles(file, ydkFiles);
            } else {
                // 如果是文件，检查是否为YDK文件
                String fileName = file.getName().toLowerCase(Locale.US);
                if (fileName.endsWith(Constants.YDK_FILE_EX)) {
                    ydkFiles.add(file);
                }
            }
        }
    }

    //读取卡组目录下的所有ydk文件，解析ydk文件（包括从ydk文件内容中读取deckId），生成List<MyDeckItem>解析结果
    public static List<MyDeckItem> getMyDeckItem() {
        List<MyDeckItem> result = new ArrayList<>();
        File[] files = getAllYdk();
        for (File file : files) {
            String deckId = getId(file);
            MyDeckItem item = new MyDeckItem();
            item.setDeckName(file.getName());
            item.setDeckType(file.getParentFile().getName());
            item.setUpdateTimestamp(file.lastModified());
            item.setDeckPath(file.getPath());
            if (deckId != null) {
                item.setDeckId(deckId);
                item.setIdUploaded(2);
            } else {
                item.setIdUploaded(0);
            }
            result.add(item);
        }
        return result;
    }

    // 读取deckPath对应的卡组文件到string，将卡组id、用户id设置到string中
    //注意，不更改ydk文件内容
    //下载卡组后，保存卡组之前
    //上传卡组前，对于未存在卡组id的卡组，自动填入新的卡组id、用户id
    public static String setDeckId(String deckPath, Integer userId, String deckId) {
        StringBuilder contentBuilder = new StringBuilder();
        boolean userIdFlag = false;
        boolean deckIdFlag = false;

        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(deckPath);

            InputStreamReader in = null;
            in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(in);

            String line = null;
            while ((line = reader.readLine()) != null) {
                /* 如果文件中已经存在deckId，则将其替换 */
                if (line.contains("##") && !line.contains("###")) {
                    deckIdFlag = true;
                    line = "##" + deckId;
                } else if (line.contains("###")) {//存在userId，将其替换
                    userIdFlag = true;
                    line = "###" + userId;
                }
                contentBuilder.append(line);
                contentBuilder.append('\n'); // Add line break
            }

        } catch (Exception e) {
            LogUtil.e(TAG, "read 1", e);
        } finally {
            IOUtils.close(inputStream);
        }


        if (!userIdFlag) {//原始ydk中不存在userId，添加userId行
            contentBuilder.append("\n");
            contentBuilder.append("##" + userId);
        }
        if (!deckIdFlag) {//原始ydk中不存在deckId，添加deckId行
            contentBuilder.append("\n");
            contentBuilder.append("###" + deckId);
        }

        String content = contentBuilder.toString();

        return content;
    }

    public static boolean saveFile(File file, String content, long modificationTime) {
        FileOutputStream fos = null;
        try {
            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean dirsCreated = parentDir.mkdirs(); // 创建所有缺失的父目录
                if (!dirsCreated) {
                    LogUtil.e(TAG, "无法创建文件目录: " + parentDir.getAbsolutePath());
                    return false;
                }
            }

            // 创建文件对象（如果文件不存在，会自动创建）
            if (!file.exists()) {
                boolean fileCreated = file.createNewFile();
                if (!fileCreated) {
                    LogUtil.e(TAG, "无法创建文件: " + file.getAbsolutePath());
                    return false;
                }
            }

            // 创建文件输出流
            fos = new FileOutputStream(file);
            // 写入内容
            fos.write(content.getBytes(StandardCharsets.UTF_8)); // 使用 UTF-8 编码
            fos.flush();

            // 设置指定的最后修改时间
            boolean timeSet = file.setLastModified(modificationTime);
            if (!timeSet) {
                LogUtil.w(TAG, "设置文件修改时间失败: " + file.getPath());
            } else {
                LogUtil.d(TAG, "设置文件修改时间成功: " + file.getPath());
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "保存文件失败", e);
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                }
            }
        }
        return true;
    }

    /**
     *
     * @param fileFullPath 文件的完整路径
     * @param content
     * @param modificationTime
     * @return
     */
    public static boolean saveFileToPath(String fileFullPath, String content, long modificationTime) {
        File file = new File(fileFullPath);
        return saveFile(file, content, modificationTime);
    }

    /**
     * 保存文件到指定路径，并设置指定的最后修改时间
     *
     * @param fileParentPath   保存文件的父目录路径
     * @param fileName         文件名
     * @param content          文件内容
     * @param modificationTime 最后修改时间（毫秒时间戳）
     * @return 保存是否成功
     */
    public static boolean saveFileToPath(String fileParentPath, String fileName, String content, long modificationTime) {

        File file = new File(fileParentPath, fileName);
        return saveFile(file, content, modificationTime);
    }


    public static List<Card> convertTempDeckYdk(String deckYdk) {
        String[] deckLine = deckYdk.split("\r\n|\r|\n");

        List<Integer> tempDeck = new ArrayList<>();//存储当前卡组的基本内容
        List<Card> cardList = new ArrayList<>();

        CardManager mCardManager = DataManager.get().getCardManager();
        for (String line : deckLine) {
            if (!line.contains("#") && !line.contains("!")) {
                //line.to
                try {
                    Integer cardId = Integer.parseInt(line);
                    tempDeck.add(cardId);
                    Card card = mCardManager.getCard(cardId);
                    cardList.add(card);
                } catch (NumberFormatException e) {
                    LogUtil.i(TAG, "cannot parse Interget" + line + e.getMessage());
                }

            }
        }
        return cardList;
    }


    public static long convertToUnixTimestamp(String DateTime) {
        try {
            //DateTime 格式为 ""yyyy-MM-dd'T'HH:mm:ss""
            DateTimeFormatter formatter = null;
            // 解析为本地时间，再关联到 UTC+8 时区
            LocalDateTime localDateTime = null;
            ZonedDateTime zonedDateTime = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
                localDateTime = LocalDateTime.parse(DateTime, formatter);
                zonedDateTime = localDateTime.atZone(ZoneId.of("Asia/Shanghai"));
                return zonedDateTime.toInstant().toEpochMilli();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
        return 0;
    }

    /**
     * 将 Unix 时间戳转换为 GMT 格式的日期字符串
     * @param timestamp 时间戳（毫秒）
     * @return GMT 格式的日期字符串（例如：Thu, 04 Jul 2025 08:00:55 GMT）
     */
    public static String convertToGMTDate(long timestamp) {
        try {
            // 创建格式化器并设置时区为 GMT
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

            // 格式化时间戳
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
