package com.pair.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.ui.DPLoader;
import com.pair.util.PLog;
import com.pair.util.UiHelpers;

import java.util.List;

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

    public static String join(Context context, CharSequence delimiter, List<User> users) {
        StringBuilder sb = new StringBuilder(users.size() * 10);
        String mainUserId = UserManager.getMainUserId();

        sb.append(context.getString(R.string.you));
        for (User user : users) {
            if (mainUserId.equals(user.getUserId())) {
                continue;
            }
            sb.append(delimiter);
            sb.append(user.getName());
        }
        return sb.toString();
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
            convertView.setTag(holder);
        } else {
            holder = ((ViewHolder) convertView.getTag());
        }
        holder.groupName.setText(group.getName());
        DPLoader.load(context,group.getUserId(), group.getDP())
                .placeholder(R.drawable.group_avatar)
                .error(R.drawable.group_avatar)
                .resize(150, 150)
                .into(holder.groupIcon);

        String users = join(context, ",", group.getMembers());
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
