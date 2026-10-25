package com.example.videoplayer.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PlaylistManager {
    private const val PREFS_NAME = "vlc_playlists_prefs"
    private const val KEY_PLAYLISTS = "playlists_json"

    fun getPlaylists(context: Context): List<Playlist> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PLAYLISTS, null)
        if (jsonStr.isNullOrEmpty()) {
            val defaults = getDefaultPlaylists()
            savePlaylists(context, defaults)
            return defaults
        }

        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<Playlist>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uris = mutableListOf<String>()
                val urisArray = obj.optJSONArray("itemUris")
                if (urisArray != null) {
                    for (j in 0 until urisArray.length()) {
                        uris.add(urisArray.getString(j))
                    }
                }
                list.add(
                    Playlist(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        iconType = obj.optString("iconType", "playlist"),
                        itemUris = uris,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            if (list.isEmpty()) getDefaultPlaylists() else list
        } catch (e: Exception) {
            getDefaultPlaylists()
        }
    }

    fun savePlaylists(context: Context, playlists: List<Playlist>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val array = JSONArray()
            for (p in playlists) {
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("description", p.description)
                obj.put("iconType", p.iconType)
                obj.put("createdAt", p.createdAt)
                val urisArray = JSONArray()
                for (uri in p.itemUris) {
                    urisArray.put(uri)
                }
                obj.put("itemUris", urisArray)
                array.put(obj)
            }
            prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun createPlaylist(context: Context, name: String, iconType: String = "playlist"): Playlist {
        val playlists = getPlaylists(context).toMutableList()
        val newPlaylist = Playlist(
            id = "pl_${System.currentTimeMillis()}",
            name = name,
            description = "Пользовательский плейлист",
            iconType = iconType,
            itemUris = emptyList()
        )
        playlists.add(0, newPlaylist)
        savePlaylists(context, playlists)
        return newPlaylist
    }

    fun deletePlaylist(context: Context, playlistId: String) {
        val playlists = getPlaylists(context).filter { it.id != playlistId }
        savePlaylists(context, playlists)
    }

    fun addItemToPlaylist(context: Context, playlistId: String, uri: String) {
        val playlists = getPlaylists(context).map { p ->
            if (p.id == playlistId) {
                if (!p.itemUris.contains(uri)) {
                    p.copy(itemUris = p.itemUris + uri)
                } else p
            } else p
        }
        savePlaylists(context, playlists)
    }

    fun removeItemFromPlaylist(context: Context, playlistId: String, uri: String) {
        val playlists = getPlaylists(context).map { p ->
            if (p.id == playlistId) {
                p.copy(itemUris = p.itemUris.filter { it != uri })
            } else p
        }
        savePlaylists(context, playlists)
    }

    private fun getDefaultPlaylists(): List<Playlist> {
        return listOf(
            Playlist(
                id = "pl_favorites",
                name = "Избранное",
                description = "Любимые видео и треки",
                iconType = "favorites",
                itemUris = listOf(
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4"
                )
            ),
            Playlist(
                id = "pl_recent",
                name = "Недавние",
                description = "Недавно просмотренные медиафайлы",
                iconType = "recent",
                itemUris = listOf(
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
                )
            ),
            Playlist(
                id = "pl_cinema",
                name = "Фильмы и Кино",
                description = "Коллекция фильмов в HD",
                iconType = "movie",
                itemUris = listOf(
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
                )
            ),
            Playlist(
                id = "pl_music",
                name = "Музыкальные клипы",
                description = "Клипы и атмосферные саундтреки",
                iconType = "music",
                itemUris = listOf(
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4"
                )
            )
        )
    }
}
