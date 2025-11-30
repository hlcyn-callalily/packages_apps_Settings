/*
 * Copyright (C) 2021-2025 Halcyon Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 package com.android.settings.halcyon.security

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.settings.R
import com.google.android.material.appbar.AppBarLayout
import com.android.internal.util.halcyon.UserSelectedSpoofUtils

class UserSelectedAppSpoofSettings: Fragment(R.layout.hide_developer_status_layout) {

    private lateinit var activityManager: ActivityManager
    private lateinit var packageManager: PackageManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private lateinit var packageList: List<PackageInfo>
    private lateinit var userManager: UserManager
    private lateinit var userInfos: List<UserInfo>

    private var appBarLayout: AppBarLayout? = null
    private var searchText = ""
    private var customFilter: ((PackageInfo) -> Boolean)? = null
    private var comparator: ((PackageInfo, PackageInfo) -> Int)? = null
    private var showSystem = false
    private var optionsMenu: Menu? = null

    private lateinit var profiles: Array<String>
    private lateinit var profileLabels: Array<String>

    override fun onStart() {
        super.onStart()
        updateOptionsMenu()
        activity?.invalidateOptionsMenu()
    }

    @SuppressLint("QueryPermissionsNeeded")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        requireActivity().title = getString(R.string.user_selectable_app_spoofing_title)

        appBarLayout = requireActivity().findViewById(R.id.app_bar)
        activityManager = requireContext().getSystemService(ActivityManager::class.java) as ActivityManager
        packageManager = requireContext().packageManager
        packageList = packageManager.getInstalledPackages(PackageManager.MATCH_ANY_USER)
        userManager = UserManager.get(requireContext())
        userInfos = userManager.getUsers()

        profiles = resources.getStringArray(R.array.halcyon_spoof_profile_values)
        profileLabels = resources.getStringArray(R.array.halcyon_spoof_profile_labels)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = AppListAdapter()
        recyclerView = view.findViewById<RecyclerView>(R.id.apps_list).also {
            it.layoutManager = LinearLayoutManager(context)
            it.adapter = adapter
        }
        refreshList()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        optionsMenu = menu
        inflater.inflate(R.menu.hide_developer_status_menu, menu)

        menu.findItem(R.id.show_system).isVisible = showSystem
        menu.findItem(R.id.hide_system).isVisible = !showSystem

        val searchView = (menu.findItem(R.id.search).actionView as SearchView)
        searchView.queryHint = getString(R.string.search_apps)
        searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String) = false
            override fun onQueryTextChange(newText: String): Boolean {
                searchText = newText
                refreshList()
                return true
            }
        })
        updateOptionsMenu()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.show_system, R.id.hide_system -> {
                showSystem = !showSystem
                refreshList()
            }
        }
        updateOptionsMenu()
        return true
    }

    private fun updateOptionsMenu() {
        optionsMenu?.let { menu ->
            menu.findItem(R.id.show_system).isVisible = !showSystem
            menu.findItem(R.id.hide_system).isVisible = showSystem
        }
    }

    /** Called when user selects a profile for an app. */
    private fun onListUpdate(packageName: String, profile: String?) {
        // Load current map from settings
        val stored = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.HALCYON_SPOOFED_APPS
        ) ?: ""

        val map = mutableMapOf<String, String>()

        if (stored.isNotEmpty()) {
            stored.split(",").forEach { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    map[parts[0]] = parts[1]
                }
            }
        }

        // Update or remove entry
        if (profile != null) {
            map[packageName] = profile
            userInfos.forEach { info ->
                UserSelectedSpoofUtils.addApp(requireContext(), packageName, profile, info.id)
            }
        } else {
            map.remove(packageName)
            userInfos.forEach { info ->
                UserSelectedSpoofUtils.removeApp(requireContext(), packageName, info.id)
            }
        }

        // Store updated string
        val newStored = map.entries.joinToString(",") { entry ->
            "${entry.key}:${entry.value}"
        }

        Settings.Secure.putString(
            requireContext().contentResolver,
            Settings.Secure.HALCYON_SPOOFED_APPS,
            newStored
        )

        try { activityManager.forceStopPackage(packageName) } catch (_: Exception) {}
    }

    private fun getInitialProfiles(): Map<String, String> {
        val stored = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.HALCYON_SPOOFED_APPS
        ) ?: return emptyMap()

        return stored.split(",").mapNotNull {
            val pair = it.split(":")
            if (pair.size == 2) pair[0] to pair[1] else null
        }.toMap()
    }

    private fun refreshList() {
        var list = packageList.filter {
            val ai = it.applicationInfo ?: return@filter false
            if (!showSystem) !ai.isSystemApp() && !ai.packageName.contains("android.settings")
            else !ai.isResourceOverlay()
        }.filter {
            val ai = it.applicationInfo ?: return@filter false
            ai.loadLabel(packageManager).toString().contains(searchText, true)
        }
        list = customFilter?.let { f -> list.filter(f) } ?: list
        list = comparator?.let { cmp -> list.sortedWith(cmp) } 
                ?: list.sortedBy { it.applicationInfo?.loadLabel(packageManager).toString() }

        if (::adapter.isInitialized) adapter.submitList(
            list.map { 
                val ai = it.applicationInfo ?: return@map null
                AppInfo(it.packageName, ai.loadLabel(packageManager).toString(), ai.loadIcon(packageManager)) 
            }.filterNotNull()
        )
    }

    private inner class AppListAdapter: ListAdapter<AppInfo, AppListViewHolder>(itemCallback) {
        private val selectedMap = getInitialProfiles().toMutableMap()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            AppListViewHolder(layoutInflater.inflate(R.layout.hide_developer_status_list_item, parent, false))

        override fun onBindViewHolder(holder: AppListViewHolder, position: Int) {
            val info = getItem(position)
            holder.label.text = info.label
            holder.packageName.text = info.packageName
            holder.icon.setImageDrawable(info.icon)

            val currentProfile = selectedMap[info.packageName]
            holder.checkBox.isChecked = currentProfile != null
            holder.label.text = if (currentProfile != null) "${info.label} ($currentProfile)" else info.label

            holder.itemView.setOnClickListener {
                val initial = selectedMap[info.packageName]

                val selectedIndex = profiles.indexOf(initial ?: "None")

                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.user_select_spoofing_profile_title))
                    .setSingleChoiceItems(profileLabels, selectedIndex) { dialog, which ->

                        val selectedValue = profiles[which]
                        if (selectedValue != "None") {
                            selectedMap[info.packageName] = selectedValue
                            onListUpdate(info.packageName, selectedValue)
                        } else {
                            selectedMap.remove(info.packageName)
                            onListUpdate(info.packageName, null)
                        }

                        notifyItemChanged(position)
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    private class AppListViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
        val packageName: TextView = view.findViewById(R.id.packageName)
        val checkBox: CheckBox = view.findViewById(R.id.checkBox)
    }

    private data class AppInfo(val packageName: String, val label: String, val icon: android.graphics.drawable.Drawable)

    companion object {
        private val itemCallback = object: DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(old: AppInfo, new: AppInfo) = old.packageName == new.packageName
            override fun areContentsTheSame(old: AppInfo, new: AppInfo) = old == new
        }
    }
}
