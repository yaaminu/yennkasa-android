package com.pairapp.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ListView;

import com.pairapp.R;
import com.pairapp.data.User;
import com.rey.material.widget.CheckBox;

import java.util.Set;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 9/18/2015.
 */
public class MultiChoiceUsersAdapter extends UsersAdapter {
    private final Set<String> selectedItems;
    private int checkBoxResId = 0;
    private Delegate delegate;


    @SuppressWarnings("unused")
    public MultiChoiceUsersAdapter(Delegate delegate, Realm realm, RealmResults<User> realmResults, Set<String> selectedItems) {
        this(delegate, realm, realmResults, selectedItems, R.id.cb_checked);
    }

    public MultiChoiceUsersAdapter(Delegate delegate, Realm realm, RealmResults<User> realmResults, Set<String> selectedItems, int checkBoxResId) {
        super(delegate.getContext(), realm, realmResults, true);
        if (selectedItems == null || !selectedItems.isEmpty()) {
            throw new IllegalArgumentException("null || non-empty container");
        }
        this.selectedItems = selectedItems;
        this.checkBoxResId = checkBoxResId;
        this.delegate = delegate;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        final View view = super.getView(position, convertView, parent);
        final User user = getItem(position);
        final String userId = user.getUserId();
        final CheckBox checkBox = (CheckBox) view.findViewById(checkBoxResId);

        //clear this so that some old converted view cannot cause a problems as we check this
        //checkbox
        checkBox.setOnCheckedChangeListener(null);
        final boolean isSelected = selectedItems.contains(userId);
        final ListView listView = (ListView) parent;
        listView.setItemChecked(position, isSelected);
        checkBox.setCheckedImmediately(isSelected);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked && selectedItems.add(userId)) {
                    listView.setItemChecked(position, true);
                    delegate.onItemSelected(((AdapterView) parent), view, position, -1, true);
                } else if (selectedItems.remove(userId)) {
                    listView.setItemChecked(position, false);
                    delegate.onItemSelected(((AdapterView) parent), view, position, -1, false);
                }
            }
        });
        return view;
    }

    public interface Delegate {
        void onItemSelected(AdapterView<?> parent, View view, int position, long id, boolean isSelected);

        Context getContext();
    }
}
