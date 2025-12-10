package com.dombien.bleappscantest.BLE

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dombien.bleappscantest.R

class DevicesAdapter(
    private var bleItems: MutableList<BleDevice>?,
    private val onDeviceClick: (BleDevice) -> Unit
) : RecyclerView.Adapter<DevicesAdapter.BleVH>() {

    fun updateList(newList: MutableList<BleDevice>?) {
        this.bleItems = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BleVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return BleVH(v)
    }

    override fun onBindViewHolder(holder: BleVH, position: Int) {
        val d = bleItems?.get(position) ?: return
        holder.tvName.text = d.name ?: "Unknown Device"
        holder.tvAddress.text = d.address

        holder.itemView.setOnClickListener {
            onDeviceClick(d)
        }
    }

    override fun getItemCount(): Int {
        return bleItems?.size ?: 0
    }

    class BleVH(v: View) : RecyclerView.ViewHolder(v) {
        var tvName: TextView = v.findViewById(R.id.tvName)
        var tvAddress: TextView = v.findViewById(R.id.tvAddress)
    }
}