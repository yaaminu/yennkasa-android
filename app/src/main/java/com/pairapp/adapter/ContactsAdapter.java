package com.pairapp.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.DrawableRes;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.pairapp.R;
import com.pairapp.data.UserManager;
import com.pairapp.ui.ImageLoader;
import com.pairapp.util.PhoneNumberNormaliser;
import com.pairapp.util.TypeFaceUtil;
import com.pairapp.util.UiHelpers;
import com.pairapp.util.ViewUtils;
import com.rey.material.widget.Button;

import java.util.List;
import java.util.Locale;

import static com.pairapp.data.ContactsManager.Contact;


/**
 * @author Null-Pointer on 6/12/2015.
 */
public class ContactsAdapter extends BaseAdapter {
    private static final String TAG = ContactsAdapter.class.getSimpleName();
    private final String userIsoCountry;
    private final int[] layoutResource = {
            R.layout.registered_contact_item,
            R.layout.unregistered_contact_item,
    };
    private List<Contact> contacts;
    private boolean isAddOrRemoveFromGroup;
    private FragmentActivity context;
    private final Drawable[] bgColors = new Drawable[5];

    public ContactsAdapter(FragmentActivity context, List<Contact> contacts, boolean isAddOrRemoveFromGroup) {
        this.contacts = contacts;
        this.isAddOrRemoveFromGroup = isAddOrRemoveFromGroup;
        this.context = context;
        userIsoCountry = UserManager.getInstance().getUserCountryISO();
        bgColors[0] = getDrawable(context, R.drawable.pink_round_back_ground);
        bgColors[1] = getDrawable(context, R.drawable.blue_round_back_ground);
        bgColors[2] = getDrawable(context, R.drawable.red_round_back_ground);
        bgColors[3] = getDrawable(context, R.drawable.green_round_back_ground);
        bgColors[4] = getDrawable(context, R.drawable.orange_round_back_ground);
    }

    private Drawable getDrawable(Context context, @DrawableRes int res) {
        return context.getResources().getDrawable(res);
    }

    @Override
    public int getCount() {
        return contacts.size();
    }

    @Override
    public Contact getItem(int position) {
        return contacts.get(position);
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
    public int getItemViewType(int position) {
        return getItem(position).isRegisteredUser ? 0 : 1;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final Contact contact = getItem(position);

        final ViewHolder holder;
        int layoutRes = layoutResource[getItemViewType(position)];
        if (convertView == null) {
            //noinspection ConstantConditions
            convertView = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
            holder = new ViewHolder();
            if (isAddOrRemoveFromGroup) {
                holder.userName = ((TextView) convertView);
            } else {
                holder.userName = ((TextView) convertView.findViewById(R.id.tv_user_name));
            }
            holder.inviteButton = (Button) convertView.findViewById(R.id.bt_invite);
            holder.userDp = ((ImageView) convertView.findViewById(R.id.iv_display_picture));
            holder.userPhone = (TextView) convertView.findViewById(R.id.tv_user_phone_group_admin);
            holder.initials = (TextView) convertView.findViewById(R.id.tv_initials);

            ViewUtils.setTypeface(holder.inviteButton, TypeFaceUtil.ROBOTO_REGULAR_TTF);
            ViewUtils.setTypeface(holder.userPhone, TypeFaceUtil.ROBOTO_REGULAR_TTF);
            ViewUtils.setTypeface(holder.initials, TypeFaceUtil.ROBOTO_LIGHT_TTF);
            ViewUtils.setTypeface(holder.userName, TypeFaceUtil.ROBOTO_BOLD_TTF);
            convertView.setTag(holder);
        } else {
            holder = ((ViewHolder) convertView.getTag());
        }

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClick(v, contact);
            }
        };
        holder.userName.setText(contact.name);
        if (isAddOrRemoveFromGroup) {
            return convertView;
        }
        if (contact.isRegisteredUser) {
            TargetOnclick targetOnclick = new TargetOnclick(holder.userDp, contact.numberInIEE_Format);
            ImageLoader.load(context, contact.DP)
                    .error(R.drawable.user_avartar)
                    .placeholder(R.drawable.user_avartar)
                    .resize((int) context.getResources().getDimension(R.dimen.thumbnail_width), (int) context.getResources().getDimension(R.dimen.thumbnail_height))
                    .onlyScaleDown()
                    .centerInside()
                    .into(targetOnclick);
            holder.userName.setClickable(true);
            holder.userDp.setClickable(true);
            holder.userDp.setOnClickListener(targetOnclick);
            holder.userPhone.setText(PhoneNumberNormaliser.toLocalFormat(getItem(position).numberInIEE_Format, userIsoCountry));
            holder.userPhone.setClickable(true);
            holder.userPhone.setOnClickListener(listener);
        } else {
            holder.userPhone.setText(PhoneNumberNormaliser.toLocalFormat(contact.numberInIEE_Format, userIsoCountry));
            holder.userPhone.setOnClickListener(listener);
            holder.userName.setOnClickListener(listener);
            holder.inviteButton.setOnClickListener(listener);
            if (contact.name.length() > 1) {
                String[] parts = contact.name.trim().split("[\\s[^A-Za-z]]+");
                if (parts.length > 1) {
                    StringBuilder builder = new StringBuilder(2);
                    int chars = 0;
                    for (int i = 0; i < parts.length && chars < 2; i++) {
                        if (parts[i].isEmpty()) {
                            continue;
                        }
                        builder.append(parts[i].charAt(0));
                        chars++;
                    }
                    if (chars >= 2) {
                        holder.initials.setText(builder.toString().toUpperCase(Locale.getDefault()));
                    } else {
                        holder.initials.setText(contact.name.substring(0, 1).toUpperCase(Locale.getDefault()));
                    }
                } else {
                    holder.initials.setText(contact.name.substring(0, 2).toUpperCase(Locale.getDefault()));
                }
            } else {
                holder.initials.setText(" " + contact.name.toUpperCase(Locale.getDefault()));
            }
            int i = position % bgColors.length;
            //noinspection deprecation
            holder.initials.setBackgroundDrawable(bgColors[(i >= bgColors.length) ? i - bgColors.length : i]);
        }

        return convertView;
    }

    private void callContact(View v, Contact contact) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + PhoneNumberNormaliser.toLocalFormat("+" + contact.numberInIEE_Format, userIsoCountry)));
        v.getContext().startActivity(intent);
    }

    public void refill(List<Contact> contacts) {
        this.contacts = contacts;
        notifyDataSetChanged();
    }

    private void handleClick(View view, Contact contact) {
        int id = view.getId();

        if (contact.isRegisteredUser) {
            if (id == R.id.iv_display_picture) {
                UiHelpers.gotoProfileActivity(view.getContext(), contact.numberInIEE_Format);
            } else if (id == R.id.tv_user_phone_group_admin) {
                callContact(view, contact);
            }
        } else {
            if (id == R.id.bt_invite) {
                invite(view.getContext(), contact);
            } else if (id == R.id.tv_user_phone_group_admin) {
                callContact(view, contact);
            }
        }
    }

    private static void invite(final Context context, final Contact contact) {
        UiHelpers.doInvite(context, contact);
    }

    //    private class InviteContact implements View.OnClickListener {
//        Contact contact;
//
//        public InviteContact(Contact contact) {
//            this.contact = contact;
//        }
//
//        @Override
//        public void onClick(View v) {
//            Uri uri = Uri.parse("sms:"+contact.phoneNumber);
//            Intent intent = new Intent(Intent.ACTION_SENDTO);
//            intent.setData(uri);
//            intent.putExtra(Intent.EXTRA_TEXT, message);
//            v.getContext().startActivity(intent);
//        }
//
//    }

    private class ViewHolder {
        private TextView userName, initials;
        private Button inviteButton;
        private ImageView userDp;
        private TextView userPhone;
    }

}
