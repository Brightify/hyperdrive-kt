@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package org.brightify.hyperdrive

import kotlinx.coroutines.CancellationException
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.property.ObservableProperty
import org.brightify.hyperdrive.property.defaultEqualityPolicy
import org.brightify.hyperdrive.property.impl.MutexValueObservableProperty
import org.brightify.hyperdrive.property.impl.ValueObservableProperty
import org.brightify.hyperdrive.property.map

/* TODO: Split all observing and operators from BaseViewModel to ObservableObject. It can then be used here in InterfaceLock and possibly in
 other places */

/**
 * Locking for user interface operations.
 *
 * Use an instance of this class to ensure user interactions are ignored while a user-initiated operation is already running.
 *
 * Each instance of [InterfaceLock] is a member of a [Group] and if no [group] parameter is provided to the constructor a new group is
 * created by default. Only a single operation can be running across all [InterfaceLock] instances in the same group. Additional operations
 * are not run until the currently running one completes.
 *
 * Since the [runExclusively] is not blocking or suspending, you can monitor the progress using the [state] and [observeState] properties.
 */
public class InterfaceLock(
    // TODO: Research if we should be using a "global" scope, or if we should use a "lifecycle" scope instead.
    private val lifecycle: Lifecycle,
    private val group: Group = Group(),
) {
    private companion object {
        val logger = Logger(InterfaceLock::class)
    }

    private val mutableState = ValueObservableProperty<State>(State.Idle, defaultEqualityPolicy())
    public val observeState: ObservableProperty<State> = mutableState
    public var state: State
        get() = mutableState.value
        private set(newState) {
            mutableState.value = newState
        }

    /**
     * Whether the lock is currently executing work and is locked.
     */
    public val isLocked: Boolean get() = observeIsLocked.value
    public val observeIsLocked: ObservableProperty<Boolean> = mutableState.map { it == State.Running }

    /**
     * While the supplied `work` is running, this lock is considered taken and all other invocations of this method will just return, doing nothing.
     */
    public fun runExclusively(work: suspend () -> Unit) {
        val willRun = lifecycle.runOnceIfAttached {
            group.runExclusively {
                mutableState.value = State.Running
                mutableState.value = try {
                    work()

                    State.Idle
                } catch (cancellation: CancellationException) {
                    throw cancellation
                }
                // TODO: It might be unsafe to catch any Throwable. Please research.
                catch (t: Throwable) {
                    logger.warning(t) { "Work under lock failed." }
                    State.Failed(t)
                }
            }
        }
        if (!willRun) {
            logger.warning { "Can't run exclusively because $lifecycle is not attached.\n${lifecycle.dumpBranchToRoot()}" }
        }
    }

    /**
     * Resets the current state to [Idle][State.Idle] if it's currently [Failed][State.Failed].
     */
    public fun resetFailedState(): Boolean {
        val currentState = mutableState.value
        return if (currentState is State.Failed) {
            mutableState.value = State.Idle
            true
        } else {
            false
        }
    }

    /**
     * Exclusive access lock group. Multiple locks in the same group are competing with each other.
     *
     * When `runExclusively` is executing on one lock in the group, second lock's `runExclusively`
     * bounces as if the second lock was already in middle of executing `runExclusively` itself.
     */
    public class Group {
        private val mutableIsOperationRunning = MutexValueObservableProperty(false, defaultEqualityPolicy())
        public val observeIsOperationRunning: ObservableProperty<Boolean> = mutableIsOperationRunning
        public val isOperationRunning: Boolean
            get() = mutableIsOperationRunning.value

        /**
         * While the supplied `work` is running, this group is considered locked and all other invocations of this method will just return, doing nothing.
         */
        public suspend fun runExclusively(work: suspend () -> Unit) {
            if (!mutableIsOperationRunning.compareAndSet(expect = false, update = true)) {
                logger.warning { "Can't run exclusively because another operation is already running." }
                return
            }

            try {
                work()
            } finally {
                mutableIsOperationRunning.set(false)
            }
        }
    }

    /**
     * State of the lock.
     */
    public sealed class State {
        /**
         * Lock is currently executing work.
         */
        public object Running: State()

        /**
         * Lock has failed during work execution and provides the [throwable].
         */
        public class Failed(public val throwable: Throwable): State()

        /**
         * Lock is currently not executing work.
         */
        public object Idle: State()
    }
}
