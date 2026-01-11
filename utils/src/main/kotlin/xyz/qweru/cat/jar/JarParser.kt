package xyz.qweru.cat.jar

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.profile.Timer
import xyz.qweru.cat.thread.Threads
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

object JarParser {
    private val logger = KotlinLogging.logger {}

    fun read(config: Configuration) = JarFile(config.input).use { jar ->
        val timer = Timer()
        val classOpts = ClassReader.SKIP_FRAMES or if (config.strip) 2 else 0
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
                    val timer = Timer()
                    val output = ClassNode()
                    val reader = ClassReader(bytes)

                    reader.accept(output, classOpts)
                    container.put(output)

                    logger.info { "Processed class file: ${entry.name} (took ${timer.time()}ms)" }
                } else {
                    container.put(Resource(entry.name, bytes))
                    logger.info { "Processed resource:   ${entry.name}" }
                }
            }
        }

        parallel.await()
        logger.info { "Jar parsing took ${timer.time()}ms" }

        return@use container
    }

    fun write(container: JarContainer, config: Configuration) {
        val bytes = ConcurrentHashMap<String, ByteArray>()
        val parallel = Threads.optional(config.threadAsm)
            { fromCount(container.size(), config.threadAsmCapacity) }
            .createInvocator()

        val timer = Timer()

        for (entry in container.classes) {
            parallel {
                val node = entry.value
                val timer = Timer()
                val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)

                node.accept(writer)
                bytes["${node.name}.class"] = writer.toByteArray()

                logger.info { "Processed class file: ${node.name} (took ${timer.time()}ms)" }
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
}