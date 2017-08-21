package runquickly.entrance;

import com.alibaba.fastjson.JSON;
import com.google.protobuf.GeneratedMessageV3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import runquickly.mode.*;
import runquickly.redis.RedisService;
import runquickly.utils.ByteUtils;
import runquickly.utils.CoreStringUtils;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created date 2016/3/25
 * Author pengyi
 */
public class RunQuicklyClient implements Runnable {

    private final InputStream is;
    private final OutputStream os;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private Socket s;
    private int userId;
    private RedisService redisService;
    private String roomNo;
    private Boolean connect;
    private byte[] md5Key = "2704031cd4814eb2a82e47bd1d9042c6".getBytes();

    private GameBase.BaseConnection request;
    private GameBase.BaseConnection.Builder response;

    RunQuicklyClient(Socket s, RedisService redisService) {
        this.s = s;
        connect = true;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = s.getInputStream();
            outputStream = s.getOutputStream();
            this.redisService = redisService;
        } catch (EOFException e) {
            logger.info("socket.shutdown.message");
            close();
        } catch (IOException e) {
            logger.info("socket.connection.fail.message" + e.getMessage());
            close();
        }
        is = inputStream;
        os = outputStream;
        request = GameBase.BaseConnection.newBuilder().build();
        response = GameBase.BaseConnection.newBuilder();
    }

    public void send(GeneratedMessageV3 messageV3, int userId) {
        try {
            if (RunQuicklyTcpService.userClients.containsKey(userId)) {
                synchronized (RunQuicklyTcpService.userClients.get(userId).os) {
                    OutputStream os = RunQuicklyTcpService.userClients.get(userId).os;
                    String md5 = CoreStringUtils.md5(ByteUtils.addAll(md5Key, messageV3.toByteArray()), 32, false);
                    messageV3.sendTo(os, md5);
                    logger.info("mahjong send:len=\n" + messageV3 + "\nuser=" + userId + "\n");
                }
            }
        } catch (IOException e) {
            logger.info("socket.server.sendMessage.fail.message" + userId + e.getMessage());
//            client.close();
        }
    }

    public void close() {
        connect = false;
        try {
            if (is != null)
                is.close();
            if (os != null)
                os.close();
            if (s != null) {
                s.close();
            }
            if (0 != userId) {
//                exit();
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    private int readInt(InputStream is) throws IOException {
        int ch1 = is.read();
        int ch2 = is.read();
        int ch3 = is.read();
        int ch4 = is.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0) {
            throw new EOFException();
        }
        return (ch1 << 24 | ((ch2 << 16) & 0xff) | ((ch3 << 8) & 0xff) | (ch4 & 0xFF));
    }

    private String readString(InputStream is) throws IOException {
        int len = readInt(is);
        byte[] bytes = new byte[len];
        is.read(bytes);
        return new String(bytes);
    }

    @Override
    public void run() {
        try {
            while (connect) {

                int len = readInt(is);
                String md5 = readString(is);
                len -= md5.getBytes().length + 4;
                byte[] data = new byte[len];
                boolean check = true;
                if (0 != len) {
                    int l = is.read(data);
                    check = CoreStringUtils.md5(ByteUtils.addAll(md5Key, data), 32, false).equalsIgnoreCase(md5);
                }
                if (check) {
                    request = GameBase.BaseConnection.parseFrom(data);
                    switch (request.getOperationType()) {
                        case CONNECTION:
                            //加入玩家数据
                            if (redisService.exists("maintenance")) {
                                break;
                            }
                            GameBase.RoomCardIntoRequest intoRequest = GameBase.RoomCardIntoRequest.parseFrom(request.getData());
                            userId = intoRequest.getID();
                            roomNo = intoRequest.getRoomNo();
                            RunQuicklyTcpService.userClients.put(userId, this);
                            GameBase.RoomCardIntoResponse.Builder roomCardIntoResponseBuilder = GameBase.RoomCardIntoResponse.newBuilder();
                            if (redisService.exists("room" + roomNo)) {
                                redisService.lock("lock_room" + roomNo);
                                Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                                //房间是否已存在当前用户，存在则为重连
                                final boolean[] find = {false};
                                room.getSeats().stream().filter(seat -> seat.getUserId() == userId).forEach(seat -> find[0] = true);
                                if (!find[0]) {
                                    if (room.getCount() < room.getSeats().size()) {
                                        User user = new User(userId, "测试帐号");
                                        room.addSeat(user);
                                    } else {
                                        roomCardIntoResponseBuilder.setError(GameBase.ErrorCode.COUNT_FULL);
                                        response.setOperationType(GameBase.OperationType.CONNECTION).setData(roomCardIntoResponseBuilder.build().toByteString());
                                        send(response.build(), userId);
                                        redisService.unlock("lock_room" + roomNo);
                                        break;
                                    }
                                }

                                RunQuickly.RunQuicklyIntoResponse intoResponse = RunQuickly.RunQuicklyIntoResponse.newBuilder()
                                        .setBaseScore(room.getBaseScore()).setCount(room.getCount()).setGameTimes(room.getGameTimes())
                                        .setRoomNo(roomNo).build();
                                roomCardIntoResponseBuilder.setError(GameBase.ErrorCode.SUCCESS);
                                response.setOperationType(GameBase.OperationType.ROOM_INFO).setData(roomCardIntoResponseBuilder.build().toByteString());
                                send(response.build(), userId);

                                GameBase.RoomSeatsInfo.Builder roomSeatsInfo = GameBase.RoomSeatsInfo.newBuilder();
                                for (Seat seat1 : room.getSeats()) {
                                    GameBase.SeatResponse.Builder seatResponse = GameBase.SeatResponse.newBuilder();
                                    seatResponse.setSeatNo(seat1.getSeatNo());
                                    seatResponse.setID(seat1.getUserId());
                                    seatResponse.setScore(seat1.getScore());
                                    seatResponse.setIsReady(seat1.isReady());
                                    seatResponse.setAreaString(seat1.getAreaString());
                                    roomSeatsInfo.addSeats(seatResponse.build());
                                }
                                response.setOperationType(GameBase.OperationType.SEAT_INFO).setData(roomSeatsInfo.build().toByteString());
                                send(response.build(), userId);

                                if (0 == room.getGameStatus().compareTo(GameStatus.PLAYING)) {
                                    RunQuickly.RunQuicklyGameInfo.Builder gameInfo = RunQuickly.RunQuicklyGameInfo.newBuilder().setGameStatus(GameBase.GameStatus.PLAYING);
                                    gameInfo.setOperationUser(room.getSeats().get(room.getOperationSeat() - 1).getUserId());
                                    gameInfo.setLastOperationUser(room.getLastOperation());
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
                                        gameInfo.addSeats(seatResponse.build());
                                    }
                                    response.setOperationType(GameBase.OperationType.GAME_INFO).setData(gameInfo.build().toByteString());
                                    send(response.build(), userId);

                                    //才开始的时候检测是否该当前玩家出牌
                                    GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder().setID(room.getSeats().get(room.getOperationSeat() - 1).getUserId()).build();
                                    response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                                    send(response.build(), userId);
                                }
                                redisService.addCache("room" + roomNo, JSON.toJSONString(room));
                                redisService.unlock("lock_room" + roomNo);
                            } else {
                                roomCardIntoResponseBuilder.setError(GameBase.ErrorCode.ROOM_NOT_EXIST);
                                response.setOperationType(GameBase.OperationType.CONNECTION).setData(roomCardIntoResponseBuilder.build().toByteString());
                                send(response.build(), userId);
                            }
                            break;
                        case READY:
                            if (redisService.exists("room" + roomNo)) {
                                redisService.lock("lock_room" + roomNo);
                                Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
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
                                    //骰子
                                    RunQuickly.RunQuicklyStartResponse.Builder dealCard = RunQuickly.RunQuicklyStartResponse.newBuilder();
                                    dealCard.setFirstID(room.getSeats().get(room.getOperationSeat() - 1).getUserId());
                                    response.setOperationType(GameBase.OperationType.START);
                                    room.getSeats().stream().filter(seat -> RunQuicklyTcpService.userClients.containsKey(seat.getUserId())).forEach(seat -> {
                                        dealCard.clearCards();
                                        dealCard.addAllCards(seat.getCards());
                                        response.setData(dealCard.build().toByteString());
                                        RunQuicklyTcpService.userClients.get(seat.getUserId()).send(response.build(), seat.getUserId());
                                    });

                                    GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder().setID(room.getSeats().get(room.getOperationSeat() - 1).getUserId()).build();
                                    response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                                    send(response.build(), userId);
                                }
                                redisService.addCache("room" + roomNo, JSON.toJSONString(room));
                            } else {
                                logger.warn("房间不存在");
                            }
                            break;
                        case COMPLETED:
                            if (redisService.exists("room" + roomNo)) {
                                redisService.lock("lock_room" + roomNo);
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
                            } else {
                                logger.warn("房间不存在");
                            }
                            break;
                        case ACTION:
                            GameBase.BaseAction actionRequest = GameBase.BaseAction.parseFrom(request.getData());
                            GameBase.BaseAction.Builder actionResponse = GameBase.BaseAction.newBuilder();
                            if (redisService.exists("room" + roomNo)) {
                                redisService.lock("lock_room" + roomNo);
                                Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                                switch (actionRequest.getOperationId()) {
                                    case PLAY_CARD:
                                        RunQuickly.RunQuicklyPlayCardRequest playCardRequest = RunQuickly.RunQuicklyPlayCardRequest.parseFrom(actionRequest.getData());
                                        room.getSeats().stream().filter(seat -> seat.getUserId() == userId).forEach(seat -> {
                                            if (seat.getCards().containsAll(playCardRequest.getCardList())) {
                                                //判断该出牌的牌型
                                                CardType cardType = null;
                                                int value = 0;
                                                //出牌正确性验证
                                                if (room.getHistoryList().size() > 0) {
                                                    int i = 1;
                                                    for (OperationHistory operationHistory : room.getHistoryList()) {
                                                        if (0 == operationHistory.getCards().size()) {
                                                            cardType = Card.getCardType(operationHistory.getCards());
                                                            value = Card.getCardValue(operationHistory.getCards(), cardType);
                                                            break;
                                                        }
                                                        i++;
                                                        if (i == room.getCount()) {
                                                            break;
                                                        }
                                                    }
                                                }

                                                if (null == cardType) {
                                                    if (0 == playCardRequest.getCardList().size()) {
                                                        logger.warn("必须出牌");
                                                        return;
                                                    }
                                                } else {
                                                    //不出
                                                    if (0 != playCardRequest.getCardList().size()) {
                                                        CardType myCardType = Card.getCardType(playCardRequest.getCardList());

                                                        if (0 == myCardType.compareTo(CardType.ERROR)) {
                                                            logger.warn("牌型错误");
                                                            return;
                                                        }

                                                        if (0 == myCardType.compareTo(cardType)) {
                                                            int myValue = Card.getCardValue(playCardRequest.getCardList(), myCardType);
                                                            if (myValue <= value) {
                                                                logger.warn("牌型错误");
                                                                return;
                                                            }
                                                        } else if (myCardType != CardType.ZHADAN) {
                                                            logger.warn("牌型错误");
                                                            return;
                                                        }
                                                    }
                                                }
                                                room.getHistoryList().add(new OperationHistory(userId, OperationHistoryType.PLAY_CARD, playCardRequest.getCardList()));

                                                seat.getCards().removeAll(playCardRequest.getCardList());
                                                RunQuickly.RunQuicklyPlayCardResponse playCardResponse = RunQuickly.RunQuicklyPlayCardResponse.newBuilder()
                                                        .setID(seat.getUserId()).addAllCard(playCardRequest.getCardList()).build();
                                                actionResponse.setOperationId(GameBase.ActionId.PLAY_CARD).setData(playCardResponse.toByteString());
                                                response.setOperationType(GameBase.OperationType.ACTION).setData(actionResponse.build().toByteString());
                                                room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                                                        .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));

                                                if (0 == seat.getCards().size()) {
                                                    room.gameOver(response, redisService);
                                                    return;
                                                }

                                                GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder().setID(room.getSeats().get(room.getOperationSeat() - 1).getUserId()).build();
                                                response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                                                room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                                                        .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));

                                            } else {
                                                logger.warn("用户手中没有此牌" + userId);
                                            }
                                        });
                                        break;
                                }
                                redisService.addCache("room" + roomNo, JSON.toJSONString(room));
                                redisService.unlock("lock_room" + roomNo);
                            } else {
                                logger.warn("房间不存在");
                            }
                            break;
                        case REPLAY:
                            RunQuickly.RunQuicklyReplayResponse.Builder replayResponse = RunQuickly.RunQuicklyReplayResponse.newBuilder();
                            if (redisService.exists("room" + roomNo)) {
                                redisService.lock("lock_room" + roomNo);
                                Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                                for (OperationHistory operationHistory : room.getHistoryList()) {
                                    GameBase.OperationHistory.Builder builder = GameBase.OperationHistory.newBuilder();
                                    builder.setID(operationHistory.getUserId());
                                    builder.addAllCard(operationHistory.getCards());
                                    builder.setOperationId(GameBase.ActionId.PLAY_CARD);
                                    replayResponse.addHistory(builder);
                                }
                                response.setOperationType(GameBase.OperationType.REPLAY).setData(replayResponse.build().toByteString());
                                send(response.build(), userId);
                                redisService.unlock("lock_room" + roomNo);
                            }
                            break;
                        case EXIT:
                            break;
                    }
                }
            }
        } catch (EOFException e) {
            logger.info("socket.shutdown.message");
            close();
        } catch (IOException e) {
            logger.info("socket.dirty.shutdown.message" + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            logger.info("socket.dirty.shutdown.message");
            e.printStackTrace();
        }
    }

}