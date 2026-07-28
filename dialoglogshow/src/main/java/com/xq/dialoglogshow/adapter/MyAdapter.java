package com.xq.dialoglogshow.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.dialog.DialogShowLogInfo;
import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.config.ShowLogProvider;
import com.xq.dialoglogshow.manager.ShowLogManager;
import com.xq.dialoglogshow.utils.ClipboardUtils;
import com.xq.dialoglogshow.utils.DateUtils;
import com.xq.dialoglogshow.utils.ShareUtils;
import com.xq.dialoglogshow.view.BigTextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Android-小强 on 2023/1/10.
 * mailbox:980766134@qq.com
 * description:
 */
public class MyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements View.OnClickListener, View.OnLongClickListener {

    private Context mContext;
    private RecyclerView mRecyclerView;
    private LayoutInflater mLayoutInflater;
    private List<BaseShowData> mList = new ArrayList<>();
    public ClickAdapter mClickAdapter;

    public void removedHttpData(BaseShowData data, int position) {
        mList.remove(position);
        notifyItemRemoved(position);
    }

    public interface ClickAdapter {
        void clickDeleteHttpLog(BaseShowData data, int position);

        void clickVpnHeaderAction();

        void clickVpnHeaderCertificateAction();
    }

    public List<BaseShowData> getListNodeData() {
        return mList;
    }

    public void setList(List<BaseShowData> baseShowData) {
        mList.clear();
        if (baseShowData != null) {
            mList.addAll(baseShowData);
        }
        if (mList.isEmpty()) {
            Toast.makeText(mContext.getApplicationContext(), R.string.show_sdk_no_data, Toast.LENGTH_SHORT).show();
        }
        notifyDataSetChanged();
    }
    private View rootView;

    public MyAdapter(Context context, RecyclerView recyclerView,View rootView) {
        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        mRecyclerView = recyclerView;
        this.rootView = rootView;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == BaseShowData.TYE_ONE) {
            //普通
            MyViewHolderData myViewHolderData = new MyViewHolderData(mLayoutInflater
                    .inflate(R.layout.show_sdk_httplog_item_one_layout,
                            parent, false));

            myViewHolderData.itemView.setOnClickListener(this);
            myViewHolderData.itemView.setOnLongClickListener(this);

            return myViewHolderData;
        }
        if (viewType == BaseShowData.TYE_TOW) {
            //父亲
            MyViewHolderHttpFather myViewHolderHttpFather = new MyViewHolderHttpFather(mLayoutInflater
                    .inflate(R.layout.show_sdk_httplog_item_tow_layout,
                            parent, false));

            myViewHolderHttpFather.itemView.setOnClickListener(this);
            myViewHolderHttpFather.itemView.setOnLongClickListener(this);

            return myViewHolderHttpFather;
        }
        if (viewType == BaseShowData.TYE_THREE) {
            //孩子
            MyViewHolderHttpChild myViewHolderHttpChild = new MyViewHolderHttpChild(mLayoutInflater
                    .inflate(R.layout.show_sdk_httplog_item_three_layout,
                            parent, false));

            myViewHolderHttpChild.viewDelete.setOnClickListener(this);
            myViewHolderHttpChild.itemView.setOnClickListener(this);
            myViewHolderHttpChild.itemView.setOnLongClickListener(this);

            return myViewHolderHttpChild;
        }
        if (viewType == BaseShowData.TYE_FOUR) {
            MyViewHolderVpnHeader myViewHolderVpnHeader = new MyViewHolderVpnHeader(mLayoutInflater
                    .inflate(R.layout.show_sdk_vpn_log_header_layout, parent, false));
            myViewHolderVpnHeader.actionView.setOnClickListener(this);
            myViewHolderVpnHeader.certificateActionView.setOnClickListener(this);
            return myViewHolderVpnHeader;
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        int layoutPosition = holder.getLayoutPosition();
        if (holder instanceof MyViewHolderData) {
            //普通
            ((MyViewHolderData) holder).setData(mList.get(layoutPosition), mList);
        }
        if (holder instanceof MyViewHolderHttpFather) {
            //父亲
            ((MyViewHolderHttpFather) holder).setData(mList.get(layoutPosition));
        }
        if (holder instanceof MyViewHolderHttpChild) {
            //孩子
            ((MyViewHolderHttpChild) holder).setData(mList.get(layoutPosition));
        }
        if (holder instanceof MyViewHolderVpnHeader) {
            ((MyViewHolderVpnHeader) holder).setData(mList.get(layoutPosition));
        }
    }

    @Override
    public int getItemViewType(int position) {
        return mList.get(position).getItemType();
//            return super.getItemViewType(position);
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    /**
     * 分享 当前集合数据
     */
    public void share() {

    }


    private long lastClickTime = 0;

    @Override
    public void onClick(View v) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < 500) {
            return; // 500ms 防抖处理，避免频繁重复点击
        }
        lastClickTime = currentTime;

        RecyclerView.ViewHolder containingViewHolder = mRecyclerView.findContainingViewHolder(v);
        if (containingViewHolder instanceof MyViewHolderHttpFather) {


            //父亲
            int layoutPosition = containingViewHolder.getLayoutPosition();
            BaseShowData data = mList.get(layoutPosition);


            boolean expansion = data.isExpansion();
            data.setExpansion(!expansion);
            notifyItemChanged(layoutPosition);
            if (expansion) {
                //展开了 要关闭
                List<BaseShowData> list = data.getList();
                if (list != null) {
                    int size = list.size();
                    for (int i = 0; i < size; i++) {
                        mList.remove(layoutPosition + 1);
                    }
                    notifyItemRangeRemoved(layoutPosition + 1, size);
                }

            } else {
                //关闭了 要开展
                List<BaseShowData> list = data.getList();
                if (list != null) {
                    int size = list.size();
                    for (int i = size - 1; i >= 0; i--) {
                        BaseShowData data1 = list.get(i);
                        mList.add(layoutPosition + 1, data1);
                    }
                    notifyItemRangeInserted(layoutPosition + 1, size);
                }
            }
        }
        if (containingViewHolder instanceof MyViewHolderHttpChild) {
            //孩子
            if (v.getId() == R.id.show_sdk_id_dialog_http_log_delete) {
                int layoutPosition = containingViewHolder.getLayoutPosition();
                BaseShowData data = mList.get(layoutPosition);
                if (mClickAdapter == null) {
                    return;
                }
                mClickAdapter.clickDeleteHttpLog(data, layoutPosition);
            } else {
                int layoutPosition = containingViewHolder.getLayoutPosition();
                BaseShowData data = mList.get(layoutPosition);
                if (true) {
                    //暂时改为跳转到详情页
                    new DialogShowLogInfo(mContext, data.getContent(), containingViewHolder.itemView,rootView).show();
                    return;
                }
                data.setExpansion(!data.isExpansion());
                notifyItemChanged(layoutPosition);
            }

        }
        if (containingViewHolder instanceof MyViewHolderData) {
            // 为什么这样做：VPN 历史日志当前走普通列表样式，如果这里不接详情弹窗，开发者只能看到摘要，无法查看完整头/体明细。
            int layoutPosition = containingViewHolder.getLayoutPosition();
            BaseShowData data = mList.get(layoutPosition);
            if (!TextUtils.isEmpty(data.getContent())) {
                new DialogShowLogInfo(mContext, data.getContent(), containingViewHolder.itemView, rootView).show();
            }
        }
        if (containingViewHolder instanceof MyViewHolderVpnHeader) {
            if (mClickAdapter == null) {
                return;
            }
            if (v.getId() == R.id.show_sdk_id_vpn_log_header_action) {
                mClickAdapter.clickVpnHeaderAction();
            } else if (v.getId() == R.id.show_sdk_id_vpn_log_header_cert_action) {
                mClickAdapter.clickVpnHeaderCertificateAction();
            }
        }
    }

    @Override
    public boolean onLongClick(View v) {

        RecyclerView.ViewHolder containingViewHolder = mRecyclerView.findContainingViewHolder(v);
        if (containingViewHolder instanceof MyViewHolderHttpFather) {
            //父亲
            int layoutPosition = containingViewHolder.getLayoutPosition();
            BaseShowData data = mList.get(layoutPosition);
            List<BaseShowData> list = data.getList();
            JSONArray jsonArray = new JSONArray();
            for (BaseShowData da : list) {
                jsonArray.put(da.getContent());
            }
            String string = jsonArray.toString();

            ClipboardUtils.copyText(string, mContext);
            ShareUtils.shareText(mContext, string);
        }
        if (containingViewHolder instanceof MyViewHolderHttpChild) {
            //孩子
            int layoutPosition = containingViewHolder.getLayoutPosition();
            BaseShowData data = mList.get(layoutPosition);
            String content = data.getContent();
            ClipboardUtils.copyText(content, mContext);
            ShareUtils.shareText(mContext, content);


        }
        if (containingViewHolder instanceof MyViewHolderData) {
            //其他
            int layoutPosition = containingViewHolder.getLayoutPosition();
            BaseShowData data = mList.get(layoutPosition);
            String content = data.getContent();
            ClipboardUtils.copyText(content, mContext);
            ShareUtils.shareText(mContext, content);
        }


        return true;
    }
}

/**
 * 网络日志 父类
 */
class MyViewHolderHttpFather extends RecyclerView.ViewHolder {
    private TextView mTextView;
    private View mViewArrow;

    public MyViewHolderHttpFather(View itemView) {
        super(itemView);
        mTextView = itemView.findViewById(R.id.show_sdk_id_item_http_log_one_tv_content);
        mViewArrow = itemView.findViewById(R.id.show_sdk_id_item_http_log_one__iv_jaintou);
    }

    @SuppressLint("SetTextI18n")
    public void setData(BaseShowData baseShowData) {
        String status = !android.text.TextUtils.isEmpty(baseShowData.getResMsg()) ? (" [" + baseShowData.getResMsg() + "]") : "";
        int childCount = baseShowData.getList() != null ? baseShowData.getList().size() : 0;
        mTextView.setText("Index：" + (baseShowData.getIndex())
                + status + " (明细：" + childCount + "项)"
                + "\n"
                + "Url：" + baseShowData.getUrl());
        if (baseShowData.isExpansion()) {
            mViewArrow.setRotation(90);
        } else {
            mViewArrow.setRotation(0);
        }
    }
}

/**
 * 网络日志 子类
 */
class MyViewHolderHttpChild extends RecyclerView.ViewHolder {

    BigTextView mBigTextView;
    View viewDelete;

    public MyViewHolderHttpChild(View itemView) {
        super(itemView);
        mBigTextView = itemView.findViewById(R.id.show_sdk_id_dialog_http_log_tvbig);
        viewDelete = itemView.findViewById(R.id.show_sdk_id_dialog_http_log_delete);
        ShowLogProvider provider = ShowLogManager.getInstance().getProvider();
        View viewById = itemView.findViewById(R.id.show_sdk_id_dialog_http_log_root);
        if (viewById != null && viewById.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) viewById.getLayoutParams();
            if (provider.isSortUrlForTime()) {
                // 常用列表模式：无需左侧树状大缩进，保持紧凑的 2dp 边距
                int m = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2F, itemView.getResources().getDisplayMetrics()) + 0.5F);
                lp.setMargins(m, m, m, m);
            } else {
                // 父子树状展开模式：保留左侧 14dp 自然层级缩进
                int start = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14F, itemView.getResources().getDisplayMetrics()) + 0.5F);
                int top = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2F, itemView.getResources().getDisplayMetrics()) + 0.5F);
                int end = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2F, itemView.getResources().getDisplayMetrics()) + 0.5F);
                int bottom = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4F, itemView.getResources().getDisplayMetrics()) + 0.5F);
                lp.setMargins(start, top, end, bottom);
            }
            viewById.setLayoutParams(lp);
        }
    }

    public void setData(BaseShowData baseShowData) {
        String format = DateUtils.format(baseShowData.getTime());
        String status = !android.text.TextUtils.isEmpty(baseShowData.getResMsg()) ? (" [" + baseShowData.getResMsg() + "]") : "";
        String msg = itemView.getContext().getString(
                R.string.show_sdk_http_child_summary,
                String.valueOf(baseShowData.getIndex()),
                status,
                format,
                baseShowData.getUrl() != null ? baseShowData.getUrl() : itemView.getContext().getString(R.string.show_sdk_no_data));
        mBigTextView.setMessage(msg);
    }
}

/**
 * 其他数据的item
 */
class MyViewHolderData extends RecyclerView.ViewHolder {

    private TextView mTextView;

    public MyViewHolderData(View itemView) {
        super(itemView);
        mTextView = itemView.findViewById(R.id.show_sdk_id_item_http_log_one_tv_content);
    }

    public void setData(BaseShowData data, List<BaseShowData> list) {
        String msg;
        if (!TextUtils.isEmpty(data.getResMsg())) {
            // 为什么这样做：VPN 一级列表摘要已经在外层收口为“时间/URL/结果/状态码”，这里优先展示摘要，避免一屏塞满完整报文。
            msg = data.getResMsg();
        } else {
            int layoutPosition = getLayoutPosition();
            int size = list.size();
            int i = size - layoutPosition;
            msg = itemView.getContext().getString(R.string.show_sdk_index_prefix, String.valueOf(i))
                    + "\n"
                    + data.getContent();
        }
        mTextView.setText(msg);
    }
}

/**
 * VPN 日志列表头部操作区
 */
class MyViewHolderVpnHeader extends RecyclerView.ViewHolder {

    TextView actionView;
    TextView certificateActionView;

    public MyViewHolderVpnHeader(View itemView) {
        super(itemView);
        actionView = itemView.findViewById(R.id.show_sdk_id_vpn_log_header_action);
        certificateActionView = itemView.findViewById(R.id.show_sdk_id_vpn_log_header_cert_action);
    }

    /**
     * 为什么这样做：列表已经有日志时，开发者通常是在“看日志的过程中顺手操作”，
     * 因此这里把开关与证书入口直接跟随列表内容展示，避免来回切换面板。
     */
    public void setData(BaseShowData data) {
        actionView.setText(data.getContent());
        if (TextUtils.isEmpty(data.getResMsg())) {
            certificateActionView.setVisibility(View.GONE);
        } else {
            certificateActionView.setVisibility(View.VISIBLE);
            certificateActionView.setText(data.getResMsg());
        }
    }
}
