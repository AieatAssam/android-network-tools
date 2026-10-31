package net.aieat.netswissknife.app.data

import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

/**
 * Last form inputs and terminal result for tools whose ViewModel is scoped to a navigation entry.
 *
 * Leaving a tool with Back pops its entry, which clears the ViewModel and its SavedStateHandle.
 * This holder outlives that entry for the life of the activity, so returning to the tool shows
 * the previous state again. It survives rotation and is dropped with the activity.
 *
 * Only plain immutable data may be stored here: no Context, no Android types, and no secrets.
 * Nothing is written to disk or to the saved-state Bundle. Results are not Parcelable, so a
 * process death keeps only the SavedStateHandle inputs and drops the result.
 *
 * Accessed from the main thread only, like the ViewModels that use it.
 */
@ActivityRetainedScoped
class ToolSessionMemory
    @Inject
    constructor() {
        @PublishedApi
        internal val slots = HashMap<String, Any>()

        inline fun <reified T : Any> get(key: String): T? = slots[key] as? T

        fun put(
            key: String,
            value: Any,
        ) {
            slots[key] = value
        }
    }
