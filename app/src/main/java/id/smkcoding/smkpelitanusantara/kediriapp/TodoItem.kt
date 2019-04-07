package id.smkcoding.smkpelitanusantara.kediriapp

import com.google.gson.annotations.SerializedName

class TodoItem{
    @SerializedName("text")
    var text: String? = null
    @SerializedName("id")
    var id: String? = null

    @SerializedName("complete")
    var isComplete: Boolean = false
}