package com.pairapp.ui;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.jakewharton.rxbinding.widget.RxTextView;
import com.pairapp.R;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.UiHelpers;
import com.pairapp.util.ViewUtils;
import com.rey.material.widget.SnackBar;

import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class SearchActivity extends PairAppActivity {

    public static final String EXTRA_TEXT = "text";
    @Bind(R.id.clear_search)
    View clearSearch;

    @Bind(R.id.et_filter)
    EditText filterEt;

    private Observable<CharSequence> textChangeEvents;
    private Subscription textchangesSubscription;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        ButterKnife.bind(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        filterEt.requestFocus();
        registerForEvent(EventBus.getDefault(), UserManager.EVENT_SEARCH_RESULTS, this);
        textChangeEvents = RxTextView.textChanges(filterEt).skip(1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        textchangesSubscription = textChangeEvents
                .filter(new Func1<CharSequence, Boolean>() {
                    @Override
                    public Boolean call(CharSequence s) {
                        return s.length() >= 3;
                    }
                })
                .debounce(1, TimeUnit.SECONDS)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .subscribe(new Action1<CharSequence>() {
                    @Override
                    public void call(CharSequence charSequence) {
                        userManager.search(charSequence.toString());
                    }
                });

        String text = getIntent().getStringExtra(EXTRA_TEXT);
        if (!text.isEmpty()) {
            filterEt.setText(text);
            filterEt.setSelection(text.length());
        }
    }

    @OnTextChanged(R.id.et_filter)
    void textChanged(Editable editable) {
        String o = editable.toString();
        if (o.isEmpty()) {
            ViewUtils.hideViews(clearSearch);
        } else {
            ViewUtils.showViews(clearSearch);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!textchangesSubscription.isUnsubscribed()) {
            textchangesSubscription.unsubscribe();
        }
    }

    @Nullable
    List<User> searchResutls = null;

    @Override
    protected void handleEvent(Event event) {
        if (event.getTag().equals(UserManager.EVENT_SEARCH_RESULTS)) {
            //noinspection unchecked
            searchResutls = ((List<User>) event.getData());
            //noinspection ThrowableResultOfMethodCallIgnored
            Exception error = event.getError();
            if (error != null) {
                UiHelpers.showErrorDialog(this, error.getMessage());
            }
            refreshDisplay();
        } else {
            super.handleEvent(event);
        }
    }

    private void refreshDisplay() {
        UiHelpers.showToast("results retrieved");
    }

    @Override
    protected void onDestroy() {
        unRegister(EventBus.getDefault(), UserManager.EVENT_SEARCH_RESULTS, this);
        super.onDestroy();
    }

    @Bind(R.id.notification_bar)
    SnackBar snackbar;

    @NonNull
    @Override
    protected SnackBar getSnackBar() {
        return snackbar;
    }

    @OnClick(R.id.back)
    void goBack() {
        finish();
    }

    @OnClick(R.id.clear_search)
    void clearSearch() {
        filterEt.setText("");
    }

}
