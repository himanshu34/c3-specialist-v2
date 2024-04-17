package co.nayan.c3specialist_v2.impl

import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.models.CityKmlResponse
import co.nayan.c3v2.core.models.CityWards
import co.nayan.c3v2.core.models.MapData
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job

interface ICityKmlManager {

    suspend fun getCityKmlBoundaries(): MutableList<MapData>
    suspend fun fetchCityWards(
        shouldFreshDownload: Boolean,
        location: LatLng,
        cityWards: MutableList<CityWards>
    ): Job

    suspend fun fetchCityWardSpecs(cityName: String?): CityKmlResponse?
    fun subscribe(): MutableLiveData<MutableList<MapData>>
}