package com.pair.adapter;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;

import com.pair.data.UserManager;
import com.pair.data.settings.PersistedSetting;
import com.pair.pairapp.R;
import com.rey.material.widget.CheckBox;
import com.rey.material.widget.TextView;

import java.util.List;

/**
 * @author Null-Pointer on 9/27/2015.
 */
public class SettingsAdapter extends BaseAdapter {

    private final List<PersistedSetting> results;
    private View.OnTouchListener touchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return true;
        }
    };

    public SettingsAdapter(List<PersistedSetting> results) {
        this.results = results;

    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public int getCount() {
        return results.size();
    }

    @Override
    public PersistedSetting getItem(int position) {
        return results.get(position);
    }

    @Override
    public int getItemViewType(int position) {
        PersistedSetting item = getItem(position);
        switch (item.getType()) {
            case PersistedSetting.TYPE_CATEGORY:
                return 0;
            case PersistedSetting.TYPE_TRUE_FALSE:
                return 1;
            case PersistedSetting.TYPE_LIST_STRING:
                return 2;
            case PersistedSetting.TYPE_INTEGER:
                return 2;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(layoutResources[getItemViewType(position)], parent, false);
            holder = new ViewHolder();
            holder.checkBox = ((CheckBox) convertView.findViewById(R.id.cb_checked));
            holder.summary = ((TextView) convertView.findViewById(R.id.tv_summary));
            holder.title = (TextView) convertView.findViewById(R.id.tv_title);
            convertView.setTag(holder);
        }
        holder = (ViewHolder) convertView.getTag();

        final PersistedSetting setting = getItem(position);

        holder.title.setText(setting.getTitle());
        if (setting.getType() != PersistedSetting.TYPE_CATEGORY) {
            if (setting.getType() == PersistedSetting.TYPE_TRUE_FALSE) {
                holder.checkBox.setCheckedImmediately(setting.getBoolValue());
                holder.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        boolean boolValue = setting.getBoolValue();
                        UserManager.getInstance().putPref(setting.getKey(), !boolValue);
                        setting.setBoolValue(!boolValue);
                    }
                });
            } else {
                holder.summary.setText(setting.getSummary());
            }
        } else {
            convertView.setOnTouchListener(touchListener);
        }
        try {
            if (position + 1 == getCount() || getItem(position + 1).getType() == PersistedSetting.TYPE_CATEGORY) {
                convertView.findViewById(R.id.divider).setVisibility(View.GONE);
            } else {
                convertView.findViewById(R.id.divider).setVisibility(View.VISIBLE);
            }
        } catch (Exception e) { //index out of bound
        }
        return convertView;
    }

    private class ViewHolder {
        CheckBox checkBox;
        TextView title, summary;
    }

    private final int[] layoutResources = {
            R.layout.category_list_item,
            R.layout.true_or_false_list_item,
            R.layout.list_list_item
    };
}
