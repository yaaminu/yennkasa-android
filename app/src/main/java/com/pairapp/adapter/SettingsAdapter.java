package com.pairapp.adapter;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;

import com.pairapp.R;
import com.pairapp.data.PersistedSetting;
import com.pairapp.data.UserManager;
import com.pairapp.util.TaskManager;
import com.pairapp.util.TypeFaceUtil;
import com.pairapp.util.UiHelpers;
import com.pairapp.util.ViewUtils;
import com.rey.material.widget.CheckBox;
import com.rey.material.widget.TextView;

import java.util.List;

/**
 * @author Null-Pointer on 9/27/2015.
 */
public class SettingsAdapter extends BaseAdapter {

    private final List<PersistedSetting> results;
    private final Delegate delegate;
    private View.OnTouchListener touchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return true;
        }
    };

    public SettingsAdapter(List<PersistedSetting> results, Delegate delegate) {
        this.results = results;
        this.delegate = delegate;
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
    public View getView(final int position, View convertView, final ViewGroup parent) {
        ViewHolder holder;
        final PersistedSetting setting = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(layoutResources[getItemViewType(position)], parent, false);
            holder = new ViewHolder();
            holder.checkBox = ((CheckBox) convertView.findViewById(R.id.cb_checked));
            holder.summary = ((TextView) convertView.findViewById(R.id.tv_summary));
            holder.title = (TextView) convertView.findViewById(R.id.tv_title);
            ViewUtils.setTypeface(holder.summary, TypeFaceUtil.ROBOTO_REGULAR_TTF);
            if (setting.getType() == PersistedSetting.TYPE_CATEGORY) {
                ViewUtils.setTypeface(holder.title, TypeFaceUtil.ROBOTO_BOLD_TTF);
            } else {
                ViewUtils.setTypeface(holder.title, TypeFaceUtil.ROBOTO_REGULAR_TTF);
            }
            convertView.setTag(holder);
        }
        holder = (ViewHolder) convertView.getTag();

        holder.title.setText(setting.getTitle());
        if (setting.getType() != PersistedSetting.TYPE_CATEGORY) {
            if (setting.getType() == PersistedSetting.TYPE_TRUE_FALSE) {
                holder.checkBox.setCheckedImmediately(setting.getBoolValue());
                final View finalConvertView = convertView;
                holder.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (setting.getKey().equals(UserManager.ENABLE_SEARCH)) {
                            updateSearchPreference(finalConvertView.getContext(), setting);
                        } else {
                            boolean boolValue = setting.getBoolValue();
                            UserManager.getInstance().putPref(setting.getKey(), !boolValue);
                            setting.setBoolValue(!boolValue);
                        }
                    }
                });
                holder.summary.setText(setting.getSummary());
            } else {
                holder.summary.setText(setting.getSummary());
            }
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    delegate.onItemClick(((AdapterView) parent), v, position, -1);
                }
            });
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

    private void updateSearchPreference(final Context context, final PersistedSetting settings) {
        boolean enable = !settings.getBoolValue();
        if (!enable) {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (i == DialogInterface.BUTTON_POSITIVE) {
                        settings.setBoolValue(false);
                        actuallyUpdateSearchSettings(context, false);
                    }
                }
            };
            new AlertDialog.Builder(context)
                    .setMessage(R.string.disableSearchWarning)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, listener)
                    .setNegativeButton(android.R.string.cancel, listener)
                    .create().show();
        } else {
            settings.setBoolValue(true);
            actuallyUpdateSearchSettings(context, true);
        }
    }

    private void actuallyUpdateSearchSettings(final Context context, boolean enable) {
        final ProgressDialog dialog = new ProgressDialog(context);
        dialog.setMessage(context.getString(R.string.st_please_wait));
        dialog.setCancelable(false);
        dialog.show();
        UserManager.getInstance().enableSearch(enable, new UserManager.CallBack() {
            @Override
            public void done(final Exception e) {
                dialog.dismiss();
                if (e != null) {
                    TaskManager.executeOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            UiHelpers.showPlainOlDialog(context, e.getMessage());
                        }
                    });
                }
            }
        });
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

    public interface Delegate {
        void onItemClick(AdapterView parent, View view, int position, long itemId);
    }
}
