package com.pair.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pair.data.Message;

import java.util.Collection;

/**
 * Created by Null-Pointer on 5/27/2015.
 */
public class MessageJsonAdapter implements BaseJsonAdapter<Message> {
    @Override
    public JsonObject toJson(Message message) {
        JsonObject obj = new JsonObject();

        obj.addProperty("from", message.getFrom());
        obj.addProperty("to", message.getTo());
        obj.addProperty("state", message.getState());
        obj.addProperty("id", message.getId());
        obj.addProperty("messageBody", message.getMessageBody());
        obj.addProperty("dateComposed", message.getDateComposed().getTime());
        return obj;
    }

    @Override
    public JsonArray toJson(Collection<Message> messages) {
        JsonArray array = new JsonArray();
        for (Message message : messages) {
            array.add(toJson(message));
        }
        return array;
    }

    public static final MessageJsonAdapter INSTANCE = new MessageJsonAdapter();
}
