package jstudio.fallDetector;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.v7.widget.AppCompatImageView;
import android.telephony.SmsManager;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import static java.lang.Thread.sleep;
import static java.lang.Thread.yield;

public class FallActivity extends Activity {

    private int countdown = 20;
    private long fallTime = 0;
    Handler handler;
    TextView fallCountdown, txtHint;
    ImageView sms;
    Vibrator vibrator;
    ImageView report;
    ImageView call;
    ImageView fine;
    boolean reported = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fall);
        fallTime = getIntent().getLongExtra("time", 0);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        //動畫
        ImageView icon = (ImageView) findViewById(R.id.countdownIcon);
        icon.setImageResource(R.drawable.fall_activity);
        AnimationDrawable anim = (AnimationDrawable)icon.getDrawable();
        anim.start();

        report = (ImageView) findViewById(R.id.report);
        report.setImageResource(R.drawable.fall_report);
       ((AnimationDrawable) report.getDrawable()).start();

        txtHint = (TextView) findViewById(R.id.txtHint);
        call = (ImageView) findViewById(R.id.call);
        fine = (ImageView) findViewById(R.id.fine);
        call.setOnTouchListener(new DragListener(true));
        fine.setOnTouchListener(new DragListener(false));

        final Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        fallCountdown = (TextView) findViewById(R.id.fallCountdown);

        vibrator.vibrate(countdown * 1000);

        new Thread(new Runnable() {
            long time = System.currentTimeMillis();
            String text = String.valueOf(countdown);
            @Override
            public void run() {
                while((System.currentTimeMillis()-time)/1000 < countdown && !reported) {
                    if (!text.equals(String.valueOf(countdown - (System.currentTimeMillis() - time) / 1000))) {
                        text = String.valueOf(countdown - (System.currentTimeMillis() - time) / 1000);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                fallCountdown.setText(text);
                            }
                        });
                    }
                }
                if(!reported){
                    startActivity(new Intent().setClass(FallActivity.this, MainActivity.class)
                            .putExtra("time", fallTime).putExtra("fall", true));
                    finish();
                }

            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        vibrator.cancel();
        countdown = 0;
        super.onDestroy();
    }

    class DragListener implements View.OnTouchListener{
        boolean isCall;
        boolean actCall = false, actFine = false;
        int ox = -1;   // 原本圖片存在的X,Y軸位置
        float x, y;     // 觸碰的位置
        int mx, my;     // 圖片被拖曳的X ,Y軸距離長度
        DragListener(boolean isCall){
            this.isCall = isCall;
        }
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            ox = ox==-1? (int)view.getX() : ox;
            switch (event.getAction()) {          //判斷觸控的動作
                case MotionEvent.ACTION_DOWN:// 按下圖片時
                    x = event.getX();                  //觸控的X軸位置
                case MotionEvent.ACTION_MOVE:// 移動圖片時
                    //getX()：是獲取當前控件(View)的座標
                    //getRawX()：是獲取相對顯示螢幕左上角的座標
                    mx = (int) (event.getRawX() - x);
                    if(isCall){
                        fine.setOnTouchListener(null);      //其中一個移動時，另一個不能動
                        mx = (mx <= ox)? ox : mx;//左底線
                        if(mx > (report.getWidth()/2)-view.getWidth()){
                            mx = ox + (report.getWidth()-view.getWidth())/2;
                            report.setImageResource(R.drawable.fall_report_call);
                            call.setAlpha(0.5f);
                            actCall = true;
//                            txtHint.setTextColor(Color.RED);
//                            txtHint.setText(R.string.call);
                        }else {
                            report.setImageResource(R.drawable.fall_report);
                            call.setAlpha(1f);
                            actCall = false;
//                            txtHint.setText("");
                        }
                    }else{
                        call.setOnTouchListener(null);      //其中一個移動時，另一個不能動
                        mx = (mx >= ox)? ox : mx;//右底線
                        if(mx < (report.getWidth()/2)){
                            mx = ox - (report.getWidth()-view.getWidth())/2;
                            report.setImageResource(R.drawable.fall_report_fine);
                            fine.setAlpha(0.5f);
                            actFine = true;
//                            txtHint.setTextColor(Color.GREEN);
//                            txtHint.setText(R.string.fine);
                        }else {
                            report.setImageResource(R.drawable.fall_report);
                            fine.setAlpha(1f);
                            actFine = false;
//                            txtHint.setText("");
                        }
                    }
                    view.setX(mx);
//                    view.layout(mx, (int) view.getY(), mx + view.getWidth(), (int) view.getY() + view.getHeight());
                    return true;
                case MotionEvent.ACTION_UP:// 放開圖片時
                    if(actCall){
                        vibrator.cancel();
                        reported = true;
                        startActivity(new Intent().setClass(FallActivity.this, MainActivity.class)
                                .putExtra("time", fallTime).putExtra("fall", true));
                        finish();
                    }else if(actFine) {
                        vibrator.cancel();
                        reported = true;
                        startActivity(new Intent().setClass(FallActivity.this, MainActivity.class)
                                .putExtra("time", fallTime).putExtra("fall", false));
                        finish();
                    }else{
                        mx = ox;
                        view.setX(ox);
//                        view.layout(mx, (int) view.getY(), mx + view.getWidth(), (int) view.getY() + view.getHeight());
                    }
                    if(isCall)
                        fine.setOnTouchListener(new DragListener(false));
                    else
                        call.setOnTouchListener(new DragListener(true));
                    return true;
            }
            return false;
        }

    }

}
