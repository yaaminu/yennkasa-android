package com.pairapp.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import com.pairapp.R;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.ui.ImageLoader;
import com.pairapp.util.PhoneNumberNormaliser;
import com.pairapp.util.TypeFaceUtil;
import com.pairapp.util.ViewUtils;
import com.rey.material.widget.CheckBox;

import java.util.regex.Pattern;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmBaseAdapter;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import io.realm.Sort;

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
            ViewUtils.setTypeface(holder.userName, TypeFaceUtil.ROBOTO_BOLD_TTF);
            ViewUtils.setTypeface(holder.userPhone, TypeFaceUtil.ROBOTO_REGULAR_TTF);
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
            ImageLoader.load(context, user.getDP())
                    .error(User.isGroup(user) ? R.drawable.group_avatar : R.drawable.user_avartar)
                    .placeholder(User.isGroup(user) ? R.drawable.group_avatar : R.drawable.user_avartar)
                    .resize((int) context.getResources().getDimension(R.dimen.thumbnail_width), (int) context.getResources().getDimension(R.dimen.thumbnail_height))
                    .onlyScaleDown().into(new TargetOnclick(holder.iv, user.getUserId()));
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
                    RealmResults<User> filtered = doFilter(results.values.toString());
                    if (filtered != null) {
                        filterResults = filtered;
                        notifyDataSetChanged();
                    }
                }
            }

            private String normaliseConstraint(String constraintAsString) {
                if (TextUtils.isEmpty(constraintAsString)) {
                    return "";
                }
                if (alphabet.matcher(constraintAsString).find()) {
                    return constraintAsString;
                }
                PhoneNumberNormaliser.cleanNonDialableChars(constraintAsString);
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
                    //the next condition will never have to worry about nput like "00","011 as they will be sieved off!
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

    protected RealmResults<User> doFilter(String results) {
        final RealmQuery<User> originalQuery = getOriginalQuery();
        if (originalQuery == null) {
            return realm.where(User.class)
                    .beginGroup()
                    .contains(User.FIELD_NAME, results, Case.INSENSITIVE).or()
                    .contains(User.FIELD_ID, results)
                    .notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP)
                    .endGroup()
                    .notEqualTo(User.FIELD_ID, UserManager.getInstance()
                            .getCurrentUser()
                            .getUserId())
                    .findAllSorted(User.FIELD_NAME, Sort.ASCENDING, User.FIELD_TYPE, Sort.DESCENDING);
        } else {
            return originalQuery
                    .beginGroup()
                    .contains(User.FIELD_NAME, results, Case.INSENSITIVE).or()
                    .contains(User.FIELD_ID, results)
                    .endGroup()
                    .findAllSorted(User.FIELD_NAME, Sort.ASCENDING, User.FIELD_TYPE, Sort.DESCENDING);
        }
    }

    private static class ViewHolder {
        private ImageView iv;
        private TextView userName, userPhone;
        @SuppressWarnings("unused")
        private com.rey.material.widget.CheckBox checkBox;
    }

}
