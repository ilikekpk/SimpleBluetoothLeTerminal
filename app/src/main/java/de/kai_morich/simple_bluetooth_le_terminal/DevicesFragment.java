package de.kai_morich.simple_bluetooth_le_terminal;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;

import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * show list of BLE devices
 */

class BLEdevice {
    BluetoothDevice device;
    byte[] data;
}

public class DevicesFragment extends ListFragment {

    private enum ScanState { NONE, LESCAN, DISCOVERY, DISCOVERY_FINISHED }
    private ScanState                       scanState = ScanState.NONE;
    private static final long               LESCAN_PERIOD = 10000; // similar to bluetoothAdapter.startDiscovery
    private Handler                         leScanStopHandler = new Handler();
    private BluetoothAdapter.LeScanCallback leScanCallback;
    private BroadcastReceiver               discoveryBroadcastReceiver;
    private IntentFilter                    discoveryIntentFilter;

    private Menu                            menu;
    private BluetoothAdapter                bluetoothAdapter;
    private ArrayList<BLEdevice>      listItems = new ArrayList<>();
    private ArrayAdapter<BLEdevice>   listAdapter;

    public DevicesFragment() {
        leScanCallback = (device, rssi, scanRecord) -> {
            if(device != null && getActivity() != null) {
                getActivity().runOnUiThread(() -> { updateScan(device, scanRecord);});
            }
        };
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if(getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        listAdapter = new ArrayAdapter<BLEdevice>(getActivity(), 0, listItems) {
            @Override
            public View getView(int position, View view, ViewGroup parent) {
                BLEdevice bledevice = listItems.get(position);
                if (view == null)
                    view = getActivity().getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
                TextView text1 = view.findViewById(R.id.text1);
                TextView text2 = view.findViewById(R.id.text2);
                TextView text3 = view.findViewById(R.id.text3);
                TextView text4 = view.findViewById(R.id.text4);

                if(bledevice.device.getName() == null || bledevice.device.getName().isEmpty())
                    text1.setText("<unnamed>");
                else
                    text1.setText(bledevice.device.getName());
                    text2.setText(bledevice.device.getAddress());

                    byte[] res = advParseManufacturerData(bledevice.data);

                    if (res != null)
                    {
                        text3.setText(bytesToHex(res));
                        long[] l = getParams(res);
                        String paramsString = "param1: "  + l[0] +
                                              " param2: "  + l[1] +
                                              " param3: "  + l[2] +
                                              " param4: "  + l[3] +
                                              " param5: "  + l[4] +
                                              " param6: "  + l[5] +
                                              " param7: "  + l[6] +
                                              " param8: "  + l[7];
                        text4.setText(paramsString);
                        for (int i = 0; i < 13; i++) {

                            Log.e("1", Long.toString(getParams(res)[i]));
                        }
                        Log.e("1", "-------------------");
                    }
                return view;
            }
        };
    }

    private int getParam(String key)
    {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(getActivity());
        String x = sharedPreferences.getString(key, "");
        if (x != null) return Integer.parseInt(x);
        else return 0;
    }

    private long[] getParams(byte[] manufData)
    {
        int len = manufData.length;
        int pointer = 2; // skip manufacturer info
        long[] params = new long[13];

        for (int i = 0; i < 8; i++) {
            int paramID = getParam("param" + (i + 1));
            int margin_size = 0;
            boolean isSigned;
            switch (paramID) {
                case 2:         // uint8_t
                    margin_size = 1;
                    isSigned = false;
                    break;
                case 3:         // int8_t
                    margin_size = 1;
                    isSigned = true;
                    break;
                case 4:         // uint16_t
                    margin_size = 2;
                    isSigned = false;
                    break;
                case 5:         // int16_t
                    margin_size = 2;
                    isSigned = true;
                    break;
                case 6:         // uint32_t
                    margin_size = 4;
                    isSigned = false;
                    break;
                case 7:         // int32_t
                    margin_size = 4;
                    isSigned = true;
                    break;
                default:
                    margin_size = 0;
                    isSigned = false;
                    break;
            }

            Log.e("2", "param: " + i + " size: " + margin_size);

            for (int j = 0; j < margin_size; j++) {
                params[i] |= (manufData[pointer++] & 0xFFL) << j * 8;

                if (isSigned && j == margin_size - 1)
                {
                    if ((params[i] & (0x80L << j * 8)) != 0) params[i] -= Math.pow(2, margin_size * 8);
                }

                if (pointer >= len) return params;
            }
        }
        return params;
    }



    private byte[] advParseManufacturerData(byte[] data)
    {
        int index = 0;

        while (index < data.length - 1)
        {
            int field_length = data[index];
            byte field_type = data[index + 1];

            if (field_type == (byte) 0xff)
            {
                byte[] result = new byte[field_length];
                for (int i = 0; i < field_length; i++) result[i] = data[i + index + 2];
                return result;
            }
            index += field_length + 1;
        }
        return null;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setListAdapter(null);
        setEmptyText("initializing...");
        ((TextView) getListView().getEmptyView()).setTextSize(18);
        setListAdapter(listAdapter);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_devices, menu);
        this.menu = menu;
        if (bluetoothAdapter == null) {
            menu.findItem(R.id.bt_settings).setEnabled(false);
            menu.findItem(R.id.ble_scan).setEnabled(false);
        } else if(!bluetoothAdapter.isEnabled()) {
            menu.findItem(R.id.ble_scan).setEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.ble_scan) {
            startScan();
            return true;
        } else if (id == R.id.ble_scan_stop) {
            stopScan();
            return true;
        } else if (id == R.id.bt_settings) {
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivity(intent);
            return true;
        } else if (id == R.id.parser_settings) {
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            startActivity(intent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @SuppressLint("StaticFieldLeak") // AsyncTask needs reference to this fragment
    private void startScan() {
        if(scanState != ScanState.NONE)
            return;
        scanState = ScanState.LESCAN;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getActivity().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                scanState = ScanState.NONE;
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.location_permission_title);
                builder.setMessage(R.string.location_permission_message);
                builder.setPositiveButton(android.R.string.ok,
                        (dialog, which) -> requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0));
                builder.show();
                return;
            }
            LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
            boolean         locationEnabled = false;
            try {
                locationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            } catch(Exception ignored) {}
            try {
                locationEnabled |= locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            } catch(Exception ignored) {}
            if(!locationEnabled)
                scanState = ScanState.DISCOVERY;
            // Starting with Android 6.0 a bluetooth scan requires ACCESS_COARSE_LOCATION permission, but that's not all!
            // LESCAN also needs enabled 'location services', whereas DISCOVERY works without.
            // Most users think of GPS as 'location service', but it includes more, as we see here.
            // Instead of asking the user to enable something they consider unrelated,
            // we fall back to the older API that scans for bluetooth classic _and_ LE
            // sometimes the older API returns less results or slower
        }
        listItems.clear();
        listAdapter.notifyDataSetChanged();
        setEmptyText("<scanning...>");
        menu.findItem(R.id.ble_scan).setVisible(false);
        menu.findItem(R.id.ble_scan_stop).setVisible(true);
        if(scanState == ScanState.LESCAN) {
            leScanStopHandler.postDelayed(this::stopScan, LESCAN_PERIOD);
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void[] params) {
                    bluetoothAdapter.startLeScan(null, leScanCallback);
                    return null;
                }
            }.execute(); // start async to prevent blocking UI, because startLeScan sometimes take some seconds
        } else {
            bluetoothAdapter.startDiscovery();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // ignore requestCode as there is only one in this fragment
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            new Handler(Looper.getMainLooper()).postDelayed(this::startScan,1); // run after onResume to avoid wrong empty-text
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getText(R.string.location_denied_title));
            builder.setMessage(getText(R.string.location_denied_message));
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        }
    }

    private void updateScan(BluetoothDevice device, byte[] scanRecord) {
        if(scanState == ScanState.NONE)
            return;

        for (int i = 0; i < listItems.size(); i++) {
            BluetoothDevice userListName = listItems.get(i).device;
            if (userListName.equals(device)) {
                return;
            }
        }

            BLEdevice dev = new BLEdevice();
            dev.data = scanRecord;
            dev.device = device;

            CharSequence filter_name = this.getArguments().getCharSequence("filter_name", "");

            if (filter_name.toString().equals("")) {
                listItems.add(dev);
                listAdapter.notifyDataSetChanged();
                return;
            }

            if (dev.device.getName() != null && dev.device.getName().toLowerCase().indexOf(filter_name.toString().toLowerCase()) != -1) {
                listItems.add(dev);
                listAdapter.notifyDataSetChanged();
            }
    }

    private void stopScan() {
        if(scanState == ScanState.NONE)
            return;
        setEmptyText("<no bluetooth devices found>");
        if(menu != null) {
            menu.findItem(R.id.ble_scan).setVisible(true);
            menu.findItem(R.id.ble_scan_stop).setVisible(false);
        }
        switch(scanState) {
            case LESCAN:
                leScanStopHandler.removeCallbacks(this::stopScan);
                bluetoothAdapter.stopLeScan(leScanCallback);
                break;
            case DISCOVERY:
                bluetoothAdapter.cancelDiscovery();
                break;
            default:
                // already canceled
        }
        scanState = ScanState.NONE;

    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        stopScan();
        BluetoothDevice device = listItems.get(position-1).device;
        Bundle args = new Bundle();
        args.putString("device", device.getAddress());
        Fragment fragment = new TerminalFragment();
        fragment.setArguments(args);
        getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "terminal").addToBackStack(null).commit();
    }
}
