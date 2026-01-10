package xyz.qweru.cat.jar

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.mapping.MappingLookup
import java.util.concurrent.ConcurrentHashMap

class JarContainer {
    val classes: MutableSet<ClassNode> = ConcurrentHashMap.newKeySet()
    val resources: MutableSet<Resource> = ConcurrentHashMap.newKeySet()
    val mappings = MappingLookup("")

    fun put(classNode: ClassNode) = classes.add(classNode)
    fun put(resource: Resource) = resources.add(resource)

    fun size() = classes.size + resources.size

    fun accept(classVisitor: ClassVisitor) {
        for (node in classes) {
            node.accept(classVisitor)
        }
    }
}