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
     * 将file内容更新为List<DeckItem>中的内容
     * 删除file，之后将List<DeckItem>中的内容保存到file中
     * @param items
     * @param file 原有file
     * @return 如果file为null，返回false
     */
    public static boolean save(List<DeckItem> items, String deck_id, File file) {
        FileOutputStream outputStream = null;
        OutputStreamWriter writer = null;
        try {
            if (file == null) {
                return false;
            }
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            outputStream = new FileOutputStream(file);
            writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            writer.write("#created by ygomobile".toCharArray());
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
            if (deck_id != null)
                writer.write(("\n##" + deck_id).toCharArray());
            writer.flush();
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            IOUtils.close(writer);
            IOUtils.close(outputStream);
        }
        return true;
    }

    public static void makeItems(DeckInfo deckInfo, boolean isPack, DeckAdapater adapater) {
        if (deckInfo != null) {
            if (deckInfo.deckId != null)
                adapater.setId(deckInfo.deckId);
            DeckItem.resetLabel(deckInfo, isPack);
            adapater.addItem(new DeckItem(DeckItemType.MainLabel));
            List<Card> main = deckInfo.getMainCards();
            if (main == null) {
                for (int i = 0; i < Constants.DECK_MAIN_MAX; i++) {
                    adapater.addItem(new DeckItem());
                }
            } else {
                for (Card card : main) {
                    adapater.addItem(new DeckItem(card, DeckItemType.MainCard));
                }
                if (main.size() < Constants.DECK_MAIN_MAX) {
                    for (int i = main.size(); i < Constants.DECK_MAIN_MAX; i++) {
                        adapater.addItem(new DeckItem());
                    }
                } else {
                    //填充空舍的位置便于滚动到底部时不和底部功能按钮重叠
                    int emty = Constants.DECK_WIDTH_COUNT - deckInfo.getMainCount() % Constants.DECK_WIDTH_COUNT;
                    for (int i = main.size(); i < (isPack ? emty : 0) + deckInfo.getMainCount(); i++) {
                        adapater.addItem(new DeckItem());
                    }
                }
            }

            if (!isPack) {
                List<Card> extra = deckInfo.getExtraCards();
                List<Card> side = deckInfo.getSideCards();
                //extra
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
                //side
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
