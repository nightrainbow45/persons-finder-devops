package com.persons.finder.domain.services

import com.persons.finder.data.Location
import com.persons.finder.data.LocationRepository
import com.persons.finder.data.Person
import com.persons.finder.data.PersonRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class ServiceTest {

    @Autowired
    lateinit var personsService: PersonsService

    @Autowired
    lateinit var locationsService: LocationsService

    @Autowired
    lateinit var personRepository: PersonRepository

    @Autowired
    lateinit var locationRepository: LocationRepository

    // PersonsService tests

    @Test
    fun `should save and retrieve a person`() {
        val saved = personsService.save(Person(name = "Alice"))
        val found = personsService.getById(saved.id)
        assertNotNull(found)
        assertEquals("Alice", found!!.name)
    }

    @Test
    fun `should return null for non-existent person`() {
        val found = personsService.getById(999L)
        assertNull(found)
    }

    @Test
    fun `should retrieve multiple persons by ids`() {
        val p1 = personsService.save(Person(name = "Alice"))
        val p2 = personsService.save(Person(name = "Bob"))
        personsService.save(Person(name = "Charlie"))

        val found = personsService.getByIds(listOf(p1.id, p2.id))
        assertEquals(2, found.size)
    }

    @Test
    fun `should return empty list for non-existent ids`() {
        val found = personsService.getByIds(listOf(998L, 999L))
        assertTrue(found.isEmpty())
    }

    // LocationsService tests

    @Test
    fun `should add and retrieve a location`() {
        val person = personsService.save(Person(name = "Alice"))
        locationsService.addLocation(
            Location(referenceId = person.id, latitude = 40.7128, longitude = -74.0060)
        )
        val found = locationsService.getByReferenceId(person.id)
        assertNotNull(found)
        assertEquals(40.7128, found!!.latitude)
    }

    @Test
    fun `should update location when adding for same person`() {
        val person = personsService.save(Person(name = "Alice"))
        locationsService.addLocation(
            Location(referenceId = person.id, latitude = 40.7128, longitude = -74.0060)
        )
        locationsService.addLocation(
            Location(referenceId = person.id, latitude = 51.5074, longitude = -0.1278)
        )
        val found = locationsService.getByReferenceId(person.id)
        assertNotNull(found)
        assertEquals(51.5074, found!!.latitude)
    }

    @Test
    fun `should remove location`() {
        val person = personsService.save(Person(name = "Alice"))
        locationsService.addLocation(
            Location(referenceId = person.id, latitude = 40.7128, longitude = -74.0060)
        )
        locationsService.removeLocation(person.id)
        val found = locationsService.getByReferenceId(person.id)
        assertNull(found)
    }

    // Haversine distance calculation tests

    @Test
    fun `should calculate zero distance for same point`() {
        val impl = locationsService as LocationsServiceImpl
        val distance = impl.calculateDistance(40.7128, -74.0060, 40.7128, -74.0060)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun `should calculate correct distance between New York and London`() {
        // New York: 40.7128, -74.0060
        // London: 51.5074, -0.1278
        // Expected distance: ~5570 km
        val impl = locationsService as LocationsServiceImpl
        val distance = impl.calculateDistance(40.7128, -74.0060, 51.5074, -0.1278)
        assertEquals(5570.0, distance, 50.0) // within 50km tolerance
    }

    @Test
    fun `should calculate correct distance between nearby points`() {
        // Two points ~1.1 km apart in Manhattan
        val impl = locationsService as LocationsServiceImpl
        val distance = impl.calculateDistance(40.7128, -74.0060, 40.7228, -74.0060)
        assertEquals(1.11, distance, 0.1)
    }

    // Nearby person search tests

    @Test
    fun `should find nearby persons within radius`() {
        val alice = personsService.save(Person(name = "Alice"))
        val bob = personsService.save(Person(name = "Bob"))
        val charlie = personsService.save(Person(name = "Charlie"))

        // Alice in Manhattan
        locationsService.addLocation(
            Location(referenceId = alice.id, latitude = 40.7128, longitude = -74.0060)
        )
        // Bob nearby (~1km away)
        locationsService.addLocation(
            Location(referenceId = bob.id, latitude = 40.7138, longitude = -74.0050)
        )
        // Charlie far away (London)
        locationsService.addLocation(
            Location(referenceId = charlie.id, latitude = 51.5074, longitude = -0.1278)
        )

        val nearby = locationsService.findAround(40.7128, -74.0060, 5.0)
        val nearbyIds = nearby.map { it.referenceId }
        assertTrue(nearbyIds.contains(alice.id))
        assertTrue(nearbyIds.contains(bob.id))
        assertFalse(nearbyIds.contains(charlie.id))
    }

    @Test
    fun `should return empty list when no persons nearby`() {
        val nearby = locationsService.findAround(0.0, 0.0, 1.0)
        assertTrue(nearby.isEmpty())
    }
}
