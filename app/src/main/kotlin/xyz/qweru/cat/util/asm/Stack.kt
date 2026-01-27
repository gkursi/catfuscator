package xyz.qweru.cat.util.asm

import org.objectweb.asm.Type

/**
 * Leaves the resulting array on the stack
 */
fun InsnBuilder.createArrayFromStack(types: Array<Type>, size: Int = types.size) {
    loadConstant(size)
    newObjectArray("java/lang/Object")
    dup()

    for (i in size - 1 downTo  0) {
        val typeSort = types[i].sort
        if (isDoubleSize(typeSort)) {
            // stack: ... value i-1, value i 1/2, value i 2/2, array, array
            dup2_x2()
            // stack: ... value i-1, array, array, value i 1/2, value i 2/2, array, array
            pop2()
            // stack: ... value i-1, array, array, value i 1/2, value i 2/2
            loadConstant(i)
            // stack: ... value i-1, array, array, value i 1/2, value i 2/2, index
            dup_x2()
            // stack: ... value i-1, array, array, index, value i 1/2, value i 2/2, index
            pop()
            // stack: ... value i-1, array, array, index, value i 1/2, value i 2/2
        } else {
            // stack: ... value i-1, value i, array, array
            dup2_x1()
            // stack: ... value i-1, array, array, value i, array, array
            pop2()
            // stack: ... value i-1, array, array, value i
            loadConstant(i)
            // stack: ... value i-1, array, array, value i, index
            swap()
            // stack: ... value i-1, array, array, index, value i
        }

        boxPrimitive(typeSort)
        storeObjectInArray()
        // stack: ... value i-1, array
        dup()
        // stack: ... value i-1, array, array
    }
    // stack: array, array
    pop()
    // stack: array
}
fun isDoubleSize(type: Int) =
    type == Type.LONG || type == Type.DOUBLE

fun InsnBuilder.boxPrimitive(type: Int) = when(type) {
    Type.OBJECT, Type.ARRAY -> {}
    Type.INT -> invokeStatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
    Type.FLOAT -> invokeStatic("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;")
    Type.BOOLEAN -> invokeStatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;")
    Type.BYTE -> invokeStatic("java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;")
    Type.SHORT -> invokeStatic("java/lang/Short", "valueOf", "(S)Ljava/lang/Short;")
    Type.LONG -> invokeStatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;")
    Type.DOUBLE -> invokeStatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;")
    Type.CHAR -> invokeStatic("java/lang/Character", "valueOf", "(C)Ljava/lang/Characterl")
    else -> throw IllegalArgumentException("Unknown type sort: $type")
}

fun InsnBuilder.unboxType(type: Type) = when (type.sort) {
    Type.VOID -> pop()
    Type.OBJECT, Type.ARRAY -> checkCast(type.internalName)
    Type.INT -> unboxPrimitive("java/lang/Integer", "int", "I")
    Type.FLOAT -> unboxPrimitive("java/lang/Float", "float", "F")
    Type.BOOLEAN -> unboxPrimitive("java/lang/Boolean", "boolean", "Z")
    Type.BYTE -> unboxPrimitive("java/lang/Byte", "byte", "B")
    Type.SHORT -> unboxPrimitive("java/lang/Short", "short", "S")
    Type.LONG -> unboxPrimitive("java/lang/Long", "long", "J")
    Type.DOUBLE -> unboxPrimitive("java/lang/Double", "double", "D")
    Type.CHAR -> unboxPrimitive("java/lang/Character", "char", "C")
    else -> throw IllegalArgumentException("Unknown type sort: ${type.sort} (type $type)")
}

fun InsnBuilder.pushType(type: Type) =
    if (type.sort == Type.OBJECT || type.sort == Type.ARRAY) {
        ldc(type)
    } else {
        pushPrimitiveType(type.sort)
    }

fun InsnBuilder.pushPrimitiveType(type: Int) = getStaticField("java/lang/${when (type) {
    Type.INT -> "Integer"
    Type.FLOAT -> "Float"
    Type.BOOLEAN -> "Boolean"
    Type.BYTE -> "Byte"
    Type.SHORT -> "Short"
    Type.LONG -> "Long"
    Type.DOUBLE -> "Double"
    Type.CHAR -> "Character"
    Type.VOID -> "Void"
    else -> throw IllegalArgumentException("Invalid primitive sort: $type")
}}", "TYPE", "Ljava/lang/Class;")

fun InsnBuilder.unboxPrimitive(owner: String, simple: String, primitiveDesc: String) {
    checkCast(owner)
    invokeVirtual(owner, "${simple}Value", "()$primitiveDesc")
}

fun getInvocationTypes(methodDesc: String, owner: String, static: Boolean) =
    Type.getArgumentTypes(methodDesc).let {
        return@let arrayOf(if (static) {
            Type.getType("Ljava/lang/Object;")
        } else {
            Type.getType("L$owner;")
        }) + it
    }
