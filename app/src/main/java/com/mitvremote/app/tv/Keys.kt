package com.mitvremote.app.tv

/** Android key codes understood by the TV. */
object Key {
    const val HOME = 3
    const val BACK = 4
    const val UP = 19
    const val DOWN = 20
    const val LEFT = 21
    const val RIGHT = 22
    const val CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val SPACE = 62
    const val DEL = 67
    const val ENTER = 66
    const val MENU = 82
    const val SEARCH = 84
    const val PLAY_PAUSE = 85
    const val NEXT = 87
    const val PREVIOUS = 88
    const val REWIND = 89
    const val FAST_FORWARD = 90
    const val MUTE = 164
    const val INFO = 165
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val SETTINGS = 176
    const val TV_INPUT = 178
    const val GUIDE = 172
}

data class TvApp(val name: String, val id: String)

object Apps {
    val NETFLIX = TvApp("Netflix", "com.netflix.ninja")
    val PRIME = TvApp("Prime Video", "com.amazon.amazonvideo.livingroom")
    val YOUTUBE = TvApp("YouTube", "com.google.android.youtube.tv")

    val all = listOf(
        YOUTUBE,
        NETFLIX,
        PRIME,
        TvApp("YouTube Music", "com.google.android.youtube.tvmusic"),
        TvApp("Spotify", "com.spotify.tv.android"),
        TvApp("Play Store", "com.android.vending"),
        TvApp("TV Settings", "com.android.tv.settings"),
    )
}
