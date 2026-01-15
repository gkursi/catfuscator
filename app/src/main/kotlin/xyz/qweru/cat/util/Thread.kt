package xyz.qweru.cat.util

import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.thread.Threads

fun createExecutorFrom(configuration: Configuration): Threads.ExecutorInvocator =
    Threads.optional(configuration.threadTransform)
        { fromCount(configuration.threadTransformCapacity) }
        .createWrappedInvocator()