package com.mbientlab.multimwdemo;

import com.mbientlab.bletoolbox.MWScannerFragment;
import com.mbientlab.bletoolbox.MWScannerFragment.ScannerCallback;
import com.mbientlab.metawear.api.MetaWearBleService;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends Activity implements ScannerCallback, ServiceConnection {
    private final static String FRAGMENT_KEY= "com.mbientlab.multimwdemo.MainActivity.FRAGMENT_KEY";
    private final static int REQUEST_ENABLE_BT= 0;

    private MainFragment mainFrag= null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            mainFrag= new MainFragment();
            getFragmentManager().beginTransaction()
            .add(R.id.container, mainFrag).commit();
        } else {
            mainFrag= (MainFragment) getFragmentManager().getFragment(savedInstanceState, FRAGMENT_KEY);
        }

        BluetoothAdapter btAdapter= ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        if (btAdapter == null) {
            new AlertDialog.Builder(this).setTitle(R.string.error_title)
            .setMessage(R.string.error_no_bluetooth)
            .setCancelable(false)
            .setPositiveButton(R.string.label_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    MainActivity.this.finish();
                }
            })
            .create()
            .show();
        } else if (!btAdapter.isEnabled()) {
            final Intent enableIntent= new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
        
        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class), 
                this, Context.BIND_AUTO_CREATE);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        getApplicationContext().unbindService(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch(item.getItemId()) {
        case R.id.action_connect:
            new MWScannerFragment().show(getFragmentManager(), "metawear_scanner_fragment");
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
        case REQUEST_ENABLE_BT:
            if (resultCode == Activity.RESULT_CANCELED) {
                finish();
            }
            break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mainFrag != null) {
            getFragmentManager().putFragment(outState, FRAGMENT_KEY, mainFrag);
        }
    }

    @Override
    public void btDeviceSelected(BluetoothDevice device) {
        mainFrag.addMetwearBoard(device);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ((MetaWearBleService.LocalBinder) service).getService().useLocalBroadcasterManager(true);
        
        LocalBroadcastManager.getInstance(this).registerReceiver(MetaWearBleService.getMetaWearBroadcastReceiver(), 
                MetaWearBleService.getMetaWearIntentFilter());
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(MetaWearBleService.getMetaWearBroadcastReceiver());
        
    }
}
