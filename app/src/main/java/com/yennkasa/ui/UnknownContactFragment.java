package com.yennkasa.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.yennkasa.R;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.util.TaskManager;
import com.yennkasa.util.ThreadUtils;
import com.yennkasa.util.UiHelpers;
import com.yennkasa.util.ViewUtils;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.realm.Realm;

import static com.yennkasa.util.ViewUtils.showViews;

/**
 * @author aminu on 11/20/2016.
 */
public class UnknownContactFragment extends Fragment {

    @Bind(R.id.notice)
    TextView addToContactsNotice;
    private UserProvider userProvider;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.unknown_user_fragment, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        userProvider = ((UserProvider) context);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UiHelpers.ADD_TO_CONTACTS_REQUEST && resultCode == Activity.RESULT_OK) {
            Realm realm = userProvider.realm();
            realm.beginTransaction();
            userProvider.currentUser().setInContacts(true);
            realm.commitTransaction();
            refreshDisplay();
        }
    }

    @OnClick(R.id.add_to_contact_or_invite)
    void addToContacts() {
        UiHelpers.addToContact(getActivity(), userProvider.currentUser());
    }

    @OnClick(R.id.block)
    void blockUser() {
        UiHelpers.showErrorDialog(getActivity(), R.string.block_user_confirmatory_alert, android.R.string.ok, android.R.string.no, new UiHelpers.Listener() {
            @Override
            public void onClick() {
                ProgressDialog dialog = new ProgressDialog(getActivity());
                dialog.setCancelable(false);
                dialog.setMessage(getString(R.string.st_please_wait));
                dialog.show();
                doBlockUser(dialog);
            }
        }, null);
    }

    private void doBlockUser(final ProgressDialog dialog) {
        final String userId = userProvider.currentUser().getUserId();
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                if (ThreadUtils.isMainThread()) {
                    dialog.dismiss();
                    UiHelpers.showToast(getString(R.string.user_blocked));
                    getActivity().finish();
                } else {
                    UserManager.getInstance().blockUser(userId);
                    TaskManager.executeOnMainThread(this);
                }
            }
        }, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshDisplay();
    }

    private void refreshDisplay() {
        if (userProvider.hideNotice()) {
            ViewUtils.hideViews(getView());
        } else {
            showViews(getView());
            addToContactsNotice.setText(getString(R.string.not_in_contacts_notice, userProvider.currentUser().getName()));
        }
    }

    public interface UserProvider {
        User currentUser();

        boolean hideNotice();

        Realm realm();
    }
}
