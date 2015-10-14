package com.idea.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collection;

/**
 * @author Null-Pointer on 5/27/2015.
 */
@SuppressWarnings("unused")
public class UserJsonAdapter implements BaseJsonAdapter<User> {
    @Override
    public JsonObject toJson(User user) {
        JsonObject obj = new JsonObject();
        obj.addProperty(User.FIELD_ID, user.getUserId());
        obj.addProperty(User.FIELD_NAME, user.getName());
        obj.addProperty(User.FIELD_PASSWORD, user.getPassword());
        obj.addProperty(User.FIELD_LAST_ACTIVITY, user.getLastActivity());
        obj.addProperty(User.FIELD_COUNTRY, user.getCountry());
        obj.addProperty(User.FIELD_HAS_CALL, user.getHasCall());
        return obj;
    }

    @Override
    public JsonArray toJson(Collection<User> users) {
        JsonArray array = new JsonArray();
        for (User user : users) {
            array.add(toJson(user));
        }
        return array;
    }
}
