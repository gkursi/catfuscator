package xyz.qweru.cat.thread

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object Threads {
    private const val THREAD_CAPACITY = 5 // approx. number of tasks per thread

    fun optional(config: Boolean, delegate: Threads.() -> ExecutorService) = OptionalExecutorService(config, delegate)

    fun fromCount(count: Int, capacity: Int = THREAD_CAPACITY): ExecutorService = Executors.newWorkStealingPool(
        count.floorDiv(capacity).coerceAtLeast(1)
    )

    open class ExecutorInvocator(val executor: ExecutorService) : (() -> Unit) -> Unit {
        override fun invoke(t: () -> Unit) {
            executor.submit(t)
        }

        /**
         * Equal to calling
         * ```
         * executor.shutdown()
         * executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
         * ```
         */
        fun await() {
            executor.shutdown()
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        }
    }

    class WrappedExecutorInvocator(executor: ExecutorService) : ExecutorInvocator(executor) {
        override fun invoke(t: () -> Unit) {
            super.invoke {
                try {
                    t()
                } catch (e: Exception) {
                    e.printStackTrace(System.err)
                    throw e
                }
            }
        }
    }
}