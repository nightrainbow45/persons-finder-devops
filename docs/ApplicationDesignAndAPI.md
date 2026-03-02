# Requirement 1: Application Design & API

> **Requirement:** Build a RESTful API service (Spring Boot preferred). Implement at least 3 endpoints. Use proper HTTP status codes. Bonus: OpenAPI/Swagger documentation.

---

## Quick Reference

**Requirement vs Implementation**

| Requirement | Current Status |
|---|---|
| RESTful API service (Spring Boot) | ✅ Spring Boot 2.7.x, Kotlin, `@RestController` at `api/v1/persons` |
| At least 3 endpoints | ✅ 4 endpoints: POST, PUT, GET (nearby), GET (by IDs) |
| Proper HTTP status codes | ✅ 201 Created, 200 OK, 400 Bad Request, 404 Not Found |
| 3-layer architecture | ✅ `presentation/` → `domain/services/` → `data/` |
| OpenAPI/Swagger (bonus) | ✅ springdoc-openapi, live Swagger UI, env-switchable |

**Code Snippet Source Map**

| Snippet | Source |
|---|---|
| Controller class declaration | `PersonController.kt` lines 58–64 |
| `POST /api/v1/persons` — create person | `PersonController.kt` lines 66–113 |
| `PUT /api/v1/persons/{id}/location` — update location | `PersonController.kt` lines 115–175 |
| `GET /api/v1/persons/{id}/nearby` — find nearby | `PersonController.kt` lines 177–228 |
| `GET /api/v1/persons` — get by IDs | `PersonController.kt` lines 230–274 |
| Haversine formula | `LocationsServiceImpl.kt` lines 45–53 |
| `Person` JPA entity | `Person.kt` lines 1–21 |
| `Location` JPA entity + lat/lon index | `Location.kt` lines 1–42 |
| `PersonsService` interface | `PersonsService.kt` lines 1–7 |
| `LocationsService` interface | `LocationsService.kt` lines 1–8 |
| SpringDoc config | `application.properties` lines 19–30 |
| OpenAPI bean (title, version, contact) | `OpenAPIConfig.kt` lines 1–43 |
| CORS configuration | `CorsConfig.kt` lines 1–51 |

---

## 1. What Was Asked

| Requirement item | Description |
|---|---|
| Framework | Spring Boot preferred |
| Minimum endpoints | At least 3 RESTful endpoints |
| HTTP status codes | Use proper codes (2xx, 4xx) |
| Swagger / OpenAPI | Bonus: API documentation |

---

## 2. What Was Implemented

The requirement is **fully satisfied** — 4 REST endpoints, correct HTTP semantics, full OpenAPI documentation, and a clean 3-layer architecture. Both mandatory items and the bonus item are implemented.

---

### 2.1 Three-Layer Architecture

```
src/main/kotlin/com/persons/finder/
├── presentation/          ← REST layer: PersonController (HTTP in/out)
├── domain/services/       ← Business logic: PersonsService, LocationsService + Haversine
└── data/                  ← JPA entities + Spring Data repositories
```

Each layer depends only on the layer below it via interfaces (`PersonsService`, `LocationsService`), making the business logic independently testable.

---

### 2.2 REST Controller

> `src/main/kotlin/com/persons/finder/presentation/PersonController.kt` lines 58–64

```kotlin
@RestController                                        // line 58
@RequestMapping("api/v1/persons")                      // line 59 — base path
@Tag(name = "Persons", description = "Person management and location tracking APIs")
class PersonController(
    private val personsService: PersonsService,        // injected via constructor
    private val locationsService: LocationsService
)
```

---

### 2.3 Endpoint 1 — Create Person

**`POST /api/v1/persons`** → HTTP 201 Created

> `PersonController.kt` lines 66–113

```kotlin
@PostMapping("")                                       // line 66
fun createPerson(request: CreatePersonRequest): ResponseEntity<Person> {
    if (request.name.isBlank()) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank")  // 400
    }
    val person = personsService.save(Person(name = request.name))
    return ResponseEntity.status(HttpStatus.CREATED).body(person)  // 201
}
```

| Condition | HTTP Status |
|---|---|
| Valid name | 201 Created + `{ "id": 1, "name": "John Doe" }` |
| Blank name | 400 Bad Request |

---

### 2.4 Endpoint 2 — Update Location

**`PUT /api/v1/persons/{id}/location`** → HTTP 200 OK

> `PersonController.kt` lines 115–175

```kotlin
@PutMapping("/{id}/location")                          // line 115
fun updateLocation(@PathVariable id: Long, request: UpdateLocationRequest): ResponseEntity<Location> {
    val person = personsService.getById(id)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found with id: $id")  // 404

    if (request.latitude < -90 || request.latitude > 90) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude must be between -90 and 90")  // 400
    }
    if (request.longitude < -180 || request.longitude > 180) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Longitude must be between -180 and 180")  // 400
    }

    val location = Location(referenceId = person.id, latitude = request.latitude, longitude = request.longitude)
    locationsService.addLocation(location)
    return ResponseEntity.ok(location)                 // 200
}
```

| Condition | HTTP Status |
|---|---|
| Valid person + valid coordinates | 200 OK + location body |
| Person not found | 404 Not Found |
| Latitude out of range (−90..90) | 400 Bad Request |
| Longitude out of range (−180..180) | 400 Bad Request |

---

### 2.5 Endpoint 3 — Find Nearby Persons

**`GET /api/v1/persons/{id}/nearby?radius=10`** → HTTP 200 OK

> `PersonController.kt` lines 177–228

```kotlin
@GetMapping("/{id}/nearby")                            // line 177
fun findNearby(
    @PathVariable id: Long,
    @RequestParam(defaultValue = "10") radius: Double  // line 203 — default 10 km
): ResponseEntity<NearbyResponse> {
    val person = personsService.getById(id)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, ...)   // 404

    val personLocation = locationsService.getByReferenceId(person.id)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, ...)   // 404

    val nearbyLocations = locationsService.findAround(
        personLocation.latitude, personLocation.longitude, radius     // delegates to Haversine
    )
    val nearbyPersonIds = nearbyLocations
        .map { it.referenceId }
        .filter { it != id }                           // exclude the querying person

    return ResponseEntity.ok(NearbyResponse(personIds = nearbyPersonIds))  // 200
}
```

| Condition | HTTP Status |
|---|---|
| Person found + has location | 200 OK + `{ "personIds": [2, 3] }` |
| Person not found | 404 Not Found |
| Person has no location | 404 Not Found |

---

### 2.6 Endpoint 4 — Get Persons by IDs

**`GET /api/v1/persons?ids=1,2,3`** → HTTP 200 OK

> `PersonController.kt` lines 230–274

```kotlin
@GetMapping("")                                        // line 230
fun getPersons(@RequestParam(required = false) ids: String?): ResponseEntity<List<Person>> {
    if (ids.isNullOrBlank()) {
        return ResponseEntity.ok(emptyList())          // 200 — empty list, not 400
    }
    val idList = try {
        ids.split(",").map { it.trim().toLong() }
    } catch (e: NumberFormatException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid id format")  // 400
    }
    val persons = personsService.getByIds(idList)
    return ResponseEntity.ok(persons)                  // 200
}
```

---

### 2.7 Data Models (JPA Entities)

**`Person`** — `data/Person.kt` lines 1–21

```kotlin
@Entity
@Table(name = "persons")
data class Person(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val name: String = ""
)
```

**`Location`** — `data/Location.kt` lines 1–42

```kotlin
@Entity
@Table(
    name = "locations",
    indexes = [Index(name = "idx_location_lat_lon", columnList = "latitude,longitude")]  // line 14
)
data class Location(
    @Id @Column(name = "reference_id")
    val referenceId: Long = 0,    // 1-to-1 FK to Person.id; no separate surrogate PK
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
```

The composite index on `(latitude, longitude)` improves spatial query performance. `referenceId` serves as both PK and FK — each person has at most one active location.

---

### 2.8 Haversine Distance Formula

> `src/main/kotlin/com/persons/finder/domain/services/LocationsServiceImpl.kt` lines 45–53

```kotlin
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {  // line 45
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_KM * c                 // EARTH_RADIUS_KM = 6371.0
}
```

Used by `findAround()` (line 31): fetches all locations, applies Haversine filter in-memory, returns matches within `radiusInKm`.

---

### 2.9 OpenAPI / Swagger Documentation (Bonus)

**Configuration** — `src/main/resources/application.properties` lines 19–30:

```properties
springdoc.api-docs.path=/v3/api-docs          # line 20 — JSON spec endpoint
springdoc.swagger-ui.path=/swagger-ui.html    # line 21 — Swagger UI
springdoc.swagger-ui.tryItOutEnabled=true     # line 24 — "Try it out" enabled by default
springdoc.packages-to-scan=com.persons.finder.presentation
springdoc.paths-to-match=/api/v1/**

springdoc.api-docs.enabled=${SWAGGER_ENABLED:true}   # line 29 — env-switchable
springdoc.swagger-ui.enabled=${SWAGGER_ENABLED:true}
```

**OpenAPI bean** — `src/main/kotlin/com/persons/finder/config/OpenAPIConfig.kt` lines 1–43:

```kotlin
@Bean
fun customOpenAPI(): OpenAPI {
    return OpenAPI()
        .info(Info()
            .title(title)          // injected from API_TITLE env var
            .version(version)      // injected from API_VERSION env var
            .description(description)
            .contact(Contact().name("DevOps Team").email("devops@example.com"))
            .license(License().name("Apache 2.0")))
}
```

Every endpoint is annotated with `@Operation`, `@ApiResponses`, `@ApiResponse`, `@Schema`, and `@ExampleObject` — producing a fully documented interactive spec. In production, `SWAGGER_ENABLED=false` disables both the UI and the JSON spec endpoint without a code change.

---

### 2.10 Service Interfaces (Dependency Inversion)

> `domain/services/PersonsService.kt`

```kotlin
interface PersonsService {
    fun getById(id: Long): Person?
    fun getByIds(ids: List<Long>): List<Person>
    fun save(person: Person): Person
}
```

> `domain/services/LocationsService.kt`

```kotlin
interface LocationsService {
    fun addLocation(location: Location)
    fun removeLocation(locationReferenceId: Long)
    fun findAround(latitude: Double, longitude: Double, radiusInKm: Double): List<Location>
    fun getByReferenceId(referenceId: Long): Location?
}
```

The controller depends on interfaces, not concrete implementations. Spring injects `PersonsServiceImpl` and `LocationsServiceImpl` at runtime.

---

## 3. Beyond the Minimum

| Additional Feature | Description |
|---|---|
| CORS configuration | `CorsConfig.kt` — configurable via `CORS_ALLOWED_ORIGINS` env var; separate rules for `/api/**` and `/v3/api-docs/**` |
| Actuator health probes | `application.properties` lines 10–14 — `/actuator/health` with liveness/readiness states for K8s probes |
| Composite DB index | `Location.kt` line 14 — `(latitude, longitude)` index for spatial query performance |
| Input validation | Coordinate range checks (lat −90..90, lon −180..180) + blank name rejection |
| 13 controller integration tests | `PersonControllerTest.kt` — full MockMvc tests covering all endpoints and error paths |
| 15 service/domain unit tests | `ServiceTest.kt` — Haversine accuracy tests (NY→London distance), radius filter tests |

---

## 4. Tests

`PersonControllerTest.kt` (185 lines, 13 `@Test` methods) — `@SpringBootTest + @AutoConfigureMockMvc`, full HTTP round-trip via MockMvc:

| Test | Endpoint | Asserts |
|---|---|---|
| `POST should create a person and return 201` | POST | status 201, name, id is number |
| `POST should return 400 for blank name` | POST | status 400 |
| `PUT location should update person location` | PUT | status 200, lat/lon values |
| `PUT location should return 404 for non-existent person` | PUT | status 404 |
| `PUT location should return 400 for invalid latitude` | PUT | status 400 |
| `PUT location should return 400 for invalid longitude` | PUT | status 400 |
| `GET nearby should return nearby person ids` | GET nearby | correct IDs returned |
| `GET nearby should return 404 for non-existent person` | GET nearby | status 404 |
| `GET nearby should return 404 when person has no location` | GET nearby | status 404 |
| `GET persons should return persons by ids` | GET | correct person list |
| `GET persons should return empty list when no ids provided` | GET | empty array |
| `GET persons should handle missing persons gracefully` | GET | partial result |
| `GET persons should return 400 for invalid id format` | GET | status 400 |

---

## 5. File Map

| File | Lines | Purpose |
|---|---|---|
| `presentation/PersonController.kt` | 274 | All 4 REST endpoints + request/response DTOs |
| `domain/services/PersonsService.kt` | 7 | Interface: person CRUD |
| `domain/services/PersonsServiceImpl.kt` | 23 | Implementation: JPA repository delegation |
| `domain/services/LocationsService.kt` | 8 | Interface: location add/remove/find |
| `domain/services/LocationsServiceImpl.kt` | 54 | Implementation: Haversine filter + @Transactional |
| `data/Person.kt` | 21 | JPA entity: id + name |
| `data/Location.kt` | 42 | JPA entity: referenceId + lat/lon + composite index |
| `config/OpenAPIConfig.kt` | 43 | OpenAPI bean: title, version, contact, license |
| `config/CorsConfig.kt` | 51 | CORS: env-configurable allowed origins |
| `src/main/resources/application.properties` | 39 | H2 datasource, springdoc, actuator, CORS config |
| `test/.../PersonControllerTest.kt` | 185 | 13 integration tests (MockMvc) |
| `test/.../domain/services/ServiceTest.kt` | — | 15 unit tests: Haversine accuracy, radius filter |
