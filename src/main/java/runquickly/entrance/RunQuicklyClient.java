package runquickly.entrance;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import runquickly.mode.*;
import runquickly.redis.RedisService;
import runquickly.utils.HttpUtil;
import runquickly.utils.LoggerUtil;

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
                synchronized (this) {
                    try {
                        wait(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                if (null != room) {
                    for (Seat seat : room.getSeats()) {
                        if (seat.getUserId() == userId) {
                            seat.setRobot(true);
                            break;
                        }
                    }

                    RunQuickly.RunQuicklyGameInfo.Builder gameInfo = RunQuickly.RunQuicklyGameInfo.newBuilder().setGameStatus(GameBase.GameStatus.PLAYING);
                    gameInfo.setGameCount(room.getGameCount());
                    gameInfo.setGameTimes(room.getGameTimes());
                    addSeat(room, gameInfo);
                    response.setOperationType(GameBase.OperationType.GAME_INFO).setData(gameInfo.build().toByteString());
                    messageReceive.send(response.build(), userId);

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
                        Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                        //房间是否已存在当前用户，存在则为重连
                        final boolean[] find = {false};
                        room.getSeats().stream().filter(seat -> seat.getUserId() == userId).forEach(seat -> find[0] = true);
                        if (!find[0]) {
                            if (room.getCount() > room.getSeats().size()) {
                                JSONObject jsonObject = new JSONObject();
                                jsonObject.put("userId", userId);
                                ApiResponse<User> userResponse = JSON.parseObject(HttpUtil.urlConnectionByRsa("http://127.0.0.1:9999/api/user/info", jsonObject.toJSONString()), new TypeReference<ApiResponse<User>>() {
                                });
                                if (0 == userResponse.getCode()) {
                                    room.addSeat(userResponse.getData());
                                }

                            } else {
                                roomCardIntoResponseBuilder.setError(GameBase.ErrorCode.COUNT_FULL);
                                response.setOperationType(GameBase.OperationType.ROOM_INFO).setData(roomCardIntoResponseBuilder.build().toByteString());
                                messageReceive.send(response.build(), userId);
                                redisService.unlock("lock_room" + roomNo);
                                break;
                            }
                        }

                        RunQuickly.RunQuicklyIntoResponse intoResponse = RunQuickly.RunQuicklyIntoResponse.newBuilder()
                                .setBaseScore(room.getBaseScore()).setCount(room.getCount()).setGameTimes(room.getGameTimes())
                                .setGameRules(room.getGameRules()).build();
                        roomCardIntoResponseBuilder.setError(GameBase.ErrorCode.SUCCESS).setData(intoResponse.toByteString());
                        response.setOperationType(GameBase.OperationType.ROOM_INFO).setData(roomCardIntoResponseBuilder.build().toByteString());
                        messageReceive.send(response.build(), userId);

                        GameBase.RoomSeatsInfo.Builder roomSeatsInfo = GameBase.RoomSeatsInfo.newBuilder();
                        for (Seat seat1 : room.getSeats()) {
                            GameBase.SeatResponse.Builder seatResponse = GameBase.SeatResponse.newBuilder();
                            seatResponse.setSeatNo(seat1.getSeatNo());
                            seatResponse.setID(seat1.getUserId());
                            seatResponse.setScore(seat1.getScore());
                            seatResponse.setReady(seat1.isReady());
                            seatResponse.setAreaString(seat1.getAreaString());
                            seatResponse.setNickname(seat1.getNickname());
                            seatResponse.setHead(seat1.getHead());
                            seatResponse.setSex(seat1.isSex());
                            seatResponse.setOffline(seat1.isRobot());
                            roomSeatsInfo.addSeats(seatResponse.build());
                        }
                        response.setOperationType(GameBase.OperationType.SEAT_INFO).setData(roomSeatsInfo.build().toByteString());
                        for (Seat seat : room.getSeats()) {
                            if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                messageReceive.send(response.build(), seat.getUserId());
                            }
                        }

                        if (0 != room.getGameStatus().compareTo(GameStatus.WAITING)) {
                            RunQuickly.RunQuicklyGameInfo.Builder gameInfo = RunQuickly.RunQuicklyGameInfo.newBuilder();
                            Seat operationSeat = null;
                            for (Seat seat : room.getSeats()) {
                                if (seat.getSeatNo() == room.getOperationSeat()) {
                                    operationSeat = seat;
                                }
                            }
                            gameInfo.setGameCount(room.getGameCount());
                            gameInfo.setGameTimes(room.getGameTimes());
                            if (0 == room.getGameStatus().compareTo(GameStatus.READYING)) {
                                gameInfo.setGameStatus(GameBase.GameStatus.READYING);
                            } else {
                                gameInfo.setGameStatus(GameBase.GameStatus.PLAYING);
                            }
                            addSeat(room, gameInfo);
                            response.setOperationType(GameBase.OperationType.GAME_INFO).setData(gameInfo.build().toByteString());
                            messageReceive.send(response.build(), userId);

                            if (null != operationSeat) {
                                //才开始的时候检测是否该当前玩家出牌
                                GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder().setID(operationSeat.getUserId()).build();
                                response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                                messageReceive.send(response.build(), userId);
                            }
                        }
                        redisService.addCache("room" + roomNo, JSON.toJSONString(room));
                        redisService.unlock("lock_room" + roomNo);
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
                                room.setGameCount(room.getGameCount() + 1);
                                room.setGameStatus(GameStatus.PLAYING);
                                room.dealCard();
                                RunQuickly.RunQuicklyStartResponse.Builder dealCard = RunQuickly.RunQuicklyStartResponse.newBuilder();
                                response.setOperationType(GameBase.OperationType.START);
                                room.getSeats().stream().filter(seat -> RunQuicklyTcpService.userClients.containsKey(seat.getUserId())).forEach(seat -> {
                                    dealCard.clearCards();
                                    dealCard.addAllCards(seat.getCards());
                                    response.setData(dealCard.build().toByteString());
                                    RunQuicklyTcpService.userClients.get(seat.getUserId()).send(response.build(), seat.getUserId());
                                });

                                for (Seat seat : room.getSeats()) {
                                    if (seat.getSeatNo() == room.getOperationSeat()) {
                                        GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder().setID(room.getSeats().get(room.getOperationSeat() - 1).getUserId()).build();
                                        response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                                        for (Seat seat1 : room.getSeats()) {
                                            if (RunQuicklyTcpService.userClients.containsKey(seat1.getUserId())) {
                                                messageReceive.send(response.build(), seat1.getUserId());
                                            }
                                        }
                                        break;
                                    }
                                }
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
                                room.getSeats().stream().filter(seat -> seat.getUserId() == userId && room.getOperationSeat() == seat.getSeatNo()).forEach(seat -> {
                                    if (seat.getCards().containsAll(playCardRequest.getCardList())) {
                                        //判断该出牌的牌型
                                        CardType cardType = null;
                                        int value = 0;
                                        int cardSize = 0;
                                        //出牌正确性验证
                                        if (room.getHistoryList().size() > 0) {
                                            for (int i = room.getHistoryList().size() - 1; i > room.getHistoryList().size() - room.getCount() && i > -1; i--) {
                                                OperationHistory operationHistory = room.getHistoryList().get(i);
                                                if (0 == OperationHistoryType.PLAY_CARD.compareTo(operationHistory.getHistoryType())) {
                                                    cardType = Card.getCardType(operationHistory.getCards());
                                                    value = Card.getCardValue(operationHistory.getCards(), cardType);
                                                    cardSize = operationHistory.getCards().size();
                                                    break;
                                                }
                                            }
                                        }

                                        //如果没出牌
                                        if (null == cardType) {
                                            if (0 == playCardRequest.getCardList().size()) {
                                                logger.warn("必须出牌");
                                                return;
                                            } else {
                                                CardType myCardType = Card.getCardType(playCardRequest.getCardList());
                                                if (0 == myCardType.compareTo(CardType.ERROR)) {
                                                    logger.warn("牌型错误");
                                                    return;
                                                }
                                            }
                                        } else {
                                            //是否出牌
                                            if (0 != playCardRequest.getCardList().size()) {
                                                CardType myCardType = Card.getCardType(playCardRequest.getCardList());

                                                if (0 == myCardType.compareTo(CardType.ERROR)) {
                                                    logger.warn("牌型错误");
                                                    return;
                                                }

                                                if (0 == myCardType.compareTo(cardType)) {
                                                    //张数相等
                                                    if (playCardRequest.getCardCount() == cardSize) {
                                                        int myValue = Card.getCardValue(playCardRequest.getCardList(), myCardType);
                                                        if (myValue <= value) {
                                                            logger.warn("出牌错误:值小于");
                                                            return;
                                                        }
                                                    } else if (0 != CardType.ZHADAN.compareTo(cardType) || playCardRequest.getCardCount() < cardSize) {
                                                        logger.warn("出牌错误:张数不同，不是炸弹或张数小于");
                                                        return;
                                                    }
                                                } else if (myCardType != CardType.ZHADAN) {
                                                    logger.warn("出牌错误:牌型不同并且不是炸弹");
                                                    return;
                                                }
                                            } else {
                                                room.getHistoryList().add(new OperationHistory(userId, OperationHistoryType.PASS, null));
                                                room.setLastOperation(seat.getUserId());
                                                room.setOperationSeat(room.getNextSeat());
                                                actionResponse.setOperationId(GameBase.ActionId.PASS).clearData();
                                                response.setOperationType(GameBase.OperationType.ACTION).setData(actionResponse.build().toByteString());
                                                room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                                                        .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));

                                                Seat operationSeat = null;
                                                for (Seat seat1 : room.getSeats()) {
                                                    if (seat1.getSeatNo() == room.getOperationSeat()) {
                                                        operationSeat = seat1;
                                                    }
                                                }

                                                GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder().setID(operationSeat.getUserId()).build();
                                                response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                                                room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                                                        .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));
                                                return;
                                            }
                                        }
                                        room.getHistoryList().add(new OperationHistory(userId, OperationHistoryType.PLAY_CARD, playCardRequest.getCardList()));
                                        room.setLastOperation(seat.getUserId());
                                        room.setOperationSeat(room.getNextSeat());

                                        seat.getCards().removeAll(playCardRequest.getCardList());
                                        RunQuickly.RunQuicklyPlayCard playCardResponse = RunQuickly.RunQuicklyPlayCard.newBuilder()
                                                .addAllCard(playCardRequest.getCardList()).build();
                                        actionResponse.setOperationId(GameBase.ActionId.PLAY_CARD).setData(playCardResponse.toByteString());
                                        response.setOperationType(GameBase.OperationType.ACTION).setData(actionResponse.build().toByteString());
                                        room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                                                .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));

                                        if (0 == seat.getCards().size()) {
                                            room.gameOver(response, redisService);
                                            return;
                                        }

                                        Seat operationSeat = null;
                                        for (Seat seat1 : room.getSeats()) {
                                            if (seat1.getSeatNo() == room.getOperationSeat()) {
                                                operationSeat = seat1;
                                            }
                                        }

                                        GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder().setID(operationSeat.getUserId()).build();
                                        response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                                        room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                                                .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));

                                    } else {
                                        logger.warn("用户手中没有此牌" + userId);
                                    }
                                });
                                break;
                            case PASS:
                                for (Seat seat : room.getSeats()) {
                                    if (seat.getUserId() == userId && room.getOperationSeat() == seat.getSeatNo()) {
                                        boolean canPass = false;
                                        if (room.getHistoryList().size() > 0) {
                                            for (int i = room.getHistoryList().size() - 1; i > room.getHistoryList().size() - room.getCount() && i > -1; i--) {
                                                OperationHistory operationHistory = room.getHistoryList().get(i);
                                                if (0 == OperationHistoryType.PLAY_CARD.compareTo(operationHistory.getHistoryType())) {
                                                    canPass = true;
                                                    break;
                                                }
                                            }
                                        }
                                        if (!canPass) {
                                            logger.warn("必须出牌");
                                            break;
                                        }

                                        room.getHistoryList().add(new OperationHistory(userId, OperationHistoryType.PASS, null));
                                        room.setLastOperation(seat.getUserId());
                                        room.setOperationSeat(room.getNextSeat());
                                        actionResponse.setID(userId).setOperationId(GameBase.ActionId.PASS).clearData();
                                        response.setOperationType(GameBase.OperationType.ACTION).setData(actionResponse.build().toByteString());
                                        room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                                                .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));

                                        Seat operationSeat = null;
                                        for (Seat seat1 : room.getSeats()) {
                                            if (seat1.getSeatNo() == room.getOperationSeat()) {
                                                operationSeat = seat1;
                                            }
                                        }

                                        GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder().setID(operationSeat.getUserId()).build();
                                        response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                                        room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                                                .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));
                                        break;
                                    }
                                }
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
                        Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                        response.setOperationType(GameBase.OperationType.DISSOLVE).clearData();
                        for (Seat seat : room.getSeats()) {
                            if (RunQuicklyTcpService.userClients.containsKey(seat.getUserId())) {
                                messageReceive.send(response.build(), seat.getUserId());
                            }
                        }
                        room.roomOver(response, redisService);
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