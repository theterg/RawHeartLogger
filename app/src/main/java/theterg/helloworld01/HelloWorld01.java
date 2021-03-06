package theterg.helloworld01;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.support.v13.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.text.SimpleDateFormat;


public class HelloWorld01 extends Activity implements ActionBar.TabListener {
    private final static String TAG = HelloWorld01.class.getSimpleName();
    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v13.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;
    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress = "";
    private String mDeviceName = "";
    private ActionBar actionBar;
    protected SharedPreferences prefs;

    private final String PREF_NAME = "com.terg.HelloWorld01.PREFS";

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            TextView UsernameView = (TextView) findViewById(R.id.username);
            TextView PasswordView = (TextView) findViewById(R.id.password);
            final String username = prefs.getString("USER", null);
            final String password = prefs.getString("PASS", null);
            if (username != null) {
                UsernameView.setText(username);
                mBluetoothLeService.getUploadManager().setUsername(username);
            }
            if (password != null) {
                PasswordView.setText(password);
                mBluetoothLeService.getUploadManager().setPassword(password);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Fragment frag = mSectionsPagerAdapter.getRegisteredFragment(mViewPager.getCurrentItem());
            if (!frag.getClass().equals(StatusFragment.class)) {
                return;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            Date now = new Date();
            if (UploadManager.ACTION_UPLOAD_SUCCESSFUL.equals(action)) {
                ((TextView)findViewById(R.id.LastUpload)).setText(sdf.format(now));
            } else if (UploadManager.ACTION_UPLOAD_FAILURE.equals(action)) {
                ((TextView)findViewById(R.id.LastUpload)).setText("Error");
            } else if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                ((TextView)findViewById(R.id.StatusText)).setText("Connected");
                ((TextView)findViewById(R.id.Address)).setText(mBluetoothLeService.getConnectedAddress());
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                ((TextView)findViewById(R.id.StatusText)).setText("Disconnected");
                ((TextView)findViewById(R.id.Address)).setText("");
                ((TextView)findViewById(R.id.DeviceName)).setText("");
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                ((TextView)findViewById(R.id.StatusText)).setText("Services Discovered");
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                ((TextView)findViewById(R.id.StatusText)).setText("Got Data");
                if (intent.hasExtra(BluetoothLeService.HR_DATA)){
                    int hr = (int)intent.getIntExtra(BluetoothLeService.HR_DATA, -1);
                    ((TextView)findViewById(R.id.LastHR)).setText(Integer.toString(hr));
                }
                if (intent.hasExtra(BluetoothLeService.RR_DATA)){
                    ArrayList<Integer> rrValues = intent.getIntegerArrayListExtra(BluetoothLeService.RR_DATA);
                    if ((rrValues != null)&&(rrValues.size() > 0)) {
                        ((TextView)findViewById(R.id.LastRR)).setText(Integer.toString(rrValues.get(rrValues.size()-1)));
                    }
                }
            }
        }
    };

    protected void connectToAddr(String name, String addr) {
        // Automatically connects to the device upon successful start-up initialization.
        mDeviceName = name;
        mDeviceAddress = addr;
        ((TextView)findViewById(R.id.Address)).setText(addr);
        ((TextView)findViewById(R.id.DeviceName)).setText(name);
        Log.i(TAG, "Attempting to connect to "+mDeviceAddress);
        mBluetoothLeService.storeAddress(addr);
        mBluetoothLeService.connect(mDeviceAddress);
        actionBar.selectTab(actionBar.getTabAt(0));

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hello_world01);

        prefs = getSharedPreferences(PREF_NAME, 0);

        // Set up the action bar.
        actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }

        // Bind to the service
        final Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UploadManager.ACTION_UPLOAD_SUCCESSFUL);
        intentFilter.addAction(UploadManager.ACTION_UPLOAD_FAILURE);
        return intentFilter;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.hello_world01, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        SparseArray<Fragment> registeredFragments = new SparseArray<Fragment>();

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a AltFragment (defined as a static inner class below).
            switch (position) {
                case 0:
                    return StatusFragment.newInstance();
                case 1:
                    return SettingsFragment.newInstance();
                case 2:
                    return LogFragment.newInstance();
            }
            return StatusFragment.newInstance();
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return getString(R.string.title_section1).toUpperCase(l);
                case 1:
                    return getString(R.string.title_section2).toUpperCase(l);
                case 2:
                    return getString(R.string.title_section3).toUpperCase(l);
            }
            return null;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            registeredFragments.put(position, fragment);
            return fragment;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            registeredFragments.remove(position);
            super.destroyItem(container, position, object);
        }

        public Fragment getRegisteredFragment(int position) {
            return registeredFragments.get(position);
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class StatusFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static StatusFragment newInstance() {
            StatusFragment fragment = new StatusFragment();
            /*Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, which_tab);
            fragment.setArguments(args);*/
            return fragment;
        }

        public StatusFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_status, container, false);
            return rootView;
        }
    }

    public static class SettingsFragment extends Fragment implements View.OnClickListener {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */

        private SharedPreferences prefs;
        private HelloWorld01 app;

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static SettingsFragment newInstance() {
            SettingsFragment fragment = new SettingsFragment();
            /*Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, which_tab);
            fragment.setArguments(args);*/
            return fragment;
        }

        public SettingsFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            app = (HelloWorld01)getActivity();
            View rootView = inflater.inflate(R.layout.fragment_settings, container, false);
            Button PickDevice = (Button) rootView.findViewById(R.id.pick_device);
            PickDevice.setOnClickListener(this);
            TextView UsernameView = (TextView) rootView.findViewById(R.id.username);
            TextView PasswordView = (TextView) rootView.findViewById(R.id.password);
            final String username = app.prefs.getString("USER", null);
            final String password = app.prefs.getString("PASS", null);
            UsernameView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    if (!charSequence.toString().equals(username)) {
                        SharedPreferences.Editor editor = app.prefs.edit();
                        editor.putString("USER", charSequence.toString());
                        editor.commit();
                        app.mBluetoothLeService.getUploadManager().setUsername(charSequence.toString());
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });
            PasswordView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    if (!charSequence.toString().equals(password)) {
                        SharedPreferences.Editor editor = ((HelloWorld01)getActivity()).prefs.edit();
                        editor.putString("PASS", charSequence.toString());
                        editor.commit();
                        app.mBluetoothLeService.getUploadManager().setPassword(charSequence.toString());
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });
            return rootView;
        }

        @Override
        public void onClick(View view) {
            final Intent intent = new Intent(getActivity(), DeviceScanActivity.class);
            startActivityForResult(intent, DeviceScanActivity.PICK_DEVICE_REQUEST_CODE);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == DeviceScanActivity.PICK_DEVICE_REQUEST_CODE && resultCode == RESULT_OK){
                final String name = data.getStringExtra(DeviceScanActivity.EXTRAS_DEVICE_NAME);
                final String address = data.getStringExtra(DeviceScanActivity.EXTRAS_DEVICE_ADDRESS);
                ((TextView) getActivity().findViewById(R.id.DeviceName)).setText(name);
                ((TextView) getActivity().findViewById(R.id.DeviceAddress)).setText(address);
                ((HelloWorld01)getActivity()).connectToAddr(name, address);
            }
        }
    }

    public static class LogFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static LogFragment newInstance() {
            LogFragment fragment = new LogFragment();
            /*Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, which_tab);
            fragment.setArguments(args);*/
            return fragment;
        }

        public LogFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_log, container, false);
            return rootView;
        }
    }

}
