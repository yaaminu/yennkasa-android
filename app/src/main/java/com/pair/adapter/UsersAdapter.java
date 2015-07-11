package com.pair.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.data.User;
import com.pair.pairapp.R;

import io.realm.RealmBaseAdapter;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class UsersAdapter extends RealmBaseAdapter<User> {
    public UsersAdapter(Context context, RealmResults<User> realmResults, boolean automaticUpdate) {
        super(context, realmResults, automaticUpdate);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        context = parent.getContext();
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.user_item, parent, false);
            ViewHolder holder = new ViewHolder();
            holder.iv = ((ImageView) convertView.findViewById(R.id.iv_display_picture));
            holder.tv = ((TextView) convertView.findViewById(R.id.tv_user_name));
            convertView.setTag(holder);
        }
        ViewHolder holder = (ViewHolder) convertView.getTag();
        holder.user = getItem(position);
        holder.tv.setText(getItem(position).getName());
        return convertView;
    }

    public class ViewHolder {
        public User user;
        ImageView iv;
        TextView tv;
    }
}
