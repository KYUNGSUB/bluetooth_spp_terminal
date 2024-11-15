package com.example.bluetooth;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;

import com.pedro.library.BuildConfig;

import java.io.UnsupportedEncodingException;

/**
 * Вспомогательные методы
 * Created by sash0k on 29.01.14.
 */
public class Utils {

    /**
     * Общий метод вывода отладочных сообщений в лог
     */
    public static void log(String message) {
        if (BuildConfig.DEBUG) {
            if (message != null) Log.i(Const.TAG, message);
        }
    }
    // ============================================================================


    /**
     * Конвертация hex-команд в строку для отображения
     */
    public static String printHex(String hex) {
        StringBuilder sb = new StringBuilder();
        int len = hex.length();
        try {
            for (int i = 0; i < len; i += 2) {
                sb.append("0x").append(hex.substring(i, i + 2)).append(" ");
            }
        } catch (NumberFormatException e) {
            log("printHex NumberFormatException: " + e.getMessage());

        } catch (StringIndexOutOfBoundsException e) {
            log("printHex StringIndexOutOfBoundsException: " + e.getMessage());
        }
        return sb.toString();
    }
    // ============================================================================


    /**
     * Перевод введенных ASCII-команд в hex побайтно.
     * @param hex - команда
     * @return - массив байт команды
     */
    public static byte[] toHex(String hex) {
        int len = hex.length();
        byte[] result = new byte[len/2];    // len -> len/2 : 2 byte string "0b" -> 1 byte 0b
        try {
            int index = 0;
            for (int i = 0; i < len; i += 2) {
                String value = hex.substring(i, i + 2);
//                result[index] = (byte) Integer.parseInt(value, 16);
                result[index] = (byte) Integer.parseInt(value, 16);
                index++;
            }
        } catch (NumberFormatException e) {
            log("toHex NumberFormatException: " + e.getMessage());

        } catch (StringIndexOutOfBoundsException e) {
            log("toHex StringIndexOutOfBoundsException: " + e.getMessage());
        }
        return result;
    }
    // ============================================================================


    /**
     * Метод сливает два массива в один
     */
    public static byte[] concat(byte[] A, byte[] B) {
        byte[] C = new byte[A.length + B.length];
        System.arraycopy(A, 0, C, 0, A.length);
        System.arraycopy(B, 0, C, A.length, B.length);
        return C;
    }
    // ============================================================================


    /**
     * Modulo
     */
    public static int mod(int x, int y) {
        int result = x % y;
        return result < 0 ? result + y : result;
    }
    // ============================================================================

    /**
     * Расчёт контрольной суммы
     */
    public static String calcModulo256(String command)
    {
        int crc = 0;
        for (int i = 0; i< command.length(); i++) {
            crc += (int)command.charAt(i);
        }
        return Integer.toHexString(Utils.mod(crc, 256));
    }
    // ============================================================================

    /**
     * Раскрасить текст нужным цветом
     */
    public static String mark(String text, String color) {
        return "<font color=" + color + ">" + text + "</font>";
    }
    // ============================================================================

    /**
     * Получение id сохранённого в игрушке звукового набора
     */
    public static String getPrefence(Context context, String item) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return settings.getString(item, Const.TAG);
    }
    // ============================================================================


    /**
     * Получение флага из настроек
     */
    public static boolean getBooleanPrefence(Context context, String tag) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return settings.getBoolean(tag, true);
    }

    public static String printHexString(String readMessage) {
        StringBuilder sb = new StringBuilder();
        try {
            for(final byte b: readMessage.getBytes("UTF-8"))
                sb.append(String.format("%02x ", b&0xff));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
    // ============================================================================


    /**
     * Класс-фильтр полей ввода
     */
    // ============================================================================
    public static class InputFilterHex implements InputFilter {

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            for (int i = start; i < end; i++) {
                if (!Character.isDigit(source.charAt(i))
                        && source.charAt(i) != 'A' && source.charAt(i) != 'D'
                        && source.charAt(i) != 'B' && source.charAt(i) != 'E'
                        && source.charAt(i) != 'C' && source.charAt(i) != 'F'
                        ) {
                    return "";
                }
            }
            return null;
        }
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes, int len) {
        char[] hexChars = new char[len * 2];
        for ( int j = 0; j < len; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len/2];

        for(int i = 0; i < len; i+=2){
            data[i/2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
        }

        return data;
    }
    // ============================================================================
}
