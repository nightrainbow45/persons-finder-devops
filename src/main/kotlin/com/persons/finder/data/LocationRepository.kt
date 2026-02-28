package com.persons.finder.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LocationRepository : JpaRepository<Location, Long> {
    fun findByReferenceId(referenceId: Long): Location?
    fun deleteByReferenceId(referenceId: Long)
}
