@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package org.brightify.hyperdrive.util

import org.brightify.hyperdrive.CancellationToken
import org.brightify.hyperdrive.utils.WeakReference

internal class WeakListenerHandler<LISTENER: Any>(
    // As long as this handler lives (due to vended tokens) it should retain its owner.
    @Suppress("unused")
    private val owner: Any,
) {
    private val listeners = mutableListOf<WeakReference<LISTENER>>()
    private var isNotifyingListeners = false
    private val pendingListenerModifications = mutableListOf<ListenerModification<LISTENER>>()

    fun addListener(listener: LISTENER): CancellationToken {
        val listenerReference = CancellableListenerReference(listener)
        if (isNotifyingListeners) {
            pendingListenerModifications.add(ListenerModification.Add(listenerReference.reference))
        } else {
            doAddListenerReference(listenerReference.reference)
        }
        return listenerReference
    }

    fun removeListener(listener: LISTENER) {
        if (isNotifyingListeners) {
            pendingListenerModifications.add(ListenerModification.Remove(listener))
        } else {
            doRemoveListener(listener)
        }
    }

    fun notifyListeners(notify: LISTENER.() -> Unit) {
        check(!isNotifyingListeners) { "Reentrancy! Trying to notify listeners while notifying listeners!" }
        try {
            isNotifyingListeners = true
            listeners.listIterator().let { iterator ->
                for (reference in iterator) {
                    val listener = reference.get()
                    if (listener != null) {
                        listener.notify()
                    } else {
                        iterator.remove()
                    }
                }
            }
        } finally {
            isNotifyingListeners = false
            if (pendingListenerModifications.isNotEmpty()) {
                pendingListenerModifications.forEach { modification ->
                    when (modification) {
                        is ListenerModification.Add -> doAddListenerReference(modification.listenerReference)
                        is ListenerModification.Remove -> doRemoveListener(modification.listener)
                    }
                }
                pendingListenerModifications.clear()
            }
        }
    }

    private fun doAddListenerReference(listenerReference: WeakReference<LISTENER>) {
        if (listenerReference.get() != null) {
            listeners.add(listenerReference)
        }
    }

    private fun doRemoveListener(listener: LISTENER) {
        listeners.removeAll { reference -> reference.get().let { it == listener || it == null } }
    }

    private sealed interface ListenerModification<LISTENER: Any> {
        class Add<LISTENER: Any>(val listenerReference: WeakReference<LISTENER>): ListenerModification<LISTENER>
        class Remove<LISTENER: Any>(val listener: LISTENER): ListenerModification<LISTENER>
    }

    private inner class CancellableListenerReference(
        listener: LISTENER,
    ): CancellationToken {
        val reference = WeakReference(listener)
        private var strongReference: LISTENER? = listener

        override val isCanceled: Boolean
            get() = reference.get() == null

        override fun cancel() {
            val listener = strongReference ?: return
            removeListener(listener)
            strongReference = null
        }
    }
}
