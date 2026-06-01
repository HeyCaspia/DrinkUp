package com.example.drinkyourwater.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object IconUtils {
    fun setThirstyIcon(context: Context, isThirsty: Boolean) {
        val packageManager = context.packageManager
        val defaultComponent = ComponentName(context, "com.example.drinkyourwater.MainActivityDefault")
        val thirstyComponent = ComponentName(context, "com.example.drinkyourwater.MainActivityThirsty")

        if (isThirsty) {
            packageManager.setComponentEnabledSetting(
                thirstyComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            packageManager.setComponentEnabledSetting(
                defaultComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } else {
            packageManager.setComponentEnabledSetting(
                defaultComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            packageManager.setComponentEnabledSetting(
                thirstyComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
