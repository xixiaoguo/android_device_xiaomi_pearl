/*
 * SPDX-FileCopyrightText: 2018 The LineageOS Project
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.lineageos.settings.cutoutmask.CutoutMaskUtils
import org.lineageos.settings.gestures.GestureUtils
import org.lineageos.settings.thermal.ThermalUtils

/** Everything begins at boot. */
class BootCompletedReceiverMain : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed, starting services")
        ThermalUtils.getInstance(context).startService()
        GestureUtils.onBootCompleted(context)

        // 前摄挖孔遮罩：与反编译一致，无条件拉起服务；
        // 是否真的显示遮罩由 CutoutMaskService 内部读 pearl_cutout_mask_enabled 决定。
        CutoutMaskUtils.startService(context)
    }

    companion object {
        private const val TAG = "XiaomiParts-BCR"
    }
}
