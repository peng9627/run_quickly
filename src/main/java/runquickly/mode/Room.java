package runquickly.mode;


import java.util.ArrayList;
import java.util.List;

/**
 * Author pengyi
 * Date 17-3-7.
 */
public class Room {

    private Double baseScore; //基础分
    private String roomNo;  //桌号
    private List<Seat> seats;//座位
    private int operationSeat;
    private List<OperationHistory> historyList;
    private GameStatus gameStatus;
    private List<GameResult> gameResults;

    private String lastOperation;

    private int gameTimes; //游戏局数
    private int count;//人数

    public Room(Double baseScore, String roomNo, int gameTimes, int count) {
        this.baseScore = baseScore;
        this.roomNo = roomNo;
        this.gameTimes = gameTimes;
        this.count = count;
        this.gameStatus = GameStatus.WAITING;
    }

    public Double getBaseScore() {
        return baseScore;
    }

    public void setBaseScore(Double baseScore) {
        this.baseScore = baseScore;
    }

    public String getRoomNo() {
        return roomNo;
    }

    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public void setSeats(List<Seat> seats) {
        this.seats = seats;
    }

    public int getOperationSeat() {
        return operationSeat;
    }

    public void setOperationSeat(int operationSeat) {
        this.operationSeat = operationSeat;
    }

    public List<OperationHistory> getHistoryList() {
        return historyList;
    }

    public void setHistoryList(List<OperationHistory> historyList) {
        this.historyList = historyList;
    }

    public GameStatus getGameStatus() {
        return gameStatus;
    }

    public void setGameStatus(GameStatus gameStatus) {
        this.gameStatus = gameStatus;
    }

    public List<GameResult> getGameResults() {
        return gameResults;
    }

    public void setGameResults(List<GameResult> gameResults) {
        this.gameResults = gameResults;
    }

    public String getLastOperation() {
        return lastOperation;
    }

    public void setLastOperation(String lastOperation) {
        this.lastOperation = lastOperation;
    }

    public int getGameTimes() {
        return gameTimes;
    }

    public void setGameTimes(int gameTimes) {
        this.gameTimes = gameTimes;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void addSeat(User user) {
        Seat seat = new Seat();
        seat.setRobot(false);
        seat.setReady(false);
        seat.setAreaString("");
        seat.setGold(0);
        seat.setScore(0);
        seat.setSeatNo(seats.size() + 1);
        seat.setUserName(user.getUsername());
        seats.add(seat);
    }

    public void dealCard() {
        int min = Card.getAllCard().size();
        if (operationSeat == 0) {
            List<Integer> surplusCards = Card.getAllCard();
            for (Seat seat : seats) {
                List<Integer> cardList = new ArrayList<>();
                for (int i = 0; i < 13; i++) {
                    int cardIndex = (int) (Math.random() * surplusCards.size());
                    if (cardIndex < min) {
                        min = cardIndex;
                        operationSeat = seat.getSeatNo();
                    }
                    cardList.add(surplusCards.get(cardIndex));
                    surplusCards.remove(cardIndex);
                }
                seat.setCards(cardList);
            }
            operationSeat = 1;
        }
    }

    public int getNextSeat() {
        int next = operationSeat;
        if (count == next) {
            next = 1;
        } else {
            next += 1;
        }
        return next;
    }
}
