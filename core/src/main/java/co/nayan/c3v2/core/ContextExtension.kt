package co.nayan.c3v2.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.material.snackbar.Snackbar
import java.util.UUID
import kotlin.math.roundToLong

var mToast: Toast? = null
fun Context.showToast(message: String) {
    if (mToast != null) {
        mToast?.cancel()
    }
    mToast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
    mToast?.show()
}

/**
 * Helper functions to simplify permission checks/requests.
 */
fun Context.hasPermission(permission: String): Boolean {
    // Background permissions didn't exit prior to Q, so it's approved by default.
    if (permission == Manifest.permission.ACCESS_BACKGROUND_LOCATION &&
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    ) {
        return true
    }

    return ActivityCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * Requests permission and if the user denied a previous request, but didn't check
 * "Don't ask again", we provide additional rationale.
 *
 * Note: The Snackbar should have an action to request the permission.
 */
fun Fragment.requestPermissionWithRationale(
    permission: String,
    requestCode: Int,
    snackbar: Snackbar
) {
    val provideRationale = shouldShowRequestPermissionRationale(permission)

    if (provideRationale) {
        snackbar.show()
    } else {
        requestPermissions(arrayOf(permission), requestCode)
    }
}

fun Context.checkLocationPermission(): Boolean {
    val accessFineLocationPermission =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
    val courseFineLocationPermission =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
    return !(accessFineLocationPermission != PackageManager.PERMISSION_GRANTED
            && courseFineLocationPermission != PackageManager.PERMISSION_GRANTED)
}

fun Context.hasNetwork(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
        else -> false
    }
}

fun Context.isGooglePlayServicesAvailable(): Boolean {
    val apiAvailability = GoogleApiAvailability.getInstance()
    val resultCode = apiAvailability.isGooglePlayServicesAvailable(this)
    return resultCode == ConnectionResult.SUCCESS
}

fun Context.getDeviceTotalRAM(): String {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    val totalMemory: Long = memoryInfo.totalMem / 1024 // Convert to kilobytes
    val sizeInMB = totalMemory / 1024.0
    return if (sizeInMB >= 1) String.format("%.2f GB", sizeInMB / 1024.0)
    else String.format("%.2f MB", sizeInMB)
}

fun Context.getDeviceAvailableRAM(): Float {
    val activityManager = getSystemService(Activity.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    val ram = (memoryInfo.availMem) / (1000F * 1000F * 1000F)
    return (ram * 100).roundToLong() / 100F
}

@SuppressLint("HardwareIds")
fun Context.generateUniqueIdentifier(): String {
    val uuid = UUID.randomUUID().toString()
    val androidId = try {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
    return (uuid + androidId).trim()
}