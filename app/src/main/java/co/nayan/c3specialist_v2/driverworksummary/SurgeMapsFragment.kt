package co.nayan.c3specialist_v2.driverworksummary

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.getDistanceInMeter
import co.nayan.c3specialist_v2.getMarkerBitmap
import co.nayan.c3specialist_v2.impl.CityKmlManagerImpl
import co.nayan.c3v2.core.models.Coordinates
import co.nayan.c3v2.core.models.MapData
import co.nayan.c3v2.core.models.SurgeLocation
import co.nayan.c3v2.core.models.SurgeLocationsResponse
import co.nayan.c3v2.core.showToast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SurgeMapsFragment : BaseFragment(R.layout.fragment_object_of_interest_maps) {

    private val viewModel: ObjectOfInterestMapsViewModel by activityViewModels()
    private lateinit var googleMap: GoogleMap

    @Inject
    lateinit var cityKmlManagerImpl: CityKmlManagerImpl

    private val callback = OnMapReadyCallback { googleMap ->
        /**
         * Manipulates the map once available.
         * This callback is triggered when the map is ready to be used.
         * This is where we can add markers or lines, add listeners or move the camera.
         * In this case, we just add a marker near Sydney, Australia.
         * If Google Play services is not installed on the device, the user will be prompted to
         * install it inside the SupportMapFragment. This method will only be triggered once the
         * user has installed Google Play services and returned to the app.
         */
        /**
         * Manipulates the map once available.
         * This callback is triggered when the map is ready to be used.
         * This is where we can add markers or lines, add listeners or move the camera.
         * In this case, we just add a marker near Sydney, Australia.
         * If Google Play services is not installed on the device, the user will be prompted to
         * install it inside the SupportMapFragment. This method will only be triggered once the
         * user has installed Google Play services and returned to the app.
         */
        this.googleMap = googleMap
        viewModel.surgeLocationResponse.observe(viewLifecycleOwner, surgeObserver)
        cityKmlManagerImpl.subscribe().observe(viewLifecycleOwner, wardBoundariesObserver)
        viewModel.fetchSurgeLocations()
        enableMyLocation()
        googleMap.setOnMarkerClickListener { marker ->
            val latLng = marker.position
            val gmmIntentUri =
                Uri.parse("google.navigation:q=${latLng.latitude},${latLng.longitude}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            mapIntent.resolveActivity(requireActivity().packageManager)?.let {
                startActivity(mapIntent)
            } ?: run { requireContext().showToast("Google Navigation app not found") }

            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(callback)
    }

    private val wardBoundariesObserver: Observer<MutableList<MapData>> = Observer { mapData ->
        mapData.map { currentWard ->
            currentWard.geometry?.coordinates?.let { wardCoordinates ->
                googleMap.addPolygon(drawPolygon(wardCoordinates))
            }
        }
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

    private val surgeObserver: Observer<SurgeLocationsResponse?> = Observer { response ->
        val locations = response?.surgeLocations ?: mutableListOf()
        drawSurgeOnMap(requireContext(), locations)
    }

    private fun drawSurgeOnMap(
        context: Context,
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
                coordinate.iconUrl?.let { image ->
                    context.getMarkerBitmap(image, iconSize)
                } ?: run { null }
            }
            withContext(Dispatchers.Main) {
                val markerOptions = MarkerOptions().position(latLng).title(coordinate.description)
                bitmap?.let {
                    val markerView =
                        View.inflate(requireContext(), R.layout.view_custom_marker, null)
                    val imgEvent = markerView.findViewById<ImageView>(R.id.img_event)
                    imgEvent.setImageBitmap(bitmap)
                    val bitmapDescriptor =
                        BitmapDescriptorFactory.fromBitmap(getBitmapFromView(markerView))
                    markerOptions.icon(bitmapDescriptor)
                }
                googleMap.addMarker(markerOptions)
                googleMap.addCircle(drawBounds(latLng, coordinate.radius!!.toDouble()))
            }
        }
    }

    private fun drawBounds(point: LatLng, radius: Double): CircleOptions {
        return CircleOptions().center(point).radius(radius) // In meters
            .strokeWidth(4f)
            .strokeColor(Color.RED)
            .fillColor(0x22FF0000)
    }

    private fun enableMyLocation() {
        val accessFineLocationPermission =
            ContextCompat.checkSelfPermission(requireContext(), ACCESS_FINE_LOCATION)
        val courseFineLocationPermission =
            ContextCompat.checkSelfPermission(requireContext(), ACCESS_COARSE_LOCATION)
        if (accessFineLocationPermission != PackageManager.PERMISSION_GRANTED
            && courseFineLocationPermission != PackageManager.PERMISSION_GRANTED
        ) {
            val permissions = arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
            requestPermissionsLauncher.launch(permissions)
            return
        }

        val location = viewModel.getLastLocation()
        if (::googleMap.isInitialized) {
            googleMap.isMyLocationEnabled = true
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 10.0f))
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() = SurgeMapsFragment()
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.map {
                Timber.d("Permissions requested --> ${it.key} = ${it.value}")
                if (it.key == ACCESS_FINE_LOCATION && it.value) enableMyLocation()
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
}