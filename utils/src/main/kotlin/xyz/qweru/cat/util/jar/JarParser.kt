package xyz.qweru.cat.util.jar

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.profile.Timer
import xyz.qweru.cat.util.thread.Threads
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

private val logger = KotlinLogging.logger {}

fun readJar(config: Configuration) = JarFile(config.input).use { jar ->
    val timer = Timer()
    val parsingOptions = ClassReader.SKIP_FRAMES or if (config.strip) ClassReader.SKIP_DEBUG else 0
    val container = JarContainer()
    val parallel = Threads.optional(config.threadAsm)
        { fromCount(jar.size(), config.threadAsmCapacity) }
        .createInvocator()

    for (entry in jar.entries()) {
        if (entry.isDirectory) continue
        parallel {
            val bytes = jar.getInputStream(entry).use {
                it.readAllBytes()
            }

            if (entry.name.endsWith(".class")) {
                val output = ClassNode()
                val reader = ClassReader(bytes)

                reader.accept(output, parsingOptions)
                container.put(output)
            } else {
                container.put(Resource(entry.name, bytes))
            }
        }
    }

    parallel.await()
    logger.info { "Jar parsing took ${timer.time()}ms" }

    return@use container
}

fun writeJar(container: JarContainer, config: Configuration) {
    val bytes = ConcurrentHashMap<String, ByteArray>()
    val parallel = Threads.optional(config.threadAsm)
        { fromCount(container.size(), config.threadAsmCapacity) }
        .createWrappedInvocator()

    val timer = Timer()

    for (entry in container.classes) {
        parallel {
            val writer = container.createClassWriter(ClassWriter.COMPUTE_FRAMES)
            val node = entry.value

            if (config.strip) {
                node.sourceDebug = null
                node.sourceFile = null
            }

            node.accept(writer)
            bytes["${node.name}.class"] = writer.toByteArray()
            logger.info { "Computed ${node.name}" }
        }
    }

    for (resource in container.resources) {
        bytes[resource.name] = resource.bytes
    }

    parallel.await()
    logger.info { "Jar writing took ${timer.time()}ms" }

    val file = File(config.output)
    JarOutputStream(FileOutputStream(file)).use { stream ->
        for (entry in bytes.entries) {
            stream.putNextEntry(ZipEntry(entry.key))
            stream.write(entry.value)
            stream.closeEntry()
        }
    }

    logger.info { "Wrote to ${file.absolutePath}" }
}