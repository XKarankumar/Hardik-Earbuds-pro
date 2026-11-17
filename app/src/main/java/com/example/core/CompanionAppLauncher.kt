package com.example.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

data class CompanionAppLaunchResult(
    val launched: Boolean,
    val packageName: String?,
    val openedPlayStoreFallback: Boolean,
    val message: String
)

object CompanionAppLauncher {

    fun findInstalledPackage(context: Context, candidatePackages: List<String>): String? {
        val pm = context.packageManager
        for (pkg in candidatePackages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                return pkg
            }
        }
        return null
    }

    fun launchOrOpenStore(
        context: Context,
        appDisplayName: String,
        candidatePackages: List<String>
    ): CompanionAppLaunchResult {
        val pm = context.packageManager
        val installedPkg = findInstalledPackage(context, candidatePackages)
        if (installedPkg != null) {
            val launchIntent = pm.getLaunchIntentForPackage(installedPkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                return CompanionAppLaunchResult(
                    launched = true,
                    packageName = installedPkg,
                    openedPlayStoreFallback = false,
                    message = "Opened $appDisplayName ($installedPkg)"
                )
            }
        }

        val primaryPkg = candidatePackages.firstOrNull()
        if (primaryPkg != null) {
            val storeIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$primaryPkg")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = storeIntent.resolveActivity(pm) != null
            if (resolved) {
                context.startActivity(storeIntent)
                return CompanionAppLaunchResult(
                    launched = false,
                    packageName = primaryPkg,
                    openedPlayStoreFallback = true,
                    message = "$appDisplayName is not installed. Opened Play Store listing."
                )
            }
        }

        return CompanionAppLaunchResult(
            launched = false,
            packageName = null,
            openedPlayStoreFallback = false,
            message = "$appDisplayName is not installed on this device."
        )
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (_: Exception) {
            false
        }
    }
}
