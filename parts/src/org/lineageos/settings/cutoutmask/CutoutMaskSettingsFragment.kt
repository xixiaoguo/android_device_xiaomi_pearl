/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.cutoutmask

import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceFragment
import androidx.preference.SwitchPreferenceCompat
import org.lineageos.settings.R
import org.lineageos.settings.cutoutmask.CutoutMaskService.Companion.DEFAULT_ENABLED
import org.lineageos.settings.cutoutmask.CutoutMaskService.Companion.KEY_ENABLED
import org.lineageos.settings.utils.dlog

/** 单个开关：前摄使用时是否遮住挖孔（对应反编译的 CutoutMaskSettingsFragment）。 */
class CutoutMaskSettingsFragment :
    PreferenceFragment(), Preference.OnPreferenceChangeListener {

    private val enabledPref by lazy {
        findPreference<SwitchPreferenceCompat>(KEY_ENABLED)!!
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.cutout_mask_settings)

        enabledPref.onPreferenceChangeListener = this
        loadValues()
    }

    override fun onResume() {
        super.onResume()
        loadValues()
    }

    private fun loadValues() {
        val cr = context?.contentResolver ?: return
        enabledPref.isChecked = Settings.System.getInt(cr, KEY_ENABLED, DEFAULT_ENABLED) != 0
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val context = preference.context
        val cr = context.contentResolver
        dlog(TAG, "${preference.key} -> $newValue")

        // 只有开关本身需要处理，其它 key 返回 false 表示不接管。
        if (preference.key != KEY_ENABLED) return false

        val enabled = newValue as? Boolean ?: return false
        Settings.System.putInt(cr, KEY_ENABLED, if (enabled) 1 else 0)

        // 关闭时不直接 stopService：让服务收到设置变化，先播完 200ms 退出动画，
        // 再由 CutoutMaskService 自己 stopSelf()。
        if (enabled) {
            CutoutMaskUtils.startService(context)
        }
        return true
    }

    companion object {
        private const val TAG = "CutoutMaskSettings"
    }
}
