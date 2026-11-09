/*
 * SPDX-FileCopyrightText: 2023-2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.gestures

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.lineageos.settings.R
import org.lineageos.settings.utils.dlog

/**
 * 双击 / 三击背板手势设置页。
 *
 * 视觉上与温控页、刷新率页保持一致：说明文字 + 分组标题 + 圆角卡片列表，
 * 点击整行弹出 Android 16 风格的单选对话框。
 *
 * 数据层仍然由 [PreferenceFragmentCompat] + gesture_settings.xml 承担：两个
 * [ListPreference] 依旧挂在 PreferenceManager 的默认 SharedPreferences 上，
 * BackTapService 直接读同一份数据，因此键名与 String 类型的值都保持不变。
 * 本页只是把自己渲染成自定义卡片布局，不再显示原生的 Preference 列表。
 */
class GestureSettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

    private lateinit var doubleTapPref: ListPreference
    private lateinit var tripleTapPref: ListPreference

    private var doubleTapRow: GestureRow? = null
    private var tripleTapRow: GestureRow? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.gesture_settings, rootKey)

        doubleTapPref = findPreference(KEY_BACK_DOUBLE_TAP)!!
        tripleTapPref = findPreference(KEY_BACK_TRIPLE_TAP)!!

        doubleTapPref.onPreferenceChangeListener = this
        tripleTapPref.onPreferenceChangeListener = this
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.gesture_layout, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isAvailable = GestureUtils.isBackTapAvailable(requireContext())
        dlog(TAG, "onViewCreated: backTapAvailable=$isAvailable")

        val card = view.findViewById<ViewGroup>(R.id.gesture_card)

        doubleTapRow =
            GestureRow(
                inflater = LayoutInflater.from(requireContext()),
                parent = card,
                iconRes = R.drawable.ic_gesture_double_tap,
                titleRes = R.string.back_double_tap_title,
                pref = doubleTapPref,
            ).also { card.addView(it.root) }

        tripleTapRow =
            GestureRow(
                inflater = LayoutInflater.from(requireContext()),
                parent = card,
                iconRes = R.drawable.ic_gesture_triple_tap,
                titleRes = R.string.back_triple_tap_title,
                pref = tripleTapPref,
            ).also { card.addView(it.root) }

        // 设备没有 back tap 传感器时，两行都置灰且不可点击。
        if (!isAvailable) {
            doubleTapRow?.setUnavailable()
            tripleTapRow?.setUnavailable()
        }
    }

    override fun onResume() {
        super.onResume()
        doubleTapRow?.refresh()
        tripleTapRow?.refresh()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean =
        when (preference.key) {
            KEY_BACK_DOUBLE_TAP,
            KEY_BACK_TRIPLE_TAP -> {
                // 值由 ListPreference 自行持久化，BackTapService 监听同一个 key。
                // 这里只负责把新值同步到摘要行。
                if (preference.key == KEY_BACK_DOUBLE_TAP) doubleTapRow?.refresh()
                else tripleTapRow?.refresh()
                true
            }
            else -> false
        }

    /** 一行手势项：图标 + 标题 + 当前动作 + 行尾箭头。 */
    private inner class GestureRow(
        inflater: LayoutInflater,
        parent: ViewGroup,
        iconRes: Int,
        titleRes: Int,
        private val pref: ListPreference,
    ) {
        val root: View = inflater.inflate(R.layout.gesture_list_item, parent, false)
        private val iconView: ImageView = root.findViewById(R.id.gesture_icon)
        private val titleView: TextView = root.findViewById(R.id.gesture_title)
        private val summaryView: TextView = root.findViewById(R.id.gesture_summary)

        init {
            iconView.setImageResource(iconRes)
            titleView.setText(titleRes)
            root.setOnClickListener { showActionDialog() }
        }

        /** 把 preference 的当前值同步到摘要行。 */
        fun refresh() {
            val entries = pref.entries
            val values = pref.entryValues
            val index = values?.indexOf(pref.value)
            summaryView.text =
                if (index != null && index in entries.indices) entries[index]
                else pref.entry
        }

        fun setUnavailable() {
            root.isEnabled = false
            root.isClickable = false
            root.isFocusable = false
            root.alpha = 0.5f
            summaryView.setText(R.string.gesture_unavailable)
        }

        private fun showActionDialog() {
            val entries = pref.entries ?: return
            val values = pref.entryValues ?: return
            val current = values.indexOf(pref.value).coerceAtLeast(0)

            AlertDialog.Builder(requireContext())
                .setTitle(pref.title)
                .setSingleChoiceItems(entries, current) { dialog, which ->
                    // 写回 ListPreference：由它负责持久化成 String，
                    // BackTapService 的 OnSharedPreferenceChangeListener 立即收到变更。
                    pref.value = values[which].toString()
                    refresh()
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    companion object {
        private const val TAG = "GestureSettingsFragment"
        private const val KEY_BACK_DOUBLE_TAP = "back_double_tap"
        private const val KEY_BACK_TRIPLE_TAP = "back_triple_tap"
    }
}