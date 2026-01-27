package xyz.qweru.cat.mapping.lookup

class ClassMappingLookup(val original: String, var name: String = original) {
    val fields = StringMappingLookup()
    val methods = MethodMappingLookup()
}