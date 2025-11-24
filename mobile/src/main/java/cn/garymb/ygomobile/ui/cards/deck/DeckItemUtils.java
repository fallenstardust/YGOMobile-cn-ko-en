package cn.garymb.ygomobile.ui.cards.deck;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.MD5Util;
import ocgcore.data.Card;

class DeckItemUtils {

    public static String makeMd5(List<DeckItem> items) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("#main");
        for (int i = DeckItem.MainStart; i < DeckItem.MainStart + items.size(); i++) {
            DeckItem deckItem = items.get(i);
            if (deckItem.getType() == DeckItemType.Space) {
                break;
            }
            Card cardInfo = deckItem.getCardInfo();
            if (cardInfo != null) {
                stringBuilder.append("\n");
//                if(!cardInfo.isExtraCard()) {
                stringBuilder.append(cardInfo.Code);
//                }
            }
        }
        stringBuilder.append("\n#extra");
        for (int i = DeckItem.ExtraStart; i < DeckItem.ExtraStart + Constants.DECK_EXTRA_MAX; i++) {
            DeckItem deckItem = items.get(i);
            if (deckItem.getType() == DeckItemType.Space) {
                break;
            }
            Card cardInfo = deckItem.getCardInfo();
            if (cardInfo != null) {
                stringBuilder.append("\n");
//                if(cardInfo.isExtraCard()) {
                stringBuilder.append(cardInfo.Code);
//                }
            }
        }
        stringBuilder.append("\n!side");
        for (int i = DeckItem.SideStart; i < DeckItem.SideStart + Constants.DECK_SIDE_MAX; i++) {
            DeckItem deckItem = items.get(i);
            if (deckItem.getType() == DeckItemType.Space) {
                break;
            }
            Card cardInfo = deckItem.getCardInfo();
            if (cardInfo != null) {
                stringBuilder.append("\n");
                stringBuilder.append(cardInfo.Code);
            }
        }
        return MD5Util.getStringMD5(stringBuilder.toString());
    }

    public static Deck toDeck(List<DeckItem> items, File file) {
        Deck deck;
        if (file == null) {
            deck = new Deck();
        } else {
            deck = new Deck(file.getName());
        }
        try {
            for (int i = DeckItem.MainStart; i < DeckItem.MainStart + Constants.DECK_MAIN_MAX; i++) {
                DeckItem deckItem = items.get(i);
                if (deckItem.getType() == DeckItemType.Space) {
                    break;
                }
                Card cardInfo = deckItem.getCardInfo();
                if (cardInfo != null) {
//                    if(!cardInfo.isExtraCard()) {
                    deck.addMain(cardInfo.Code);
//                    }
                }
            }
            for (int i = DeckItem.ExtraStart; i < DeckItem.ExtraStart + Constants.DECK_EXTRA_MAX; i++) {
                DeckItem deckItem = items.get(i);
                if (deckItem.getType() == DeckItemType.Space) {
                    break;
                }
                Card cardInfo = deckItem.getCardInfo();
                if (cardInfo != null) {
//                    if(cardInfo.isExtraCard()) {
                    deck.addExtra(cardInfo.Code);
//                    }
                }
            }
            for (int i = DeckItem.SideStart; i < DeckItem.SideStart + Constants.DECK_SIDE_MAX; i++) {
                DeckItem deckItem = items.get(i);
                if (deckItem.getType() == DeckItemType.Space) {
                    break;
                }
                Card cardInfo = deckItem.getCardInfo();
                if (cardInfo != null) {
                    deck.addSide(cardInfo.Code);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return deck;
    }

    /**
     * 将指定文件内容更新为List<DeckItem>中的卡组数据。
     * 操作流程：删除原文件，创建新文件，并将卡组信息按格式写入文件。
     * 文件结构包括主卡组、额外卡组、副卡组以及可选的deckId和userId标识。
     *
     * @param items  包含卡组信息的列表，其中包含主卡组、额外卡组和副卡组的数据
     * @param deckId 可选参数，表示卡组ID，若非空则写入文件末尾（以 ## 开头）
     * @param userId 可选参数，表示用户ID，若非空则写入文件末尾（以 ### 开头）
     * @param file   要被覆盖写入的文件对象，如果为null则直接返回false
     * @return 操作成功返回true；如果file为null或发生IO异常则返回false
     */
    public static boolean save(List<DeckItem> items, String deckId, Integer userId, File file) {
        FileOutputStream outputStream = null;
        OutputStreamWriter writer = null;
        try {
            // 参数校验：如果文件为空，直接返回失败
            if (file == null) {
                return false;
            }

            // 若文件已存在，则先删除旧文件再重新创建
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();

            // 初始化输出流并设置编码为UTF-8
            outputStream = new FileOutputStream(file);
            writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);

            // 写入文件头部标识
            writer.write("#created by ygomobile".toCharArray());

            // 写入主卡组部分
            writer.write("\n#main".toCharArray());
            for (int i = DeckItem.MainStart; i < DeckItem.MainStart + Constants.DECK_MAIN_MAX; i++) {
                DeckItem deckItem = items.get(i);
                if (deckItem.getType() == DeckItemType.Space) {
                    break;
                }
                Card cardInfo = deckItem.getCardInfo();
                if (cardInfo != null) {
                    writer.write(("\n" + cardInfo.Code).toCharArray());
                }
            }

            // 写入额外卡组部分
            writer.write("\n#extra".toCharArray());
            for (int i = DeckItem.ExtraStart; i < DeckItem.ExtraStart + Constants.DECK_EXTRA_MAX; i++) {
                DeckItem deckItem = items.get(i);
                if (deckItem.getType() == DeckItemType.Space) {
                    break;
                }
                Card cardInfo = deckItem.getCardInfo();
                if (cardInfo != null) {
                    writer.write(("\n" + cardInfo.Code).toCharArray());
                }
            }

            // 写入副卡组部分
            writer.write("\n!side".toCharArray());
            for (int i = DeckItem.SideStart; i < DeckItem.SideStart + Constants.DECK_SIDE_MAX; i++) {
                DeckItem deckItem = items.get(i);
                if (deckItem.getType() == DeckItemType.Space) {
                    break;
                }
                Card cardInfo = deckItem.getCardInfo();
                if (cardInfo != null) {
                    writer.write(("\n" + cardInfo.Code).toCharArray());
                }
            }

            // 如果提供了deckId和userId，追加到文件末尾
            if (deckId != null)
                writer.write(("\n##" + deckId).toCharArray());
            if (userId != null)
                writer.write(("\n###" + userId).toCharArray());

            // 刷新缓冲区确保所有数据写入磁盘
            writer.flush();
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            // 关闭资源，避免内存泄漏
            IOUtils.close(writer);
            IOUtils.close(outputStream);
        }
        return true;
    }


    /**
     * 根据传入的卡组信息构建并添加卡组项到适配器中。
     *
     * @param deckInfo 卡组信息对象，包含主卡组、额外卡组和副卡组等数据
     * @param isPack 是否为卡包模式，影响布局和显示方式
     * @param adapater 用于接收和管理卡组项的适配器对象
     */
    public static void makeItems(DeckInfo deckInfo, boolean isPack, DeckAdapater adapater) {
        if (deckInfo != null) {
            // 设置卡组ID和用户ID（如果存在）
            if (deckInfo.deckId != null)
                adapater.setDeckId(deckInfo.deckId);
            if (deckInfo.userId != null)
                adapater.setUserId(deckInfo.userId);

            // 重置标签状态
            DeckItem.resetLabel(deckInfo, isPack);

            // 添加主卡区域标签
            adapater.addItem(new DeckItem(DeckItemType.MainLabel));

            List<Card> main = deckInfo.getMainCards();

            // 构建主卡区域内容
            if (main == null) {
                // 主卡为空时，填充默认数量的空位
                for (int i = 0; i < Constants.DECK_MAIN_MAX; i++) {
                    adapater.addItem(new DeckItem());
                }
            } else {
                // 添加实际主卡项
                for (Card card : main) {
                    adapater.addItem(new DeckItem(card, DeckItemType.MainCard));
                }

                // 补足剩余空位或根据需要填充空白以避免UI重叠
                if (main.size() < Constants.DECK_MAIN_MAX) {
                    for (int i = main.size(); i < Constants.DECK_MAIN_MAX; i++) {
                        adapater.addItem(new DeckItem());
                    }
                } else {
                    // 填充空舍的位置便于滚动到底部时不和底部功能按钮重叠
                    int emty = Constants.DECK_WIDTH_COUNT - deckInfo.getMainCount() % Constants.DECK_WIDTH_COUNT;
                    for (int i = main.size(); i < (isPack ? emty : 0) + deckInfo.getMainCount(); i++) {
                        adapater.addItem(new DeckItem());
                    }
                }
            }

            // 非卡包模式下处理额外卡组与副卡组
            if (!isPack) {
                List<Card> extra = deckInfo.getExtraCards();
                List<Card> side = deckInfo.getSideCards();

                // 处理额外卡组
                adapater.addItem(new DeckItem(DeckItemType.ExtraLabel));
                if (extra == null) {
                    for (int i = 0; i < Constants.DECK_EXTRA_COUNT; i++) {
                        adapater.addItem(new DeckItem());
                    }
                } else {
                    for (Card card : extra) {
                        adapater.addItem(new DeckItem(card, DeckItemType.ExtraCard));
                    }
                    for (int i = extra.size(); i < Constants.DECK_EXTRA_COUNT; i++) {
                        adapater.addItem(new DeckItem());
                    }
                }

                // 处理副卡组
                adapater.addItem(new DeckItem(DeckItemType.SideLabel));
                if (side == null) {
                    for (int i = 0; i < Constants.DECK_SIDE_COUNT; i++) {
                        adapater.addItem(new DeckItem());
                    }
                } else {
                    for (Card card : side) {
                        adapater.addItem(new DeckItem(card, DeckItemType.SideCard));
                    }
                    for (int i = side.size(); i < Constants.DECK_SIDE_COUNT; i++) {
                        adapater.addItem(new DeckItem());
                    }
                }

                // 添加一个占位空间项
                adapater.addItem(new DeckItem(DeckItemType.Space));
            }
        }
    }

    public static boolean isMain(int pos) {
        return pos >= DeckItem.MainStart && pos <= DeckItem.MainEnd;
    }

    public static boolean isExtra(int pos) {
        return pos >= DeckItem.ExtraStart && pos <= DeckItem.ExtraEnd;
    }

    public static boolean isSide(int pos) {
        return pos >= DeckItem.SideStart && pos <= DeckItem.SideEnd;
    }

    public static boolean isLabel(int position) {
        return position == DeckItem.MainLabel || position == DeckItem.ExtraLabel || position == DeckItem.SideLabel;
    }
}
