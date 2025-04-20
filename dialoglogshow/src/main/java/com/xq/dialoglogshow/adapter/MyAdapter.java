package com.xq.dialoglogshow.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.entity.LogConfigData;
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
            Toast.makeText(mContext.getApplicationContext(), "无数据", Toast.LENGTH_SHORT).show();
        }
        notifyDataSetChanged();
    }

    public MyAdapter(Context context, RecyclerView recyclerView) {
        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        mRecyclerView = recyclerView;
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


    @Override
    public void onClick(View v) {
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
                data.setExpansion(!data.isExpansion());
                notifyItemChanged(layoutPosition);
            }

        }
        if (containingViewHolder instanceof MyViewHolderData) {
            //其他
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
        mTextView.setText("Index：" + (baseShowData.getIndex())
                + "，Count：" + baseShowData.getList().size()
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
        LogConfigData logConfigData = ShowLogManager.getCallback().loadConfig();
        if (logConfigData != null && logConfigData.sortUrlForTime) {
            View viewById = itemView.findViewById(R.id.show_sdk_id_dialog_http_log_root);
            int v = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8F, itemView.getResources().getDisplayMetrics()) + 0.5F);
            viewById.setPadding(v, v, v, v);
        }
    }

    public void setData(BaseShowData baseShowData) {

        if (baseShowData.isExpansion()) {
            mBigTextView.setMessage(baseShowData.getContent());
        } else {
            String format = DateUtils.format(baseShowData.getTime());
            String msg = "Count：" + baseShowData.getIndex() + "，" +
                    "Time：" + format + "\n" +
                    baseShowData.getUrl() + "\n" +
                    "Result：" + baseShowData.getResMsg();
            mBigTextView.setMessage(msg);
        }


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
        int layoutPosition = getLayoutPosition();
        int size = list.size();
        int i = size - layoutPosition;
        String msg = "Index：" + i + "\n" + data.getContent();
        mTextView.setText(msg);
    }
}

