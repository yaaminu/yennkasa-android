package com.yennkasa.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.yennkasa.R;
import com.yennkasa.data.Country;


import butterknife.Bind;

public class CountriesListAdapter extends YennkasaBaseAdapter<Country> {

    public CountriesListAdapter(Delegate<Country> delegate) {
        super(delegate);
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.country_spinner_item, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void doBindHolder(Holder holder, int position) {
        Country country = getItem(position);
        ((ViewHolder) holder).CCC.setText("+" + country.getCcc());
        ((ViewHolder) holder).name.setText(country.getName());
    }

    @SuppressWarnings("WeakerAccess")
    static class ViewHolder extends Holder {
        @Bind(R.id.tv_country_ccc)
        TextView CCC;
        @Bind(R.id.tv_country_name)
        TextView name;

        public ViewHolder(View v) {
            super(v);
        }
    }
}
