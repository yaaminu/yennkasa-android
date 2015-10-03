package com.idea.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.idea.data.Country;
import com.idea.pairapp.R;
import com.idea.util.TypeFaceUtil;
import com.idea.util.ViewUtils;
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
            holder = new ViewHolder();
            holder.name = ((TextView) convertView.findViewById(R.id.tv_country_name));
            holder.CCC = (TextView) convertView.findViewById(R.id.tv_country_ccc);
            ViewUtils.setTypeface(holder.name, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);
            ViewUtils.setTypeface(holder.CCC, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);
            convertView.setTag(R.id.tag,holder);
        }
        holder = ((ViewHolder) convertView.getTag(R.id.tag));

        Country country = getItem(position);
        holder.name.setText(country.getName());
        if (position == 0) {
            holder.CCC.setText("");
        } else {
            holder.CCC.setText("+" + country.getCcc());
        }
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