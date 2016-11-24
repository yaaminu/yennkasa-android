package com.pairapp.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pairapp.R;
import com.pairapp.data.Message;
import com.pairapp.data.User;
import com.pairapp.ui.ImageLoader;
import com.pairapp.util.ViewUtils;

import java.util.Date;

import butterknife.Bind;
import io.realm.Realm;

import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.getRelativeTimeSpanString;

/**
 * @author aminu on 7/20/2016.
 */
public class CallLogAdapter extends PairappBaseAdapter<Message> {

    private final LayoutInflater layoutInflater;

    public CallLogAdapter(CallLogDelegate delegate) {
        super(delegate);
        layoutInflater = LayoutInflater.from(delegate.getContext());

    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void doBindHolder(Holder holder, int position) {
        VHolder holder1 = ((VHolder) holder);
        Message message = getItem(position);
        Context context = ((VHolder) holder).itemView.getContext();
        User user = ((CallLogDelegate) delegate).getUser(Message.isOutGoing(delegate.userRealm(), message) ? message.getTo() : message.getFrom());
        holder1.peerName.setText(user.getName());

        long now = new Date().getTime();
        long then = message.getDateComposed().getTime();
        CharSequence formattedDate;

        long ONE_MINUTE = 60000;
        formattedDate = ((now - then) < ONE_MINUTE) ?
                context.getString(R.string.now) : getRelativeTimeSpanString(then, now, MINUTE_IN_MILLIS);

        holder1.callDate.setText(formattedDate);
        holder1.callSummary.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        holder1.callSummary.setCompoundDrawablesWithIntrinsicBounds(getDrawable(delegate.userRealm(), message), 0, 0, 0);
        holder1.callSummary.setText("  " + Message.getCallSummary(context, delegate.userRealm(), message));

        ImageLoader.load(context, user.getDP())
                .error(R.drawable.user_avartar)
                .placeholder(R.drawable.user_avartar)
                .resize((int) context.getResources().getDimension(R.dimen.thumbnail_width),
                        (int) context.getResources().getDimension(R.dimen.thumbnail_height))
                .onlyScaleDown().into(((VHolder) holder).userAvartar);

        if (position == getItemCount() - 1) {
            ViewUtils.hideViews(((VHolder) holder).divider);
        } else {
            ViewUtils.showViews(((VHolder) holder).divider);
        }
    }

    static int getDrawable(Realm userRealm, Message message) {
        if (Message.isOutGoing(userRealm, message)) {
            return R.drawable.ic_call_made_black_24dp;
        } else {
            //noinspection ConstantConditions
            if (message.getCallBody().getCallDuration() <= 0) {
                return R.drawable.ic_call_missed_black_24dp;
            } else {
                return R.drawable.ic_call_received_black_24dp;
            }
        }
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new VHolder(layoutInflater.inflate(R.layout.call_log_item, parent, false));
    }

    public interface CallLogDelegate extends PairappBaseAdapter.Delegate<Message> {
        @NonNull
        Context getContext();

        @NonNull
        User getUser(String peerId);
    }
}


class VHolder extends PairappBaseAdapter.Holder {

    @Bind(R.id.tv_sender)
    TextView peerName;

    @Bind(R.id.iv_user_avatar)
    ImageView userAvartar;

    @Bind(R.id.tv_call_summary)
    TextView callSummary;

    @Bind(R.id.tv_call_date)
    TextView callDate;

    @Bind(R.id.divider)
    View divider;

    public VHolder(View v) {
        super(v);
    }
}

