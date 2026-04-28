package com.phq.swl.pioms.domain

import com.phq.swl.pioms.data.PersonDB
import com.phq.swl.pioms.data.PersonRecord
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single
class PersonUseCase(
    private val personDB: PersonDB,
) {
    fun addPerson(
        name: String,
        numImages: Long,
    ): Long =
        personDB.addPerson(
            PersonRecord(
                personName = name,
                numImages = numImages,
                addTime = System.currentTimeMillis(),
            ),
        )

    fun removePerson(id: Long) {
        personDB.removePerson(id)
    }

    fun findByName(name: String): PersonRecord? = personDB.findByName(name)

    fun getAll(): Flow<List<PersonRecord>> = personDB.getAll()

    fun getCount(): Long = personDB.getCount()
}
