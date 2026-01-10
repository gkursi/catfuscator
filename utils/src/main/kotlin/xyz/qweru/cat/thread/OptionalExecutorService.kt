package xyz.qweru.cat.thread

import xyz.qweru.cat.thread.Threads.ExecutorInvocator
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class OptionalExecutorService(
    private val thread: Boolean,
    provider: Threads.() -> ExecutorService
) : ExecutorService {
    val delegate by lazy { provider.invoke(Threads) }

    override fun shutdown() = if (thread) delegate.shutdown() else Unit

    override fun shutdownNow(): List<Runnable> = if (thread) delegate.shutdownNow() else emptyList()

    override fun isShutdown() = if (thread) delegate.isShutdown else false

    override fun isTerminated() = if (thread) delegate.isTerminated else false

    override fun awaitTermination(timeout: Long, unit: TimeUnit) =
        if (thread) delegate.awaitTermination(timeout, unit)
        else false

    override fun <T : Any> submit(task: Callable<T>): Future<T> =
        if (thread) delegate.submit(task)
        else CompletableFuture.completedFuture(task.call())

    override fun <T : Any> submit(task: Runnable, result: T): Future<T> {
        if (thread) return delegate.submit(task, result)
        task.run()
        return CompletableFuture.completedFuture(result)
    }

    override fun submit(task: Runnable): Future<*> =
        if (thread) delegate.submit(task)
        else CompletableFuture.completedFuture(task.run())

    override fun <T : Any> invokeAll(tasks: Collection<Callable<T>>): List<Future<T>> =
        if (thread) delegate.invokeAll(tasks)
        else tasks.map { CompletableFuture.completedFuture(it.call()) }

    override fun <T : Any?> invokeAll(
        tasks: Collection<Callable<T?>?>,
        timeout: Long,
        unit: TimeUnit
    ): List<Future<T?>?> {
        throw NotImplementedError("im too lazy")
    }

    override fun <T : Any?> invokeAny(tasks: Collection<Callable<T?>?>): T & Any {
        throw NotImplementedError("im too lazy")
    }

    override fun <T : Any?> invokeAny(
        tasks: Collection<Callable<T?>?>,
        timeout: Long,
        unit: TimeUnit
    ): T {
        throw NotImplementedError("im too lazy")
    }

    override fun execute(command: Runnable) {
        submit(command)
    }

    fun createInvocator() = ExecutorInvocator(this)
}