package com.yennkasa.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;

/**
 * @author by Null-Pointer on 5/26/2015.
 */
public interface BaseJsonAdapter<T> {
    JSONObject toJson(T t);

    JSONArray toJson(Collection<T> t);

}
