package xyz.qweru.cat.jar

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.thread.Threads
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object JarParser {
    private val logger = KotlinLogging.logger {}

    fun read(config: Configuration) = JarFile(config.input).use { jar ->
        val classOpts = if (config.strip) 2 else 0
        val container = JarContainer()
        val parallel = Threads.optional(config.threadAsm)
            { fromCount(jar.size(), config.threadAsmCapacity) }
            .createInvocator()

        val start = System.nanoTime()

        for (entry in jar.entries()) {
            if (entry.isDirectory) continue
            parallel {
                val bytes = jar.getInputStream(entry).use {
                    it.readAllBytes()
                }

                if (entry.name.endsWith(".class")) {
                    val start = System.nanoTime()
                    val output = ClassNode()
                    val reader = ClassReader(bytes)

                    reader.accept(output, classOpts)
                    container.put(output)

                    val time = System.nanoTime() - start
                    logger.info { "Processed class file: ${entry.name} (took ${time / 1_000_000}ms)" }
                } else {
                    container.put(Resource(entry.name, bytes))
                    logger.info { "Processed resource:   ${entry.name}" }
                }
            }
        }

        parallel.await()
        val end = System.nanoTime() - start
        logger.info { "Jar parsing took ${end / 1_000_000}ms" }

        return@use container
    }

    fun write(config: Configuration, container: JarContainer) {
        val bytes = ConcurrentHashMap<String, ByteArray>()
        val parallel = Threads.optional(config.threadAsm)
            { fromCount(container.size(), config.threadAsmCapacity) }
            .createInvocator()

        val start = System.nanoTime()

        for (node in container.classes) {
            parallel {
                val start = System.nanoTime()
                val writer = ClassWriter(0)

                node.accept(writer)
                bytes["${node.name}.class"] = writer.toByteArray()

                val time = System.nanoTime() - start
                logger.info { "Processed class file: ${node.name} (took ${time / 1_000_000}ms)" }
            }
        }

        for (resource in container.resources) {
            bytes[resource.name] = resource.bytes
        }

        parallel.await()
        val end = System.nanoTime() - start
        logger.info { "Jar writing took ${end / 1_000_000}ms" }

        val file = File(config.output)
        JarOutputStream(FileOutputStream(file)).use { stream ->
            for (entry in bytes.entries) {
                writeToZip(stream, entry.key, entry.value)
            }
        }
        logger.info { "Wrote to ${file.absolutePath}" }
    }

    private fun writeToZip(stream: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        stream.putNextEntry(entry)
        stream.write(bytes)
        stream.closeEntry()
    }

}