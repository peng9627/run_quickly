package runquickly.mode;


import com.alibaba.fastjson.JSON;
import runquickly.entrance.RunQuicklyTcpService;
import runquickly.redis.RedisService;

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

    private int lastOperation;

    private int gameTimes; //游戏局数
    private int count;//人数
    private int multiple;
    private int gameCount;
    private List<Record> recordList;//战绩

    public Room(Double baseScore, String roomNo, int gameTimes, int count) {
        this.baseScore = baseScore;
        this.roomNo = roomNo;
        this.gameTimes = gameTimes;
        this.count = count;
        this.gameStatus = GameStatus.WAITING;
        this.multiple = 1;
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

    public int getLastOperation() {
        return lastOperation;
    }

    public void setLastOperation(int lastOperation) {
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

    public int getMultiple() {
        return multiple;
    }

    public void setMultiple(int multiple) {
        this.multiple = multiple;
    }

    public int getGameCount() {
        return gameCount;
    }

    public void setGameCount(int gameCount) {
        this.gameCount = gameCount;
    }

    public List<Record> getRecordList() {
        return recordList;
    }

    public void setRecordList(List<Record> recordList) {
        this.recordList = recordList;
    }

    public void addSeat(User user) {
        Seat seat = new Seat();
        seat.setRobot(false);
        seat.setReady(false);
        seat.setAreaString("");
        seat.setScore(0);
        seat.setSeatNo(seats.size() + 1);
        seat.setUserId(user.getId());
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
                seat.setInitialCards(cardList);
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


    public void gameOver(GameBase.BaseConnection.Builder response, RedisService redisService) {

        Record record = new Record();
        record.setMultiple(multiple);
        List<SeatRecord> seatRecords = new ArrayList<>();
        record.setHistoryList(historyList);


        //TODO 计算比分
        RunQuickly.ResultResponse.Builder resultResponse = RunQuickly.ResultResponse.newBuilder();
        int winScore = 0;
        Seat winSeat = null;

        for (Seat seat : seats) {
            if (seat.getCards().size() > 0) {
                int score = 0;
                int cardSize = seat.getCards().size();
                if (cardSize == 13) {
                    score = 30;
                } else if (cardSize > 9) {
                    score = cardSize * 2;
                }
                score *= multiple;
                winScore += score;
                RunQuickly.Result result = RunQuickly.Result.newBuilder().setID(seat.getUserId())
                        .addAllCards(seat.getCards()).setScore(0 - score).build();
                resultResponse.addResult(result);

                SeatRecord seatRecord = new SeatRecord();
                seatRecord.setUserId(seat.getUserId());
                seatRecord.setInitialCards(seat.getInitialCards());
                seatRecord.setCards(seat.getCards());
                seatRecord.setWinOrLoce(0 - score);
                seatRecords.add(seatRecord);

                seat.setScore(seat.getScore() - score);
            } else {
                winSeat = seat;
            }
        }
        if (null == winSeat) {
            return;
        }
        RunQuickly.Result result = RunQuickly.Result.newBuilder().setID(winSeat.getUserId()).setScore(winScore).build();
        resultResponse.addResult(result);
        winSeat.setScore(winSeat.getScore() + winScore);

        SeatRecord seatRecord = new SeatRecord();
        seatRecord.setUserId(winSeat.getUserId());
        seatRecord.setInitialCards(winSeat.getInitialCards());
        seatRecord.setCards(winSeat.getCards());
        seatRecord.setWinOrLoce(winScore);
        seatRecords.add(seatRecord);

        record.setSeatRecordList(seatRecords);
        recordList.add(record);

        response.setOperationType(GameBase.OperationType.RESULT).setData(resultResponse.build().toByteString());
        seats.stream().filter(seat -> RunQuicklyTcpService.userClients.containsKey(seat.getUserId()))
                .forEach(seat -> RunQuicklyTcpService.userClients.get(seat.getUserId()).send(response.build(), seat.getUserId()));

        clear();

        //结束房间
        if (gameCount == gameTimes) {

//            Xingning.OverResponse.Builder over = Xingning.OverResponse.newBuilder();
//
//            for (Seat seat : seats) {
//                //TODO 统计
//                Xingning.SeatGameOver.Builder seatGameOver = Xingning.SeatGameOver.newBuilder()
//                        .setID(seat.getUserId()).setMinggang(seat.getMinggang()).setAngang(seat.getAngang())
//                        .setZimoCount(seat.getZimoCount()).setHuCount(seat.getHuCount()).setDianpaoCount(seat.getDianpaoCount());
//                over.addGameOver(seatGameOver);
//            }
//
//            response.setOperationType(GameBase.OperationType.OVER).setData(over.build().toByteString());
//            seats.stream().filter(seat -> MahjongTcpService.userClients.containsKey(seat.getUserId()))
//                    .forEach(seat -> MahjongTcpService.userClients.get(seat.getUserId()).send(response.build(), seat.getUserId()));

            //删除该桌
            redisService.delete("room" + roomNo);
            redisService.lock("lock_room_nos" + roomNo);
            List<String> roomNos = JSON.parseArray(redisService.getCache("room_nos"), String.class);
            roomNos.remove(roomNo);
            redisService.addCache("room_nos", JSON.toJSONString(roomNos), 86400);
            redisService.unlock("lock_room_nos" + roomNo);
        }
    }

    private void clear() {

        historyList.clear();
        gameStatus = GameStatus.READYING;
        lastOperation = 0;
        multiple = 0;
        seats.forEach(Seat::clear);
    }
}
