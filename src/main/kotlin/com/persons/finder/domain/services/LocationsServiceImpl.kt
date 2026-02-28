package com.persons.finder.domain.services

import com.persons.finder.data.Location
import com.persons.finder.data.LocationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class LocationsServiceImpl(
    private val locationRepository: LocationRepository
) : LocationsService {

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0
    }

    @Transactional
    override fun addLocation(location: Location) {
        locationRepository.save(location)
    }

    @Transactional
    override fun removeLocation(locationReferenceId: Long) {
        locationRepository.deleteByReferenceId(locationReferenceId)
    }

    override fun getByReferenceId(referenceId: Long): Location? {
        return locationRepository.findByReferenceId(referenceId)
    }

    override fun findAround(latitude: Double, longitude: Double, radiusInKm: Double): List<Location> {
        return locationRepository.findAll().filter { location ->
            calculateDistance(latitude, longitude, location.latitude, location.longitude) <= radiusInKm
        }
    }

    /**
     * Calculates the distance between two points on Earth using the Haversine formula.
     * @return distance in kilometers
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }
}
