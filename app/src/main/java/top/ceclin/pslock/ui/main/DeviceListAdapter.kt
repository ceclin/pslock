package top.ceclin.pslock.ui.main

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.history_list_item.view.*
import top.ceclin.pslock.R
import top.ceclin.pslock.model.Device

class DeviceListAdapter : ListAdapter<Device, ViewHolder>(DiffCallBack) {

    var onItemClick: (View) -> Unit = {}
    var onItemLongClick: (View) -> Boolean = { false }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(View.inflate(parent.context, R.layout.history_list_item, parent)
            .apply {
                setOnClickListener(onItemClick)
                setOnLongClickListener(onItemLongClick)
            })
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(item: Device) {
        itemView.device_name.text = item.name
        itemView.device_mac.text = item.mac
    }
}

private object DiffCallBack : DiffUtil.ItemCallback<Device>() {

    override fun areItemsTheSame(oldItem: Device, newItem: Device): Boolean {
        return oldItem.mac == newItem.mac
    }

    override fun areContentsTheSame(oldItem: Device, newItem: Device): Boolean {
        return oldItem == newItem
    }
}