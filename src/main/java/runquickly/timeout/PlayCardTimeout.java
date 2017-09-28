package runquickly.timeout;

import com.alibaba.fastjson.JSON;
import runquickly.constant.Constant;
import runquickly.entrance.RunQuicklyTcpService;
import runquickly.mode.*;
import runquickly.redis.RedisService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by pengyi
 * Date : 17-8-31.
 * desc:
 */
public class PlayCardTimeout extends Thread {

    private int userId;
    private String roomNo;
    private int operationCount;
    private int gameCount;
    private RedisService redisService;
    private GameBase.BaseConnection.Builder response;

    public PlayCardTimeout(int userId, String roomNo, int operationCount, int gameCount, RedisService redisService) {
        this.userId = userId;
        this.roomNo = roomNo;
        this.operationCount = operationCount;
        this.gameCount = gameCount;
        this.redisService = redisService;
        this.response = GameBase.BaseConnection.newBuilder();
    }

    @Override
    public void run() {

        synchronized (this) {
            try {
                wait(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (RunQuicklyTcpService.userClients.containsKey(userId)) {
            synchronized (this) {
                try {
                    wait(Constant.playCardTimeout);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        if (redisService.exists("room" + roomNo)) {
            while (!redisService.lock("lock_room" + roomNo)) {
            }
            Room room = JSON.parseObject(redisService.getCache("room" + roomNo), Room.class);
            if (room.getHistoryList().size() == operationCount && room.getGameCount() == gameCount) {
                //判断该出牌的牌型
                CardType cardType = null;
                //出牌正确性验证
                if (room.getHistoryList().size() > 0) {
                    for (int i = room.getHistoryList().size() - 1; i > room.getHistoryList().size() - room.getCount() && i > -1; i--) {
                        OperationHistory operationHistory = room.getHistoryList().get(i);
                        if (operationHistory.getUserId() == userId) {
                            break;
                        }
                        if (0 == OperationHistoryType.PLAY_CARD.compareTo(operationHistory.getHistoryType())) {
                            cardType = Card.getCardType(operationHistory.getCards(), 1 == room.getGameRules() % 2);
                            break;
                        }
                    }
                }

                List<Integer> cardList = new ArrayList<>();
                Seat operationSeat = null;
                for (Seat seat1 : room.getSeats()) {
                    if (seat1.getSeatNo() == room.getOperationSeat()) {
                        operationSeat = seat1;
                        cardList.add(seat1.getCards().get(0));
                        break;
                    }
                }
                if (null == cardType) {
                    for (Seat seat1 : room.getSeats()) {
                        seat1.setCanPlay(true);
                    }
                    room.getHistoryList().add(new OperationHistory(userId, OperationHistoryType.PLAY_CARD, cardList));
                    room.setLastOperation(operationSeat.getUserId());
                    room.setOperationSeat(room.getNextSeat());


                    operationSeat.getCards().removeAll(cardList);
                    RunQuickly.RunQuicklyPlayCard playCardResponse = RunQuickly.RunQuicklyPlayCard.newBuilder()
                            .addAllCard(cardList).build();
                    GameBase.BaseAction.Builder actionResponse = GameBase.BaseAction.newBuilder().setID(operationSeat.getUserId());
                    actionResponse.setOperationId(GameBase.ActionId.PLAY_CARD).setData(playCardResponse.toByteString());
                    response.setOperationType(GameBase.OperationType.ACTION).setData(actionResponse.build().toByteString());
                    room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                            .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));
                    if (0 == operationSeat.getCards().size()) {
                        room.gameOver(response, redisService);
                        if (null != room.getRoomNo()) {
                            redisService.addCache("room" + roomNo, JSON.toJSONString(room));
                        }
                        return;
                    }

                    for (Seat seat1 : room.getSeats()) {
                        if (seat1.getSeatNo() == room.getOperationSeat()) {
                            operationSeat = seat1;
                            break;
                        }
                    }

                    new PlayCardTimeout(operationSeat.getUserId(), roomNo, room.getHistoryList().size(), gameCount, redisService).start();
                    GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder().setID(operationSeat.getUserId()).build();
                    response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                    room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                            .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));
                } else {
                    if (1 == room.getGameRules() % 2) {
                        operationSeat.setCanPlay(false);
                    }
                    room.getHistoryList().add(new OperationHistory(userId, OperationHistoryType.PASS, null));
                    room.setLastOperation(operationSeat.getUserId());
                    room.setOperationSeat(room.getNextSeat());

                    GameBase.BaseAction.Builder actionResponse = GameBase.BaseAction.newBuilder().setID(operationSeat.getUserId());
                    actionResponse.setOperationId(GameBase.ActionId.PASS).clearData();
                    response.setOperationType(GameBase.OperationType.ACTION).setData(actionResponse.build().toByteString());
                    room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                            .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));

                    for (Seat seat1 : room.getSeats()) {
                        if (seat1.getSeatNo() == room.getOperationSeat()) {
                            operationSeat = seat1;
                            break;
                        }
                    }

                    new PlayCardTimeout(operationSeat.getUserId(), roomNo, room.getHistoryList().size(), gameCount, redisService).start();
                    GameBase.RoundResponse roundResponse = GameBase.RoundResponse.newBuilder().setID(operationSeat.getUserId()).build();
                    response.setOperationType(GameBase.OperationType.ROUND).setData(roundResponse.toByteString());
                    room.getSeats().stream().filter(seat1 -> RunQuicklyTcpService.userClients.containsKey(seat1.getUserId()))
                            .forEach(seat1 -> RunQuicklyTcpService.userClients.get(seat1.getUserId()).send(response.build(), seat1.getUserId()));
                }
            }
            if (null != room.getRoomNo()) {
                redisService.addCache("room" + roomNo, JSON.toJSONString(room));
            }
            redisService.unlock("lock_room" + roomNo);
        }
    }
}
