package com.pair.ui;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.pair.pairapp.R;
import com.pair.util.TypeFaceUtil;
import com.pair.util.ViewUtils;
import com.rey.material.widget.Button;

/**
 * A simple {@link Fragment} subclass.
 */
public class NoticeFragment extends Fragment implements View.OnClickListener {

    private NoticeFragmentCallback callback;

    public NoticeFragment() {
        // Required empty public constructor
    }


    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        if (activity instanceof NoticeFragmentCallback) {
            callback = ((NoticeFragmentCallback) activity);
        } else {
            throw new ClassCastException(activity.getClass().getName() + " must implement interface" + NoticeFragmentCallback.class.getName());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_notice, container, false);
        TextView textView = ((TextView) view.findViewById(R.id.tv_notice));
        ViewUtils.setTypeface(textView, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);
        CharSequence noticeText = callback.getNoticeText();
        if (!TextUtils.isEmpty(noticeText)) {
            textView.setText(noticeText, TextView.BufferType.SPANNABLE);
        }
        final Button actionButton = ((Button) view.findViewById(R.id.action));
        ViewUtils.setTypeface(textView, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        noticeText = callback.getActionText();
        if (!TextUtils.isEmpty(noticeText)) {
            actionButton.setText(noticeText);
        }
        actionButton.setOnClickListener(this);
        return view;
    }

    @Override
    public void onClick(View v) {
        callback.onAction();
    }

    interface NoticeFragmentCallback {

        Spanned getNoticeText();

        CharSequence getActionText();

        void onAction();
    }
}
