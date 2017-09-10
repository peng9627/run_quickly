package runquickly.mode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
        return new ArrayList<>(Arrays.asList(2, 102, 202, 302, 3, 103, 203, 303, 4, 104, 204, 304, 5, 105, 205, 305, 6, 106, 206, 306, 7, 107, 207, 307, 8, 108, 208, 308, 9, 109, 209, 309, 10, 110, 210, 310, 11, 111, 211, 311, 12, 112, 212, 312, 13, 113, 213, 313, 14, 114, 214, 314));
    }

    public static CardType getCardType(List<Integer> cardList, boolean feijizha) {
        CardType cardType = getCardType(cardList, 1, feijizha);
        if (0 == CardType.ERROR.compareTo(cardType)) {
            cardType = getCardType(cardList, 2, feijizha);
        }
        return cardType;
    }

    public static CardType getCardType(List<Integer> cardList, int times, boolean feijizha) {
        List<Integer> cards = new ArrayList<>();
        for (int integer : cardList) {
            if (1 == times && 2 == integer % 100) {
                cards.add(15);
            } else if (2 == times && 14 == integer % 100) {
                cards.add(1);
            } else {
                cards.add(integer % 100);
            }
        }
        cards.sort(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return o1.compareTo(o2);
            }
        });
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
                    if (cards.get(i) != cards.get(i + 1) - 1) {
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
                    if (cards.get(i) != cards.get(i + 1) - 1) {
                        return CardType.ERROR;
                    }
                }
                return CardType.SHUNZI;
            case 6:
                if (cards.get(0).intValue() == cards.get(2) && cards.get(3).intValue() == cards.get(5) && cards.get(0) == cards.get(3) - 1) {
                    return CardType.FEIJI;
                }
                if (cards.get(0).intValue() == cards.get(1) && cards.get(0) == cards.get(2) - 1 && cards.get(0) == cards.get(3) - 1
                        && cards.get(0) == cards.get(4) - 2 && cards.get(0) == cards.get(5) - 2) {
                    return CardType.LIANDUI;
                }
                for (int i = 0; i < 5; i++) {
                    if (cards.get(i) != cards.get(i + 1) - 1) {
                        return CardType.ERROR;
                    }
                }
                return CardType.SHUNZI;
        }
        if (6 < cards.size()) {
            //顺子
            boolean shunzi = true;
            for (int i = 0; i < 5; i++) {
                if (cards.get(i) != cards.get(i + 1) - 1) {
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
                    if (dui.get(i).intValue() != dui.get(i + 1) || dui.get(i) != dui.get(i + 2) - 1) {
                        liandui = false;
                        break;
                    }
                }
            }
            if (liandui) {
                return CardType.LIANDUI;
            }

            boolean isFeiji = true;
            //飞机
            List<Integer> san = get_san(cards);
            if (cards.size() == san.size() || cards.size() - san.size() == 1) {
                for (int i = 0; i < san.size() - 3; i += 3) {
                    if (san.get(i) != san.get(i + 3) - 1) {
                        isFeiji = false;
                        break;
                    }
                }
            } else {
                isFeiji = false;
            }
            if (isFeiji) {
                return CardType.FEIJI;
            }

            if (feijizha) {
                List<Integer> si = get_si(cards);
                if (cards.size() == si.size()) {
                    for (int i = 0; i < si.size() - 4; i += 4) {
                        if (si.get(i) != si.get(i + 4) - 1) {
                            return CardType.ERROR;
                        }
                    }
                } else {
                    return CardType.ERROR;
                }
                return CardType.ZHADAN;
            }

        }
        return CardType.ERROR;
    }

    public static int getCardValue(List<Integer> cardList, CardType cardType) {
        List<Integer> cards = new ArrayList<>();
        for (int integer : cardList) {
            if (2 == integer % 100) {
                cards.add(15);
            } else {
                cards.add(integer % 100);
            }
        }
        cards.sort(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return o1.compareTo(o2);
            }
        });
        switch (cardType) {
            case DANPAI:
            case DUIPAI:
                return cards.get(0);
            case LIANDUI:
                if (2 == Card.containSize(cardList, 3) && 2 == Card.containSize(cardList, 15)) {
                    if (2 == Card.containSize(cardList, 14)) {
                        return 1;
                    } else {
                        return 2;
                    }
                }
                return cards.get(1);
            case SHUNZI:
            case ZHADAN:
                if (cards.contains(3) && cards.contains(2)) {
                    if (cards.contains(14)) {
                        return 1;
                    } else {
                        return 2;
                    }
                }
                return cards.get(0);
            case SANZHANG:
                return cards.get(2);
            case FEIJI:
                if (3 == Card.containSize(cardList, 3) && 3 == Card.containSize(cardList, 15)) {
                    if (3 == Card.containSize(cardList, 14)) {
                        return 1;
                    } else {
                        return 2;
                    }
                }
                for (Integer integer : cardList) {
                    if (3 == Card.containSize(cardList, integer)) {
                        return integer;
                    }
                }
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

    private static List<Integer> get_si(List<Integer> cards) {
        List<Integer> san_arr = new ArrayList<>();
        if (cards.size() >= 4) {
            for (int i = 0; i < cards.size() - 3; i++) {
                if (cards.get(i).intValue() == cards.get(i + 3).intValue()) {
                    san_arr.add(cards.get(i));
                    san_arr.add(cards.get(i));
                    san_arr.add(cards.get(i));
                    san_arr.add(cards.get(i));
                    i += 3;
                }
            }
        }
        return san_arr;
    }
}
