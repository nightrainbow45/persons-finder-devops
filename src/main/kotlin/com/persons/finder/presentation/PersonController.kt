package com.persons.finder.presentation

import com.persons.finder.data.Location
import com.persons.finder.data.Person
import com.persons.finder.domain.services.LocationsService
import com.persons.finder.domain.services.PersonsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@Schema(description = "Request to create a new person")
data class CreatePersonRequest(
    @field:Schema(
        description = "Person's full name",
        example = "John Doe",
        required = true,
        minLength = 1
    )
    val name: String = ""
)
@Schema(description = "Request to update a person's location")
data class UpdateLocationRequest(
    @field:Schema(
        description = "Latitude coordinate",
        example = "-33.8688",
        required = true,
        minimum = "-90",
        maximum = "90"
    )
    val latitude: Double = 0.0,
    
    @field:Schema(
        description = "Longitude coordinate",
        example = "151.2093",
        required = true,
        minimum = "-180",
        maximum = "180"
    )
    val longitude: Double = 0.0
)
@Schema(description = "Response containing nearby person IDs")
data class NearbyResponse(
    @field:Schema(
        description = "List of person IDs within the specified radius",
        example = "[2, 3, 5]"
    )
    val personIds: List<Long>
)

@RestController
@RequestMapping("api/v1/persons")
@Tag(name = "Persons", description = "Person management and location tracking APIs")
class PersonController(
    private val personsService: PersonsService,
    private val locationsService: LocationsService
) {

    @PostMapping("")
    @Operation(
        summary = "Create a new person",
        description = "Creates a new person with the provided name"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "201",
            description = "Person created successfully",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = Person::class),
                examples = [ExampleObject(
                    value = """{"id": 1, "name": "John Doe"}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request - name is blank",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{"timestamp": "2024-01-01T00:00:00", "status": 400, "error": "Bad Request", "message": "Name must not be blank"}"""
                )]
            )]
        )
    ])
    fun createPerson(
        @RequestBody 
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Person creation request",
            required = true,
            content = [Content(
                schema = Schema(implementation = CreatePersonRequest::class),
                examples = [ExampleObject(
                    value = """{"name": "John Doe"}"""
                )]
            )]
        )
        request: CreatePersonRequest
    ): ResponseEntity<Person> {
        if (request.name.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank")
        }
        val person = personsService.save(Person(name = request.name))
        return ResponseEntity.status(HttpStatus.CREATED).body(person)
    }

    @PutMapping("/{id}/location")
    @Operation(
        summary = "Update person's location",
        description = "Updates the geographic location for a specific person"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Location updated successfully",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = Location::class),
                examples = [ExampleObject(
                    value = """{"referenceId": 1, "latitude": -33.8688, "longitude": 151.2093}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Person not found"
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid coordinates"
        )
    ])
    fun updateLocation(
        @PathVariable 
        @io.swagger.v3.oas.annotations.Parameter(description = "Person ID", example = "1")
        id: Long,
        @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Location update request",
            required = true,
            content = [Content(
                schema = Schema(implementation = UpdateLocationRequest::class),
                examples = [ExampleObject(
                    value = """{"latitude": -33.8688, "longitude": 151.2093}"""
                )]
            )]
        )
        request: UpdateLocationRequest
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
    @Operation(
        summary = "Find nearby persons",
        description = "Finds all persons within a specified radius of a given person's location"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Nearby persons found",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = NearbyResponse::class),
                examples = [ExampleObject(
                    value = """{"personIds": [2, 3, 5]}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Person or location not found"
        )
    ])
    fun findNearby(
        @PathVariable 
        @io.swagger.v3.oas.annotations.Parameter(description = "Person ID", example = "1")
        id: Long,
        @RequestParam(defaultValue = "10")
        @io.swagger.v3.oas.annotations.Parameter(
            description = "Search radius in kilometers",
            example = "10",
            schema = Schema(defaultValue = "10", minimum = "0")
        )
        radius: Double
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
    @Operation(
        summary = "Get persons by IDs",
        description = "Retrieves multiple persons by their IDs"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Persons retrieved successfully",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = Person::class, type = "array"),
                examples = [ExampleObject(
                    value = """[{"id": 1, "name": "John Doe"}, {"id": 2, "name": "Jane Smith"}]"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid ID format"
        )
    ])
    fun getPersons(
        @RequestParam(required = false)
        @io.swagger.v3.oas.annotations.Parameter(
            description = "Comma-separated list of person IDs",
            example = "1,2,3",
            required = false
        )
        ids: String?
    ): ResponseEntity<List<Person>> {
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
