package com.persons.finder.presentation

import com.persons.finder.data.Location
import com.persons.finder.data.Person
import com.persons.finder.domain.services.LocationsService
import com.persons.finder.domain.services.PersonsService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

data class CreatePersonRequest(val name: String = "")
data class UpdateLocationRequest(val latitude: Double = 0.0, val longitude: Double = 0.0)
data class NearbyResponse(val personIds: List<Long>)

@RestController
@RequestMapping("api/v1/persons")
class PersonController(
    private val personsService: PersonsService,
    private val locationsService: LocationsService
) {

    @PostMapping("")
    fun createPerson(@RequestBody request: CreatePersonRequest): ResponseEntity<Person> {
        if (request.name.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank")
        }
        val person = personsService.save(Person(name = request.name))
        return ResponseEntity.status(HttpStatus.CREATED).body(person)
    }

    @PutMapping("/{id}/location")
    fun updateLocation(
        @PathVariable id: Long,
        @RequestBody request: UpdateLocationRequest
    ): ResponseEntity<Location> {
        val person = personsService.getById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found with id: $id")

        if (request.latitude < -90 || request.latitude > 90) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude must be between -90 and 90")
        }
        if (request.longitude < -180 || request.longitude > 180) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Longitude must be between -180 and 180")
        }

        val location = Location(
            referenceId = person.id,
            latitude = request.latitude,
            longitude = request.longitude
        )
        locationsService.addLocation(location)
        return ResponseEntity.ok(location)
    }

    @GetMapping("/{id}/nearby")
    fun findNearby(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "10") radius: Double
    ): ResponseEntity<NearbyResponse> {
        val person = personsService.getById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found with id: $id")

        val personLocation = locationsService.getByReferenceId(person.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found for person with id: $id")

        val nearbyLocations = locationsService.findAround(
            personLocation.latitude,
            personLocation.longitude,
            radius
        )

        val nearbyPersonIds = nearbyLocations
            .map { it.referenceId }
            .filter { it != id }

        return ResponseEntity.ok(NearbyResponse(personIds = nearbyPersonIds))
    }

    @GetMapping("")
    fun getPersons(@RequestParam(required = false) ids: String?): ResponseEntity<List<Person>> {
        if (ids.isNullOrBlank()) {
            return ResponseEntity.ok(emptyList())
        }

        val idList = try {
            ids.split(",").map { it.trim().toLong() }
        } catch (e: NumberFormatException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid id format")
        }

        val persons = personsService.getByIds(idList)
        return ResponseEntity.ok(persons)
    }
}
