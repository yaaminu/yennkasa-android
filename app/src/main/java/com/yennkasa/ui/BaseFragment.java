package com.yennkasa.ui;

import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import butterknife.ButterKnife;

/**
 * Created by yaaminu on 1/12/17.
 */
public abstract class BaseFragment extends Fragment {

    @Nullable
    @Override
    public final View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(getLayout(), container, false);
        ButterKnife.bind(this, view);
        donOnCreateView(view, savedInstanceState);
        return view;
    }

    protected void donOnCreateView(@NonNull View view, @Nullable Bundle savedInstance) {
    }

    @LayoutRes
    protected abstract int getLayout();

    @Override
    public final void onDestroyView() {
        doOnDestroyView();
        ButterKnife.unbind(this);
        super.onDestroyView();
    }

    protected void doOnDestroyView() {
    }
}
