package de.qabel.qabelbox.activities;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.LightingColorFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.cocosw.bottomsheet.BottomSheet;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.qabel.core.config.Contact;
import de.qabel.core.config.Identity;
import de.qabel.desktop.repository.ContactRepository;
import de.qabel.desktop.repository.IdentityRepository;
import de.qabel.desktop.repository.exception.EntityNotFoundExcepion;
import de.qabel.desktop.repository.exception.PersistenceException;
import de.qabel.qabelbox.BuildConfig;
import de.qabel.qabelbox.QabelBoxApplication;
import de.qabel.qabelbox.R;
import de.qabel.qabelbox.adapter.FilesAdapter;
import de.qabel.qabelbox.chat.ChatServer;
import de.qabel.qabelbox.chat.ShareHelper;
import de.qabel.qabelbox.communication.VolumeFileTransferHelper;
import de.qabel.qabelbox.communication.connection.ConnectivityManager;
import de.qabel.qabelbox.config.AppPreference;
import de.qabel.qabelbox.config.QabelSchema;
import de.qabel.qabelbox.dagger.HasComponent;
import de.qabel.qabelbox.dagger.components.MainActivityComponent;
import de.qabel.qabelbox.dagger.modules.ActivityModule;
import de.qabel.qabelbox.dagger.modules.MainActivityModule;
import de.qabel.qabelbox.dialogs.SelectIdentityForUploadDialog;
import de.qabel.qabelbox.exceptions.QblStorageException;
import de.qabel.qabelbox.fragments.AboutLicencesFragment;
import de.qabel.qabelbox.fragments.BaseFragment;
import de.qabel.qabelbox.fragments.ContactBaseFragment;
import de.qabel.qabelbox.fragments.ContactChatFragment;
import de.qabel.qabelbox.fragments.ContactFragment;
import de.qabel.qabelbox.fragments.CreateIdentityMainFragment;
import de.qabel.qabelbox.fragments.FilesFragment;
import de.qabel.qabelbox.fragments.FilesFragmentBase;
import de.qabel.qabelbox.fragments.HelpMainFragment;
import de.qabel.qabelbox.fragments.IdentitiesFragment;
import de.qabel.qabelbox.fragments.QRcodeFragment;
import de.qabel.qabelbox.fragments.SelectUploadFolderFragment;
import de.qabel.qabelbox.helper.AccountHelper;
import de.qabel.qabelbox.helper.CacheFileHelper;
import de.qabel.qabelbox.helper.ExternalApps;
import de.qabel.qabelbox.helper.FileHelper;
import de.qabel.qabelbox.helper.PreferencesHelper;
import de.qabel.qabelbox.helper.Sanity;
import de.qabel.qabelbox.helper.UIHelper;
import de.qabel.qabelbox.providers.BoxProvider;
import de.qabel.qabelbox.services.LocalQabelService;
import de.qabel.qabelbox.storage.BoxExternalFile;
import de.qabel.qabelbox.storage.BoxFile;
import de.qabel.qabelbox.storage.BoxFolder;
import de.qabel.qabelbox.storage.BoxNavigation;
import de.qabel.qabelbox.storage.BoxObject;
import de.qabel.qabelbox.storage.BoxVolume;
import de.qabel.qabelbox.views.DrawerNavigationView;
import de.qabel.qabelbox.views.DrawerNavigationViewHolder;

public class MainActivity extends CrashReportingActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        FilesFragment.FilesListListener,
        IdentitiesFragment.IdentityListListener,
        HasComponent<MainActivityComponent> {

    public static final String TAG_CONTACT_CHAT_FRAGMENT = "TAG_CONTACT_CHAT_FRAGMENT";

    private static final int REQUEST_CODE_CHOOSE_EXPORT = 14;
    private static final int REQUEST_CREATE_IDENTITY = 16;
    private static final int REQUEST_SETTINGS = 17;
    public static final int REQUEST_EXPORT_IDENTITY = 18;
    public static final int REQUEST_EXTERN_VIEWER_APP = 19;
    public static final int REQUEST_EXTERN_SHARE_APP = 20;

    public static final String TAG_FILES_FRAGMENT = "TAG_FILES_FRAGMENT";
    public static final String TAG_CONTACT_LIST_FRAGMENT = "TAG_CONTACT_LIST_FRAGMENT";
    static final String TAG_ABOUT_FRAGMENT = "TAG_ABOUT_FRAGMENT";
    static final String TAG_HELP_FRAGMENT = "TAG_HELP_FRAGMENT";
    static final String TAG_MANAGE_IDENTITIES_FRAGMENT = "TAG_MANAGE_IDENTITIES_FRAGMENT";
    static final String TAG_FILES_SHARE_INTO_APP_FRAGMENT = "TAG_FILES_SHARE_INTO_APP_FRAGMENT";
    private static final String TAG = "BoxMainActivity";

    private static final int REQUEST_CODE_UPLOAD_FILE = 12;

    public static final int REQUEST_EXPORT_IDENTITY_AS_CONTACT = 19;

    private static final String FALLBACK_MIMETYPE = "application/octet-stream";
    private static final int NAV_GROUP_IDENTITIES = 1;
    private static final int NAV_GROUP_IDENTITY_ACTIONS = 2;
    private static final int REQUEST_CODE_OPEN = 21;
    private static final int REQUEST_CODE_DELETE_FILE = 22;

    // Intent extra to specify if the files fragment should be started
    // Defaults to true and is used in tests to shortcut the activity creation
    public static final String START_FILES_FRAGMENT = "START_FILES_FRAGMENT";
    public static final String START_CONTACTS_FRAGMENT = "START_CONTACTS_FRAGMENT";
    public static final String ACTIVE_IDENTITY = "ACTIVE_IDENTITY";
    public static final String ACTIVE_CONTACT = "ACTIVE_CONTACT";

    public BoxVolume boxVolume;
    public ActionBarDrawerToggle toggle;
    private BoxProvider provider;
    @BindView(R.id.drawer_layout)
    DrawerLayout drawer;
    @BindView(R.id.nav_view)
    DrawerNavigationView navigationView;
    @BindView(R.id.fab)
    public FloatingActionButton fab;
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.app_bap_main)
    public View appBarMain;
    public FilesFragment filesFragment;
    private MainActivity self;
    private boolean identityMenuExpanded;

    // Used to save the document uri that should exported while waiting for the result
    // of the create document intent.
    private Uri exportUri;
    public LocalQabelService mService;
    private ServiceConnection mServiceConnection;
    private SelectUploadFolderFragment shareFragment;
    public ChatServer chatServer;
    private ContactFragment contactFragment;
    private LightingColorFilter mDrawerIndicatorTintFilter;

    @Inject
    ConnectivityManager connectivityManager;

    private DrawerNavigationViewHolder drawerHolder;

    @Inject
    Identity activeIdentity;

    @Inject
    IdentityRepository identityRepository;
    @Inject
    public ContactRepository contactRepository;

    @Inject
    SharedPreferences sharedPreferences;

    private MainActivityComponent component;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "On Activity result");
        final Uri uri;
        if (requestCode == REQUEST_EXTERN_VIEWER_APP) {
            Log.d(TAG, "result from extern app " + resultCode);
        }
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_SETTINGS) {
                //add functions if ui need refresh after settings changed
            }
            if (requestCode == REQUEST_CREATE_IDENTITY) {
                if (data != null && data.hasExtra(CreateIdentityActivity.P_IDENTITY)) {
                    Identity identity = (Identity) data.getSerializableExtra(CreateIdentityActivity.P_IDENTITY);
                    if (identity == null) {
                        Log.w(TAG, "Recieved data with identity null");
                    }
                    addIdentity(identity);
                    return;
                }
            }
            if (data != null) {
                if (requestCode == REQUEST_CODE_OPEN) {
                    uri = data.getData();
                    Log.i(TAG, "Uri: " + uri.toString());
                    Intent viewIntent = new Intent();
                    String type = URLConnection.guessContentTypeFromName(uri.toString());
                    Log.i(TAG, "Mime type: " + type);
                    viewIntent.setDataAndType(uri, type);
                    viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(viewIntent, "Open with"));
                    return;
                }
                if (requestCode == REQUEST_CODE_UPLOAD_FILE) {
                    uri = data.getData();

                    if (filesFragment != null) {
                        BoxNavigation boxNavigation = filesFragment.getBoxNavigation();
                        if (boxNavigation != null) {
                            String path = boxNavigation.getPath();
                            VolumeFileTransferHelper.upload(self, uri, boxNavigation, boxVolume);
                        }
                    }
                    return;
                }
                if (requestCode == REQUEST_CODE_DELETE_FILE) {
                    uri = data.getData();
                    Log.i(TAG, "Deleting file: " + uri.toString());
                    new AsyncTask<Uri, Void, Boolean>() {

                        @Override
                        protected Boolean doInBackground(Uri... params) {

                            return DocumentsContract.deleteDocument(getContentResolver(), params[0]);
                        }
                    }.execute(uri);
                    return;
                }
                if (requestCode == REQUEST_CODE_CHOOSE_EXPORT) {
                    uri = data.getData();
                    Log.i(TAG, "Export uri chosen: " + uri.toString());
                    new AsyncTask<Void, Void, Void>() {

                        @Override
                        protected Void doInBackground(Void... params) {

                            try {
                                InputStream inputStream = getContentResolver().openInputStream(exportUri);
                                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                                if (inputStream == null || outputStream == null) {
                                    Log.e(TAG, "could not open streams");
                                    return null;
                                }
                                IOUtils.copy(inputStream, outputStream);
                                inputStream.close();
                                outputStream.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Failed to export file", e);
                            }
                            return null;
                        }
                    }.execute();
                    return;
                }
            }
        }
        Log.d(TAG, "super.onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        component = getApplicationComponent()
                .plus(new ActivityModule(this))
                .plus(new MainActivityModule(this));
        component.inject(this);
        self = this;
        Log.d(TAG, "onCreate " + this.hashCode());
        Intent serviceIntent = new Intent(this, LocalQabelService.class);
        mServiceConnection = getServiceConnection();
        if (Sanity.startWizardActivities(this)) {
            Log.d(TAG, "started wizard dialog");
            return;
        }
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        View header = navigationView.getHeaderView(0);
        drawerHolder = new DrawerNavigationViewHolder(header);

        setSupportActionBar(toolbar);
        mDrawerIndicatorTintFilter = new LightingColorFilter(0, getResources().getColor(R.color.tintDrawerIndicator));


        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        installConnectivityManager();
        addBackStackListener();

        setupAccount();
    }

    private void setupAccount() {
        AccountHelper.createSyncAccount(getApplicationContext());
        AccountHelper.configurePeriodicPolling();
    }

    public void installConnectivityManager(ConnectivityManager connectivityManager) {
        this.connectivityManager = connectivityManager;
        installConnectivityManager();
    }

    public void installConnectivityManager() {
        connectivityManager.setListener(new ConnectivityManager.ConnectivityListener() {

            private AlertDialog offlineIndicator;

            @Override
            public void handleConnectionLost() {
                if (offlineIndicator == null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(self);
                    builder.setTitle(R.string.no_connection)
                            .setIcon(R.drawable.information)
                            .setNegativeButton(R.string.close_app, (dialog, id) -> {
                                self.finishAffinity();
                            })
                            .setPositiveButton(R.string.retry_action, null);
                    offlineIndicator = builder.create();
                    offlineIndicator.setCancelable(false);
                    offlineIndicator.show();
                    offlineIndicator.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                        if (connectivityManager.isConnected()) {
                            offlineIndicator.dismiss();
                            offlineIndicator = null;
                        }
                    });
                } else {
                    offlineIndicator.show();
                }
            }

            @Override
            public void handleConnectionEtablished() {
                if (offlineIndicator != null && offlineIndicator.isShowing()) {
                    offlineIndicator.dismiss();
                    offlineIndicator = null;
                }
            }

            @Override
            public void onDestroy() {
                if (this.offlineIndicator != null) {
                    this.offlineIndicator.dismiss();
                }
            }
        });
    }

    private void handleMainFragmentChange() {
        // Set FAB visibility according to currently visible fragment
        Fragment activeFragment = getFragmentManager().findFragmentById(R.id.fragment_container);

        if (activeFragment instanceof BaseFragment) {
            BaseFragment fragment = ((BaseFragment) activeFragment);
            toolbar.setTitle(fragment.getTitle());
            if (fragment.isFabNeeded()) {
                fab.show();
            } else {
                fab.hide();
            }
            if (!fragment.supportSubtitle()) {
                toolbar.setSubtitle(null);
            } else {
                fragment.updateSubtitle();
            }
        }
        //check if navigation drawer need to reset
        if (getFragmentManager().getBackStackEntryCount() == 0 || (activeFragment instanceof BaseFragment) && !((BaseFragment) activeFragment).supportBackButton()) {
            if (!(activeFragment instanceof SelectUploadFolderFragment)) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                toggle.setDrawerIndicatorEnabled(true);
            }
        }
    }

    private void addBackStackListener() {

        getFragmentManager().addOnBackStackChangedListener(() -> handleMainFragmentChange());
    }

    @NonNull
    private ServiceConnection getServiceConnection() {

        return new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                LocalQabelService.LocalBinder binder = (LocalQabelService.LocalBinder) service;
                mService = binder.getService();
                QabelBoxApplication.getInstance().serviceCreatedOutside(mService);
                onLocalServiceConnected(getIntent());
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

                mService = null;
            }
        };
    }


    private void onLocalServiceConnected(Intent intent) {

        Log.d(TAG, "LocalQabelService connected");
        provider = ((QabelBoxApplication) getApplication()).getProvider();
        Log.i(TAG, "Provider: " + provider);
        provider.setLocalService(mService);
        initDrawer();
        handleIntent(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();

        Log.i(TAG, "Intent action: " + action);

        // Checks if a fragment should be launched
        boolean startFilesFragment = intent.getBooleanExtra(START_FILES_FRAGMENT, true);
        boolean startContactsFragment = intent.getBooleanExtra(START_CONTACTS_FRAGMENT, false);
        String activeContact = intent.getStringExtra(ACTIVE_CONTACT);
        refreshFilesBrowser(activeIdentity);
        if (type != null && intent.getAction() != null) {
            String scheme = intent.getScheme();

            switch (intent.getAction()) {
                case Intent.ACTION_VIEW:
                    if (scheme.compareTo(ContentResolver.SCHEME_FILE) == 0 || scheme.compareTo(ContentResolver.SCHEME_CONTENT) == 0) {
                        handleActionViewResolver(intent);

                    }
                    break;
                case Intent.ACTION_SEND:
                    Log.i(TAG, "Action send in main activity");
                    Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (imageUri != null) {
                        ArrayList<Uri> data = new ArrayList<Uri>();
                        data.add(imageUri);
                        shareIntoApp(data);
                    }

                    break;
                case Intent.ACTION_SEND_MULTIPLE:
                    Log.i(TAG, "Action send multiple in main activity");
                    ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (imageUris != null && imageUris.size() > 0) {
                        shareIntoApp(imageUris);
                    }
                    break;
                default:
                    if (startContactsFragment) {
                        selectContactsFragment(activeContact);
                    } else if (startFilesFragment) {
                        initAndSelectFilesFragment();
                    }
                    break;
            }
        } else {
            if (startContactsFragment) {
                selectContactsFragment(activeContact);
            } else if (startFilesFragment) {
                initAndSelectFilesFragment();
            }
        }
    }

    public Identity getActiveIdentity() {
        return activeIdentity;
    }

    public LocalQabelService getService() {
        return mService;
    }

    /**
     * handle open view resolver to open the correct import tool
     *
     * @param intent
     */
    private void handleActionViewResolver(Intent intent) {
        final Uri uri = intent.getData();
        String realPath;
        //check if schema content, then get real path and filename
        if (ContentResolver.SCHEME_CONTENT.compareTo(intent.getScheme()) == 0) {
            realPath = FileHelper.getRealPathFromURI(self, uri);
            if (realPath == null) {
                realPath = uri.toString();
                Log.d(TAG, "can't get real path. try to use uri " + realPath);
            }
        } else {
            //schema is file
            realPath = intent.getDataString();
        }
        String extension = FilenameUtils.getExtension(realPath);

        //grep spaces from extension like test.qco (1)
        if (extension != null && extension.length() > 0) {
            extension = extension.split(" ")[0];
            if (QabelSchema.FILE_SUFFIX_CONTACT.equals(extension)) {
                new ContactBaseFragment().importContactFromUri(self, uri);
            } else if (QabelSchema.FILE_SUFFIX_IDENTITY.equals(extension)) {
                if (
                        new CreateIdentityMainFragment().importIdentity(self, intent)) {
                    UIHelper.showDialogMessage(self, R.string.infos, R.string.idenity_imported);
                }
            } else {
                UIHelper.showDialogMessage(this, R.string.infos, R.string.cant_import_file_type_is_unknown);
            }
        }
    }

    private void initAndSelectFilesFragment() {
        initFilesFragment();
        selectFilesFragment();
    }

    public void refreshFilesBrowser(Identity activeIdentity) {
        try {
            initBoxVolume(activeIdentity);
            initFilesFragment();
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not init box volume in MainActivity", e);
        }
    }

    private void shareIntoApp(final ArrayList<Uri> data) {

        fab.hide();

        boolean identities = false;
        try {
            identities = identityRepository.findAll().getIdentities().size() > 1;
        } catch (PersistenceException ignored) {
        }
        if (identities) {
            new SelectIdentityForUploadDialog(self, new SelectIdentityForUploadDialog.Result() {
                @Override
                public void onCancel() {

                    UIHelper.showDialogMessage(self, R.string.dialog_headline_warning, R.string.share_into_app_canceled);
                    onBackPressed();
                }

                @Override
                public void onIdentitySelected(Identity identity) {

                    changeActiveIdentity(identity);

                    shareIdentitySelected(data, identity);
                }
            });
        } else {
            changeActiveIdentity(getActiveIdentity());
            shareIdentitySelected(data, getActiveIdentity());
        }
    }

    private void shareIdentitySelected(final ArrayList<Uri> data, Identity activeIdentity) {

        toggle.setDrawerIndicatorEnabled(false);
        shareFragment = SelectUploadFolderFragment.newInstance(boxVolume, data, activeIdentity);
        getFragmentManager().beginTransaction()
                .add(R.id.fragment_container,
                        shareFragment, TAG_FILES_SHARE_INTO_APP_FRAGMENT)
                .addToBackStack(null)
                .commit();
    }

    private void initBoxVolume(Identity activeIdentity) {

        boxVolume = provider.getVolumeForRoot(
                activeIdentity.getEcPublicKey().getReadableKeyIdentifier(),
                VolumeFileTransferHelper.getPrefixFromIdentity(activeIdentity));
    }

    @OnClick(R.id.fab)
    public void floatingActionButtonClick() {
        Fragment activeFragment = getFragmentManager().findFragmentById(R.id.fragment_container);
        String activeFragmentTag = activeFragment.getTag();
        if (activeFragment instanceof BaseFragment) {
            BaseFragment bf = (BaseFragment) activeFragment;
            //call fab action in basefragment. if fragment handled this, we are done
            if (bf.handleFABAction()) {
                return;
            }
        }
        switch (activeFragmentTag) {
            case TAG_FILES_FRAGMENT:
                filesFragmentBottomSheet();
                break;

            case TAG_MANAGE_IDENTITIES_FRAGMENT:
                selectAddIdentityFragment();
                break;

            default:
                Log.e(TAG, "Unknown FAB action for fragment tag: " + activeFragmentTag);
        }
    }

    private void filesFragmentBottomSheet() {

        new BottomSheet.Builder(self).sheet(R.menu.bottom_sheet_files_add)
                .grid()
                .listener((dialog, which) -> {
                    switch (which) {
                        case R.id.create_folder:
                            newFolderDialog();
                            break;
                        case R.id.upload_file:
                            Intent intentOpen = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intentOpen.addCategory(Intent.CATEGORY_OPENABLE);
                            intentOpen.setType("*/*");
                            startActivityForResult(intentOpen, REQUEST_CODE_UPLOAD_FILE);
                            break;
                    }
                }).show();
    }

    private void newFolderDialog() {

        UIHelper.showEditTextDialog(this, R.string.add_folder_header, R.string.add_folder_name,
                R.string.ok, R.string.cancel, (dialog, which, editText) -> {

                    UIHelper.hideKeyboard(self, editText);
                    String newFolderName = editText.getText().toString();
                    if (!newFolderName.equals("")) {
                        createFolder(newFolderName, filesFragment.getBoxNavigation());
                    }
                }, null);
    }


    protected void filterSheet(BoxObject boxObject, BottomSheet.Builder sheet) {

        if (!(boxObject instanceof BoxFile) || !((BoxFile) boxObject).isShared()) {
            sheet.remove(R.id.unshare);
        }
        if (!(boxObject instanceof BoxFile)) {

            sheet.remove(R.id.unshare);
        }
        if (boxObject instanceof BoxExternalFile) {
            sheet.remove(R.id.fordward);
        }
        if (!(boxObject instanceof BoxFile)) {
            sheet.remove(R.id.open);
            sheet.remove(R.id.export);
            sheet.remove(R.id.share);
            sheet.remove(R.id.fordward);
        }
    }

    /**
     * open system show file dialog
     *
     * @param boxObject
     */

    public void showFile(BoxObject boxObject) {

        Uri uri = VolumeFileTransferHelper.getUri(boxObject, boxVolume, filesFragment.getBoxNavigation());
        String type = getMimeType(uri);
        Log.v(TAG, "Mime type: " + type);
        Log.v(TAG, "Uri: " + uri.toString() + " " + uri.toString().length());

        //check if file type is image
        if (type != null && type.indexOf("image") == 0) {
            Intent intent = new Intent(self, ImageViewerActivity.class);
            intent.putExtra(ImageViewerActivity.P_URI, uri);
            intent.putExtra(ImageViewerActivity.P_TYPE, type);
            startActivity(intent);
            return;
        }

        Intent viewIntent = new Intent();
        viewIntent.setDataAndType(uri, type);
        //check if file type is video
        if (type != null && type.indexOf("video") == 0) {
            viewIntent.setAction(Intent.ACTION_VIEW);
        }
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(viewIntent, "Open with"), REQUEST_EXTERN_VIEWER_APP);
    }

    //@todo move outside
    private String getMimeType(BoxObject boxObject) {

        return getMimeType(VolumeFileTransferHelper.getUri(boxObject, boxVolume, filesFragment.getBoxNavigation()));
    }

    //@todo move outside
    private String getMimeType(Uri uri) {

        return URLConnection.guessContentTypeFromName(uri.toString());
    }


    @Override
    public void onBackPressed() {

        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {

            Fragment activeFragment = getFragmentManager().findFragmentById(R.id.fragment_container);
            if (activeFragment == null) {
                super.onBackPressed();
                return;
            }
            if (activeFragment.getTag() == null) {
                getFragmentManager().popBackStack();
            } else {
                switch (activeFragment.getTag()) {
                    case TAG_FILES_SHARE_INTO_APP_FRAGMENT:
                        if (!shareFragment.handleBackPressed() && !shareFragment.browseToParent()) {
                            UIHelper.showDialogMessage(self, R.string.dialog_headline_warning,
                                    R.string.share_in_app_go_back_without_upload, R.string.yes, R.string.no, (dialog, which) -> {
                                        getFragmentManager().popBackStack();
                                    }, null);
                        }
                        break;
                    case TAG_FILES_FRAGMENT:
                        toggle.setDrawerIndicatorEnabled(true);
                        if (!filesFragment.handleBackPressed() && !filesFragment.browseToParent()) {
                            finishAffinity();
                        }
                        break;
                    case TAG_CONTACT_LIST_FRAGMENT:
                        super.onBackPressed();
                        break;
                    default:
                        if (getFragmentManager().getBackStackEntryCount() > 0) {
                            getFragmentManager().popBackStack();
                        } else {
                            finishAffinity();
                        }
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.ab_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_infos) {
            String text = getString(R.string.dummy_infos_text);
            UIHelper.showDialogMessage(self, R.string.dialog_headline_info, text);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        if (id == R.id.nav_tellafriend) {
            ShareHelper.tellAFriend(this);
        }
        if (id == R.id.nav_contacts) {
            selectContactsFragment();
        } else if (id == R.id.nav_browse) {
            selectFilesFragment();
        } else if (id == R.id.nav_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivityForResult(intent, REQUEST_SETTINGS);
        } else if (id == R.id.nav_about) {
            selectAboutFragment();
        } else if (id == R.id.nav_help) {
            selectHelpFragment();
        } else if (id == R.id.nav_logout) {
            performLogout();
        }
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    //@todo move outside
    public void createFolder(final String name, final BoxNavigation boxNavigation) {

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    boxNavigation.createFolder(name);
                    boxNavigation.commit();
                } catch (QblStorageException e) {
                    Log.e(TAG, "Failed creating folder " + name, e);
                }
                return null;
            }

            @Override
            protected void onCancelled() {
                filesFragment.setIsLoading(false);
                showAbortMessage();
            }

            @Override
            protected void onPreExecute() {
                filesFragment.setIsLoading(true);
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                onDoRefresh(filesFragment);
            }
        }.execute();
    }

    public void selectIdentity(Identity identity) {
        changeActiveIdentity(identity);
    }

    public void addIdentity(Identity identity) {

        mService.addIdentity(identity);
        changeActiveIdentity(identity);
        provider.notifyRootsUpdated();
        Snackbar.make(appBarMain, "Added identity: " + identity.getAlias(), Snackbar.LENGTH_LONG)
                .show();
        selectFilesFragment();
    }

    public void changeActiveIdentity(Identity identity) {
        PreferencesHelper.setLastActiveIdentityID(sharedPreferences, identity.getKeyIdentifier());
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(ACTIVE_IDENTITY, identity.getKeyIdentifier());
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        startActivity(intent);

    }

    //@todo move this to filesfragment
    private void initFilesFragment() {

        if (filesFragment != null) {
            getFragmentManager().beginTransaction().remove(filesFragment)
                    .commitAllowingStateLoss();
        }
        filesFragment = FilesFragment.newInstance(boxVolume);
        filesFragment.setOnItemClickListener(new FilesAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {

                final BoxObject boxObject = filesFragment.getFilesAdapter().get(position);
                if (boxObject != null) {
                    if (boxObject instanceof BoxFolder) {
                        filesFragment.browseTo(((BoxFolder) boxObject));
                    } else if (boxObject instanceof BoxFile) {
                        // Open
                        showFile(boxObject);
                    }
                }
            }

            @Override
            public void onItemLockClick(View view, final int position) {

                final BoxObject boxObject = filesFragment.getFilesAdapter().get(position);
                if (boxObject.name.equals(BoxFolder.RECEIVED_SHARE_NAME)) {
                    return;
                }
                BottomSheet.Builder sheet = new BottomSheet.Builder(self)
                        .title(boxObject.name)
                        .icon((boxObject instanceof BoxFolder ? R.drawable.folder : R.drawable.file))
                        .sheet(R.menu.bottom_sheet_files)
                        .listener((dialog, which) -> {

                            switch (which) {
                                case R.id.open:
                                    ExternalApps.openExternApp(self, VolumeFileTransferHelper.getUri(boxObject, boxVolume, filesFragment.getBoxNavigation()), getMimeType(boxObject), Intent.ACTION_VIEW);
                                    break;
                                case R.id.share:
                                    ExternalApps.share(self, VolumeFileTransferHelper.getUri(boxObject, boxVolume, filesFragment.getBoxNavigation()), getMimeType(boxObject));
                                    break;
                                case R.id.fordward:
                                    ShareHelper.shareToQabelUser(self, mService, boxObject);
                                    break;
                                case R.id.delete:
                                    filesFragment.delete(boxObject);
                                    break;
                                case R.id.unshare:
                                    filesFragment.unshare((BoxFile) boxObject);
                                    break;
                                case R.id.export:
                                    // Export handled in the MainActivity
                                    if (boxObject instanceof BoxFolder) {
                                        Toast.makeText(self, R.string.folder_export_not_implemented,
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        onExport(filesFragment.getBoxNavigation(), boxObject);
                                    }
                                    break;
                            }
                        });
                filterSheet(boxObject, sheet);
                sheet.show();
            }
        });
    }


    @Override
    public void onScrolledToBottom(boolean scrolledToBottom) {

        if (scrolledToBottom) {
            fab.hide();
        } else {
            Fragment activeFragment = getFragmentManager().findFragmentById(R.id.fragment_container);
            if (activeFragment != null) {
                if (activeFragment instanceof BaseFragment && ((BaseFragment) activeFragment).handleFABAction()) {
                    fab.show();
                }
            }
        }
    }

    @Override
    public void deleteIdentity(Identity identity) {

        provider.notifyRootsUpdated();
        mService.deleteIdentity(identity);
        if (mService.getIdentities().getIdentities().size() == 0) {
            UIHelper.showDialogMessage(this, R.string.dialog_headline_info,
                    R.string.last_identity_delete_create_new, (dialog, which) -> {

                        selectAddIdentityFragment();
                    });
        } else {
            try {
                changeActiveIdentity(identityRepository.findAll().getIdentities().iterator().next());
            } catch (PersistenceException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void modifyIdentity(Identity identity) {

        provider.notifyRootsUpdated();
        mService.modifyIdentity(identity);
        drawerHolder.textViewSelectedIdentity.setText(getActiveIdentity().getAlias());
    }

    /**
     * Handle an export request sent from the FilesFragment
     */
    //@todo move outside
    public void onExport(BoxNavigation boxNavigation, BoxObject boxObject) {

        String path = boxNavigation.getPath(boxObject);
        String documentId = boxVolume.getDocumentId(path);
        Uri uri = DocumentsContract.buildDocumentUri(
                BuildConfig.APPLICATION_ID + BoxProvider.AUTHORITY, documentId);
        exportUri = uri;

        // Chose a suitable place for this file, determined by the mime type
        String type = getMimeType(uri);
        if (type == null) {
            type = FALLBACK_MIMETYPE;
        }
        Log.i(TAG, "Mime type: " + type);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, boxObject.name);
        intent.setDataAndType(uri, type);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // the activity result will handle the actual file copy
        startActivityForResult(intent, REQUEST_CODE_CHOOSE_EXPORT);
    }

    @Override
    protected void onDestroy() {

        if (mServiceConnection != null && mService != null) {
            unbindService(mServiceConnection);
        }
        if (isTaskRoot()) {
            new CacheFileHelper().freeCacheAsynchron(QabelBoxApplication.getInstance().getApplicationContext());
        }

        if (connectivityManager != null) {
            connectivityManager.onDestroy();
        }

        super.onDestroy();
    }

    @Override
    public void onDoRefresh(final FilesFragmentBase filesFragment) {
        filesFragment.refresh();
    }

    private void setDrawerLocked(boolean locked) {

        if (locked) {
            drawer.setDrawerListener(null);
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            toggle.setDrawerIndicatorEnabled(false);
        } else {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            toggle.setDrawerIndicatorEnabled(true);
        }
    }


    public void selectIdentityLayoutClick() {

        if (identityMenuExpanded) {
            drawerHolder.imageViewExpandIdentity.setImageResource(R.drawable.menu_down);
            drawerHolder.imageViewExpandIdentity.setColorFilter(mDrawerIndicatorTintFilter);
            navigationView.getMenu().clear();
            navigationView.inflateMenu(R.menu.activity_main_drawer);
            identityMenuExpanded = false;
        } else {
            drawerHolder.imageViewExpandIdentity.setImageResource(R.drawable.menu_up);
            drawerHolder.imageViewExpandIdentity.setColorFilter(mDrawerIndicatorTintFilter);
            navigationView.getMenu().clear();
            List<Identity> identityList;
            try {
                identityList = new ArrayList<>(
                        identityRepository.findAll().getIdentities());
            } catch (PersistenceException e) {
                Log.e(TAG, "Could not list identities", e);
                identityList = new ArrayList<>();
            }
            Collections.sort(identityList, (lhs, rhs) -> lhs.getAlias().compareTo(rhs.getAlias()));
            for (final Identity identity : identityList) {
                navigationView.getMenu()
                        .add(NAV_GROUP_IDENTITIES, Menu.NONE, Menu.NONE, identity.getAlias())
                        .setIcon(R.drawable.account)
                        .setOnMenuItemClickListener(item -> {
                            drawer.closeDrawer(GravityCompat.START);
                            selectIdentity(identity);
                            return true;
                        });
            }
            navigationView.getMenu()
                    .add(NAV_GROUP_IDENTITY_ACTIONS, Menu.NONE, Menu.NONE, R.string.add_identity)
                    .setIcon(R.drawable.plus_circle)
                    .setOnMenuItemClickListener(item -> {
                        drawer.closeDrawer(GravityCompat.START);
                        selectAddIdentityFragment();
                        return true;
                    });
            navigationView.getMenu()
                    .add(NAV_GROUP_IDENTITY_ACTIONS, Menu.NONE, Menu.NONE, R.string.manage_identities)
                    .setIcon(R.drawable.settings)
                    .setOnMenuItemClickListener(item -> {
                        drawer.closeDrawer(GravityCompat.START);
                        selectManageIdentitiesFragment();
                        return true;
                    });
            identityMenuExpanded = true;
        }
    }

    private void initDrawer() {
        toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        drawerHolder.qabelLogo.setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.START);
            showQRCode(self, getActiveIdentity());
        });

        drawerHolder.selectIdentityLayout.setOnClickListener(
                (View v) -> selectIdentityLayoutClick());

        setDrawerLocked(false);
        navigationView.setNavigationItemSelectedListener(this);

        // Map QR-Code indent to alias textview in nav_header_main
        String boxName = new AppPreference(self).getAccountName();
        drawerHolder.textViewBoxAccountName.setText(boxName != null ? boxName : getString(R.string.app_name));

        drawerHolder.imageViewExpandIdentity.setColorFilter(mDrawerIndicatorTintFilter);

        drawerHolder.textViewSelectedIdentity.setText(activeIdentity.getAlias());
        drawer.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {

            }

            @Override
            public void onDrawerOpened(View drawerView) {

            }

            @Override
            public void onDrawerClosed(View drawerView) {
                navigationView.getMenu().clear();
                navigationView.inflateMenu(R.menu.activity_main_drawer);
                drawerHolder.imageViewExpandIdentity.setImageResource(R.drawable.menu_down);
                identityMenuExpanded = false;
            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });
    }

    private void performLogout() {
        new AppPreference(this).logout();
        swapWithCreateAccountActivity();
    }

    private void swapWithCreateAccountActivity() {
        Intent intent = new Intent(self, CreateAccountActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        startActivity(intent);
        finish();
    }

    public static void showQRCode(MainActivity activity, Identity identity) {
        activity.getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, QRcodeFragment.newInstance(identity), null)
                .addToBackStack(null)
                .commit();
    }

    private void selectAddIdentityFragment() {

        Intent i = new Intent(self, CreateIdentityActivity.class);
        int identitiesCount = mService.getIdentities().getIdentities().size();
        i.putExtra(CreateIdentityActivity.FIRST_RUN, identitiesCount == 0 ? true : false);

        if (identitiesCount == 0) {
            finish();
            self.startActivity(i);
        } else {
            self.startActivityForResult(i, REQUEST_CREATE_IDENTITY);
        }
    }

    private void showAbortMessage() {
        Toast.makeText(self, R.string.aborted,
                Toast.LENGTH_SHORT).show();
    }

    /*
        FRAGMENT SELECTION METHODS
    */
    private void selectManageIdentitiesFragment() {
        showMainFragment(IdentitiesFragment.newInstance(mService.getIdentities()),
                TAG_MANAGE_IDENTITIES_FRAGMENT);
    }

    private void selectContactsFragment(String activeContact) {
        if (activeContact == null) {
            selectContactsFragment();
            return;
        }
        try {
            Contact contact = contactRepository.findByKeyId(activeIdentity, activeContact);
            Log.d(TAG, "Selecting chat with  contact " + contact.getAlias());
            getFragmentManager().beginTransaction().add(R.id.fragment_container,
                    ContactChatFragment.newInstance(contact),
                    MainActivity.TAG_CONTACT_CHAT_FRAGMENT)
                    .addToBackStack(MainActivity.TAG_CONTACT_CHAT_FRAGMENT).commit();
        } catch (EntityNotFoundExcepion entityNotFoundExcepion) {
            Log.w(TAG, "Could not find contact " + activeContact);
            selectContactsFragment();
        }
    }

    private void selectContactsFragment() {
        contactFragment = new ContactFragment();
        showMainFragment(contactFragment, TAG_CONTACT_LIST_FRAGMENT);
    }

    private void selectHelpFragment() {
        showMainFragment(new HelpMainFragment(), TAG_HELP_FRAGMENT);
    }

    private void selectAboutFragment() {
        showMainFragment(AboutLicencesFragment.newInstance(), TAG_ABOUT_FRAGMENT);
    }

    private void selectFilesFragment() {
        filesFragment.navigateBackToRoot();
        filesFragment.setIsLoading(false);
        showMainFragment(filesFragment, TAG_FILES_FRAGMENT);
    }

    private void showMainFragment(Fragment fragment, String tag) {
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment, tag).commit();
        try {
            while (getFragmentManager().executePendingTransactions()) {
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Error waiting for fragment change", e);
        }
        handleMainFragmentChange();
    }

    @Override
    public MainActivityComponent getComponent() {
        return component;
    }
}
