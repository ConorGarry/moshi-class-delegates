import com.squareup.moshi.*
import com.squareup.moshi.JsonReader.Token
import java.lang.reflect.Type
import kotlin.reflect.*
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField

interface Person {
    val name: String
    val surname: String
    val age: Int
}

data class PersonData(
    override val name: String,
    override val surname: String,
    override val age: Int
) : Person

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER
)
annotation class PersonIdJson


@PersonIdJson
data class PersonId(
    @Json(name = "id")
    override val id: String
) : Identifiable

data class Student(
    val personId: PersonId,
    val data: PersonData,
    val university: String,
    val passed: Boolean,
    val subjects: List<String>,
    val book: Book
) : Identifiable by personId, Person by data

data class StudentNoId(
    val data: PersonData,
    val university: String,
    val passed: Boolean,
    val subjects: List<String>,
    val book: Book
) : Person by data

@JsonClass(generateAdapter = true)
data class Book(
    val bookTitle: String
)

// Important! ORDER MATTERS!
private val moshi = Moshi.Builder()
    .add(FactoryCreate<Student>(Person::class, Identifiable::class))
    .add(FactoryCreate<StudentNoId>(Person::class))
    /*.add(KotlinJsonAdapterFactory())*/
    .build()

fun main() {
    /*val person = moshi.adapter(Student::class.java).fromJson(jsonStudent)*/
    val person = moshi.adapter(StudentNoId::class.java).fromJson(jsonStudent)
    println("person: $person")
}

/**
 * Create a copy of a JsonReader payload to look ahead on and fetch names with tokens.
 */
@OptIn(ExperimentalStdlibApi::class)
fun JsonReader.peekNames(): List<Pair<String, Token>> =
    with(peekJson()) {
        return buildList {
            use { p ->
                p.readObject {
                    if (p.peek() == Token.NAME) {
                        add(nextName() to p.peek())
                    } else {
                        p.skipValue()
                    }
                }
            }
        }
    }

/**
 * Facade for instantiating [DelegateAdapterFactory] which in turn creates [ClassDelegateAdapter].
 * Params [C] and [I] need to be known at runtime for [findDelegatingPropertyClass] to work,
 * hence the requirement for `inline` and `reified`.
 *
 * @param C Concrete class we want to deserialise.
 * @param I Top-level interface, contains core fields required for parsing.
 */
@Suppress("FunctionName") // Function name.
inline fun <reified C : Any, reified I : Any> FactoryCreate() =
    DelegateAdapterFactory(C::class,
        findDelegatingPropertyClass(C::class, I::class)
            .map { it.name to it.returnType.classifier }
    )

@Suppress("FunctionName")
inline fun <reified C : Any> FactoryCreate(vararg delegateTypes: KClass<*>): DelegateAdapterFactory<C> {
    val delegates = delegateTypes.map {
        findDelegatingPropertyClass(C::class, it)
            .map { m -> m.name to m.returnType.classifier }
    }
    /*println("types: $delegates")*/
    return DelegateAdapterFactory(C::class, delegates.flatten())
}


/**
 * Factory for creating [ClassDelegateAdapter].
 *
 * @param kClass [C] Class we want this adapter to deserialise.
 */
class DelegateAdapterFactory<C : Any>(
    private val kClass: KClass<C>,
    private val delegates: List<Pair<String, KClassifier?>>
) : JsonAdapter.Factory {

    /** Type will always be the top-most interface, not sure if that's of any use? */
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi)
            : ClassDelegateAdapter<C>? {
        /*println("factory type: ${type.typeName}, annotations: $annotations")*/
        return kClass.takeIf { it.qualifiedName == type.typeName }?.run {
            ClassDelegateAdapter(this, delegates)
        }
    }
}

/**
 *
 */
@OptIn(ExperimentalStdlibApi::class)
class ClassDelegateAdapter<C : Any>(
    private val kClass: KClass<C>,
    private val delegateProps: List<Pair<String, KClassifier?>>
) : JsonAdapter<C>() {

    // TODO: Working, but:
    //  - Needs good cleanup, lots of mapping / list / grouping can be tightened.
    //  - Maybe make better user of the model class? `DelegateInfo`. Could be we nest it?
    //  - Ordering not properly done, note user of `reverse()` to get it working.
    //  - Nullability fields not supported yet.
    //  - Can we create a lookup table? Maybe .params can also be made neater?
    //  - Start writing tests, commit at working stage.
    @FromJson
    override fun fromJson(reader: JsonReader): C {
        // Look-ahead to get names in payload.
        val peekFields: List<Pair<String, Token>> = reader.peekNames()
        val jsonNames = reader.peekNames().map { it.first }

        val delegateNames = delegateProps.map { it }
        val types = delegateProps.map { it.second }.first()
        /*println("props: $delegateProps")*/

        val grouped: Map<String, List<Map<String?, KType>>> =
            delegateProps.groupBy({ (it.second as KClass<*>).primaryConstructor!!.returnType.toString() }, {
                (it.second as KClass<*>).primaryConstructor!!
                    .parameters.associate { p -> p.name to p.type }
            })
        /*println("grouped: $grouped")*/

        // Fields _not_ provided by delegates.
        val selfFieldNames = jsonNames.filter {
            !grouped.map { it.value.map { it.keys } }
                .flatten().flatten().contains(it)
        }

        // Register names for JsonReader's selectName parsing.
        val nameOptions = JsonReader.Options.of(*jsonNames.toTypedArray())

        val selfConstructor =
            requireNotNull(kClass.primaryConstructor) {
                "Type should have constructor: $kClass"
            }

        val delegationConstructor: KFunction<Any> =
            requireNotNull((types as KClass<*>).primaryConstructor) {
                "Delegate type should have constructor: $types"
            }

        val delegateConstructors: Map<String, List<KFunction<Any>>> =
            delegateProps.map { (it.second as KClass<*>).primaryConstructor!! }
                .groupBy { it.returnType.toString() }

        val constructParams: List<Pair<String, KType>> =
            delegationConstructor.parameters.map { it.name!! to it.type }

        // Partition to two Lists: 0: delegate field names, 1: current object field names.
        val (_, currentFields) = jsonNames.partition { j -> constructParams.map { it.first }.contains(j) }

        // TODO: Other post types?
        val stringAdapter: JsonAdapter<List<String>> = moshi.adapter<List<String>>(
            Types.newParameterizedType(List::class.java, String::class.java)
        ).nonNull()

        // Map of index to name and type. // index by int // pair with type
        val linked = linkedMapOf(*peekFields.mapIndexed { i, nameType -> i to nameType }.toTypedArray())
        println("\nlinked: $linked")
        // Map of type to JsonReader parse function. // map with return function for type
        val parseMap = mapOf<Token, (JsonReader) -> Any>(
            Token.STRING to { it.nextString() },
            Token.NUMBER to { it.nextInt() },
            Token.BOOLEAN to { it.nextBoolean() },
            Token.BEGIN_ARRAY to { stringAdapter.fromJson(it)!! },
            Token.BEGIN_OBJECT to {
                adapterMap[it.path]?.fromJson(it) ?: error("No known adapter for ${it.path}")
            },
        )

        // Ext. function for extracting
        fun List<String>.params(values: Map<Int, Any>) = map { f ->
            values[linked.values.indexOfFirst { it.first == f }]
        }.toTypedArray()

        data class DelegateInfo<T>(
            val constructor: KFunction<T>,
            val params: List<Pair<String, KType>>,
            val fields: List<String>,
            //  key    key = type
            // {data=[{name=kotlin.String, surname=kotlin.String, age=kotlin.Int}], personId=[{id=kotlin.String}]}
            val delegateConstructors: Map<String, List<KFunction<Any>>>,
            val delegateProps: Map<String, List<Map<String?, KType>>>,
            val valueMap: MutableMap<Int, Any> = mutableMapOf()
        ) {
            fun instantiate(): T {
                println("\nInstantiate pattern:")
                val delegateParams: List<KParameter> =
                    constructor.parameters.filter {
                        delegateProps.map { it.key }.contains(it.type.toString())
                    }

                val constructors: List<KFunction<Any>> = delegateConstructors.values.flatten()
                val objects = constructors.mapIndexed { i, kFunction ->
                    kFunction.call(
                        *delegateProps.values.toList()[i].first().map { it.key }.params(valueMap).toList()
                            .toTypedArray()
                    )
                }.reversed() // TODO: Delicate hack, need to ensure correct order.
                println("objects: $objects")

                println("delegateConstructors: $delegateConstructors}")
                println("delegateConstructParams: $delegateParams}")

                fun List<String>.filterDelegateFields(): List<String> =
                    filter {
                        !delegateProps.values.toList().flatten().map {
                            it.map { it.key }
                        }.flatten().contains(it)
                    }

                val currentFieldsTest = currentFields.params(valueMap)
                println("currentFieldsTest: ${currentFieldsTest.toList()}")

                val constructed = constructor.call(
                    // Iterate delegate Props, call constructors with *spread params.
                    *objects.toTypedArray(),
                    // *Spread self parameters.
                    *currentFields.toList().filterDelegateFields().params(valueMap)
                )
                println("\nconstructed: $constructed\n")
                return constructed
            }

            fun List<String?>.params(values: Map<Int, Any>): Array<Any?> = map { f ->
                values[linked.values
                    .indexOfFirst { it.first == f }]
            }.toTypedArray()
        }

        val delegateInfo = DelegateInfo(
            selfConstructor,
            constructParams,
            selfFieldNames,
            delegateConstructors,
            grouped
        )
        println("delegateInfo: $delegateInfo")

        val valueMap = delegateInfo.valueMap
        return reader.let {
            it.readObject {
                with(it.selectName(nameOptions)) {
                    valueMap[this] = parseMap[linked[this]!!.second]!!.invoke(it)
                }
            }
        }.run {
            delegateInfo.instantiate()
        }
    }

    @ToJson
    override fun toJson(writer: JsonWriter, json: C?) = throw UnsupportedOperationException("no-op")
}

inline fun <reified T : Any, DELEGATE : Any> findDelegatingPropertyClass(
    klass: KClass<T>,
    delegatingTo: KClass<DELEGATE>
): List<KProperty1<T, *>> {
    return klass.declaredMemberProperties.mapNotNull { prop ->
        prop.takeIf {
            it.javaField?.run {
                delegatingTo.java.isAssignableFrom(type)
                    .also {
                        isAccessible = true
                    }
            } == true
        }
    }
}


val jsonStudent = """
      {
        "name": "studentName",
        "surname": "studentSurname",
        "age": 20,
        "university": "universityName",
        "passed": true,
        "subjects": ["Maths", "Geography", "History"],
        "book": { "bookTitle": "Learning to Code"}
        }
""".trimIndent()

val jsonStudentWithId = """
      {
        "id": "abcd-1234",
        "name": "studentName",
        "surname": "studentSurname",
        "age": 20,
        "university": "universityName",
        "passed": true,
        "subjects": ["Maths", "Geography", "History"],
        "book": { "bookTitle": "Learning to Code"}
        }
""".trimIndent()

private val adapterMap = mapOf(
    "$.book" to BookJsonAdapter(moshi)
)
