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
import androidx.preference.PreferenceFragmentCompat
import org.lineageos.settings.R
import org.lineageos.settings.utils.dlog

/**
 * 双击 / 三击背板手势设置页。
 *
 * 视觉上与温控页、刷新率页保持一致：说明文字 + 分组标题 + 圆角卡片列表，
 * 点击整行弹出单选对话框。
 *
 * 数据层仍然由 PreferenceFragmentCompat + gesture_settings.xml 承担。
 * 两个 ListPreference 继续使用 PreferenceManager 的默认 SharedPreferences，
 * BackTapService 可以直接读取同一份数据。
 *
 * 本页面使用完全自定义的 View，不显示原生 Preference RecyclerView。
 */
class GestureSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var doubleTapPref: ListPreference
    private lateinit var tripleTapPref: ListPreference

    /**
     * GestureRow 持有页面中的 View，因此只能在 Fragment View 生命周期内存在。
     */
    private var doubleTapRow: GestureRow? = null
    private var tripleTapRow: GestureRow? = null

    /**
     * 保存当前显示的对话框，以便 View 销毁时及时关闭。
     */
    private var actionDialog: AlertDialog? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.gesture_settings, rootKey)

        doubleTapPref =
            requireNotNull(findPreference<ListPreference>(KEY_BACK_DOUBLE_TAP)) {
                "Missing ListPreference: $KEY_BACK_DOUBLE_TAP"
            }

        tripleTapPref =
            requireNotNull(findPreference<ListPreference>(KEY_BACK_TRIPLE_TAP)) {
                "Missing ListPreference: $KEY_BACK_TRIPLE_TAP"
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.gesture_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        /*
         * 注意：这里不能调用 super.onViewCreated()。
         *
         * 当前 Fragment 重写了 onCreateView()，返回的是自定义布局，
         * 没有通过 PreferenceFragmentCompat.onCreateView() 创建和初始化
         * Preference RecyclerView。
         *
         * 如果调用 PreferenceFragmentCompat.onViewCreated()，父类将尝试
         * 绑定未初始化的 Preference RecyclerView，可能触发空指针异常。
         */
        val context = view.context
        val rowInflater = LayoutInflater.from(context)
        val isAvailable = GestureUtils.isBackTapAvailable(context)

        dlog(TAG, "onViewCreated: backTapAvailable=$isAvailable")

        val card =
            requireNotNull(view.findViewById<ViewGroup>(R.id.gesture_card)) {
                "R.id.gesture_card is missing from gesture_layout.xml"
            }

        /*
         * 防止同一个 View 被重复初始化时重复添加条目。
         */
        card.removeAllViews()

        doubleTapRow =
            GestureRow(
                inflater = rowInflater,
                parent = card,
                iconRes = R.drawable.ic_gesture_double_tap,
                titleRes = R.string.back_double_tap_title,
                pref = doubleTapPref,
            ).also { row ->
                card.addView(row.root)
            }

        tripleTapRow =
            GestureRow(
                inflater = rowInflater,
                parent = card,
                iconRes = R.drawable.ic_gesture_triple_tap,
                titleRes = R.string.back_triple_tap_title,
                pref = tripleTapPref,
            ).also { row ->
                card.addView(row.root)
            }

        if (!isAvailable) {
            doubleTapRow?.setUnavailable()
            tripleTapRow?.setUnavailable()
        }
    }

    override fun onResume() {
        super.onResume()

        /*
         * 从其他页面返回时，重新读取 Preference 的当前值。
         */
        doubleTapRow?.refresh()
        tripleTapRow?.refresh()
    }

    override fun onDestroyView() {
        /*
         * Dialog 持有 Activity Context。View 销毁时主动关闭，避免继续引用
         * 已退出的 Activity，或在回调中访问已经销毁的 Fragment View。
         */
        actionDialog?.setOnDismissListener(null)
        actionDialog?.dismiss()
        actionDialog = null

        /*
         * GestureRow 持有 root、ImageView 和 TextView，必须在这里释放。
         */
        doubleTapRow = null
        tripleTapRow = null

        /*
         * 关键修复：
         *
         * 即使 onViewCreated() 因为自定义 Preference 页面而跳过父类实现，
         * onDestroyView() 仍必须调用 Fragment 的父类实现。
         *
         * 否则 Fragment.performDestroyView() 会抛出：
         *
         * SuperNotCalledException:
         * Fragment did not call through to super.onDestroyView()
         */
        super.onDestroyView()
    }

    /**
     * 一行手势设置项：图标、标题、当前动作摘要以及行尾箭头。
     */
    private inner class GestureRow(
        inflater: LayoutInflater,
        parent: ViewGroup,
        iconRes: Int,
        titleRes: Int,
        private val pref: ListPreference,
    ) {
        val root: View =
            inflater.inflate(R.layout.gesture_list_item, parent, false)

        private val iconView: ImageView =
            root.findViewById(R.id.gesture_icon)

        private val titleView: TextView =
            root.findViewById(R.id.gesture_title)

        private val summaryView: TextView =
            root.findViewById(R.id.gesture_summary)

        init {
            iconView.setImageResource(iconRes)
            titleView.setText(titleRes)

            root.setOnClickListener {
                showActionDialog()
            }

            refresh()
        }

        /**
         * 将 ListPreference 当前保存的值转换为对应的显示文本。
         */
        fun refresh() {
            val entries = pref.entries
            val values = pref.entryValues

            if (entries == null || values == null) {
                summaryView.text = pref.summary
                return
            }

            val index = values.indexOf(pref.value)

            summaryView.text =
                if (index in entries.indices) {
                    entries[index]
                } else {
                    pref.entry ?: pref.summary.orEmpty()
                }
        }

        /**
         * 设备不支持背板手势时禁用整行。
         */
        fun setUnavailable() {
            root.isEnabled = false
            root.isClickable = false
            root.isFocusable = false
            root.alpha = UNAVAILABLE_ALPHA

            summaryView.setText(R.string.gesture_unavailable)
        }

        private fun showActionDialog() {
            /*
             * 避免禁用状态下通过无障碍或其他输入路径触发对话框，
             * 同时避免连续点击创建多个 Dialog。
             */
            if (!root.isEnabled || actionDialog?.isShowing == true) {
                return
            }

            val entries = pref.entries ?: return
            val values = pref.entryValues ?: return

            /*
             * 如果当前值不在 entryValues 中，indexOf() 返回 -1。
             * AlertDialog 使用 -1 表示当前没有选中项。
             */
            val checkedItem = values.indexOf(pref.value)

            val dialog =
                AlertDialog.Builder(requireContext())
                    .setTitle(pref.title)
                    .setSingleChoiceItems(
                        entries,
                        checkedItem,
                    ) { selectedDialog, which ->
                        val newValue =
                            values.getOrNull(which)?.toString()
                                ?: return@setSingleChoiceItems

                        /*
                         * 保留标准 Preference 变更检查。
                         *
                         * 如果以后为 Preference 注册了变更监听器，监听器可以返回
                         * false 拒绝本次修改。
                         */
                        if (pref.callChangeListener(newValue)) {
                            pref.value = newValue
                            refresh()
                            selectedDialog.dismiss()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()

            dialog.setOnDismissListener {
                if (actionDialog === dialog) {
                    actionDialog = null
                }
            }

            actionDialog = dialog
            dialog.show()
        }
    }

    companion object {
        private const val TAG = "GestureSettingsFragment"

        private const val KEY_BACK_DOUBLE_TAP = "back_double_tap"
        private const val KEY_BACK_TRIPLE_TAP = "back_triple_tap"

        private const val UNAVAILABLE_ALPHA = 0.5f
    }
}
