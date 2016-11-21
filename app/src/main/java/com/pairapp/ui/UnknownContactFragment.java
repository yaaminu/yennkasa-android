package com.pairapp.ui;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.pairapp.R;
import com.pairapp.data.User;
import com.pairapp.util.ViewUtils;
import com.rey.material.util.ViewUtil;
import com.rey.material.widget.Button;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * @author aminu on 11/20/2016.
 */
public class UnknownContactFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.unknown_user_fragment, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (((UserProvider) getActivity()).hideNotice()) {
            ViewUtils.hideViews(getView());
        } else {
            ViewUtils.showViews(getView());
        }
    }

    public interface UserProvider {
        User currentUser();

        boolean isCurrentUserRegistered();

        boolean hideNotice();
    }
}
