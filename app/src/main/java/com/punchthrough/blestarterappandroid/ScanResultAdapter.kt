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

import android.Manifest
import android.app.Activity
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.RecyclerView
import com.punchthrough.blestarterappandroid.databinding.RowScanResultBinding

class ScanResultAdapter(
    private val items: List<ScanResult>,
    private val onClickListener: ((device: ScanResult) -> Unit)
) : RecyclerView.Adapter<ScanResultAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowScanResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, parent.context, onClickListener)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    class ViewHolder(
        private val binding: RowScanResultBinding,
        private val context: Context,
        private val onClickListener: ((device: ScanResult) -> Unit)
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: ScanResult) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(
                        context as Activity,
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                        ENABLE_BLUETOOTH_REQUEST_CODE
                    )
                }
            }
            binding.apply {
                deviceName.text = result.device.name ?: "Unnamed"
                macAddress.text = result.device.address
                val signalStrengthText = context.getString(R.string.signal_strength_format, result.rssi)
                signalStrength.text = signalStrengthText
                root.setOnClickListener { onClickListener.invoke(result) }
            }

        }
    }
}
