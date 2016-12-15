package com.pairapp.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.pairapp.R;
import com.pairapp.data.Country;
import com.rey.material.widget.TextView;

import io.realm.RealmResults;

public class CountriesListAdapter extends ArrayAdapter<Country> {
    private RealmResults<Country> countries;

    public CountriesListAdapter(Context context, RealmResults<Country> countries) {
        super(context, android.R.layout.simple_list_item_1);
        this.countries = countries;
    }

    @Override
    public int getCount() {
        return countries.size() + 1;
    }

    @Override
    public Country getItem(int position) {
        if (position == 0) {
            Country selectCountry = new Country();
            selectCountry.setName(getContext().getString(R.string.select_country));
            selectCountry.setCcc("");
            selectCountry.setCcc("");
            return selectCountry;
        }
        return countries.get(position - 1);
    }

    @Override
    public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
        return getView(position, convertView, parent);
    }

    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        final Context context = getContext();
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        ViewHolder holder;
        // FIXME: 8/14/2015 ensure we use the view holder pattern;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.country_spinner_item, parent, false);
            holder = new ViewHolder();
            holder.name = ((TextView) convertView.findViewById(R.id.tv_country_name));
            holder.CCC = (TextView) convertView.findViewById(R.id.tv_country_ccc);
            convertView.setTag(holder);
        }
        holder = ((ViewHolder) convertView.getTag());

        Country country = getItem(position);
        holder.name.setText(country.getName());
        if (position == 0) {
            holder.CCC.setText("");
        } else {
            holder.CCC.setText("+" + country.getCcc());
        }
        return convertView;
    }

    private class ViewHolder {
        TextView CCC;
    }
}
