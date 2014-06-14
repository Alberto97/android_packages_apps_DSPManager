
package com.bel.android.dspmanager.activity;

import android.app.ActionBar;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.bel.android.dspmanager.R;
import com.bel.android.dspmanager.service.HeadsetService;
import com.bel.android.dspmanager.widgets.CustomDrawerLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Setting utility for CyanogenMod's DSP capabilities. This page is displays the
 * activity_main-level configurations menu.
 *
 * @author alankila@gmail.com
 */
public final class DSPManager extends Activity {
    //==================================
    // Static Fields
    //==================================
    private static final String STATE_SELECTED_POSITION = "selected_navigation_drawer_position";
    private static final String PREF_USER_LEARNED_DRAWER = "navigation_drawer_learned";
    private static final String PREF_IS_TABBED = "pref_is_tabbed";
    //==================================
    public static final String SHARED_PREFERENCES_BASENAME = "com.bel.android.dspmanager";
    public static final String ACTION_UPDATE_PREFERENCES = "com.bel.android.dspmanager.UPDATE";
    //==================================
    private static String[] mEntries;
    private static List<HashMap<String, String>> mTitles;

    //==================================
    // Drawer
    //==================================
    private ActionBarDrawerToggle mDrawerToggle;
    private CustomDrawerLayout mDrawerLayout;
    private ListView mDrawerListView;
    private View mFragmentContainerView;
    private int mCurrentSelectedPosition = 0;
    private boolean mFromSavedInstanceState;
    private boolean mUserLearnedDrawer;

    //==================================
    // ViewPager
    //==================================
    protected MyAdapter pagerAdapter;
    protected ViewPager viewPager;
    protected PagerTabStrip pagerTabStrip;
    //==================================
    // Fields
    //==================================
    private SharedPreferences mPreferences;
    private boolean mIsTabbed = true;
    private CharSequence mTitle;

    //==================================
    // Overridden Methods
    //==================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mUserLearnedDrawer = mPreferences.getBoolean(PREF_USER_LEARNED_DRAWER, false);

        if (getResources().getBoolean(R.bool.config_allow_toggle_tabbed)) {
            mIsTabbed = mPreferences.getBoolean(PREF_IS_TABBED,
                    getResources().getBoolean(R.bool.config_use_tabbed));
        } else {
            mIsTabbed = getResources().getBoolean(R.bool.config_use_tabbed);
        }

        mTitle = getTitle();

        ActionBar mActionBar = getActionBar();
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setHomeButtonEnabled(true);
        mActionBar.setDisplayShowTitleEnabled(true);

        if (savedInstanceState != null) {
            mCurrentSelectedPosition = savedInstanceState.getInt(STATE_SELECTED_POSITION);
            mFromSavedInstanceState = true;
        }

        setUpUi();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!mIsTabbed) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!mIsTabbed) {
            outState.putInt(STATE_SELECTED_POSITION, mCurrentSelectedPosition);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                HeadsetService service = ((HeadsetService.LocalBinder) binder).getService();
                String routing = service.getAudioOutputRouting();
                if (mIsTabbed && viewPager != null) {
                    String[] entries = getEntries();
                    for (int i = 0; i < entries.length; i++) {
                        if (routing.equals(entries[i])) {
                            viewPager.setCurrentItem(i);
                            break;
                        }
                    }
                }
                unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        Intent serviceIntent = new Intent(this, HeadsetService.class);
        bindService(serviceIntent, connection, 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!isDrawerOpen()) {
            getMenuInflater().inflate(mIsTabbed ? R.menu.menu_tabbed : R.menu.menu, menu);
            if (!getResources().getBoolean(R.bool.config_allow_toggle_tabbed)) {
                menu.removeItem(R.id.dsp_action_tabbed);
            }
            getActionBar().setTitle(mTitle);
            return true;
        } else {
            getActionBar().setTitle(getString(R.string.app_name));
            return super.onCreateOptionsMenu(menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!mIsTabbed) {
            if (mDrawerToggle.onOptionsItemSelected(item)) {
                return true;
            }
        }

        int choice = item.getItemId();
        switch (choice) {
            case R.id.help:
                DialogFragment df = new HelpFragment();
                df.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
                df.show(getFragmentManager(), "help");
                return true;
            case R.id.dsp_action_tabbed:
                mIsTabbed = !mIsTabbed;
                mPreferences.edit().putBoolean(PREF_IS_TABBED, mIsTabbed).commit();
                Utils.restartActivity(this);
                return true;
            default:
                return false;
        }
    }

    //==================================
    // Methods
    //==================================

    private void setUpUi() {

        mTitles = getTitles();
        mEntries = getEntries();

        if (!mIsTabbed) {
            setContentView(R.layout.activity_main);
            mDrawerListView = (ListView) findViewById(R.id.dsp_navigation_drawer);
            mDrawerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    selectItem(position);
                }
            });

            mDrawerListView.setAdapter(new SimpleAdapter(
                    getActionBar().getThemedContext(),
                    mTitles,
                    R.layout.drawer_item,
                    new String[]{"ICON", "TITLE"},
                    new int[]{R.id.drawer_icon, R.id.drawer_title}));
            mDrawerListView.setItemChecked(mCurrentSelectedPosition, true);

            setUpNavigationDrawer(
                    findViewById(R.id.dsp_navigation_drawer),
                    findViewById(R.id.dsp_drawer_layout));

        } else {
            setContentView(R.layout.activity_main_tabbed);

            pagerAdapter = new MyAdapter(getFragmentManager());
            viewPager = (ViewPager) findViewById(R.id.viewPager);
            viewPager.setAdapter(pagerAdapter);
            viewPager.setCurrentItem(0);
            pagerTabStrip = (PagerTabStrip) findViewById(R.id.pagerTabStrip);

            Intent serviceIntent = new Intent(this, HeadsetService.class);
            startService(serviceIntent);

            pagerTabStrip.setDrawFullUnderline(true);
            pagerTabStrip.setTabIndicatorColor(
                    getResources().getColor(android.R.color.holo_blue_light));

        }
    }

    /**
     * Users of this fragment must call this method to set up the
     * navigation menu_drawer interactions.
     *
     * @param fragmentContainerView The view of this fragment in its activity's layout.
     * @param drawerLayout          The DrawerLayout containing this fragment's UI.
     */
    public void setUpNavigationDrawer(View fragmentContainerView, View drawerLayout) {
        mFragmentContainerView = fragmentContainerView;
        mDrawerLayout = (CustomDrawerLayout) drawerLayout;

        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                R.drawable.ic_drawer,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        ) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);

                invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);

                if (!mUserLearnedDrawer) {
                    mUserLearnedDrawer = true;
                    mPreferences.edit().putBoolean(PREF_USER_LEARNED_DRAWER, true).commit();
                }

                invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
            }
        };

        if (!mUserLearnedDrawer && !mFromSavedInstanceState) {
            mDrawerLayout.openDrawer(mFragmentContainerView);
        }

        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mDrawerToggle.syncState();
            }
        });

        mDrawerLayout.setDrawerListener(mDrawerToggle);

        selectItem(mCurrentSelectedPosition);
    }

    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
    }

    private void selectItem(int position) {
        mCurrentSelectedPosition = position;
        if (mDrawerListView != null) {
            mDrawerListView.setItemChecked(position, true);
        }
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(mFragmentContainerView);
        }

        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.dsp_container, PlaceholderFragment.newInstance(position))
                .commit();
        mTitle = mTitles.get(position).get("TITLE");
        getActionBar().setTitle(mTitle);
    }

    /**
     * @return String[] containing titles
     */
    private List<HashMap<String, String>> getTitles() {
        // TODO: use real drawables
        ArrayList<HashMap<String, String>> tmpList = new ArrayList<HashMap<String, String>>();
        // Headset
        HashMap<String, String> mTitleMap = new HashMap<String, String>();
        mTitleMap.put("ICON", R.drawable.empty_icon + "");
        mTitleMap.put("TITLE", getString(R.string.headset_title));
        tmpList.add(mTitleMap);
        // Speaker
        mTitleMap = new HashMap<String, String>();
        mTitleMap.put("ICON", R.drawable.empty_icon + "");
        mTitleMap.put("TITLE", getString(R.string.speaker_title));
        tmpList.add(mTitleMap);
        // Bluetooth
        mTitleMap = new HashMap<String, String>();
        mTitleMap.put("ICON", R.drawable.empty_icon + "");
        mTitleMap.put("TITLE", getString(R.string.bluetooth_title));
        tmpList.add(mTitleMap);
        // Usb
        mTitleMap = new HashMap<String, String>();
        mTitleMap.put("ICON", R.drawable.empty_icon + "");
        mTitleMap.put("TITLE", getString(R.string.usb_title));
        tmpList.add(mTitleMap);

        // Determine if WM8994 is supported
        if (WM8994.isSupported(this)) {
            mTitleMap = new HashMap<String, String>();
            mTitleMap.put("ICON", R.drawable.empty_icon + "");
            mTitleMap.put("TITLE", getString(R.string.wm8994_title));
            tmpList.add(mTitleMap);
        }

        return tmpList;
    }

    /**
     * @return String[] containing titles
     */
    private String[] getEntries() {
        ArrayList<String> entryString = new ArrayList<String>();
        entryString.add("headset");
        entryString.add("speaker");
        entryString.add("bluetooth");
        entryString.add("usb");

        // Determine if WM8994 is supported
        if (WM8994.isSupported(this)) {
            entryString.add(WM8994.NAME);
        }

	return entryString.toArray(new String[entryString.size()]);
    }

    //==================================
    // Internal Classes
    //==================================

    /**
     * Loads our Fragments.
     */
    public static class PlaceholderFragment extends Fragment {

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static Fragment newInstance(int fragmentId) {

            // Determine if fragment is WM8994
            if (mEntries[fragmentId].equals(WM8994.NAME)) {
                return new WM8994();
            } else {
                final DSPScreen dspFragment = new DSPScreen();
                Bundle b = new Bundle();
                b.putString("config", mEntries[fragmentId]);
                dspFragment.setArguments(b);
                return dspFragment;
            }
        }

        public PlaceholderFragment() {
            // intentionally left blank
        }
    }

    public class MyAdapter extends FragmentPagerAdapter {
        private final String[] entries;
        private final List<HashMap<String, String>> titles;

        public MyAdapter(FragmentManager fm) {
            super(fm);

            entries = DSPManager.mEntries;
            titles = DSPManager.mTitles;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return titles.get(position).get("TITLE");
        }

        public String[] getEntries() {
            return entries;
        }

        @Override
        public int getCount() {
            return entries.length;
        }

        @Override
        public Fragment getItem(int position) {

            // Determine if fragment is WM8994
            if (entries[position].equals(WM8994.NAME)) {
                return new WM8994();
            } else {
                final DSPScreen dspFragment = new DSPScreen();
                Bundle b = new Bundle();
                b.putString("config", entries[position]);
                dspFragment.setArguments(b);
                return dspFragment;
            }
        }
    }

    public static class HelpFragment extends DialogFragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
            View v = inflater.inflate(R.layout.help, null);
            TextView tv = (TextView) v.findViewById(R.id.help);
            tv.setText(R.string.help_text);
            return v;
        }
    }

}
