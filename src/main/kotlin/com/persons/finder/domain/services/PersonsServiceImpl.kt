package com.persons.finder.domain.services

import com.persons.finder.data.Person
import com.persons.finder.data.PersonRepository
import org.springframework.stereotype.Service

@Service
class PersonsServiceImpl(
    private val personRepository: PersonRepository
) : PersonsService {

    override fun getById(id: Long): Person? {
        return personRepository.findById(id).orElse(null)
    }

    override fun getByIds(ids: List<Long>): List<Person> {
        return personRepository.findAllById(ids)
    }

    override fun save(person: Person): Person {
        return personRepository.save(person)
    }
}
