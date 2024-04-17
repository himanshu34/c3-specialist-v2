package co.nayan.c3v2.core.location

import android.location.GpsStatus
import android.util.Log

class NmeaParser : GpsStatus.NmeaListener {

    @Deprecated("Deprecated in Java")
    override fun onNmeaReceived(timestamp: Long, nmea: String) {
        parseNmeaString(nmea)
    }

    private fun parseNmeaString(nmea: String) {
        // Implement your NMEA parsing logic here
        // Extract GPS coordinates and other relevant information
        // Example: $GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47
        // Example: $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A
        // Parse the GGA and RMC sentences to get latitude, longitude, and other data
        Log.d("LocationManagerImpl", "NmeaParser -> $nmea")
    }
}