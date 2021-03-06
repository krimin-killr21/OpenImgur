package com.kenny.openimgur.fragments;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.kenny.openimgur.R;
import com.kenny.openimgur.activities.ViewActivity;
import com.kenny.openimgur.adapters.GalleryAdapter;
import com.kenny.openimgur.api.ApiClient;
import com.kenny.openimgur.api.Endpoints;
import com.kenny.openimgur.api.ImgurBusEvent;
import com.kenny.openimgur.classes.ImgurBaseObject;
import com.kenny.openimgur.classes.ImgurHandler;
import com.kenny.openimgur.ui.MultiStateView;
import com.kenny.openimgur.util.ViewUtils;

import org.apache.commons.collections15.list.SetUniqueList;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kcampagna on 8/14/14.
 */
public class GalleryFragment extends BaseGridFragment implements GalleryFilterFragment.FilterListener {
    private static final String KEY_SECTION = "section";

    private static final String KEY_SORT = "sort";

    private static final String KEY_SHOW_VIRAL = "showViral";

    public enum GallerySection {
        HOT("hot"),
        USER("user");

        private final String mSection;

        private GallerySection(String s) {
            mSection = s;
        }

        public String getSection() {
            return mSection;
        }

        /**
         * Returns the Enum value for the section based on the string
         *
         * @param section
         * @return
         */
        public static GallerySection getSectionFromString(String section) {
            if (HOT.getSection().equals(section)) {
                return HOT;
            }

            return USER;
        }

        /**
         * Returns the position in the enum array of the given section
         *
         * @param section
         * @return
         */
        public static int getPositionFromSection(GallerySection section) {
            GallerySection[] sections = GallerySection.values();

            for (int i = 0; i < sections.length; i++) {
                if (section.equals(sections[i])) {
                    if (i == 1) {
                        return 49;
                    } else if (i == 2) {
                        return 99;
                    }

                    return i;
                }
            }

            return 0;
        }

        /**
         * Returns the GallerySection in the enum array from the given position
         *
         * @param position
         * @return
         */
        public static GallerySection getSectionFromPosition(int position) {
            GallerySection[] sections = GallerySection.values();

            for (GallerySection section : sections) {
                if (section == sections[position]) {
                    return section;
                }
            }

            return GallerySection.HOT;
        }

        /**
         * Returns the String Resource for the section
         *
         * @return
         */
        @StringRes
        public int getResourceId() {
            switch (this) {
                case HOT:
                    return R.string.viral;

                case USER:
                    return R.string.user_sub;
            }

            return R.string.viral;
        }
    }

    public enum GallerySort {
        TIME("time"),
        RISING("rising"),
        VIRAL("viral");

        private final String mSort;

        private GallerySort(String s) {
            mSort = s;
        }

        public String getSort() {
            return mSort;
        }

        /**
         * Returns the Enum value based on a string
         *
         * @param sort
         * @return
         */
        public static GallerySort getSortFromString(String sort) {
            if (TIME.getSort().equals(sort)) {
                return TIME;
            } else if (RISING.getSort().equals(sort)) {
                return RISING;
            }

            return VIRAL;
        }
    }

    private GallerySection mSection = GallerySection.HOT;

    private GallerySort mSort = GallerySort.TIME;

    private boolean mShowViral = true;

    public static GalleryFragment createInstance() {
        return new GalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gallery, container, false);
    }

    @Override
    protected void onItemSelected(int position, ArrayList<ImgurBaseObject> items) {
        startActivity(ViewActivity.createIntent(getActivity(), items, position));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.gallery, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh:
                refresh();
                return true;

            case R.id.filter:
                if (mListener != null) mListener.onUpdateActionBar(false);

                GalleryFilterFragment fragment = GalleryFilterFragment.createInstance(mSort, mSection, mShowViral);
                fragment.setFilterListener(this);
                getFragmentManager().beginTransaction()
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .add(android.R.id.content, fragment, "filter")
                        .commit();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Returns the URL based on the selected sort and section
     *
     * @return
     */
    private String getGalleryUrl() {
        return String.format(Endpoints.GALLERY.getUrl(), mSection.getSection(), mSort.getSort(), mCurrentPage, mShowViral);
    }

    @Override
    public void onFilterChange(GallerySection section, GallerySort sort, boolean showViral) {
        FragmentManager fm = getFragmentManager();
        fm.beginTransaction().remove(fm.findFragmentByTag("filter")).commit();
        if (mListener != null) mListener.onUpdateActionBar(true);

        // Null values represent that the filter was canceled
        if (section == null || sort == null || (section == mSection && mSort == sort && mShowViral == showViral)) {
            return;
        }

        if (getAdapter() != null) {
            getAdapter().clear();
        }

        mSection = section;
        mSort = sort;
        mShowViral = showViral;
        mCurrentPage = 0;
        mIsLoading = true;
        mMultiStateView.setViewState(MultiStateView.ViewState.LOADING);

        if (mListener != null) {
            mListener.onLoadingStarted();
            mListener.onUpdateActionBarTitle(getString(mSection.getResourceId()));
        }

        fetchGallery();
    }

    private ImgurHandler mHandler = new ImgurHandler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_ACTION_COMPLETE:
                    List<ImgurBaseObject> gallery = (List<ImgurBaseObject>) msg.obj;

                    if (getAdapter() == null) {
                        mGrid.addHeaderView(ViewUtils.getHeaderViewForTranslucentStyle(getActivity(), 0));
                        setAdapter(new GalleryAdapter(getActivity(), SetUniqueList.decorate(gallery)));
                    } else {
                        getAdapter().addItems(gallery);
                    }

                    if (mListener != null) {
                        mListener.onLoadingComplete();
                    }

                    mMultiStateView.setViewState(MultiStateView.ViewState.CONTENT);

                    // Due to MultiStateView setting the views visibility to GONE, the list will not reset to the top
                    // If they change the filter or refresh
                    if (mCurrentPage == 0) {
                        mMultiStateView.post(new Runnable() {
                            @Override
                            public void run() {
                                mGrid.setSelection(0);
                            }
                        });
                    }
                    break;

                case MESSAGE_ACTION_FAILED:
                    if (getAdapter() == null || getAdapter().isEmpty()) {
                        if (mListener != null) {
                            mListener.onError((Integer) msg.obj);
                        }

                        mMultiStateView.setErrorText(R.id.errorMessage, (Integer) msg.obj);
                        mMultiStateView.setErrorButtonText(R.id.errorButton, R.string.retry);
                        mMultiStateView.setErrorButtonClickListener(R.id.errorButton, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                mMultiStateView.setViewState(MultiStateView.ViewState.LOADING);
                                fetchGallery();
                            }
                        });

                        mMultiStateView.setViewState(MultiStateView.ViewState.ERROR);
                    }
                    break;

                case MESSAGE_EMPTY_RESULT:
                default:
                    mIsLoading = false;
                    super.handleMessage(msg);
                    break;
            }

            mIsLoading = false;
        }
    };

    @Override
    protected void fetchGallery() {
        if (mApiClient == null) {
            mApiClient = new ApiClient(getGalleryUrl(), ApiClient.HttpRequest.GET);
        } else {
            mApiClient.setUrl(getGalleryUrl());
        }

        mRequestId = mSection.getSection() + "." + mCurrentPage;
        mApiClient.doWork(getEventType(), mRequestId, null);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SECTION, mSection.getSection());
        outState.putString(KEY_SORT, mSort.getSort());
        outState.putBoolean(KEY_SHOW_VIRAL, mShowViral);
    }

    @Override
    protected void onRestoreSavedInstance(Bundle savedInstanceState) {
        super.onRestoreSavedInstance(savedInstanceState);

        if (savedInstanceState == null) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            mSection = GallerySection.getSectionFromString(pref.getString(KEY_SECTION, null));
            mSort = GallerySort.getSortFromString(pref.getString(KEY_SORT, null));
            mShowViral = pref.getBoolean(KEY_SHOW_VIRAL, true);
        } else {
            mSort = GallerySort.getSortFromString(savedInstanceState.getString(KEY_SORT, GallerySort.TIME.getSort()));
            mSection = GallerySection.getSectionFromString(savedInstanceState.getString(KEY_SECTION, GallerySection.HOT.getSection()));
            mShowViral = savedInstanceState.getBoolean(KEY_SHOW_VIRAL, true);
        }

        if (mListener != null)
            mListener.onUpdateActionBarTitle(getString(mSection.getResourceId()));
    }

    @Override
    protected void saveFilterSettings() {
        app.getPreferences().edit()
                .putString(KEY_SECTION, mSection.getSection())
                .putBoolean(KEY_SHOW_VIRAL, mShowViral)
                .putString(KEY_SORT, mSort.getSort()).apply();
    }

    @Override
    public ImgurBusEvent.EventType getEventType() {
        return ImgurBusEvent.EventType.GALLERY;
    }

    @Override
    protected ImgurHandler getHandler() {
        return mHandler;
    }
}
