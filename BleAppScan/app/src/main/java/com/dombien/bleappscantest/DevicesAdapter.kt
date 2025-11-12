package com.dombien.bleappscantest

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dombien.bleappscantest.DevicesAdapter.BleVH

class DevicesAdapter(private var bleItems: MutableList<BleDevice>?) :
    RecyclerView.Adapter<BleVH>() { // Usunięto zbędny '?'
    fun updateList(newList: MutableList<BleDevice>?) {
        this.bleItems = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BleVH {
        val v = LayoutInflater.from(parent.context) // Użycie .context zamiast .getContext()
            .inflate(R.layout.item_device, parent, false)
        return BleVH(v)
    }

    override fun onBindViewHolder(holder: BleVH, position: Int) {
        val d = bleItems!!.get(position)
        holder.tvName.text = if (d.name != null) d.name else "—" // Użycie .text zamiast .setText()
        holder.tvAddress.text = d.address
        holder.tvRssi.text = "RSSI: " + d.rssi + " dBm"
        holder.tvType.text = "Type: " + deviceTypeToString(d.type)
    }

    override fun getItemCount(): Int {
        return bleItems?.size ?: 0
    }

    inner class BleVH(v: View) : RecyclerView.ViewHolder(v) {
        var tvName: TextView = v.findViewById(R.id.tvName)
        var tvAddress: TextView = v.findViewById(R.id.tvAddress)
        var tvRssi: TextView = v.findViewById(R.id.tvRssi)
        var tvType: TextView = v.findViewById(R.id.tvType)
    }

    private fun deviceTypeToString(type: Int): String {
        return when (type) { // Bardziej idiomatyczny zapis w Kotlinie
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
            BluetoothDevice.DEVICE_TYPE_LE -> "LE"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL"
            else -> "UNKNOWN"
        }
    }
}