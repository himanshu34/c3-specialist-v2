package co.nayan.c3v2.core.location

import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.R
import co.nayan.c3v2.core.checkLocationPermission
import co.nayan.c3v2.core.isGooglePlayServicesAvailable
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.LocationFailureState
import co.nayan.c3v2.core.models.LocationSuccessState
import co.nayan.c3v2.core.models.ProgressState
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Location manager class
 *
 * @property context
 */
@Singleton
class LocationManagerImpl @Inject constructor(
    @ApplicationContext val context: Context
) : ILocationManager {

    private val locationState = MutableLiveData<ActivityState>(InitialState)
    private val nmeaParser by lazy { NmeaParser() }

    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    private val interval by lazy { TimeUnit.SECONDS.toMillis(20) }
    private lateinit var locationRequest: LocationRequest
    private val connectivityManager by lazy { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val locationManager by lazy { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    init {
        initializeLocationRequest()
    }

    /**
     * Sets up the location request. Android has two location request settings:
     * {@code ACCESS_COARSE_LOCATION} and {@code ACCESS_FINE_LOCATION}. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     * <p/>
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     * <p/>
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    override fun initializeLocationRequest() {
        locationRequest = LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, interval)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(interval / 4)
            .setMaxUpdateDelayMillis(interval)
            .build()
    }

    override fun checkForLocationRequest() {
        if (context.checkLocationPermission()) {
            locationState.postValue(ProgressState)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)
            ) {
                // Check network availability
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities?.let {
                    if (capabilities.hasCapability(NET_CAPABILITY_INTERNET) && context.isGooglePlayServicesAvailable()) {
                        val builder =
                            LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
                        val client: SettingsClient = LocationServices.getSettingsClient(context)
                        val task: Task<LocationSettingsResponse> =
                            client.checkLocationSettings(builder.build())
                        task.addOnSuccessListener {
                            startReceivingLocationUpdate()
                        }.addOnFailureListener { exception ->
                            stopReceivingLocationUpdate()
                            locationState.postValue(
                                LocationFailureState(
                                    context.getString(R.string.offline_location_error),
                                    exception
                                )
                            )
                        }
                    } else registerLocationManagerWithNMEAListener()
                } ?: run { registerLocationManagerWithNMEAListener() }
            } else locationState.postValue(
                LocationFailureState(
                    context.getString(R.string.no_gps_provider),
                    null
                )
            )
        } else {
            locationState.postValue(
                LocationFailureState(context.getString(R.string.permission_error), null)
            )
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(results: LocationResult) {
            super.onLocationResult(results)
            results.let {
                if (it.lastLocation == null) {
                    it.locations.find { loc ->
                        loc.altitude.toInt() != 0 && loc.latitude.toInt() != 0
                    }?.let { lastLocation ->
                        locationState.postValue(LocationSuccessState(lastLocation))
                    } ?: run { locationState.postValue(LocationSuccessState(it.lastLocation)) }
                } else locationState.postValue(LocationSuccessState(it.lastLocation))
            }
        }
    }

    private val locationListener = LocationListener {
        locationState.postValue(LocationSuccessState(it))
    }

    override fun startReceivingLocationUpdate() {
        if (context.checkLocationPermission()) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)
                ) {
                    // Check network availability
                    val network = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    capabilities?.let {
                        if (capabilities.hasCapability(NET_CAPABILITY_INTERNET) && context.isGooglePlayServicesAvailable()) {
                            fusedLocationClient.requestLocationUpdates(
                                locationRequest,
                                locationCallback,
                                Looper.getMainLooper()
                            )
                        } else registerLocationManagerWithNMEAListener()
                    } ?: run { registerLocationManagerWithNMEAListener() }
                } else locationState.postValue(
                    LocationFailureState(
                        context.getString(R.string.no_gps_provider),
                        null
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Firebase.crashlytics.recordException(e)
            }
        }
    }

    private fun registerLocationManagerWithNMEAListener() {
        if (context.checkLocationPermission()) {
            // Network not available, request location updates using GPS provider and NMEA listener
            locationManager.addNmeaListener(nmeaParser)
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                (interval / 4),
                5f,
                locationListener
            )
        }
    }

    override fun stopReceivingLocationUpdate() {
        try {
            locationState.postValue(InitialState)
            locationManager.removeNmeaListener(nmeaParser)
            locationManager.removeUpdates(locationListener)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            e.printStackTrace()
            Firebase.crashlytics.recordException(e)
        }
    }

    override fun getLocationRequest() = locationRequest

    override fun subscribeLocation(): MutableLiveData<ActivityState> = locationState
}