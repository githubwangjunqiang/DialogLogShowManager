package com.xq.dialoglogshow.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.xq.dialoglogshow.IShowLoadDataCallback;
import com.xq.dialoglogshow.manager.ShowLogManager;
import com.xq.dialoglogshow.utils.ShowTask;
import com.xq.dialoglogshow.utils.SizeUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;

/**
 * Created by Android-小强 on 2023/1/13.
 * mailbox:980766134@qq.com
 * description: 分享文件
 */
public class FileShareActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout frameLayout = new FrameLayout(this);
        ProgressBar progressBar = new ProgressBar(this);
        int screenWidth = SizeUtils.getScreenWidth(this.getApplicationContext());
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams((int) (screenWidth * 0.2),
                (int) (screenWidth * 0.2));
        layoutParams.gravity = Gravity.CENTER;
        frameLayout.addView(progressBar, layoutParams);
        setContentView(frameLayout);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                createFile(null);
            }
        });

    }

    private ShowTask mAsyncTask;
    private static final int CREATE_FILE = 1001;

    private void createFile(Uri pickerInitialUri) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        long l = System.currentTimeMillis();
        String format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(l);
        format = "cache_log_file_debug_log_" + format + ".txt";
        intent.putExtra(Intent.EXTRA_TITLE, format);

        // Optionally, specify a URI for the directory that should be opened in
        // the system file picker when your app creates the document.
        if (pickerInitialUri != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);
        }

        startActivityForResult(intent, CREATE_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_FILE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {

            Uri uri = data.getData();
            saeFile(uri);


        } else {
            Toast.makeText(this.getApplicationContext(), "save error", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void saeFile(Uri uri) {
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }
        mAsyncTask = new ShowTask<Uri>() {
            @Override
            protected void postMainData(Uri uri) {
                Toast.makeText(getApplicationContext(), "save debug success", Toast.LENGTH_SHORT).show();
                openFile(getApplicationContext(),
                        uri, "text/*");
                finish();
            }

            @Override
            protected Uri doInBackground(Object[] objects) {
                boolean extracted = extracted((Uri) objects[0]);
                if (extracted) {
                    return (Uri) objects[0];
                }
                return null;
            }
        };
        mAsyncTask.execute(uri);

    }

    private void openFile(Context applicationContext, Uri uri, String type) {

        try {
//            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            intent.addCategory(Intent.CATEGORY_OPENABLE);
//            intent.setDataAndType(uri, type);
//            startActivity(intent);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if (mAsyncTask != null) {
                mAsyncTask.cancel(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        try {
            if (mAsyncTask != null) {
                mAsyncTask.cancel(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    /**
     * 存入文件内容
     *
     * @param uri
     * @return
     */
    private boolean extracted(Uri uri) {
        BufferedWriter bufferedWriter = null;
        BufferedReader bufferedReader = null;
        try {
            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
            String path = ((IShowLoadDataCallback) ShowLogManager.getInstance()).loadAllData();

            if (TextUtils.isEmpty(path)) {
                return false;
            }

            File file = new File(path);
            bufferedReader = new BufferedReader(new FileReader(file));

            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                bufferedWriter.write(line);
                bufferedWriter.newLine();
            }
            bufferedWriter.flush();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                bufferedWriter.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                bufferedReader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

}
