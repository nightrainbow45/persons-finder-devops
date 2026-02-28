package com.persons.finder.presentation

import com.persons.finder.data.Location
import com.persons.finder.data.Person
import com.persons.finder.domain.services.LocationsService
import com.persons.finder.domain.services.PersonsService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PersonControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var personsService: PersonsService

    @Autowired
    lateinit var locationsService: LocationsService

    // POST /api/v1/persons tests

    @Test
    fun `POST should create a person and return 201`() {
        mockMvc.perform(
            post("/api/v1/persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "John"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("John"))
            .andExpect(jsonPath("$.id").isNumber)
    }

    @Test
    fun `POST should return 400 for blank name`() {
        mockMvc.perform(
            post("/api/v1/persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": ""}""")
        )
            .andExpect(status().isBadRequest)
    }

    // PUT /api/v1/persons/{id}/location tests

    @Test
    fun `PUT location should update person location`() {
        val person = personsService.save(Person(name = "John"))
        mockMvc.perform(
            put("/api/v1/persons/${person.id}/location")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"latitude": 40.7128, "longitude": -74.0060}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.referenceId").value(person.id))
            .andExpect(jsonPath("$.latitude").value(40.7128))
            .andExpect(jsonPath("$.longitude").value(-74.0060))
    }

    @Test
    fun `PUT location should return 404 for non-existent person`() {
        mockMvc.perform(
            put("/api/v1/persons/999/location")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"latitude": 40.7128, "longitude": -74.0060}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PUT location should return 400 for invalid latitude`() {
        val person = personsService.save(Person(name = "John"))
        mockMvc.perform(
            put("/api/v1/persons/${person.id}/location")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"latitude": 91.0, "longitude": -74.0060}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PUT location should return 400 for invalid longitude`() {
        val person = personsService.save(Person(name = "John"))
        mockMvc.perform(
            put("/api/v1/persons/${person.id}/location")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"latitude": 40.7128, "longitude": 181.0}""")
        )
            .andExpect(status().isBadRequest)
    }

    // GET /api/v1/persons/{id}/nearby tests

    @Test
    fun `GET nearby should return nearby person ids`() {
        val john = personsService.save(Person(name = "John"))
        val jane = personsService.save(Person(name = "Jane"))
        val far = personsService.save(Person(name = "Far"))

        locationsService.addLocation(Location(referenceId = john.id, latitude = 40.7128, longitude = -74.0060))
        locationsService.addLocation(Location(referenceId = jane.id, latitude = 40.7138, longitude = -74.0050))
        locationsService.addLocation(Location(referenceId = far.id, latitude = 51.5074, longitude = -0.1278))

        mockMvc.perform(
            get("/api/v1/persons/${john.id}/nearby")
                .param("radius", "5")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.personIds").isArray)
            .andExpect(jsonPath("$.personIds[0]").value(jane.id))
    }

    @Test
    fun `GET nearby should return 404 for non-existent person`() {
        mockMvc.perform(
            get("/api/v1/persons/999/nearby")
                .param("radius", "10")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET nearby should return 404 when person has no location`() {
        val person = personsService.save(Person(name = "John"))
        mockMvc.perform(
            get("/api/v1/persons/${person.id}/nearby")
                .param("radius", "10")
        )
            .andExpect(status().isNotFound)
    }

    // GET /api/v1/persons tests

    @Test
    fun `GET persons should return persons by ids`() {
        val p1 = personsService.save(Person(name = "Alice"))
        val p2 = personsService.save(Person(name = "Bob"))

        mockMvc.perform(
            get("/api/v1/persons")
                .param("ids", "${p1.id},${p2.id}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").isString)
    }

    @Test
    fun `GET persons should return empty list when no ids provided`() {
        mockMvc.perform(get("/api/v1/persons"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET persons should handle missing persons gracefully`() {
        val p1 = personsService.save(Person(name = "Alice"))
        mockMvc.perform(
            get("/api/v1/persons")
                .param("ids", "${p1.id},999")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `GET persons should return 400 for invalid id format`() {
        mockMvc.perform(
            get("/api/v1/persons")
                .param("ids", "abc,def")
        )
            .andExpect(status().isBadRequest)
    }
}
