package com.mbientlab.multimwdemo;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;

import com.mbientlab.metawear.api.MetaWearBleService;
import com.mbientlab.metawear.api.MetaWearController;
import com.mbientlab.metawear.api.Module;
import com.mbientlab.metawear.api.MetaWearController.DeviceCallbacks;
import com.mbientlab.metawear.api.controller.Accelerometer;
import com.mbientlab.metawear.api.controller.Accelerometer.Orientation;
import com.mbientlab.metawear.api.controller.LED;
import com.mbientlab.metawear.api.controller.LED.ColorChannel;
import com.mbientlab.metawear.api.controller.MechanicalSwitch;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class MainFragment extends Fragment implements ServiceConnection {
    private class DeviceState {
        public MetaWearController mwController;
        public BluetoothDevice device;
        public boolean buttonPressed;
        public String orientation;
        public ColorChannel ledColor= null;
    }
    
    private class ConnectedDeviceAdapter extends ArrayAdapter<DeviceState> {
        public ConnectedDeviceAdapter(Context context) {
            super(context, 0);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            
            if (convertView == null) {
                convertView= LayoutInflater.from(getContext()).inflate(R.layout.metawear_status, parent, false);
                
                viewHolder= new ViewHolder();
                viewHolder.deviceAddress= (TextView) convertView.findViewById(R.id.device_address);
                viewHolder.deviceName= (TextView) convertView.findViewById(R.id.device_name);
                viewHolder.buttonState= (TextView) convertView.findViewById(R.id.device_button);
                viewHolder.orientation= (TextView) convertView.findViewById(R.id.device_orientation);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            
            DeviceState current= (DeviceState) getItem(position);
            
            final String deviceName= current.device.getName();
            
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText("Unknown Device");
            
            viewHolder.deviceAddress.setText(current.device.getAddress());
            viewHolder.buttonState.setText(Boolean.valueOf(current.buttonPressed).toString());
            viewHolder.orientation.setText(current.orientation);
            return convertView;
        }
        
        private class ViewHolder {
            public TextView deviceAddress;
            public TextView deviceName;
            public TextView buttonState;
            public TextView orientation;
        }
    }
    
    private static final short DURATION= 1000;
    
    private HashMap<Orientation, String> orientationNames= null;
    private ConnectedDeviceAdapter connectedDevices= null;
    private MetaWearBleService mwService;
    private final HashSet<MetaWearController> activeControllers= new HashSet<>();
    private final ArrayDeque<ColorChannel> availableColors;
    
    public MainFragment() {
        availableColors= new ArrayDeque<>();
        for(ColorChannel it: ColorChannel.values()) {
            availableColors.add(it);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        
        activity.getApplicationContext().bindService(new Intent(activity, MetaWearBleService.class), 
                this, Context.BIND_AUTO_CREATE);
        if (orientationNames == null) {
            Resources resouces= activity.getResources();
            
            orientationNames= new HashMap<>();
            orientationNames.put(Orientation.BACK_LANDSCAPE_LEFT, resouces.getString(R.string.text_back_left));
            orientationNames.put(Orientation.BACK_LANDSCAPE_RIGHT, resouces.getString(R.string.text_back_right));
            orientationNames.put(Orientation.BACK_PORTRAIT_DOWN, resouces.getString(R.string.text_back_down));
            orientationNames.put(Orientation.BACK_PORTRAIT_UP, resouces.getString(R.string.text_back_up));
            orientationNames.put(Orientation.FRONT_LANDSCAPE_LEFT, resouces.getString(R.string.text_front_left));
            orientationNames.put(Orientation.FRONT_LANDSCAPE_RIGHT, resouces.getString(R.string.text_front_right));
            orientationNames.put(Orientation.FRONT_PORTRAIT_DOWN, resouces.getString(R.string.text_front_down));
            orientationNames.put(Orientation.FRONT_PORTRAIT_UP, resouces.getString(R.string.text_front_up));
        }
    }
    
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        getActivity().getApplicationContext().unbindService(this);
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        setRetainInstance(true);
        return inflater.inflate(R.layout.fragment_main, container,
                false);
    }
    
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        if (connectedDevices == null) {
            connectedDevices= new ConnectedDeviceAdapter(getActivity());
            connectedDevices.setNotifyOnChange(true);
        }
        
        ListView connectedDevicesView= (ListView) view.findViewById(R.id.connected_devices);
        connectedDevicesView.setAdapter(connectedDevices);
        connectedDevicesView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                    int position, long id) {
                DeviceState current= (DeviceState) connectedDevices.getItem(position);
                
                ((Accelerometer) current.mwController.getModuleController(Module.ACCELEROMETER)).stopComponents();
                ((MechanicalSwitch) current.mwController.getModuleController(Module.MECHANICAL_SWITCH)).disableNotification();
                ((LED) current.mwController.getModuleController(Module.LED)).stop(true);
                
                current.mwController.close(true, true);
                return false;
            }
        });
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mwService= ((MetaWearBleService.LocalBinder) service).getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // TODO Auto-generated method stub
        
    }
    
    public void addMetwearBoard(BluetoothDevice mwBoard) {
        final DeviceState newState= new DeviceState();
        
        newState.device= mwBoard;
        newState.mwController= mwService.getMetaWearController(mwBoard);
        newState.mwController.setRetainState(false);
        
        if (!availableColors.isEmpty()) {
            newState.ledColor= availableColors.poll();
            newState.mwController.addModuleCallback(new MechanicalSwitch.Callbacks() {
                @Override
                public void pressed() {
                    newState.buttonPressed= true;
                    connectedDevices.notifyDataSetChanged();
                    
                    for(MetaWearController it: activeControllers) {
                        if (it != newState.mwController) {
                            LED ledCtrllr= (LED) it.getModuleController(Module.LED);
                            ledCtrllr.setColorChannel(newState.ledColor).withHighIntensity((byte) 16)
                                    .withLowIntensity((byte) 16)
                                    .withHighTime(DURATION)
                                    .withPulseDuration(DURATION)
                                    .withRepeatCount((byte) -1)
                                    .commit();
                            ledCtrllr.play(false);
                        }
                    }
                }

                @Override
                public void released() {
                    newState.buttonPressed= false;
                    connectedDevices.notifyDataSetChanged();
                    
                    for(MetaWearController it: activeControllers) {
                        if (it != newState.mwController) {
                            LED ledCtrllr= (LED) it.getModuleController(Module.LED);
                            ledCtrllr.stop(true);
                        }
                    }
                }
            });
        }
        
        newState.mwController.addDeviceCallback(new DeviceCallbacks() {
            @Override
            public void receivedGattError(GattOperation gattOp, int status) {
                Log.d("MainFragment", String.format("%s, %d", gattOp.toString(), status));
            }

            @Override
            public void connected() {
                connectedDevices.add(newState);
                activeControllers.add(newState.mwController);
                
                Accelerometer accelCtrllr= (Accelerometer) newState.mwController.getModuleController(Module.ACCELEROMETER);
                accelCtrllr.stopComponents();
                accelCtrllr.enableOrientationDetection();
                accelCtrllr.startComponents();
                
                ((MechanicalSwitch) newState.mwController.getModuleController(Module.MECHANICAL_SWITCH)).enableNotification();
                
                ((LED) newState.mwController.getModuleController(Module.LED)).stop(true);
            }
            
            @Override
            public void disconnected() {
                connectedDevices.remove(newState);
                activeControllers.remove(newState.mwController);
                
                if (newState.ledColor != null) {
                    availableColors.add(newState.ledColor);
                }
            }
        }).addModuleCallback(new Accelerometer.Callbacks() {
            @Override
            public void orientationChanged(Orientation accelOrientation) {
                newState.orientation= orientationNames.get(accelOrientation);
                connectedDevices.notifyDataSetChanged();
            }
        });
        
        newState.mwController.connect();
    }
}
