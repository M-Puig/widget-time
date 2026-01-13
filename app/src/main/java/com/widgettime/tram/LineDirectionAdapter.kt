package com.widgettime.tram

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.widgettime.tram.data.models.LineDirection
import com.widgettime.tram.databinding.ItemLineDirectionBinding

/**
 * Adapter for displaying line/direction options in the configuration activity.
 */
class LineDirectionAdapter(
    private val onItemClick: (LineDirection) -> Unit
) : ListAdapter<LineDirection, LineDirectionAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLineDirectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemLineDirectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(lineDirection: LineDirection) {
            binding.lineText.text = lineDirection.line
            binding.directionText.text = lineDirection.direction
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<LineDirection>() {
        override fun areItemsTheSame(oldItem: LineDirection, newItem: LineDirection): Boolean {
            return oldItem.line == newItem.line && oldItem.direction == newItem.direction
        }

        override fun areContentsTheSame(oldItem: LineDirection, newItem: LineDirection): Boolean {
            return oldItem == newItem
        }
    }
}
