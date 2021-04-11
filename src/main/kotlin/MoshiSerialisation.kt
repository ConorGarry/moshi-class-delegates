import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.*

/*val moshi by lazy {
    Moshi.Builder()
        .add(RoomIdAdapter())
        *//*.add(KotlinJsonAdapterFactory())*//*
        .build()
}

fun main() {
    val room = moshi.adapter(Room::class.java).fromJson(json)
    println("room: $room")
}*/

class RoomIdAdapter {

    @FromJson
    @RoomIdJson
    fun fromJson(json: Map<String, Any?>): RoomId {
        println("json: $json")
        return RoomId("room_id_parsed")
    }

    @ToJson
    fun toJson(writer: JsonWriter, @RoomIdJson roomId: RoomId) {
        throw java.lang.UnsupportedOperationException("toJson not used.")
    }
}

class RoomIdAdapterFactory {
    @FromJson
    fun fromJson(jsonReader: JsonReader): List<Room> {
        /*println("reader: ${jsonReader.readJsonValue()}")*/
        return RoomParser().parse(jsonReader)
    }

    @Throws(Exception::class)
    @Suppress("UNUSED_PARAMETER")
    @ToJson
    fun toJson(jsonWriter: JsonWriter?, value: Room) {
        throw UnsupportedOperationException(
            "@RoomIdJson is only used to deserialize objects."
        )
    }
}


val json = """
            {
                  "id": "01F2KMBWSAEVBTE8WM5W7B25AY",
                  "title": "Barnaby's fun room",
                  "cover": "https://d31rdvqcubemre.cloudfront.net/69a4a3c0-a7b8-11ea-b178-ad8f8454f900/569bb90d276255df5844a7ab9c510488",
                  "description": "Funtime with Barnaby Jones",
                  "events": {
                    "scheduled": [
                      "0001G7CZERZJ0ZM71JM3SGX6NZ"
                    ],
                    "started": null
                  },
                  "host": "69a4a3c0-a7b8-11ea-b178-ad8f8454f900"
                }
        """.trimIndent()


@Retention(RUNTIME)
@JsonQualifier
@Target(
    FUNCTION,
    FIELD,
    CLASS,
    TYPE_PARAMETER,
    PROPERTY,
    VALUE_PARAMETER
)
annotation class RoomIdJson

private const val ID = "id"
private const val TITLE = "title"
private const val COVER = "cover"
private const val DESCRIPTION = "description"
private const val EVENTS = "events"
private const val HOST = "host"
private const val SCHEDULED = "scheduled"
private const val STARTED = "started"

class RoomParser {
    companion object {
        val NAMES: JsonReader.Options = JsonReader.Options.of(
            ID,
            TITLE,
            COVER,
            DESCRIPTION,
            EVENTS,
            HOST
        )
    }

    private fun parseEvents(reader: JsonReader): RoomEvents {
        val names = JsonReader.Options.of(
            SCHEDULED, SCHEDULED
        )
        lateinit var scheduled: String
        var started: String? = null

        reader.readObject {
            when (reader.selectName(names)) {
                0 -> scheduled = reader.nextSource().toString()
                1 -> started = reader.nextString()
                else -> reader.skipNameAndValue()
            }
        }
        return RoomEvents(listOf(scheduled), started)
    }

    fun parse(reader: JsonReader): List<Room> {
        println("parse: $reader")
        return reader.readArrayToList {
            // lateinit vars are assertions, these _must_ be resolved.
            lateinit var id: String
            lateinit var title: String
            lateinit var cover: String
            lateinit var description: String
            lateinit var host: String
            lateinit var events: RoomEvents

            reader.readObject {
                when (reader.selectName(NAMES)) {
                    0 -> id = reader.nextString()
                    1 -> title = reader.nextString()
                    2 -> cover = reader.nextString()
                    3 -> description = reader.nextString()
                    4 -> events = parseEvents(reader)
                    5 -> host = reader.nextString()
                    else -> reader.skipNameAndValue()
                }
            }

            Room(
                RoomId(id),
                RoomData(title, cover, description),
                host,
                events
            )
        }
    }
}

fun JsonReader.skipNameAndValue() {
    skipName()
    skipValue()
}

inline fun JsonReader.readObject(body: () -> Unit) {
    beginObject()
    while (hasNext()) {
        body()
    }
    endObject()
}

inline fun JsonReader.readArray(body: () -> Unit) {
    beginArray()
    while (hasNext()) {
        body()
    }
    endArray()
}

inline fun <T : Any> JsonReader.readArrayToList(body: () -> T?): List<T> {
    val result = mutableListOf<T>()
    beginArray()
    while (hasNext()) {
        body()?.let { result.add(it) }
    }
    endArray()
    return result
}

