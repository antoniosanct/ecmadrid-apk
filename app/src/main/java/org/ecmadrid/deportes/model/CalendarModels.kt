package org.ecmadrid.deportes.model

data class Sport(val id: Int, val name: String)

data class Competition(val id: Int, val name: String)

data class Team(
    val id: Int,
    val name: String,
    val shieldUrl: String? = null
)

data class Favorite(
    val sportId: Int,
    val sportName: String,
    val competitionId: Int,
    val competitionName: String,
    val teamId: Int,
    val teamName: String,
    val shieldUrl: String? = null,
    val alias: String? = null
) {
    /**
     * Nombre a mostrar para la gestión del calendario en pestañas y barra superior (alias o nombre del equipo).
     * El nombre oficial del equipo se mantiene inalterado en [teamName].
     */
    val calendarDisplayName: String
        get() = alias?.takeIf { it.isNotBlank() } ?: teamName

    val displayName: String
        get() = calendarDisplayName
}

data class CalendarMatch(
    val id: Int,
    val roundNumber: Int,
    val roundName: String = "Jornada $roundNumber",
    val date: String? = null,
    val time: String? = null,
    val rawDateTime: String? = null,
    val field: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String? = null,
    val localTeamId: Int = 0,
    val localTeamName: String = "",
    val localShieldUrl: String? = null,
    val visitorTeamId: Int = 0,
    val visitorTeamName: String = "",
    val visitorShieldUrl: String? = null,
    val localScore: Int? = null,
    val visitorScore: Int? = null,
    val isResting: Boolean = false,
    val isPostponed: Boolean = false,
    val isModified: Boolean = false
)

val sports = listOf(
    Sport(1, "Baloncesto"),
    Sport(2, "Fútbol sala"),
    Sport(3, "Voleibol")
)
