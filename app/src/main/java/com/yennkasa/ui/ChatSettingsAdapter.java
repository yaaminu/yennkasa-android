package com.yennkasa.ui;

import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.rey.material.widget.CheckBox;
import com.rey.material.widget.TextView;
import com.yennkasa.R;
import com.yennkasa.adapter.YennkasaBaseAdapter;

import butterknife.Bind;

/**
 * Created by yaaminu on 1/12/17.
 */
public class ChatSettingsAdapter extends YennkasaBaseAdapter<ChatSettingsItem> {
    private final Delegate delegate;

    public ChatSettingsAdapter(Delegate delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    @Override
    public int getItemViewType(int position) {
        return delegate.getViewLayout(position);
    }

    @Override
    protected void doBindHolder(final Holder holder, int position) {
        ChatSettingsItem item = getItem(position);
        ((ChatSettingsHolder) holder).title.setText(item.title);
        if (((ChatSettingsHolder) holder).summary != null) {
            //noinspection ConstantConditions
            ((ChatSettingsHolder) holder).summary.setText(item.summary);
        }
        if (((ChatSettingsHolder) holder).checkBox != null) {
            //noinspection ConstantConditions
            ((ChatSettingsHolder) holder).checkBox.setChecked(item.value == 1);
            ((ChatSettingsHolder) holder).checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((ChatSettingsHolder) holder).itemView.performClick();
                }
            });
        }
        if (((ChatSettingsHolder) holder).divider != null) {
            //noinspection ConstantConditions
            ((ChatSettingsHolder) holder).divider.setVisibility(delegate.hideDivider(position) ?
                    View.GONE : View.VISIBLE);
        }
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        return new ChatSettingsHolder(view);
    }

    public static class ChatSettingsHolder extends Holder {
        @Bind(R.id.tv_title)
        TextView title;

        @Nullable
        @Bind(R.id.tv_summary)
        TextView summary;

        @Nullable
        @Bind(R.id.cb_checked)
        CheckBox checkBox;

        @Nullable
        @Bind(R.id.divider)
        View divider;

        public ChatSettingsHolder(View v) {
            super(v);
        }
    }

    public interface Delegate extends YennkasaBaseAdapter.Delegate<ChatSettingsItem> {
        String getUserId();

        @LayoutRes
        int getViewLayout(int position);

        boolean hideDivider(int position);
    }

}
