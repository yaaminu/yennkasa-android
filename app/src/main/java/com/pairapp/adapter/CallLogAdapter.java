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
import java.util.Locale;

import butterknife.Bind;

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
        User user = ((CallLogDelegate) delegate).getUser(Message.isOutGoing(message) ? message.getTo() : message.getFrom());
        holder1.peerName.setText(user.getName());

        long now = new Date().getTime();
        long then = message.getDateComposed().getTime();
        CharSequence formattedDate;

        long ONE_MINUTE = 60000;
        formattedDate = ((now - then) < ONE_MINUTE) ?
                context.getString(R.string.now) : getRelativeTimeSpanString(then, now, MINUTE_IN_MILLIS);

        holder1.callDate.setText(formattedDate);

        int summary;
        //noinspection ConstantConditions
        int callDuration = message.getCallBody().getCallDuration();
        if (Message.isOutGoing(message)) {
            summary = R.string.dialed_call;
        } else {
            summary = callDuration <= 0 ? R.string.missed_call : R.string.recieved_call;
        }
        holder1.callSummary.setText(summary);
        holder1.callSummary.append(callDuration > 0 ? " " + formatTimespan(callDuration) + "  " : "");

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

    static String formatTimespan(long timespan) {
        long totalSeconds = timespan / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
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

