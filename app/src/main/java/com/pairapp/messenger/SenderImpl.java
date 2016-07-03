package com.pairapp.messenger;

import com.pairapp.data.StatusManager;
import com.pairapp.net.sockets.PairappSocket;

/**
 * @author aminu on 7/2/2016.
 */
public class SenderImpl implements StatusManager.Sender {

    private final PairappSocket socket;

    public SenderImpl(PairappSocket socket) {
        this.socket = socket;
    }

    @Override
    public void sendMessage(byte[] payload) {
        socket.send(payload);
    }
}
