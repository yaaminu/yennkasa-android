package com.pair.pairapp.ui;


import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.adapter.UsersAdapter;
import com.pair.data.User;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;
import com.squareup.picasso.Picasso;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;


/**
 * A simple {@link Fragment} subclass.
 */
public class UsersFragment extends Fragment implements AdapterView.OnItemClickListener {

    public static final String ARG_ACTION = "action",
            ARG_SHOW_GROUP_MEMBERS = "show_members",
            ARG_GROUP_ID = "id";

    public UsersFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Activity activity) {
        setRetainInstance(true);
        super.onAttach(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        String title = getArguments().getString(MainActivity.ARG_TITLE);
        ActionBarActivity activity = (ActionBarActivity) getActivity();
        View view = inflater.inflate(R.layout.fragment_freinds, container, false);
        GridView usersGrid = ((GridView) view.findViewById(R.id.gv_users));
        //noinspection ConstantConditions
        activity.getSupportActionBar().setTitle(title);
        Realm realm = Realm.getInstance(getActivity());
        String action = getArguments().getString(ARG_ACTION);
        BaseAdapter adapter;
        if (action != null && action.equals(ARG_SHOW_GROUP_MEMBERS)) {
            String groupId = getArguments().getString(ARG_GROUP_ID);
            if (groupId == null) {
                throw new IllegalArgumentException("id cannot be null");
            }
            User group = realm.where(User.class).equalTo("_id", groupId).findFirst();
            if (group == null) {
                throw new IllegalArgumentException("no group with id:  " + groupId);
            }
            adapter = new MembersAdapter(group.getMembers(), groupId);
        } else {
            RealmResults<User> results = realm.where(User.class).notEqualTo("_id", getCurrentUser().get_id()).findAllSorted("name", true);
            adapter = new UsersAdapter(getActivity(), results, true);
        }
        usersGrid.setAdapter(adapter);
        usersGrid.setOnItemClickListener(this);
        return view;
    }

    private User getCurrentUser() {
        return UserManager.getInstance(getActivity().getApplication()).getMainUser();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        //navigate to chat activity
        String peerId;
        try {
            peerId = ((UsersAdapter.ViewHolder) view.getTag()).userId;
            UiHelpers.enterChatRoom(getActivity(), peerId);
        } catch (ClassCastException e) {
            //noinspection ConstantConditions
            peerId = ((MembersAdapter.ViewHolder) view.getTag()).userId;
            UiHelpers.gotoProfileActivity(getActivity(), peerId);
        }
    }

    private class MembersAdapter extends BaseAdapter {

        private final RealmList<User> items;
        private final String groupId;

        MembersAdapter(RealmList<User> items, String groupId) {
            this.items = items;
            this.groupId = groupId;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = parent.getContext();
            if (convertView == null) {
                //noinspection ConstantConditions
                convertView = LayoutInflater.from(context).inflate(R.layout.user_item, parent, false);
                ViewHolder holder = new ViewHolder();
                holder.iv = ((ImageView) convertView.findViewById(R.id.iv_display_picture));
                holder.username = ((TextView) convertView.findViewById(R.id.tv_user_name));
                convertView.setTag(holder);
            }
            User user = ((User) getItem(position));
            String adminText = context.getResources().getString(R.string.admin);
            ViewHolder holder = (ViewHolder) convertView.getTag();
            holder.userId = user.get_id();
            if (UserManager.INSTANCE.isMainUser(user.get_id())) {
                holder.username.setText(context.getResources().getString(R.string.you));
            } else {
                holder.username.setText(user.getName());
            }

            if (UserManager.INSTANCE.isAdmin(groupId, user.get_id())) {
                holder.username.append(" - " + adminText);
            }
            Picasso.with(context)
                    .load(Config.DP_ENDPOINT + "/" + holder.userId)
                    .placeholder(R.drawable.avatar_empty)
                    .error(R.drawable.avatar_empty)
                    .into(holder.iv);
            return convertView;
        }

        public class ViewHolder {
            public String userId;
            private ImageView iv;
            private TextView username;
        }
    }
}
