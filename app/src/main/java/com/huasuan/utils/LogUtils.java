package com.huasuan.utils;

import android.util.Log;

import com.huasuan.Env;

public class LogUtils {
    /**
     * 此方法的message string不要用+拼接，否则编译服务器不能去掉log信息
     * @param tag
     * @param msg
     */
    public static void logError(String tag, String msg) {
        if (Env.DEBUG) {
            Log.e(tag, msg);
        }
    }

    /**
     * 此方法的message string不要用+拼接，否则编译服务器不能去掉log信息
     * @param tag
     * @param msg
     */
    public static void logDebug(String tag, String msg) {
        if (Env.DEBUG) {
            Log.d(tag, msg);
        }
    }

    /**
     * 此方法的message string不要用+拼接，否则编译服务器不能去掉log信息
     * @param tag
     * @param msg
     * @param e
     */
    public static void logError(String tag, String msg, Exception e) {
        if (Env.DEBUG) {
            Log.e(tag, msg, e);
        }
    }

    /**
     * 此方法的message string不要用+拼接，否则编译服务器不能去掉log信息
     * @param tag
     * @param msg
     * @param e
     */
    public static void logError(String tag, String msg, Throwable e) {
        if (Env.DEBUG) {
            Log.e(tag, msg, e);
        }
    }
}
