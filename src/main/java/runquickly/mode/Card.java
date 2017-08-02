package runquickly.mode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by pengyi
 * Date : 16-6-12.
 */
public class Card {
    public static int containSize(List<Integer> cardList, Integer containCard) {
        int size = 0;
        for (Integer card : cardList) {
            if (card.intValue() == containCard) {
                size++;
            }
        }
        return size;
    }

    public static List<Integer> getAllCard() {
        return Arrays.asList(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115,
                203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215,
                303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 315);
    }

    public static CardType getCardType(List<Integer> cardList) {
        List<Integer> cards = new ArrayList<>();
        for (Integer integer : cardList) {
            cards.add(integer % 100);
        }
        cards.sort(Integer::compareTo);
        switch (cards.size()) {
            case 1:
                return CardType.DANPAI;
            case 2:
                if (cards.get(0).intValue() == cards.get(1)) {
                    return CardType.DUIPAI;
                }
                break;
            case 3:
                if (cards.get(0).intValue() == cards.get(2)) {
                    return CardType.SANZHANG;
                }
                break;
            case 4:
                if (cards.get(0).intValue() == cards.get(3)) {
                    return CardType.ZHADAN;
                }
                if (cards.get(0).intValue() == cards.get(1) && cards.get(0) == cards.get(2) - 1 && cards.get(0) == cards.get(3) - 1) {
                    return CardType.LIANDUI;
                }
                if (cards.get(0).intValue() == cards.get(2) || cards.get(1).intValue() == cards.get(3)) {
                    return CardType.SANZHANG;
                }
                for (int i = 0; i < 3; i++) {
                    if (cards.get(i).intValue() != cards.get(i + 1)) {
                        return CardType.ERROR;
                    }
                }
                return CardType.SHUNZI;
            case 5:
                if ((cards.get(0).intValue() == cards.get(2) && cards.get(3).intValue() == cards.get(4))
                        || cards.get(0).intValue() == cards.get(1) && cards.get(2).intValue() == cards.get(4)) {
                    return CardType.SANZHANG;
                }
                for (int i = 0; i < 4; i++) {
                    if (cards.get(i).intValue() != cards.get(i + 1)) {
                        return CardType.ERROR;
                    }
                }
                return CardType.SHUNZI;
            case 6:
                if (cards.get(0).intValue() == cards.get(2) && cards.get(3).intValue() == cards.get(5)) {
                    return CardType.FEIJI;
                }
                if (cards.get(0).intValue() == cards.get(3) || cards.get(1).intValue() == cards.get(4) || cards.get(2).intValue() == cards.get(5)) {
                    return CardType.FEIJI;
                }
                if (cards.get(0).intValue() == cards.get(1) && cards.get(0) == cards.get(2) - 1 && cards.get(0) == cards.get(3) - 1
                        && cards.get(0) == cards.get(4) - 2 && cards.get(0) == cards.get(5) - 2) {
                    return CardType.LIANDUI;
                }
                for (int i = 0; i < 5; i++) {
                    if (cards.get(i).intValue() != cards.get(i + 1)) {
                        return CardType.ERROR;
                    }
                }
                return CardType.SHUNZI;
        }
        if (6 < cards.size()) {
            //顺子
            boolean shunzi = true;
            for (int i = 0; i < 5; i++) {
                if (cards.get(i).intValue() != cards.get(i + 1)) {
                    shunzi = false;
                    break;
                }
            }
            if (shunzi) {
                return CardType.SHUNZI;
            }

            //连对
            boolean liandui = true;
            List<Integer> dui = get_dui(cards);
            if (cards.size() - dui.size() < 2) {
                for (int i = 0; i < dui.size() - 2; i += 2) {
                    if (dui.get(i).intValue() == dui.get(i + 1) && dui.get(i) == dui.get(i + 2) - 1) {
                        liandui = false;
                        break;
                    }
                }
            }
            if (liandui) {
                return CardType.LIANDUI;
            }

            //飞机
            List<Integer> san = get_san(cards);
            if (cards.size() - san.size() == 0 || cards.size() - san.size() == san.size() / 3) {
                for (int i = 0; i < san.size() - 3; i += 2) {
                    if (san.get(i).intValue() == san.get(i + 1) && san.get(i) == san.get(i + 2) - 1) {
                        return CardType.ERROR;
                    }
                }
            }
            return CardType.FEIJI;

        }
        return CardType.ERROR;
    }

    public static int getCardValue(List<Integer> cardList, CardType cardType) {
        List<Integer> cards = new ArrayList<>();
        for (Integer integer : cardList) {
            cards.add(integer % 100);
        }
        cards.sort(Integer::compareTo);
        switch (cardType) {
            case DANPAI:
            case DUIPAI:
            case LIANDUI:
            case SHUNZI:
            case ZHADAN:
                return cards.get(0);
            case SANZHANG:
                return cards.get(2);
            case FEIJI:
                return get_san(cards).get(0);
            case SIDAIER:
                return cards.get(3);
        }
        return 0;
    }

    private static List<Integer> get_dui(List<Integer> cards) {
        List<Integer> dui_arr = new ArrayList<>();
        if (cards.size() >= 2) {
            for (int i = 0; i < cards.size() - 1; i++) {
                if (cards.get(i).intValue() == cards.get(i + 1).intValue()) {
                    dui_arr.add(cards.get(i));
                    dui_arr.add(cards.get(i));
                    i++;
                }
            }
        }
        return dui_arr;
    }

    private static List<Integer> get_san(List<Integer> cards) {
        List<Integer> san_arr = new ArrayList<>();
        if (cards.size() >= 3) {
            for (int i = 0; i < cards.size() - 2; i++) {
                if (cards.get(i).intValue() == cards.get(i + 2).intValue()) {
                    san_arr.add(cards.get(i));
                    san_arr.add(cards.get(i));
                    san_arr.add(cards.get(i));
                    i += 2;
                }
            }
        }
        return san_arr;
    }
}
