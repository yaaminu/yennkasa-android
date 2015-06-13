package com.pair.messenger;

import android.app.AlarmManager;
import android.os.Handler;
import android.util.Log;

import com.google.gson.JsonObject;
import com.pair.adapter.BaseJsonAdapter;
import com.pair.data.Message;
import com.pair.net.Dispatcher;
import com.pair.net.HttpResponse;
import com.pair.net.api.MessageApi;
import com.pair.util.Config;
import com.pair.util.ConnectionHelper;

import org.apache.http.HttpStatus;

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
        //FIXME spawn a background daemons to send messages in the collection instead of looping over it
        for (Message message : messages) {
            dispatch(message);
        }
    }

    public void setJsonAdapter(BaseJsonAdapter<Message> adapter) {
        synchronized (adapterLock) {
            this.jsonAdapter = adapter;
        }
    }

    private void doDispatch(JsonObject data, String id) {
        SenderJob job = new SenderJob(id, data, 0);
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
        final static long MIN_DELAY = 5000; // 5 seconds
        public static final long MAX_DELAY = AlarmManager.INTERVAL_HOUR;
        int retryTimes;
        JsonObject data;
        String id;
        long backOff;

        SenderJob(String id, JsonObject data, int retryTimes) {
            this.retryTimes = retryTimes;
            this.data = data;
            this.id = id;
            this.backOff = MIN_DELAY;
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

                    if (dispatcherMonitor != null) {
                        dispatcherMonitor.onSendSucceeded(job.id);
                    }
                }

                @Override
                public void failure(RetrofitError retrofitError) {
                    //retry if network available
                    if (retrofitError.getKind().equals(RetrofitError.Kind.UNEXPECTED)) {
                        Log.i(TAG, "unexpected error, trying to send message again");
                        tryAgain(job);
                    } else if (retrofitError.getKind().equals(RetrofitError.Kind.HTTP)) {
                        int statusCode = retrofitError.getResponse().getStatus();
                        if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                            //TODO use  a correct exponential backoff algorithm to avoid overwhelming the server with bunch of requests
                            // while it attempts to come back alive.
                            Log.i(TAG, "internal server error, trying to send again");
                            tryAgain(job);
                        } else {//crash early
                            // as far as we know, our backend will only return other status code if its is our fault and that normally should not happen
                            throw new RuntimeException("An unknown internal error occurred");
                        }
                    } else if (retrofitError.getKind().equals(RetrofitError.Kind.CONVERSION)) { //crash early
                        throw new RuntimeException("poorly encoded json data");
                    } else if (retrofitError.getKind().equals(RetrofitError.Kind.NETWORK)) {
                        //TODO handle the EOF error that retrofit causes every first time we try to make a network request
                        //bubble up error and empty send queue let callers re-dispatch messages again;
                        if (ConnectionHelper.isConnectedOrConnecting(Config.getApplicationContext())) {
                            Log.i(TAG, "failed to send message retrying");
                            tryAgain(job);
                        } else if (dispatcherMonitor != null) {
                            dispatcherMonitor.onSendFailed("Error in network connection", job.id);
                        }
                    }
                }
            });
        }

        private void tryAgain(final SenderJob job) {
            if (job.retryTimes < MAX_RETRY_TIMES) {
                job.retryTimes++;
                job.backOff *= 2; //backoff exponentially
                if (job.backOff > SenderJob.MAX_DELAY) {
                    job.backOff = SenderJob.MAX_DELAY;
                }
                RETRY_HANDLER.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        doSend(job); //async
                    }
                }, job.backOff);
                return;
            }
            if (dispatcherMonitor != null)
                dispatcherMonitor.onSendFailed("an unknown error occurred", job.id);

        }

    }

    private final Handler RETRY_HANDLER = new Handler();
}
