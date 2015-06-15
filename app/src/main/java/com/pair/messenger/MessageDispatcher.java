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
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;
import retrofit.client.Response;

/**
 * @author by Null-Pointer on 5/26/2015.
 */
class MessageDispatcher implements Dispatcher<Message> {
    //TODO implement messageDispatcher as a service
    private static final String TAG = MessageDispatcher.class.getSimpleName();
    private volatile int NUM_OF_TASKS = 0;
    private static volatile MessageDispatcher INSTANCE;
    private final int MAX_RETRY_TIMES;
    private final MessageApi MESSAGE_API;
    private final Object dispatcherMonitorLock = new Object();
    private final BaseJsonAdapter<Message> jsonAdapter;
    private final Sender sender;
    private DispatcherMonitor dispatcherMonitor;
    private final Handler RETRY_HANDLER;

    private MessageDispatcher(BaseJsonAdapter<Message> jsonAdapter, DispatcherMonitor errorHandler, int retryTimes) {
        RestAdapter adapter = new RestAdapter.Builder()
                .setEndpoint(Config.PAIRAPP_ENDPOINT)
                .setRequestInterceptor(INTERCEPTOR)
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setLog(new AndroidLog(TAG))
                .build();

        this.MESSAGE_API = adapter.create(MessageApi.class);
        this.sender = new Sender();
        this.dispatcherMonitor = errorHandler;
        this.MAX_RETRY_TIMES = (retryTimes < 0) ? 0 : retryTimes;
        this.jsonAdapter = jsonAdapter;
        RETRY_HANDLER = new Handler();

    }

    static MessageDispatcher getInstance(BaseJsonAdapter<Message> adapter, DispatcherMonitor dispatcherMonitor, int retryTimes) {
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
        if (!ConnectionHelper.isConnectedOrConnecting(Config.getApplicationContext())) {
            Log.w(TAG, "no internet connection, message will no be sent");
            reportError(message, "not internet connection");
        }
        if ((message.getType() == Message.TYPE_DATE_MESSAGE)) {
            Log.w(TAG, "attempted to send a date message,but will not be sent");
            reportError(message, "date messages cannot be sent");
            return;
        }
        if (message.getState() != Message.STATE_PENDING || message.getState() == Message.STATE_SEND_FAILED) {
            Log.w(TAG, "attempted to send a sent message, but will not be sent");
            reportError(message, "message already send");
            return;
        }
        doDispatch(message);
    }

    private void reportError(Message message, String reason) {
        if (dispatcherMonitor != null) {
            dispatcherMonitor.onSendFailed(reason, message.getId());
        }
    }
    @Override
    public void dispatch(Collection<Message> messages) {
        //FIXME spawn background daemons to send messages in the collection instead of looping over it
        for (Message message : messages) {
            dispatch(message);
        }
    }

    private void doDispatch(Message message) {
        JsonObject data = jsonAdapter.toJson(message);
        SenderJob job = new SenderJob(message.getId(), data, 0);
        job.jobType = message.getType();
        if ((message.getType() != Message.TYPE_TEXT_MESSAGE)) {
            job.binPath = message.getMessageBody(); //message body must be a valid file
        }
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
        // TODO: 6/15/2015 sender job is to hardcoded, got to do something
        final static long MIN_DELAY = 5000; // 5 seconds
        public static final long MAX_DELAY = AlarmManager.INTERVAL_HOUR;
        int retryTimes;
        JsonObject data;
        String id;
        long backOff;
        int jobType = Message.TYPE_TEXT_MESSAGE;
        String binPath;

        SenderJob(String id, JsonObject data, int retryTimes) {
            this.retryTimes = retryTimes;
            this.data = data;
            this.id = id;
            this.backOff = MIN_DELAY;
        }

    }

    private class Sender {

        void enqueue(SenderJob job) {
            // TODO: 6/15/2015 implement an internal queue where some of the messages will be queued if there are too many to send
            doSend(job);
        }

        void doSend(final SenderJob job) {
            incrementNumOfTasks();
            Log.i(TAG, "about to send message: " + job.data.toString());
            sendTextMessage(job);

        }

        void doSendBin(final SenderJob job) {
            incrementNumOfTasks();
            Log.i(TAG, "about to send a binary message" + job.data.toString());
        }

        private void sendTextMessage(final SenderJob job) {
            //actual send implementation
            MESSAGE_API.sendMessage(job.data, new Callback<HttpResponse>() {
                @Override
                public void success(HttpResponse httpResponse, Response response) {
                    if (dispatcherMonitor != null) {
                        dispatcherMonitor.onSendSucceeded(job.id);
                    }
                    decrementNumOfTasks();
                }

                @Override
                public void failure(RetrofitError retrofitError) {
                    //retry if network available
                    handleError(retrofitError, job);
                }
            });
        }

        private void handleError(RetrofitError retrofitError, SenderJob job) {
            if (retrofitError.getKind().equals(RetrofitError.Kind.UNEXPECTED)) {
                Log.w(TAG, "unexpected error, trying to send message again");
                tryAgain(job);
            } else if (retrofitError.getKind().equals(RetrofitError.Kind.HTTP)) {
                int statusCode = retrofitError.getResponse().getStatus();
                if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                    Log.w(TAG, "internal server error, trying to send again");
                    tryAgain(job);
                } else {//crash early
                    // as far as we know, our backend will only return other status code if its is our fault and that normally should not happen
                    Log.wtf(TAG, "internal error, exiting");
                    throw new RuntimeException("An unknown internal error occurred");
                }
            } else if (retrofitError.getKind().equals(RetrofitError.Kind.CONVERSION)) { //crash early
                Log.wtf(TAG, "internal error ");
                throw new RuntimeException("poorly encoded json data");
            } else if (retrofitError.getKind().equals(RetrofitError.Kind.NETWORK)) {
                //bubble up error and empty send queue let callers re-dispatch messages again;
                if (ConnectionHelper.isConnectedOrConnecting(Config.getApplicationContext())) {
                    tryAgain(job);
                } else if (dispatcherMonitor != null) {
                    dispatcherMonitor.onSendFailed("Error in network connection", job.id);
                    decrementNumOfTasks();
                }
            }
        }

        private void tryAgain(final SenderJob job) {
            // TODO: 6/15/2015 check network availability before trying 
            // TODO: 6/15/2015 if there is no network we wont try again but will rather wait till we get connected
            if (job.retryTimes < MAX_RETRY_TIMES) {
                job.retryTimes++;
                job.backOff *= job.retryTimes; //backoff exponentially
                if (job.backOff > SenderJob.MAX_DELAY) {
                    job.backOff = SenderJob.MAX_DELAY;
                }
                Log.i(TAG, "retrying in: " + job.backOff + " milliseconds");
                RETRY_HANDLER.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        doSend(job); //async
                    }
                }, job.backOff);
                return;
            }
            if (dispatcherMonitor != null) {
                Log.w(TAG, "unable to send message after: " + job.backOff + "seconds");
                dispatcherMonitor.onSendFailed("an unknown error occurred", job.id);
                decrementNumOfTasks();
            }

        }
    }

    private void decrementNumOfTasks() {
        synchronized (this) {
            NUM_OF_TASKS--;
        }
        if (NUM_OF_TASKS == 0) {
            if (dispatcherMonitor != null) {
                dispatcherMonitor.onAllDispatched();
            }
        }
    }

    private void incrementNumOfTasks() {
        synchronized (this) {
            NUM_OF_TASKS++;
        }
    }

    private static final RequestInterceptor INTERCEPTOR = new RequestInterceptor() {
        @Override
        public void intercept(RequestFacade requestFacade) {
            requestFacade.addHeader("Authorization", "kiiboda+=s3cr3te");
            requestFacade.addHeader("User-Agent", Config.APP_USER_AGENT);
        }
    };
}
