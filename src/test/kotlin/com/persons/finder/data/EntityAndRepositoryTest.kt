package com.persons.finder.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@DataJpaTest
class EntityAndRepositoryTest {

    @Autowired
    lateinit var personRepository: PersonRepository

    @Autowired
    lateinit var locationRepository: LocationRepository

    @Test
    fun `should create and persist a Person entity`() {
        val person = personRepository.save(Person(name = "Alice"))
        assertNotNull(person.id)
        assertTrue(person.id > 0)
        assertEquals("Alice", person.name)
    }

    @Test
    fun `should find Person by id`() {
        val saved = personRepository.save(Person(name = "Bob"))
        val found = personRepository.findById(saved.id)
        assertTrue(found.isPresent)
        assertEquals("Bob", found.get().name)
    }

    @Test
    fun `should return empty when Person not found`() {
        val found = personRepository.findById(999L)
        assertFalse(found.isPresent)
    }

    @Test
    fun `should find all persons by ids`() {
        val p1 = personRepository.save(Person(name = "Alice"))
        val p2 = personRepository.save(Person(name = "Bob"))
        personRepository.save(Person(name = "Charlie"))

        val found = personRepository.findAllById(listOf(p1.id, p2.id))
        assertEquals(2, found.size)
    }

    @Test
    fun `should create and persist a Location entity`() {
        val person = personRepository.save(Person(name = "Alice"))
        val location = locationRepository.save(
            Location(referenceId = person.id, latitude = 40.7128, longitude = -74.0060)
        )
        assertEquals(person.id, location.referenceId)
        assertEquals(40.7128, location.latitude)
        assertEquals(-74.0060, location.longitude)
    }

    @Test
    fun `should find Location by referenceId`() {
        val person = personRepository.save(Person(name = "Alice"))
        locationRepository.save(
            Location(referenceId = person.id, latitude = 51.5074, longitude = -0.1278)
        )
        val found = locationRepository.findByReferenceId(person.id)
        assertNotNull(found)
        assertEquals(51.5074, found!!.latitude)
    }

    @Test
    fun `should return null when Location not found by referenceId`() {
        val found = locationRepository.findByReferenceId(999L)
        assertNull(found)
    }

    @Test
    fun `should update existing Location`() {
        val person = personRepository.save(Person(name = "Alice"))
        locationRepository.save(
            Location(referenceId = person.id, latitude = 40.7128, longitude = -74.0060)
        )
        locationRepository.save(
            Location(referenceId = person.id, latitude = 51.5074, longitude = -0.1278)
        )
        val found = locationRepository.findByReferenceId(person.id)
        assertNotNull(found)
        assertEquals(51.5074, found!!.latitude)
        assertEquals(-0.1278, found.longitude)
    }
}
