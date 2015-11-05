package com.idea.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;

import com.idea.pairapp.R;
import com.idea.util.ScreenUtility;
import com.idea.util.TypeFaceUtil;
import com.idea.util.ViewUtils;
import com.jmpergar.awesometext.AwesomeTextHandler;
import com.jmpergar.awesometext.MentionSpanRenderer;

import java.util.HashSet;
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
    private static final String MENTION_PATTERN = "(@[\\p{L}0-9-_ [^@]]+)";

    private OnFragmentInteractionListener interactionListener;
    private Filter filter;
    private GridView gridContainer;
    @SuppressWarnings("FieldCanBeLocal")
    private ListView listContainer;
    private View addView;
    private Set<String> selectedItems = new HashSet<>();
    private AwesomeTextHandler awesomeTextViewHandler;
    private EditText filterEditText;

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
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_items_selector, container, false);
        addView = view.findViewById(R.id.bt_add);
        filterEditText = ((EditText) view.findViewById(R.id.et_filter_input_box));
        gridContainer = (GridView) view.findViewById(R.id.gv_container);
        listContainer = (ListView) view.findViewById(R.id.lv_container);
        selectedItems.clear();
        awesomeTextViewHandler = new AwesomeTextHandler();
        TextView selectedUsers = (TextView) view.findViewById(R.id.tv_selected_tems);
        awesomeTextViewHandler
                .addViewSpanRenderer(MENTION_PATTERN, new MentionSpanRenderer())
                .setView(selectedUsers);
        awesomeTextViewHandler.hide();

        ViewUtils.setTypeface(selectedUsers, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        ViewUtils.setTypeface(filterEditText, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        ViewUtils.setTypeface((TextView) addView, TypeFaceUtil.ROBOTO_REGULAR_TTF);
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
            filterEditText.setVisibility(View.VISIBLE);
            filterEditText.addTextChangedListener(this);
            filter = interactionListener.filter().getFilter();
        } else {
            ViewUtils.hideViews(filterEditText);
        }
        if (interactionListener.multiChoice()) {
            ViewUtils.showViews(selectedUsers);
        } else {
            ViewUtils.hideViews(selectedUsers);
        }
        if (interactionListener.supportAddCustom()) {
            addView.setVisibility(View.VISIBLE);
            addView.setEnabled(filterEditText.getText().length() > 5);
            addView.setOnClickListener(this);
        } else {
            ViewUtils.hideViews(addView);
        }
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

    private void onSetupFilterEditText() {
        final long itemCount = interactionListener.getAdapter().getCount();
        if (!interactionListener.supportAddCustom()) { //show the filterEditText if clients support adding custom
            //we will not show the filter if the content fit on the screen without the need to scroll
            float height = new ScreenUtility(getActivity()).getPixelsHeight();
            float preferredListItemHeight = getActivity().getResources().getDimension(R.dimen.list_item_height);

            //assume the toolbar is 100 pixels in height
            //noinspection ConstantConditions
            float actualHeight = height - 100;
            int numOfMaxItems = ((int) (actualHeight / preferredListItemHeight));

            if (interactionListener.preferredContainer().equals(ContainerType.LIST)) {
                if (itemCount > numOfMaxItems) {
                    filterEditText.setVisibility(View.VISIBLE);
                } else {
                    filterEditText.setVisibility(View.GONE);
                }
            } else {
                if (itemCount > gridContainer.getAdapter().getCount() * 3) { //we assume the gridview has 3 columns
                    filterEditText.setVisibility(View.VISIBLE);
                } else {
                    filterEditText.setVisibility(View.GONE);
                }
            }
        } else {
            if (itemCount > 0) {
                filterEditText.setHint(R.string.search_or_add_custom);
                filterEditText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_search, 0, 0, 0);
            } else {
                filterEditText.setHint(R.string.add_custom);
                filterEditText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
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
        if (filter != null) {
            filter.filter(s);
        }
//        onSetupFilterEditText();
        if (interactionListener.supportAddCustom()) {
            if (interactionListener.getAdapter().getCount() > 0) {
                filterEditText.setHint(R.string.search_or_add_custom);
                filterEditText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_search, 0, 0, 0);
            } else {
                filterEditText.setHint(R.string.add_custom);
                filterEditText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
        }
        addView.setEnabled(s.length() > 5 && TextUtils.isDigitsOnly(s));
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        interactionListener.onItemClick(parent, view, position, id);
    }

    void onItemsChanged() {
        if (interactionListener.multiChoice()) {
            selectedItems = interactionListener.selectedItems();
            if (selectedItems.isEmpty()) {
                awesomeTextViewHandler.hide();
            } else {
                awesomeTextViewHandler.show();
                CharSequence sequence = TextUtils.join("", selectedItems);
                awesomeTextViewHandler.setText(sequence);
            }
        } else {
            throw new IllegalStateException("only for interactionListeners that support multiChoice ");
        }
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

        Set<String> selectedItems();

        void onCustomAdded(String item);

        void onItemClick(AdapterView<?> parent, View view, int position, long id);

//        Collection<String> selectedItems();
    }
}
