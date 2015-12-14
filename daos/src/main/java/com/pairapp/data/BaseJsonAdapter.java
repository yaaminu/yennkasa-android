package com.pairapp.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collection;

/**
 * Created by Null-Pointer on 5/26/2015.
 */
public interface BaseJsonAdapter<T> {
    JsonObject toJson(T t);

    JsonArray toJson(Collection<T> t);

}
