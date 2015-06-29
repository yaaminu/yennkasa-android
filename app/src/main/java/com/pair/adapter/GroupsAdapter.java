package com.pair.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.pair.data.User;
import com.pair.pairapp.R;
import com.pair.util.UserManager;

import io.realm.RealmBaseAdapter;
import io.realm.RealmList;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 6/28/2015.
 */
public class GroupsAdapter extends RealmBaseAdapter<User> {
    public GroupsAdapter(Context context, RealmResults<User> realmResults) {
        super(context, realmResults, true);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        User group = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.group_list_item, parent, false);
            holder = new ViewHolder();
            holder.groupName = ((TextView) convertView.findViewById(R.id.tv_user_name));
            holder.groupMembers = ((TextView) convertView.findViewById(R.id.tv_group_members));
            convertView.setTag(holder);
        } else {
            holder = ((ViewHolder) convertView.getTag());
        }
        holder.groupName.setText(group.getName());
        RealmList<User> groupMembers = group.getMembers();
        User mainUser = UserManager.INSTANCE.getMainUser();
        StringBuilder members = new StringBuilder(groupMembers.size() * 10); //summary
        members.append("You");
        for (int i = 0; i < groupMembers.size(); i++) {
            User groupMember = groupMembers.get(i);
            if (!groupMember.get_id().equals(mainUser.get_id())) {
                members.append(groupMember.getName());
            }
            if ((i + 1) == groupMembers.size()) {
                continue;
            }
            members.append(",");
        }
        holder.groupMembers.setText(members.toString());
        return convertView;
    }

    private class ViewHolder {
        private TextView groupName,
                groupMembers;
    }
}
