package com.pair.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.pair.data.Country;
import com.pair.pairapp.R;
import com.rey.material.widget.TextView;

import java.util.Locale;

import io.realm.RealmResults;

public class CountriesListAdapter extends ArrayAdapter<Country> {
    private RealmResults<Country> countries;

    public CountriesListAdapter(Context context, RealmResults<Country> countries) {
        super(context, android.R.layout.simple_list_item_1);
        this.countries = countries;
    }

    @Override
    public int getCount() {
        return countries.size();
    }

    @Override
    public Country getItem(int position) {
        return countries.get(position);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getView(position, convertView, parent);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Context context = getContext();
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        ViewHolder holder;
        // FIXME: 8/14/2015 ensure we use the view holder pattern;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.country_spinner_item, parent, false);
        }
        holder = new ViewHolder();
        holder.name = ((TextView) convertView.findViewById(R.id.tv_country_name));
        holder.CCC = (TextView) convertView.findViewById(R.id.tv_country_ccc);
        convertView.setTag(holder);

        Country country = getItem(position);
        Locale locale = new Locale("", country.getIso2letterCode());
        holder.name.setText(locale.getDisplayCountry().trim());
        holder.CCC.setText("+" + country.getCcc());
        return convertView;
    }

    private String GetCountryZipCode(String ssid) {
        Locale loc = new Locale("", ssid);

        return loc.getDisplayCountry().trim();
    }

    private class ViewHolder {
        TextView name, CCC;
    }
}