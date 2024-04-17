package co.nayan.c3specialist_v2.surgeMap

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.IntentSender
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.Extras.COMING_FROM
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.databinding.ActivityDriverSurgeMapSingleLayBinding
import co.nayan.c3specialist_v2.driverworksummary.ObjectOfInterestMapsViewModel
import co.nayan.c3specialist_v2.formatMilliseconds
import co.nayan.c3specialist_v2.getBitmapFromDrawable
import co.nayan.c3specialist_v2.getDistanceInMeter
import co.nayan.c3specialist_v2.getMarkerBitmap
import co.nayan.c3specialist_v2.getTrackColor
import co.nayan.c3specialist_v2.impl.CityKmlManagerImpl
import co.nayan.c3specialist_v2.startGraphHopperSyncingRequest
import co.nayan.c3specialist_v2.utils.MySupportMapFragment
import co.nayan.c3v2.core.api.client_error.ClientErrorManagerImpl
import co.nayan.c3v2.core.checkLocationPermission
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.location.LocationManagerImpl
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.Coordinates
import co.nayan.c3v2.core.models.LocationFailureState
import co.nayan.c3v2.core.models.LocationSuccessState
import co.nayan.c3v2.core.models.MapData
import co.nayan.c3v2.core.models.SurgeLocation
import co.nayan.c3v2.core.models.driver_module.Drivers
import co.nayan.c3v2.core.models.driver_module.SegmentTrackData
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.Constants.Error.INTERNAL_ERROR_TAG
import co.nayan.c3v2.core.utils.Constants.Error.MARKER_TAG_DRIVERS
import co.nayan.c3v2.core.utils.Constants.Error.UNAUTHORIZED_TAG
import co.nayan.c3v2.core.utils.disabled
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3v2.login.LoginActivity
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.AIResultsHelperImpl
import com.nayan.nayancamv2.NayanCamActivity
import com.nayan.nayancamv2.adjustConstraints
import com.nayan.nayancamv2.extcam.common.IAIResultsHelper
import com.nayan.nayancamv2.helper.GlobalParams._segments
import com.nayan.nayancamv2.hovermode.BackgroundCameraService
import com.nayan.nayancamv2.hovermode.HoverPermissionCallback
import com.nayan.nayancamv2.isHoverPermissionGranted
import com.nayan.nayancamv2.launchHoverService
import com.nayan.nayancamv2.requestHoverPermission
import com.nayan.nayancamv2.scout.EventsAdapter
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.storage.StorageUtil
import com.nayan.nayancamv2.ui.PermissionsDisclosureActivity
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_10
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_2_sec
import com.nayan.nayancamv2.util.TrackingUtility
import com.nayan.nayancamv2.util.isServiceRunning
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class DriverSurgeMapActivity : FragmentActivity(), OnMapReadyCallback {

    companion object {
        const val IS_FORCE_OPENED = "IS_FORCE_OPENED"
    }

    private var navigationWidth: Int = 0
    private var activeRoles: List<String> = listOf()
    private val viewModel: ObjectOfInterestMapsViewModel by viewModels()
    private lateinit var binding: ActivityDriverSurgeMapSingleLayBinding
    private var mGoogleMap: GoogleMap? = null
    private lateinit var eventsAdapter: EventsAdapter

    @Inject
    lateinit var nayanCamModuleInteractor: NayanCamModuleInteractor

    @Inject
    lateinit var locationManagerImpl: LocationManagerImpl

    val iaiResultsHelper: IAIResultsHelper = AIResultsHelperImpl
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var storageUtil: StorageUtil

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var cityKmlManagerImpl: CityKmlManagerImpl

    @Inject
    lateinit var clientErrorManagerImpl: ClientErrorManagerImpl

    private val locationPermissions by lazy {
        arrayOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION)
    }
    private val pictureInPictureParamsBuilder by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PictureInPictureParams.Builder() else
            null
    }

    private val locationRequestHandler by lazy { Handler(Looper.getMainLooper()) }
    private val locationRequestRunnable = Runnable {
        if (::locationManagerImpl.isInitialized)
            locationManagerImpl.checkForLocationRequest()
    }

    private val segmentsRequestHandler = Handler(Looper.getMainLooper())
    private val segmentsRequestRunnable = Runnable { setUpSegmentsOnMap() }
    private val removableMarkers = mutableListOf<Marker>()
    private var currentLocationMarker: Marker? = null
    private var navigationIcon: BitmapDescriptor? = null
    private var shouldUsePhoneLocation = true

    private val drawSurgeOnMapHandler = Handler(Looper.getMainLooper()) /*UI thread*/
    private val workRunnable = Runnable {
        viewModel.surgeLocationResponse.value?.let {
            drawSurgeOnMap(it.surgeLocations)
        }
    }

    private fun logoutUser(errorMessage: String?) = lifecycleScope.launch {
        userRepository.userLoggedOut()
        moveToLoginScreen(errorMessage)
    }

    private fun moveToLoginScreen(errorMessage: String?) {
        Intent(this, LoginActivity::class.java).apply {
            putExtra(LoginActivity.VERSION_NAME, BuildConfig.VERSION_NAME)
            putExtra(LoginActivity.VERSION_CODE, BuildConfig.VERSION_CODE)
            putExtra(LoginActivity.ERROR_MESSAGE, errorMessage)
            startActivity(this)
        }
        finishAffinity()
    }

    private val errorCodeReceiver = Observer<Intent?> { intent ->
        intent?.let {
            when (it.action) {
                UNAUTHORIZED_TAG -> {
                    logoutUser(getString(R.string.unauthorized_error_message))
                }

                INTERNAL_ERROR_TAG -> {
                    Snackbar.make(
                        binding.rootView,
                        getString(co.nayan.c3v2.core.R.string.something_went_wrong),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }

                else -> {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriverSurgeMapSingleLayBinding.inflate(layoutInflater)
        clientErrorManagerImpl.subscribe().observe(this, errorCodeReceiver)
        sharedPrefManager = SharedPrefManager(this)
        storageUtil = StorageUtil(this, sharedPrefManager, nayanCamModuleInteractor)

        shouldUsePhoneLocation = !(intent.hasExtra(COMING_FROM)
                && intent.getStringExtra(COMING_FROM).equals("External", true))

        if (shouldUsePhoneLocation.not()) {
            init()
            return
        }
        val currentTime = System.currentTimeMillis()
        val diff = currentTime - viewModel.getLastSurgeDisplayed()
        if (intent.hasExtra(IS_FORCE_OPENED) &&
            intent.getBooleanExtra(IS_FORCE_OPENED, false)
        ) init()
        else if (diff > TimeUnit.HOURS.toMillis(3)) init()
        else openCam()
    }

    private fun switchToPiP() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            windowMetrics.bounds
        } else {
            val display = windowManager.defaultDisplay
            val rect = Rect()
            display?.getRectSize(rect)
            rect
        }
        pictureInPictureParamsBuilder?.setAspectRatio(Rational(rect.width(), rect.height()))
        pictureInPictureParamsBuilder?.build()?.let { enterPictureInPictureMode(it) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) hideViewsForPip() else showViewsForPip()
    }

    private fun showViewsForPip() {
        val orientation = this.resources.configuration.orientation
        val isLandscapeMode = (orientation == Configuration.ORIENTATION_LANDSCAPE)
        binding.rvEvents.visible()
        eventsAdapter.updateOrientation(isLandscapeMode)
        binding.startRecording.visible()
        binding.resumeBtn.visible()
        if (shouldUsePhoneLocation) showNearbyDrivers()
        if (BuildConfig.FLAVOR == "qa" || activeRoles.contains(Role.ADMIN) || userRepository.isSurveyor())
            binding.refreshBtn.visible()
    }

    private fun hideViewsForPip() {
        binding.rvEvents.gone()
        binding.startRecording.gone()
        binding.resumeBtn.gone()
        binding.refreshBtn.gone()
        if (shouldUsePhoneLocation) lifecycleScope.launch { clearNearByDrivers() }
    }

    private fun startObservingWorkRequest(workRequestId: UUID) = lifecycleScope.launch {
        WorkManager.getInstance(this@DriverSurgeMapActivity)
            .getWorkInfoByIdLiveData(workRequestId) // requestId is the WorkRequest id
            .observe(this@DriverSurgeMapActivity) { workInfo ->
                if (workInfo?.state == null) return@observe
                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> binding.refreshBtn.disabled()
                    WorkInfo.State.SUCCEEDED -> {
                        binding.refreshBtn.enabled()
                    }

                    WorkInfo.State.CANCELLED -> binding.refreshBtn.enabled()
                    WorkInfo.State.FAILED -> binding.refreshBtn.enabled()
                    else -> {}
                }
            }
    }

    private fun init() {
        setContentView(binding.root)
        viewModel.updateLastSurgeDisplayed()
        observeLocation()
        eventsAdapter = EventsAdapter()
        iaiResultsHelper.getMutablePIPLD().observe(this@DriverSurgeMapActivity) {
            Timber.tag("PIPTEST").d(("isActive Observed from drone and --> $it"))
            when (it) {
                is AIResultsHelperImpl.DismissPIP -> {
                    finish()
                    iaiResultsHelper.getMutablePIPLD().postValue(AIResultsHelperImpl.InitState(""))
                }

                is AIResultsHelperImpl.SwitchToPIP -> {
                    switchToPiP()
                }
            }
        }

        activeRoles = userRepository.getUserRoles()
        if (BuildConfig.FLAVOR == "qa" || activeRoles.contains(Role.ADMIN) || userRepository.isSurveyor())
            binding.refreshBtn.visible()
        setOnClickListeners()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as MySupportMapFragment
        mapFragment.getMapAsync(this)
        mapFragment.setListener {
            removeLocationObserver()
            if (currentLocationMarker?.isVisible == true) currentLocationMarker?.isVisible = false
            binding.resumeBtn.visible()
        }

        onBackPressedDispatcher.addCallback(
            this,
            onBackPressedCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (iaiResultsHelper.getMutablePIPLD().value is AIResultsHelperImpl.SwitchToPIP)
                        switchToPiP()
                    else finish()
                }

            })
    }

    private val locationUpdateObserver = Observer<ActivityState> {
        when (it) {
            is LocationSuccessState -> {
                it.location?.let { l ->
                    val latLng = LatLng(l.latitude, l.longitude)
                    viewModel.saveLastLocation(latLng)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        animateGoogleMapToMyLocation(latLng, l.bearing, l.bearingAccuracyDegrees)
                    else animateGoogleMapToMyLocation(latLng, l.bearing)
                }
            }

            is LocationFailureState -> {
                it.exception?.let { exception ->
                    if (exception is ResolvableApiException) {
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            resolutionForResult.launch(
                                IntentSenderRequest.Builder(exception.resolution).build()
                            )
                        } catch (sendEx: IntentSender.SendIntentException) {
                            Firebase.crashlytics.recordException(sendEx)
                            Timber.e(sendEx)
                        }
                    }
                } ?: run { requestPermissionsLauncher.launch(locationPermissions) }
            }
        }
    }

    private val externalCamObserver = Observer<Location> { l ->
        val latLng = LatLng(l.latitude, l.longitude)
        viewModel.saveLastLocation(latLng)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            animateGoogleMapToMyLocation(latLng, l.bearing, l.bearingAccuracyDegrees)
        else animateGoogleMapToMyLocation(latLng, l.bearing)
    }

    private val resolutionForResult =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
            if (activityResult.resultCode == RESULT_OK) {
                Timber.i("User agreed to make required location settings changes.")
                startReceivingLocationUpdates()
            } else Timber.i("User chose not to make required location settings changes.")
        }

    private fun startReceivingLocationUpdates() {
        if (::locationManagerImpl.isInitialized)
            locationManagerImpl.startReceivingLocationUpdate()
    }

    private fun stopReceivingLocationUpdates() {
        if (::locationManagerImpl.isInitialized)
            locationManagerImpl.stopReceivingLocationUpdate()
    }

    override fun onResume() {
        super.onResume()
        locationRequestHandler.post(locationRequestRunnable)
    }

    private fun setUpSegmentsOnMap() = lifecycleScope.launch(Dispatchers.Default) {
        val segmentTrackDataList = storageUtil.getAllSegments().filter { it.count > 0 }
        // Instead of adding all segments in one go, consider processing the data in smaller batches
        // Split the segment list into batches, e.g., 100 items per batch
        val batchSize = 100
        val batches = segmentTrackDataList.chunked(batchSize)
        for (batch in batches) {
            val allPolylineSegments = drawTrackSegments(batch)
            withContext(Dispatchers.Main) {
                for (polylineOptions in allPolylineSegments) {
                    mGoogleMap?.addPolyline(polylineOptions)
                }
            }

            // Consider introducing a delay between batches to avoid rapid UI updates,
            // which can lead to ANRs.
            delay(100)
        }
    }

    private fun showNearbyDrivers() = lifecycleScope.launch(Dispatchers.Main) {
        // Clear older markers
        async { clearNearByDrivers() }.await()
        val driverMarkers = withContext(Dispatchers.IO) {
            drawDriverMarkers(storageUtil.getNearbyDrivers())
        }
        driverMarkers?.forEach { pairDriverMarkers ->
            val rotation = (Math.random() * 360)
            mGoogleMap?.apply {
                addMarker(pairDriverMarkers.first)?.apply {
                    tag = MARKER_TAG_DRIVERS
                    this.rotation = rotation.toFloat()
                    removableMarkers.add(this)
                    zIndex = 10F
                }

                addMarker(pairDriverMarkers.second)?.apply {
                    tag = MARKER_TAG_DRIVERS
                    this.rotation = rotation.toFloat()
                    removableMarkers.add(this)
                }
            }
        }
    }

    private suspend fun clearNearByDrivers() = withContext(Dispatchers.Main) {
        removableMarkers.forEach { it.remove() }
        removableMarkers.clear()
    }

    private suspend fun drawDriverMarkers(
        driverList: MutableList<Drivers>
    ): MutableList<Pair<MarkerOptions, MarkerOptions>>? = withContext(Dispatchers.IO) {
        return@withContext try {
            val markerOptionsList = mutableListOf<Pair<MarkerOptions, MarkerOptions>>()
            val context = this@DriverSurgeMapActivity
            val dim = resources.getDimension(co.nayan.canvas.R.dimen.margin_60).toInt()
            val originalIcon = ContextCompat.getDrawable(context, R.drawable.nearby_driver)
            val bgIcon = ContextCompat.getDrawable(context, R.drawable.bg_nearby_driver)
                ?.apply { setTint(getColor(R.color.white)) }?.getBitmapFromDrawable(dim, dim)

            driverList.map { driver ->
                val locationData = driver.location.split(",")
                val specificLatLng = LatLng(locationData[0].toDouble(), locationData[1].toDouble())
                // add foreground maker
                val foregroundMarker = MarkerOptions().position(specificLatLng)
                    .icon(originalIcon?.let {
                        it.setTint(Color.parseColor(driver.color))
                        BitmapDescriptorFactory.fromBitmap(
                            it.getBitmapFromDrawable(dim, dim)
                        )
                    })
                val backgroundMarker = MarkerOptions().position(specificLatLng)
                    .icon(bgIcon?.let { BitmapDescriptorFactory.fromBitmap(it) })

                markerOptionsList.add(Pair(foregroundMarker, backgroundMarker))
            }

            markerOptionsList
        } catch (e: Exception) {
            e.printStackTrace()
            Firebase.crashlytics.recordException(e)
            null
        }
    }

    private suspend fun drawTrackSegments(
        dataList: List<SegmentTrackData>
    ): MutableList<PolylineOptions> = withContext(Dispatchers.Default) {
        val polylineOptions = mutableListOf<PolylineOptions>()
        for (it in dataList) {
            val coordinateList = mutableListOf<LatLng>()
            val segmentKey = it.coordinates?.split(",")
            segmentKey?.let {
                if (segmentKey.size > 1) {
                    val initialSegment = LatLng(
                        segmentKey[0].toDouble(),
                        segmentKey[1].toDouble()
                    )

                    coordinateList.add(initialSegment)
                }
                if (segmentKey.size > 3) {
                    val finalSegment = LatLng(
                        segmentKey[2].toDouble(),
                        segmentKey[3].toDouble()
                    )

                    coordinateList.add(finalSegment)
                }
            }

            polylineOptions.add(drawPolyline(coordinateList, getTrackColor(it.count)))
        }

        return@withContext polylineOptions
    }

    private fun drawWardBoundaries(mapData: MutableList<MapData>) = lifecycleScope.launch {
        mapData.map { currentWard ->
            val geometryCoordinates = currentWard.geometry?.coordinates?.toMutableList()
            geometryCoordinates?.let { wardCoordinates ->
                mGoogleMap?.addPolygon(drawPolygon(wardCoordinates))
            }
        }
    }

    private fun drawSurgeOnMap(
        locations: List<SurgeLocation>
    ) = lifecycleScope.launch(Dispatchers.IO) {
        val userLocation = viewModel.getLastLocation()
        val iconSize = resources.getDimension(co.nayan.canvas.R.dimen.margin_35).toInt()
        val kmlOfInterestRadius =
            viewModel.sharedStorage.getKmlOfInterestRadius() * 1000 // In Meters
        locations.filter {
            getDistanceInMeter(userLocation, it.latitude, it.longitude) <= kmlOfInterestRadius
        }.map { coordinate ->
            if (coordinate.latitude.isNullOrEmpty() || coordinate.longitude.isNullOrEmpty()) return@map

            val latLng = LatLng(coordinate.latitude!!.toDouble(), coordinate.longitude!!.toDouble())
            val bitmap = withContext(Dispatchers.IO) {
                coordinate.iconUrl?.let { image -> getMarkerBitmap(image, iconSize) }
                    ?: run { null }
            }
            withContext(Dispatchers.Main) {
                val markerOptions = MarkerOptions().position(latLng).title(coordinate.description)
                bitmap?.let {
                    val markerView =
                        View.inflate(this@DriverSurgeMapActivity, R.layout.view_custom_marker, null)
                    val imgEvent = markerView.findViewById<ImageView>(R.id.img_event)
                    imgEvent.setImageBitmap(bitmap)
                    val bitmapDescriptor =
                        BitmapDescriptorFactory.fromBitmap(getBitmapFromView(markerView))
                    markerOptions.icon(bitmapDescriptor)
                }
                mGoogleMap?.addMarker(markerOptions)
                mGoogleMap?.addCircle(drawBounds(latLng, coordinate.radius!!.toDouble()))
            }
        }
    }

    private fun drawPolyline(
        coordinateList: MutableList<LatLng>,
        trackColor: Int
    ): PolylineOptions {
        return PolylineOptions()
            .clickable(true)
            .width(8f)
            .color(trackColor)
            .addAll(coordinateList)
    }

    private fun drawPolygon(geometryCoordinates: MutableList<Coordinates>): PolygonOptions {
        val coordinateList = geometryCoordinates.map { LatLng(it.lat, it.lon) }
        return PolygonOptions()
            .clickable(true)
            .strokeWidth(8f)
            .strokeColor(Color.BLACK)
            .fillColor(0x1A000000)
            .addAll(coordinateList)
    }

    private fun drawBounds(point: LatLng, radius: Double): CircleOptions {
        return CircleOptions()
            .center(point)
            .radius(radius) // In meters
            .strokeWidth(4f)
            .strokeColor(Color.RED)
            .fillColor(0x22FF0000)
    }

    private fun setObservers() {
        viewModel.surgeLocationResponse.observe(this) {
            drawSurgeOnMapHandler.removeCallbacks(workRunnable)
            drawSurgeOnMapHandler.postDelayed(workRunnable, DELAYED_2_sec)
        }

        if (_segments.value == null) _segments.postValue(storageUtil.getAllSegments())
        _segments.observe(this) {
            segmentsRequestHandler.removeCallbacks(segmentsRequestRunnable)
            segmentsRequestHandler.postDelayed(segmentsRequestRunnable, DELAYED_2_sec)
        }

        viewModel.events.observe(this) { data ->
            val orientation = this.resources.configuration.orientation
            val isLandscapeMode = (orientation == Configuration.ORIENTATION_LANDSCAPE)
            if (data.isNullOrEmpty()) {
                binding.rvEvents.gone()
                if (isLandscapeMode) binding.mapLayout.adjustConstraints(binding.rootView)
            } else {
                data.sortByDescending { events -> events.score?.toInt() ?: run { 0 } }
                binding.rvEvents.apply {
                    this.layoutManager = layoutManager
                    adapter = eventsAdapter
                    eventsAdapter.addAll(data)
                    eventsAdapter.updateOrientation(isLandscape = isLandscapeMode)
                    if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)) visible()
                }
            }
        }
    }

    private fun setOnClickListeners() {
        binding.startRecording.setOnClickListener {
            if (isServiceRunning<BackgroundCameraService>().not()) openCam()
            else finishAffinity()
        }

        binding.refreshBtn.setOnClickListener {
            val currentTimeMillis = System.currentTimeMillis()
            val diff = currentTimeMillis - sharedPrefManager.getGraphHopperSyncTimeStamp()
            val timeLeft = DELAYED_10 - diff
            if (diff >= DELAYED_10) {
                val workRequestId = startGraphHopperSyncingRequest(false)
                startObservingWorkRequest(workRequestId)
                binding.refreshBtn.disabled()
            } else showToast("Wait time to hit next request ${timeLeft.formatMilliseconds()}")
        }

        binding.resumeBtn.setOnClickListener {
            observeLocation()
            currentLocationMarker?.isVisible = true
            animateGoogleMapToMyLocation(viewModel.getLastLocation(), isDefault = true)
            binding.resumeBtn.gone()
        }
    }

    private fun openCam() {
        if (TrackingUtility.hasRequiredPermissions(this)) {
            if (storageUtil.isDefaultHoverMode()) {
                if (!isHoverPermissionGranted())
                    requestHoverPermission(
                        nayanCamModuleInteractor.getDeviceModel(),
                        hoverPermissionCallback
                    )
                else {
                    sharedPrefManager.setLastHoverRestartCalled()
                    launchHoverService()
                }
            } else openDriverCamera()
        } else showPermissionsDisclosure()
    }

    private val hoverPermissionCallback = object : HoverPermissionCallback {
        override fun onPermissionGranted() {
            sharedPrefManager.setLastHoverRestartCalled()
            launchHoverService()
        }

        override fun onPermissionDenied(intent: Intent) {
            requestOverLayPermissionLauncher.launch(intent)
        }

        override fun onPermissionDeniedAdditional(intent: Intent) {
            AlertDialog.Builder(this@DriverSurgeMapActivity)
                .setTitle("Please Enable the additional permissions")
                .setMessage("Hover mode can not function in background if you disable these permissions.")
                .setPositiveButton("Enable now!") { _, _ -> startActivity(intent) }
                .setCancelable(false)
                .show()
        }
    }

    private val requestOverLayPermissionLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (isHoverPermissionGranted()) openCam()
            else {
                showToast(getString(co.nayan.nayancamv2.R.string.draw_over_other_app_))
                finish()
            }
        }

    private fun animateGoogleMapToMyLocation(
        latLng: LatLng,
        bearing: Float = 0F,
        bearingAccuracyDegrees: Float = 0F,
        isDefault: Boolean = false
    ) = lifecycleScope.launch {
        val cameraPosition = CameraPosition.Builder().also {
            it.target(latLng)
            if (bearingAccuracyDegrees in 0.0..90.0)
                it.tilt(bearingAccuracyDegrees)
            it.zoom(17.0f)
            it.bearing(bearing)
        }.build()
        mGoogleMap?.let { gMap ->
            gMap.apply {
                if (isDefault.not()) {
                    currentLocationMarker?.let {
                        it.position = latLng
                        it.rotation = bearing
                    } ?: addMarker(
                        MarkerOptions().position(latLng)
                            .icon(navigationIcon).flat(true)
                    )?.apply {
                        rotation = bearing
                        currentLocationMarker = this
                        zIndex = 15F
                    }
                }
                gMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            }
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.map {
                Timber.d("Permissions requested --> ${it.key} = ${it.value}")
                if (it.key == ACCESS_FINE_LOCATION && it.value)
                    if (::locationManagerImpl.isInitialized) locationManagerImpl.checkForLocationRequest()
            }
        }

    private fun openDriverCamera() {
        startActivity(Intent(this, NayanCamActivity::class.java))
        finish()
    }

    private fun showPermissionsDisclosure() {
        startActivity(Intent(this, PermissionsDisclosureActivity::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopReceivingLocationUpdates()
        drawSurgeOnMapHandler.removeCallbacks(workRunnable)
        segmentsRequestHandler.removeCallbacks(segmentsRequestRunnable)
        locationRequestHandler.removeCallbacks(locationRequestRunnable)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        this.mGoogleMap = googleMap
        this.mGoogleMap?.clear()
        setObservers()
        val navigationDrawable = if (shouldUsePhoneLocation) {
            navigationWidth = resources.getDimension(R.dimen.margin_100).toInt()
            ContextCompat.getDrawable(
                this,
                R.drawable.ic_google_pointer
            )
        } else {
            navigationWidth = resources.getDimension(R.dimen.margin_80).toInt()
            ContextCompat.getDrawable(this, R.drawable.drone_marker)
        }

        val navigationHeight = resources.getDimension(R.dimen.margin_64).toInt()
        navigationIcon = navigationDrawable?.let {
            BitmapDescriptorFactory.fromBitmap(
                it.getBitmapFromDrawable(navigationWidth, navigationHeight)
            )
        } ?: BitmapDescriptorFactory.defaultMarker()
        // Enable my location by default if location is taking time
        animateGoogleMapToMyLocation(viewModel.getLastLocation(), isDefault = true)
        if (checkLocationPermission()) {
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = false
        } else requestPermissionsLauncher.launch(locationPermissions)

        viewModel.fetchSurgeLocations()
        viewModel.getEvents()
        cityKmlManagerImpl.subscribe().observe(this) { drawWardBoundaries(it) }
        if (shouldUsePhoneLocation) showNearbyDrivers()

        mGoogleMap?.setOnMarkerClickListener {
            return@setOnMarkerClickListener MARKER_TAG_DRIVERS == it.tag
        }
    }

    private fun getBitmapFromView(view: View): Bitmap {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val bitmap =
            Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        view.draw(canvas)
        return bitmap
    }

    private fun observeLocation() {
        if (shouldUsePhoneLocation && ::locationManagerImpl.isInitialized)
            locationManagerImpl.subscribeLocation().observe(this, locationUpdateObserver)
        else iaiResultsHelper.getLocationLiveData().observe(this, externalCamObserver)
    }

    private fun removeLocationObserver() {
        if (shouldUsePhoneLocation && ::locationManagerImpl.isInitialized)
            locationManagerImpl.subscribeLocation().removeObservers(this)
        else iaiResultsHelper.getLocationLiveData().removeObservers(this)
    }
}
