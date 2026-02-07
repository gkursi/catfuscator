package xyz.qweru.cat.util.jar

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.analysis.Analyzer
import xyz.qweru.cat.util.hierarchy.HierarchyClassWriter
import xyz.qweru.cat.util.hierarchy.HierarchyVerifier
import xyz.qweru.cat.util.hierarchy.createHierarchy
import xyz.qweru.cat.util.mapping.lookup.JarMappingLookup
import java.util.concurrent.ConcurrentHashMap

class JarContainer {
    val resources: MutableSet<Resource> = ConcurrentHashMap.newKeySet()
    val classes: MutableMap<String, ClassNode> = ConcurrentHashMap()
    val hierarchy = createHierarchy(this)
    val mappings = JarMappingLookup()

    fun put(classNode: ClassNode) {
        classes[classNode.name] = classNode
    }

    fun put(resource: Resource) {
        resources.add(resource)
    }

    fun size() = classes.size + resources.size

    fun createClassWriter(flags: Int) =
        HierarchyClassWriter(hierarchy, flags)

    fun createAnalyzer() =
        Analyzer(HierarchyVerifier(hierarchy))
}