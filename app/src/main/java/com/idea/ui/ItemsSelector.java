package com.idea.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Filterable;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;

import com.idea.pairapp.R;
import com.idea.util.TypeFaceUtil;
import com.idea.util.UiHelpers;
import com.idea.util.ViewUtils;

import java.util.Set;

/**
 * A simple {@link Fragment} for selecting items.
 * Activities that contain this fragment must implement the
 * {@link ItemsSelector.OnFragmentInteractionListener} interface
 * to handle interaction events.
 */
public class ItemsSelector extends Fragment implements View.OnClickListener, TextWatcher, AdapterView.OnItemClickListener {

//    private static final String TAG = ItemsSelector.class.getSimpleName();


    //    private static final String HASHTAG_PATTERN = "(#[\\p{L}0-9-_]+)";
    // private static final String MENTION_PATTERN = "@_#_@_#[.[^_#_@_#]]+)";
//    private static final String MENTION_PATTERN = "(@[\\p{L}0-9-_ [^@]]+)";

    private OnFragmentInteractionListener interactionListener;
    private Filterable filter;
    @SuppressWarnings("FieldCanBeLocal")
    private ListView listContainer;
    //    private Set<String> selectedItems = new HashSet<>();
//    private AwesomeTextHandler awesomeTextViewHandler;
    private EditText filterEditText;
    private ViewGroup searchBar;
    //    private TextView selectedUsers;

    public ItemsSelector() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        try {
            interactionListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement " + OnFragmentInteractionListener.class.getName());
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        interactionListener = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        setRetainInstance(true);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_items_selector, container, false);
        GridView gridContainer = (GridView) view.findViewById(R.id.gv_container);
        listContainer = (ListView) view.findViewById(R.id.lv_container);
//        selectedItems.clear();
//        awesomeTextViewHandler = new AwesomeTextHandler();
//        selectedUsers = (TextView) view.findViewById(R.id.tv_selected_tems);
//        awesomeTextViewHandler
//                .addViewSpanRenderer(MENTION_PATTERN, new MentionSpanRenderer())
//                .setView(selectedUsers);
//        awesomeTextViewHandler.hide();
//        ViewUtils.setTypeface(selectedUsers, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        ViewUtils.setTypeface(filterEditText, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        View emptyView = interactionListener.emptyView();
        final TextView defaultEmptyView = ((TextView) view.findViewById(R.id.tv_empty));
        //noinspection StatementWithEmptyBody
        if (emptyView == null) {
            emptyView = defaultEmptyView;
        } else {
//            we are having troubles adding the view to the view hierarchy.
////             TODO: 9/19/2015 fix this
////            add the view to the parent of the collection container(list/grid)
//            final ViewGroup parent = (ViewGroup) defaultEmptyView.getParent();
//            parent.addView(emptyView, 0, defaultEmptyView.getLayoutParams());
//            parent.removeView(defaultEmptyView);
        }
        defaultEmptyView.setText(((TextView) emptyView).getText());
        ViewUtils.setTypeface(defaultEmptyView, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);

        emptyView = defaultEmptyView;
        if (interactionListener.preferredContainer() == ContainerType.LIST) {
            listContainer.setEmptyView(emptyView);
            listContainer.setOnItemClickListener(this);
            listContainer.setChoiceMode(interactionListener.multiChoice() ? AbsListView.CHOICE_MODE_MULTIPLE : AbsListView.CHOICE_MODE_SINGLE);
            listContainer.setAdapter(interactionListener.getAdapter());
            ((ViewGroup) view).removeView(gridContainer);
        } else {
            gridContainer.setEmptyView(emptyView);
            gridContainer.setAdapter(interactionListener.getAdapter());
            gridContainer.setOnItemClickListener(this);
//            gridContainer.setChoiceMode(interactionListener.multiChoice() ? AbsListView.CHOICE_MODE_MULTIPLE : AbsListView.CHOICE_MODE_SINGLE);
            ((ViewGroup) view).removeView(listContainer);
        }
        if (interactionListener.filter() != null) {
            searchBar = interactionListener.searchBar();
            filterEditText = (EditText) searchBar.findViewById(R.id.et_filter_input_box);
            View cancelView = searchBar.findViewById(R.id.cancel_search_filter);
            cancelView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    filterEditText.setText("");
                    View toolBar = interactionListener.getToolBar();
                    if (toolBar != null) {
                        ViewUtils.showViews(toolBar);
                    }
                    ViewUtils.hideViews(searchBar);
                }
            });
            filterEditText.addTextChangedListener(this);
            filterEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == getResources().getInteger(R.integer.imei_search_add) || (interactionListener.supportAddCustom() && actionId == EditorInfo.IME_NULL)) {
                        String number = filterEditText.getText().toString().trim();
                        if (number.length() <= 5) {
                            UiHelpers.showToast(getString(R.string.number_too_short));
                        } else {
                            interactionListener.onCustomAdded(number);
                            filterEditText.setText("");
                            ViewUtils.hideViews(searchBar);
                            ViewUtils.showViews(interactionListener.getToolBar());
                        }
                        return true;
                    }
                    return false;
                }
            });
            filter = interactionListener.filter();
            ViewUtils.hideViews(searchBar);
        }

//        ViewUtils.hideViews(selectedUsers);
        try {
            final String title = getArguments().getString(MainActivity.ARG_TITLE);
            if (title != null) {
                //noinspection ConstantConditions,deprecation
                ((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(title);
            }
        } catch (NullPointerException e) {
            //no arguments passed or maybe containing activity has no action bar
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        onSetupFilterEditText();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (interactionListener.filter() != null) {
            MenuItem item = menu.add(Menu.NONE, R.id.action_search, 2000, R.string.search);
            item.setIcon(R.drawable.abc_ic_search_api_mtrl_alpha);
            MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        }
        if (interactionListener.supportAddCustom()) {
            menu.add(Menu.NONE, R.id.action_add, Menu.NONE, R.string.add_custom);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        View toolBar = interactionListener.getToolBar();
        switch (itemId) {
            case R.id.action_search:
                if (interactionListener.supportAddCustom()) {
                    filterEditText.setImeActionLabel(getString(R.string.add_ime), getResources().getInteger(R.integer.imei_search_add));
                } else {
                    filterEditText.setImeActionLabel(null, EditorInfo.IME_ACTION_NONE);
                }
                ViewUtils.showViews(searchBar);
                if (toolBar != null) {
                    ViewUtils.hideViews(toolBar);
                }
                break;
            case R.id.action_add:
                filterEditText.setImeActionLabel(getString(R.string.add_ime), getResources().getInteger(R.integer.imei_search_add));
                ViewUtils.showViews(searchBar);
                if (toolBar != null) {
                    ViewUtils.hideViews(toolBar);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onSetupFilterEditText() {
        if (interactionListener.supportAddCustom() || interactionListener.filter() != null) {
            final long itemCount = interactionListener.getAdapter().getCount();
            if (!interactionListener.supportAddCustom() && interactionListener.filter() != null) { //show the filterEditText if clients support adding custom
                filterEditText.setHint(R.string.abc_search_hint);

            } else {
                if (itemCount > 0) {
                    filterEditText.setHint(R.string.search_or_add_custom);
                } else {
                    filterEditText.setHint(R.string.add_custom);
                }
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_add:
                interactionListener.onCustomAdded(filterEditText.getText().toString());
                filterEditText.setText("");
                break;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        CharSequence ss = s.toString().trim();
        if (filter != null) {
            filter.getFilter().filter(ss);
        }
//        onSetupFilterEditText();
        if (interactionListener.supportAddCustom()) {
            filterEditText.setHint(R.string.search_or_add_custom);
        } else {
            filterEditText.setHint(R.string.abc_search_hint);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        interactionListener.onItemClick(parent, view, position, id);
    }

    void onItemsChanged() {
//        if (interactionListener.multiChoice()) {
//            selectedItems = interactionListener.selectedItems();
//            if (selectedItems.isEmpty()) {
//                awesomeTextViewHandler.hide();
//                ViewUtils.hideViews(selectedUsers);
//            } else {
//                awesomeTextViewHandler.show();
//                CharSequence sequence = TextUtils.join("", selectedItems);
//                ViewUtils.showViews(selectedUsers);
//                awesomeTextViewHandler.setText(sequence);
//            }
//        } else {
//            throw new IllegalStateException("only for interactionListeners that support multiChoice ");
//        }
    }

    public enum ContainerType {
        LIST, GRID
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     */
    public interface OnFragmentInteractionListener {
        BaseAdapter getAdapter();

        Filterable filter();

        ContainerType preferredContainer();

        View emptyView();

        boolean multiChoice();

        boolean supportAddCustom();

        @SuppressWarnings("unused")
            //will be important later
        Set<String> selectedItems();

        void onCustomAdded(String item);

        void onItemClick(AdapterView<?> parent, View view, int position, long id);

        View getToolBar();

        ViewGroup searchBar();
//        Collection<String> selectedItems();
    }
}
