package com.idea.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.idea.util.UiHelpers;
import com.idea.data.User;
import com.idea.data.UserManager;
import com.idea.pairapp.R;
import com.idea.ui.DPLoader;
import com.idea.util.PLog;
import com.idea.util.TypeFaceUtil;
import com.idea.util.ViewUtils;
import android.text.TextUtils;
import java.util.*;
import io.realm.RealmBaseAdapter;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 6/28/2015.
 */
public class GroupsAdapter extends RealmBaseAdapter<User> {
    private static final String TAG = GroupsAdapter.class.getSimpleName();
    public GroupsAdapter(Context context, RealmResults<User> realmResults) {
        super(context, realmResults, true);
    }

    public static String join(CharSequence delimiter, List<User> users) {
        Set<String> names = new HashSet<>();
        for (User user : users) {
            names.add(user.getName());
        }
        return TextUtils.join(delimiter,names);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        final User group = getItem(position);
        if (convertView == null) {
            //noinspection ConstantConditions
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.group_list_item, parent, false);
            holder = new ViewHolder();
            holder.groupName = ((TextView) convertView.findViewById(R.id.tv_user_name));
            holder.groupMembers = ((TextView) convertView.findViewById(R.id.tv_group_members));
            holder.groupIcon = (ImageView) convertView.findViewById(R.id.iv_group_dp);
            ViewUtils.setTypeface(holder.groupName, TypeFaceUtil.DROID_SERIF_BOLD_TTF);
            ViewUtils.setTypeface(holder.groupMembers, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);
            convertView.setTag(holder);
        } else {
            holder = ((ViewHolder) convertView.getTag());
        }
        holder.groupName.setText(group.getName());
        DPLoader.load(context, group.getDP())
                .placeholder(R.drawable.group_avatar)
                .error(R.drawable.group_avatar)
                .resize(150, 150)
                .into(holder.groupIcon);

        String users = join(",", group.getMembers());
        holder.groupMembers.setText(users);
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiHelpers.gotoProfileActivity(v.getContext(), group.getUserId());
            }
        };
        holder.groupIcon.setOnClickListener(listener);
        holder.groupName.setOnClickListener(listener);
        PLog.i(TAG, "Display Picture: " + group.getDP());
        return convertView;
    }

    public class ViewHolder {
        private TextView groupName,
                groupMembers;
        private ImageView groupIcon;
    }
}
