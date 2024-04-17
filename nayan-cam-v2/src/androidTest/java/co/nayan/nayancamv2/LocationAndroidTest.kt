package co.nayan.nayancamv2

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.nayan.c3v2.core.location.LocationManagerImpl
import com.nayan.nayancamv2.util.getOrAwaitValue
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Created by Vivek Panchal on 4/4/2022
 * https://vivekpanchal.dev
 * Copyright (c) 4/4/2022 . All rights reserved.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LocationAndroidTest {

    private lateinit var locationManagerImpl: LocationManagerImpl

    @Before
    fun setup() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
//        Assert.assertEquals("co.nayan.nayancamv2.test", appContext.packageName)
        locationManagerImpl = LocationManagerImpl(appContext)
        locationManagerImpl.startReceivingLocationUpdate()
        Log.d(TAG, "setup: setup is done")
    }

    @Test
    fun `1_checkAppContext`() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Assert.assertEquals("co.nayan.nayancamv2.test", appContext.packageName)
        println("Application context ${appContext.packageName}")
        Log.d(TAG, "Application context ${appContext.packageName}")
    }

    @Test
    fun `2_checkIfLocationManagerNotNull`() {
        assert(locationManagerImpl != null)
        Log.d(TAG, "Location manager is not null starting receiving location updates now")
        locationManagerImpl.startReceivingLocationUpdate()
    }

    @Test
    fun `3_getLocation`() {
        Assert.assertThat(locationManagerImpl.subscribeLocation().getOrAwaitValue(), notNullValue())
        val location = locationManagerImpl.subscribeLocation().getOrAwaitValue()
        Log.d(TAG, "location manager location values :: $location")

    }


    @Test
    fun `4_getLocationFromGeoCoder`() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val location = locationManagerImpl.subscribeLocation().getOrAwaitValue()
//        Assert.assertThat(
//            appContext.getUserLocationInfo(location).validLocation,
//            notNullValue()
//        )
        Log.d(
            TAG,
            "4_getLocationFromGeoCoder: location values :: ${locationManagerImpl.subscribeLocation().getOrAwaitValue()}"
        )
    }


    companion object {
        private const val TAG = "LocationAndroidTest"
    }
}