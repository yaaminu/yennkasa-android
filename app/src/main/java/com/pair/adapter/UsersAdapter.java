package com.pair.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.ui.DPLoader;
import com.pair.util.PhoneNumberNormaliser;
import com.pair.util.TypeFaceUtil;
import com.pair.util.ViewUtils;
import com.pair.view.CheckBox;

import java.util.regex.Pattern;

import io.realm.Realm;
import io.realm.RealmBaseAdapter;
import io.realm.RealmQuery;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class UsersAdapter extends RealmBaseAdapter<User> implements Filterable {
    Pattern alphabet = Pattern.compile("\\p{Alpha}");
    private RealmResults<User> filterResults;
    private int layoutResource;
    private boolean multiSelect;
    private Realm realm;

    public UsersAdapter(Context context, Realm realm, RealmResults<User> realmResults) {
        this(context, realm, realmResults, false);
    }

    public UsersAdapter(Context context, Realm realm, RealmResults<User> realmResults, boolean multiChoice) {
        super(context, realmResults, true);
        filterResults = realmResults;
        this.layoutResource = multiChoice ? R.layout.multi_select_user_item : R.layout.user_item;
        this.multiSelect = multiChoice;
        this.realm = realm;
    }

    @Override
    public int getCount() {
        return filterResults.size();
    }

    @Override
    public User getItem(int i) {
        return filterResults.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Context context = parent.getContext();
        if (convertView == null) {
            //noinspection ConstantConditions
            convertView = LayoutInflater.from(context).inflate(layoutResource, parent, false);
            ViewHolder holder = new ViewHolder();
            holder.iv = ((ImageView) convertView.findViewById(R.id.iv_display_picture));
            holder.userName = ((TextView) convertView.findViewById(R.id.tv_user_name));
            holder.userPhone = (TextView) convertView.findViewById(R.id.tv_user_phone_group_admin);
            holder.checkBox = (CheckBox) convertView.findViewById(R.id.cb_checked);
            ViewUtils.setTypeface(holder.userName, TypeFaceUtil.DROID_SERIF_BOLD_TTF);
            ViewUtils.setTypeface(holder.userPhone, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);
            convertView.setTag(holder);
        }
        User user = getItem(position);
        ViewHolder holder = (ViewHolder) convertView.getTag();
        holder.userName.setText(user.getName());

        if (UserManager.getInstance().isGroup(user.getUserId())) {
            holder.userPhone.setText(R.string.group);
        } else {
            holder.userPhone.setText(PhoneNumberNormaliser.toLocalFormat("+" + user.getUserId(), UserManager.getInstance().getUserCountryISO()));
        }
        if (!multiSelect) {
            DPLoader.load(context, user.getDP())
                    .error(User.isGroup(user) ? R.drawable.group_avatar : R.drawable.user_avartar)
                    .placeholder(User.isGroup(user) ? R.drawable.group_avatar : R.drawable.user_avartar)
                    .resize(150, 150)
                    .into(holder.iv);
        }
        return convertView;
    }

    protected RealmQuery<User> getOriginalQuery() {
        return null;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {

                String normalisedConstraint = normaliseConstraint(constraint.toString().trim());

                FilterResults results = new FilterResults();
                results.count = 0;
                results.values = normalisedConstraint;
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results.values != null) {
                    final RealmQuery<User> originalQuery = getOriginalQuery();
                    if (originalQuery == null) {
                        filterResults = realm.where(User.class)
                                .beginGroup()
                                .contains(User.FIELD_NAME, results.values.toString(), false).or()
                                .beginGroup()
                                .contains(User.FIELD_ID, results.values.toString())
                                .notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP)
                                .endGroup()
                                .endGroup()
                                .notEqualTo(User.FIELD_ID, UserManager.getInstance()
                                        .getCurrentUser()
                                        .getUserId())
                                .findAllSorted(User.FIELD_NAME, false);
                    } else {
                        filterResults = originalQuery
                                .beginGroup()
                                .contains(User.FIELD_NAME, results.values.toString(), false).or()
                                .beginGroup()
                                .contains(User.FIELD_ID, results.values.toString())
                                .notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP)
                                .endGroup()
                                .endGroup().findAllSorted(User.FIELD_NAME);
                    }
                    notifyDataSetChanged();
                }
            }

            private String normaliseConstraint(String constraintAsString) {
                if (TextUtils.isEmpty(constraintAsString)) {
                    return "";
                }
                if (alphabet.matcher(constraintAsString).find()) {
                    return constraintAsString;
                }
                if (constraintAsString.startsWith("+")) { //then its not in in the IEE format. eg +233XXXXXXXXX (for Ghanaian number)
                    if (constraintAsString.length() > 1) //avoid indexOutOfBoundException
                        constraintAsString = constraintAsString.substring(1);
                    else {
                        return null;
                    }
                } else if (constraintAsString.startsWith("00")) {
                    if (constraintAsString.length() > 2) //avoid indexOutOfBoundException
                        constraintAsString = constraintAsString.substring(2);
                    else {
                        return null;
                    }
                } else if (constraintAsString.startsWith("011")) {
                    if (constraintAsString.length() > 3) //avoid indexOutOfBoundException
                        constraintAsString = constraintAsString.substring(3);
                    else {
                        return null;
                    }
                    //the next condition will never have to worry about input like "00","011 as they will be sieved off!
                } else { //number in local format take of the trunk digit
                    if (constraintAsString.length() > 1) //avoid indexOutOfBoundException
                        constraintAsString = constraintAsString.substring(1);
                    else {
                        return null;
                    }
                }
                return constraintAsString;
            }
        };
    }

    private static class ViewHolder {
        private ImageView iv;
        private TextView userName, userPhone;
        private CheckBox checkBox;
    }

}
