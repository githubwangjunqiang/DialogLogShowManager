package com.xq.dialoglogshow.ui.view;

import android.content.Context;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.utils.ShareUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SDK 文件浏览主 Tab 的承载视图。
 * 为什么拆为独立 View：目录访问、导航状态和异步任务需要与 Hub 页签切换隔离，避免主 Dialog 膨胀并便于关闭时统一释放。
 */
@SuppressLint("ViewConstructor")
public final class FileBrowserView extends LinearLayout {

    private static final int ROW_HEIGHT_RES = R.dimen.dp_52;
    private static final int TEXT_PAGE_CHARS = 32 * 1024;
    private static final long CLICK_THROTTLE_MS = 350L;
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final File mRootDirectory;
    private final ExecutorService mIoExecutor;
    private final List<File> mNavigationStack = new ArrayList<>();
    private final FileAdapter mAdapter;
    private final TextView mPathView;
    private final TextView mStatusView;
    private final ProgressBar mProgressBar;
    private final RecyclerView mRecyclerView;
    private final AtomicInteger mLoadGeneration = new AtomicInteger();
    private FilePreviewDialog mActivePreview;
    private long mLastClickTime;
    private boolean mReleased;

    /**
     * 创建浏览页并从配置路径开始读取。
     */
    public FileBrowserView(@NonNull Context context, @NonNull String rootPath) {
        super(context);
        setOrientation(VERTICAL);
        mRootDirectory = new File(rootPath);
        mIoExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ShowLogFileBrowser-" + THREAD_IDS.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        mAdapter = new FileAdapter(context, this::onEntryClicked);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(R.dimen.dp_8), dp(R.dimen.dp_4), dp(R.dimen.dp_8), dp(R.dimen.dp_4));
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView backButton = createText(context, R.string.go_to_parent_folder, R.dimen.sp_14);
        backButton.setGravity(Gravity.CENTER);
        backButton.setPadding(dp(R.dimen.dp_10), dp(R.dimen.dp_8), dp(R.dimen.dp_10), dp(R.dimen.dp_8));
        backButton.setOnClickListener(view -> {
            if (!isClickThrottled()) {
                navigateUp();
            }
        });
        header.addView(backButton, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mPathView = createText(context, 0, R.dimen.sp_13);
        mPathView.setTextColor(ContextCompat.getColor(context, R.color.showlog_text_secondary));
        mPathView.setSingleLine(true);
        mPathView.setEllipsize(TextUtils.TruncateAt.START);
        LayoutParams pathParams = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        pathParams.leftMargin = dp(R.dimen.dp_8);
        header.addView(mPathView, pathParams);

        FrameLayout content = new FrameLayout(context);
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        mRecyclerView = new RecyclerView(context);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        mRecyclerView.setAdapter(mAdapter);
        content.addView(mRecyclerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mStatusView = createText(context, 0, R.dimen.sp_14);
        mStatusView.setGravity(Gravity.CENTER);
        mStatusView.setVisibility(GONE);
        content.addView(mStatusView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mProgressBar = new ProgressBar(context);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(R.dimen.dp_40), dp(R.dimen.dp_40), Gravity.CENTER);
        content.addView(mProgressBar, progressParams);
        mProgressBar.setVisibility(GONE);

        mNavigationStack.add(mRootDirectory);
        loadCurrentDirectory();
    }

    /**
     * 关闭页面时使未完成的目录结果失效，防止回调更新已销毁的弹框。
     */
    public void release() {
        mReleased = true;
        mLoadGeneration.incrementAndGet();
        mIoExecutor.shutdownNow();
        if (mActivePreview != null && mActivePreview.isShowing()) {
            mActivePreview.dismiss();
        }
        mActivePreview = null;
        mAdapter.clear();
    }

    private void onEntryClicked(@NonNull File file) {
        if (isClickThrottled()) {
            return;
        }
        try {
            if (file.isDirectory()) {
                if (!isWithinRoot(file)) {
                    showStatus(R.string.folder_cannot_be_accessed);
                    return;
                }
                mNavigationStack.add(file);
                loadCurrentDirectory();
                return;
            }
            if (!file.isFile() || !isWithinRoot(file)) {
                showStatus(R.string.folder_cannot_be_accessed);
                return;
            }
            if (mActivePreview != null && mActivePreview.isShowing()) {
                mActivePreview.dismiss();
            }
            mActivePreview = new FilePreviewDialog(getContext(), mRootDirectory, file);
            mActivePreview.setOnDismissListener(dialog -> mActivePreview = null);
            mActivePreview.show();
        } catch (SecurityException exception) {
            showStatus(R.string.folder_cannot_be_accessed);
        }
    }

    private void navigateUp() {
        if (mNavigationStack.size() <= 1 || mReleased) {
            return;
        }
        mNavigationStack.remove(mNavigationStack.size() - 1);
        loadCurrentDirectory();
    }

    private void loadCurrentDirectory() {
        if (mReleased) {
            return;
        }
        File current = mNavigationStack.get(mNavigationStack.size() - 1);
        mPathView.setText(current.getPath());
        int generation = mLoadGeneration.incrementAndGet();
        setLoading(true);
        mIoExecutor.execute(() -> {
            List<FileItem> entries;
            int errorString = 0;
            try {
                if (!isWithinRoot(current)) {
                    errorString = R.string.folder_cannot_be_accessed;
                    entries = new ArrayList<>();
                } else if (!current.exists()) {
                    errorString = R.string.folder_not_found;
                    entries = new ArrayList<>();
                } else if (!current.isDirectory()) {
                    errorString = R.string.configured_path_is_not_folder;
                    entries = new ArrayList<>();
                } else if (!current.canRead()) {
                    errorString = R.string.folder_cannot_be_accessed;
                    entries = new ArrayList<>();
                } else {
                    File[] files = current.listFiles();
                    if (files == null) {
                        errorString = R.string.folder_cannot_be_accessed;
                        entries = new ArrayList<>();
                    } else {
                        entries = new ArrayList<>();
                        for (File file : files) {
                            try {
                                if (isWithinRoot(file)) {
                                    boolean directory = file.isDirectory();
                                    long length = directory ? 0L : file.length();
                                    entries.add(new FileItem(file, file.getName(), directory, length));
                                }
                            } catch (SecurityException ignored) {
                                // 为什么这样做：单个条目的类型/大小权限可能单独失效，保留名称并在点击时再判定。
                                entries.add(new FileItem(file, file.getName(), false, -1L));
                            }
                        }
                        // Collections.sort 可覆盖 SDK minSdk 21；List.sort/Comparator.comparing 在旧系统上没有平台实现。
                        Collections.sort(entries, (left, right) -> {
                            if (left.isDirectory != right.isDirectory) {
                                return left.isDirectory ? -1 : 1;
                            }
                            return left.name.toLowerCase(Locale.ROOT)
                                    .compareTo(right.name.toLowerCase(Locale.ROOT));
                        });
                    }
                }
            } catch (SecurityException exception) {
                entries = new ArrayList<>();
                errorString = R.string.folder_cannot_be_accessed;
            } catch (RuntimeException exception) {
                entries = new ArrayList<>();
                errorString = R.string.folder_cannot_be_accessed;
            }
            List<FileItem> result = entries;
            int error = errorString;
            MAIN_HANDLER.post(() -> {
                if (mReleased || generation != mLoadGeneration.get()) {
                    return;
                }
                setLoading(false);
                if (error != 0) {
                    mAdapter.submit(result);
                    showStatus(error);
                } else if (result.isEmpty()) {
                    mAdapter.submit(result);
                    showStatus(R.string.folder_is_empty);
                } else {
                    mStatusView.setVisibility(GONE);
                    mRecyclerView.setVisibility(VISIBLE);
                    mAdapter.submit(result);
                }
            });
        });
    }

    private boolean isWithinRoot(@NonNull File candidate) {
        return isWithinRoot(mRootDirectory, candidate);
    }

    private static boolean isWithinRoot(@NonNull File rootDirectory, @NonNull File candidate) {
        try {
            String root = rootDirectory.getCanonicalPath();
            String target = candidate.getCanonicalPath();
            return target.equals(root) || target.startsWith(root.endsWith(File.separator) ? root : root + File.separator);
        } catch (IOException | SecurityException exception) {
            return false;
        }
    }

    private void showStatus(int message) {
        mStatusView.setText(message);
        mStatusView.setVisibility(VISIBLE);
        mRecyclerView.setVisibility(GONE);
        mStatusView.setOnClickListener(view -> {
            if (!isClickThrottled()) {
                loadCurrentDirectory();
            }
        });
    }

    private void setLoading(boolean loading) {
        mProgressBar.setVisibility(loading ? VISIBLE : GONE);
        if (loading) {
            mStatusView.setVisibility(GONE);
            mRecyclerView.setVisibility(GONE);
        }
    }

    private boolean isClickThrottled() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (mLastClickTime != 0L && now - mLastClickTime < CLICK_THROTTLE_MS) {
            return true;
        }
        mLastClickTime = now;
        return false;
    }

    private int dp(int dimensionResource) {
        return dp(getContext(), dimensionResource);
    }

    private static int dp(Context context, int dimensionResource) {
        return context.getResources().getDimensionPixelSize(dimensionResource);
    }

    private static TextView createText(Context context, int stringId, int sizeResource) {
        TextView view = new TextView(context);
        if (stringId != 0) {
            view.setText(stringId);
        }
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(sizeResource));
        view.setTextColor(ContextCompat.getColor(context, R.color.showlog_text_primary));
        return view;
    }

    private static final class FileAdapter extends RecyclerView.Adapter<FileRowHolder> {
        private final Context mContext;
        private final EntryClickListener mListener;
        private final List<FileItem> mItems = new ArrayList<>();

        FileAdapter(Context context, EntryClickListener listener) {
            mContext = context;
            mListener = listener;
        }

        void submit(List<FileItem> files) {
            List<FileItem> oldItems = new ArrayList<>(mItems);
            List<FileItem> newItems = new ArrayList<>(files);
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldItems.size();
                }

                @Override
                public int getNewListSize() {
                    return newItems.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return oldItems.get(oldItemPosition).file.equals(newItems.get(newItemPosition).file);
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    FileItem oldItem = oldItems.get(oldItemPosition);
                    FileItem newItem = newItems.get(newItemPosition);
                    return oldItem.name.equals(newItem.name)
                            && oldItem.isDirectory == newItem.isDirectory
                            && oldItem.length == newItem.length;
                }
            });
            mItems.clear();
            mItems.addAll(newItems);
            diff.dispatchUpdatesTo(this);
        }

        void clear() {
            mItems.clear();
        }

        @NonNull
        @Override
        public FileRowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(mContext);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(mContext, R.dimen.dp_12), 0, dp(mContext, R.dimen.dp_12), 0);
            TextView label = createText(mContext, 0, R.dimen.sp_14);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView detail = createText(mContext, 0, R.dimen.sp_12);
            detail.setTextColor(ContextCompat.getColor(mContext, R.color.showlog_text_secondary));
            row.addView(detail, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            return new FileRowHolder(row, label, detail);
        }

        @Override
        public void onBindViewHolder(@NonNull FileRowHolder holder, int position) {
            FileItem item = mItems.get(position);
            holder.name.setText(mContext.getString(
                    item.isDirectory ? R.string.folder_item_name : R.string.file_item_name,
                    item.name));
            holder.detail.setText(item.isDirectory || item.length < 0
                    ? ""
                    : android.text.format.Formatter.formatFileSize(mContext, item.length));
            holder.itemView.setOnClickListener(view -> mListener.onClick(item.file));
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }
    }

    private static final class FileItem {
        final File file;
        final String name;
        final boolean isDirectory;
        final long length;

        FileItem(File file, String name, boolean isDirectory, long length) {
            this.file = file;
            this.name = name;
            this.isDirectory = isDirectory;
            this.length = length;
        }
    }

    private static final class FileRowHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView detail;

        FileRowHolder(View itemView, TextView name, TextView detail) {
            super(itemView);
            this.name = name;
            this.detail = detail;
            itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(itemView.getContext(), ROW_HEIGHT_RES)));
        }
    }

    private interface EntryClickListener {
        void onClick(File file);
    }

    /**
     * 独立预览弹框；图片只进行平台原生采样解码，文本始终按页读入以限制常驻内存。
     */
    private static final class FilePreviewDialog extends android.app.Dialog {
        private final File mAllowedRoot;
        private final File mFile;
        private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
        private final List<Long> mPageOffsets = new ArrayList<>();
        private final AtomicInteger mGeneration = new AtomicInteger();
        private LinearLayout mContent;
        private TextView mTextView;
        private TextView mPageLabel;
        private Button mPreviousButton;
        private Button mNextButton;
        private Bitmap mBitmap;
        private int mCurrentPageCharCount;
        private boolean mPageLoading;
        private long mLastActionTime;
        private int mPageIndex;

        FilePreviewDialog(@NonNull Context context, @NonNull File allowedRoot, @NonNull File file) {
            super(context);
            mAllowedRoot = allowedRoot;
            mFile = file;
            mPageOffsets.add(0L);
        }

        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            LinearLayout root = new LinearLayout(getContext());
            root.setOrientation(VERTICAL);
            root.setPadding(dp(getContext(), R.dimen.dp_12), dp(getContext(), R.dimen.dp_12), dp(getContext(), R.dimen.dp_12), dp(getContext(), R.dimen.dp_12));
            root.setBackgroundResource(R.drawable.bg_showlog_dialog);

            LinearLayout header = new LinearLayout(getContext());
            header.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView title = createText(getContext(), 0, R.dimen.sp_14);
            title.setTypeface(null, Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            title.setText(mFile.getName());
            header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView share = createText(getContext(), R.string.show_sdk_share, R.dimen.sp_14);
            share.setGravity(Gravity.CENTER);
            share.setPadding(dp(getContext(), R.dimen.dp_12), dp(getContext(), R.dimen.dp_8), dp(getContext(), R.dimen.dp_12), dp(getContext(), R.dimen.dp_8));
            share.setOnClickListener(view -> {
                if (isActionThrottled()) return;
                if (!isWithinRoot(mAllowedRoot, mFile)) {
                    showUnsupported();
                    return;
                }
                share.setEnabled(false);
                share.setText(R.string.preparing_file_to_share);
                ShareUtils.shareFile(getContext(), mFile.getAbsolutePath(), success -> {
                    if (isShowing()) {
                        share.setText(R.string.show_sdk_share);
                        share.setEnabled(true);
                    }
                });
            });
            header.addView(share, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            FrameLayout body = new FrameLayout(getContext());
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            bodyParams.topMargin = dp(getContext(), R.dimen.dp_10);
            root.addView(body, bodyParams);
            mContent = new LinearLayout(getContext());
            mContent.setOrientation(VERTICAL);
            mContent.setBackgroundResource(R.drawable.show_sdk_content_bg);
            mContent.setPadding(dp(getContext(), R.dimen.dp_8), dp(getContext(), R.dimen.dp_8), dp(getContext(), R.dimen.dp_8), dp(getContext(), R.dimen.dp_8));
            body.addView(mContent, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            setContentView(root);
            Window window = getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setLayout((int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.90f), (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.78f));
            }
            loadPreview();
        }

        private void loadPreview() {
            int generation = mGeneration.incrementAndGet();
            mExecutor.execute(() -> {
                PreviewKind kind;
                try {
                    kind = detectKind(mAllowedRoot, mFile);
                } catch (RuntimeException | OutOfMemoryError exception) {
                    kind = PreviewKind.UNSUPPORTED;
                }
                PreviewKind detectedKind = kind;
                MAIN_HANDLER.post(() -> {
                    if (!isShowing() || generation != mGeneration.get()) return;
                    if (detectedKind == PreviewKind.TEXT) {
                        showTextPage(0);
                    } else if (detectedKind == PreviewKind.IMAGE) {
                        showImage();
                    } else {
                        showUnsupported();
                    }
                });
            });
        }

        private void showTextPage(int pageIndex) {
            mPageIndex = Math.max(0, pageIndex);
            long offset = mPageOffsets.get(mPageIndex);
            mContent.removeAllViews();
            ScrollView scroll = new ScrollView(getContext());
            mTextView = createText(getContext(), 0, R.dimen.sp_13);
            mTextView.setTextIsSelectable(true);
            scroll.addView(mTextView, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            mContent.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            LinearLayout controls = new LinearLayout(getContext());
            controls.setGravity(Gravity.CENTER_VERTICAL);
            mContent.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            mPreviousButton = new Button(getContext());
            mPreviousButton.setText(R.string.previous_page);
            mPreviousButton.setOnClickListener(view -> {
                if (!isActionThrottled() && mPageIndex > 0) {
                    showTextPage(mPageIndex - 1);
                }
            });
            controls.addView(mPreviousButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            mPageLabel = createText(getContext(), 0, R.dimen.sp_12);
            mPageLabel.setGravity(Gravity.CENTER);
            controls.addView(mPageLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            mNextButton = new Button(getContext());
            mNextButton.setText(R.string.next_page);
            mNextButton.setOnClickListener(view -> {
                if (!isActionThrottled()) loadNextPage();
            });
            controls.addView(mNextButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            mPreviousButton.setEnabled(pageIndex > 0);
            mNextButton.setEnabled(false);
            mPageLabel.setText(getContext().getString(R.string.page_number, pageIndex + 1));
            loadTextAsync(offset, pageIndex);
        }

        private void loadNextPage() {
            if (mPageLoading) {
                return;
            }
            int nextPage = mPageIndex + 1;
            if (nextPage >= mPageOffsets.size()) {
                long nextOffset = mPageOffsets.get(mPageIndex) + mCurrentPageCharCount;
                mPageOffsets.add(nextOffset);
            }
            showTextPage(nextPage);
        }

        private void loadTextAsync(long offset, int pageIndex) {
            int generation = mGeneration.incrementAndGet();
            mPageLoading = true;
            mPreviousButton.setEnabled(false);
            mNextButton.setEnabled(false);
            mTextView.setText(R.string.reading_folder_contents);
            mExecutor.execute(() -> {
                TextPageData page;
                try {
                    if (!isWithinRoot(mAllowedRoot, mFile)) {
                        throw new SecurityException("File left the configured root directory");
                    }
                    page = readTextPage(mAllowedRoot, mFile, offset, TEXT_PAGE_CHARS);
                } catch (Exception exception) {
                    page = null;
                }
                TextPageData result = page;
                MAIN_HANDLER.post(() -> {
                    if (!isShowing() || generation != mGeneration.get() || pageIndex != mPageIndex) return;
                    mPageLoading = false;
                    if (result == null) {
                        showUnsupported();
                        return;
                    }
                    mTextView.setText(result.text);
                    mCurrentPageCharCount = result.text.length();
                    mPreviousButton.setEnabled(pageIndex > 0);
                    mNextButton.setEnabled(result.hasMore);
                });
            });
        }

        private void showImage() {
            mContent.removeAllViews();
            ImageView imageView = new ImageView(getContext());
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mContent.addView(imageView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            imageView.post(() -> {
                if (isShowing()) {
                    decodeImage(imageView);
                }
            });
        }

        private void decodeImage(ImageView imageView) {
            int targetWidth = Math.max(1, imageView.getWidth());
            int targetHeight = Math.max(1, imageView.getHeight());
            int generation = mGeneration.incrementAndGet();
            mExecutor.execute(() -> {
                Bitmap decoded = null;
                try {
                    if (!isWithinRoot(mAllowedRoot, mFile)) {
                        throw new SecurityException("File left the configured root directory");
                    }
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(mFile.getAbsolutePath(), bounds);
                    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                        int sample = 1;
                        while (bounds.outWidth / sample > targetWidth * 2 || bounds.outHeight / sample > targetHeight * 2) {
                            sample *= 2;
                        }
                        while (decoded == null && sample <= 1 << 20) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inSampleSize = sample;
                            // 默认 ARGB_8888 保留透明 PNG 的 alpha；采样率负责把像素内存控制在视图尺寸附近。
                            try {
                                decoded = BitmapFactory.decodeFile(mFile.getAbsolutePath(), options);
                            } catch (OutOfMemoryError error) {
                                decoded = null;
                            }
                            if (decoded == null) sample *= 2;
                        }
                    }
                } catch (RuntimeException | OutOfMemoryError ignored) {
                    decoded = null;
                }
                Bitmap result = decoded;
                MAIN_HANDLER.post(() -> {
                    if (!isShowing() || generation != mGeneration.get()) {
                        if (result != null) result.recycle();
                        return;
                    }
                    if (result == null) {
                        showUnsupported();
                    } else {
                        mBitmap = result;
                        imageView.setImageBitmap(result);
                    }
                });
            });
        }

        private void showUnsupported() {
            mContent.removeAllViews();
            TextView message = createText(getContext(), R.string.file_preview_not_supported_share_with_another_app, R.dimen.sp_14);
            message.setGravity(Gravity.CENTER);
            mContent.addView(message, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        private boolean isActionThrottled() {
            long now = android.os.SystemClock.elapsedRealtime();
            if (mLastActionTime != 0L && now - mLastActionTime < CLICK_THROTTLE_MS) return true;
            mLastActionTime = now;
            return false;
        }

        @Override
        public void dismiss() {
            mGeneration.incrementAndGet();
            mExecutor.shutdownNow();
            if (mBitmap != null && !mBitmap.isRecycled()) {
                mBitmap.recycle();
                mBitmap = null;
            }
            super.dismiss();
        }
    }

    private enum PreviewKind { TEXT, IMAGE, UNSUPPORTED }

    private static PreviewKind detectKind(File rootDirectory, File file) {
        if (!isWithinRoot(rootDirectory, file)) return PreviewKind.UNSUPPORTED;
        if (!file.isFile() || !file.canRead()) return PreviewKind.UNSUPPORTED;
        String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                android.webkit.MimeTypeMap.getFileExtensionFromUrl(file.getName()).toLowerCase(Locale.ROOT));
        if ("application/pdf".equals(mime)) return PreviewKind.UNSUPPORTED;
        if (mime != null && mime.startsWith("image/")) return PreviewKind.IMAGE;
        if (mime != null && (mime.startsWith("text/") || mime.equals("application/json") || mime.equals("application/xml"))) return PreviewKind.TEXT;
        BitmapFactory.Options imageBounds = new BitmapFactory.Options();
        imageBounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), imageBounds);
        if (imageBounds.outWidth > 0 && imageBounds.outHeight > 0) return PreviewKind.IMAGE;
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] sample = new byte[4096];
            int count = input.read(sample);
            if (count < 0) return PreviewKind.TEXT;
            if (count >= 5 && sample[0] == '%' && sample[1] == 'P' && sample[2] == 'D'
                    && sample[3] == 'F' && sample[4] == '-') {
                return PreviewKind.UNSUPPORTED;
            }
            // UTF-16 BOM 后的 NUL 字节是编码特征，不应误判成二进制文件。
            if (count >= 2 && (((sample[0] & 0xff) == 0xff && (sample[1] & 0xff) == 0xfe)
                    || ((sample[0] & 0xff) == 0xfe && (sample[1] & 0xff) == 0xff))) {
                return PreviewKind.TEXT;
            }
            for (int index = 0; index < count; index++) {
                int value = sample[index] & 0xff;
                if (value == 0) return PreviewKind.UNSUPPORTED;
                if (value < 0x09 || (value > 0x0d && value < 0x20)) return PreviewKind.UNSUPPORTED;
            }
            return PreviewKind.TEXT;
        } catch (IOException | SecurityException exception) {
            return PreviewKind.UNSUPPORTED;
        }
    }

    private static TextPageData readTextPage(File rootDirectory, File file, long offset, int maxChars) throws IOException {
        if (!isWithinRoot(rootDirectory, file)) {
            throw new SecurityException("File left the configured root directory");
        }
        try (Reader reader = openTextReader(file)) {
            skipFully(reader, offset);
            char[] buffer = new char[Math.min(8192, maxChars)];
            StringBuilder result = new StringBuilder(maxChars);
            int remaining = maxChars;
            while (remaining > 0) {
                int read = reader.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) break;
                result.append(buffer, 0, read);
                remaining -= read;
            }
            boolean hasMore = false;
            if (remaining == 0 && reader.markSupported()) {
                reader.mark(1);
                hasMore = reader.read() >= 0;
                reader.reset();
            }
            return new TextPageData(result.toString(), hasMore);
        }
    }

    private static final class TextPageData {
        final String text;
        final boolean hasMore;

        TextPageData(String text, boolean hasMore) {
            this.text = text;
            this.hasMore = hasMore;
        }
    }

    private static BufferedReader openTextReader(File file) throws IOException {
        PushbackInputStream input = new PushbackInputStream(new FileInputStream(file), 3);
        byte[] prefix = new byte[3];
        int count = input.read(prefix);
            while (count >= 0 && count < prefix.length) {
                int read = input.read(prefix, count, prefix.length - count);
                if (read < 0) break;
                count += read;
            }
        Charset charset = StandardCharsets.UTF_8;
        int skip = 0;
        if (count >= 3 && (prefix[0] & 0xff) == 0xef && (prefix[1] & 0xff) == 0xbb && (prefix[2] & 0xff) == 0xbf) {
            skip = 3;
        } else if (count >= 2 && (prefix[0] & 0xff) == 0xff && (prefix[1] & 0xff) == 0xfe) {
            charset = Charset.forName("UTF-16LE");
            skip = 2;
        } else if (count >= 2 && (prefix[0] & 0xff) == 0xfe && (prefix[1] & 0xff) == 0xff) {
            charset = Charset.forName("UTF-16BE");
            skip = 2;
        }
        if (count > skip) input.unread(prefix, skip, count - skip);
        return new BufferedReader(new InputStreamReader(input, charset), 8192);
    }

    private static void skipFully(Reader reader, long chars) throws IOException {
        long remaining = chars;
        char[] buffer = new char[8192];
        while (remaining > 0) {
            long skipped = reader.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else {
                int read = reader.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) break;
                remaining -= read;
            }
        }
    }
}
