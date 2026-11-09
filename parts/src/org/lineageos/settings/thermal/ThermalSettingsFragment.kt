/*
 * SPDX-FileCopyrightText: 2020 The LineageOS Project
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.thermal

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import org.lineageos.settings.R
import org.lineageos.settings.thermal.ThermalUtils.ThermalState
import org.lineageos.settings.utils.dlog
import java.util.Locale

/**
 * 温控档位设置页。
 *
 * 和刷新率页相同的单列表结构：顶部开关卡片 + 分组标题 + 应用列表卡片。
 * 开关直接绑定 MaterialSwitch，不再通过 PreferenceFragment 注入布局，
 * 因此也没有 wrap_content 高度 pin 那一套测量逻辑。
 */
class ThermalSettingsFragment : Fragment() {

    private lateinit var appsAdapter: AppsAdapter
    private lateinit var launcherApps: LauncherApps
    private lateinit var thermalUtils: ThermalUtils
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var loadingView: View
    private lateinit var listCard: View
    private lateinit var sectionTitle: View
    private lateinit var switchView: MaterialSwitch

    @Volatile private var isLoaded = false

    private val handlerThread = HandlerThread(TAG).apply { start() }
    private val bgHandler = Handler(handlerThread.looper)

    private val launcherAppsCallback =
        object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                if (user != Process.myUserHandle()) return
                val index = appsAdapter.entries.indexOfFirst { it.packageName == packageName }
                if (index != -1) {
                    dlog(TAG, "onPackageRemoved: $packageName")
                    appsAdapter.entries.removeAt(index)
                    appsAdapter.notifyItemRemovedOnUiThread(index)
                }
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                if (user != Process.myUserHandle()) return
                val info = launcherApps.getActivityList(packageName, user).firstOrNull() ?: return
                val entry = toAppEntry(info)
                val entries = appsAdapter.entries
                if (entries.any { it.packageName == packageName }) return
                val index = entries.binarySearchBy(entry.label) { it.label }
                val insertAt = if (index < 0) -(index + 1) else index
                entries.add(insertAt, entry)
                dlog(TAG, "onPackageAdded: $packageName")
                appsAdapter.notifyItemInsertedOnUiThread(insertAt)
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {}

            override fun onPackagesAvailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean
            ) {}

            override fun onPackagesUnavailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean
            ) {}
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcherApps =
            requireActivity().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        thermalUtils = ThermalUtils.getInstance(requireActivity())
        appsAdapter = AppsAdapter(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.thermal_layout, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchView = view.findViewById(R.id.thermal_switch)
        switchView.isChecked = thermalUtils.enabled
        switchView.setOnCheckedChangeListener { _, isChecked ->
            thermalUtils.enabled = isChecked
            updateRvVisibility()
        }

        appsRecyclerView =
            view.findViewById<RecyclerView>(R.id.thermal_rv_view).apply {
                layoutManager = LinearLayoutManager(activity)
                adapter = appsAdapter
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            }
        loadingView = view.findViewById(R.id.thermal_loading)
        listCard = view.findViewById(R.id.thermal_list_container)
        sectionTitle = view.findViewById(R.id.thermal_section_title)
        updateRvVisibility()
        loadApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        dlog(TAG, "onDestroy")
        handlerThread.quitSafely()
        runCatching { launcherApps.unregisterCallback(launcherAppsCallback) }
    }

    /** 开关关闭 -> 整组应用列表隐藏；开关打开但还没加载完 -> 只显示"加载中"。 */
    private fun updateRvVisibility() {
        activity?.runOnUiThread {
            val enabled = thermalUtils.enabled
            sectionTitle.visibility = if (enabled) View.VISIBLE else View.GONE
            listCard.visibility = if (enabled) View.VISIBLE else View.GONE
            appsRecyclerView.visibility =
                if (enabled && isLoaded) View.VISIBLE else View.GONE
            loadingView.visibility = if (enabled && !isLoaded) View.VISIBLE else View.GONE
        }
    }

    private fun loadApps() {
        bgHandler.post {
            val appEntries =
                launcherApps
                    .getActivityList(null, Process.myUserHandle())
                    .distinctBy { it.componentName.packageName } // Filter out duplicates
                    .map { toAppEntry(it) }
                    .sortedBy { it.label.toString().lowercase(Locale.ROOT) } // Sort case-insensitively
            dlog(TAG, "loaded ${appEntries.size} apps")
            appsAdapter.entries.clear()
            appsAdapter.entries.addAll(appEntries)
            appsAdapter.notifyDataSetChangedOnUiThread()
            isLoaded = true
            updateRvVisibility()
            launcherApps.registerCallback(launcherAppsCallback, bgHandler)
        }
    }

    private fun toAppEntry(info: LauncherActivityInfo) =
        AppEntry(
            packageName = info.componentName.packageName,
            label = info.label.toString(),
            icon = info.getIcon(0),
            state = thermalUtils.getStateForPackage(info.componentName.packageName)
        )

    private data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: Drawable,
        val state: ThermalState,
    )

    private inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.app_name)
        val summary: TextView = view.findViewById(R.id.app_mode_summary)
        val icon: ImageView = view.findViewById(R.id.app_icon)
    }

    /** 弹出 Android 16 风格的单选对话框，取代原来的 Spinner。 */
    private fun showModeDialog(entry: AppEntry) {
        val context = requireContext()
        val labels = ThermalState.values().map { context.getString(it.label) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(entry.label)
            .setSingleChoiceItems(labels, entry.state.id) { dialog, which ->
                thermalUtils.writePackage(entry.packageName, which)
                val newState = ThermalState.values().first { it.id == which }
                val index = appsAdapter.entries.indexOfFirst {
                    it.packageName == entry.packageName
                }
                if (index >= 0) {
                    appsAdapter.entries[index] =
                        appsAdapter.entries[index].copy(state = newState)
                    appsAdapter.notifyItemChanged(index)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private inner class AppsAdapter(private val activity: Activity) :
        RecyclerView.Adapter<ViewHolder>() {

        val entries: MutableList<AppEntry> = ArrayList()

        override fun getItemCount() = entries.size

        override fun getItemId(position: Int) = entries[position].hashCode().toLong()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.thermal_list_item, parent, false)
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]
            holder.title.text = entry.label
            holder.summary.setText(entry.state.label)
            holder.icon.setImageDrawable(entry.icon)
            holder.itemView.setOnClickListener { showModeDialog(entry) }
        }

        fun notifyDataSetChangedOnUiThread() {
            activity.runOnUiThread { notifyDataSetChanged() }
        }

        fun notifyItemInsertedOnUiThread(position: Int) {
            activity.runOnUiThread { notifyItemInserted(position) }
        }

        fun notifyItemRemovedOnUiThread(position: Int) {
            activity.runOnUiThread { notifyItemRemoved(position) }
        }
    }

    private companion object {
        const val TAG = "ThermalSettingsFragment"
    }
}