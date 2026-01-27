package xyz.qweru.cat.transform.encrypt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodInsnNode
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.*
import xyz.qweru.cat.util.thread.createExecutorFrom
import java.util.concurrent.CopyOnWriteArrayList

private val logger = KotlinLogging.logger {  }

class MethodCallEncryptTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("MethodCallEncrypt", "Encrypt method calls", target, opts) {

    init {
        target.apply {
            val parallel = createExecutorFrom(opts)

            val targets = CopyOnWriteArrayList<Method>()
            val invokeDesc = "(Ljava/lang/Object;[Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
            val poolName = "cat/MethodPool"
            val invokeMethod = "call"

            for (entry in classes) {
                if (!canTarget(entry)) continue
                parallel {
                    for (method in entry.value.methods) {
                        transformMethod(method) {
                            replace({ it is MethodInsnNode && it.opcode != Opcodes.INVOKEINTERFACE && it.name != "<init>" }) { invoke, _, _ ->
                                instructionsFor(method) {
                                    invoke as MethodInsnNode

                                    val mInvoke = Method(invoke.owner, invoke.name, invoke.desc, mapTag(invoke.name, invoke.opcode))
                                    val types = Type.getArgumentTypes(invoke.desc)

                                    targets.add(mInvoke)
                                    logger.info { "Types ${types.joinToString(",")}" }

                                    createArrayFromStack(types)
                                    if (mInvoke.isStatic) {
                                        constantNull()
                                        swap()
                                    }
                                    ldc(mInvoke.hash())
                                    invokeStatic(poolName, invokeMethod, invokeDesc)
                                    unboxType(Type.getReturnType(invoke.desc))
                                }
                            }
                        }
                    }
                }
            }

            parallel.await()

            val poolClass = newClass(
                poolName,
                versionFromJar(this),
                Opcodes.ACC_PUBLIC
            ) {
                field("map", PUBLIC_STATIC, "Ljava/util/concurrent/ConcurrentHashMap;", null)

                method(invokeMethod, PUBLIC_STATIC, invokeDesc) {
                    // method params
                    val instance = 0 // Ljava/lang/Object;
                    val args = 1 // [Ljava/lang/Object;
                    val target = 2 // Ljava/lang/String;
                    getStaticField(poolName, "map", "Ljava/util/concurrent/ConcurrentHashMap;")
                    loadLocalObject(target)
                    invokeVirtual("java/util/concurrent/ConcurrentHashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")
                    checkCast("java/lang/invoke/MethodHandle")

                    val static = label()
                    loadLocalObject(instance)
                    jumpIfNull(static)

                    loadLocalObject(instance)
                    invokeVirtual("java/lang/invoke/MethodHandle", "bindTo", "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;") // L

                    +static // mh
                    loadLocalObject(args) // mh, [L
                    invokeVirtual("java/lang/invoke/MethodHandle", "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;") // L
                    returnInstance() // Ljava/lang/Object;
                }

                method("<clinit>", PUBLIC_STATIC, "()V") {
                    newObject("java/util/concurrent/ConcurrentHashMap", "(I)V") { ldc(targets.size) }
                    dup()
                    storeStaticField(poolName, "map", "Ljava/util/concurrent/ConcurrentHashMap;")

                    for (method in targets.shuffled()) {
                        dup()
                        ldc(method.hash())
                        getHandle(method)
                        invokeVirtual(
                            "java/util/concurrent/ConcurrentHashMap",
                            "put",
                            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
                        )
                        pop()
                    }

                    pop()
                    returnVoid()
                }
            }

            put(poolClass)
        }
    }

    private fun mapTag(name: String, op: Int): Int = when(op) {
        Opcodes.INVOKESTATIC -> Opcodes.H_INVOKESTATIC
        Opcodes.INVOKEVIRTUAL -> Opcodes.H_INVOKEVIRTUAL
        Opcodes.INVOKESPECIAL -> if (name == "<init>") Opcodes.H_NEWINVOKESPECIAL else Opcodes.H_INVOKESPECIAL
        else -> throw IllegalArgumentException("unknown tag")
    }

    private fun InsnBuilder.getHandle(method: Method) {
        val clazz = "java/lang/Class"
        val mhandle = "java/lang/invoke/MethodHandle"
        val mtype = "java/lang/invoke/MethodType"
        val mhandles = "java/lang/invoke/MethodHandles"
        val lookup = $$"$$mhandles$Lookup"
        val args = Type.getArgumentTypes(method.desc)

        // obtain the base methodhandle
        // (the constant pool is used
        // for methods in closed named
        // packages instead of private
        // lookups)
        if (!method.owner.startsWith("java") && !method.owner.startsWith("[")) {
            // todo: dedupe lookups

            ldc(Type.getType("L${method.owner};"))
            dup()
            invokeStatic(mhandles, "lookup", "()L$lookup;")
            invokeStatic(mhandles, "privateLookupIn", "(L$clazz;L$lookup;)L$lookup;")
            swap()

            ldc(method.name)

            // methodtype
            pushType(Type.getReturnType(method.desc))
            ldc(args.size)
            newObjectArray("java/lang/Class")
            for ((i, type) in args.withIndex()) {
                dup()
                ldc(i)
                pushType(type)
                storeObjectInArray()
                logger.info { "Push type for method ${method.name} : $type " }
            }

            invokeStatic(mtype, "methodType", "(L$clazz;[L$clazz;)L$mtype;") // lookup, cl, name, mtype

            when (method.tag) {
                Opcodes.H_INVOKESTATIC -> invokeVirtual(
                    lookup, "findStatic",
                    "(L$clazz;Ljava/lang/String;L$mtype;)L$mhandle;"
                )
                Opcodes.H_INVOKEVIRTUAL -> invokeVirtual(
                    lookup, "findVirtual",
                    "(L$clazz;Ljava/lang/String;L$mtype;)L$mhandle;"
                )
                Opcodes.H_INVOKESPECIAL -> {
                    ldc(Type.getType("L${method.owner};")) // lookup, cl, name, mtype, caller
                    invokeVirtual(
                        lookup, "findSpecial",
                        "(L$clazz;Ljava/lang/String;L$mtype;L$clazz;)L$mhandle;"
                    )
                }
            }
        } else {
            ldc(Handle(
                method.tag,
                method.owner,
                method.name,
                method.desc,
                method.tag == Opcodes.H_INVOKEINTERFACE
            ))
        }

        // adapt to use with an array

        invokeVirtual(mhandle, "asFixedArity", "()L$mhandle;")
        pushType(Type.getType("[Ljava/lang/Object;"))
        ldc(args.size)
        invokeVirtual(mhandle, "asSpreader", "(Ljava/lang/Class;I)L$mhandle;")
    }

    private data class Method(val owner: String, val name: String, val desc: String, val tag: Int) {
        val isStatic = tag == Opcodes.H_INVOKESTATIC

        fun hash(): String =
             xyz.qweru.cat.util.crypto.hash("$tag: $owner#$name$desc $isStatic")
    }
}