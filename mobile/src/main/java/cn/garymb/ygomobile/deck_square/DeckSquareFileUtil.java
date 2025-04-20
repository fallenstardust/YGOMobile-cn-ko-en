package cn.garymb.ygomobile.deck_square;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.LogUtil;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.data.Card;

public class DeckSquareFileUtil {
    //
    private static final String TAG = DeckSquareListAdapter.class.getSimpleName();

    //    public static List<String> readLastLinesWithNIO(File file, int numLines) {
//        try {
//            List<String> lines = Files.readAllLines(file);
//            int fromIndex = Math.max(0, lines.size() - numLines);
//            return lines.subList(fromIndex, lines.size());
//        } catch (IOException e) {
//            e.printStackTrace();
//            return Collections.emptyList();
//        }
//    }
//
    // 使用示例
//    Path logPath = Paths.get(context.getFilesDir().getAbsolutePath(), "log.txt");
//    List<String> lastTwo = readLastLinesWithNIO(logPath, 2);

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
                LogUtil.i(TAG, line);
                if (line.startsWith("###")) {//注意，先判断###，后判断##。因为###会包括##的情况
                    try {
                        String data = line.replace("#", "");
                        if (!data.isEmpty()) {
                            userId = Integer.parseInt(data);
                        }
                        // userId = Integer.parseInt(line.replaceAll("###", ""));
                    } catch (NumberFormatException e) {
                        LogUtil.e(TAG, "integer" + line + "parse error" + e.toString());
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

    //查询卡组目录下的所有ydk文件，返回File[]
    public static File[] getAllYdk() {
        File dir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_DECK_PATH);
        File[] files = dir.listFiles((file, s) -> s.toLowerCase(Locale.US).endsWith(Constants.YDK_FILE_EX));

        return files;
    }

    //读取卡组目录下的所有ydk文件，解析ydk文件，生成List<MyDeckItem>解析结果
    public static List<MyDeckItem> getMyDeckItem() {
        List<MyDeckItem> result = new ArrayList<>();
        File[] files = getAllYdk();
        for (File file : files) {
            String deckId = getId(file);
            MyDeckItem item = new MyDeckItem();
            item.deckName = file.getName();
            item.setDeckSouce(0);
            item.setDeckPath(file.getPath());
            if (deckId != null) {
                item.deckId = deckId;
                item.idUploaded = 2;
            } else {
                item.idUploaded = 0;
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

    public static boolean saveFileToPath(String path, String fileName, String content) {
        try {
            // Create file object
            File file = new File(path, fileName);

            // Create file output stream
            FileOutputStream fos = new FileOutputStream(file);

            // Write content
            fos.write(content.getBytes());
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
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
}
