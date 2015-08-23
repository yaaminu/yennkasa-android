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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.pair.adapter.UsersAdapter;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.Config;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.util.PicassoWrapper;
import com.pair.util.UiHelpers;
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
            ARG_GROUP_ID = Message.FIELD_ID;

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
        ListView usersList = ((ListView) view.findViewById(R.id.lv_users));
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
            User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
            if (group == null) {
                throw new IllegalArgumentException("no group with id:  " + groupId);
            }
            adapter = new MembersAdapter(group.getMembers(), groupId);
        } else {
            RealmResults<User> results = realm.where(User.class).notEqualTo(User.FIELD_ID, getCurrentUser().get_id()).findAllSorted(User.FIELD_NAME, true);
            results.sort(User.FIELD_TYPE, false, User.FIELD_NAME, true);
            adapter = new UsersAdapter(getActivity(), results);
        }
        usersList.setAdapter(adapter);
        usersList.setOnItemClickListener(this);
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
            peerId = ((User) parent.getAdapter().getItem(position)).get_id();
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
        private final Picasso PICASSO;

        MembersAdapter(RealmList<User> items, String groupId) {
            this.items = items;
            this.groupId = groupId;
            PICASSO = PicassoWrapper.with(getActivity());
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
            if (UserManager.getInstance().isMainUser(user.get_id())) {
                holder.username.setText(context.getResources().getString(R.string.you));
            } else {
                holder.username.setText(user.getName());
            }

            if (UserManager.getInstance().isAdmin(groupId, user.get_id())) {
                holder.username.append(" - " + adminText);
            }
            PICASSO
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
