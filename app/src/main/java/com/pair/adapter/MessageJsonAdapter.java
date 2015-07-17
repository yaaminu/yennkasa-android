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

        obj.addProperty(Message.FIELD_FROM, message.getFrom());
        obj.addProperty(Message.FIELD_TO, message.getTo());
        obj.addProperty(Message.FIELD_STATE, message.getState());
        obj.addProperty(Message.FIELD_ID, message.getId());
        obj.addProperty(Message.FIELD_MESSAGE_BODY, message.getMessageBody());
        obj.addProperty(Message.FIELD_DATE_COMPOSED, message.getDateComposed().getTime());
        obj.addProperty(Message.FIELD_TYPE, message.getType());
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
