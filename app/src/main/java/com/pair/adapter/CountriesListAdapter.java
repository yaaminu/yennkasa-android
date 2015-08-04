package com.pair.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.pair.data.Country;

import java.util.Locale;

import io.realm.RealmResults;

public class CountriesListAdapter extends ArrayAdapter<Country> {
	private RealmResults<Country> countries;
	public CountriesListAdapter(Context context,RealmResults<Country> countries) {
		super(context,android.R.layout.simple_list_item_1);
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
		return getView(position,convertView,parent);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) parent.getContext()
			.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
 
		convertView = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
//		TextView textView = (TextView) rowView.findViewById(R.id.txtViewCountryName);
//		ImageViewer imageView = (ImageViewer) rowView.findViewById(R.id.imgViewFlag);
		
		((TextView)convertView).setText(getItem(position).getName());
    	
    	//String pngName = g[1].trim().toLowerCase();
    	//imageView.setImageResource(context.getResources().getIdentifier("drawable/" + pngName, null, context.getPackageName()));
		return convertView;
	}

	private String GetCountryZipCode(String ssid){
        Locale loc = new Locale("", ssid);
        
        return loc.getDisplayCountry().trim();
    }
}