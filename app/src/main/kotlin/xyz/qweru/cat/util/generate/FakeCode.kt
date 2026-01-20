package xyz.qweru.cat.util.generate

import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.util.asm.isStatic

fun findFields(klass: ClassNode, descriptor: String, static: Boolean = true): List<String> {
    val fields = arrayListOf<String>()
    for (node in klass.fields) {
        if (node.desc != descriptor) continue
        if (node.isStatic != static) continue
        fields.add(node.name)
    }
    return fields
}