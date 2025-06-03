package com.xq.dialoglogshow.utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Description:
 * Author:王俊强 980766134@qq.com
 * Date:2025/6/3
 */
public class PagedTextLoader {
    private File file;
    private long filePointer = 0;
    private int pageSize;

    public PagedTextLoader(File file, int pageSize) {
        this.file = file;
        this.pageSize = pageSize;
    }

    public List<String> loadNextPage() throws IOException {
        List<String> lines = new ArrayList<>();
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            raf.seek(filePointer);

            String line;
            int count = 0;

            while ((line = raf.readLine()) != null && count < pageSize) {
                lines.add(new String(line.getBytes("ISO-8859-1"), StandardCharsets.UTF_8)); // 防乱码
                count++;
            }

            filePointer = raf.getFilePointer();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                assert raf != null;
                raf.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return lines;
    }

    public boolean hasMore() throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        raf.seek(filePointer);
        boolean result = raf.readLine() != null;
        raf.close();
        return result;
    }
}
