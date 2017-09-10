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
    private int userId;
    private RedisService redisService;
    private String roomNo;

    private GameBase.BaseConnection.Builder response;
    private MessageReceive messageReceive;

    RunQuicklyClient(RedisService redisService, MessageReceive messageReceive) {
        this.redisService = redisService;
        this.messageReceive = messageReceive;
        this.response = GameBase.BaseConnection.newBuilder();
    }

    public void close() {
        if (0 != userId) {
            if (redisService.exists("room" + roomNo)) {
                while (!redisService.lock("lock_room" + roomNo)) {
                }
                Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                if (null != room) {
                    for (Seat seat : room.getSeats()) {
                        if (seat.getUserId() == userId) {
                            seat.setRobot(true);
                            break;
                        }
                    }
                    room.sendSeatInfo(response);

                    redisService.addCache("room" + roomNo, JSON.toJSONString(room));
                }
                redisService.unlock("lock_room" + roomNo);
            }
        }
    }

    private void addSeat(Room room, RunQuickly.RunQuicklyGameInfo.Builder gameInfo) {
        for (Seat seat1 : room.getSeats()) {
            RunQuickly.RunQuicklySeatGameInfo.Builder seatResponse = RunQuickly.RunQuicklySeatGameInfo.newBuilder();
            seatResponse.setID(seat1.getUserId());
            seatResponse.setIsRobot(seat1.isRobot());
            if (null != seat1.getCards()) {
                if (seat1.getUserId() == userId) {
                    seatResponse.addAllCards(seat1.getCards());
                } else {
                    seatResponse.setCardsSize(seat1.getCards().size());
                }
            }
            seatResponse.setPassStatus(false);
            if (null != room.getHistoryList()) {
                for (int i = room.getHistoryList().size() - 1; i > room.getHistoryList().size() - room.getCount() && i > -1; i--) {
                    OperationHistory operationHistory = room.getHistoryList().get(i);
                    if (seat1.getUserId() == operationHistory.getUserId()) {
                        if (0 == OperationHistoryType.PLAY_CARD.compareTo(operationHistory.getHistoryType())) {
                            seatResponse.addAllDesktopCards(operationHistory.getCards());
                        } else {
                            seatResponse.setPassStatus(true);
                        }
                        break;
                    }
                }
            }
            gameInfo.addSeats(seatResponse.build());
        }
    }

    synchronized void receive(GameBase.BaseConnection request) {
        try {
            switch (request.getOperationType()) {
                case CONNECTION:
                    //加入玩家数据
                    if (redisService.exists("maintenance")) {
                        break;
                    }
                    GameBase.RoomCardIntoRequest intoRequest = GameBase.RoomCardIntoRequest.parseFrom(request.getData());
                    userId = intoRequest.getID();

                    roomNo = intoRequest.getRoomNo();
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
                    roomCardIntoResponseBuilder.setGameType(GameBase.GameType.RUN_QUICKLY).setRoomNo(roomNo);
                    if (redisService.exists("room" + roomNo)) {
                        while (!redisService.lock("lock_room" + roomNo)) {
                        }
                        redisService.addCache("reconnect" + userId, "run_quickly," + roomNo);

                        //是否竞技场
                        if (redisService.exists("room_match" + roomNo)) {
                            String matchNo = redisService.getCache("room_match" + roomNo);
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

                                GameBase.MatchData matchData = GameBase.MatchData.newBuilder()
                                        .setCurrentCount(matchInfo.getMatchUsers().size())
                                        .setStartDate(matchInfo.getStartDate().getTime())
                                        .setStatus(matchInfo.getStatus()).build();
                                messageReceive.send(response.setOperationType(GameBase.OperationType.MATCH_DATA)
                                        .setData(matchData.toByteString()).build(), userId);

                                if (!matchInfo.isStart()) {
                                    List<Integer> roomNos = matchInfo.getRooms();
                                    for (Integer roomNo1 : roomNos) {
                                        new ReadyTimeout(roomNo1, redisService).start();
                                    }
                                }
                                matchInfo.setStart(true);
                                redisService.addCache("match_info" + matchNo, JSON.toJSONString(matchInfo));
                                redisService.unlock("lock_match_info" + matchNo);
                            }
                        }

                        Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                        room.setRoomOwner(room.getRoomOwner());
                        roomCardIntoResponseBuilder.setStarted(0 != room.getGameStatus().compareTo(GameStatus.READYING) && 0 != room.getGameStatus().compareTo(GameStatus.WAITING));
                        if (0 == room.getGameStatus().compareTo(GameStatus.READYING) && redisService.exists("room_match" + roomNo)) {
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
                                redisService.unlock("lock_room" + roomNo);
                                break;
                            }
                        }

                        room.sendRoomInfo(roomCardIntoResponseBuilder, response, userId);
                        room.sendSeatInfo(response);
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
                                if (redisService.exists("room_match" + roomNo)) {
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
                        redisService.addCache("room" + roomNo, JSON.toJSONString(room));
                        redisService.unlock("lock_room" + roomNo);
                    } else if (redisService.exists("match_info" + roomNo)) {
                        while (!redisService.lock("lock_match_info" + roomNo)) {
                        }
                        MatchInfo matchInfo = JSON.parseObject(redisService.getCache("match_info" + roomNo), MatchInfo.class);
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
                            seatResponse.setSeatNo(0);
                            seatResponse.setID(userId);
                            seatResponse.setScore(score);
                            seatResponse.setReady(false);
                            seatResponse.setAreaString(userResponse.getData().getArea());
                            seatResponse.setNickname(userResponse.getData().getNickname());
                            seatResponse.setHead(userResponse.getData().getHead());
                            seatResponse.setSex(userResponse.getData().getSex().equals("MAN"));
                            seatResponse.setOffline(false);
                            roomSeatsInfo.addSeats(seatResponse.build());
                            messageReceive.send(response.setOperationType(GameBase.OperationType.SEAT_INFO).setData(roomSeatsInfo.build().toByteString()).build(), userId);
                        }
                        redisService.unlock("lock_match_info" + roomNo);
                    } else {
                        roomCardIntoResponseBuilder.setError(GameBase.ErrorCode.ROOM_NOT_EXIST);
                        response.setOperationType(GameBase.OperationType.ROOM_INFO).setData(roomCardIntoResponseBuilder.build().toByteString());
                        messageReceive.send(response.build(), userId);
                    }
                    break;
                case READY:
                    if (redisService.exists("room" + roomNo)) {
                        while (!redisService.lock("lock_room" + roomNo)) {
                        }
                        Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
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
                        redisService.addCache("room" + roomNo, JSON.toJSONString(room));
                        redisService.unlock("lock_room" + roomNo);
                    } else {
                        logger.warn("房间不存在");
                    }
                    break;
                case COMPLETED:
                    if (redisService.exists("room" + roomNo)) {
                        while (!redisService.lock("lock_room" + roomNo)) {
                        }
                        Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
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
                        redisService.addCache("room" + roomNo, JSON.toJSONString(room));
                        redisService.unlock("lock_room" + roomNo);
                    } else {
                        logger.warn("房间不存在");
                    }
                    break;
                case ACTION:
                    GameBase.BaseAction actionRequest = GameBase.BaseAction.parseFrom(request.getData());
                    GameBase.BaseAction.Builder actionResponse = GameBase.BaseAction.newBuilder();
                    actionResponse.setID(userId);
                    if (redisService.exists("room" + roomNo)) {
                        while (!redisService.lock("lock_room" + roomNo)) {
                        }
                        Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
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
                            redisService.addCache("room" + roomNo, JSON.toJSONString(room));
                        }
                        redisService.unlock("lock_room" + roomNo);
                    } else {
                        logger.warn("房间不存在");
                    }
                    break;
                case REPLAY:
                    RunQuickly.RunQuicklyReplayResponse.Builder replayResponse = RunQuickly.RunQuicklyReplayResponse.newBuilder();
                    if (redisService.exists("room" + roomNo)) {
                        while (!redisService.lock("lock_room" + roomNo)) {
                        }
                        Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                        for (OperationHistory operationHistory : room.getHistoryList()) {
                            GameBase.OperationHistory.Builder builder = GameBase.OperationHistory.newBuilder();
                            builder.setID(operationHistory.getUserId());
                            builder.addAllCard(operationHistory.getCards());
                            builder.setOperationId(GameBase.ActionId.PLAY_CARD);
                            replayResponse.addHistory(builder);
                        }
                        response.setOperationType(GameBase.OperationType.REPLAY).setData(replayResponse.build().toByteString());
                        messageReceive.send(response.build(), userId);
                        redisService.unlock("lock_room" + roomNo);
                    }
                    break;
                case EXIT:
                    break;
                case DISSOLVE:
                    if (redisService.exists("room" + roomNo)) {
                        while (!redisService.lock("lock_room" + roomNo)) {
                        }
                        redisService.addCache("dissolve" + roomNo, "-" + userId);
                        GameBase.DissolveApply dissolveApply = GameBase.DissolveApply.newBuilder().setUserId(userId).build();
                        Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                        response.setOperationType(GameBase.OperationType.DISSOLVE).setData(dissolveApply.toByteString());
                        for (Seat seat : room.getSeats()) {
                            if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                messageReceive.send(response.build(), seat.getUserId());
                            }
                        }
                        redisService.unlock("lock_room" + roomNo);
                    }
                    break;
                case DISSOLVE_REPLY:
                    GameBase.DissolveReply dissolveReply = GameBase.DissolveReply.parseFrom(request.getData());
                    if (redisService.exists("room" + roomNo)) {
                        while (!redisService.lock("lock_room" + roomNo)) {
                        }
                        while (!redisService.lock("lock_dissolve" + roomNo)) {
                        }
                        if (redisService.exists("dissolve" + roomNo)) {
                            Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                            response.setOperationType(GameBase.OperationType.DISSOLVE_REPLY).setData(dissolveReply.toBuilder().setUserId(userId).build().toByteString());
                            boolean confirm = true;
                            String dissolveStatus = redisService.getCache("dissolve" + roomNo);
                            for (Seat seat : room.getSeats()) {
                                if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                    messageReceive.send(response.build(), seat.getUserId());
                                }
                                if (!dissolveStatus.contains("-" + seat.getUserId()) && seat.getUserId() != userId) {
                                    confirm = false;
                                }
                            }
                            if (!dissolveReply.getAgree()) {
                                GameBase.DissolveConfirm dissolveConfirm = GameBase.DissolveConfirm.newBuilder().setDissolved(false).build();
                                response.setOperationType(GameBase.OperationType.DISSOLVE_CONFIRM).setData(dissolveConfirm.toByteString());
                                for (Seat seat : room.getSeats()) {
                                    if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                        messageReceive.send(response.build(), seat.getUserId());
                                    }
                                }
                                redisService.delete("dissolve" + roomNo);
                            } else if (confirm) {
                                GameBase.DissolveConfirm dissolveConfirm = GameBase.DissolveConfirm.newBuilder().setDissolved(true).build();
                                response.setOperationType(GameBase.OperationType.DISSOLVE_CONFIRM).setData(dissolveConfirm.toByteString());
                                for (Seat seat : room.getSeats()) {
                                    if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                        messageReceive.send(response.build(), seat.getUserId());
                                    }
                                }
                                room.roomOver(response, redisService);
                                redisService.delete("dissolve" + roomNo);
                            } else {
                                redisService.addCache("dissolve" + roomNo, dissolveStatus + "-" + userId);
                            }
                        }
                        redisService.unlock("lock_dissolve" + roomNo);
                        redisService.unlock("lock_room" + roomNo);
                    }
                    break;
                case MESSAGE:
                    if (redisService.exists("room" + roomNo)) {
                        while (!redisService.lock("lock_room" + roomNo)) {
                        }
                        Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                        GameBase.Message message = GameBase.Message.parseFrom(request.getData());

                        GameBase.Message messageResponse = GameBase.Message.newBuilder().setUserId(userId)
                                .setMessageType(message.getMessageType()).setContent(message.getContent()).build();

                        for (Seat seat : room.getSeats()) {
                            if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                messageReceive.send(response.setOperationType(GameBase.OperationType.MESSAGE)
                                        .setData(messageResponse.toByteString()).build(), seat.getUserId());
                            }
                        }
                        redisService.unlock("lock_room" + roomNo);
                    }
                    break;
                case INTERACTION:
                    if (redisService.exists("room" + roomNo)) {
                        while (!redisService.lock("lock_room" + roomNo)) {
                        }
                        Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                        GameBase.AppointInteraction appointInteraction = GameBase.AppointInteraction.parseFrom(request.getData());

                        GameBase.AppointInteraction appointInteractionResponse = GameBase.AppointInteraction.newBuilder().setUserId(userId)
                                .setToUserId(appointInteraction.getToUserId()).setContentIndex(appointInteraction.getContentIndex()).build();
                        for (Seat seat : room.getSeats()) {
                            if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                messageReceive.send(response.setOperationType(GameBase.OperationType.MESSAGE)
                                        .setData(appointInteractionResponse.toByteString()).build(), seat.getUserId());
                            }
                        }
                        redisService.unlock("lock_room" + roomNo);
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