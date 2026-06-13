// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.Contact
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to device contacts via [android.provider.ContactsContract]
 * (§8/§14). Observable reads use [Flow] (backed by a ContentObserver); mutations
 * are `suspend` and return [AppResult].
 */
interface ContactsRepository {

    /**
     * Observe all contacts (optionally filtered to [accountType], e.g. "com.google").
     * Emits a fresh list whenever the provider notifies a change.
     */
    fun observeContacts(accountType: String? = null): Flow<List<Contact>>

    /** Observe a single contact by its stable [lookupKey], or null if removed. */
    fun observeContact(lookupKey: String): Flow<Contact?>

    /** One-shot search across display name + numbers for [query]. */
    suspend fun searchContacts(query: String): AppResult<List<Contact>>

    /** Resolve the contact owning [number] (E.164 or raw), or null when unknown. */
    suspend fun findByNumber(number: String): AppResult<Contact?>

    /** Create a contact and return its new [Contact.lookupKey]. */
    suspend fun createContact(displayName: String, number: String): AppResult<String>

    /** Update an existing contact's fields. */
    suspend fun updateContact(contact: Contact): AppResult<Unit>

    /** Delete the contact identified by [lookupKey]. */
    suspend fun deleteContact(lookupKey: String): AppResult<Unit>

    /** Set the [number] as the default for the contact identified by [lookupKey]. */
    suspend fun setDefaultNumber(lookupKey: String, number: String): AppResult<Unit>
}
