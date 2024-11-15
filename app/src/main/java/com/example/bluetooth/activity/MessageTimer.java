package com.example.bluetooth.activity;

import android.content.Context;
import android.util.Log;

import com.example.bluetooth.R;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by we25 on 2017-10-16.
 */

// SingleTon : 객체를 여러개 만들지 않고, 한 개만 만들어서 사용할 떄
public class MessageTimer {
    // 메시지 ID 정의
    public static final int NONE = 0;
    public static final int RETRIEVE_LED_STATUS_RESP = 0x50;
    public static final int RETRIEVE_LED_STATUS_REQ = 0x40;
    public static final int CHANGE_LED_STATUS_RESP = 0x51;
    public static final int CHANGE_LED_STATUS_REQ = 0x41;
    public static final int REPORT_SENGING_VALUE_IND = 0x10;

    private int mCommand;       // 어떤 명령어를 아두이노쪽으로 전송
    private long mDuration = 1000L;     // 명령어에 대한 Timeout 시간을 저장
    private Timer timer;        // Timer 구동하기 위한 클래스 변수 선언
    private Context mContext;   // Activity를 저장 ㅣ DeviceControlActivity Context 저장
    private static MessageTimer instance;   // 참조변수
    public static MessageTimer getInstance() {
        if(instance == null)
            instance = new MessageTimer();
        return instance;
    }
    private MessageTimer() { }

    public void init(Context context) {
        mContext = context;
        mCommand = NONE;
    }

    public boolean start(int command) {
        if(mCommand != NONE)
            return false;
        else {
            mCommand = command;
            if(timer == null)
                timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if(mCommand == RETRIEVE_LED_STATUS_REQ)
                        Log.w("debug", mContext.getString(R.string.timeout_ret_led_status));
                    else if(mCommand == CHANGE_LED_STATUS_REQ)
                        Log.w("debug", mContext.getString(R.string.timeout_chg_led_status));
                    else
                        Log.w("debug", mContext.getString(R.string.invalid_command));
                    mCommand = NONE;
                }
            }, mDuration);
            return true;
        }
    }

    public boolean isRunning(int command) {
        if(timer != null && mCommand == command)
            return true;
        else
            return false;
    }

    public void stop() {
        timer.cancel();
        timer = null;
        mCommand = NONE;
    }
}
