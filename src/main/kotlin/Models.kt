import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

interface Identifiable {
    val id: String
}

interface IRoom {
    @get:Json(name = "title")
    val title: String

    @get:Json(name = "cover")
    val cover: String

    @get:Json(name = "description")
    val description: String
}

/**
 * Shared event component. Also makes this an [Identifiable]
 * which makes for neater RecyclerView adapter creation.
 */
@JsonClass(generateAdapter = true)
    data class RoomId(
        @get:Json(name = "id")
        override val id: String
    ) : Identifiable

/**
 * Core Room data items.
 *
 * @param title [String] The name of your room.
 * @param description [String] The description of your room.
 * @param cover [String] URL of an image to serve as room cover.
 */
@JsonClass(generateAdapter = true)
data class RoomData(
    override val title: String,
    override val cover: String,
    override val description: String
) : IRoom

// No extra fields, but having this makes for readability when creating Room.
data class CreateRoom(
    private val data: RoomData
) : IRoom by data

// Doesn't seem to require a `Details` version.
@JsonClass(generateAdapter = true)
data class Room(
    @RoomIdJson
    @get:Json(name = "id")
    val roomId: RoomId,
    val data: RoomData,

    @get:Json(name = "host")
    val host: String,

    @get:Json(name = "events")
    val events: RoomEvents,

    // TODO: 09/04/2021 subscribed not on server yet, and this field name is just a guess.
    //  This is just locally for now.
    @get:Json(name = "subscribed")
    val isSubscribed: Boolean? = false,
) : Identifiable by roomId, IRoom by data

@JsonClass(generateAdapter = true)
data class RoomEvents(
    @get:Json(name = "scheduled")
    val scheduled: List<String>,

    @get:Json(name = "started")
    val started: String? = null
)