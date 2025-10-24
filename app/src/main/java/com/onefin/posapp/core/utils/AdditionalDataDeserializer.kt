import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class AdditionalDataDeserializer : JsonDeserializer<Map<String, Any>?> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Map<String, Any>? {
        return when {
            json.isJsonNull -> null
            json.isJsonPrimitive && json.asJsonPrimitive.isString -> {
                try {
                    val jsonString = json.asString
                    val gson = Gson()
                    gson.fromJson(
                        jsonString,
                        object : TypeToken<Map<String, Any>>() {}.type
                    )
                } catch (e: Exception) {
                    // Nếu parse lỗi, trả về map với key "value"
                    mapOf("value" to json.asString)
                }
            }

            json.isJsonObject -> {
                context.deserialize(
                    json,
                    object : TypeToken<Map<String, Any>>() {}.type
                )
            }

            else -> null
        }
    }
}