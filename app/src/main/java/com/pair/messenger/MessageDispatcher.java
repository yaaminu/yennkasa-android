package com.pair.messenger;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pair.adapter.BaseJsonAdapter;
import com.pair.data.Message;
import com.pair.net.Dispatcher;
import com.pair.net.HttpResponse;
import com.pair.net.api.MessageApi;
import com.pair.util.Config;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;
import retrofit.client.Response;

/**
 * Created by Null-Pointer on 5/26/2015.
 */
public class MessageDispatcher implements Dispatcher<Message> {

    private static final String TAG = MessageDispatcher.class.getSimpleName();
    private static volatile MessageDispatcher INSTANCE;
    private final int MAX_RETRY_TIMES;
    private final MessageApi MESSAGE_API;
    private final Object adapterLock = new Object();
    private final Object dispatcherMonitorLock = new Object();
    private BaseJsonAdapter<Message> jsonAdapter;
    private Sender sender;
    private DispatcherMonitor dispatcherMonitor;

    private MessageDispatcher(BaseJsonAdapter<Message> jsonAdapter, DispatcherMonitor errorHandler, int retryTimes) {
        RestAdapter adapter = new RestAdapter.Builder()
                .setEndpoint(Config.PAIRAPP_ENDPOINT)
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setLog(new AndroidLog(TAG))
                .build();

        this.MESSAGE_API = adapter.create(MessageApi.class);
        this.sender = new Sender();
        this.dispatcherMonitor = errorHandler;
        this.MAX_RETRY_TIMES = (retryTimes < 0) ? 0 : retryTimes;
        this.jsonAdapter = jsonAdapter;
    }

    public static MessageDispatcher getInstance(BaseJsonAdapter<Message> adapter, DispatcherMonitor dispatcherMonitor, int retryTimes) {
        MessageDispatcher localInstance = INSTANCE;
        if (localInstance == null) {
            synchronized (MessageDispatcher.class) {
                localInstance = INSTANCE;
                if (localInstance == null) {
                    INSTANCE = localInstance = new MessageDispatcher(adapter, dispatcherMonitor, retryTimes);
                }
            }
        }
        return localInstance;
    }

    @Override
    public void dispatch(Message message) {
        JsonObject object = jsonAdapter.toJson(message);
        doDispatch(object, message.getId());
    }

    @Override
    public void dispatch(Collection<Message> messages) {
        //FIXME spawn a background daemon to send messages in the collection instead of looping over it
        for (Message message : messages) {
            dispatch(message);
        }
    }

    public void setJsonAdapter(BaseJsonAdapter<Message> adapter) {
        synchronized (adapterLock) {
            this.jsonAdapter = adapter;
        }
    }

    private void doDispatch(JsonObject data, long id) {
        SenderJob job = new SenderJob(id, 1, data);
        sender.enqueue(job);
    }

    @Override
    public void setDispatcherMonitor(DispatcherMonitor newErrorHandler) {
        synchronized (dispatcherMonitorLock) {
            this.dispatcherMonitor = newErrorHandler;
        }
    }

    @Override
    public boolean cancelDispatchMayPossiblyFail(Message message) {
        return false;
    }


    private static class SenderJob {
        int retryTimes;
        JsonObject data;
        long id;

        SenderJob(long id, int retryTimes, JsonObject data) {
            this.retryTimes = retryTimes;
            this.data = data;
            this.id = id;
        }
    }

    private class Sender {

        void enqueue(SenderJob job) {
            doSend(job);
        }

        void doSend(final SenderJob job) {
            Log.d(TAG, job.data.toString());
            //actual send implementation
            MESSAGE_API.sendMessage(job.data, new Callback<HttpResponse>() {
                @Override
                public void success(HttpResponse httpResponse, Response response) {
                    dispatcherMonitor.onSendSucceeded(job.id);
                }

                @Override
                public void failure(RetrofitError retrofitError) {
                    //retry if network available
                    if (retrofitError.getKind().equals(RetrofitError.Kind.UNEXPECTED)) {
                        if (job.retryTimes < MAX_RETRY_TIMES) {
                            job.retryTimes++;
                            tryAgain(job);
                        } else {
                            dispatcherMonitor.onSendFailed("an unknown error occurred", job.id);
                        }
                    } else if (retrofitError.getKind().equals(RetrofitError.Kind.HTTP)) {
                        int statusCode = retrofitError.getResponse().getStatus();
                        if ((statusCode == HttpStatus.SC_NOT_FOUND)
                                || (statusCode == HttpStatus.SC_BAD_REQUEST)
                                || (statusCode == HttpStatus.SC_UNAUTHORIZED)
                                ) {
                            //bubble up error
                            String reason = "an error occured"; //= getHttpErrorResponse(retrofitError, "an error occurred");
                            dispatcherMonitor.onSendFailed(reason, job.id);
                        } else { //crash early
                            throw new RuntimeException("An unknown internal error occurred");
                        }
                    } else if (retrofitError.getKind().equals(RetrofitError.Kind.CONVERSION)) { //crash early
                        throw new RuntimeException("poorly encoded json data");
                    } else if (retrofitError.getKind().equals(retrofitError.getKind().equals(RetrofitError.Kind.NETWORK))) {
                        //bubble up error and empty send queue let callers re-dispatch messages again;
                        dispatcherMonitor.onSendFailed("Error in network connection", job.id);
                    }

                }
            });
        }

        private String getHttpErrorResponse(RetrofitError error, String defaultMessage) {
            String reason = (defaultMessage != null) ? defaultMessage : "an unknow error occured";
            try {
                InputStream in = error.getResponse().getBody().in();
                String response = IOUtils.toString(in);
                Gson gson = new GsonBuilder().create();
                return gson.fromJson(response, HttpResponse.class).getMessage();
            } catch (IOException e) {
                Log.e(TAG, "error while retrieving reason for send error");
            } catch (IllegalStateException e) {
                Log.e(TAG, e.getMessage(), e.getCause());
            }
            return reason;
        }

        private void tryAgain(SenderJob job) {
            doSend(job);
        }

//        private void sendNextIfavailable() {
//            //TODO make sure this piece of code is synchronized
//            if (sendQueue.peek() != null) {
//                doSend(sendQueue.poll()); //recursive call
//            } else {
//                busy.set(false);
//            }
//        }
    }
}
