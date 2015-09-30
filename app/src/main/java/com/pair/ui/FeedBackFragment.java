package com.pair.ui;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.pair.pairapp.R;

/**
 * A simple {@link Fragment} subclass.
 */
public class FeedBackFragment extends Fragment {


    public FeedBackFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //noinspection ConstantConditions
        return inflater.inflate(R.layout.fragment_feed_back, container, false);
    }


}
