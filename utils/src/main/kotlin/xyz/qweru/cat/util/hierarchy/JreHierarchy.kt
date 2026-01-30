package xyz.qweru.cat.util.hierarchy

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}
private val jrt by lazy {
    FileSystems.getFileSystem(URI.create("jrt:/"))
}

/**
 * The nodes in this hierarchy do not contain any code
 */
val jreHierarchy = createJreHierarchy()

private fun createJreHierarchy(): ClassHierarchy {
    val start = System.nanoTime()
    val modules = jrt.getPath("/modules")
    val lookup = ConcurrentHashMap<String, ClassNode>()

    for (path in Files.walk(modules).filter { it.toString().endsWith(".class") }) {
        val input = Files.newInputStream(path)
        val cr = ClassReader(input)
        val node = ClassNode()

        cr.accept(node, ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES)
        lookup[node.name] = node
    }

    val time = System.nanoTime() - start
    logger.info { "Created jre hierarchy in ${time / 1_000_000}ms" }

    return ClassHierarchy(lookup::get)
}