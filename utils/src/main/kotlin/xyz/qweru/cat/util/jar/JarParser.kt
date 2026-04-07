package xyz.qweru.cat.util.jar

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.util.analysis.FastFrameStateAnalyzer
import xyz.qweru.cat.util.asm.analyseMethod
import xyz.qweru.cat.util.asm.analyseMethodStack
import xyz.qweru.cat.util.asm.analyseMethodStackHeight
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.profile.Timer
import xyz.qweru.cat.util.thread.Threads
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

private val logger = KotlinLogging.logger {}

fun readJar(config: Configuration) = JarFile(config.input).use { jar ->
    val parsingOptions = ClassReader.SKIP_FRAMES or if (config.strip) ClassReader.SKIP_DEBUG else 0
    val container = JarContainer()
    val timer = Timer()
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
//        parallel {
            val writer = container.createClassWriter(ClassWriter.COMPUTE_FRAMES)
            val node = entry.value

            for (methodNode in node.methods) {
                try {
                    val a = FastFrameStateAnalyzer()

                    try {
                        a.cuh = analyseMethod(node, methodNode)
                    } catch (_: Exception) {}

                    a.analyze(methodNode.instructions)
                } catch (ex: Exception) {
                    ex.printStackTrace(System.out)
                    System.out.flush()
                    throw ex
                }
            }

            if (config.strip) {
                node.sourceDebug = null
                node.sourceFile = null
            }

            logger.info { "Computing ${node.name}" }
            node.accept(writer)
            bytes["${node.name}.class"] = writer.toByteArray()
//        }
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