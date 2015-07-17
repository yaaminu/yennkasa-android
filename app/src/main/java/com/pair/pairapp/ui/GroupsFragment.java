package com.pair.pairapp.ui;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.ListFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.pair.adapter.GroupsAdapter;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

import io.realm.Realm;
import io.realm.RealmResults;


/**
 * A simple {@link ListFragment} subclass.
 */
public class GroupsFragment extends ListFragment {

    Realm realm;

    public GroupsFragment() {
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
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_groups, container, false);
        realm = Realm.getInstance(getActivity());
        UserManager.INSTANCE.refreshGroups();
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        RealmResults<User> groups = realm.where(User.class).equalTo(Message.FIELD_TYPE, User.TYPE_GROUP).findAllSorted(User.FIELD_NAME);
        BaseAdapter adapter = new GroupsAdapter(getActivity(), groups);
        setListAdapter(adapter);
    }

    @Override
    public void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        String groupId = ((GroupsAdapter.ViewHolder) v.getTag()).groupId; //very safe
        UiHelpers.enterChatRoom(getActivity(), groupId);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, R.id.action_createGroup, 0, "Create Group");
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        long id = item.getItemId();
        if (id == R.id.action_createGroup) {
            //show a dialog
            Dialogue dialogue = new Dialogue();
            dialogue.show(getFragmentManager(), null);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @SuppressWarnings("ConstantConditions")
    public static class Dialogue extends DialogFragment {
        ProgressDialog pDialog;

        public Dialogue() {
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            pDialog = new ProgressDialog(getActivity());
            pDialog.setMessage(getResources().getString(R.string.st_please_wait));
            pDialog.setCancelable(false);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            View view = LayoutInflater.from(getActivity()).inflate(R.layout.create_group, null);
            final EditText et = ((EditText) view.findViewById(R.id.et_group_name));
            builder.setTitle("Create Group")
                    .setView(view)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String groupName = UiHelpers.getFieldContent(et);
                            if (!TextUtils.isEmpty(groupName)) {
                                //TODO use regex to validate name
                                pDialog.show();
                                UserManager.INSTANCE.createGroup(groupName, callBack);
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.no, null);
            return builder.create();
        }

        private UserManager.CallBack callBack = new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                pDialog.dismiss();
                if (e != null) {
                    UiHelpers.showErrorDialog(getActivity(), e.getMessage());
                } else {
                    Toast.makeText(Config.getApplicationContext(), "Success", Toast.LENGTH_LONG).show();
                }
            }
        };
    }

}
