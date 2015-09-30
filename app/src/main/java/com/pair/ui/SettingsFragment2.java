package com.pair.ui;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.pair.pairapp.R;
import com.pair.util.UiHelpers;

/**
 * A simple {@link Fragment} subclass.
 */
public class SettingsFragment2 extends ListFragment {


    public SettingsFragment2() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_settings_fragment2, container, false);
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ArrayAdapter adapter = ArrayAdapter.createFromResource(getContext(), R.array.setting_list_items, android.R.layout.simple_list_item_1);
        setListAdapter(adapter);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        switch (position) {
            case 0:
                UiHelpers.gotoProfileActivity(getContext());
                break;
            case 1:
                UiHelpers.gotoSettingsActivity(getContext(),1);
                break;
            case 2:
                UiHelpers.gotoSettingsActivity(getContext(),2);
                break;
            case 3:
                UiHelpers.gotoSettingsActivity(getContext(),3);
                break;
            default:
                throw new AssertionError();
        }
    }
}
