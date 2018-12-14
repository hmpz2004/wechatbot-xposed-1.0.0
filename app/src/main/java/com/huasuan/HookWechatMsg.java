package com.huasuan;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.text.TextUtils;

import com.huasuan.utils.LogUtils;

import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.newInstance;

public class HookWechatMsg implements IXposedHookLoadPackage {

    private static final String TAG = "HookWechatMsg";

    private static boolean isInjected = false;

    private Context mAppContext = null;

    private Class mClassZau = null;
    private Class mClassModelmultiI = null;
    private Class mClassModelM = null;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.processName.contains(Env.TARGET_PACKAGE_NAME)) {
            return;
        }

        if (isInjected) {
            return;
        }
        isInjected = true;

        log("in " + lpparam.processName);

        final ClassLoader cl = lpparam.classLoader;

        Class classAppApplication = cl.loadClass("com.tencent.mm.app.Application");
        Class classTinkerApplication = cl.loadClass("com.tencent.tinker.loader.app.TinkerApplication");

        Class classSQLiteDatabase = cl.loadClass("com.tencent.wcdb.database.SQLiteDatabase");
        Class classBua = cl.loadClass("com.tencent.mm.bu.a");
        Class classZau = cl.loadClass("com.tencent.mm.z.au");
        mClassZau = classZau;
        Class classModelmultiI = cl.loadClass("com.tencent.mm.modelmulti.i");
        mClassModelmultiI = classModelmultiI;
        Class classModelM = cl.loadClass("com.tencent.mm.pluginsdk.model.m");
        mClassModelM = classModelM;
        // Class class = cl.loadClass("");

        XposedHelpers.findAndHookMethod(classTinkerApplication, "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        LogUtils.logDebug(TAG, "after classTinkerApplication onCreate() and call getApplicationContext()");

                        Object tinkerObj = param.thisObject;

                        Method methodGetAppCtx = ContextWrapper.class.getDeclaredMethod("getApplicationContext");
                        Object appCtxObj = methodGetAppCtx.invoke(tinkerObj);
                        if (appCtxObj != null) {
                            LogUtils.logDebug(TAG, "got app ctx");
                            mAppContext = (Context) appCtxObj;
                        } else {
                            LogUtils.logDebug(TAG, "failed to get app ctx");
                        }
                    }
                });

//        XposedHelpers.findAndHookMethod(classBua, "a",
//                String.class,
//                String.class,
//                String.class,
//                long.class,
//                String.class,
//                HashMap.class,
//                boolean.class,
//
//                new XC_MethodHook() {
//                    @Override
//                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//
//                        LogUtils.logDebug(TAG, "in classBua a()");
//                        try {
//                            StringBuilder sb = new StringBuilder();
//                            for (Object itemObj : param.args) {
//                                sb.append("" + itemObj);
//                            }
//                            // LogUtils.logDebug(TAG, "params : " + sb.toString());
//                        } catch (Exception e) {
//                            LogUtils.logError(TAG, e.getMessage(), e);
//                        }
//                    }
//                });

        findAndHookMethod(classSQLiteDatabase, "insert",String.class,String.class, ContentValues.class, new XC_MethodHook() {

            /**
             * 回复微信消息
             * @param talker
             * @param msgContent
             */
            public void replyMsg(final String talker,final String msgContent){
                if (mClassZau == null || mClassModelmultiI == null) {
                    LogUtils.logError(TAG, "replyMsg() error, mClassZau or mClassModelmultiI not ready");
                    return;
                }
                Object objDvResult = callStaticMethod(mClassZau, "Dv");
                Object objMsgEntity = newInstance(mClassModelmultiI,talker,msgContent,1,0,null);
                callMethod(objDvResult, "a", objMsgEntity, 0);
            }

            /**
             * 回复新加好友消息
             * @param talker
             * @param ticket
             */
            public void acceptNewFriend(String talker, String ticket) {
                if (mClassZau == null || mClassModelM == null) {
                    LogUtils.logError(TAG, "acceptNewFriend() error, mClassZau or mClassModelM not ready");
                    return;
                }
                Object objDvResult = callStaticMethod(mClassZau, "Dv");
                Object objMsgNewFriendEntity = newInstance(mClassModelM,
                        3,talker,ticket,30);
                callMethod(objDvResult, "a", objMsgNewFriendEntity, 0);
            }

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                LogUtils.logDebug(TAG, "before classSQLiteDatabase insert()");
                LogUtils.logDebug(TAG, "insert() : " + param.args[0] + " " + param.args[1] + " " + param.args[2]);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                ContentValues contentValues = (ContentValues) param.args[2];
                String tableName = (String) param.args[0];
                LogUtils.logDebug(TAG, "after classSQLiteDatabase insert()");
                //判断新消息
                if (!TextUtils.isEmpty(tableName) && "message".equals(tableName)) {
                    Integer isSend = contentValues.getAsInteger("isSend");
                    Integer type = contentValues.getAsInteger("type");
                    Integer status = contentValues.getAsInteger("status");
                    final String talker = contentValues.getAsString("talker");
                    final String content = contentValues.getAsString("content");

                    if (isSend == 0 && status == 3 && type == 1){ // type 1.文本 3.图片 48.位置
                        //群组 发言 talker=5885584579@chatroom content=zzxm88:text
                        //群组@ 发言 talker=5885584579@chatroom content=zzxm88:@嘻嘻  text

                        // 消息发送给server
                        byte[] data = (talker +"-----"+content).getBytes();
                        SocketModule.sendBytesToSocketServer(mAppContext, data);

                        // 消息内容包括 talker content
                        if ("帮助".equals(content)) {
                            replyMsg(talker, "hello");
                        }
                    }
                }
                //判断新好友
                if (!TextUtils.isEmpty(tableName) && "fmessage_msginfo".equals(tableName)) {
                    Integer isSend = contentValues.getAsInteger("isSend");
                    Integer type = contentValues.getAsInteger("type");
                    final String talker = contentValues.getAsString("talker");
                    final String content = contentValues.getAsString("content");
                    String msgcontent = contentValues.getAsString("msgContent");
                    if (isSend == 0 && type == 1){
                        /*
                        参数3 = encryptTalker=v1_cdf8a7b3da023da5307395352cd08500f7b4150729e300531a9da91d1f296ba7@stranger msgContent=<msg fromusername="zzxm88" encryptusername="v1_cdf8a7b3da023da5307395352cd08500f7b4150729e300531a9da91d1f296ba7@stranger" fromnickname="明" content="我是明" fullpy="ming" shortpy="M" imagestatus="3" scene="30" country="DE" province="Hamburg" city="" sign="" percard="1" sex="1" alias="xm________________" weibo="" weibonickname="" albumflag="0" albumstyle="0" albumbgimgid="912895298764800_912895298764800" snsflag="48" snsbgimgid="" snsbgobjectid="0" mhash="1b3dad158913a69d934caaadde76a00f" mfullhash="1b3dad158913a69d934caaadde76a00f" bigheadimgurl="http://wx.qlogo.cn/mmhead/ver_1/OGlGw9icNc0W5md9kfaC54hRN6zsxxLicIiboXkjYTSUs6FEhvV56QLz5icRLdkbicSsZCktcxiaQKT0qZsSEk6MYwng/0" smallheadimgurl="http://wx.qlogo.cn/mmhead/ver_1/OGlGw9icNc0W5md9kfaC54hRN6zsxxLicIiboXkjYTSUs6FEhvV56QLz5icRLdkbicSsZCktcxiaQKT0qZsSEk6MYwng/96" ticket="v2_c9ded7a183c8bc8c587756da612cea8fadc77558e98239c10d8bb1ff590f5a0088bdd7ef3ae4f6e99acc93e160c2baec@stranger" opcode="2" googlecontact="" qrticket="" chatroomusername="" sourceusername="" sourcenickname=""><brandlist count="0" ver="698050163"></brandlist></msg> chatroomName= svrId=5540230629124514099 createTime=1526281451000 talker=zzxm88 type=1 isSend=0
                        */

                        DocumentBuilder newDocumentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                        Document parse = newDocumentBuilder.parse(new ByteArrayInputStream(msgcontent.getBytes()));

                        String ticket = parse.getFirstChild().getAttributes().getNamedItem("ticket").getNodeValue();

                        // 自动同意好友
                        acceptNewFriend(talker, ticket);

                        //<<>>给server发送消息
                        //postmsgbeta(lpparam.classLoader,talker,"newfriend");
                        byte[] data = (talker +"-----"+"newfriend").getBytes();
                    }

                }

            }
        });
    }
}
