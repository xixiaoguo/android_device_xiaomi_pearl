/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.cutoutmask

import android.content.Context
import android.content.Intent
import android.os.UserHandle
import org.lineageos.settings.utils.dlog

/** 起停遮罩服务的小工具，设置页与开机广播共用。 */
object CutoutMaskUtils {

    fun startService(context: Context) {
        dlog(TAG, "startService")
        runCatching {
                context.startServiceAsUser(
                    Intent(context, CutoutMaskService::class.java),
                    UserHandle.CURRENT
                )
            }
            .onFailure { dlog(TAG, "failed to start service: $it") }
    }

    fun stopService(context: Context) {
        dlog(TAG, "stopService")
        runCatching { context.stopService(Intent(context, CutoutMaskService::class.java)) }
            .onFailure { dlog(TAG, "failed to stop service: $it") }
    }

    private const val TAG = "CutoutMask"
}
