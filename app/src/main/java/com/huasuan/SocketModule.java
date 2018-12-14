package com.huasuan;

import android.content.Context;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.huasuan.utils.IoStreamUtils;
import com.huasuan.utils.LogUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class SocketModule extends Thread {

    private static final String TAG = SocketModule.class.getSimpleName();

    private static volatile SocketModule sInstance = null;

    private Context mContext = null;

    private Socket mSocketClient = null;

    private SocketSenderThread mSenderThread = null;

    private SocketReceiverThread mReceiverThread = null;

    private HeartBeatThread mHeartBeatThread = null;

    private String mType1UserName = "";

    private BlockingQueue<byte[]> mBytesBufferQueue = null;

    private byte[] mReceivedBytes = new byte[0];

    private static long sLastTrySocket = 0;

    public SocketModule(Context context) {
        mContext = context;
    }

    public static void sendBytesToSocketServer(Context context, byte[] bytes) {
        if (sInstance == null) {
            synchronized (SocketModule.class) {
                if (sInstance == null) {
                    LogUtils.logDebug(TAG, "will new instance of SocketModule and init");
                    sInstance = new SocketModule(context);
                    sInstance.start();
                }
            }
        }
        sInstance.addToSocketClientBufferQueue(bytes);
        LogUtils.logDebug(TAG, "add bytes to SocketModule done");
    }

    @Override
    public void run() {
        super.run();
        if (mBytesBufferQueue == null) {
            mBytesBufferQueue = new LinkedBlockingDeque<byte[]>(Env.SOCKET_OUT_QUEUE_SIZE);
        }

        initSocket();
    }

    private void initSocket() {
        try {
            // 控制重试socket的时间间隔
            long cur = System.currentTimeMillis();
            if (Math.abs(cur - sLastTrySocket) < Env.SOCKET_RETRY_INTERVAL) {
                LogUtils.logDebug(TAG, "not right time to try socket");
            }
            sLastTrySocket = System.currentTimeMillis();

            // 初始化socket
            mSocketClient = new Socket(Env.SOCKET_HOST, Env.SOCKET_PORT);

            // init输入输出流
            OutputStream os = mSocketClient.getOutputStream();
            InputStream ins = mSocketClient.getInputStream();

            // 启动socket消息发送线程
            if (mSenderThread != null) {
                mSenderThread.exit();
            }
            mSenderThread = new SocketSenderThread(os);
            mSenderThread.start();

            // 启动socket消息接收线程
            if (mReceiverThread != null) {
                mReceiverThread.exit();
            }
            mReceiverThread = new SocketReceiverThread(ins);
            mReceiverThread.start();

            // 启动心跳线程
            if (mHeartBeatThread != null) {
                mHeartBeatThread.exit();
            }
            mHeartBeatThread = new HeartBeatThread();
            mHeartBeatThread.start();

            // 从sqlite读取联系人信息
            String[] rContactJson = WxSQLiteUtil.getRContactJson(mContext);
            String type1UserName = rContactJson[0];
            mType1UserName = type1UserName;
            addToSocketClientBufferQueue(String.format("{\"method\":\"init\",\"data\":\"wechat|%s\"}", type1UserName).getBytes());
            //<<>>
//            addToSocketClientBufferQueue(("{\"method\":\"initcontact\",\"data\":\""+String.format("%s",rContactJson[1]).replace("\"","\\\"")+"\"}").getBytes());

        } catch (ConnectException ce) {
            markSocketError();
        } catch (Throwable e) {
            // 此处必须catch Throwable，否则中间读取数据库的错误catch不到会直接crash
            LogUtils.logError(TAG, "SocketModule init failed");
            LogUtils.logError(TAG, e.getMessage(), e);
        }
    }

    private void markSocketError() {
        LogUtils.logDebug(TAG, "markSocketError()");
        if (sInstance != null) {
            if (sInstance.mSenderThread != null)
                sInstance.mSenderThread.exit();
            if (sInstance.mReceiverThread != null)
                sInstance.mReceiverThread.exit();
            if (sInstance.mHeartBeatThread != null)
                sInstance.mHeartBeatThread.exit();
            sInstance = null;
        }
    }

    private void addToSocketClientBufferQueue(byte[] bytes) {
        try {
            if (mBytesBufferQueue != null) {
                mBytesBufferQueue.offer(Protocol.Packet(bytes));
            }
        } catch (Exception e) {
            LogUtils.logError(TAG, e.getMessage(), e);
        }
    }

    private void processReceivedBytes(String msgContent) {
        JSONObject json = JSON.parseObject(msgContent);
        if (json == null){
            return;
        }
        String Data = json.getString("data");
        //json.getIntValue("status");
        //json.getString("id");
        //json.getString("to");
        String Method = json.getString("method");
        if (Method.equals("sendtextmsg")){
            JSONObject d = JSON.parseObject(Data);
            //<<>>回复微信消息
//            WechatSend("",d.getString("to")+"-----"+d.getString("content"));
            json.put("data","{\"status\":1}");
            json.put("method","msgreturn");
            addToSocketClientBufferQueue(json.toJSONString().getBytes());
        }
        if (Method.equals("islogin")){
            json.put("method","msgreturn");
            json.put("data","{\"isrun\":1,\"islogin\":1,\"name\":\"\",\"lastlogin\":\"\",\"runid\":\""+ mType1UserName +"\"}");
            addToSocketClientBufferQueue(json.toJSONString().getBytes());
        }
    }

    private static byte[] byteMergerAll(byte[]... values) {
        int length_byte = 0;
        for (int i = 0; i < values.length; i++) {
            length_byte += values[i].length;
        }
        byte[] all_byte = new byte[length_byte];
        int countLength = 0;
        for (int i = 0; i < values.length; i++) {
            byte[] b = values[i];
            System.arraycopy(b, 0, all_byte, countLength, b.length);
            countLength += b.length;
        }
        return all_byte;
    }

    private static Boolean isServerClose(Socket socket){
        try{
            LogUtils.logDebug(TAG, "isServerClose()");
            socket.sendUrgentData(0xff);//发送1个字节的紧急数据，默认情况下，服务器端没有开启紧急数据处理，不影响正常通信
            return false;
        }catch(Exception se){
            return true;
        }
    }

    class SocketReceiverThread extends Thread {

        private InputStream mInputStream = null;

        private boolean needExit = false;

        private void exit() {
            LogUtils.logDebug(TAG, "SocketReceiverThread exit()");
            try {
                needExit = true;
                interrupt();
            } catch (Exception e) {
                LogUtils.logError(TAG, e.getMessage(), e);
            }
        }

        public SocketReceiverThread(InputStream in) {
            mInputStream = in;
        }

        @Override
        public void run() {
            super.run();
            try {
                while (true) {

                    if (needExit) {
                        break;
                    }

                    ByteWrapper byteWrapper = new ByteWrapper();
                    byte[] buff = new byte[1024];

                    LogUtils.logDebug(TAG, "in SocketReceiverThread connected : " + mSocketClient.isConnected() + " isclosed " + mSocketClient.isClosed());

                    int size = 0;
                    if ((size = mInputStream.read(buff)) != -1) {
                        LogUtils.logDebug(TAG, "in.read() buf not -1");

                        //<<>>
                        String tmpStr = new String(buff).trim();
                        LogUtils.logDebug(TAG, "received : " + tmpStr);

                        try {
                            mReceivedBytes = byteMergerAll(mReceivedBytes, Arrays.copyOfRange(buff, 0, size));
                            mReceivedBytes = Protocol.Unpack(mReceivedBytes, byteWrapper);
                            String unpackedString = new String(byteWrapper.readAndClear());
                            if (unpackedString != null && unpackedString.length() > 0) {
                                processReceivedBytes(unpackedString);
                            } else {
                                LogUtils.logError(TAG, "unpack null string");
                            }
                        } catch (Exception e) {
                            LogUtils.logError(TAG, e.getMessage(), e);
                        }
                    } else {
                        LogUtils.logDebug(TAG, "in.read() buf is -1");
                        // socket流异常，退出循环
                        break;
                    }
                }
                //<<>>
//            } catch (SocketException e) {
//                LogUtils.logError(TAG, "socket receive failed and restart socket");
//                LogUtils.logError(TAG, e.getMessage(), e);
//            } catch (IOException ioe) {
//                LogUtils.logError(TAG, "socket receive io exception and restart socket");
            } catch (Throwable e) {
                LogUtils.logError(TAG, e.getMessage(), e);
            } finally {
                //<<>>
//                IoStreamUtils.closeSilently(mInputStream);
                markSocketError();
            }

            LogUtils.logDebug(TAG, "SocketReceiverThread exit");
        }
    }

    class SocketSenderThread extends Thread {

        private OutputStream mOutputStream = null;

        private boolean needExit = false;

        public SocketSenderThread(OutputStream os) {
            mOutputStream = os;
        }

        private void exit() {
            LogUtils.logDebug(TAG, "SocketSenderThread exit()");
            try {
                needExit = true;
                interrupt();
            } catch (Exception e) {
                LogUtils.logError(TAG, e.getMessage(), e);
            }
        }

        @Override
        public void run() {
            super.run();

            try {
                while (true) {

                    if (needExit) {
                        break;
                    }

                    // queue中没有内容时会阻塞在take这
                    byte[] outBufBytes = mBytesBufferQueue.take();
                    LogUtils.logDebug(TAG, "SocketSenderThread take outBufBytes and write");

                    mOutputStream.write(outBufBytes);
                    //<<>>
//                    mOutputStream.write("\n".getBytes());  // server以readLine的方式读取消息，所以此处消息末尾要添加\n
                    //<<>>
                    String tmpStr = new String(outBufBytes);
                    LogUtils.logDebug(TAG, "output string is : " + tmpStr);

                    mOutputStream.flush();
                    LogUtils.logDebug(TAG, "SocketSenderThread flush done");
                }
                //<<>>
//            } catch (SocketException e) {
//                // socket连接出问题，需要重连
//                LogUtils.logError(TAG, "socket write failed and try to reconnect");
//                LogUtils.logError(TAG, e.getMessage(), e);
//            } catch (IOException e) {
//                LogUtils.logError(TAG, "socket io exception and try to reconnect");
//                LogUtils.logError(TAG, e.getMessage(), e);
            } catch (Throwable  e) {
                LogUtils.logError(TAG, e.getMessage(), e);
            } finally {
                //<<>>
//                IoStreamUtils.closeSilently(mOutputStream);
                markSocketError();
            }

            LogUtils.logDebug(TAG, "SocketSenderThread exit");
        }
    }

    class HeartBeatThread extends Thread {

        private boolean needExit = false;

        private void exit() {
            LogUtils.logDebug(TAG, "HeartBeatThread exit()");
            try {
                needExit = true;
                interrupt();
            } catch (Exception e) {
                LogUtils.logError(TAG, e.getMessage(), e);
            }
        }

        @Override
        public void run() {
            while (true) {

                if (needExit) {
                    break;
                }

                try {
                    Thread.sleep(10000);
                    addToSocketClientBufferQueue("HeartBoom".getBytes());
                } catch (Exception e) {
                    LogUtils.logError(TAG, e.getMessage(), e);
                }
            }
        }
    }
}
