package runquickly.mode;

import java.util.List;

/**
 * Created by pengyi
 * Date 2017/7/28.
 */
public class OperationHistory {

    private String userName;
    private OperationHistoryType historyType;
    private List<Integer> cards;

    public OperationHistory() {
    }

    public OperationHistory(String userName, OperationHistoryType historyType, List<Integer> cards) {
        this.userName = userName;
        this.historyType = historyType;
        this.cards = cards;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public OperationHistoryType getHistoryType() {
        return historyType;
    }

    public void setHistoryType(OperationHistoryType historyType) {
        this.historyType = historyType;
    }

    public List<Integer> getCards() {
        return cards;
    }

    public void setCards(List<Integer> cards) {
        this.cards = cards;
    }
}
