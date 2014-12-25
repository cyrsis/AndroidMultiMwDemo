package com.mbientlab.multimwdemo;

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

import android.app.Fragment;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
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
        public String motion;
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
                viewHolder.motion= (TextView) convertView.findViewById(R.id.device_motion);
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
            viewHolder.motion.setText(current.motion);
            return convertView;
        }
        
        private class ViewHolder {
            public TextView deviceAddress;
            public TextView deviceName;
            public TextView buttonState;
            public TextView motion;
        }
    }
    
    private static final short DURATION= 1000;
    
    private ConnectedDeviceAdapter connectedDevices= null;
    private MetaWearBleService mwService;
    private final HashSet<MetaWearController> activeControllers= new HashSet<>();
    
    public MainFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
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
                ((DeviceState) connectedDevices.getItem(position)).mwController.close(true);
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
        newState.motion= "N/A";
        newState.mwController= mwService.getMetaWearController(mwBoard);
        newState.mwController.addDeviceCallback(new DeviceCallbacks() {
            @Override
            public void connected() {
                connectedDevices.add(newState);
                activeControllers.add(newState.mwController);
                
                Accelerometer accelCtrllr= (Accelerometer) newState.mwController.getModuleController(Module.ACCELEROMETER);
                accelCtrllr.enableOrientationDetection();
                accelCtrllr.startComponents();
                
                ((MechanicalSwitch) newState.mwController.getModuleController(Module.MECHANICAL_SWITCH)).enableNotification();
            }
            
            @Override
            public void disconnected() {
                connectedDevices.remove(newState);
            }
        }).addModuleCallback(new MechanicalSwitch.Callbacks() {
            @Override
            public void pressed() {
                newState.buttonPressed= true;
                connectedDevices.notifyDataSetChanged();
                
                for(MetaWearController it: activeControllers) {
                    if (it != newState.mwController) {
                        LED ledCtrllr= (LED) it.getModuleController(Module.LED);
                        ledCtrllr.setColorChannel(ColorChannel.GREEN).withHighIntensity((byte) 16)
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
                        ledCtrllr.stop(true);;
                    }
                }
            }
        }).addModuleCallback(new Accelerometer.Callbacks() {
            @Override
            public void orientationChanged(Orientation accelOrientation) {
                newState.motion= accelOrientation.name();
                connectedDevices.notifyDataSetChanged();
            }
        });
        
    }
}
