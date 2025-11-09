package com.dombien.bleappscantest;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DevicesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_BACKEND = 0;
    private static final int TYPE_BLE = 1;

    private SensorData sensorData;
    private List<BleDevice> bleItems;

    public DevicesAdapter(List<BleDevice> bleItems) {
        this.bleItems = bleItems;
    }

    public void setSensorData(SensorData sensorData) {
        this.sensorData = sensorData;
        notifyItemChanged(0); // odśwież tylko pierwszy element
    }

    public void updateList(List<BleDevice> newList) {
        this.bleItems = newList;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return (position == 0) ? TYPE_BACKEND : TYPE_BLE;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_BACKEND) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sensor, parent, false);
            return new SensorVH(v);
        } else {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_device, parent, false);
            return new BleVH(v);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SensorVH) {
            SensorVH vh = (SensorVH) holder;
            if (sensorData != null) {
                vh.tvTemp.setText("Temperature: " + sensorData.temperature);
                vh.tvHumidity.setText("Humidity: " + sensorData.humidity);
                vh.tvStatus.setText("Status: " + sensorData.status);
            } else {
                vh.tvTemp.setText("Temperature: --");
                vh.tvHumidity.setText("Humidity: --");
                vh.tvStatus.setText("Status: waiting...");
            }
        } else if (holder instanceof BleVH) {
            BleDevice d = bleItems.get(position - 1); // -1, bo 0 to backend
            BleVH vh = (BleVH) holder;
            vh.tvName.setText(d.name != null ? d.name : "—");
            vh.tvAddress.setText(d.address);
            vh.tvRssi.setText("RSSI: " + d.rssi + " dBm");
            vh.tvType.setText("Type: " + deviceTypeToString(d.type));
        }
    }

    @Override
    public int getItemCount() {
        // zawsze co najmniej 1 (backend)
        return 1 + (bleItems != null ? bleItems.size() : 0);
    }

    // --- VIEW HOLDER DLA BACKENDU ---
    static class SensorVH extends RecyclerView.ViewHolder {
        TextView tvTemp, tvHumidity, tvStatus;
        SensorVH(View v) {
            super(v);
            tvTemp = v.findViewById(R.id.sensorTemp);
            tvHumidity = v.findViewById(R.id.sensorHumidity);
            tvStatus = v.findViewById(R.id.sensorStatus);
        }
    }

    // --- VIEW HOLDER DLA BLE URZĄDZEŃ ---
    static class BleVH extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress, tvRssi, tvType;
        BleVH(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            tvAddress = v.findViewById(R.id.tvAddress);
            tvRssi = v.findViewById(R.id.tvRssi);
            tvType = v.findViewById(R.id.tvType);
        }
    }

    private String deviceTypeToString(int type) {
        switch (type) {
            case android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC:
                return "CLASSIC";
            case android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE:
                return "LE";
            case android.bluetooth.BluetoothDevice.DEVICE_TYPE_DUAL:
                return "DUAL";
            default:
                return "UNKNOWN";
        }
    }
}
