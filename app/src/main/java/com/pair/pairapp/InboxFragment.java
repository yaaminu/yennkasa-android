package com.pair.pairapp;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

/**
 * Created by Null-Pointer on 5/29/2015.
 */
public class InboxFragment extends ListFragment {

    private static final String [] days = {
            "Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"
    };
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inbox, container, false);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),android.R.layout.simple_list_item_1,days);
        setListAdapter(adapter);
        return view;
    }
}
