package com.android.ranit.smartthermostat.view.adapter;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.android.ranit.smartthermostat.R;
import com.android.ranit.smartthermostat.common.ConnectionStates;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by: Ranit Raj Ganguly on 20/06/2021
 */
public class BleDeviceAdapter extends RecyclerView.Adapter<BleDeviceAdapter.ViewHolder> {
    private static final String TAG = BleDeviceAdapter.class.getSimpleName();

    private static final String ANIMATION_INITIAL = "ble_connect.json";
    private static final String ANIMATION_CONNECTED = "on.json";
    private static final String ANIMATION_CONNECTING = "connecting.json";
    private static final String ANIMATION_DISCONNECT = "off.json";

    private Context mContext;
    private List<BluetoothDevice> mDeviceList;
    private DeviceItemClickListener mClickListener;
    private ConnectionStates mCurrentState = ConnectionStates.DISCONNECTED;
    private int mSelectedPosition = -1;

    // Click listener Interface
    public interface DeviceItemClickListener {
        void onDeviceClicked(int position);
    }

    // Constructor
    public BleDeviceAdapter(Context context, List<BluetoothDevice> deviceList,
                            DeviceItemClickListener clickListener) {
        this.mContext = context;
        this.mDeviceList = deviceList;
        this.mClickListener = clickListener;
    }

    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_ble_sensor, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BluetoothDevice currentDevice = mDeviceList.get(position);
        holder.tvDeviceName.setText(currentDevice.getName());
        holder.tvDeviceAddress.setText(currentDevice.getAddress());
        prepareLottieAnimationView(holder.lottieConnectivityStatus, ANIMATION_INITIAL,true);

        holder.layoutItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mClickListener.onDeviceClicked(position);
            }
        });

        customizeViewHolder(holder, position, currentDevice);
    }

    @Override
    public int getItemCount() {
        return mDeviceList.size();
    }

    // View-Holder
    public static class ViewHolder extends RecyclerView.ViewHolder {
        ConstraintLayout layoutItem;
        TextView tvDeviceName;
        TextView tvDeviceAddress;
        LottieAnimationView lottieConnectivityStatus;

        public ViewHolder(@NotNull View itemView) {
            super(itemView);

            layoutItem = itemView.findViewById(R.id.layoutBleItem);
            tvDeviceName = itemView.findViewById(R.id.tvDeviceName);
            tvDeviceAddress = itemView.findViewById(R.id.tvDeviceAddress);
            lottieConnectivityStatus = itemView.findViewById(R.id.lottieViewStatus);
        }
    }

    /**
     * Set the DeviceList
     */
    public void setDeviceList(List<BluetoothDevice> list) {
        this.mDeviceList = list;
    }

    /**
     * Customize UI based on state of HRM device
     */
    public void setCurrentDeviceState(ConnectionStates state, int position) {
        this.mCurrentState = state;
        Log.d(TAG, "setCurrentDeviceState() called with state: "+state);
        if (position != -1) {
            this.mSelectedPosition = position;
        }
    }

    /**
     * Set-up lottie animation
     */
    private void prepareLottieAnimationView(LottieAnimationView lottieView,
                                            String animationName, boolean loop) {
        lottieView.setAnimation(animationName);
        lottieView.loop(loop);
    }

    /**
     * Customize UI for view-holder as per current device connectivity state
     *
     * @param holder - current view-holder
     * @param position - view-holder position
     * @param currentDevice - current BluetoothDevice object selected by user
     */
    private void customizeViewHolder(ViewHolder holder, int position, BluetoothDevice currentDevice) {
        // Customize UI
        holder.layoutItem.setClickable(true);
        if (mSelectedPosition == position) {
            if (mCurrentState == ConnectionStates.DISCONNECTED) {
                holder.tvDeviceName.setText(currentDevice.getName());
                prepareLottieAnimationView(holder.lottieConnectivityStatus,
                        ANIMATION_DISCONNECT, false);
            } else if (mCurrentState == ConnectionStates.CONNECTED) {
                String text = "Connected to "+currentDevice.getName();
                holder.tvDeviceName.setText(text);
                prepareLottieAnimationView(holder.lottieConnectivityStatus,
                        ANIMATION_CONNECTED, false);
            } else if (mCurrentState == ConnectionStates.CONNECTING) {
                holder.layoutItem.setClickable(false);
                prepareLottieAnimationView(holder.lottieConnectivityStatus,
                        ANIMATION_CONNECTING, true);
            } else if (mCurrentState == ConnectionStates.DISCONNECTING) {
                String text = "Disconnecting from "+currentDevice.getName();
                holder.tvDeviceName.setText(text);
            }
        } else {
            prepareLottieAnimationView(holder.lottieConnectivityStatus,
                    ANIMATION_INITIAL, true);
        }
    }
}
