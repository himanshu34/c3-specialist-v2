package co.nayan.nayancamv2

import co.nayan.c3v2.core.location.LocationManagerImpl
import org.junit.Before
import org.junit.Test
import javax.inject.Inject

/**
 * Created by Vivek Panchal on 4/1/2022
 * https://vivekpanchal.dev
 * Copyright (c) 4/1/2022 . All rights reserved.
 */


class LocationTest {

    @Inject
    var locationManagerImpl: LocationManagerImpl? = null


    @Before
    fun setUp() {
        locationManagerImpl?.startReceivingLocationUpdate()
    }


    @Test
    fun `When location is fetched`() {
        assert(locationManagerImpl?.subscribeLocation() != null)
    }

}