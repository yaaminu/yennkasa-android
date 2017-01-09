package com.yennkasa.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.yennkasa.R;
import com.yennkasa.adapter.CountriesListAdapter;
import com.yennkasa.adapter.YennkasaBaseAdapter;
import com.yennkasa.data.Country;
import com.yennkasa.util.ViewUtils;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;
import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmResults;
import rx.Observer;
import rx.Subscription;
import rx.subjects.BehaviorSubject;

/**
 * by yaaminu on 12/15/16.
 */
public class CountryLists extends AppCompatActivity {

    @Bind(R.id.main_toolbar)
    Toolbar toolbar;

    @Bind(R.id.search_bar)
    View searchView;

    @Bind(R.id.et_filter_input_box)
    EditText filterEt;

    @Bind(R.id.recycler_view)
    RecyclerView recyclerView;
    Realm realm;
    RealmResults<Country> countries;
    private Subscription subscription;
    private CountriesListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        realm = Country.REALM(this);
        countries = realm.where(Country.class).findAllSorted(Country.FIELD_NAME);
        searchSubject = BehaviorSubject.create();
        setContentView(R.layout.activity_countries);
        ButterKnife.bind(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        filterEt.requestFocus();
        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        adapter = new CountriesListAdapter(delegate);
        recyclerView.setAdapter(adapter);
        ViewUtils.hideViews(searchView);
    }

    @Override
    protected void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    private final YennkasaBaseAdapter.Delegate<Country> delegate = new YennkasaBaseAdapter.Delegate<Country>() {
        @Override
        public void onItemClick(YennkasaBaseAdapter<Country> adapter, View view, int position, long id) {
            Intent intent = new Intent();
            intent.putExtra(Country.FIELD_CCC, adapter.getItem(position).getCcc());
            setResult(RESULT_OK, intent);
            //quick fix for handling situations where result is null on some versions of android
            LoginFragment.results = intent;
            finish();
        }

        @Override
        public boolean onItemLongClick(YennkasaBaseAdapter<Country> adapter, View view, int position, long id) {
            return false;
        }

        @NonNull
        @Override
        public List<Country> dataSet() {
            return countries;
        }

        @Override
        public Realm userRealm() {
            return realm;
        }
    };

    @OnClick(R.id.clear_search)
    void clearSearch() {
        filterEt.setText("");
    }

    @Override
    public void onBackPressed() {
        if (ViewUtils.isViewVisible(searchView)) {
            closeSearch();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_search).setVisible(!ViewUtils.isViewVisible(searchView));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.select_country, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search:
                if (!ViewUtils.isViewVisible(searchView)) {
                    openSearch();
                }
                break;
            case android.R.id.home:
                if (ViewUtils.isViewVisible(searchView)) {
                    closeSearch();
                    break;
                }
                //else fall through
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void openSearch() {
        filterEt.setText("");
        ViewUtils.showViews(searchView);
        supportInvalidateOptionsMenu();
        subscription = searchSubject/*.debounce(1, TimeUnit.SECONDS)
                .map(new Func1<String, String>() {
                    @Override
                    public String call(String s) {
                        if (s.startsWith("+")) {
                            if (s.length() > 1) return s.substring(1);
                            return "";
                        }
                        return s;
                    }
                })
                .distinctUntilChanged()*/.subscribe(observer);
        filterEt.setText("");
    }

    private void closeSearch() {
        filterEt.setText("");
        if (!subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
        ViewUtils.hideViews(searchView);
        supportInvalidateOptionsMenu();
    }


    BehaviorSubject<String> searchSubject;

    @OnTextChanged(R.id.et_filter_input_box)
    void onTextChange(Editable text) {

        String filter = text.toString().trim();
        if (filter.startsWith("+")) {
            if (filter.length() > 1) {
                filter = filter.substring(1);
            } else {
                filter = "";
            }
        }
        searchSubject.onNext(filter);
    }

    private Observer<String> observer = new Observer<String>() {
        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {

        }

        @Override
        public void onNext(String s) {
            countries = realm.where(Country.class)
                    .beginsWith(Country.FIELD_NAME, s, Case.INSENSITIVE)
                    .or()
                    .beginsWith(Country.FIELD_CCC, s)
                    .findAllSorted(Country.FIELD_NAME);
            adapter.notifyDataChanged();
        }
    };

}
