package com.pair.ui;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.pair.pairapp.R;
import com.pair.util.TypeFaceUtil;
import com.pair.util.ViewUtils;

/**
 * A simple {@link Fragment} subclass.
 */
public class AboutFragment extends Fragment {


    public AboutFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about, container, false);

        Button legalNotice = (Button) view.findViewById(R.id.bt_legal_notice);
        ViewUtils.setTypeface(legalNotice, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        TextView appname = (TextView) view.findViewById(R.id.tv_app_name),
        appVersion = ((TextView) view.findViewById(R.id.tv_app_version));

        ViewUtils.setTypeface(appname, TypeFaceUtil.two_d_font);
        ViewUtils.setTypeface(appVersion, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        TextView copyRight = ((TextView) view.findViewById(R.id.copy_right));
        ViewUtils.setTypeface(copyRight, TypeFaceUtil.two_d_font);

        return view;
    }


}
