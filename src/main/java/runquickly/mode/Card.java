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
        return containSize(cardList, containCard, true);
    }

    public static int containSize(List<Integer> cardList, Integer containCard, boolean sameColor) {
        int size = 0;
        for (Integer card : cardList) {
            if (card.intValue() == containCard || (!sameColor && card % 100 == containCard % 100)) {
                size++;
            }
        }
        return size;
    }

    public static boolean contain(List<Integer> cardList, Integer containCard) {
        for (Integer card : cardList) {
            if (card % 100 == containCard % 100) {
                return true;
            }
        }
        return false;
    }

    public static List<Integer> getAllCard() {
        return new ArrayList<>(Arrays.asList(
                2, 102, 202, 302,
                3, 103, 203, 303,
                4, 104, 204, 304,
                5, 105, 205, 305,
                6, 106, 206, 306,
                7, 107, 207, 307,
                8, 108, 208, 308,
                9, 109, 209, 309,
                10, 110, 210, 310,
                11, 111, 211, 311,
                12, 112, 212, 312,
                13, 113, 213, 313,
                14, 114, 214, 314));
    }

    public static CardType getCardType(List<Integer> cardList, boolean feijizha) {
        List<Integer> cards = new ArrayList<>();
        for (int integer : cardList) {
            cards.add(integer % 100);
        }
        cards.sort(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return (o1 % 100 > o2 % 100) ? 1 : -1;
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
                return CardType.ERROR;
            case 5:
                if (cards.get(0).intValue() == cards.get(3) || cards.get(1).intValue() == cards.get(4)) {
                    return CardType.SIZHANG;
                }
                if (cards.get(0).intValue() == cards.get(2) || cards.get(1).intValue() == cards.get(3) || cards.get(2).intValue() == cards.get(4)) {
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
                    return CardType.SANLIAN;
                }
                if (cards.get(0).intValue() == cards.get(1) && cards.get(0) == cards.get(2) - 1 && cards.get(0) == cards.get(3) - 1
                        && cards.get(0) == cards.get(4) - 2 && cards.get(0) == cards.get(5) - 2) {
                    return CardType.LIANDUI;
                }
                if (cards.get(0).intValue() == cards.get(3) || cards.get(1).intValue() == cards.get(4) || cards.get(2).intValue() == cards.get(5)) {
                    return CardType.SIZHANG;
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
            if (cards.size() - dui.size() == 0) {
                for (int i = 0; i < dui.size() - 2; i += 2) {
                    if (dui.get(i).intValue() != dui.get(i + 1) || dui.get(i) != dui.get(i + 2) - 1) {
                        liandui = false;
                        break;
                    }
                }
            } else {
                liandui = false;
            }
            if (liandui) {
                return CardType.LIANDUI;
            }

//            boolean isFeijiZha = true;
//            if (feijizha) {
//                List<Integer> si = get_si(cards);
//                if (cards.size() == si.size()) {
//                    for (int i = 0; i < si.size() - 4; i += 4) {
//                        if (si.get(i) != si.get(i + 4) - 1) {
//                            isFeijiZha = false;
//                        }
//                    }
//                } else {
//                    isFeijiZha = false;
//                }
//            }
//            if (isFeijiZha) {
//                return CardType.ZHADAN;
//            }

            boolean isFeiji = true;
            //飞机
            List<Integer> san = get_san(cards);
            if (4 < san.size()) {
                if (cards.size() == san.size()) {
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
                    return CardType.SANLIAN;
                }
                isFeiji = true;
                if (0 == cards.size() % 4) {
                    if (cards.size() / 4 < san.size() / 3) {
                        if (san.get(0) != san.get(3) - 1) {
                            Card.removeAll(san, san.subList(0, 3));
                        } else {
                            Card.removeAll(san, san.subList(san.size() - 3, san.size()));
                        }
                    } else if (cards.size() / 4 > san.size()) {
                        isFeiji = false;
                    }

                    for (int i = 0; i < san.size() - 3; i += 3) {
                        if (san.get(i) != san.get(i + 3) - 1) {
                            isFeiji = false;
                            break;
                        }
                    }
                } else if (0 == cards.size() % 5) {
                    if (cards.size() / 5 == san.size() / 3) {
                        for (int i = 0; i < san.size() - 3; i += 3) {
                            if (san.get(i) != san.get(i + 3) - 1) {
                                isFeiji = false;
                                break;
                            }
                        }
                    } else if (cards.size() / 5 > san.size()) {
                        isFeiji = false;
                    }
                } else {
                    isFeiji = false;
                }
                if (isFeiji) {
                    return CardType.FEIJI;
                }
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
                return (o1 % 100 > o2 % 100) ? 1 : -1;
            }
        });
        switch (cardType) {
            case DANPAI:
            case DUIPAI:
            case LIANDUI:
            case SHUNZI:
            case ZHADAN:
            case SANLIAN:
                return cards.get(0) % 100;
            case SANZHANG:
            case SIZHANG:
                return cards.get(2) % 100;
            case FEIJI:
                for (Integer integer : cardList) {
                    if (3 <= Card.containSize(cardList, integer, false) && 3 <= Card.containSize(cardList, integer + 1, false)) {
                        return integer % 100;
                    }
                }
        }
        return 0;
    }

    public static List<Integer> get_dui(List<Integer> cards) {
        List<Integer> dui_arr = new ArrayList<>();
        if (cards.size() >= 2) {
            for (int i = 0; i < cards.size() - 1; i++) {
                if (cards.get(i) % 100 == cards.get(i + 1) % 100) {
                    dui_arr.add(cards.get(i));
                    dui_arr.add(cards.get(i + 1));
                    i++;
                }
            }
        }
        return dui_arr;
    }

    public static List<Integer> get_san(List<Integer> cards) {
        List<Integer> san_arr = new ArrayList<>();
        if (cards.size() >= 3) {
            for (int i = 0; i < cards.size() - 2; i++) {
                if (cards.get(i) % 100 == cards.get(i + 2) % 100) {
                    san_arr.add(cards.get(i));
                    san_arr.add(cards.get(i + 1));
                    san_arr.add(cards.get(i + 2));
                    i += 2;
                }
            }
        }
        return san_arr;
    }

    public static List<Integer> get_si(List<Integer> cards) {
        List<Integer> san_arr = new ArrayList<>();
        if (cards.size() >= 4) {
            for (int i = 0; i < cards.size() - 3; i++) {
                if (cards.get(i) % 100 == cards.get(i + 3) % 100) {
                    san_arr.add(cards.get(i));
                    san_arr.add(cards.get(i + 1));
                    san_arr.add(cards.get(i + 2));
                    san_arr.add(cards.get(i + 3));
                    i += 3;
                }
            }
        }
        return san_arr;
    }

    public static void remove(List<Integer> cards, Integer card) {
        for (Integer card1 : cards) {
            if (card1.intValue() == card) {
                cards.remove(card1);
                return;
            }
        }
    }

    public static void removeAll(List<Integer> cards, List<Integer> removes) {
        for (Integer card : removes) {
            for (Integer card1 : cards) {
                if (card1.intValue() == card) {
                    cards.remove(card1);
                    break;
                }
            }
        }
    }
}
