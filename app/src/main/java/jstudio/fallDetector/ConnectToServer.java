package jstudio.fallDetector;

import android.graphics.Color;
import android.os.Message;
import android.preference.PreferenceManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Created by LABUSE on 2017/6/22.
 */

class ConnectToServer extends Thread {
    private final int PORT = 5024;
    private final int TRY = 20;
    private final int TIMEOUT = 1;//秒

    private int times = 0;
    private Socket socket;
    private String ip;

    ConnectToServer(String ip, int state) {
        super();
        this.ip = ip;
        if(state == 0)
            start();
    }

    @Override
    public void run() {
        MainActivity.log("TCP連接中... ip = " + ip);
        sendMessage(1);
        while (times < TRY) {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(InetAddress.getByName(ip), PORT), TIMEOUT*1000);//timeout = 1秒
                if(socket.isConnected())
                    break;
            }catch (UnknownHostException u){
                MainActivity.log("IP位址不存在：ip = " + ip);
                sendMessage(-1);
                break;
            }catch (SocketTimeoutException s) {
                times++;
            }catch (IOException e) {
                MainActivity.log("連線錯誤52" + e.toString());
                times++;
            }
        }
        MainActivity.log("TCP停止連接");
        if (socket.isConnected())//TCP連接成功
            sendMessage(2);
        else{
            socket = null;
            sendMessage(0);
        }

    }

    void stopConnecting() {
        times = TRY;
    }

    private void sendMessage(int state) {//0為連接失敗；2為連接成功；-1為IP錯誤
        Message msg = new Message();
        msg.what = state;
        msg.obj = socket;
        MainActivity.handler.sendMessage(msg);
    }
}
