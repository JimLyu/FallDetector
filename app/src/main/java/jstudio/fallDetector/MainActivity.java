package jstudio.fallDetector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {

    private final String DEFAULT_UUID = "db69b1af-6cd4-4aae-9e44-4fe611808817";
    private final int NOTIFICATION_SERVICE_ID = 40;
    private final int UPLOAD_CD = 60*1000;   //每60秒上傳一次
    private final int POST_FREQ = 100;  //繪圖頻率
    static final double HIGH_THRESHOLD = 1.8;  //1.8*G
    static final double LOW_THRESHOLD = -0.4;  //-0.4*G
    static final int NAME_LIMIT = 12;
    static String defaultName = Build.MODEL;

    static MainHandler handler;
    SharedPreferences settings;
    Socket tcpSocket;
    private UUID uuid;
    private String userName;

    LinearLayout linearlayout;
    HorizontalScrollView plotScrollView;
    PlotView plotView = null;
    SensorManager sensorManager;
    NotificationManager notificationManager;
    Sensor acSensor, gvSensor;
    SensorEventListener acListener, gvListener;
    String serverSays = "";
    ImageButton btnStart;
    TextView txtConnectionState, txtButtonInfo;
    AbstractMap.SimpleEntry<Long, Float> acceleration;
    float highest_ac = 0;                    // G的倍數
    float[] linearAcceleration, gravity;    // m/s2
    MsgAcceptThread msgAcceptThread;
    SQLRecordThread     sqlRecordThread;
    int falling_state = 0;
    long onCreateTime, fallTime = 0;
    Vibrator vibrator;
    boolean falling = false; //FallActivity是否正在執行
//    long tempTime = 0;
    private PowerManager.WakeLock mWakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialize();
        loadUser();
        connect();
        settings.edit().remove(SettingsActivity.NET_STATE).apply();
        changeBackgroundColor();
//        settings.edit().clear().apply();//清除設定
    }

    @Override
    protected void onDestroy() {
        notificationManager.cancelAll();
        unregisterListeners();
        closeSocket();
        handler.removeCallbacks(handler.upload);
        sqlRecordThread.close();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if(keyCode == KeyEvent.KEYCODE_BACK)
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("結束" + getString(R.string.app_name))
                    .setMessage("確定結束應用程式")
                    .setIcon(R.drawable.ic_directions_run_black_48dp)
                    .setPositiveButton("確定",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {finish();}
                            })
                    .setNegativeButton("取消",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {}
                    })
                    .show();
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        changeBackgroundColor();
        falling = false;
        String time = String.valueOf(intent.getLongExtra("time", 0));
        boolean fall = intent.getBooleanExtra("fall", false);
        if(fall)//求救
            sendSMS();
        new MsgSendThread("/return " + time  + " " + fall).start();//回報
    }

    private void changeBackgroundColor(){
        String colorString = PreferenceManager.getDefaultSharedPreferences(this).getString(SettingsActivity.USER_THEME,
                getResources().getStringArray(R.array.set_user_theme_value)[0]);
        findViewById(R.id.activity_main).setBackgroundColor(Color.parseColor(colorString));
    }

    private void initialize() {
        onCreateTime = System.currentTimeMillis();
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        linearlayout = (LinearLayout) findViewById(R.id.chart);
        plotScrollView = (HorizontalScrollView) findViewById(R.id.plotScrollView);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        acSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gvSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        btnStart = (ImageButton) findViewById(R.id.btnStart);
        btnStart.setOnClickListener(lsrDisable);
        handler = new MainHandler();
        linearAcceleration = new float[3];
        gravity = new float[3];
        gravity[2] = 9.81f;
        txtConnectionState = (TextView) findViewById(R.id.connectingState);
        txtConnectionState.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connect();
            }
        });
        txtButtonInfo = (TextView) findViewById(R.id.info);
//        dataRecordThread = new DataRecordThread();
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        sqlRecordThread = new SQLRecordThread();
        Button test = (Button) findViewById(R.id.button);
        test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                startActivity(new Intent().setClass(MainActivity.this, FallActivity.class));
//                sendSMS();
                String s = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString(SettingsActivity.USER_UUID, "");
                Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show();
            }
        });
        ImageButton imageButton = (ImageButton) findViewById(R.id.settings);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent().setClass(MainActivity.this, SettingsActivity.class));
            }
        });
        mWakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getCanonicalName());
    }

    static void log(Object msg) {
        Log.e("AndroidRuntime", "Log：" +  String.valueOf(msg));
    }

    /*SensorListener*/
    private void registerListeners() {
        /*Chart*/
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(linearlayout.getWidth(), linearlayout.getHeight());//設定Canvas的大小
        plotView = new PlotView(MainActivity.this, linearlayout.getHeight(), linearlayout.getWidth());
        linearlayout.addView(plotView, params);
        /*Acceleration*/
        acListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                /*記錄*/
                sqlRecordThread.put(System.currentTimeMillis(), sensorEvent.values, gravity);
                linearAcceleration = sensorEvent.values;
//                log("time = " + (System.currentTimeMillis()-tempTime));
//                tempTime = System.currentTimeMillis();
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
                //do nothing
            }
        };
        sensorManager.registerListener(acListener, acSensor, SensorManager.SENSOR_DELAY_FASTEST, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        /*gravity*/
        gvListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                gravity = sensorEvent.values;
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };
        sensorManager.registerListener(gvListener, gvSensor, SensorManager.SENSOR_DELAY_FASTEST, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        /*發通知*/
        notification("偵測跌倒中");
    }

    /*unregister*/
    private void unregisterListeners() {
        sensorManager.unregisterListener(acListener);
        sensorManager.unregisterListener(gvListener);
        plotView = null;
        linearlayout.removeAllViews();
        notificationManager.cancelAll();
    }

    /*顯示通知*/
    private void notification(String text){
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setContentTitle("Fall Detector")
                        .setContentText(text)
                        .setOngoing(true)
                        .setProgress(0, 0, true);
        //Android 5.0以上用固定的圖示
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setSmallIcon(R.drawable.ic_directions_run_black_48dp);
            mBuilder.setVibrate(new long[0]);
        } else {
            mBuilder.setSmallIcon(R.drawable.walker);
        }
//        mBuilder.setPriority(Notification.PRIORITY_HIGH);

// Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, MainActivity.class);
// add the followed two lines to resume the app same with previous statues
        resultIntent.setAction(Intent.ACTION_MAIN);
        resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        mBuilder.setContentIntent(
                PendingIntent.getActivity(MainActivity.this, 0, resultIntent,PendingIntent.FLAG_UPDATE_CURRENT));
//      mBuilder.build().notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        notificationManager.notify(NOTIFICATION_SERVICE_ID, mBuilder.build());
    }

    void sendSMS(){
        final String number = settings.getString(SettingsActivity.SMS_NUMBER, "").replaceFirst("[0]", "+886");
        final String content = settings.getString(SettingsActivity.SMS_CONTENT, getResources().getString(R.string.set_sms_content_default));
        try{
            SmsManager.getDefault().sendTextMessage(number, null, content, null, null);
        }catch (IllegalArgumentException i){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "destinationAddress or text are empty", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "SMS: [" + number + "]" + "[" + content + "]", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /*讀取UUID及使用者名稱*/
    private void loadUser() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String id = settings.getString(SettingsActivity.USER_UUID, DEFAULT_UUID);
        if(DEFAULT_UUID.equals(id)){
            log("第一次執行");
            uuid = UUID.randomUUID();
            settings.edit().putString(SettingsActivity.USER_UUID, uuid.toString()).apply();
            /*輸入使用者名稱*/
            final View layout = LayoutInflater.from(MainActivity.this).inflate(R.layout.layout_selectfile, null);
            final EditText editText = (EditText) layout.findViewById(R.id.editText);
            editText.setText(defaultName);
            new AlertDialog.Builder(this)
                    .setTitle(getResources().getString(R.string.name_input) + NAME_LIMIT + getResources().getString(R.string.name_limit))
                    .setView(layout)
                    .setPositiveButton("確認", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            updateUserName(editText.getText().toString());
                        }
                    }).show();
            updateUserName(defaultName);
        }else{
            uuid = UUID.fromString(id);
            userName = settings.getString(SettingsActivity.USER_NAME, defaultName);
            ((TextView)findViewById(R.id.userName)).setText(userName);
        }
    }

    /*更改名稱*/
    void updateUserName(String name){
        userName = defaultName;
        if (name.length() == 0)
            Toast.makeText(MainActivity.this, "輸入無效，使用預設名稱：" + defaultName, Toast.LENGTH_SHORT).show();
        else if(name.matches(".*[[\\s]\\Q/&*#?<>.*\"\\|:\\E].*")) {// \s /&*#?<>.*"\:都不行
            userName = name.replaceAll("[[\\s]\\Q/&*#?<>.*\"\\|:\\E]", "");
            Toast.makeText(MainActivity.this, "不得包含特殊字元及空格", Toast.LENGTH_SHORT).show();
        }else
            userName = name;
        if(userName.length() >= NAME_LIMIT)
            userName = userName.substring(0, NAME_LIMIT);
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(SettingsActivity.USER_NAME, userName).apply();
        ((TextView) (findViewById(R.id.userName))).setText(userName);
        new MsgSendThread("/register " + uuid.toString() + " " + userName).start();//再次註冊以更名
    }

    /*連接伺服器*/
    void connect(){
        String ip  = settings.getString(SettingsActivity.NET_IP, getResources().getString(R.string.defaultIp));
        int state = Integer.parseInt(settings.getString(SettingsActivity.NET_STATE, "0"));
        new ConnectToServer(ip, state);
    }

    void changeState(final int state){
        switch (state){
            case 0://未連接
                txtConnectionState.setText(R.string.disconnecting);
                txtConnectionState.setTextColor(Color.RED);
                break;
            case 1://連接中
                txtConnectionState.setText(R.string.connecting);
                txtConnectionState.setTextColor(Color.YELLOW);
                break;
            case 2://已連接
                txtConnectionState.setText(R.string.connected);
                txtConnectionState.setTextColor(Color.GREEN);
                break;
        }
        PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit()
                .putString(SettingsActivity.NET_STATE, String.valueOf(state)).apply();
    }

    /*關閉socket*/
    private void closeSocket() {
        if (tcpSocket != null) {
            try {
                tcpSocket.close();
                tcpSocket = null;
                log("TCP socket關閉成功");
            } catch (IOException e) {
                log("TCP socket關閉失敗");
            }
        }
        txtConnectionState.setText(R.string.disconnecting);
        txtConnectionState.setTextColor(Color.RED);
    }
    /*連線Listener*/
    View.OnClickListener lsrDisable = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Toast.makeText(MainActivity.this, "無網路連線，請等連線成功後再試一次", Toast.LENGTH_SHORT).show();
            connect();
        }
    };
    /*啟動Listener*/
    View.OnClickListener lsrStart = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mWakeLock.acquire();//電力控制

            registerListeners();
            btnStart.setImageResource(R.drawable.btn_stop);
            btnStart.setOnClickListener(lsrStop);
        }
    };
    /*停止Listener*/
    View.OnClickListener lsrStop = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mWakeLock.release();

            unregisterListeners();
            btnStart.setImageResource(R.drawable.btn_start);
            btnStart.setOnClickListener(lsrStart);
        }
    };

    //重力絕對值平方
    private double gravity_square() {
        return Math.pow(gravity[0], 2) + Math.pow(gravity[1], 2) + Math.pow(gravity[2], 2);
    }

    //向量絕對值
    private double abs(double[] dv) {
        return Math.sqrt(Math.pow(dv[0], 2) + Math.pow(dv[1], 2) + Math.pow(dv[2], 2));
    }

    //向量投影
    private double projection(double[] a, double[] b) {
        return dot(a, b) / abs(b);
    }

    //向量內積
    private double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /*Chart*/
    private class PlotView extends View {

        private Paint[] paint;
        private long[] timeArray;
        private float[] dataArray;//資料

        private int index;
        public int scaleY, scaleX = 2;
        private int ViewHeight, ViewWidth;
        private int windowWidth;

        private final int SAMPLE = 1;//顯示的取樣頻率
        private final double f = 0.01d; //1ms有幾像素

        boolean onTouch = false;

        public  PlotView(Context context, int height, int width) {
            super(context);
            setWillNotDraw(false);

            paint = new Paint[5];
            for (int i = 0; i < 5; i++) {
                paint[i] = new Paint();//0=X, 1=Y, 2=Z
                paint[i].setAntiAlias(true);
                paint[i].setStrokeWidth(3);
            }
            paint[0].setColor(Color.RED);
            paint[1].setColor(Color.CYAN);  //#00FFFF 藍綠色
            paint[2].setColor(Color.LTGRAY);
            paint[3].setColor(Color.TRANSPARENT);
            paint[4].setColor(Color.WHITE);
            paint[4].setAlpha(50);

            ViewHeight = height;
            ViewWidth = width;
            index = 1;

            /*控制刻度*/
            scaleY = height / 8;
            int size = width / scaleX;

            dataArray = new float[size];
            timeArray = new long[size];
            for (int i = 0; i < size; i++){
                timeArray[i] = 0;
                dataArray[i] = 0;
            }

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            windowWidth = metrics.widthPixels; //取得螢幕寬度

            setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    switch (motionEvent.getAction()){
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_MOVE:
                            return onTouch = true;
                        case MotionEvent.ACTION_UP:
                            return onTouch = false;
                        default:
                            return false;
                    }
                }
            });

        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int p;  //選擇畫筆參數
            canvas.drawLine(index*scaleX + 10, 0, index*scaleX + 10, ViewHeight, paint[4]);
            for (int k = 0; (k + 1)*scaleX + 10 < canvas.getWidth(); k += SAMPLE) {
//                if (dataArray[k] > HIGH_THRESHOLD)
//                    p = 0;
//                else if (dataArray[k] < LOW_THRESHOLD)
//                    p = 2;
//                else if(k >= index && k <= index + windowWidth/10)
//                    p = 3;//透明
//                else
//                    p = 1;
                p = (k >= index && k <= index + windowWidth/10)? 3 : 1;
                p = (k > index + windowWidth/10)? 2 : p;
                if(dataArray[k+1] != 0)
                    canvas.drawLine(k*scaleX + 10, ViewHeight/2 - dataArray[k]*scaleY,
                            (k + 1)*scaleX + 10, ViewHeight/2 - dataArray[k + 1] * scaleY, paint[p]);
            }
            /*自動滾動*/
            if(!onTouch)
                plotScrollView.scrollTo(index*scaleX - windowWidth/2, 0);
        }

        public void add() {//資料間距 = 1000/POST_FREQ
            index = (index >= dataArray.length -1) ? 1 : index + 1;
            timeArray[index] = MainActivity.this.acceleration.getKey();
            dataArray[index] = MainActivity.this.acceleration.getValue();
        }
    }

    /*Message 傳送Thread*/
    private class MsgSendThread extends Thread{
        private String msg = null;

        MsgSendThread(String msg){
            this.msg = msg;
        }

        @Override
        public void run() {
            if(tcpSocket != null){
                try{
                    PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(tcpSocket.getOutputStream(), "UTF-8")), true);
                    writer.println(msg);
                    writer.flush();
                    log("Send to Server: " + msg);
                }catch(IOException i){
                    log("發送訊息失敗！549");
                    i.printStackTrace();
                }
            }
        }
    }

    /*Msg接收Thread*/
    private class MsgAcceptThread extends Thread {
        String buffer = null;
        MsgAcceptThread() {
            super();
        }
        @Override
        public void run() {
            try {
                while (tcpSocket != null) {
                    buffer = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream())).readLine();
                    if (buffer != null) {
                        log("Server says: " + buffer);
                        serverSays = buffer;
                        interpreter(buffer.split("\\s"));
                    } else {
                        disConnected();
                        break;
                    }
                }
            } catch (IOException e) {
                disConnected();
            }
        }

        void interpreter(String[] msg){
            try{
                if(msg[0].equals("/fall")){ // "/fall fallTime sms"
                    if(!falling){
                        falling = true;
                        startActivity(new Intent().setClass(MainActivity.this, FallActivity.class).putExtra("time", Long.parseLong(msg[1])));
                    }
                }else if(msg[0].equals("/receive")){
                    long start = Long.parseLong(msg[1]);
                    long end  = Long.parseLong(msg[2]);
                    sqlRecordThread.delete(start, end);
                }else if(msg[0].equals("/stream")){
                    new DataStreamThread().start();
                }
            }catch (ArrayIndexOutOfBoundsException a){
                log("Server msg 參數錯誤");
            }
        }

        void disConnected(){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    changeState(0);
//                    Toast.makeText(MainActivity.this, "失去連線", Toast.LENGTH_SHORT).show();
                    closeSocket();
                }
            });
        }
    }

    /*SQLite記錄*/
    private class SQLRecordThread extends Thread {
        private final long BEFORE = 500;    //跌倒高峰前後的時限(單位：ms)
        private final long AFTER = 1500;

        private boolean interrupted = false;
        private ArrayDeque<Long> uploadingFallTime;
        private DataSheet dataSheet;

        private long postTime = 0;

        private SQLiteClient sqlClient;

        SQLRecordThread() {
            sqlClient = new SQLiteClient(MainActivity.this);
            log("sqlCount = " + sqlClient.getCount());
            uploadingFallTime = new ArrayDeque<>();
            dataSheet = new DataSheet();
            setPriority(1);
            start();
        }

        @Override
        public void run() {
            while (!interrupted) {//SQL insert
                if(acceleration != null){
                    switch (falling_state) {
                        case 0://正常情況下
                            if(acceleration.getValue() > HIGH_THRESHOLD){
                                fallTime = acceleration.getKey();
                                highest_ac = acceleration.getValue();
                                falling_state = 1;
                            }
                            break;
                        case 1://跌倒高峰中
                            if((System.currentTimeMillis() - fallTime) > AFTER){
                                falling_state = 0;
                                highest_ac = 0;
                            }else{
                                if(acceleration.getValue() > highest_ac){
                                    fallTime = acceleration.getKey();
                                    highest_ac = acceleration.getValue();
                                }
                                if(acceleration.getValue() <= LOW_THRESHOLD){
                                    String value = String.format(Locale.TAIWAN, " high:%.2f low:%.2f", highest_ac, acceleration.getValue());
                                    log("Fall Threshold at " + System.currentTimeMillis() + value);
                                    falling_state = 0;//Falling! 繼續判斷
                                    highest_ac = 0;
                                    uploadingFallTime.add(fallTime);
//                                    startActivity(new Intent().setClass(MainActivity.this, FallActivity.class));
                                }
                            }
                            break;
                    }
                /*檢查有沒有可以上傳的跌倒*/
                    Long t = uploadingFallTime.peekFirst();
                    if(t != null && acceleration != null)
                        if(acceleration.getKey()-t >= AFTER) {//最新的資料時間距跌倒已超過1.5秒
                            uploadingFallTime.removeFirst();
                            DataSheet upload;
                            if(dataSheet.getStart() > (t-BEFORE)){//表示前面可能剛發生跌倒，dataSheet物件被重建了
                                upload = sqlClient.getByTime(t); //從跌倒前0.5秒到最後全部
                                for(int i = 0; i < dataSheet.getSize(); i++){
                                    if(dataSheet.get(i).getKey() <= (t+AFTER))
                                        upload.add(dataSheet.getData(i));
                                    else
                                        break;
                                }
                            }else{
                                upload = dataSheet.setFall(t);
                                dataSheet = new DataSheet();        //換新的
                            }
                            new DataUploadThread(upload);  //自動start
                        }
                    /*2.5秒前的data可以存入資料庫了*/
                    if(dataSheet.getStart() < System.currentTimeMillis()-(BEFORE+AFTER)){
                        DataSheet.Data data = dataSheet.pollFirst();
                        if(data != null)
                            sqlClient.insert(data.time, data.ax, data.ay, data.az, data.gx, data.gy, data.gz);
                    }
//                    log("size = " + dataSheet.getSize());
                    /*繪圖*/
                    if(System.currentTimeMillis()-postTime > (1000/POST_FREQ) && plotView != null){
                        /*繪圖*/
                        plotView.add();
                        plotView.postInvalidate();
                        postTime = System.currentTimeMillis();
                    }
                }
            }
        }

        void put(long time, float[] ac, float[] gv) {
            acceleration = dataSheet.add(time, ac, gv);
        }

        long insertAll(){
            long id = 0;
            DataSheet.Data data;
            while((data = dataSheet.pollFirst())!= null){
                id = sqlClient.insert(data.time, data.ax, data.ay, data.az, data.gx, data.gy, data.gz);
            }
            return id;
        }

        int count() {
            return sqlClient.getCount();
        }

        long getStart(){
            return sqlClient.getStart();
        }

        void upload(long time) {//parameter: 所需上傳的最大時間； limit：假定頻率*上傳間隔
            DataSheet data = sqlClient.getByTime(-1, time, UPLOAD_CD*POST_FREQ/1000);
            new DataUploadThread(data, time);   //自動start
        }

        void delete(long start, long end){
            sqlClient.delete(start, end);
            handler.post(handler.upload);   //刪除之後再檢查一次
        }

        void close() {
            interrupted = true;
            insertAll();
            sqlClient.close();
        }
    }

    /*Data上傳雲端 (0606重作)*/
    private class DataUploadThread extends Thread {
        private final int TIMEOUT = 10000;
        private DataSheet dataSheet;
        private Socket dataSocket;
        private long startTime, eof = -1;

        DataUploadThread(DataSheet dataSheet) {//Fall專用
            if(!dataSheet.isEmpty()){
                this.dataSheet = dataSheet;
                new MsgSendThread("/fall " + dataSheet.getStart()).start();
                startTime = System.currentTimeMillis();
                start();
            }
        }

        DataUploadThread(DataSheet dataSheet, long eof) {//定時上傳專用
            if(!dataSheet.isEmpty()){
                this.dataSheet = dataSheet;
                this.eof = eof;
                new MsgSendThread("/data " + dataSheet.getStart() + " " + dataSheet.getEnd()).start();
                startTime = System.currentTimeMillis();
                start();
            }
        }

        @Override
        public void run() {
            String accept = "/accept " + dataSheet.getStart() + " ";
            dataSocket = new Socket();
            try {
                while(tcpSocket != null && tcpSocket.isConnected()){    //主通道暢通中
                    if(System.currentTimeMillis()-startTime > TIMEOUT){
                        log("786等待上傳逾時 " + dataSheet.getStart());
                        break;
                    }else if((serverSays).matches(accept + ".*")){
                        int port = Integer.parseInt(serverSays.replaceFirst(accept, ""));   //由server動態分配port
                        serverSays = "";
                        dataSocket.connect(new InetSocketAddress(tcpSocket.getInetAddress(), port), TIMEOUT);//timeout = 10秒
                        if(dataSocket.isConnected()){
                            ObjectOutputStream outputStream = new ObjectOutputStream(dataSocket.getOutputStream());
                            outputStream.writeObject(dataSheet);
                            outputStream.flush();//傳送
                            outputStream.close();
                            if(eof == -1)
                                log(dataSheet.getStart() + "發送完畢[fall at " + dataSheet.getFallTime() + "]");
                            else
                                log(dataSheet.getStart() + "發送完畢[data size = " + dataSheet.getSize() + "]");
                            break;
                        }
                    }
                }
            }catch (SocketTimeoutException s){
                log("data port連線逾時 " + dataSheet.getStart());
            }catch (IOException e) {    //bind?
                log("774" + e.toString());
            }
        }

    }

    /*串流資料*/
    class DataStreamThread extends Thread{
        private final int TIMEOUT = 10000;
        private final int PORT = 42449;//
        private Socket dataSocket;
        private long last;

        DataStreamThread(){
            dataSocket = new Socket();
            last = System.currentTimeMillis();
        }

        @Override
        public void run() {
            try {
                dataSocket.connect(new InetSocketAddress(tcpSocket.getInetAddress(), PORT), TIMEOUT);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), "UTF-8"));
                while(tcpSocket != null){
                    long now = System.currentTimeMillis();
                    if(now-last > 1000/POST_FREQ){
                        String dataString = String.valueOf(now);
                        for(int i = 0; i < 3; i++)
                            dataString += " " + String.format(Locale.TAIWAN, "%.6f", linearAcceleration[i]);
                        for(int j = 0; j < 3; j++)
                            dataString += " "+ String.format(Locale.TAIWAN, "%.6f", gravity[j]);
                        String ac = String.format(Locale.TAIWAN, " %.6f", ((linearAcceleration[0]*gravity[0] + linearAcceleration[1]*gravity[1] + linearAcceleration[2]*gravity[2])/96.2361d));
                        writer.write(dataString + ac + "\n");
                        writer.flush();
                        last = now;
                        log(dataString + ac);
                    }
                }
            } catch (IOException e) {
                log("Stop Stream");
            }
        }
    }

    /*Handler*/
    class MainHandler extends Handler{

        final int WRONG_IP = -1;
        final int DISCONNECT = 0;
        final int CONNECTING = 1;
        final int CONNECTED = 2;

        final int UPDATE_NAME = 4;

        MainHandler(){
            super();
        }        
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case WRONG_IP:
                    Toast.makeText(MainActivity.this, "IP位址不存在", Toast.LENGTH_SHORT).show();
                    break;
                case CONNECTED:
                    tcpSocket = (Socket) msg.obj;
                    new MsgSendThread("/register " + uuid.toString() + " " + userName).start();
                    log("向伺服器註冊使用者資料");
                    msgAcceptThread = new MsgAcceptThread();
                    msgAcceptThread.start();
                    log("開啟MsgAcceptThread");
                    MainHandler.this.post(upload);
                    changeButtonState(true);
                case CONNECTING:
                case DISCONNECT:
                    changeState(msg.what);
                    break;
                case UPDATE_NAME:
                    updateUserName((String)msg.obj);
                    break;
            }
        }

        Runnable upload = new Runnable() {
            @Override
            public void run() {
                MainHandler.this.removeCallbacks(this);         //先取消之前的post
                long now = System.currentTimeMillis();
                long sqlTime = sqlRecordThread.getStart();
                if (sqlTime != 0 && sqlTime+UPLOAD_CD < now)   //sqlTime=0表示資料內庫無資料
                    sqlRecordThread.upload(now);
                MainHandler.this.postDelayed(this, UPLOAD_CD);  //無論如何都先預約一次
            }
        };

        void changeButtonState(boolean enable){
            if(enable){
                btnStart.setImageResource(R.drawable.btn_start);
                btnStart.setOnClickListener(lsrStart);
                txtButtonInfo.setText(getResources().getString(R.string.btnStart));
            }else{
                btnStart.setImageResource(R.drawable.btn_disable);
                btnStart.setOnClickListener(lsrDisable);
                txtButtonInfo.setText(getResources().getString(R.string.btnDisable));
            }
        }
    }
}
