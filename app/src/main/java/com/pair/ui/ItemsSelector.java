package com.pair.ui;

import android.app.Activity;
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

import com.pair.pairapp.R;
import com.pair.util.ScreenUtility;

/**
 * A simple {@link Fragment} for selecting items.
 * Activities that contain this fragment must implement the
 * {@link ItemsSelector.OnFragmentInteractionListener} interface
 * to handle interaction events.
 */
public class ItemsSelector extends Fragment implements View.OnClickListener, TextWatcher {

    private static final String TAG = ItemsSelector.class.getSimpleName();
    private OnFragmentInteractionListener interactionListener;
    private Filter filter;
    private GridView gridContainer;
    private ListView listContainer;
    private View addView;


    public ItemsSelector() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Activity activity) {
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


    private EditText filterEditText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_items_selector, container, false);
        addView = view.findViewById(R.id.bt_add);
        filterEditText = ((EditText) view.findViewById(R.id.et_filter_input_box));
        gridContainer = (GridView) view.findViewById(R.id.gv_container);
        listContainer = (ListView) view.findViewById(R.id.lv_container);

        View emptyView = interactionListener.emptyView();
        final View defaultEmptyView = view.findViewById(R.id.tv_empty);
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
        ((TextView) defaultEmptyView).setText(((TextView) emptyView).getText());
        emptyView = defaultEmptyView;
        if (interactionListener.preferredContainer() == ContainerType.LIST) {
            listContainer.setEmptyView(emptyView);
            listContainer.setOnItemClickListener(interactionListener);
            listContainer.setChoiceMode(interactionListener.multiChoice() ? AbsListView.CHOICE_MODE_MULTIPLE : AbsListView.CHOICE_MODE_SINGLE);
            listContainer.setAdapter(interactionListener.getAdapter());
            ((ViewGroup) view).removeView(gridContainer);
        } else {
            gridContainer.setEmptyView(emptyView);
            gridContainer.setAdapter(interactionListener.getAdapter());
            gridContainer.setOnItemClickListener(interactionListener);
            gridContainer.setChoiceMode(interactionListener.multiChoice() ? AbsListView.CHOICE_MODE_MULTIPLE : AbsListView.CHOICE_MODE_SINGLE);
            ((ViewGroup) view).removeView(listContainer);
        }
        if (interactionListener.filter() != null) {
            filterEditText.setVisibility(View.VISIBLE);
            filterEditText.addTextChangedListener(this);
            filter = interactionListener.filter().getFilter();
        }

        if (interactionListener.supportAddCustom()) {
            addView.setVisibility(View.VISIBLE);
            addView.setEnabled(filterEditText.getText().length() > 5);
            addView.setOnClickListener(this);
        }
        try {
            final String title = getArguments().getString(MainActivity.ARG_TITLE);
            if (title != null) {
                //noinspection ConstantConditions
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
        if (!interactionListener.supportAddCustom()) { //show the filterEditText if clients support adding custom
            //we will not show the filter if the content fit on the screen without the need to scroll
            float height = new ScreenUtility(getActivity()).getPixelsHeight();
            float preferredListItemHeight = getActivity().getResources().getDimension(R.dimen.list_item_height);

            //assume the toolbar is 100 pixels in height
            //noinspection ConstantConditions
            float actualHeight = height - 100;
            int numOfMaxItems = ((int) (actualHeight / preferredListItemHeight));

            if (interactionListener.preferredContainer().equals(ContainerType.LIST)) {
                if (listContainer.getAdapter().getCount() > numOfMaxItems) {
                    filterEditText.setVisibility(View.VISIBLE);
                } else {
                    filterEditText.setVisibility(View.GONE);
                }
            } else {
                if (gridContainer.getAdapter().getCount() > gridContainer.getNumColumns() * numOfMaxItems) {
                    filterEditText.setVisibility(View.VISIBLE);
                } else {
                    filterEditText.setVisibility(View.GONE);
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
        if (filter != null) {
            filter.filter(s);
        }
        addView.setEnabled(s.length() > 5 && TextUtils.isDigitsOnly(s));
    }


    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     */
    public interface OnFragmentInteractionListener extends AdapterView.OnItemClickListener {
        BaseAdapter getAdapter();

        Filterable filter();

        ContainerType preferredContainer();

        View emptyView();

        boolean multiChoice();

        boolean supportAddCustom();

        void onCustomAdded(String item);

//        Collection<String> selectedItems();
    }

    public enum ContainerType {
        LIST, GRID
    }
}
