package xyz.qweru.cat.util.generate

import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.util.asm.InsnBuilder
import xyz.qweru.cat.util.asm.isStatic
import kotlin.random.Random

fun findFields(klass: ClassNode, descriptor: String, static: Boolean = true): List<String> {
    val fields = arrayListOf<String>()
    for (node in klass.fields) {
        if (node.desc != descriptor) continue
        if (node.isStatic != static) continue
        fields.add(node.name)
    }
    return fields
}

fun InsnBuilder.stringLength() =
    invokeVirtual("java/lang/String", "length", "()I")


fun InsnBuilder.randomStringConstant(maxLength: Int = 16) {
    ldc(randomString(maxLength))
}

fun randomString(minLength: Int = 4, maxLength: Int = 64): String {
    val builder = StringBuilder()

    for (i in 0..<maxLength) {
        builder.append((Random.nextBits(16)).toChar())
        if (i >= minLength && Random.nextBoolean() && Random.nextBoolean()) {
            break
        }
    }

    return builder.toString()
}

fun exactRandomString(length: Int): String {
    val builder = StringBuilder()

    for (i in 0..<length) {
        builder.append((Random.nextBits(Char.SIZE_BITS)).toChar())
    }

    return builder.toString()
}