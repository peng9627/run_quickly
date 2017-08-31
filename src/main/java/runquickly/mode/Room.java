package runquickly.mode;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.LoggerFactory;
import runquickly.entrance.RunQuicklyTcpService;
import runquickly.redis.RedisService;
import runquickly.utils.HttpUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Author pengyi
 * Date 17-3-7.
 */
public class Room {

    private int baseScore; //基础分
    private String roomNo;  //桌号
    private int roomOwner;
    private List<Seat> seats = new ArrayList<>();//座位
    private int operationSeat;
    private List<OperationHistory> historyList = new ArrayList<>();
    private GameStatus gameStatus;
    private List<Integer> seatNos;

    private int lastOperation;

    private int gameTimes; //游戏局数
    private int count;//人数
    private int multiple;
    private int gameCount;
    private List<Record> recordList = new ArrayList<>();//战绩
    private int gameRules;

    public int getBaseScore() {
        return baseScore;
    }

    public void setBaseScore(int baseScore) {
        this.baseScore = baseScore;
    }

    public String getRoomNo() {
        return roomNo;
    }

    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
    }

    public int getRoomOwner() {
        return roomOwner;
    }

    public void setRoomOwner(int roomOwner) {
        this.roomOwner = roomOwner;
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

    public List<Integer> getSeatNos() {
        return seatNos;
    }

    public void setSeatNos(List<Integer> seatNos) {
        this.seatNos = seatNos;
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

    public int getGameRules() {
        return gameRules;
    }

    public void setGameRules(int gameRules) {
        this.gameRules = gameRules;
    }

    public void addSeat(User user) {
        Seat seat = new Seat();
        seat.setRobot(false);
        seat.setReady(false);
        seat.setAreaString(user.getArea());
        seat.setHead(user.getHead());
        seat.setNickname(user.getNickname());
        seat.setSex(user.getSex().equals("MAN"));
        seat.setScore(0);
        seat.setSeatNo(seatNos.get(0));
        seatNos.remove(0);
        seat.setUserId(user.getUserId());
        seats.add(seat);
    }

    public void dealCard() {
        int min = Card.getAllCard().size();
        if (operationSeat == 0) {
            List<Integer> surplusCards = Card.getAllCard();
            for (Seat seat : seats) {
                seat.setReady(false);
                List<Integer> cardList = new ArrayList<>();
                for (int i = 0; i < 13; i++) {
                    int cardIndex = (int) (Math.random() * surplusCards.size());
                    if (cardIndex < min && 0 != cardIndex) {
                        min = cardIndex;
                        operationSeat = seat.getSeatNo();
                    }
                    cardList.add(surplusCards.get(cardIndex));
                    surplusCards.remove(cardIndex);
                }
                seat.setCards(cardList);
                seat.setInitialCards(cardList);
            }
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
        record.getHistoryList().addAll(historyList);


        //TODO 计算比分
        RunQuickly.RunQuicklyResultResponse.Builder resultResponse = RunQuickly.RunQuicklyResultResponse.newBuilder();
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
                } else {
                    score = cardSize;
                }
                score *= multiple;
                winScore += score;
                RunQuickly.RunQuicklyResult result = RunQuickly.RunQuicklyResult.newBuilder().setID(seat.getUserId())
                        .addAllCards(seat.getCards()).setScore(0 - score).build();
                resultResponse.addResult(result);

                SeatRecord seatRecord = new SeatRecord();
                seatRecord.setUserId(seat.getUserId());
                seatRecord.getInitialCards().addAll(seat.getInitialCards());
                seatRecord.getCards().addAll(seat.getCards());
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
        RunQuickly.RunQuicklyResult result = RunQuickly.RunQuicklyResult.newBuilder().setID(winSeat.getUserId()).setScore(winScore).build();
        resultResponse.addResult(result);
        winSeat.setScore(winSeat.getScore() + winScore);

        SeatRecord seatRecord = new SeatRecord();
        seatRecord.setUserId(winSeat.getUserId());
        seatRecord.getInitialCards().addAll(winSeat.getInitialCards());
        seatRecord.getCards().addAll(winSeat.getCards());
        seatRecord.setWinOrLoce(winScore);
        seatRecords.add(seatRecord);

        record.getSeatRecordList().addAll(seatRecords);
        recordList.add(record);

        response.setOperationType(GameBase.OperationType.RESULT).setData(resultResponse.build().toByteString());
        seats.stream().filter(seat -> RunQuicklyTcpService.userClients.containsKey(seat.getUserId()))
                .forEach(seat -> RunQuicklyTcpService.userClients.get(seat.getUserId()).send(response.build(), seat.getUserId()));

        clear();

        //结束房间
        if (gameCount == gameTimes) {
            roomOver(response, redisService);
        }
    }

    public void roomOver(GameBase.BaseConnection.Builder response, RedisService redisService) {
        RunQuickly.RunQuicklyResponse.Builder over = RunQuickly.RunQuicklyResponse.newBuilder();

        for (Seat seat : seats) {
            //TODO 统计
            RunQuickly.RunQuicklySeatOver.Builder seatOver = RunQuickly.RunQuicklySeatOver.newBuilder()
                    .setID(seat.getUserId()).setWinOrLose(seat.getScore());
            over.addGameOver(seatOver);
        }

        StringBuilder people = new StringBuilder();

        for (Seat seat : seats) {
            people.append(",").append(seat.getUserId());
            redisService.delete("reconnect" + seat.getUserId());
            if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                String uuid = UUID.randomUUID().toString().replace("-", "");
                while (redisService.exists(uuid)) {
                    uuid = UUID.randomUUID().toString().replace("-", "");
                }
                redisService.addCache("backkey" + uuid, seat.getUserId() + "", 1800);
                over.setBackKey(uuid);
                over.setDateTime(new Date().getTime());
                response.setOperationType(GameBase.OperationType.OVER).setData(over.build().toByteString());
                RunQuicklyTcpService.userClients.get(seat.getUserId()).send(response.build(), seat.getUserId());
            }
        }

        List<TotalScore> totalScores = new ArrayList<>();
        for (Seat seat : seats) {
            TotalScore totalScore = new TotalScore();
            totalScore.setHead(seat.getHead());
            totalScore.setNickname(seat.getNickname());
            totalScore.setUserId(seat.getUserId());
            totalScore.setScore(seat.getScore());
            totalScores.add(totalScore);
        }

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("gameType", 1);
        jsonObject.put("roomOwner", roomOwner);
        jsonObject.put("people", people.toString().substring(1));
        jsonObject.put("gameTotal", gameTimes);
        jsonObject.put("gameCount", gameCount);
        jsonObject.put("peopleCount", count);
        jsonObject.put("roomNo", Integer.parseInt(roomNo));
        jsonObject.put("gameData", JSON.toJSONString(recordList).getBytes());
        jsonObject.put("scoreData", JSON.toJSONString(totalScores).getBytes());

        ApiResponse apiResponse = JSON.parseObject(HttpUtil.urlConnectionByRsa("http://127.0.0.1:9999/api/gamerecord/create", jsonObject.toJSONString()), ApiResponse.class);
        if (0 != apiResponse.getCode()) {
            LoggerFactory.getLogger(this.getClass()).error("http://127.0.0.1:9999/api/gamerecord/create?" + jsonObject.toJSONString());
        }

        //删除该桌
        redisService.delete("room" + roomNo);
        redisService.delete("room_type" + roomNo);
        roomNo = null;
    }

    private void clear() {
        historyList.clear();
        gameStatus = GameStatus.READYING;
        operationSeat = 0;
        lastOperation = 0;
        multiple = 1;
        seats.forEach(Seat::clear);
    }
}
