package hierarchy

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.util.jar.JarContainer
import kotlin.reflect.KClass

fun readBytes(klass: KClass<*>): ByteArray {
    val klass = klass.java
    val resource = klass.getName().replace('.', '/') + ".class"
    val inp = if (klass.classLoader != null) {
        klass.getClassLoader().getResourceAsStream(resource)
    } else {
        ClassLoader.getSystemResourceAsStream(resource)
    }

    return inp?.readAllBytes() ?: throw IllegalStateException("Could not read bytes for $resource")
}

fun createClassNode(byteArray: ByteArray): ClassNode {
    val cr = ClassReader(byteArray)
    val cn = ClassNode()
    cr.accept(cn, ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES)
    return cn
}

fun createContainer(vararg classes: KClass<*>): JarContainer {
    val container = JarContainer()
    for (klass in classes) {
        container.put(
            createClassNode(
                readBytes(klass)
            )
        )
    }
    return container
}