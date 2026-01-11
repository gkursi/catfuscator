package xyz.qweru.cat.jar

import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.mapping.lookup.JarMappingLookup
import java.util.concurrent.ConcurrentHashMap

class JarContainer {
    val classes: MutableMap<String, ClassNode> = ConcurrentHashMap()
    val resources: MutableSet<Resource> = ConcurrentHashMap.newKeySet()
    val mappings = JarMappingLookup()

    fun put(classNode: ClassNode) {
        classes[classNode.name] = classNode
    }

    fun put(resource: Resource) {
        resources.add(resource)
    }

    fun size() = classes.size + resources.size
}