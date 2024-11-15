package com.example.bluetooth.activity;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.bluetooth.DeviceData;
import com.example.bluetooth.R;
import com.example.bluetooth.bluetooth.DeviceConnector;
import com.example.bluetooth.bluetooth.DeviceListActivity;
import com.example.bluetooth.Utils;
import com.pedro.library.AutoPermissions;
import com.pedro.library.AutoPermissionsListener;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class DeviceControlActivity extends BaseActivity implements AutoPermissionsListener {
    private static final String DEVICE_NAME = "DEVICE_NAME";
    private static final String LOG = "LOG";

    // Подсветка crc
    private static final String CRC_OK = "#FFFF00";
    private static final String CRC_BAD = "#FF0000";

    private static final SimpleDateFormat timeformat = new SimpleDateFormat("HH:mm:ss.SSS");

    private static String MSG_NOT_CONNECTED;
    private static String MSG_CONNECTING;
    private static String MSG_CONNECTED;

    private static DeviceConnector connector;
    private static BluetoothResponseHandler mHandler;

    private StringBuilder logHtml;
    private TextView logTextView;
    private TextView tValueView;
    private TextView hValueView;
    private TextView hTimeView;
    private TextView dTimeView;
    private Button ledValueBtn;

    private MessageTimer mMessageTimer; // 참조변수
    private int ledStatus;

    // Настройки приложения
    private boolean hexMode, checkSum, needClean;
    private boolean show_timings, show_direction;
    private String command_ending;
    private String deviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(this, R.xml.settings_activity, false);

        if (mHandler == null) mHandler = new BluetoothResponseHandler(this);
        else mHandler.setTarget(this);

        MSG_NOT_CONNECTED = getString(R.string.msg_not_connected);
        MSG_CONNECTING = getString(R.string.msg_connecting);
        MSG_CONNECTED = getString(R.string.msg_connected);

        setContentView(R.layout.activity_terminal);
        if (isConnected() && (savedInstanceState != null)) {
            setDeviceName(savedInstanceState.getString(DEVICE_NAME));
        } else getActionBar().setSubtitle(MSG_NOT_CONNECTED);

        this.logHtml = new StringBuilder();
        if (savedInstanceState != null) this.logHtml.append(savedInstanceState.getString(LOG));

        this.logTextView = (TextView) findViewById(R.id.log_textview);
        this.logTextView.setMovementMethod(new ScrollingMovementMethod());
        this.logTextView.setText(Html.fromHtml(logHtml.toString()));

        tValueView = (TextView) findViewById(R.id.temperatureValue);
        hValueView = (TextView)findViewById(R.id.humidityValue);
        hTimeView = (TextView)findViewById(R.id.humidityTime);
        dTimeView = (TextView)findViewById(R.id.ledTime);
        ledValueBtn = (Button)findViewById(R.id.ledValue);
        mMessageTimer = MessageTimer.getInstance();
        mMessageTimer.init(this);
        ledStatus = 0;  // OFF

//        byte[] b = {(byte) 199, (byte)197, (byte)216};
//        String s = null;
//        s = Utils.bytesToHex(b, 3);
//
//        byte[] c = new byte[0];
//        c = Utils.hexStringToByteArray(s);
//        Log.w("debug", "byte = " + c.toString());

        AutoPermissions.Companion.loadAllPermissions(this, 101);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        AutoPermissions.Companion.parsePermissions(this, requestCode, permissions, this);
    }

    // ==========================================================================

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DEVICE_NAME, deviceName);
        if (logTextView != null) {
            outState.putString(LOG, logHtml.toString());
        }
    }
    // ============================================================================


    /**
     * Проверка готовности соединения
     */
    private boolean isConnected() {
        return (connector != null) && (connector.getState() == DeviceConnector.STATE_CONNECTED);
    }
    // ==========================================================================


    /**
     * Разорвать соединение
     */
    private void stopConnection() {
        if (connector != null) {
            connector.stop();
            connector = null;
            deviceName = null;
        }
    }
    // ==========================================================================


    /**
     * Список устройств для подключения
     */
    private void startDeviceListActivity() {
        stopConnection();
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }
    // ============================================================================


    /**
     * Обработка аппаратной кнопки "Поиск"
     *
     * @return
     */
    @Override
    public boolean onSearchRequested() {
        if (super.isAdapterReady()) startDeviceListActivity();
        return false;
    }
    // ==========================================================================


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.device_control_activity, menu);
        final MenuItem bluetooth = menu.findItem(R.id.menu_search);
        if (bluetooth != null) bluetooth.setIcon(this.isConnected() ?
                R.drawable.ic_action_device_bluetooth_connected :
                R.drawable.ic_action_device_bluetooth);
        return true;
    }
    // ============================================================================


    @SuppressLint("MissingPermission")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_search) {
            if (super.isAdapterReady()) {
                if (isConnected()) stopConnection();
                else startDeviceListActivity();
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
            return true;
        }
        else if(item.getItemId() == R.id.menu_clear) {
            if (logTextView != null) logTextView.setText("");
            return true;
        }
        else if(item.getItemId() == R.id.menu_send) {
            if (logTextView != null) {
                final String msg = logTextView.getText().toString();
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, msg);
                startActivity(Intent.createChooser(intent, getString(R.string.menu_send)));
            }
            return true;
        }
        else if(item.getItemId() == R.id.menu_settings) {
            final Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        else {
            return super.onOptionsItemSelected(item);
        }
    }
    // ============================================================================


    @Override
    public void onStart() {
        super.onStart();

        // hex mode
        final String mode = Utils.getPrefence(this, getString(R.string.pref_commands_mode));
        this.hexMode = "HEX".equals(mode);
//        if (hexMode) {
//            commandEditText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
//            commandEditText.setFilters(new InputFilter[]{new Utils.InputFilterHex()});
//        } else {
//            commandEditText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
//            commandEditText.setFilters(new InputFilter[]{});
//        }

        // checksum
        final String checkSum = Utils.getPrefence(this, getString(R.string.pref_checksum_mode));
        this.checkSum = "Modulo 256".equals(checkSum);

        // Окончание строки
        this.command_ending = getCommandEnding();

        // Формат отображения лога команд
        this.show_timings = Utils.getBooleanPrefence(this, getString(R.string.pref_log_timing));
        this.show_direction = Utils.getBooleanPrefence(this, getString(R.string.pref_log_direction));
        this.needClean = Utils.getBooleanPrefence(this, getString(R.string.pref_need_clean));
    }
    // ============================================================================


    /**
     * Получить из настроек признак окончания команды
     */
    private String getCommandEnding() {
        String result = Utils.getPrefence(this, getString(R.string.pref_commands_ending));
        if (result.equals("\\r\\n")) result = "\r\n";
        else if (result.equals("\\n")) result = "\n";
        else if (result.equals("\\r")) result = "\r";
        else result = "";
        return result;
    }
    // ============================================================================


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = btAdapter.getRemoteDevice(address);
                    if (super.isAdapterReady() && (connector == null)) setupConnector(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                super.pendingRequestEnableBt = false;
                if (resultCode != Activity.RESULT_OK) {
                    Utils.log("BT not enabled");
                }
                break;
        }
    }
    // ==========================================================================


    /**
     * Установка соединения с устройством
     */
    private void setupConnector(BluetoothDevice connectedDevice) {
        stopConnection();
        try {
            String emptyName = getString(R.string.empty_device_name);
            DeviceData data = new DeviceData(connectedDevice, emptyName);
            connector = new DeviceConnector(data, mHandler);
            connector.connect();
        } catch (IllegalArgumentException e) {
            Utils.log("setupConnector failed: " + e.getMessage());
        }
    }
    // ==========================================================================

    /**
     * Добавление ответа в лог
     *
     * @param message  - текст для отображения
     * @param outgoing - направление передачи
     */
    void appendLog(String message, boolean hexMode, boolean outgoing, boolean clean) {

        StringBuilder msg = new StringBuilder();
        if (show_timings) msg.append("[").append(timeformat.format(new Date())).append("]");
        if (show_direction) {
            final String arrow = (outgoing ? " << " : " >> ");
            msg.append(arrow);
        } else msg.append(" ");

        // Убрать символы переноса строки \r\n
        message = message.replace("\r", "").replace("\n", "");

        // Проверка контрольной суммы ответа
        String crc = "";
        boolean crcOk = false;
        if (checkSum) {
            int crcPos = message.length() - 2;
            crc = message.substring(crcPos);
            message = message.substring(0, crcPos);
            crcOk = outgoing || crc.equals(Utils.calcModulo256(message).toUpperCase());
            if (hexMode) crc = Utils.printHex(crc.toUpperCase());
        }

        // Лог в html
        msg.append("<b>")
                .append(hexMode ? Utils.printHex(message) : message)
                .append(checkSum ? Utils.mark(crc, crcOk ? CRC_OK : CRC_BAD) : "")
                .append("</b>")
                .append("<br>");

        logHtml.append(msg);
        logTextView.append(Html.fromHtml(msg.toString()));

        final int scrollAmount = logTextView.getLayout().getLineTop(logTextView.getLineCount()) - logTextView.getHeight();
        if (scrollAmount > 0)
            logTextView.scrollTo(0, scrollAmount);
        else logTextView.scrollTo(0, 0);
    }
    // =========================================================================


    void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
        getActionBar().setSubtitle(deviceName);
    }

    @Override
    public void onDenied(int i, @NonNull String[] permissions) {
        Toast.makeText(this, "권한 거부: " + permissions.length, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onGranted(int i, @NonNull String[] permissions) {
        Toast.makeText(this, "권한 허용: " + permissions.length, Toast.LENGTH_SHORT).show();
    }
    // ==========================================================================

    /**
     * Обработчик приёма данных от bluetooth-потока
     */
    private class BluetoothResponseHandler extends Handler {
        private WeakReference<DeviceControlActivity> mActivity;

        public BluetoothResponseHandler(DeviceControlActivity activity) {
            mActivity = new WeakReference<DeviceControlActivity>(activity);
        }

        public void setTarget(DeviceControlActivity target) {
            mActivity.clear();
            mActivity = new WeakReference<DeviceControlActivity>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            DeviceControlActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case MESSAGE_STATE_CHANGE:

                        Utils.log("MESSAGE_STATE_CHANGE: " + msg.arg1);
                        final ActionBar bar = activity.getActionBar();
                        switch (msg.arg1) {
                            case DeviceConnector.STATE_CONNECTED:
                                bar.setSubtitle(MSG_CONNECTED);
                                break;
                            case DeviceConnector.STATE_CONNECTING:
                                bar.setSubtitle(MSG_CONNECTING);
                                break;
                            case DeviceConnector.STATE_NONE:
                                bar.setSubtitle(MSG_NOT_CONNECTED);
                                break;
                        }
                        activity.invalidateOptionsMenu();
                        break;

                    case MESSAGE_READ:
                        String readMessage = (String)msg.obj;
                        byte[] rxmsg = Utils.hexStringToByteArray(readMessage);
                        if(readMessage != null) {
                            int command = rxmsg[3];
                            if(command == MessageTimer.RETRIEVE_LED_STATUS_RESP) {
                                if(mMessageTimer.isRunning(MessageTimer.RETRIEVE_LED_STATUS_REQ)) {
                                    mMessageTimer.stop();
                                    if(rxmsg[4] == 0x01) {
                                        ledValueBtn.setText("OFF");
                                        ledStatus = 1;
                                    }
                                    else {
                                        ledValueBtn.setText("ON");
                                        ledStatus = 0;
                                    }
                                    dTimeView.setText(getRightNow());
                                }
                            }
                            else if(command == MessageTimer.CHANGE_LED_STATUS_RESP) {
                                if(mMessageTimer.isRunning(MessageTimer.CHANGE_LED_STATUS_REQ)) {
                                    mMessageTimer.stop();
                                    if(rxmsg[4] == 0x01) {
                                        if(ledStatus == 0x00) {
                                            ledValueBtn.setText("OFF");
                                            ledStatus = 1;
                                        }
                                        else {
                                            ledValueBtn.setText("ON");
                                            ledStatus = 0;
                                        }
                                    }
                                    dTimeView.setText(getRightNow());
                                }
                            }
                            else if(command == MessageTimer.REPORT_SENGING_VALUE_IND) {
                                byte[] buf = new byte[4];
                                for(int i = 0;i < 4;i++)
                                    buf[i] = rxmsg[i + 5];
                                float t = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                                for(int i = 0;i < 4;i++)
                                    buf[i] = rxmsg[i + 10];
                                float h = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                                tValueView.setText(String.format("%.2f",t));
                                hValueView.setText(String.format("%.2f",h));
                                hTimeView.setText(getRightNow());
                            }
                        }
                        /*
                        final String readMessage = (String) msg.obj;
                        Log.w("debug", "rxMsg = " + readMessage + ", length = " + readMessage.length());
                        if (readMessage != null) {
//                            activity.appendLog(Utils.printHexString(readMessage), true, false, activity.needClean);
                            activity.appendLog(Utils.printHexString(readMessage), hexMode, true, activity.needClean);
                            int command = readMessage.charAt(3);
                            if(command == MessageTimer.RETRIEVE_LED_STATUS_RESP) {
                                if(mMessageTimer.isRunning(MessageTimer.RETRIEVE_LED_STATUS_REQ)) {
                                    mMessageTimer.stop();
                                    if(readMessage.charAt(4) == 0x01) {
                                        ledValueBtn.setText("OFF");
                                        ledStatus = 1;
                                    }
                                    else {
                                        ledValueBtn.setText("ON");
                                        ledStatus = 0;
                                    }
                                    dTimeView.setText(getRightNow());
                                }
                            }
                            else if(command == MessageTimer.CHANGE_LED_STATUS_RESP) {
                                if(mMessageTimer.isRunning(MessageTimer.CHANGE_LED_STATUS_REQ)) {
                                    mMessageTimer.stop();
                                    if(readMessage.charAt(4) == 0x01) {
                                        if(ledStatus == 0x00) {
                                            ledValueBtn.setText("OFF");
                                            ledStatus = 1;
                                        }
                                        else {
                                            ledValueBtn.setText("ON");
                                            ledStatus = 0;
                                        }
                                    }
                                    dTimeView.setText(getRightNow());
                                }
                            }
                            else if(command == MessageTimer.REPORT_SENGING_VALUE_IND) {
                                byte[] buf = new byte[4];
                                for(int i = 0;i < 4;i++)
                                    buf[i] = (byte)readMessage.charAt(i + 5);
                                float t = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                                for(int i = 0;i < 4;i++)
                                buf[i] = (byte)readMessage.charAt(i + 10);
                                    float h = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                                tValueView.setText(String.format("%.2f",t));
                                hValueView.setText(String.format("%.2f",h));
                                hTimeView.setText(getRightNow());
                            }
                        }
*/
                            break;

                    case MESSAGE_DEVICE_NAME:
                        activity.setDeviceName((String) msg.obj);
                        break;

                    case MESSAGE_WRITE:
                        // stub
                        break;

                    case MESSAGE_TOAST:
                        // stub
                        break;
                }
            }
        }
    }

    private String getRightNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm:ss");
        return sdf.format(new Date());
    }

    public void changeLEDStatus(View view) {
        if(isConnected()) {
            if(mMessageTimer.isRunning(MessageTimer.CHANGE_LED_STATUS_REQ)) {
                Toast.makeText(this, R.string.already_command_running, Toast.LENGTH_LONG).show();
            }
            else {
                StringBuilder sb = new StringBuilder();
                sb.append("0b0b044101");
                if(ledStatus == 0) {
                    sb.append("0147");
                }
                else {
                    sb.append("0046");
                }
                sb.append("0f0f");
                String commandString = sb.toString();
                byte[] command = Utils.toHex(commandString);
                connector.write(command);
                appendLog(commandString, hexMode, true, needClean);
                mMessageTimer.start(MessageTimer.CHANGE_LED_STATUS_REQ);
            }
        }
    }

    public void retrieveLEDStatus(View view) {
        if(isConnected()) {
            if(mMessageTimer.isRunning(MessageTimer.RETRIEVE_LED_STATUS_REQ)) {
                Toast.makeText(this, R.string.already_command_running, Toast.LENGTH_LONG).show();
            }
            else {
                String commandString = "0b0b034001440f0f";
                byte[] command = Utils.toHex(commandString);

                connector.write(command);
                appendLog(commandString, hexMode, true, needClean);
                mMessageTimer.start(MessageTimer.RETRIEVE_LED_STATUS_REQ);
            }
        }
    }
    // ==========================================================================
}