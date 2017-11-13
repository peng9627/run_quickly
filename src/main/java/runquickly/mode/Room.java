package runquickly.mode;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.slf4j.LoggerFactory;
import runquickly.constant.Constant;
import runquickly.entrance.RunQuicklyTcpService;
import runquickly.redis.RedisService;
import runquickly.timeout.PlayCardTimeout;
import runquickly.timeout.ReadyTimeout;
import runquickly.utils.HttpUtil;

import java.util.*;

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
    private int gameCount;
    private List<Record> recordList = new ArrayList<>();//战绩
    private int gameRules;
    private Date startDate;

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

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public void addSeat(User user, int score) {
        Seat seat = new Seat();
        seat.setRobot(false);
        seat.setReady(false);
        seat.setAreaString(user.getArea());
        seat.setHead(user.getHead());
        seat.setNickname(user.getNickname());
        seat.setSex(user.getSex().equals("1"));
        seat.setScore(score);
        seat.setSeatNo(seatNos.get(0));
        seat.setIp(user.getLastLoginIp());
        seat.setGamecount(user.getGameCount());
        seatNos.remove(0);
        seat.setUserId(user.getUserId());
        seats.add(seat);
        seat.setCanPlay(true);
    }

    public void dealCard(GameBase.BaseConnection.Builder response, RedisService redisService) {
        startDate = new Date();
        int min = 14;
        if (operationSeat == 0) {
            List<Integer> surplusCards = Card.getAllCard();
            for (Seat seat : seats) {
                seat.setReady(false);
                List<Integer> cardList = new ArrayList<>();
                for (int i = 0; i < 13; i++) {
                    int cardIndex = (int) (Math.random() * surplusCards.size());
                    if (surplusCards.get(cardIndex) < min && 2 != surplusCards.get(cardIndex)) {
                        min = surplusCards.get(cardIndex);
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
        while (true) {
            if (count == next) {
                next = 1;
            } else {
                next += 1;
            }
            for (Seat seat : seats) {
                if (seat.getSeatNo() == next) {
                    return next;
                }
            }
        }
    }


    public void gameOver(GameBase.BaseConnection.Builder response, RedisService redisService) {

        Record record = new Record();
        List<SeatRecord> seatRecords = new ArrayList<>();
        record.getHistoryList().addAll(historyList);

        int multiple = 1;
        for (Seat seat : seats) {
            if (seat.getCards().size() == 0 || (1 == Card.containSize(seat.getCards(), 2)
                    && 1 == Card.containSize(seat.getCards(), 102)
                    && 1 == Card.containSize(seat.getCards(), 202)
                    && 1 == Card.containSize(seat.getCards(), 302)
                    && 1 == (gameRules >> 2) % 2)) {
                multiple = seat.getMultiple();
                break;
            }
        }
        if (0 == multiple || 1 == multiple) {
            multiple = 1;
        } else {
            multiple = 1;
            for (Seat seat : seats) {
                multiple *= seat.getMultiple() == 0 ? 1 : seat.getMultiple();
            }
        }

        RunQuickly.RunQuicklyResultResponse.Builder resultResponse = RunQuickly.RunQuicklyResultResponse.newBuilder();
        resultResponse.setReadyTimeCounter(redisService.exists("room_match" + roomNo) ? 8 : 0);
        int winScore = 0;
        Seat winSeat = null;

        int lastUser = 0;
        boolean onlyLose = false;
        if (historyList.size() > 3) {
            OperationHistory operationHistory = historyList.get(historyList.size() - 1);
            if (operationHistory.getCards().size() == 1 && historyList.size() > 1) {
                OperationHistory operationHistory1 = historyList.get(historyList.size() - 2);
                int playedCard = 0;
                if (0 != operationHistory1.getHistoryType().compareTo(OperationHistoryType.PASS)) {
                    playedCard = operationHistory1.getCards().get(0);
                }
                lastUser = operationHistory1.getUserId();
                int lastCard;
                for (int i = historyList.size() - 3; i > historyList.size() - count - 2 && i > -1; i--) {
                    OperationHistory operationHistory2 = historyList.get(i);
                    if (0 == OperationHistoryType.PLAY_CARD.compareTo(operationHistory2.getHistoryType())) {
                        if (1 == operationHistory2.getCards().size()) {
                            lastCard = operationHistory2.getCards().get(0);
                            for (Seat seat : seats) {
                                if (seat.getUserId() == lastUser) {
                                    List<Integer> tempCards = new ArrayList<>();
                                    tempCards.addAll(seat.getCards());
                                    tempCards.sort(new Comparator<Integer>() {
                                        @Override
                                        public int compare(Integer o1, Integer o2) {
                                            if (o1 % 100 == 2) {
                                                o1 = 15;
                                            }
                                            if (o2 % 100 == 2) {
                                                o2 = 15;
                                            }
                                            return o1 % 100 > o2 % 100 ? 1 : -1;
                                        }
                                    });
                                    int value = tempCards.get(tempCards.size() - 1) % 100;
                                    if (2 == value) {
                                        value = 15;
                                    }
                                    int playedCardValue = playedCard % 100;
                                    if (2 == playedCardValue) {
                                        playedCardValue = 15;
                                    }
                                    int lastCardValue = lastCard % 100;
                                    if (2 == lastCardValue) {
                                        lastCardValue = 15;
                                    }
                                    if (value > playedCardValue && value > lastCardValue % 100) {
                                        onlyLose = true;
                                    }
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }

        if (!onlyLose) {
            for (Seat seat : seats) {
                if (seat.getCards().size() > 0 && !(1 == Card.containSize(seat.getCards(), 2)
                        && 1 == Card.containSize(seat.getCards(), 102)
                        && 1 == Card.containSize(seat.getCards(), 202)
                        && 1 == Card.containSize(seat.getCards(), 302)
                        && 1 == (gameRules >> 2) % 2)) {
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

                    seat.setScore(seat.getScore() - score);

                    RunQuickly.RunQuicklyResult result = RunQuickly.RunQuicklyResult.newBuilder().setID(seat.getUserId())
                            .addAllCards(seat.getCards()).setCurrentScore(-score).setTotalScore(seat.getScore()).build();
                    resultResponse.addResult(result);

                    SeatRecord seatRecord = new SeatRecord();
                    seatRecord.setNickname(seat.getNickname());
                    seatRecord.setHead(seat.getHead());
                    seatRecord.setUserId(seat.getUserId());
                    seatRecord.getInitialCards().addAll(seat.getInitialCards());
                    seatRecord.getCards().addAll(seat.getCards());
                    seatRecord.setWinOrLose(-score);
                    seatRecord.setMultiple(seat.getMultiple());
                    seatRecords.add(seatRecord);
                    seatRecord.setScore(seat.getScore());

                } else {
                    winSeat = seat;
                }
            }
        } else {
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

                    if (seat.getUserId() != lastUser) {
                        RunQuickly.RunQuicklyResult result = RunQuickly.RunQuicklyResult.newBuilder().setID(seat.getUserId())
                                .addAllCards(seat.getCards()).setTotalScore(seat.getScore()).build();
                        resultResponse.addResult(result);

                        SeatRecord seatRecord = new SeatRecord();
                        seatRecord.setNickname(seat.getNickname());
                        seatRecord.setHead(seat.getHead());
                        seatRecord.setUserId(seat.getUserId());
                        seatRecord.getInitialCards().addAll(seat.getInitialCards());
                        seatRecord.getCards().addAll(seat.getCards());
                        seatRecord.setWinOrLose(0);
                        seatRecord.setMultiple(seat.getMultiple());
                        seatRecords.add(seatRecord);
                        seatRecord.setScore(seat.getScore());

                    }
                } else {
                    winSeat = seat;
                }
            }
            for (Seat seat : seats) {
                if (seat.getUserId() == lastUser) {
                    seat.setScore(seat.getScore() - winScore);
                    RunQuickly.RunQuicklyResult result = RunQuickly.RunQuicklyResult.newBuilder().setID(seat.getUserId())
                            .addAllCards(seat.getCards()).setCurrentScore(-winScore).setTotalScore(seat.getScore()).build();
                    resultResponse.addResult(result);

                    SeatRecord seatRecord = new SeatRecord();
                    seatRecord.setNickname(seat.getNickname());
                    seatRecord.setHead(seat.getHead());
                    seatRecord.setUserId(seat.getUserId());
                    seatRecord.getInitialCards().addAll(seat.getInitialCards());
                    seatRecord.getCards().addAll(seat.getCards());
                    seatRecord.setWinOrLose(-winScore);
                    seatRecord.setMultiple(seat.getMultiple());
                    seatRecords.add(seatRecord);
                    seatRecord.setScore(seat.getScore());
                    break;
                }
            }
        }

        if (null == winSeat) {
            return;
        }
        winSeat.setScore(winSeat.getScore() + winScore);
        winSeat.setWinCount(winSeat.getWinCount() + 1);
        RunQuickly.RunQuicklyResult result = RunQuickly.RunQuicklyResult.newBuilder().setID(winSeat.getUserId())
                .setCurrentScore(winScore).setTotalScore(winSeat.getScore()).build();
        resultResponse.addResult(result);

        SeatRecord seatRecord = new SeatRecord();
        seatRecord.setNickname(winSeat.getNickname());
        seatRecord.setHead(winSeat.getHead());
        seatRecord.setUserId(winSeat.getUserId());
        seatRecord.getInitialCards().addAll(winSeat.getInitialCards());
        seatRecord.getCards().addAll(winSeat.getCards());
        seatRecord.setWinOrLose(winScore);
        seatRecord.setMultiple(winSeat.getMultiple());
        seatRecord.setScore(winSeat.getScore());
        seatRecords.add(seatRecord);

        record.getSeatRecordList().addAll(seatRecords);
        recordList.add(record);

        if (redisService.exists("room_match" + roomNo)) {
            GameBase.ScoreResponse.Builder scoreResponse = GameBase.ScoreResponse.newBuilder();
            for (RunQuickly.RunQuicklyResult.Builder userResult : resultResponse.getResultBuilderList()) {
                if (RunQuicklyTcpService.userClients.containsKey(userResult.getID())) {
                    GameBase.MatchResult matchResult;
                    if (gameCount != gameTimes) {
                        matchResult = GameBase.MatchResult.newBuilder().setResult(0).setCurrentScore(userResult.getCurrentScore())
                                .setTotalScore(userResult.getTotalScore()).build();
                    } else {
                        matchResult = GameBase.MatchResult.newBuilder().setResult(2).setCurrentScore(userResult.getCurrentScore())
                                .setTotalScore(userResult.getTotalScore()).build();
                    }
                    RunQuicklyTcpService.userClients.get(userResult.getID()).send(response.setOperationType(GameBase.OperationType.MATCH_RESULT)
                            .setData(matchResult.toByteString()).build(), userResult.getID());
                }
                scoreResponse.addScoreResult(GameBase.ScoreResult.newBuilder().setID(userResult.getID()).setScore(userResult.getTotalScore()));
            }
            for (Seat seat : seats) {
                if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                    RunQuicklyTcpService.userClients.get(seat.getUserId()).send(response.setOperationType(GameBase.OperationType.MATCH_SCORE)
                            .setData(scoreResponse.build().toByteString()).build(), seat.getUserId());
                }
            }
        } else {
            response.setOperationType(GameBase.OperationType.RESULT).setData(resultResponse.build().toByteString());
            seats.stream().filter(seat -> RunQuicklyTcpService.userClients.containsKey(seat.getUserId()))
                    .forEach(seat -> RunQuicklyTcpService.userClients.get(seat.getUserId()).send(response.build(), seat.getUserId()));
        }
        clear();

        //结束房间
        if (gameCount == gameTimes) {
            roomOver(response, redisService);
        } else {
            if (redisService.exists("room_match" + roomNo)) {
                new ReadyTimeout(Integer.valueOf(roomNo), redisService, gameCount).start();
            }
        }
    }

    public void roomOver(GameBase.BaseConnection.Builder response, RedisService redisService) {
        JSONObject jsonObject = new JSONObject();
        //是否竞技场
        if (redisService.exists("room_match" + roomNo)) {
            String matchNo = redisService.getCache("room_match" + roomNo);
            redisService.delete("room_match" + roomNo);
            if (redisService.exists("match_info" + matchNo)) {
                while (!redisService.lock("lock_match_info" + matchNo)) {
                }
                GameBase.MatchResult.Builder matchResult = GameBase.MatchResult.newBuilder();
                MatchInfo matchInfo = JSON.parseObject(redisService.getCache("match_info" + matchNo), MatchInfo.class);
                Arena arena = matchInfo.getArena();

                //移出当前桌
                List<Integer> rooms = matchInfo.getRooms();
                for (Integer integer : rooms) {
                    if (integer == Integer.parseInt(roomNo)) {
                        rooms.remove(integer);
                        break;
                    }
                }

                //等待的人
                List<MatchUser> waitUsers = matchInfo.getWaitUsers();
                if (null == waitUsers) {
                    waitUsers = new ArrayList<>();
                    matchInfo.setWaitUsers(waitUsers);
                }
                //在比赛中的人 重置分数
                List<MatchUser> matchUsers = matchInfo.getMatchUsers();
                for (Seat seat : seats) {
                    redisService.delete("reconnect" + seat.getUserId());
                    for (MatchUser matchUser : matchUsers) {
                        if (seat.getUserId() == matchUser.getUserId()) {
                            matchUser.setScore(seat.getScore());
                        }
                    }
//                    if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
//                        RunQuicklyTcpService.userClients.get(seat.getUserId()).send(response.setOperationType(GameBase.OperationType.ROOM_INFO).clearData().build(), seat.getUserId());
//                        GameBase.RoomSeatsInfo.Builder roomSeatsInfo = GameBase.RoomSeatsInfo.newBuilder();
//                        GameBase.SeatResponse.Builder seatResponse = GameBase.SeatResponse.newBuilder();
//                        seatResponse.setSeatNo(1);
//                        seatResponse.setID(seat.getUserId());
//                        seatResponse.setScore(seat.getScore());
//                        seatResponse.setReady(false);
//                        seatResponse.setIp(seat.getIp());
//                        seatResponse.setGameCount(seat.getGamecount());
//                        seatResponse.setNickname(seat.getNickname());
//                        seatResponse.setHead(seat.getHead());
//                        seatResponse.setSex(seat.isSex());
//                        seatResponse.setOffline(false);
//                        seatResponse.setIsRobot(seat.isRobot());
//                        roomSeatsInfo.addSeats(seatResponse.build());
//                        RunQuicklyTcpService.userClients.get(seat.getUserId()).send(response.setOperationType(GameBase.OperationType.SEAT_INFO).setData(roomSeatsInfo.build().toByteString()).build(), seat.getUserId());
//                    }
                }

                //用户对应分数
                Map<Integer, Integer> userIdScore = new HashMap<>();
                for (MatchUser matchUser : matchUsers) {
                    userIdScore.put(matchUser.getUserId(), matchUser.getScore());
                }

                GameBase.MatchData.Builder matchData = GameBase.MatchData.newBuilder();
                switch (matchInfo.getStatus()) {
                    case 1:
                        //TODO 少一个0，记得加回来

                        //根据金币排序
                        seats.sort(new Comparator<Seat>() {
                            @Override
                            public int compare(Seat o1, Seat o2) {
                                return o1.getScore() > o2.getScore() ? 1 : -1;
                            }
                        });

                        //本局未被淘汰的
                        List<MatchUser> thisWait = new ArrayList<>();
                        //循环座位，淘汰
                        for (Seat seat : seats) {
                            for (MatchUser matchUser : matchUsers) {
                                if (matchUser.getUserId() == seat.getUserId()) {
                                    if (seat.getScore() < matchInfo.getMatchEliminateScore() && matchUsers.size() > arena.getCount() / 2) {
                                        matchUsers.remove(matchUser);

                                        matchResult.setResult(3).setTotalScore(seat.getScore()).setCurrentScore(-1);
                                        response.setOperationType(GameBase.OperationType.MATCH_RESULT).setData(matchResult.build().toByteString());
                                        if (RunQuicklyTcpService.userClients.containsKey(matchUser.getUserId())) {
                                            RunQuicklyTcpService.userClients.get(matchUser.getUserId()).send(response.build(), matchUser.getUserId());
                                        }
                                        response.setOperationType(GameBase.OperationType.MATCH_BALANCE).setData(GameBase.MatchBalance.newBuilder()
                                                .setRanking(matchUsers.size()).setTotalScore(matchUser.getScore()).build().toByteString());
                                        if (RunQuicklyTcpService.userClients.containsKey(matchUser.getUserId())) {
                                            RunQuicklyTcpService.userClients.get(matchUser.getUserId()).send(response.build(), matchUser.getUserId());
                                            GameBase.OverResponse.Builder over = GameBase.OverResponse.newBuilder();
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

                                        redisService.delete("reconnect" + seat.getUserId());
                                    } else {
                                        thisWait.add(matchUser);
                                        redisService.addCache("reconnect" + seat.getUserId(), "run_quickly," + matchNo);
                                    }
                                    break;
                                }
                            }
                        }

                        //淘汰人数以满
                        int count = matchUsers.size();
                        if (count == arena.getCount() / 2 && 0 == rooms.size()) {
                            waitUsers.clear();
                            List<User> users = new ArrayList<>();
                            StringBuilder stringBuilder = new StringBuilder();
                            for (MatchUser matchUser : matchUsers) {
                                stringBuilder.append(",").append(matchUser.getUserId());
                            }
                            jsonObject.clear();
                            jsonObject.put("userIds", stringBuilder.toString().substring(1));
                            ApiResponse<List<User>> usersResponse = JSON.parseObject(HttpUtil.urlConnectionByRsa(Constant.apiUrl + Constant.userListUrl, jsonObject.toJSONString()),
                                    new TypeReference<ApiResponse<List<User>>>() {
                                    });
                            if (0 == usersResponse.getCode()) {
                                users = usersResponse.getData();
                            }

                            //第二轮开始
                            matchInfo.setStatus(2);
                            matchData.setStatus(2);
                            matchData.setCurrentCount(matchUsers.size());
                            matchData.setRound(1);
                            while (4 <= users.size()) {
                                rooms.add(matchInfo.addRoom(matchNo, 2, redisService, users.subList(0, 4), userIdScore, response, matchData));
                            }
                        } else if (count > arena.getCount() / 2) {
                            //满四人继续匹配
                            waitUsers.addAll(thisWait);
                            while (4 <= waitUsers.size()) {
                                //剩余用户
                                List<User> users = new ArrayList<>();
                                StringBuilder stringBuilder = new StringBuilder();
                                for (int i = 0; i < 4; i++) {
                                    stringBuilder.append(",").append(waitUsers.remove(0).getUserId());
                                }
                                jsonObject.clear();
                                jsonObject.put("userIds", stringBuilder.toString().substring(1));
                                ApiResponse<List<User>> usersResponse = JSON.parseObject(HttpUtil.urlConnectionByRsa(Constant.apiUrl + Constant.userListUrl, jsonObject.toJSONString()),
                                        new TypeReference<ApiResponse<List<User>>>() {
                                        });
                                if (0 == usersResponse.getCode()) {
                                    users = usersResponse.getData();
                                }
                                matchData.setStatus(1);
                                matchData.setCurrentCount(matchUsers.size());
                                matchData.setRound(1);
                                rooms.add(matchInfo.addRoom(matchNo, 1, redisService, users, userIdScore, response, matchData));
                            }
                        }
                        break;
                    case 2:
                    case 3:
                        for (Seat seat : seats) {
                            redisService.addCache("reconnect" + seat.getUserId(), "run_quickly," + matchNo);
                        }
                        if (0 == rooms.size()) {
                            matchInfo.setStatus(matchInfo.getStatus() + 1);
                            matchData.setStatus(2);

                            List<User> users = new ArrayList<>();
                            StringBuilder stringBuilder = new StringBuilder();
                            for (MatchUser matchUser : matchUsers) {
                                stringBuilder.append(",").append(matchUser.getUserId());
                            }
                            jsonObject.clear();
                            jsonObject.put("userIds", stringBuilder.toString().substring(1));
                            ApiResponse<List<User>> usersResponse = JSON.parseObject(HttpUtil.urlConnectionByRsa(Constant.apiUrl + Constant.userListUrl, jsonObject.toJSONString()),
                                    new TypeReference<ApiResponse<List<User>>>() {
                                    });
                            if (0 == usersResponse.getCode()) {
                                users = usersResponse.getData();
                            }
                            matchData.setCurrentCount(matchUsers.size());
                            matchData.setRound(matchInfo.getStatus() - 1);
                            while (4 <= users.size()) {
                                rooms.add(matchInfo.addRoom(matchNo, 2, redisService, users.subList(0, 4), userIdScore, response, matchData));
                            }
                        }
                        break;
                    case 4:
                        for (Seat seat : seats) {
                            MatchUser matchUser = new MatchUser();
                            matchUser.setUserId(seat.getUserId());
                            matchUser.setScore(seat.getScore());
                            waitUsers.add(matchUser);
                            redisService.addCache("reconnect" + seat.getUserId(), "run_quickly," + matchNo);
                        }

                        waitUsers.sort(new Comparator<MatchUser>() {
                            @Override
                            public int compare(MatchUser o1, MatchUser o2) {
                                return o1.getScore() > o2.getScore() ? -1 : 1;
                            }
                        });
                        while (waitUsers.size() > 4) {
                            MatchUser matchUser = waitUsers.remove(waitUsers.size() - 1);

                            response.setOperationType(GameBase.OperationType.MATCH_BALANCE).setData(GameBase.MatchBalance.newBuilder()
                                    .setRanking(matchUsers.size()).setTotalScore(matchUser.getScore()).build().toByteString());
                            if (RunQuicklyTcpService.userClients.containsKey(matchUser.getUserId())) {
                                RunQuicklyTcpService.userClients.get(matchUser.getUserId()).send(response.build(), matchUser.getUserId());
                                GameBase.OverResponse.Builder over = GameBase.OverResponse.newBuilder();
                                String uuid = UUID.randomUUID().toString().replace("-", "");
                                while (redisService.exists(uuid)) {
                                    uuid = UUID.randomUUID().toString().replace("-", "");
                                }
                                redisService.addCache("backkey" + uuid, matchUser.getUserId() + "", 1800);
                                over.setBackKey(uuid);
                                over.setDateTime(new Date().getTime());
                                response.setOperationType(GameBase.OperationType.OVER).setData(over.build().toByteString());
                                RunQuicklyTcpService.userClients.get(matchUser.getUserId()).send(response.build(), matchUser.getUserId());
                            }
                            redisService.delete("reconnect" + matchUser.getUserId());
                        }

                        if (0 == rooms.size()) {

                            matchUsers.clear();
                            matchUsers.addAll(waitUsers);
                            waitUsers.clear();

                            matchInfo.setStatus(5);
                            matchData.setStatus(3);

                            List<User> users = new ArrayList<>();
                            StringBuilder stringBuilder = new StringBuilder();
                            for (MatchUser matchUser : matchUsers) {
                                stringBuilder.append(",").append(matchUser.getUserId());
                            }
                            jsonObject.clear();
                            jsonObject.put("userIds", stringBuilder.toString().substring(1));
                            ApiResponse<List<User>> usersResponse = JSON.parseObject(HttpUtil.urlConnectionByRsa(Constant.apiUrl + Constant.userListUrl, jsonObject.toJSONString()),
                                    new TypeReference<ApiResponse<List<User>>>() {
                                    });
                            if (0 == usersResponse.getCode()) {
                                users = usersResponse.getData();
                            }
                            matchData.setCurrentCount(matchUsers.size());
                            matchData.setRound(1);
                            while (4 == users.size()) {
                                rooms.add(matchInfo.addRoom(matchNo, 2, redisService, users, userIdScore, response, matchData));
                            }
                        }
                        break;
                    case 5:
                        matchUsers.sort(new Comparator<MatchUser>() {
                            @Override
                            public int compare(MatchUser o1, MatchUser o2) {
                                return o1.getScore() > o2.getScore() ? -1 : 1;
                            }
                        });
                        for (int i = 0; i < matchUsers.size(); i++) {
                            if (i == 0 && matchInfo.getArena().getArenaType() == 0) {
                                jsonObject.clear();
                                jsonObject.put("flowType", 1);
                                jsonObject.put("money", matchInfo.getArena().getReward());
                                jsonObject.put("description", "比赛获胜" + matchInfo.getArena().getId());
                                jsonObject.put("userId", matchUsers.get(i).getUserId());
                                ApiResponse moneyDetail = JSON.parseObject(HttpUtil.urlConnectionByRsa(Constant.apiUrl + Constant.moneyDetailedCreate, jsonObject.toJSONString()), new TypeReference<ApiResponse<User>>() {
                                });
                                if (0 != moneyDetail.getCode()) {
                                    LoggerFactory.getLogger(this.getClass()).error(Constant.apiUrl + Constant.moneyDetailedCreate + "?" + jsonObject.toJSONString());
                                }
                            }
                            matchResult.setResult(i == 0 ? 1 : 3).setTotalScore(matchUsers.get(i).getScore()).setCurrentScore(-1);
                            response.setOperationType(GameBase.OperationType.MATCH_RESULT).setData(matchResult.build().toByteString());
                            if (RunQuicklyTcpService.userClients.containsKey(matchUsers.get(i).getUserId())) {
                                RunQuicklyTcpService.userClients.get(matchUsers.get(i).getUserId()).send(response.build(), matchUsers.get(i).getUserId());
                            }
                            response.setOperationType(GameBase.OperationType.MATCH_BALANCE).setData(GameBase.MatchBalance.newBuilder()
                                    .setRanking(i + 1).setTotalScore(matchUsers.get(i).getScore()).build().toByteString());
                            if (RunQuicklyTcpService.userClients.containsKey(matchUsers.get(i).getUserId())) {
                                RunQuicklyTcpService.userClients.get(matchUsers.get(i).getUserId()).send(response.build(), matchUsers.get(i).getUserId());
                                GameBase.OverResponse.Builder over = GameBase.OverResponse.newBuilder();
                                String uuid = UUID.randomUUID().toString().replace("-", "");
                                while (redisService.exists(uuid)) {
                                    uuid = UUID.randomUUID().toString().replace("-", "");
                                }
                                redisService.addCache("backkey" + uuid, matchUsers.get(i).getUserId() + "", 1800);
                                over.setBackKey(uuid);
                                over.setDateTime(new Date().getTime());
                                response.setOperationType(GameBase.OperationType.OVER).setData(over.build().toByteString());
                                RunQuicklyTcpService.userClients.get(matchUsers.get(i).getUserId()).send(response.build(), matchUsers.get(i).getUserId());
                            }
                        }
                        matchInfo.setStatus(-1);
                        break;
                }
                if (0 < matchInfo.getStatus()) {
                    matchInfo.setMatchUsers(matchUsers);
                    matchInfo.setRooms(rooms);
                    matchInfo.setWaitUsers(waitUsers);
                    redisService.addCache("match_info" + matchNo, JSON.toJSONString(matchInfo));
                }
                redisService.unlock("lock_match_info" + matchNo);
            }
        } else {
            if (0 == gameStatus.compareTo(GameStatus.WAITING)) {
                jsonObject.put("flowType", 1);
                switch (gameTimes) {
                    case 4:
                        jsonObject.put("money", 1);
                        break;
                    case 8:
                        jsonObject.put("money", 2);
                        break;
                    case 16:
                        jsonObject.put("money", 3);
                        break;
                }
                jsonObject.put("description", "开房间退回" + roomNo);
                jsonObject.put("userId", roomOwner);
                ApiResponse moneyDetail = JSON.parseObject(HttpUtil.urlConnectionByRsa(Constant.apiUrl + Constant.moneyDetailedCreate, jsonObject.toJSONString()), new TypeReference<ApiResponse<User>>() {
                });
                if (0 != moneyDetail.getCode()) {
                    LoggerFactory.getLogger(this.getClass()).error(Constant.apiUrl + Constant.moneyDetailedCreate + "?" + jsonObject.toJSONString());
                }
            }

            if (0 != recordList.size()) {
                RunQuickly.RunQuicklyBalanceResponse.Builder balance = RunQuickly.RunQuicklyBalanceResponse.newBuilder();
                for (Seat seat : seats) {
                    RunQuickly.RunQuicklySeatBalance.Builder seatBalance = RunQuickly.RunQuicklySeatBalance.newBuilder()
                            .setID(seat.getUserId()).setWinOrLose(seat.getScore()).setWinCount(seat.getWinCount());
                    balance.addGameBalance(seatBalance);
                }
                for (Seat seat : seats) {
                    if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                        response.setOperationType(GameBase.OperationType.BALANCE).setData(balance.build().toByteString());
                        RunQuicklyTcpService.userClients.get(seat.getUserId()).send(response.build(), seat.getUserId());
                    }
                }
            }

            StringBuilder people = new StringBuilder();

            GameBase.OverResponse.Builder over = GameBase.OverResponse.newBuilder();
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

            if (0 != recordList.size()) {
                List<TotalScore> totalScores = new ArrayList<>();
                for (Seat seat : seats) {
                    TotalScore totalScore = new TotalScore();
                    totalScore.setHead(seat.getHead());
                    totalScore.setNickname(seat.getNickname());
                    totalScore.setUserId(seat.getUserId());
                    totalScore.setScore(seat.getScore());
                    totalScores.add(totalScore);
                }
                SerializerFeature[] features = new SerializerFeature[]{SerializerFeature.WriteNullListAsEmpty,
                        SerializerFeature.WriteMapNullValue, SerializerFeature.DisableCircularReferenceDetect,
                        SerializerFeature.WriteNullStringAsEmpty, SerializerFeature.WriteNullNumberAsZero,
                        SerializerFeature.WriteNullBooleanAsFalse};
                int feature = SerializerFeature.config(JSON.DEFAULT_GENERATE_FEATURE, SerializerFeature.WriteEnumUsingName, false);
                jsonObject.clear();
                jsonObject.put("gameType", 2);
                jsonObject.put("roomOwner", roomOwner);
                jsonObject.put("people", people.toString().substring(1));
                jsonObject.put("gameTotal", gameTimes);
                jsonObject.put("gameCount", gameCount);
                jsonObject.put("peopleCount", count);
                jsonObject.put("roomNo", Integer.parseInt(roomNo));
                JSONObject gameRule = new JSONObject();
                gameRule.put("gameRules", gameRules);
                jsonObject.put("gameRule", gameRule.toJSONString());
                jsonObject.put("gameData", JSON.toJSONString(recordList, feature, features).getBytes());
                jsonObject.put("scoreData", JSON.toJSONString(totalScores, feature, features).getBytes());

                ApiResponse apiResponse = JSON.parseObject(HttpUtil.urlConnectionByRsa(Constant.apiUrl + Constant.gamerecordCreateUrl, jsonObject.toJSONString()), ApiResponse.class);
                if (0 != apiResponse.getCode()) {
                    LoggerFactory.getLogger(this.getClass()).error(Constant.apiUrl + Constant.gamerecordCreateUrl + "?" + jsonObject.toJSONString());
                }
            }
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
        seats.forEach(Seat::clear);
        startDate = new Date();
    }

    public void sendRoomInfo(GameBase.RoomCardIntoResponse.Builder roomCardIntoResponseBuilder, GameBase.BaseConnection.Builder response, int userId) {
        RunQuickly.RunQuicklyIntoResponse intoResponse = RunQuickly.RunQuicklyIntoResponse.newBuilder()
                .setBaseScore(baseScore).setCount(count).setGameTimes(gameTimes)
                .setGameRules(gameRules).build();
        roomCardIntoResponseBuilder.setError(GameBase.ErrorCode.SUCCESS).setData(intoResponse.toByteString());
        roomCardIntoResponseBuilder.setGameType(GameBase.GameType.RUN_QUICKLY);
        response.setOperationType(GameBase.OperationType.ROOM_INFO).setData(roomCardIntoResponseBuilder.build().toByteString());
        if (RunQuicklyTcpService.userClients.containsKey(userId)) {
            RunQuicklyTcpService.userClients.get(userId).send(response.build(), userId);
        }
    }

    public void sendSeatInfo(GameBase.BaseConnection.Builder response) {
        GameBase.RoomSeatsInfo.Builder roomSeatsInfo = GameBase.RoomSeatsInfo.newBuilder();
        for (Seat seat1 : seats) {
            GameBase.SeatResponse.Builder seatResponse = GameBase.SeatResponse.newBuilder();
            seatResponse.setSeatNo(seat1.getSeatNo());
            seatResponse.setID(seat1.getUserId());
            seatResponse.setScore(seat1.getScore());
            seatResponse.setReady(seat1.isReady());
            seatResponse.setIp(seat1.getIp());
            seatResponse.setGameCount(seat1.getGamecount());
            seatResponse.setNickname(seat1.getNickname());
            seatResponse.setHead(seat1.getHead());
            seatResponse.setSex(seat1.isSex());
            seatResponse.setOffline(seat1.isRobot());
            seatResponse.setIsRobot(seat1.isRobot());
            roomSeatsInfo.addSeats(seatResponse.build());
        }
        response.setOperationType(GameBase.OperationType.SEAT_INFO).setData(roomSeatsInfo.build().toByteString());
        for (Seat seat : seats) {
            if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                RunQuicklyTcpService.userClients.get(seat.getUserId()).send(response.build(), seat.getUserId());
            }
        }
    }

    public void start(GameBase.BaseConnection.Builder response, RedisService redisService) {
        gameCount += 1;
        gameStatus = GameStatus.PLAYING;
        dealCard(response, redisService);
        for (Seat seat : seats) {
            seat.setMultiple(1);
        }
        RunQuickly.RunQuicklyStartResponse.Builder dealCard = RunQuickly.RunQuicklyStartResponse.newBuilder();
        response.setOperationType(GameBase.OperationType.START);
        seats.stream().filter(seat -> RunQuicklyTcpService.userClients.containsKey(seat.getUserId())).forEach(seat -> {
            dealCard.clearCards();
            dealCard.addAllCards(seat.getCards());
            response.setData(dealCard.build().toByteString());
            RunQuicklyTcpService.userClients.get(seat.getUserId()).send(response.build(), seat.getUserId());
        });

        for (Seat seat : seats) {
            if (1 == Card.containSize(seat.getCards(), 2)
                    && 1 == Card.containSize(seat.getCards(), 102)
                    && 1 == Card.containSize(seat.getCards(), 202)
                    && 1 == Card.containSize(seat.getCards(), 302)
                    && 1 == (gameRules >> 2) % 2) {
                gameOver(response, redisService);
                return;
            }
        }
        for (Seat seat : seats) {
            if (seat.getSeatNo() == operationSeat) {
                if (redisService.exists("room_match" + roomNo)) {
                    new PlayCardTimeout(seat.getUserId(), roomNo, historyList.size(), gameCount, redisService).start();
                }
                GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder().setID(seat.getUserId())
                        .setTimeCounter(redisService.exists("room_match" + roomNo) ? 8 : 0)
                        .setOnlyBomb(!seat.isCanPlay()).build();
                response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                for (Seat seat1 : seats) {
                    if (RunQuicklyTcpService.userClients.containsKey(seat1.getUserId())) {
                        RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId());
                    }
                }
                break;
            }
        }
    }

    /**
     * 出牌
     *
     * @param userId
     * @param cardList
     * @param response
     * @param redisService
     * @param actionResponse
     */
    public void playCard(int userId, List<Integer> cardList, GameBase.BaseConnection.Builder response, RedisService redisService,
                         GameBase.BaseAction.Builder actionResponse) {
        System.out.println("出牌------");
        for (Integer integers : cardList) {
            System.out.print(integers);
        }
        for (Seat seat : seats) {
            if (seat.getUserId() == userId && operationSeat == seat.getSeatNo()) {
                if (seat.getCards().containsAll(cardList)) {
                    //判断该出牌的牌型
                    CardType cardType = null;
                    int value = 0;
                    int cardSize = 0;
                    //出牌正确性验证
                    if (historyList.size() > 0) {
                        for (int i = historyList.size() - 1; i > historyList.size() - count && i > -1; i--) {
                            OperationHistory operationHistory = historyList.get(i);
                            if (operationHistory.getUserId() == userId) {
                                break;
                            }
                            if (0 == OperationHistoryType.PLAY_CARD.compareTo(operationHistory.getHistoryType())) {
                                cardType = Card.getCardType(operationHistory.getCards(), 1 == (gameRules >> 1) % 2);
                                value = Card.getCardValue(operationHistory.getCards(), cardType);
                                cardSize = operationHistory.getCards().size();
                                break;
                            }
                        }
                    } else {
                        List<Integer> temps = new ArrayList<>();
                        temps.addAll(seat.getCards());
                        temps.sort(new Comparator<Integer>() {
                            @Override
                            public int compare(Integer o1, Integer o2) {
                                return o1.compareTo(o2);
                            }
                        });
                        if (1 == Card.containSize(temps, 2)) {
                            temps.remove(Integer.valueOf(2));
                        }
                        if (0 == Card.containSize(cardList, temps.get(0))) {
                            cardList = new ArrayList<>();
                            cardList.add(temps.get(0));
                        }
                    }

                    //如果没出牌
                    if (null == cardType) {
                        for (Seat seat1 : seats) {
                            seat1.setCanPlay(true);
                        }
                        if (0 == cardList.size()) {
                            System.out.println("必须出牌");
//                            pass(userId, actionResponse, response, redisService);
                            cardList.add(seat.getCards().get(0));
                            playCard(userId, cardList, response, redisService, actionResponse);
                            return;
                        } else {
                            CardType myCardType = Card.getCardType(cardList, 1 == (gameRules >> 1) % 2);
                            if (0 == myCardType.compareTo(CardType.ERROR)) {
                                System.out.println("牌型错误");
//                                pass(userId, actionResponse, response, redisService);
                                cardList = new ArrayList<>();
                                cardList.add(seat.getCards().get(0));
                                playCard(userId, cardList, response, redisService, actionResponse);
                                return;
                            }
                            if (myCardType == CardType.ZHADAN) {
                                seat.setMultiple(seat.getMultiple() * 2);
                            }
                        }
                    } else {

                        //是否出牌
                        if (0 != cardList.size()) {
                            CardType myCardType = Card.getCardType(cardList, 1 == (gameRules >> 1) % 2);

                            if (!seat.isCanPlay() && 0 != myCardType.compareTo(CardType.ZHADAN)) {
                                System.out.println("不可反打");
                                pass(userId, actionResponse, response, redisService);
                            }

                            if (0 == myCardType.compareTo(CardType.ERROR)) {
                                System.out.println("牌型错误");
                                pass(userId, actionResponse, response, redisService);
                                return;
                            }

                            if (0 == myCardType.compareTo(cardType)) {
                                //张数相等
                                if (cardList.size() == cardSize) {
                                    int myValue = Card.getCardValue(cardList, myCardType);
                                    if (myValue <= value) {
                                        System.out.println("出牌错误:值小于");
                                        pass(userId, actionResponse, response, redisService);
                                        return;
                                    }
                                } else if (0 != CardType.ZHADAN.compareTo(cardType) || cardList.size() < cardSize) {
                                    System.out.println("出牌错误:张数不同，不是炸弹或张数小于");
                                    pass(userId, actionResponse, response, redisService);
                                    return;
                                }
                            } else if (myCardType != CardType.ZHADAN) {
                                System.out.println("出牌错误:牌型不同并且不是炸弹");
                                pass(userId, actionResponse, response, redisService);
                                return;
                            }
                            if (myCardType == CardType.ZHADAN) {
                                seat.setMultiple(seat.getMultiple() * 2);
                            }
                        } else {
                            if (1 == gameRules % 2) {
                                seat.setCanPlay(false);
                            }
                            historyList.add(new OperationHistory(userId, OperationHistoryType.PASS, null));
                            lastOperation = seat.getUserId();
                            operationSeat = getNextSeat();
                            actionResponse.setOperationId(GameBase.ActionId.PASS).clearData();
                            response.setOperationType(GameBase.OperationType.ACTION).setData(actionResponse.build().toByteString());
                            seats.stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                                    .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));

                            Seat operationSeat = null;
                            for (Seat seat1 : seats) {
                                if (seat1.getSeatNo() == this.operationSeat) {
                                    operationSeat = seat1;
                                }
                            }
                            if (redisService.exists("room_match" + roomNo)) {
                                new PlayCardTimeout(operationSeat.getUserId(), roomNo, historyList.size(), gameCount, redisService).start();
                            }
                            GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder()
                                    .setTimeCounter(redisService.exists("room_match" + roomNo) ? 8 : 0)
                                    .setOnlyBomb(!operationSeat.isCanPlay()).setID(operationSeat.getUserId()).build();
                            response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                            seats.stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                                    .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));
                            return;
                        }
                    }
                    historyList.add(new OperationHistory(userId, OperationHistoryType.PLAY_CARD, cardList));
                    lastOperation = seat.getUserId();
                    operationSeat = getNextSeat();

                    seat.getCards().removeAll(cardList);
                    RunQuickly.RunQuicklyPlayCard playCardResponse = RunQuickly.RunQuicklyPlayCard.newBuilder()
                            .addAllCard(cardList).build();
                    actionResponse.setOperationId(GameBase.ActionId.PLAY_CARD).setData(playCardResponse.toByteString());
                    response.setOperationType(GameBase.OperationType.ACTION).setData(actionResponse.build().toByteString());
                    seats.stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                            .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));

                    if (0 == seat.getCards().size()) {
                        gameOver(response, redisService);
                        return;
                    }

                    Seat operationSeat = null;
                    for (Seat seat1 : seats) {
                        if (seat1.getSeatNo() == this.operationSeat) {
                            operationSeat = seat1;
                            break;
                        }
                    }
                    if (redisService.exists("room_match" + roomNo)) {
                        new PlayCardTimeout(operationSeat.getUserId(), roomNo, historyList.size(), gameCount, redisService).start();
                    }
                    GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder()
                            .setTimeCounter(redisService.exists("room_match" + roomNo) ? 8 : 0)
                            .setOnlyBomb(!operationSeat.isCanPlay()).setID(operationSeat.getUserId()).build();
                    response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                    seats.stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                            .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));

                } else {
                    System.out.println("用户手中没有此牌" + userId);
                    pass(userId, actionResponse, response, redisService);
                }
                break;
            }
        }
    }

    /**
     * 过
     *
     * @param userId         用户id
     * @param actionResponse 行动通知
     * @param response       返回
     * @param redisService   redis
     */
    public void pass(int userId, GameBase.BaseAction.Builder actionResponse, GameBase.BaseConnection.Builder response, RedisService redisService) {
        for (Seat seat : seats) {
            if (seat.getUserId() == userId && operationSeat == seat.getSeatNo()) {
                if (1 == gameRules % 2) {


                    seat.setCanPlay(false);
                }
                boolean canPass = false;
                if (historyList.size() > 0) {
                    for (int i = historyList.size() - 1; i > historyList.size() - count && i > -1; i--) {
                        OperationHistory operationHistory = historyList.get(i);
                        if (operationHistory.getUserId() == userId) {
                            break;
                        }
                        if (0 == OperationHistoryType.PLAY_CARD.compareTo(operationHistory.getHistoryType())) {
                            canPass = true;
                            break;
                        }
                    }
                }
                if (!canPass) {
                    System.out.println("必须出牌");
                    List<Integer> cardList = new ArrayList<>();
                    cardList.add(seat.getCards().get(0));
                    playCard(userId, cardList, response, redisService, actionResponse);
                    break;
                }

                historyList.add(new OperationHistory(userId, OperationHistoryType.PASS, null));
                lastOperation = seat.getUserId();
                operationSeat = getNextSeat();
                actionResponse.setID(userId).setOperationId(GameBase.ActionId.PASS).clearData();
                response.setOperationType(GameBase.OperationType.ACTION).setData(actionResponse.build().toByteString());
                seats.stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                        .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));

                Seat operationSeat = null;
                for (Seat seat1 : seats) {
                    if (seat1.getSeatNo() == this.operationSeat) {
                        operationSeat = seat1;
                    }
                }
                if (redisService.exists("room_match" + roomNo)) {
                    new PlayCardTimeout(operationSeat.getUserId(), roomNo, historyList.size(), gameCount, redisService).start();
                }

                canPass = false;
                if (historyList.size() > 0) {
                    for (int i = historyList.size() - 1; i > historyList.size() - count && i > -1; i--) {
                        OperationHistory operationHistory = historyList.get(i);
                        if (operationHistory.getUserId() == operationSeat.getUserId()) {
                            break;
                        }
                        if (0 == OperationHistoryType.PLAY_CARD.compareTo(operationHistory.getHistoryType())) {
                            canPass = true;
                            break;
                        }
                    }
                }
                if (!canPass) {
                    operationSeat.setCanPlay(true);
                }

                GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder()
                        .setTimeCounter(redisService.exists("room_match" + roomNo) ? 8 : 0)
                        .setOnlyBomb(!operationSeat.isCanPlay()).setID(operationSeat.getUserId()).build();
                response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                seats.stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                        .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));
                break;
            }
        }
    }
}
