import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.companionObjectInstance
import kotlin.reflect.declaredMemberProperties
import kotlin.reflect.jvm.javaType

class _1
class _0_1
class _0_n
class _1_n

@Target(AnnotationTarget.CLASS)
annotation class Parents(vararg val parents: KClass<*>)

class node_t
class companion_t
class cell_list

val node_t = "node_t"
val companion_t = "companion_t"

val types = TypeMap(
        Boolean::class to "bool"
        , node_t::class to "$node_t&"
        , companion_t::class to "$companion_t"
        , String::class to "string"
        , cell_list::class to "cell::list"
)

object companiontraits {
    interface Cells {
        val cells: cell_list
    }

    interface Named {
        val name: String

        companion object : Trait() {
            override fun implement(node: KClass<*>, name: String) {
                println("${impl(name, Named)} {")
                println("    " + impl(Named::name) { " static string it = \"${node.simpleName!!}\"; return it; " })
                println("};")
            }
        }
    }

    interface ParentConstraint {

        fun canAdd(parent: node_t): Boolean

        companion object : Trait() {
            override fun implement(node: KClass<*>, name: String) {
                val parents = node.annotations
                        .filterIsInstance<Parents>()
                        .singleOrNull()
                        ?.parents
                parents?.let {
                    println("""
${impl(name, ParentConstraint)} {
    ${impl(ParentConstraint::canAdd) { " return ${it.map { val T = it.simpleName; "IS_INSTANCE(parent, $T)" }.joinToString(" || ")}; " }}
};
""")
                }
            }

            override val defaults = mapOf<KCallable<*>, Pair<List<String>, String>>(
                    ParentConstraint::canAdd to Pair(listOf("parent"), """
                        ${unused("self")};
                        ${unused("parent")};
                        return false;
                    """)
            )
        }
    }
}

object nodetraits {
    interface CompanionObject {
        val companion: companion_t

        companion object : Trait() {
            override fun implement(node: KClass<*>, name: String) {
                println("${impl(name, CompanionObject)} {")
                println("    " + impl(CompanionObject::companion) { " return g_${name}_companion(); " })
                println("};")
            }
        }
    }
}

fun gen(domain: KClass<*>) {
    domain.nestedClasses.forEach {
        assert(it.nestedClasses.isEmpty())
    }

    println(prelude)

    println("""
#include <vector>
#include <memory>
template <class T>
using container = std::vector<std::unique_ptr<T>>;
""")

    println("""
#include <typeinfo>
#define IS_INSTANCE(var, T) instanceof<T>(var)
template <class T, class Self>
bool instanceof(Self &it) { return typeid(it) == typeid(T); }
""")
    println("""
struct cell {
    using list = std::vector<cell>;
};
""")

    val companionTraitClasses = companiontraits::class.nestedClasses
    val nodeTraitClasses = nodetraits::class.nestedClasses

    fun dtorDecl(T: String) = "virtual ~$T()"
    fun dtorDef(T: String) = "$T::~$T() { }"

    println("// bases")
    run {
        println("struct $companion_t;")
        println("struct $node_t;")
        println("""
struct $companion_t {
    ${defvirt(companionTraitClasses).joinToString("\n    ")}
    ${dtorDecl(companion_t)};
};
${dtorDef(companion_t)}
""")
        println("""
struct $node_t {
    using _1 = $node_t *; // TODO: ref
    using _0_1 = $node_t *;
    using _0_n = container<$node_t>;
    using _1_n = container<$node_t>;

    ${defvirt(nodeTraitClasses).joinToString("\n    ")}
    ${dtorDecl(node_t)};
};
${dtorDef(node_t)}
""")
    }

    println("// companion traits")
    println(deftraits(companionTraitClasses).joinToString("\n"))
    println("""
template <class Self> struct ${companion_t}_fromtraits : $companion_t {
    ${defglue(companionTraitClasses).joinToString("\n    ")}
};
""")

    println("// node traits")
    println(deftraits(nodeTraitClasses).joinToString("\n"))
    println("""
template <class Self> struct ${node_t}_fromtraits : $node_t {
    ${defglue(nodeTraitClasses).joinToString("\n    ")}
};
""")

    println("// structs")
    domain.nestedClasses.forEach {
        val klass = it.simpleName!!
        println("// $klass")
        println("struct $klass final : ${node_t}_fromtraits<$klass> {")
        it.declaredMemberProperties.forEach {
            val sigil = when (it.returnType.javaType) {
                _1::class.java -> "$node_t::_1" // 1
                _0_1::class.java -> "$node_t::_0_1" // ?
                _0_n::class.java -> "$node_t::_0_n" // *
                _1_n::class.java -> "$node_t::_1_n" // +
                else -> throw IllegalArgumentException()
            }
            println("    $sigil ${it.name};")
        }
        println("    ${dtorDecl(klass)};")
        println("};")
        println(dtorDef(klass))

        val comp = "${klass}_companion"
        println("""
struct $comp : ${companion_t}_fromtraits<$comp> {
    ${dtorDecl(comp)};
};
${dtorDef(comp)}
$companion_t &g_$comp() { static $comp it; return it; }
""")
    }

    println("// impls")
    domain.nestedClasses.forEach { node ->
        val s = node.simpleName!!
        nodeTraitClasses.forEach { (it.companionObjectInstance as Trait?)?.implement(node, s) }
        companionTraitClasses.forEach { (it.companionObjectInstance as Trait?)?.implement(node, "${s}_companion") }
    }
}
