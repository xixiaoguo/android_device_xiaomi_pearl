/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.cutoutmask

import android.os.Bundle
import org.lineageos.settings.R
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

/** 前摄挖孔遮罩设置入口（设置 → 显示）。 */
class CutoutMaskSettingsActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager
            .beginTransaction()
            .replace(
                com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                CutoutMaskSettingsFragment(),
                TAG
            )
            .commit()
    }

    companion object {
        private const val TAG = "cutout_mask_settings"
    }
}
