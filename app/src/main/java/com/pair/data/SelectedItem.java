package com.pair.data;

import android.os.Parcel;
import android.os.Parcelable;

import com.pair.util.Config;

public class SelectedItem implements Parcelable {
    public String name, itemDescription,
            dpURl, id;

    public SelectedItem() {
    }

    private SelectedItem(Parcel in) {
        this.name = in.readString();
        this.itemDescription = in.readString();
        this.dpURl = in.readString();
        this.id = in.readString();
    }

    public SelectedItem(String id, String name) {
        this(id, name, Config.getApplicationContext().getString(com.pair.pairapp.R.string.st_unknown));
    }

    public SelectedItem(String id, String name, String itemDescription) {
        this(id, name, itemDescription, "");
    }

    public SelectedItem(String id, String name, String itemDescription, String dpURl) {
        this.id = id;
        this.itemDescription = itemDescription;
        this.name = name;
        this.dpURl = dpURl;
    }

    @Override
    public String toString() {
        return name != null ? name : Config.getApplicationContext().getString(com.pair.pairapp.R.string.unknown);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SelectedItem that = (SelectedItem) o;

        return !(id != null ? !id.equals(that.id) : that.id != null);

    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.name);
        dest.writeString(this.itemDescription);
        dest.writeString(this.dpURl);
        dest.writeString(this.id);
    }

    public static final Creator<SelectedItem> CREATOR = new Creator<SelectedItem>() {
        public SelectedItem createFromParcel(Parcel source) {
            return new SelectedItem(source);
        }

        public SelectedItem[] newArray(int size) {
            return new SelectedItem[size];
        }
    };

}
