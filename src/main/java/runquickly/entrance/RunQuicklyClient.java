package runquickly.entrance;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import runquickly.constant.Constant;
import runquickly.mode.*;
import runquickly.redis.RedisService;
import runquickly.timeout.DissolveTimeout;
import runquickly.timeout.MatchScoreTimeout;
import runquickly.timeout.ReadyTimeout;
import runquickly.utils.HttpUtil;
import runquickly.utils.LoggerUtil;

import java.util.Date;
import java.util.List;

/**
 * Created date 2016/3/25
 * Author pengyi
 */
public class RunQuicklyClient {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    public int userId;
    private RedisService redisService;

    private GameBase.BaseConnection.Builder response;
    private MessageReceive messageReceive;

    RunQuicklyClient(RedisService redisService, MessageReceive messageReceive) {
        this.redisService = redisService;
        this.messageReceive = messageReceive;
        this.response = GameBase.BaseConnection.newBuilder();
    }

    public void close() {
        if (0 != userId) {
            synchronized (RunQuicklyTcpService.userClients) {
                if (RunQuicklyTcpService.userClients.containsKey(userId) && messageReceive == RunQuicklyTcpService.userClients.get(userId)) {
                    RunQuicklyTcpService.userClients.remove(userId);
                    if (redisService.exists("room" + messageReceive.roomNo)) {
                        while (!redisService.lock("lock_room" + messageReceive.roomNo)) {
                        }
                        Room room = JSON.parseObject(redisService.getCache("room" + messageReceive.roomNo), Room.class);
                        if (null != room) {
                            for (Seat seat : room.getSeats()) {
                                if (seat.getUserId() == userId) {
                                    seat.setRobot(true);
                                    break;
                                }
                            }
                            room.sendSeatInfo(response);

                            redisService.addCache("room" + messageReceive.roomNo, JSON.toJSONString(room));
                        }
                        redisService.unlock("lock_room" + messageReceive.roomNo);
                    }
                }
            }
        }
    }

    private void addSeat(Room room, RunQuickly.RunQuicklyGameInfo.Builder gameInfo) {
        for (Seat seat1 : room.getSeats()) {
            RunQuickly.RunQuicklySeatGameInfo.Builder seatResponse = RunQuickly.RunQuicklySeatGameInfo.newBuilder();
            seatResponse.setID(seat1.getUserId());
            if (null != seat1.getCards()) {
                if (seat1.getUserId() == userId) {
                    seatResponse.addAllCards(seat1.getCards());
                } else {
                    seatResponse.setCardsSize(seat1.getCards().size());
                }
            }
            seatResponse.setPassStatus(false);
            if (null != room.getHistoryList()) {
                int operationSeat = 0;
                for (Seat seat : room.getSeats()) {
                    if (seat.getSeatNo() == room.getOperationSeat()) {
                        operationSeat = seat.getUserId();
                        break;
                    }
                }
                for (int i = room.getHistoryList().size() - 1; i > room.getHistoryList().size() - room.getCount() && i > -1; i--) {
                    OperationHistory operationHistory = room.getHistoryList().get(i);
                    if (seat1.getUserId() == operationHistory.getUserId()) {
                        if (operationHistory.getUserId() == operationSeat) {
                            break;
                        }
                        if (0 == OperationHistoryType.PLAY_CARD.compareTo(operationHistory.getHistoryType())) {
                            seatResponse.addAllDesktopCards(operationHistory.getCards());
                        } else {
                            seatResponse.setPassStatus(true);
                        }
                    }
                }
            }
            gameInfo.addSeats(seatResponse.build());
        }
    }

    synchronized void receive(GameBase.BaseConnection request) {
        try {
            logger.info("接收" + userId + request.getOperationType().toString());
            switch (request.getOperationType()) {
                case HEARTBEAT:
                    messageReceive.send(response.setOperationType(GameBase.OperationType.HEARTBEAT).clearData().build(), userId);
                    break;
                case CONNECTION:
                    //加入玩家数据
                    if (redisService.exists("maintenance")) {
                        break;
                    }
                    GameBase.RoomCardIntoRequest intoRequest = GameBase.RoomCardIntoRequest.parseFrom(request.getData());
                    userId = intoRequest.getID();

                    messageReceive.roomNo = intoRequest.getRoomNo();
                    if (RunQuicklyTcpService.userClients.containsKey(userId) && RunQuicklyTcpService.userClients.get(userId) != messageReceive) {
                        RunQuicklyTcpService.userClients.get(userId).close();
                    }
                    synchronized (this) {
                        try {
                            wait(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    RunQuicklyTcpService.userClients.put(userId, messageReceive);
                    GameBase.RoomCardIntoResponse.Builder roomCardIntoResponseBuilder = GameBase.RoomCardIntoResponse.newBuilder();
                    roomCardIntoResponseBuilder.setGameType(GameBase.GameType.RUN_QUICKLY).setRoomNo(messageReceive.roomNo);
                    if (redisService.exists("room" + messageReceive.roomNo)) {
                        while (!redisService.lock("lock_room" + messageReceive.roomNo)) {
                        }
                        redisService.addCache("reconnect" + userId, "run_quickly," + messageReceive.roomNo);

                        Room room = JSON.parseObject(redisService.getCache("room" + messageReceive.roomNo), Room.class);
                        room.setRoomOwner(room.getRoomOwner());
                        roomCardIntoResponseBuilder.setStarted(0 != room.getGameStatus().compareTo(GameStatus.READYING) && 0 != room.getGameStatus().compareTo(GameStatus.WAITING));
                        if (0 == room.getGameStatus().compareTo(GameStatus.READYING) && redisService.exists("room_match" + messageReceive.roomNo)) {
                            int time = 8 - (int) ((new Date().getTime() - room.getStartDate().getTime()) / 1000);
                            roomCardIntoResponseBuilder.setReadyTimeCounter(time > 0 ? time : 0);
                        }
                        roomCardIntoResponseBuilder.setRoomOwner(room.getRoomOwner());
                        //房间是否已存在当前用户，存在则为重连
                        final boolean[] find = {false};
                        room.getSeats().stream().filter(seat -> seat.getUserId() == userId).forEach(seat -> {
                            find[0] = true;
                            seat.setRobot(false);
                        });
                        if (!find[0]) {
                            if (room.getCount() > room.getSeats().size()) {
                                JSONObject jsonObject = new JSONObject();
                                jsonObject.put("userId", userId);
                                ApiResponse<User> userResponse = JSON.parseObject(HttpUtil.urlConnectionByRsa(Constant.apiUrl + Constant.userInfoUrl, jsonObject.toJSONString()), new TypeReference<ApiResponse<User>>() {
                                });
                                if (0 == userResponse.getCode()) {
                                    room.addSeat(userResponse.getData(), 0);
                                }

                            } else {
                                roomCardIntoResponseBuilder.setError(GameBase.ErrorCode.COUNT_FULL);
                                response.setOperationType(GameBase.OperationType.ROOM_INFO).setData(roomCardIntoResponseBuilder.build().toByteString());
                                messageReceive.send(response.build(), userId);
                                redisService.unlock("lock_room" + messageReceive.roomNo);
                                break;
                            }
                        }

                        room.sendRoomInfo(roomCardIntoResponseBuilder, response, userId);
                        room.sendSeatInfo(response);

                        //是否竞技场
                        if (redisService.exists("room_match" + messageReceive.roomNo)) {
                            String matchNo = redisService.getCache("room_match" + messageReceive.roomNo);
                            if (redisService.exists("match_info" + matchNo)) {
                                while (!redisService.lock("lock_match_info" + matchNo)) {
                                }
                                MatchInfo matchInfo = JSON.parseObject(redisService.getCache("match_info" + matchNo), MatchInfo.class);
                                Arena arena = matchInfo.getArena();
                                GameBase.MatchInfo matchInfoResponse = GameBase.MatchInfo.newBuilder().setArenaType(arena.getArenaType())
                                        .setCount(arena.getCount()).setEntryFee(arena.getEntryFee()).setName(arena.getName())
                                        .setReward(arena.getReward()).build();
                                messageReceive.send(response.setOperationType(GameBase.OperationType.MATCH_INFO)
                                        .setData(matchInfoResponse.toByteString()).build(), userId);

                                int status = matchInfo.getStatus();
                                int round = 1;
                                if (status == 3) {
                                    round = 2;
                                }
                                if (status == 4) {
                                    round = 3;
                                }
                                if (status > 2) {
                                    status = status == 5 ? 3 : 2;
                                }
                                GameBase.MatchData matchData = GameBase.MatchData.newBuilder()
                                        .setCurrentCount(matchInfo.getMatchUsers().size())
                                        .setStatus(status).setRound(round).build();
                                messageReceive.send(response.setOperationType(GameBase.OperationType.MATCH_DATA)
                                        .setData(matchData.toByteString()).build(), userId);

                                if (!matchInfo.isStart()) {
                                    List<Integer> roomNos = matchInfo.getRooms();
                                    for (Integer roomNo : roomNos) {
                                        new ReadyTimeout(roomNo, redisService, 0).start();
                                    }
                                    matchInfo.setStart(true);
                                    new MatchScoreTimeout(Integer.valueOf(matchNo), redisService).start();
                                }
                                redisService.addCache("match_info" + matchNo, JSON.toJSONString(matchInfo));
                                redisService.unlock("lock_match_info" + matchNo);
                            }
                        }

                        if (0 != room.getGameStatus().compareTo(GameStatus.WAITING)) {
                            RunQuickly.RunQuicklyGameInfo.Builder gameInfo = RunQuickly.RunQuicklyGameInfo.newBuilder();
                            for (int i = room.getHistoryList().size() - 1; i > room.getHistoryList().size() - room.getCount() - 1 && i > -1; i--) {
                                OperationHistory operationHistory = room.getHistoryList().get(i);
                                if (0 == OperationHistoryType.PLAY_CARD.compareTo(operationHistory.getHistoryType())) {
                                    gameInfo.setLastPlayCardUser(operationHistory.getUserId());
                                    break;
                                }
                            }
                            Seat operationSeat = null;
                            for (Seat seat : room.getSeats()) {
                                if (seat.getSeatNo() == room.getOperationSeat()) {
                                    operationSeat = seat;
                                }
                            }
                            gameInfo.setGameCount(room.getGameCount());
                            gameInfo.setGameTimes(room.getGameTimes());
                            addSeat(room, gameInfo);
                            response.setOperationType(GameBase.OperationType.GAME_INFO).setData(gameInfo.build().toByteString());
                            messageReceive.send(response.build(), userId);

                            if (null != operationSeat) {
                                //检测是否该当前玩家出牌
                                int time = 0;
                                if (redisService.exists("room_match" + messageReceive.roomNo)) {
                                    if (0 == room.getHistoryList().size()) {
                                        time = 8 - (int) ((new Date().getTime() - room.getStartDate().getTime() / 1000));
                                    } else {
                                        time = 8 - (int) ((new Date().getTime() - room.getHistoryList().get(room.getHistoryList().size() - 1).getDate().getTime() / 1000));
                                    }
                                }
                                GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder().setTimeCounter(time > 0 ? time : 0).setID(operationSeat.getUserId()).build();
                                response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                                messageReceive.send(response.build(), userId);
                            }
                        }
                        redisService.addCache("room" + messageReceive.roomNo, JSON.toJSONString(room));
                        redisService.unlock("lock_room" + messageReceive.roomNo);

                        if (redisService.exists("dissolve" + messageReceive.roomNo)) {

                            String dissolveStatus = redisService.getCache("dissolve" + messageReceive.roomNo);
                            String[] users = dissolveStatus.split("-");
                            String user = "0";
                            for (String s : users) {
                                if (s.startsWith("1")) {
                                    user = s.substring(1);
                                    break;
                                }
                            }

                            GameBase.DissolveApply dissolveApply = GameBase.DissolveApply.newBuilder()
                                    .setError(GameBase.ErrorCode.SUCCESS).setUserId(Integer.valueOf(user)).build();
                            response.setOperationType(GameBase.OperationType.DISSOLVE).setData(dissolveApply.toByteString());
                            if (RunQuicklyTcpService.userClients.containsKey(userId)) {
                                messageReceive.send(response.build(), userId);
                            }

                            GameBase.DissolveReplyResponse.Builder replyResponse = GameBase.DissolveReplyResponse.newBuilder();
                            for (Seat seat : room.getSeats()) {
                                if (dissolveStatus.contains("-1" + seat.getUserId())) {
                                    replyResponse.addDissolve(GameBase.Dissolve.newBuilder().setUserId(seat.getUserId()).setAgree(true));
                                } else if (dissolveStatus.contains("-2" + seat.getUserId())) {
                                    replyResponse.addDissolve(GameBase.Dissolve.newBuilder().setUserId(seat.getUserId()).setAgree(false));
                                }
                            }
                            response.setOperationType(GameBase.OperationType.DISSOLVE_REPLY).setData(replyResponse.build().toByteString());
                            messageReceive.send(response.build(), userId);
                        }
                    } else if (redisService.exists("match_info" + messageReceive.roomNo)) {
                        while (!redisService.lock("lock_match_info" + messageReceive.roomNo)) {
                        }
                        MatchInfo matchInfo = JSON.parseObject(redisService.getCache("match_info" + messageReceive.roomNo), MatchInfo.class);
                        int score = 0;
                        for (MatchUser m : matchInfo.getMatchUsers()) {
                            if (m.getUserId() == userId) {
                                score = m.getScore();
                                break;
                            }
                        }
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("userId", userId);
                        ApiResponse<User> userResponse = JSON.parseObject(HttpUtil.urlConnectionByRsa(Constant.apiUrl + Constant.userInfoUrl, jsonObject.toJSONString()), new TypeReference<ApiResponse<User>>() {
                        });
                        messageReceive.send(response.setOperationType(GameBase.OperationType.ROOM_INFO).clearData().build(), userId);
                        if (0 == userResponse.getCode()) {
                            GameBase.RoomSeatsInfo.Builder roomSeatsInfo = GameBase.RoomSeatsInfo.newBuilder();
                            GameBase.SeatResponse.Builder seatResponse = GameBase.SeatResponse.newBuilder();
                            seatResponse.setSeatNo(1);
                            seatResponse.setID(userId);
                            seatResponse.setScore(score);
                            seatResponse.setReady(false);
                            seatResponse.setIp(userResponse.getData().getLastLoginIp());
                            seatResponse.setGameCount(userResponse.getData().getGameCount());
                            seatResponse.setNickname(userResponse.getData().getNickname());
                            seatResponse.setHead(userResponse.getData().getHead());
                            seatResponse.setSex(userResponse.getData().getSex().equals("MAN"));
                            seatResponse.setOffline(false);seatResponse.setIsRobot(false);
                            roomSeatsInfo.addSeats(seatResponse.build());
                            messageReceive.send(response.setOperationType(GameBase.OperationType.SEAT_INFO).setData(roomSeatsInfo.build().toByteString()).build(), userId);
                        }
                        redisService.unlock("lock_match_info" + messageReceive.roomNo);
                    } else {
                        roomCardIntoResponseBuilder.setError(GameBase.ErrorCode.ROOM_NOT_EXIST);
                        response.setOperationType(GameBase.OperationType.ROOM_INFO).setData(roomCardIntoResponseBuilder.build().toByteString());
                        messageReceive.send(response.build(), userId);
                    }
                    break;
                case READY:
                    if (redisService.exists("room" + messageReceive.roomNo)) {
                        while (!redisService.lock("lock_room" + messageReceive.roomNo)) {
                        }
                        Room room = JSON.parseObject(redisService.getCache("room" + messageReceive.roomNo), Room.class);
                        if (0 != room.getGameStatus().compareTo(GameStatus.PLAYING)) {
                            room.getSeats().stream().filter(seat -> seat.getUserId() == userId && !seat.isReady()).forEach(seat -> {
                                seat.setReady(true);
                                response.setOperationType(GameBase.OperationType.READY).setData(GameBase.ReadyResponse.newBuilder().setID(seat.getUserId()).build().toByteString());
                                room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId())).forEach(seat1 ->
                                        RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));
                            });
                            boolean allReady = true;
                            for (Seat seat : room.getSeats()) {
                                if (!seat.isReady()) {
                                    allReady = false;
                                    break;
                                }
                            }
                            if (allReady && room.getCount() == room.getSeats().size()) {
                                room.start(response, redisService);
                            }
                        }
                        redisService.addCache("room" + messageReceive.roomNo, JSON.toJSONString(room));
                        redisService.unlock("lock_room" + messageReceive.roomNo);
                    } else {
                        logger.warn("房间不存在");
                    }
                    break;
                case COMPLETED:
                    if (redisService.exists("room" + messageReceive.roomNo)) {
                        while (!redisService.lock("lock_room" + messageReceive.roomNo)) {
                        }
                        Room room = JSON.parseObject(redisService.getCache("room" + messageReceive.roomNo), Room.class);
                        room.getSeats().stream().filter(seat -> seat.getUserId() == userId && !seat.isCompleted())
                                .forEach(seat -> seat.setCompleted(true));
                        boolean allCompleted = true;
                        for (Seat seat : room.getSeats()) {
                            if (!seat.isCompleted()) {
                                allCompleted = false;
                                break;
                            }
                        }
                        if (allCompleted) {
                            //TODO 出牌超时
                        }
                        redisService.addCache("room" + messageReceive.roomNo, JSON.toJSONString(room));
                        redisService.unlock("lock_room" + messageReceive.roomNo);
                    } else {
                        logger.warn("房间不存在");
                    }
                    break;
                case ACTION:
                    GameBase.BaseAction actionRequest = GameBase.BaseAction.parseFrom(request.getData());
                    logger.info("runquickly 接收 " + actionRequest.getOperationId() + userId);
                    GameBase.BaseAction.Builder actionResponse = GameBase.BaseAction.newBuilder();
                    actionResponse.setID(userId);
                    if (redisService.exists("room" + messageReceive.roomNo)) {
                        while (!redisService.lock("lock_room" + messageReceive.roomNo)) {
                        }
                        Room room = JSON.parseObject(redisService.getCache("room" + messageReceive.roomNo), Room.class);
                        switch (actionRequest.getOperationId()) {
                            case PLAY_CARD:
                                RunQuickly.RunQuicklyPlayCard playCardRequest = RunQuickly.RunQuicklyPlayCard.parseFrom(actionRequest.getData());
                                List<Integer> cards = playCardRequest.getCardList();
                                room.playCard(userId, cards, response, redisService, actionResponse);
                                break;
                            case PASS:
                                room.pass(userId, actionResponse, response, redisService);
                                break;
                        }
                        if (null != room.getRoomNo()) {
                            redisService.addCache("room" + messageReceive.roomNo, JSON.toJSONString(room));
                        }
                        redisService.unlock("lock_room" + messageReceive.roomNo);
                    } else {
                        logger.warn("房间不存在");
                    }
                    break;
                case REPLAY:
                    RunQuickly.RunQuicklyReplayResponse.Builder replayResponse = RunQuickly.RunQuicklyReplayResponse.newBuilder();
                    if (redisService.exists("room" + messageReceive.roomNo)) {
                        while (!redisService.lock("lock_room" + messageReceive.roomNo)) {
                        }
                        Room room = JSON.parseObject(redisService.getCache("room" + messageReceive.roomNo), Room.class);
                        for (OperationHistory operationHistory : room.getHistoryList()) {
                            GameBase.OperationHistory.Builder builder = GameBase.OperationHistory.newBuilder();
                            builder.setID(operationHistory.getUserId());
                            builder.addAllCard(operationHistory.getCards());
                            builder.setOperationId(GameBase.ActionId.PLAY_CARD);
                            replayResponse.addHistory(builder);
                        }
                        response.setOperationType(GameBase.OperationType.REPLAY).setData(replayResponse.build().toByteString());
                        messageReceive.send(response.build(), userId);
                        redisService.unlock("lock_room" + messageReceive.roomNo);
                    }
                    break;
                case EXIT:
                    break;
                case DISSOLVE:
                    if (redisService.exists("room" + messageReceive.roomNo) && !redisService.exists("room_match" + messageReceive.roomNo)) {
                        while (!redisService.lock("lock_room" + messageReceive.roomNo)) {
                        }
                        if (!redisService.exists("dissolve" + messageReceive.roomNo) && !redisService.exists("delete_dissolve" + messageReceive.roomNo)) {
                            GameBase.DissolveApply dissolveApply = GameBase.DissolveApply.newBuilder()
                                    .setError(GameBase.ErrorCode.SUCCESS).setUserId(userId).build();
                            Room room = JSON.parseObject(redisService.getCache("room" + messageReceive.roomNo), Room.class);
                            redisService.addCache("dissolve" + messageReceive.roomNo, "-1" + userId);
                            response.setOperationType(GameBase.OperationType.DISSOLVE).setData(dissolveApply.toByteString());
                            for (Seat seat : room.getSeats()) {
                                if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                    messageReceive.send(response.build(), seat.getUserId());
                                }
                            }

                            GameBase.DissolveReplyResponse.Builder replyResponse = GameBase.DissolveReplyResponse.newBuilder();
                            replyResponse.addDissolve(GameBase.Dissolve.newBuilder().setUserId(userId).setAgree(true));
                            response.setOperationType(GameBase.OperationType.DISSOLVE_REPLY).setData(dissolveApply.toByteString());
                            for (Seat seat : room.getSeats()) {
                                if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                    messageReceive.send(response.build(), seat.getUserId());
                                }
                            }

                            if (1 == room.getSeats().size()) {
                                GameBase.DissolveConfirm dissolveConfirm = GameBase.DissolveConfirm.newBuilder().setDissolved(true).build();
                                response.setOperationType(GameBase.OperationType.DISSOLVE_CONFIRM).setData(dissolveConfirm.toByteString());
                                for (Seat seat : room.getSeats()) {
                                    if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                        messageReceive.send(response.build(), seat.getUserId());
                                    }
                                }
                                room.roomOver(response, redisService);
                            } else {
                                new DissolveTimeout(Integer.valueOf(messageReceive.roomNo), redisService).start();
                            }
                        } else {
                            response.setOperationType(GameBase.OperationType.DISSOLVE).setData(GameBase.DissolveApply.newBuilder()
                                    .setError(GameBase.ErrorCode.AREADY_DISSOLVE).build().toByteString());
                            messageReceive.send(response.build(), userId);
                        }
                        redisService.unlock("lock_room" + messageReceive.roomNo);
                    }
                    break;
                case DISSOLVE_REPLY:
                    GameBase.DissolveReplyRequest dissolveReply = GameBase.DissolveReplyRequest.parseFrom(request.getData());
                    if (redisService.exists("room" + messageReceive.roomNo)) {
                        while (!redisService.lock("lock_room" + messageReceive.roomNo)) {
                        }
                        while (!redisService.lock("lock_dissolve" + messageReceive.roomNo)) {
                        }
                        if (redisService.exists("dissolve" + messageReceive.roomNo)) {
                            Room room = JSON.parseObject(redisService.getCache("room" + messageReceive.roomNo), Room.class);
                            String dissolveStatus = redisService.getCache("dissolve" + messageReceive.roomNo);
                            if (dissolveReply.getAgree()) {
                                dissolveStatus = dissolveStatus + "-1" + userId;
                            } else {
                                dissolveStatus = dissolveStatus + "-2" + userId;
                            }
                            redisService.addCache("dissolve" + messageReceive.roomNo, dissolveStatus);
                            int disagree = 0;
                            int agree = 0;
                            GameBase.DissolveReplyResponse.Builder replyResponse = GameBase.DissolveReplyResponse.newBuilder();
                            for (Seat seat : room.getSeats()) {
                                if (dissolveStatus.contains("-1" + seat.getUserId())) {
                                    replyResponse.addDissolve(GameBase.Dissolve.newBuilder().setUserId(userId).setAgree(true));
                                    agree++;
                                } else if (dissolveStatus.contains("-2" + seat.getUserId())) {
                                    replyResponse.addDissolve(GameBase.Dissolve.newBuilder().setUserId(userId).setAgree(false));
                                    disagree++;
                                }
                            }
                            response.setOperationType(GameBase.OperationType.DISSOLVE_REPLY).setData(replyResponse.build().toByteString());
                            for (Seat seat : room.getSeats()) {
                                if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                    messageReceive.send(response.build(), seat.getUserId());
                                }
                            }

                            if (disagree >= room.getSeats().size() / 2) {
                                GameBase.DissolveConfirm dissolveConfirm = GameBase.DissolveConfirm.newBuilder().setDissolved(false).build();
                                response.setOperationType(GameBase.OperationType.DISSOLVE_CONFIRM).setData(dissolveConfirm.toByteString());
                                for (Seat seat : room.getSeats()) {
                                    if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                        messageReceive.send(response.build(), seat.getUserId());
                                    }
                                }
                                redisService.delete("dissolve" + messageReceive.roomNo);
                                redisService.addCache("delete_dissolve" + messageReceive.roomNo, "", 60);
                            } else if (agree > room.getSeats().size() / 2) {
                                GameBase.DissolveConfirm dissolveConfirm = GameBase.DissolveConfirm.newBuilder().setDissolved(true).build();
                                response.setOperationType(GameBase.OperationType.DISSOLVE_CONFIRM).setData(dissolveConfirm.toByteString());
                                for (Seat seat : room.getSeats()) {
                                    if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                        messageReceive.send(response.build(), seat.getUserId());
                                    }
                                }
                                room.roomOver(response, redisService);
                                redisService.delete("dissolve" + messageReceive.roomNo);
                            }
                        }
                        redisService.unlock("lock_dissolve" + messageReceive.roomNo);
                        redisService.unlock("lock_room" + messageReceive.roomNo);
                    }
                    break;
                case MESSAGE:
                    if (redisService.exists("room" + messageReceive.roomNo)) {
                        while (!redisService.lock("lock_room" + messageReceive.roomNo)) {
                        }
                        Room room = JSON.parseObject(redisService.getCache("room" + messageReceive.roomNo), Room.class);
                        GameBase.Message message = GameBase.Message.parseFrom(request.getData());

                        GameBase.Message messageResponse = GameBase.Message.newBuilder().setUserId(userId)
                                .setMessageType(message.getMessageType()).setContent(message.getContent()).build();

                        for (Seat seat : room.getSeats()) {
                            if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                messageReceive.send(response.setOperationType(GameBase.OperationType.MESSAGE)
                                        .setData(messageResponse.toByteString()).build(), seat.getUserId());
                            }
                        }
                        redisService.unlock("lock_room" + messageReceive.roomNo);
                    }
                    break;
                case INTERACTION:
                    if (redisService.exists("room" + messageReceive.roomNo)) {
                        while (!redisService.lock("lock_room" + messageReceive.roomNo)) {
                        }
                        Room room = JSON.parseObject(redisService.getCache("room" + messageReceive.roomNo), Room.class);
                        GameBase.AppointInteraction appointInteraction = GameBase.AppointInteraction.parseFrom(request.getData());

                        GameBase.AppointInteraction appointInteractionResponse = GameBase.AppointInteraction.newBuilder().setUserId(userId)
                                .setToUserId(appointInteraction.getToUserId()).setContentIndex(appointInteraction.getContentIndex()).build();
                        for (Seat seat : room.getSeats()) {
                            if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                messageReceive.send(response.setOperationType(GameBase.OperationType.MESSAGE)
                                        .setData(appointInteractionResponse.toByteString()).build(), seat.getUserId());
                            }
                        }
                        redisService.unlock("lock_room" + messageReceive.roomNo);
                    }
                    break;
                case LOGGER:
                    GameBase.LoggerRequest loggerRequest = GameBase.LoggerRequest.parseFrom(request.getData());
                    LoggerUtil.logger(userId + "----" + loggerRequest.getLogger());
                    break;
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }
}