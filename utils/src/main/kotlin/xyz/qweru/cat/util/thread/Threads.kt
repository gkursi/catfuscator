package xyz.qweru.cat.util.thread

import xyz.qweru.cat.util.config.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Threads {
    private const val THREAD_CAPACITY = 5 // approx. number of tasks per thread

    fun optional(config: Boolean, delegate: Threads.() -> ExecutorService) = ConditionalExecutor(config, delegate)

    fun fromCount(count: Int, capacity: Int = THREAD_CAPACITY): ExecutorService = Executors.newWorkStealingPool(
        count.floorDiv(capacity).coerceAtLeast(1)
    )
}

fun createExecutorFrom(configuration: Configuration): ExecutorInvocator =
    Threads.optional(configuration.threadTransform)
    { fromCount(configuration.threadTransformCapacity) }
        .createWrappedInvocator()