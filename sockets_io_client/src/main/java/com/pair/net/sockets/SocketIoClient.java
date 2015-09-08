package com.pair.net.sockets;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import java.io.Closeable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.nkzawa.socketio.client.Socket.EVENT_CONNECT;
import static com.github.nkzawa.socketio.client.Socket.EVENT_CONNECT_ERROR;
import static com.github.nkzawa.socketio.client.Socket.EVENT_CONNECT_TIMEOUT;
import static com.github.nkzawa.socketio.client.Socket.EVENT_DISCONNECT;
import static com.github.nkzawa.socketio.client.Socket.EVENT_ERROR;
import static com.github.nkzawa.socketio.client.Socket.EVENT_RECONNECT;
import static com.github.nkzawa.socketio.client.Socket.EVENT_RECONNECTING;
import static com.github.nkzawa.socketio.client.Socket.EVENT_RECONNECT_ERROR;
import static com.github.nkzawa.socketio.client.Socket.EVENT_RECONNECT_FAILED;
import static com.github.nkzawa.socketio.client.Socket.Listener;

public class SocketIoClient implements Closeable {

    public static final String TAG = "SocketsIOClient";

    private final AtomicBoolean initialised = new AtomicBoolean(false);
    private final AtomicBoolean ready = new AtomicBoolean(false);
    public static final String EVENT_MESSAGE = "message";
    public static final String EVENT_MSG_STATUS = "msgStatus";

    private final List<Pair<String, Object>> waitingBroadcasts = new ArrayList<>();
    private final String userId;

    private Socket CLIENT;
    private static final WeakHashMap<String, SocketIoClient> instances = new WeakHashMap<>();
    private Handler reconnectHandler;
    private Timer reconnectTimer;
    private String endPoint;

    private final AtomicInteger referenceCount = new AtomicInteger(0);


    private final Listener ON_RECONNECTING = new Listener() {
        @Override
        public void call(Object... args) {
            ready.set(false);
            Log.i(TAG, "reconnecting...");
        }
    };

    private final Listener ON_ERROR = new Listener() {
        @Override
        public void call(Object... args) {
            ready.set(false);
            Log.e(TAG, "client error!: ");
            if (args.length > 0) {
                Log.e(TAG, "message: " + args[0]);
            }
        }
    };
    private final Listener ON_RECONNECT = new Listener() {
        @Override
        public void call(Object... args) {
            ready.set(false);
            Log.i(TAG, "preparing to reconnect...");
            attemptReconnect();
        }
    };


    private final Emitter.Listener ON_CONNECTED = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.i(TAG, "connected");
            ready.set(true);
            sendQueuedMessages();
        }
    };

    private void sendQueuedMessages() {
        if (CLIENT.connected()) {
            synchronized (waitingBroadcasts) {
                for (Pair<String, Object> waitingBroadcast : waitingBroadcasts) {
                    CLIENT.emit(waitingBroadcast.first, waitingBroadcast.second);
                }
                waitingBroadcasts.clear();
            }
        }
    }

    private final Emitter.Listener ON_DISCONNECTED = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            ready.set(false);
            if (initialised.get() && referenceCount.get() > 0) {
                Log.i(TAG, "socket disconnected unexpectedly, retrying");
                Log.i(TAG, args[0] + "");
                attemptReconnect();
            } else {
                Log.i(TAG, "disconnected");
            }
        }
    };

    private final Emitter.Listener ON_CONNECT_TIMEOUT = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            ready.set(false);
            Log.i(TAG, "connection timed out");
        }
    };

    private final Emitter.Listener ON_CONNECT_ERROR = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            ready.set(false);
            Log.i(TAG, "connection error");
        }
    };

    private SocketIoClient(String endPoint, String userId) {
        //if we are in a testing environment we cannot use handlers
        //more so if we are initialised from a thread with no looper
        try {
            //noinspection ConstantConditions
            reconnectHandler = new Handler(Looper.myLooper());
        } catch (Exception e) {
            Log.w(TAG, "it is discouraged to create socketIoClient  instance on threads with  no looper attached");
            reconnectTimer = new Timer("reconnect timer");
        }
        initialise(endPoint, userId);
        this.endPoint = endPoint;
        this.userId = userId;
    }

    private void initialise(String endPoint, String userId) {
        if (!initialised.get()) {
            try {
                URL url = new URL(endPoint); //this is just to ensure urls passed are valid.
                IO.Options options = new IO.Options();
                options.query = "userId=" + userId;
                options.forceNew = true;
                CLIENT = IO.socket(URI.create(endPoint), options);
                CLIENT.on(EVENT_CONNECT, ON_CONNECTED);
                CLIENT.on(EVENT_DISCONNECT, ON_DISCONNECTED);
                CLIENT.on(EVENT_CONNECT_ERROR, ON_CONNECT_ERROR);
                CLIENT.on(EVENT_CONNECT_TIMEOUT, ON_CONNECT_TIMEOUT);
                CLIENT.on(EVENT_RECONNECT, ON_RECONNECT);
                CLIENT.on(EVENT_RECONNECT_ERROR, ON_CONNECT_ERROR);
                CLIENT.on(EVENT_ERROR, ON_ERROR);
                CLIENT.on(EVENT_RECONNECT_FAILED, ON_CONNECT_ERROR);
                CLIENT.on(EVENT_RECONNECTING, ON_RECONNECTING);
                CLIENT.connect();
                initialised.set(true);
            } catch (URISyntaxException impossible) {
                throw new IllegalArgumentException("url passed is invalid");
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("invalid url passed");
            }
        }
    }

    public static synchronized SocketIoClient getInstance(String endPoint, String userId) {
        SocketIoClient client = instances.get(endPoint);
        if (client == null) {
            client = new SocketIoClient(endPoint, userId);
            instances.put(endPoint, client);
        }
        return client;
    }

    public boolean registerForEvent(String eventName, Listener eventReceiver) {
        if (!initialised.get()) {
            Log.w(TAG, "can't register for an event. client yet to start");
            return false;
        }
        CLIENT.on(eventName, eventReceiver);
        return true;
    }

    public boolean unRegisterEvent(String eventName, Listener eventReceiver) {
        if (!initialised.get()) {
            Log.w(TAG, "can't unregister  an event. client yet to start");
            return false;
        }
        CLIENT.off(eventName, eventReceiver);
        return true;
    }

    public boolean registerForEventOnce(String eventName, Listener eventReceiver) {
        if (!initialised.get()) {
            return false;
        }
        CLIENT.once(eventName, eventReceiver);
        return true;
    }

    public void broadcast(String eventName, Object jsonData) {
        if (ready.get() && CLIENT.connected()) {
            CLIENT.emit(eventName, jsonData);
        } else {
            ready.set(false);
            synchronized (waitingBroadcasts) {
                //queue the message.
                waitingBroadcasts.add(new Pair<>(eventName, jsonData));
                Log.i(TAG, "message queued for sending later client yet to start");
            }
            attemptReconnect();
        }
    }

    public void send(Object jsonData) {
        broadcast(EVENT_MESSAGE, jsonData);
    }

    @Override
    public synchronized void close() {
        if (initialised.get()) {
            if (referenceCount.decrementAndGet() == 0) {
                instances.remove(getEndPoint());
                if (CLIENT.connected()) {
                    CLIENT.disconnect();
                }
                CLIENT.close();
                initialised.set(false);
                ready.set(false);
            }
        }
    }


    private void attemptReconnect() {
        try {
            reconnectHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!CLIENT.connected()) {
                        CLIENT.connect();
                    }
                }
            }, CLIENT.io().reconnectionDelay());
        } catch (Exception noLooperOrInTestEnvironment) {
            reconnectTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (!CLIENT.connected()) {
                        CLIENT.connect();
                    }
                }
            }, CLIENT.io().reconnectionDelay());
        }
    }

    public String getEndPoint() {
        return endPoint;
    }
}
