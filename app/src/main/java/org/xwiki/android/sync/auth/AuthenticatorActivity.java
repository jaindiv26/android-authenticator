/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.android.sync.auth;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import org.xwiki.android.sync.AppContext;
import org.xwiki.android.sync.Constants;
import org.xwiki.android.sync.R;
import org.xwiki.android.sync.activities.SettingIpViewFlipper;
import org.xwiki.android.sync.activities.SettingSyncViewFlipper;
import org.xwiki.android.sync.activities.SignInViewFlipper;
import org.xwiki.android.sync.utils.IntentUtils;
import org.xwiki.android.sync.utils.PermissionsUtils;
import org.xwiki.android.sync.utils.SharedPrefsUtils;
import org.xwiki.android.sync.utils.StatusBarColorCompat;

import java.util.ArrayList;
import java.util.List;

import static org.xwiki.android.sync.AppContext.currentBaseUrl;


/**
 * @version $Id: $
 */
public class AuthenticatorActivity extends AccountAuthenticatorActivity{
    private static final String TAG = "AuthenticatorActivity";

    public static final String KEY_AUTH_TOKEN_TYPE = "KEY_AUTH_TOKEN_TYPE";
    public static final String KEY_ERROR_MESSAGE = "ERR_MSG";
    public final static String PARAM_USER_SERVER = "XWIKI_USER_SERVER";
    public final static String PARAM_USER_PASS = "XWIKI_USER_PASS";
    public final static String PARAM_APP_UID = "PARAM_APP_UID";
    public final static String PARAM_APP_PACKAGENAME = "PARAM_APP_PACKAGENAME";
    public final static String IS_SETTING_SYNC_TYPE = "IS_SETTING_SYNC_TYPE";

    private SettingIpViewFlipper settingsIpViewFlipper;
    private SignInViewFlipper signInViewFlipper;
    private SettingSyncViewFlipper settingSyncViewFlipper;

    private AccountManager mAccountManager;
    AlertDialog.Builder builder;
    private ViewFlipper mViewFlipper;
    private Toolbar toolbar;
    //show progress dialog
    private Dialog mProgressDialog = null;
    //add all asyncTask and clear all tasks when calling showViewFlipper and onDestroy
    private List<AsyncTask<Void, Void, Object>> mAsyncTasks = new ArrayList<>();

    private PermissionsUtils mPermissions;
    private static final int REQUEST_PERMISSIONS_CODE = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_authenticator);

        PermissionsUtils permissionsUtils = new PermissionsUtils(this);
        if (!permissionsUtils.checkPermissions()) {
            permissionsUtils.requestPermissions(REQUEST_PERMISSIONS_CODE);
        }

        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("XWiki Account");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            builder = new AlertDialog.Builder(AuthenticatorActivity.this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        } else {
            builder = new AlertDialog.Builder(AuthenticatorActivity.this);
        }
        builder.setTitle("XWiki")
                .setIcon(getResources().getDrawable(R.drawable.logo))
                .setMessage("Create XWiki Account to enjoy features like synchronization of contacts and provide credentials for other android apps" )
                .setPositiveButton("CLOSE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        mViewFlipper = findViewById(R.id.view_flipper);
        boolean is_set_sync = getIntent().getBooleanExtra(AuthenticatorActivity.IS_SETTING_SYNC_TYPE, true);
        if (is_set_sync) {
            //just set sync
            showViewFlipper(ViewFlipperLayoutId.SETTING_SYNC);
        } else {
            //add new contact
            //check if there'is already a user, finish and return, keep only one user.
            mAccountManager = AccountManager.get(getApplicationContext());
            Account availableAccounts[] = mAccountManager.getAccountsByType(Constants.ACCOUNT_TYPE);
            if (availableAccounts.length > 0) {
                Toast.makeText(this, "The user already exists!", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
    }
    @Override
    public void onBackPressed() {
        if(mViewFlipper.getDisplayedChild()==ViewFlipperLayoutId.SETTING_IP)
            super.onBackPressed();
        doPreviousNext(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearAsyncTask();
    }

    /**
     * now it's useless because of compile sdk 22
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(mPermissions.areAllRequiredPermissionsGranted(grantResults)){
            settingSyncViewFlipper.syncSettingComplete();
        }else{
            settingSyncViewFlipper.noPermissions();
        }
    }

    private void doPreviousNext(boolean next) {
        int id = mViewFlipper.getDisplayedChild();
        //set animation for view flipper (left right)
        if(next || id == ViewFlipperLayoutId.SETTING_IP){
            mViewFlipper.setInAnimation(AnimationUtils.loadAnimation(this,
                    R.anim.push_left_in));
            mViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this,
                    R.anim.push_left_out));
        }else{
            mViewFlipper.setInAnimation(AnimationUtils.loadAnimation(this,
                    R.anim.push_right_in));
            mViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this,
                    R.anim.push_right_out));
        }
        switch (id) {
            case ViewFlipperLayoutId.SETTING_IP:
                if (settingsIpViewFlipper == null) {
                    settingsIpViewFlipper = new SettingIpViewFlipper(this, mViewFlipper.getChildAt(id));
                }
                if (next) {
                    settingsIpViewFlipper.doNext();
                } else {
                    settingsIpViewFlipper.doPrevious();
                }
                break;
            case ViewFlipperLayoutId.SIGN_IN:
                if (next) {
                    signInViewFlipper.doNext();
                } else {
                    signInViewFlipper.doPrevious();
                }
                break;
            case ViewFlipperLayoutId.SETTING_SYNC:
                if (next) {
                    settingSyncViewFlipper.doNext();
                } else {
                    settingSyncViewFlipper.doPrevious();
                }
                break;
            default:
                break;
        }
    }

    public void doPrevious(View view) {
        doPreviousNext(false);
    }

    public void doNext(View view) {
        doPreviousNext(true);
    }



    public void next(View view) {
        AlertDialog dialog=builder.create();
        dialog.show();
    }
    public void signUp(View view) {
        String url = currentBaseUrl();
        if (url.endsWith("/")) {
            url += "bin/view/XWiki/Registration";
        } else {
            url += "/bin/view/XWiki/Registration";
        }
        Intent intent = IntentUtils.openLink(
            url
        );
        startActivity(intent);
    }
    public interface ViewFlipperLayoutId {
        int SETTING_IP = 0;
        int SIGN_IN = 1;
        int SETTING_SYNC = 2;
    }

    public void showViewFlipper(int id) {
        clearAsyncTask();
        mViewFlipper.setDisplayedChild(id);
        switch (id) {
            case ViewFlipperLayoutId.SETTING_IP:
                if (settingsIpViewFlipper == null) {
                    settingsIpViewFlipper = new SettingIpViewFlipper(this, mViewFlipper.getChildAt(id));
                }
                toolbar.setTitle("XWiki Account");

                break;
            case ViewFlipperLayoutId.SIGN_IN:
                if (signInViewFlipper == null) {
                    signInViewFlipper = new SignInViewFlipper(this, mViewFlipper.getChildAt(id));
                }
                toolbar.setTitle("Sign In");

                break;
            case ViewFlipperLayoutId.SETTING_SYNC:
                if (settingSyncViewFlipper == null) {
                    settingSyncViewFlipper = new SettingSyncViewFlipper(this, mViewFlipper.getChildAt(id));
                }
                toolbar.setTitle("Setting Sync");

                break;
        }
    }


    public void clearOldAccount(){
        //TODO: clear current user url
        //clear SharePreference
        SharedPrefsUtils.removeKeyValue(this, Constants.PACKAGE_LIST);
        SharedPrefsUtils.removeKeyValue(this, Constants.SELECTED_GROUPS);
        SharedPrefsUtils.removeKeyValue(this, Constants.SYNC_TYPE);
    }

    public void finishLogin(Intent intent) {
        Log.d(TAG, "> finishLogin");

        //before add new account, clear old account data.
        clearOldAccount();

        //get values
        String accountName = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
        String accountPassword = intent.getStringExtra(PARAM_USER_PASS);
        String accountServer = intent.getStringExtra(PARAM_USER_SERVER);

        // Creating the account on the device and setting the auth token we got
        // (Not setting the auth token will cause another call to the server to authenticate the user)
        Log.d(TAG, "finishLogin > addAccountExplicitly" + " " + intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE));
        final Account account = new Account(accountName, Constants.ACCOUNT_TYPE);
        mAccountManager.addAccountExplicitly(account, accountPassword, null);
        mAccountManager.setUserData(account, AccountManager.KEY_USERDATA, accountName);
        mAccountManager.setUserData(account, AccountManager.KEY_PASSWORD, accountPassword);
        mAccountManager.setUserData(account, AuthenticatorActivity.PARAM_USER_SERVER, accountServer);

        //grant permission if adding user from the third-party app (UID,PackageName);
        String packaName = getIntent().getStringExtra(PARAM_APP_PACKAGENAME);
        int uid = getIntent().getIntExtra(PARAM_APP_UID, 0);
        Log.d(TAG, packaName + ", " + getPackageName());
        //only if adding account from the third-party apps exclude android.uid.system, this will execute to grant permission and set token
        if (!packaName.contains("android.uid.system")) {
            AppContext.addAuthorizedApp(uid, packaName);
            String authToken = intent.getStringExtra(AccountManager.KEY_AUTHTOKEN);
            if (!TextUtils.isEmpty(authToken)) {
                String authTokenType = getIntent().getStringExtra(KEY_AUTH_TOKEN_TYPE);
                mAccountManager.setAuthToken(account, authTokenType, authToken);
            }
        }

        //return value to AccountManager
        Intent intentReturn = new Intent();
        intentReturn.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
        intentReturn.putExtra(AccountManager.KEY_ACCOUNT_NAME, accountName);
        setAccountAuthenticatorResult(intentReturn.getExtras());
        setResult(RESULT_OK, intentReturn);
        Log.d(TAG, ">" + "finish return");
        // in SettingSyncViewFlipper this activity finish;
    }


    public void showProgress(CharSequence message, final AsyncTask asyncTask) {
        // To avoid repeatedly create
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            return;
        }
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(message);
        dialog.setIndeterminate(true);
        dialog.setCancelable(true);
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                Log.i(TAG, "user cancelling authentication");
                if (asyncTask != null) {
                    asyncTask.cancel(true);
                }
            }
        });
        // We save off the progress dialog in a field so that we can dismiss
        // it later.
        mProgressDialog = dialog;
        mProgressDialog.show();
    }

    /**
     * Hides the progress UI for a lengthy operation.
     */
    public void hideProgress() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    public void putAsyncTask(AsyncTask<Void, Void, Object> asyncTask) {
        mAsyncTasks.add(asyncTask.execute());
    }

    private void clearAsyncTask() {
        for (AsyncTask<Void, Void, Object> asyncTask : mAsyncTasks) {
            if (asyncTask != null && !asyncTask.isCancelled()) {
                asyncTask.cancel(true);
            }
        }
        mAsyncTasks.clear();
    }

    public void hideInputMethod(){
        View view = this.getCurrentFocus();
        if(view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}