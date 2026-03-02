package com.persons.finder.data

import io.swagger.v3.oas.annotations.media.Schema
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

@Entity
@Table(
    name = "locations",
    indexes = [
        Index(name = "idx_location_lat_lon", columnList = "latitude,longitude")
    ]
)
@Schema(description = "Geographic location associated with a person")
data class Location(
    @Id
    @Column(name = "reference_id")
    @field:Schema(
        description = "Reference to person ID",
        example = "1"
    )
    val referenceId: Long = 0,
    
    @field:Schema(
        description = "Latitude coordinate",
        example = "-33.8688",
        minimum = "-90",
        maximum = "90"
    )
    val latitude: Double = 0.0,
    
    @field:Schema(
        description = "Longitude coordinate",
        example = "151.2093",
        minimum = "-180",
        maximum = "180"
    )
    val longitude: Double = 0.0
)
