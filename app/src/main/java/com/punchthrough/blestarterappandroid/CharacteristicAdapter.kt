/*
 * Copyright 2019 Punch Through Design LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.punchthrough.blestarterappandroid

import android.bluetooth.BluetoothGattCharacteristic
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.punchthrough.blestarterappandroid.ble.printProperties
import com.punchthrough.blestarterappandroid.databinding.RowCharacteristicBinding

class CharacteristicAdapter(
    private val items: List<BluetoothGattCharacteristic>,
    private val onClickListener: ((characteristic: BluetoothGattCharacteristic) -> Unit)
) : RecyclerView.Adapter<CharacteristicAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            RowCharacteristicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onClickListener)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    class ViewHolder(
        private val binding: RowCharacteristicBinding,
        private val onClickListener: ((characteristic: BluetoothGattCharacteristic) -> Unit)
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(characteristic: BluetoothGattCharacteristic) {
            binding.apply {
                characteristicUuid.text = characteristic.uuid.toString()
                characteristicProperties.text = characteristic.printProperties()
                root.setOnClickListener { onClickListener.invoke(characteristic) }
            }

        }
    }
}
