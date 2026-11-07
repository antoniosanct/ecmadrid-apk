package org.ecmadrid.deportes.data

import android.content.Context
import org.ecmadrid.deportes.model.Favorite
import org.json.JSONArray
import org.json.JSONObject

class FavoritesStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): List<Favorite> {
        val saved = preferences.getString(FAVORITES_KEY, null) ?: return emptyList()
        val array = JSONArray(saved)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Favorite(
                sportId = item.getInt("sportId"),
                sportName = item.getString("sportName"),
                competitionId = item.getInt("competitionId"),
                competitionName = item.getString("competitionName"),
                teamId = item.getInt("teamId"),
                teamName = item.getString("teamName"),
                shieldUrl = item.optString("shieldUrl").takeIf { it.isNotBlank() && it != "null" },
                alias = item.optString("alias").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    fun save(favorites: List<Favorite>) {
        val array = JSONArray()
        favorites.forEach { favorite ->
            val obj = JSONObject()
                .put("sportId", favorite.sportId)
                .put("sportName", favorite.sportName)
                .put("competitionId", favorite.competitionId)
                .put("competitionName", favorite.competitionName)
                .put("teamId", favorite.teamId)
                .put("teamName", favorite.teamName)
            favorite.shieldUrl?.let { obj.put("shieldUrl", it) }
            favorite.alias?.takeIf { it.isNotBlank() }?.let { obj.put("alias", it) }
            array.put(obj)
        }
        check(preferences.edit().putString(FAVORITES_KEY, array.toString()).commit()) {
            "No se pudieron guardar los equipos favoritos en el dispositivo."
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "ecmadrid_favorites"
        const val FAVORITES_KEY = "favorites"
    }
}
