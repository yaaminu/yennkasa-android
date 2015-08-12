package com.pair.messenger;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;

import com.pair.data.Message;

/**
 * @author Null-Pointer on 8/12/2015.
 */
public abstract class PairAppBaseActivity extends ActionBarActivity implements Notifier {
    protected PairAppClient.PairAppClientInterface pairAppClientInterface;
    protected boolean bound = false;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            pairAppClientInterface = ((PairAppClient.PairAppClientInterface) service);
            bound = true;
            onBind();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            onUnbind();
            bound = false;
            pairAppClientInterface = null; //free memory
        }
    };

    @Override
    protected void onStop() {
        if (bound) {
            bound = false;
            unbindService(connection);
        }
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        doBind();
    }

    protected void doBind() {
        Intent intent = new Intent(this, PairAppClient.class);
        intent.putExtra(PairAppClient.ACTION, PairAppClient.ACTION_SEND_ALL_UNSENT);
        bindService(intent, connection, BIND_AUTO_CREATE);
    }

    @Override
    public void notifyUser(Context context, Message message) {
        //do nothing
    }

    @Override
    public location where() {
        return location.FORE_GROUND;
    }

    protected abstract void onBind();

    protected abstract void onUnbind();
}
