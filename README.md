# moshi-class-delegates
Experiments with serialisation of Kotlin data classes with class delegation

Based on this article [Simpler Kotlin class hierarchies using class delegation](https://proandroiddev.com/simpler-kotlin-class-hierarchies-using-class-delegation-35464106fed5) we can use Kotlin to remove a lot of code repeption
when designing API parsers on our client using Kotlin's interface delegation. However, after some experimenting, and verified by one of the repsonses to the artice, it's not entirely fit for purpose.
Serialising works without issues, but derserialising doesn't; because there's no way to know how to unflatten the class structure back to together in the correct hierarchy.

This experimentation with a custom adapter can check if a class _does_ have a delegate by using Kotin reflection:

```kotlin
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
```

Using a custom adpater, with the Interface typed passed in (same process as creating a normal custom adatper) and checking it against a determined delegate, we can rebuild the
structure using the deserialised values.

So far it only works if the serialisation order matches the class propertly declaration order. But this can definitely be resolved.

Seems Kotlin delegated classes flatten / unflattening is an issue not yet generally resolved.
