package org.brightify.hyperdrive.multiplatformx

import co.touchlab.stately.ensureNeverFrozen

public interface ManageableViewModel {
    public val willChange: ObjectWillChange

    public val lifecycle: Lifecycle?

    public interface ObjectWillChange {
        public fun addListener(listener: Listener): CancellationToken

        public fun addObserver(observer: () -> Unit): CancellationToken = addListener(observer)

        public fun removeListener(listener: Listener)

        public fun interface Listener {
            public fun onObjectWillChange()
        }
    }

    public class ObjectWillChangeTrigger: ObjectWillChange, ObjectWillChange.Listener {
        private val listeners = mutableSetOf<ObjectWillChange.Listener>()

        init {
            ensureNeverFrozen()
            listeners.ensureNeverFrozen()
        }

        public override fun addListener(listener: ObjectWillChange.Listener): CancellationToken {
            listeners.add(listener)
            return CancellationToken {
                removeListener(listener)
            }
        }

        public override fun removeListener(listener: ObjectWillChange.Listener) {
            listeners.remove(listener)
        }

        public fun notifyObjectWillChange() {
            listeners.forEach { it.onObjectWillChange() }
        }

        override fun onObjectWillChange() {
            notifyObjectWillChange()
        }
    }
}