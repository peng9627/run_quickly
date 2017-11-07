package runquickly.entrance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import runquickly.mode.GameBase;
import runquickly.redis.RedisService;
import runquickly.timeout.MessageTimeout;
import runquickly.utils.ByteUtils;
import runquickly.utils.CoreStringUtils;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Date;

/**
 * Created by pengyi
 * Date 2017/7/25.
 */
public class MessageReceive implements Runnable {
    final OutputStream os;
    private final InputStream is;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private Socket s;
    private Boolean connect;
    private byte[] md5Key = "2704031cd4814eb2a82e47bd1d9042c6".getBytes();
    private RunQuicklyClient client;
    public Date lastMessageDate;
    public String roomNo;

    MessageReceive(Socket s, RedisService redisService) {

        this.s = s;
        connect = true;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = s.getInputStream();
            outputStream = s.getOutputStream();
        } catch (EOFException e) {
            logger.info("socket.shutdown.message");
            close();
        } catch (IOException e) {
            logger.info("socket.connection.fail.message" + e.getMessage());
            close();
        }
        is = inputStream;
        os = outputStream;

        client = new RunQuicklyClient(redisService, this);
    }

    public void send(GameBase.BaseConnection baseConnection, int userId) {
        try {
            logger.info("runquickly send " + baseConnection.getOperationType() + userId);
            String md5 = CoreStringUtils.md5(ByteUtils.addAll(md5Key, baseConnection.toByteArray()), 32, false);
            if (0 == userId) {
                sendTo(os, md5, baseConnection);
            }
            if (RunQuicklyTcpService.userClients.containsKey(userId)) {
                synchronized (RunQuicklyTcpService.userClients.get(userId).os) {
                    OutputStream os = RunQuicklyTcpService.userClients.get(userId).os;
                    sendTo(os, md5, baseConnection);
                }
            }
        } catch (IOException e) {
            logger.info("socket.server.sendMessage.fail.message" + e.getMessage());
//            client.close();
        } catch (Exception e) {
            logger.error(e.toString(), e);
        }
    }

    public void close() {
        connect = false;
        try {
            client.close();
            if (is != null)
                is.close();
            if (os != null)
                os.close();
            if (s != null) {
                s.close();
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
        return ((ch4 & 0xFF) | ((ch3 & 0xff) << 8) | ((ch2 & 0xff) << 16) | ch1 << 24);
    }

    private String readString(InputStream is) throws IOException {
        int len = readInt(is);
        byte[] bytes = new byte[len];
        is.read(bytes);
        return new String(bytes);
    }

    public void sendTo(OutputStream os, String h, GameBase.BaseConnection baseConnection) throws IOException {
        int len = baseConnection.getSerializedSize() + 36;
        writeInt(os, len);
        writeString(os, h);
        os.write(baseConnection.toByteArray());
    }

    private static void writeInt(OutputStream s, int v) throws IOException {
        s.write((v >>> 24) & 0xFF);
        s.write((v >>> 16) & 0xFF);
        s.write((v >>> 8) & 0xFF);
        s.write((v) & 0xFF);
    }

    private static void writeString(OutputStream s, String v) throws IOException {
        byte[] bytes = v.getBytes();
        writeInt(s, bytes.length);
        s.write(bytes);
    }

    @Override
    public void run() {
        try {
            while (connect) {
                int len = readInt(is);
                String md5 = readString(is);
                len -= md5.getBytes().length + 4;
                byte[] data = new byte[0];
                boolean check = true;
                if (0 != len) {
                    while (len != 0) {
                        byte[] bytes = new byte[len];
                        int l = is.read(bytes);
                        data = ByteUtils.addAll(data, ByteUtils.subarray(bytes, 0, l));
                        len -= l;
                    }
                    check = CoreStringUtils.md5(ByteUtils.addAll(md5Key, data), 32, false).equalsIgnoreCase(md5);
                }
                if (check) {
                    client.receive(GameBase.BaseConnection.parseFrom(data));
                    lastMessageDate = new Date();
                    new MessageTimeout(lastMessageDate, this).start();
                }
            }
        } catch (EOFException e) {
            logger.info("socket.shutdown.message" + client.userId);
            close();
        } catch (IOException e) {
            logger.info("socket.dirty.shutdown.message" + e.getMessage() + client.userId);
            close();
        } catch (Exception e) {
            logger.info("socket.dirty.shutdown.message" + client.userId);
            logger.error(e.toString(), e);
            close();
        }
    }
}
