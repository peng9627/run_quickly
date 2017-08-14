package runquickly.mode;

import java.util.List;

public class SeatRecord {
    private int userId;                         //用户名
    private List<Integer> initialCards;         //初始牌
    private List<Integer> cards;                //牌
    private int winOrLoce;                      //输赢分数

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public List<Integer> getInitialCards() {
        return initialCards;
    }

    public void setInitialCards(List<Integer> initialCards) {
        this.initialCards = initialCards;
    }

    public List<Integer> getCards() {
        return cards;
    }

    public void setCards(List<Integer> cards) {
        this.cards = cards;
    }

    public int getWinOrLoce() {
        return winOrLoce;
    }

    public void setWinOrLoce(int winOrLoce) {
        this.winOrLoce = winOrLoce;
    }
}
