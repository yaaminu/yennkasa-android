package com.pair.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.Config;
import com.pair.pairapp.R;
import com.pair.util.PicassoWrapper;
import com.pair.util.UiHelpers;
import com.squareup.picasso.Picasso;

import io.realm.RealmBaseAdapter;
import io.realm.RealmList;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 6/28/2015.
 */
public class GroupsAdapter extends RealmBaseAdapter<User> {
    private static final String TAG = GroupsAdapter.class.getSimpleName();
    private final Picasso PICASSO;

    public GroupsAdapter(Context context, RealmResults<User> realmResults) {
        super(context, realmResults, true);
        PICASSO = PicassoWrapper.with(context);
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
        PICASSO.load(Config.DP_ENDPOINT + "/" + group.getDP())
                .placeholder(R.drawable.group_avatar)
                .error(R.drawable.group_avatar)
                .resize(150, 150)
                .into(holder.groupIcon);

        RealmList<User> groupMembers = group.getMembers();
        User mainUser = UserManager.getInstance().getMainUser();
        StringBuilder members = new StringBuilder(groupMembers.size() * 10); //summary
        members.append("You");
        for (int i = 0; i < groupMembers.size(); i++) {
            User groupMember = groupMembers.get(i);
            if (!groupMember.get_id().equals(mainUser.get_id())) {
                members.append(",").append(groupMember.getName());
            }
        }
        holder.groupMembers.setText(members.toString());
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiHelpers.gotoProfileActivity(v.getContext(), group.get_id());
            }
        };
        holder.groupIcon.setOnClickListener(listener);
        holder.groupName.setOnClickListener(listener);
        Log.i(TAG, "Display Picture: " + group.getDP());
        return convertView;
    }

    public class ViewHolder {
        private TextView groupName,
                groupMembers;
        private ImageView groupIcon;
    }
}
