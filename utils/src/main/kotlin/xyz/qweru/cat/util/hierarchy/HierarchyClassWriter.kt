package xyz.qweru.cat.util.hierarchy

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassWriter

private val logger = KotlinLogging.logger {  }

class HierarchyClassWriter(
    val hierarchy: ClassHierarchy,
    flags: Int
) : ClassWriter(flags) {

    override fun getCommonSuperClass(type1: String, type2: String) =
        hierarchy.getCommonType(type1, type2)
}