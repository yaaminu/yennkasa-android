package com.idea.adapter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ListView;

import com.idea.ui.PairAppBaseActivity;
import com.idea.view.CheckBox;
import com.idea.data.User;
import com.idea.pairapp.R;

import java.util.Set;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 9/18/2015.
 */
public class MultiChoiceUsersAdapter extends UsersAdapter {
    private final Set<String> selectedItems;
    private final PairAppBaseActivity baseActivity;
    private int checkBoxResId = 0;


    @SuppressWarnings("unused")
    public MultiChoiceUsersAdapter(PairAppBaseActivity context, Realm realm, RealmResults<User> realmResults, Set<String> selectedItems) {
        this(context, realm, realmResults, selectedItems, R.id.cb_checked);
    }

    public MultiChoiceUsersAdapter(PairAppBaseActivity context, Realm realm, RealmResults<User> realmResults, Set<String> selectedItems, int checkBoxResId) {
        super(context, realm, realmResults, true);
        if (selectedItems == null || !selectedItems.isEmpty()) {
            throw new IllegalArgumentException("null || non-empty container");
        }
        this.selectedItems = selectedItems;
        this.baseActivity = context;
        this.checkBoxResId = checkBoxResId;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        final String userId = getItem(position).getUserId();
        final CheckBox checkBox = (CheckBox) view.findViewById(checkBoxResId);

        //clear this so that some old converted view cannot cause a problems as we check this
        //checkbox
        checkBox.setOnCheckedChangeListener(null);
        final boolean isSelected = selectedItems.contains(userId);
        ((ListView) parent).setItemChecked(position, isSelected);
        checkBox.setCheckedImmediately(isSelected);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    selectedItems.add(userId);
                } else {
                    selectedItems.remove(userId);
                }
                ((ListView) parent).setItemChecked(position, isChecked);
                baseActivity.supportInvalidateOptionsMenu();
            }
        });
        return view;
    }
}
