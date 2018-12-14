package com.huasuan;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.huasuan.utils.FileUtils;
import com.huasuan.utils.LogUtils;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.List;

public class WxSQLiteUtil {

    private static final String TAG = WxSQLiteUtil.class.getSimpleName();

    public static final String WX_ROOT_PATH = "/data/data/com.tencent.mm/";
    private static final String WX_SP_UIN_PATH = WX_ROOT_PATH + "shared_prefs/auth_info_key_prefs.xml";

    public static String[] getRContactJson(Context context){

        String wxUin = readWxUinFromSp();

        // 需要拷贝一下再读db，否则读取操作会破坏数据库格式
        String srcPath = WX_ROOT_PATH + "MicroMsg/" + clacMd5("mm" + wxUin) + "/EnMicroMsg.db";
        String dstPath = "/sdcard/EnMicroMsg.db";
        FileUtils.copyFile(new File(srcPath), new File(dstPath));

        String imei = Env.IMEI;
        String dbPass = clacDbPassword(imei, wxUin);

        File dbFile = new File(dstPath);

        String type1Username = queryType1UserNameFromRcontact(context, dbFile, dbPass);
        String type3UserNameAlias  = queryType3UserNameAliasFromRcontact(context, dbFile, dbPass);
        return new String[]{type1Username,type3UserNameAlias};
    }

    private static String queryType3UserNameAliasFromRcontact(Context context, File dbFile, String dbPassword) {
        // 此处的ClassLoader需要使用wechat的
        try {
            SQLiteDatabase.loadLibs(context);
        } catch (Exception e) {
            LogUtils.logError(TAG, e.getMessage(), e);
        }
        SQLiteDatabaseHook hook = new SQLiteDatabaseHook() {
            public void preKey(SQLiteDatabase database) {
            }
            public void postKey(SQLiteDatabase database) {
                database.rawExecSQL("PRAGMA cipher_migrate;"); //兼容2.0的数据库
            }
        };

        //打开数据库连接
        SQLiteDatabase db = null;
        Cursor c1 = null;
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbFile, dbPassword, null, hook);
            //查询所有联系人（verifyFlag!=0:公众号等类型，群里面非好友的类型为4，未知类型2）
            c1 = db.rawQuery("select username,alias,nickname from rcontact where verifyFlag = 0 and  type == 3", null);
            JSONArray resultSet = new JSONArray();
            while (c1.moveToNext()) {
                int totalColumn = c1.getColumnCount();
                JSONObject rowObject = new JSONObject();
                for (int i = 0; i < totalColumn; i++) {
                    if (c1.getColumnName(i) != null) {
                        try {
                            if (c1.getString(i) != null) {
                                Log.d(TAG, c1.getString(i));
                                rowObject.put(c1.getColumnName(i), c1.getString(i));
                            } else {
                                rowObject.put(c1.getColumnName(i), "");
                            }
                        } catch (Exception e) {
                            Log.d(TAG, e.getMessage());
                        }
                    }
                }
                resultSet.add(rowObject);
            }
            return resultSet.toJSONString();
        } catch (Exception e) {
            LogUtils.logError(TAG, e.getMessage(), e);
        } finally {
            if (c1 != null) {
                c1.close();
            }
            if (db != null) {
                db.close();
            }
        }

        return "";
    }

    private static String queryType1UserNameFromRcontact(Context context, File dbFile, String dbPassword) {
        String username = "";
        // 此处的ClassLoader需要使用wechat的
        try {
//            ClassLoader clWx = context.getClassLoader();
//            Class classSQLiteDatabase = clWx.loadClass(SQLiteDatabase.class.getName());
//            Method method = classSQLiteDatabase.getDeclaredMethod("loadLibs", Context.class);
//            method.invoke(null, context);
            // SQLiteDatabase.loadLibs(context);
//            Context packCtx = context.createPackageContext(Env.BOT_APK_PACKAGE_NAME, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            SQLiteDatabase.loadLibs(context);
        } catch (Exception e) {
            LogUtils.logError(TAG, e.getMessage(), e);
        }
        SQLiteDatabaseHook hook = new SQLiteDatabaseHook() {
            public void preKey(SQLiteDatabase database) {
            }
            public void postKey(SQLiteDatabase database) {
                database.rawExecSQL("PRAGMA cipher_migrate;"); //兼容2.0的数据库
            }
        };

        //打开数据库连接
        SQLiteDatabase db = null;
        Cursor c1 = null;
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbFile, dbPassword, null, hook);
            //查询所有联系人（verifyFlag!=0:公众号等类型，群里面非好友的类型为4，未知类型2）
            c1 = db.rawQuery("select username from rcontact where verifyFlag = 0 and  type == 1", null);

            c1.moveToFirst();
            username = c1.getString(0);
//        } catch (SQLiteException se) {
//            LogUtils.logError(TAG, se.getMessage(), se);
        } catch (Exception e) {
            LogUtils.logError(TAG, e.getMessage(), e);
        } finally {
            if (c1 != null) {
                c1.close();
            }
            if (db != null) {
                db.close();
            }
        }

        return username;
    }

    /**
     * 根据imei和uin生成的md5码，获取数据库的密码（去前七位的小写字母）
     *
     * @param imei
     * @param uin
     * @return
     */
    private static String clacDbPassword(String imei, String uin) {
        if (TextUtils.isEmpty(imei) || TextUtils.isEmpty(uin)) {
            //LogUtil.log("初始化数据库密码失败：imei或uid为空");
            return null;
        }
        String md5 = clacMd5(imei + uin);
        String password = md5.substring(0, 7).toLowerCase();
        return password;
    }

    /**
     * md5加密
     *
     * @param content
     * @return
     */
    private static String clacMd5(String content) {
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            md5.update(content.getBytes("UTF-8"));
            byte[] encryption = md5.digest();//加密
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < encryption.length; i++) {
                if (Integer.toHexString(0xff & encryption[i]).length() == 1) {
                    sb.append("0").append(Integer.toHexString(0xff & encryption[i]));
                } else {
                    sb.append(Integer.toHexString(0xff & encryption[i]));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取微信的uid
     * 微信的uid存储在SharedPreferences里面
     * 存储位置\data\data\com.tencent.mm\shared_prefs\auth_info_key_prefs.xml
     */
    private static String readWxUinFromSp() {
        String wxUin = null;
        File file = new File(WX_SP_UIN_PATH);
        try {
            FileInputStream in = new FileInputStream(file);
            SAXReader saxReader = new SAXReader();
            Document document = saxReader.read(in);
            Element root = document.getRootElement();
            List<Element> elements = root.elements();
            for (Element element : elements) {
                if ("_auth_uin".equals(element.attributeValue("name"))) {
                    wxUin = element.attributeValue("value");
                }
            }
        } catch (Exception e) {
            LogUtils.logError(TAG, "获取微信uid失败，请检查auth_info_key_prefs文件权限");
            LogUtils.logError(TAG, e.getMessage(), e);
        }
        return wxUin;
    }
}
