package com.persons.finder.data

import io.swagger.v3.oas.annotations.media.Schema
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "persons")
@Schema(description = "Person entity")
data class Person(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Schema(description = "Unique person identifier", example = "1")
    val id: Long = 0,
    
    @field:Schema(description = "Person's full name", example = "John Doe")
    val name: String = ""
)
