package com.pairapp.messenger;

import android.support.annotation.NonNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * @author aminu on 7/1/2016.
 */
public class FakeLock implements Lock {
    @Override
    public void lock() {

    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock() {
        return true;
    }

    @Override
    public boolean tryLock(long time, @NonNull TimeUnit unit) throws InterruptedException {
        return true;
    }

    @Override
    public void unlock() {

    }

    @NonNull
    @Override
    public Condition newCondition() {
        return null;
    }
}
