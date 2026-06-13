// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.edit

import androidx.compose.runtime.Immutable
import com.glyphdialer.core.domain.model.NumberLabel

/**
 * The single immutable UI state for the Contact Create/Edit screen (CONVENTIONS.md
 * §5; BUILD_SPEC §8 — "create/edit (ContactsContract)"). The same screen handles both
 * modes: [isEditing] distinguishes a create from an edit of an existing contact.
 *
 * Number rows are an editable, ordered list; [NumberDraft.id] is a stable client-side
 * key (not the provider id) so reordering/removal is recomposition-safe.
 *
 * @property isEditing true when editing an existing contact, false when creating.
 * @property isLoading true while loading an existing contact's fields for edit.
 * @property displayName the editable name field.
 * @property numbers the editable phone-number rows (at least one is always present).
 * @property isSaving true while a save is in flight (disables the save action).
 * @property errorMessage a transient human-readable error to surface, or null.
 */
@Immutable
data class ContactEditUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val displayName: String = "",
    val numbers: List<NumberDraft> = listOf(NumberDraft()),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Save is allowed when there's a name and at least one non-blank number. */
    val canSave: Boolean
        get() = !isSaving && displayName.isNotBlank() && numbers.any { it.value.isNotBlank() }

    /** Screen title reflecting the mode. */
    val title: String get() = if (isEditing) "EDIT CONTACT" else "NEW CONTACT"
}

/**
 * One editable phone-number row. [id] is a stable client key for list diffing.
 */
@Immutable
data class NumberDraft(
    val id: Long = nextId(),
    val value: String = "",
    val label: NumberLabel = NumberLabel.MOBILE,
) {
    companion object {
        private var counter = 0L

        /** Monotonic client-side id generator for new draft rows. */
        fun nextId(): Long = ++counter
    }
}

/**
 * User intents flowing UP from the Edit screen into [ContactEditViewModel.onEvent]
 * (CONVENTIONS.md §5).
 */
sealed interface ContactEditEvent {
    /** Edit the display-name field. */
    data class NameChanged(val name: String) : ContactEditEvent

    /** Edit a number row's value. */
    data class NumberChanged(val id: Long, val value: String) : ContactEditEvent

    /** Change a number row's label. */
    data class LabelChanged(val id: Long, val label: NumberLabel) : ContactEditEvent

    /** Append a fresh blank number row. */
    data object AddNumber : ContactEditEvent

    /** Remove the number row with [id] (a single empty row always remains). */
    data class RemoveNumber(val id: Long) : ContactEditEvent

    /** Persist the contact (create or update) via ContactsRepository. */
    data object Save : ContactEditEvent

    /** Dismiss the currently-shown error message. */
    data object DismissError : ContactEditEvent
}

/** One-shot side effects the Edit ViewModel asks the host to perform. */
sealed interface ContactEditEffect {
    /** Saved successfully → host navigates to the contact's detail by [lookupKey]. */
    data class Saved(val lookupKey: String) : ContactEditEffect

    /** Show a transient confirmation message. */
    data class ShowMessage(val message: String) : ContactEditEffect
}
