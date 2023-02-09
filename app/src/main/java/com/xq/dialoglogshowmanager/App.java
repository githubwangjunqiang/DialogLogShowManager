package com.xq.dialoglogshowmanager;

import android.app.Application;

import com.xq.dialoglogshow.IShowLoadDataCallback;
import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.entity.HttpLogData;
import com.xq.dialoglogshow.entity.PushData;
import com.xq.dialoglogshow.manager.ShowLogManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by Android-小强 on 2023/1/10.
 * mailbox:980766134@qq.com
 * description:
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        ShowLogManager.getInstance().setDataCallback(new IShowLoadDataCallback() {
            @Override
            public ArrayList<HttpLogData> loadHttpLog(long startTime, long endTime) {


                return null;
            }

            @Override
            public ArrayList<BaseShowData> loadUserInfo() {

                BaseShowData data = new BaseShowData() {

                };

                data.setContent("{\n" +
                        "            \"id\":261219073,\n" +
                        "            \"uid\":\"1lLUkBgWNQEIBlhVv5ybRJ\",\n" +
                        "            \"name\":\"兔兔\",\n" +
                        "            \"email\":\"兔兔\",\n" +
                        "            \"region\":\"BR\",\n" +
                        "            \"avatar\":\"https:\\/\\/gimg2.baidu.com\\/image_search\\/src=http%3A%2F%2Fpic3.zhimg.com%2Fv2-22592c2c925f169ee6c49cb43cde68fe_b.jpg&refer=http%3A%2F%2Fpic3.zhimg.com&app=2002&size=f9999,10000&q=a80&n=0&g=0n&fmt=jpeg?sec=1638621919&t=2f84ae6c294bdc7600b92cb2e039b23b\",\n" +
                        "            \"gender\":0,\n" +
                        "            \"locale\":\"zh-CN\",\n" +
                        "            \"total\":0,\n" +
                        "            \"balance\":0,\n" +
                        "            \"expend\":0,\n" +
                        "            \"media\":0,\n" +
                        "            \"isCahnnel\":0,\n" +
                        "            \"newUser\":1,\n" +
                        "            \"offers\":0,\n" +
                        "            \"boxNum\":0,\n" +
                        "            \"isMessage\":0,\n" +
                        "            \"serverTime\":1673324874098,\n" +
                        "            \"cashCoupon\":0,\n" +
                        "            \"level\":1,\n" +
                        "            \"isKyc\":0\n" +
                        "        }");

                ArrayList<BaseShowData> list = new ArrayList<>();
                list.add(data);

                return list;
            }

            @Override
            public ArrayList<BaseShowData> loadKeyValue() {
                return null;
            }

            @Override
            public ArrayList<PushData> loadPush() {
                return null;
            }

            @Override
            public String loadAllData() {

                File cache = getExternalFilesDir("cache");
                if (!cache.exists()) {
                    cache.mkdirs();
                }
                File file = new File(cache, "cache.txt");
                try {

                    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                    writer.write("我是缓存我哦 哈哈哈哈哈 你好\n我好我是第二行");
                    writer.flush();
                    writer.close();
                    return file.getAbsolutePath();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }


                return null;
            }

            @Override
            public ArrayList<BaseShowData> loadRestData() {
                ArrayList<BaseShowData> list = new ArrayList<>();
                BaseShowData data = new BaseShowData();
                data.setContent("哈喽 你好");
                list.add(data);
                return list;
            }
        });
//        ShowLogManager.getInstance().start(this);
    }
}
