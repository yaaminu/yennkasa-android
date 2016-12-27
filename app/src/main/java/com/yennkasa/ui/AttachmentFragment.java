package com.yennkasa.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;

import com.yennkasa.R;

// TODO: 9/30/2015 implement this
public class AttachmentFragment extends Fragment {


    public AttachmentFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_attachment, container, false);
        GridView gridView = ((GridView) view.findViewById(R.id.gv_attach_options));

        return view;
    }


    private class AttachOptionsAdapter extends BaseAdapter {
      private final int [] icons = {
        R.drawable.ic_action_picture,
              R.drawable.ic_action_video
      };
        @Override
        public int getCount() {
            return 0;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.grid_item_attachment, parent, false);
            }

            return convertView;
        }
    }

}
