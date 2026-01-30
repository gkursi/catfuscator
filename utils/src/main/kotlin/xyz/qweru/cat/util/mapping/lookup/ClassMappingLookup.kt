package xyz.qweru.cat.util.mapping.lookup

class ClassMappingLookup(val original: String, var name: String = original) {
    val fields = StringMappingLookup()
    val methods = MethodMappingLookup()
}