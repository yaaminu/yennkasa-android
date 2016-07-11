package com.pairapp.net.sockets;

import android.util.Log;

/**
 * @author aminu on 7/11/2016.
 */
interface Logger {
    Logger DEFAULT_LOGGER = new Logger() {
        @Override
        public void Log(int level, String tag, String message, Throwable cause) {
            Log.e(tag, message, cause);
        }

        @Override
        public void Log(int level, String tag, String message, Object... args) {
            Log.println(level, tag, String.format(message, args));
        }
    };

    int V = 1, D = 2, I = 3, W = 4, E = 5;

    void Log(int level, String tag, String message, Throwable cause);

    void Log(int level, String tag, String message, Object... args);
}
