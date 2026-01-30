package xyz.qweru.cat.util.hierarchy

import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.util.asm.isInterface
import xyz.qweru.cat.util.jar.JarContainer
import java.util.ArrayDeque

private const val OBJECT = "java/lang/Object"

class ClassHierarchy(private val lookup: (String) -> ClassNode?) {

    init {
        jreHierarchy // force initialization
    }

    /**
     * @return true if `current` is equal to or is a superclass/superinterface of `target`
     */
    fun isAssignableFrom(target: String, current: String): Boolean {
        if (current == OBJECT || current == target) {
            return true
        }

        // if the current class isn't of type object,
        // it cannot be a supertype of object
        if (target == OBJECT) {
            return false
        }

        val target = getOrThrow(target)
        val current = getOrThrow(current)
        val stack = ArrayDeque<ClassNode>()
        val visited = HashSet<ClassNode>()

        stack.add(target)

        while (!stack.isEmpty()) {
            val target = stack.removeLast()

            if (!visited.add(target)) {
                continue
            }

            if (current.name == target.name) {
                return true
            }

            stack.addAll(target.interfaces.map(::getOrThrow))
            stack.add(getOrThrow(target.superName ?: continue))
        }

        return false
    }

    /**
     * @see org.objectweb.asm.ClassWriter.getCommonSuperClass
     */
    fun getCommonType(type1: String, type2: String): String {

        if (isAssignableFrom(type2, type1)) {
            return type1
        }

        if (isAssignableFrom(type1, type2)) {
            return type2
        }

        var klass1: ClassNode = getOrThrow(type1)
        val klass2: ClassNode = getOrThrow(type2)

        if (klass1.isInterface || klass2.isInterface) {
            return OBJECT
        } else {
            do {
                klass1 = getOrThrow(klass1.superName ?: OBJECT) // goes through all of class1 supertypes until one of them is assignable from class2
            } while (!isAssignableFrom(type2, klass1.name))
            return klass1.name
        }
    }

    private fun getOrThrow(klass: String): ClassNode =
        lookup(klass)
            ?: (if (this == jreHierarchy) null else jreHierarchy.lookup(klass))
            ?: throw IllegalArgumentException("$klass not in lookup")

}

fun createHierarchy(jar: JarContainer): ClassHierarchy =
    ClassHierarchy { jar.classes[it] }

