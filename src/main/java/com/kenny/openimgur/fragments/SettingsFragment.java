package com.kenny.openimgur.fragments;

import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;

import com.kenny.openimgur.BuildConfig;
import com.kenny.openimgur.R;
import com.kenny.openimgur.activities.SettingsActivity;
import com.kenny.openimgur.classes.ImgurTheme;
import com.kenny.openimgur.classes.OpenImgurApp;
import com.kenny.openimgur.classes.VideoCache;
import com.kenny.openimgur.util.FileUtil;
import com.kenny.openimgur.util.ImageUtil;
import com.kenny.openimgur.util.LogUtil;
import com.kenny.snackbar.SnackBar;

import java.io.File;
import java.lang.ref.WeakReference;

public class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    private OpenImgurApp mApp;

    private boolean mFirstLaunch = true;

    public static SettingsFragment createInstance() {
        return new SettingsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mApp = OpenImgurApp.getInstance(getActivity());
        addPreferencesFromResource(R.xml.settings);
        bindPreference(findPreference(SettingsActivity.CACHE_SIZE_KEY));
        bindPreference(findPreference(SettingsActivity.THEME_KEY));
        bindPreference(findPreference(SettingsActivity.KEY_CACHE_LOC));
        findPreference(SettingsActivity.CURRENT_CACHE_SIZE_KEY).setOnPreferenceClickListener(this);
        findPreference("licenses").setOnPreferenceClickListener(this);
        findPreference("openSource").setOnPreferenceClickListener(this);
        findPreference(SettingsActivity.KEY_ADB).setOnPreferenceChangeListener(this);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mFirstLaunch = false;
    }

    private void bindPreference(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
        onPreferenceChange(preference, mApp.getPreferences().getString(preference.getKey(), ""));
    }

    @Override
    public void onResume() {
        super.onResume();
        long cacheSize = FileUtil.getDirectorySize(mApp.getImageLoader().getDiskCache().getDirectory());
        findPreference(SettingsActivity.CURRENT_CACHE_SIZE_KEY).setSummary(FileUtil.humanReadableByteCount(cacheSize, false));

        try {
            String version = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
            findPreference("version").setSummary(version);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e("Settings Activity", "Unable to get version summary", e);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object object) {
        if (preference instanceof ListPreference) {
            ListPreference listPreference = (ListPreference) preference;
            int prefIndex = listPreference.findIndexOfValue(object.toString());
            if (prefIndex >= 0) {
                preference.setSummary(listPreference.getEntries()[prefIndex]);
            }

            if (preference.getKey().equals(SettingsActivity.THEME_KEY)) {
                ImgurTheme theme = ImgurTheme.getThemeFromString(listPreference.getEntries()[prefIndex].toString());
                mApp.setImgurTheme(theme);

                if (!mFirstLaunch) {
                    startActivity(SettingsActivity.createIntent(getActivity()));
                    getActivity().finish();
                }
            } else if (preference.getKey().equals(SettingsActivity.KEY_CACHE_LOC)) {
                if (!mFirstLaunch) {
                    new DeleteCacheTask(SettingsFragment.this, object.toString()).execute();
                }
            }

            return true;
        } else if (preference instanceof CheckBoxPreference) {
            // Ignore if its a debug build
            if (!BuildConfig.DEBUG) {
                LogUtil.SHOULD_WRITE_LOGS = (Boolean) object;
                mApp.setAllowLogs((Boolean) object);
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean onPreferenceClick(final Preference preference) {
        if (preference.getKey().equals(SettingsActivity.CURRENT_CACHE_SIZE_KEY)) {
            new PopupDialogViewBuilder(getActivity())
                    .setTitle(R.string.clear_cache)
                    .setMessage(R.string.clear_cache_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.yes, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            new DeleteCacheTask(SettingsFragment.this, null).execute();
                        }
                    }).show();
            return true;
        } else if (preference.getKey().equals("licenses")) {
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setNegativeButton(R.string.dismiss, null).create();
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            WebView webView = new WebView(getActivity());
            webView.loadUrl("file:///android_asset/licenses.html");
            dialog.setView(webView);
            dialog.show();
            return true;
        } else if (preference.getKey().equals("openSource")) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kennyc1012/OpenImgur"));
            if (browserIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivity(browserIntent);
            } else {
                SnackBar.show(getActivity(), R.string.cant_launch_intent);
            }
        }

        return false;
    }

    private static class DeleteCacheTask extends AsyncTask<Void, Void, Long> {
        private WeakReference<SettingsFragment> mFragment;
        private String mCacheDirKey;

        public DeleteCacheTask(SettingsFragment fragment, String cacheDirKey) {
            mFragment = new WeakReference<>(fragment);
            mCacheDirKey = cacheDirKey;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mFragment.get().getFragmentManager().beginTransaction().add(LoadingDialogFragment.createInstance(R.string.one_moment, false), "loading").commit();
        }

        @Override
        protected Long doInBackground(Void... voids) {
            SettingsFragment frag = mFragment.get();
            frag.mApp.deleteAllCache();

            if (!TextUtils.isEmpty(mCacheDirKey)) {
                File dir = ImageUtil.getCacheDirectory(frag.getActivity(), mCacheDirKey);
                ImageUtil.initImageLoader(frag.getActivity(), dir);
                VideoCache.getInstance().setCacheDirectory(dir);
            }


            return FileUtil.getDirectorySize(frag.mApp.getCacheDir());
        }

        @Override
        protected void onPostExecute(Long cacheSize) {
            SettingsFragment frag = mFragment.get();

            if (frag != null) {
                frag.findPreference(SettingsActivity.CURRENT_CACHE_SIZE_KEY).setSummary(FileUtil.humanReadableByteCount(cacheSize, false));
                Fragment fragment = frag.getFragmentManager().findFragmentByTag("loading");

                if (fragment != null) {
                    ((DialogFragment) fragment).dismiss();
                }

                mFragment.clear();
            }
        }
    }
}