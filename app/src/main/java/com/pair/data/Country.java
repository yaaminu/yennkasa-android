package com.pair.data;

import io.realm.RealmObject;
import io.realm.annotations.RealmClass;

/**
 * @author Null-Pointer on 7/29/2015.
 */
@RealmClass
public class Country extends RealmObject {
    private String name;
    private String ccc;
    private String iso2letterCode;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCcc() {
        return ccc;
    }

    public void setCcc(String ccc) {
        this.ccc = ccc;
    }

    public String getIso2letterCode() {
        return iso2letterCode;
    }

    public void setIso2letterCode(String iso2letterCode) {
        this.iso2letterCode = iso2letterCode;
    }
}
