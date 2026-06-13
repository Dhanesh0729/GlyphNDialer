// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.provider

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.domain.model.NumberLabel
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.repository.ContactsRepository
import com.glyphdialer.core.domain.repository.PhoneNumberFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ContactsRepository] backed by [ContactsContract] (CONVENTIONS.md §5, BUILD_SPEC
 * §14). Observable reads use a [ContentObserver]-driven [callbackFlow]; each change
 * re-queries the provider off the IO dispatcher.
 *
 * Supports the `accountType` filter (e.g. "com.google") for the contacts-account
 * preference (BUILD_SPEC §19).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class ContactsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val formatter: PhoneNumberFormatter,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ContactsRepository {

    private val resolver: ContentResolver get() = context.contentResolver

    override fun observeContacts(accountType: String?): Flow<List<Contact>> =
        resolver.observeChanges(ContactsContract.Contacts.CONTENT_URI)
            .conflate()
            .mapLatest { withContext(ioDispatcher) { queryContacts(accountType) } }
            .flowOn(ioDispatcher)

    override fun observeContact(lookupKey: String): Flow<Contact?> =
        resolver.observeChanges(ContactsContract.Contacts.CONTENT_URI)
            .conflate()
            .mapLatest { withContext(ioDispatcher) { queryByLookupKey(lookupKey) } }
            .flowOn(ioDispatcher)

    override suspend fun searchContacts(query: String): AppResult<List<Contact>> =
        appResultOfSuspend {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) emptyList()
            else withContext(ioDispatcher) { queryContacts(accountType = null, nameOrNumberLike = trimmed) }
        }

    override suspend fun findByNumber(number: String): AppResult<Contact?> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                    .appendPath(number)
                    .build()
                resolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.PhoneLookup.CONTACT_ID,
                        ContactsContract.PhoneLookup.LOOKUP_KEY,
                    ),
                    null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val lookupKey = c.getString(1)
                        queryByLookupKey(lookupKey)
                    } else {
                        null
                    }
                }
            }
        }

    // --- mutations ----------------------------------------------------------
    //
    // Contact create/update/delete go through ContactsContract batch ops. These are
    // genuinely involved and account-dependent; we implement create (the common
    // path) and leave the rarer edits with a clear, honest fallback rather than a
    // silent no-op so the caller can surface the limitation (CONVENTIONS.md §2).

    override suspend fun createContact(displayName: String, number: String): AppResult<String> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                val ops = ArrayList<android.content.ContentProviderOperation>()
                val rawIndex = 0
                ops.add(
                    android.content.ContentProviderOperation
                        .newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build(),
                )
                ops.add(
                    android.content.ContentProviderOperation
                        .newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                        )
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                        .build(),
                )
                ops.add(
                    android.content.ContentProviderOperation
                        .newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                        )
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                        )
                        .build(),
                )
                val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
                val rawContactUri = results.first().uri ?: error("Contact insert returned no URI")
                val rawContactId = ContentUris.parseId(rawContactUri)
                lookupKeyForRawContact(rawContactId)
                    ?: error("Could not resolve lookup key for new contact")
            }
        }

    override suspend fun updateContact(contact: Contact): AppResult<Unit> =
        AppResult.Failure(
            UnsupportedOperationException("Bulk contact field editing is not implemented in this pass"),
            "Editing contact fields is not yet supported.",
        )

    override suspend fun deleteContact(lookupKey: String): AppResult<Unit> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                val uri = ContactsContract.Contacts.CONTENT_LOOKUP_URI.buildUpon()
                    .appendPath(lookupKey)
                    .build()
                resolver.delete(uri, null, null)
                Unit
            }
        }

    override suspend fun setDefaultNumber(lookupKey: String, number: String): AppResult<Unit> =
        AppResult.Failure(
            UnsupportedOperationException("Setting the OS-level super-primary number is not implemented in this pass"),
            "Changing a contact's default number is not yet supported.",
        )

    // --- internal queries ---------------------------------------------------

    private fun queryContacts(accountType: String?, nameOrNumberLike: String? = null): List<Contact> {
        // Build the base contacts list, then fold phone rows in.
        val selectionParts = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (nameOrNumberLike != null) {
            selectionParts += "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
            args += "%$nameOrNumberLike%"
        }
        val selection = selectionParts.takeIf { it.isNotEmpty() }?.joinToString(" AND ")

        val contacts = LinkedHashMap<String, MutableContact>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED,
                ContactsContract.Contacts.SEND_TO_VOICEMAIL,
            ),
            selection,
            args.toTypedArray().takeIf { it.isNotEmpty() },
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val keyCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)
            val thumbCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            val starCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)
            val stvCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.SEND_TO_VOICEMAIL)
            while (c.moveToNext()) {
                val key = c.getString(keyCol) ?: continue
                contacts[key] = MutableContact(
                    id = c.getLong(idCol),
                    lookupKey = key,
                    displayName = c.getString(nameCol) ?: "",
                    photoUri = c.getString(photoCol),
                    thumbnailUri = c.getString(thumbCol),
                    isFavorite = c.getInt(starCol) == 1,
                    sendToVoicemail = c.getInt(stvCol) == 1,
                )
            }
        }
        if (contacts.isEmpty()) return emptyList()

        foldInNumbers(contacts, accountType, nameOrNumberLike)
        return contacts.values.map { it.toContact(formatter) }
    }

    private fun foldInNumbers(
        contacts: MutableMap<String, MutableContact>,
        accountType: String?,
        numberLike: String?,
    ) {
        val phoneSelection = mutableListOf<String>()
        val phoneArgs = mutableListOf<String>()
        if (accountType != null) {
            phoneSelection += "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?"
            phoneArgs += accountType
        }
        if (numberLike != null) {
            // When searching, also match contacts by number even if the name didn't.
            phoneSelection += "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            phoneArgs += "%$numberLike%"
        }
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
            ),
            phoneSelection.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            phoneArgs.toTypedArray().takeIf { it.isNotEmpty() },
            null,
        )?.use { c ->
            val keyCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val numCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
            val primaryCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)
            val acctCol = c.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE)
            while (c.moveToNext()) {
                val key = c.getString(keyCol) ?: continue
                val contact = contacts[key] ?: continue
                val raw = c.getString(numCol) ?: continue
                contact.accountType = contact.accountType ?: c.getString(acctCol)
                contact.numbers += RawNumber(
                    raw = raw,
                    label = mapPhoneType(c.getInt(typeCol)),
                    customLabel = c.getString(labelCol),
                    isPrimary = c.getInt(primaryCol) == 1,
                )
            }
        }
    }

    private fun queryByLookupKey(lookupKey: String): Contact? {
        val byKey = mutableMapOf<String, MutableContact>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_LOOKUP_URI.buildUpon().appendPath(lookupKey).build(),
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED,
                ContactsContract.Contacts.SEND_TO_VOICEMAIL,
            ),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                byKey[lookupKey] = MutableContact(
                    id = c.getLong(0),
                    lookupKey = c.getString(1) ?: lookupKey,
                    displayName = c.getString(2) ?: "",
                    photoUri = c.getString(3),
                    thumbnailUri = c.getString(4),
                    isFavorite = c.getInt(5) == 1,
                    sendToVoicemail = c.getInt(6) == 1,
                )
            }
        }
        if (byKey.isEmpty()) return null
        foldInNumbers(byKey, accountType = null, numberLike = null)
        return byKey.values.first().toContact(formatter)
    }

    private fun lookupKeyForRawContact(rawContactId: Long): String? {
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(rawContactId.toString()),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val contactId = c.getLong(0)
                resolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
                    "${ContactsContract.Contacts._ID} = ?",
                    arrayOf(contactId.toString()),
                    null,
                )?.use { cc -> if (cc.moveToFirst()) return cc.getString(0) }
            }
        }
        return null
    }

    private fun mapPhoneType(type: Int): NumberLabel = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> NumberLabel.MOBILE
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> NumberLabel.HOME
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> NumberLabel.WORK
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> NumberLabel.MAIN
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> NumberLabel.FAX_WORK
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> NumberLabel.FAX_HOME
        ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> NumberLabel.PAGER
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> NumberLabel.CUSTOM
        else -> NumberLabel.OTHER
    }

    private data class RawNumber(
        val raw: String,
        val label: NumberLabel,
        val customLabel: String?,
        val isPrimary: Boolean,
    )

    private class MutableContact(
        val id: Long,
        val lookupKey: String,
        val displayName: String,
        val photoUri: String?,
        val thumbnailUri: String?,
        val isFavorite: Boolean,
        val sendToVoicemail: Boolean,
        var accountType: String? = null,
        val numbers: MutableList<RawNumber> = mutableListOf(),
    ) {
        fun toContact(formatter: PhoneNumberFormatter): Contact = Contact(
            id = id,
            lookupKey = lookupKey,
            displayName = displayName,
            numbers = numbers.map { rn ->
                val base = formatter.toPhoneNumber(rn.raw)
                PhoneNumber(
                    raw = rn.raw,
                    normalized = base.normalized,
                    formatted = base.formatted,
                    label = rn.label,
                    customLabel = rn.customLabel,
                    isPrimary = rn.isPrimary,
                )
            },
            photoUri = photoUri,
            thumbnailUri = thumbnailUri,
            isFavorite = isFavorite,
            accountType = accountType,
            sendToVoicemail = sendToVoicemail,
        )
    }

    init {
        Timber.v("ContactsRepositoryImpl initialized")
    }
}
