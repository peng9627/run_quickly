package runquickly.entrance;

import com.alibaba.fastjson.JSON;
import com.google.protobuf.MessageLite;
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
import java.util.List;

/**
 * Created date 2016/3/25
 * Author pengyi
 */
public class RunQuicklyClient implements Runnable {

    private final InputStream is;
    private final OutputStream os;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private Socket s;
    private String user;
    private RedisService redisService;
    private String roomNo;
    private double score;
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

    public void send(MessageLite lite, String username) {
        try {
            if (RunQuicklyTcpService.userClients.containsKey(username)) {
                synchronized (RunQuicklyTcpService.userClients.get(username).os) {
                    OutputStream os = RunQuicklyTcpService.userClients.get(username).os;
                    String md5 = CoreStringUtils.md5(ByteUtils.addAll(md5Key, lite.toByteArray()), 32, false);
                    int len = lite.toByteArray().length + md5.getBytes().length + 4;
                    writeInt(os, len);
                    writeString(os, md5);
                    os.write(lite.toByteArray());
                    logger.info("runquickly send:len=" + len + "user=" + username);
                }
            }
        } catch (IOException e) {
            logger.info("socket.server.sendMessage.fail.message" + username + e.getMessage());
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
            if (null != user) {
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

    private void writeInt(OutputStream stream, int value) throws IOException {
        stream.write((value >>> 24) & 0xFF);
        stream.write((value >>> 16) & 0xFF);
        stream.write((value >>> 8) & 0xFF);
        stream.write((value) & 0xFF);
    }

    private void writeString(OutputStream stream, String value) throws IOException {
        byte[] bytes = value.getBytes();
        writeInt(stream, bytes.length);
        stream.write(bytes);
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
                            RunQuickly.RoomCardIntoRequest intoRequest = RunQuickly.RoomCardIntoRequest.parseFrom(request.getData());
                            user = intoRequest.getId();
                            roomNo = intoRequest.getRoomNo();
                            RunQuicklyTcpService.userClients.put(user, this);
                            RunQuickly.RoomCardIntoResponse.Builder intoResponseBuilder = RunQuickly.RoomCardIntoResponse.newBuilder();
                            if (redisService.exists("room" + roomNo)) {
                                redisService.lock("lock_room" + roomNo);
                                Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                                score = room.getBaseScore();
                                //房间是否已存在当前用户，存在则为重连
                                final boolean[] find = {false};
                                room.getSeats().stream().filter(seat -> seat.getUserName().equals(user)).forEach(seat -> find[0] = true);
                                if (!find[0]) {
                                    if (room.getCount() < room.getSeats().size()) {
                                        User user = new User();
                                        user.setUsername(this.user);
                                        room.addSeat(user);
                                    } else {
                                        intoResponseBuilder.setError(GameBase.ErrorCode.COUNT_FULL);
                                        response.setOperationType(GameBase.OperationType.CONNECTION).setData(intoResponseBuilder.build().toByteString());
                                        send(response.build(), user);
                                        redisService.unlock("lock_room" + roomNo);
                                        break;
                                    }
                                }
                                response.setOperationType(GameBase.OperationType.ROOM_INFO).setData(intoResponseBuilder.build().toByteString());
                                send(response.build(), user);

                                RunQuickly.RoomSeatsInfo.Builder roomSeatsInfo = RunQuickly.RoomSeatsInfo.newBuilder();
                                for (Seat seat1 : room.getSeats()) {
                                    RunQuickly.SeatResponse.Builder seatResponse = RunQuickly.SeatResponse.newBuilder();
                                    seatResponse.setSeatNo(seat1.getSeatNo());
                                    seatResponse.setID(seat1.getUserName());
                                    seatResponse.setGold(seat1.getGold());
                                    seatResponse.setIsReady(seat1.isReady());
                                    seatResponse.setAreaString(seat1.getAreaString());
                                    roomSeatsInfo.addSeats(seatResponse.build());
                                }
                                response.setOperationType(GameBase.OperationType.SEAT_INFO).setData(roomSeatsInfo.build().toByteString());
                                send(response.build(), user);

                                if (0 == room.getGameStatus().compareTo(GameStatus.PLAYING)) {
                                    RunQuickly.GameInfo.Builder gameInfo = RunQuickly.GameInfo.newBuilder().setGameStatus(RunQuickly.GameStatus.PLAYING);
                                    gameInfo.setOperationUser(room.getSeats().get(room.getOperationSeat() - 1).getUserName());
                                    gameInfo.setLastOperationUser(room.getLastOperation());
                                    for (Seat seat1 : room.getSeats()) {
                                        RunQuickly.SeatGameInfo.Builder seatResponse = RunQuickly.SeatGameInfo.newBuilder();
                                        seatResponse.setID(seat1.getUserName());
                                        seatResponse.setScore(seat1.getScore());
                                        seatResponse.setIsRobot(seat1.isRobot());
                                        if (null != seat1.getCards()) {
                                            if (seat1.getUserName().equals(user)) {
                                                seatResponse.addAllCards(seat1.getCards());
                                            } else {
                                                seatResponse.setCardsSize(seat1.getCards().size());
                                            }
                                        }
                                        gameInfo.addSeats(seatResponse.build());
                                    }
                                    response.setOperationType(GameBase.OperationType.GAME_INFO).setData(gameInfo.build().toByteString());
                                    send(response.build(), user);

                                    //才开始的时候检测是否该当前玩家出牌
                                    RunQuickly.RoundResponse roundResponse = RunQuickly.RoundResponse.newBuilder().setID(room.getSeats().get(room.getOperationSeat() - 1).getUserName()).build();
                                    response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                                    send(response.build(), user);
                                }
                                redisService.addCache("room" + roomNo, JSON.toJSONString(room));
                                redisService.unlock("lock_room" + roomNo);
                            } else {
                                intoResponseBuilder.setError(GameBase.ErrorCode.ROOM_NOT_EXIST);
                                response.setOperationType(GameBase.OperationType.CONNECTION).setData(intoResponseBuilder.build().toByteString());
                                send(response.build(), user);
                            }
                            break;
                        case READY:
                            RunQuickly.BaseAction.Builder actionResponse = RunQuickly.BaseAction.newBuilder();
                            if (redisService.exists("room" + roomNo)) {
                                redisService.lock("lock_room" + roomNo);
                                Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                                room.getSeats().stream().filter(seat -> seat.getUserName().equals(user) && !seat.isReady()).forEach(seat -> {
                                    seat.setReady(true);
                                    response.setOperationType(GameBase.OperationType.READY).setData(RunQuickly.ReadyResponse.newBuilder().setID(seat.getUserName()).build().toByteString());
                                    room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserName())).forEach(seat1 ->
                                            RunQuicklyTcpService.userClients.get(seat1.getUserName()).send(response.build(), seat1.getUserName()));
                                });
                                boolean allReady = true;
                                for (Seat seat : room.getSeats()) {
                                    if (!seat.isReady()) {
                                        allReady = false;
                                        break;
                                    }
                                }
                                if (allReady && room.getCount() == room.getSeats().size()) {
                                    room.setGameStatus(GameStatus.PLAYING);
                                    room.dealCard();
                                    //骰子
                                    RunQuickly.DealCardResponse.Builder dealCard = RunQuickly.DealCardResponse.newBuilder();
                                    dealCard.setFirstID(room.getSeats().get(room.getOperationSeat() - 1).getUserName());
                                    actionResponse.setOperationId(GameBase.OperationId.DEAL_CARD);
                                    response.setOperationType(GameBase.OperationType.ACTION);
                                    room.getSeats().stream().filter(seat -> RunQuicklyTcpService.userClients.containsKey(seat.getUserName())).forEach(seat -> {
                                        dealCard.clearCards();
                                        dealCard.addAllCards(seat.getCards());
                                        actionResponse.setData(dealCard.build().toByteString());
                                        response.setData(actionResponse.build().toByteString());
                                        RunQuicklyTcpService.userClients.get(seat.getUserName()).send(response.build(), seat.getUserName());
                                    });

                                    RunQuickly.RoundResponse roundResponse = RunQuickly.RoundResponse.newBuilder().setID(room.getSeats().get(room.getOperationSeat() - 1).getUserName()).build();
                                    response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                                    send(response.build(), user);
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
                                room.getSeats().stream().filter(seat -> seat.getUserName().equals(user) && !seat.isCompleted())
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
                            RunQuickly.BaseAction actionRequest = RunQuickly.BaseAction.parseFrom(request.getData());
                            actionResponse = RunQuickly.BaseAction.newBuilder();
                            if (redisService.exists("room" + roomNo)) {
                                redisService.lock("lock_room" + roomNo);
                                Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                                switch (actionRequest.getOperationId()) {
                                    case PLAY_CARD:
                                        RunQuickly.PlayCardRequest playCardRequest = RunQuickly.PlayCardRequest.parseFrom(actionRequest.getData());
                                        room.getSeats().stream().filter(seat -> seat.getUserName().equals(user)).forEach(seat -> {
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
                                                room.getHistoryList().add(new OperationHistory(user, OperationHistoryType.PLAY_CARD, playCardRequest.getCardList()));

                                                seat.getCards().removeAll(playCardRequest.getCardList());
                                                RunQuickly.PlayCardResponse playCardResponse = RunQuickly.PlayCardResponse.newBuilder()
                                                        .setID(seat.getUserName()).addAllCard(playCardRequest.getCardList()).build();
                                                actionResponse.setOperationId(GameBase.OperationId.PLAY_CARD).setData(playCardResponse.toByteString());
                                                response.setOperationType(GameBase.OperationType.ACTION).setData(actionResponse.build().toByteString());
                                                room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserName()))
                                                        .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserName()).send(response.build(), seat1.getUserName()));
                                            } else {
                                                logger.warn("用户手中没有此牌" + user);
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
                            RunQuickly.ReplayResponse.Builder replayResponse = RunQuickly.ReplayResponse.newBuilder();
                            if (redisService.exists("room" + roomNo)) {
                                redisService.lock("lock_room" + roomNo);
                                Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
                                for (OperationHistory operationHistory : room.getHistoryList()) {
                                    RunQuickly.OperationHistory.Builder builder = RunQuickly.OperationHistory.newBuilder();
                                    builder.setID(operationHistory.getUserName());
                                    builder.addAllCard(operationHistory.getCards());
                                    builder.setOperationId(GameBase.OperationId.PLAY_CARD);
                                    replayResponse.addHistory(builder);
                                }
                                response.setOperationType(GameBase.OperationType.REPLAY).setData(replayResponse.build().toByteString());
                                send(response.build(), user);
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

    private void gameOver(Room room) {
        //TODO 计算比分
        //删除该桌
        redisService.delete("room" + roomNo);
        redisService.lock("lock_room_nos" + roomNo);
        List<String> roomNos = JSON.parseArray(redisService.getCache("room_nos"), String.class);
        roomNos.remove(roomNo);
        redisService.addCache("room_nos", JSON.toJSONString(roomNos), 86400);
        redisService.unlock("lock_room_nos" + roomNo);
    }
}