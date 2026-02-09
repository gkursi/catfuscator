package xyz.qweru.cat.util.thread

import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

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

    class Wrapped(executor: ExecutorService) : ExecutorInvocator(executor) {
        override fun invoke(t: () -> Unit) {
            super.invoke {
                try {
                    t()
                } catch (e: Throwable) {
                    e.printStackTrace(System.err)
                    System.err.flush()
                    throw e
                }
            }
        }
    }
}