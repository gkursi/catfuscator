package hierarchy

import hierarchy.classes.ClassA
import hierarchy.classes.ClassB
import hierarchy.classes.ClassC
import hierarchy.classes.ClassD
import hierarchy.classes.ClassE
import hierarchy.classes.ClassF
import hierarchy.classes.ExceptionClassA
import hierarchy.classes.InterfaceA
import hierarchy.classes.InterfaceB
import org.objectweb.asm.ClassWriter
import xyz.qweru.cat.util.hierarchy.createHierarchy
import xyz.qweru.cat.util.hierarchy.jreHierarchy
import java.util.ArrayList
import java.util.LinkedList
import kotlin.test.Test

open class HierarchyTest {
    @Test
    fun testJreHierarchy() {
        assert(jreHierarchy.isAssignableFrom("java/lang/Object", "java/lang/Object"))
        assert(jreHierarchy.isAssignableFrom("java/lang/Class", "java/lang/Object"))
        assert(jreHierarchy.isAssignableFrom("java/lang/IllegalStateException", "java/lang/Exception"))
        assert(jreHierarchy.isAssignableFrom("java/lang/IllegalStateException", "java/lang/RuntimeException"))
    }

    @Test
    fun testClassHierarchy() {
        val hierarchy = createHierarchy(createContainer(
            ClassA::class,
            ClassB::class,
            ClassC::class,
            ClassD::class,
            ClassE::class,
            ClassF::class,

            InterfaceA::class,
            InterfaceB::class,
        ))

        assert(
            hierarchy.getCommonType(
                "hierarchy/classes/ClassA",
                "hierarchy/classes/ClassB"
            ) == "hierarchy/classes/ClassA"
        )
        assert(
            hierarchy.getCommonType(
                "hierarchy/classes/ClassC",
                "hierarchy/classes/ClassE"
            ) == "hierarchy/classes/ClassA"
        )
    }

    @Test
    fun testMixedHierarchies() {
        val hierarchy = createHierarchy(createContainer(
            ExceptionClassA::class
        ))

        assert(hierarchy.isAssignableFrom("hierarchy/classes/ExceptionClassA", "java/lang/Exception"))
        assert(hierarchy.isAssignableFrom("hierarchy/classes/ExceptionClassA", "java/lang/Throwable"))
    }
}