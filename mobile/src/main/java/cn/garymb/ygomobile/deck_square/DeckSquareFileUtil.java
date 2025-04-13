package cn.garymb.ygomobile.deck_square;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.LogUtil;

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
    public static String getId(File file) {
        String deckId = null;
        Integer userId;
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);

            InputStreamReader in = null;
            in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(in);

            String line = null;
            while ((line = reader.readLine()) != null) {
                //  LogUtil.i(TAG, line);
                if (line.startsWith("###")) {//注意，先判断###，后判断##。因为###会包括##的情况
                    line = line.replace("#", "");
                    userId = Integer.parseInt(line);
                    // userId = Integer.parseInt(line.replaceAll("###", ""));

                } else if (line.startsWith("##")) {
                    line = line.replace("#", "");
                    deckId = line;

                }
            }

        } catch (Exception e) {
            Log.e(TAG, "read 1", e);
        } finally {
            IOUtils.close(inputStream);
        }
        return deckId;
    }

    public static File[] getAllYdk() {
        File dir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_DECK_PATH);
        File[] files = dir.listFiles((file, s) -> s.toLowerCase(Locale.US).endsWith(Constants.YDK_FILE_EX));

        return files;
    }

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

    //将卡组id、用户id设置到卡组文件上
    //下载卡组后，保存之前将其原有id清除
    //上传卡组前，填入新的卡组id、用户id
    public static String setDeckId(String deckPath, Integer userId, String deckId) {
        StringBuilder contentBuilder = new StringBuilder();

        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(deckPath);

            InputStreamReader in = null;
            in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(in);

            String line = null;
            while ((line = reader.readLine()) != null) {
                contentBuilder.append(line);
            }
            String content = contentBuilder.toString();

        } catch (Exception e) {
            LogUtil.e(TAG, "read 1", e);
        } finally {
            IOUtils.close(inputStream);
        }

        String content = contentBuilder.toString();


        //先替换XXX用户id
        //后替换XX卡组id
        String original = "这是##测试1\r\n的内容，还有##测试2\r\n等其他部分";
        String modified = original.replaceAll("##(.*?)\r\n", "##替换后的内容\r\n");

        System.out.println("修改前: " + original);
        System.out.println("修改后: " + modified);

        return content;
    }
}
