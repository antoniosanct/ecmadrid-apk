package org.ecmadrid.deportes.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.ecmadrid.deportes.model.CalendarMatch
import org.ecmadrid.deportes.model.Competition
import org.ecmadrid.deportes.model.Sport
import org.ecmadrid.deportes.model.Team
import org.ecmadrid.deportes.model.TeamStanding
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CalendarRepository {
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)

    suspend fun competitions(sport: Sport): List<Competition> = withContext(Dispatchers.IO) {
        val seasonId = activeSeasonId()
        val response = getJsonObject(
            "/api/CompeticionesPorDeporte?action=ObtenerCompeticionesDeporteListado" +
                "&IdDeporte=${sport.id}&IdTemporada=$seasonId&elementoTodas=true&soloActivas=true"
        )
        response.getJSONArray("data").toObjects()
            .mapNotNull { item ->
                val id = item.optInt("Id")
                val name = item.optString("Nombre").takeUnless { it == "null" }.orEmpty()
                if (id > 0 && name.isNotBlank()) Competition(id, name) else null
            }
    }

    suspend fun teams(sport: Sport, competition: Competition): List<Team> =
        withContext(Dispatchers.IO) {
            val roundResponse = getJsonObject(
                "/Calendarios/ObtenerDatosJornadaGrupo" +
                    "?IdDeporte=${sport.id}&IdCompeticion=${competition.id}" +
                    "&IdCategoria=-1&IdGrupo=-1&IdJornada=0&IdEquipo=0"
            )
            val rounds = roundResponse.optJSONArray("jornadas") ?: JSONArray()
            val teams = linkedMapOf<Int, Team>()

            coroutineScope {
                val deferredCalendars = (0 until rounds.length()).mapNotNull { index ->
                    val roundNumber = rounds.getJSONObject(index).optInt("NumeroJornada")
                    if (roundNumber > 0) {
                        async(Dispatchers.IO) {
                            runCatching {
                                getJsonArray(
                                    "/Calendarios/ObtenerCalendarioAgrupado" +
                                        "?IdDeporte=${sport.id}&IdCompeticion=${competition.id}" +
                                        "&IdCategoria=-1&NumeroJornada=$roundNumber&IdEquipo=0"
                                )
                            }.getOrNull()
                        }
                    } else null
                }
                val calendars = deferredCalendars.awaitAll()
                for (calendar in calendars) {
                    if (calendar != null) {
                        collectTeams(calendar, teams)
                    }
                }
            }

            teams.values.sortedBy { it.name.lowercase() }
        }

    suspend fun teamCalendar(
        sportId: Int,
        competitionId: Int,
        teamId: Int
    ): List<CalendarMatch> = withContext(Dispatchers.IO) {
        val roundResponse = getJsonObject(
            "/Calendarios/ObtenerDatosJornadaGrupo" +
                "?IdDeporte=$sportId&IdCompeticion=$competitionId" +
                "&IdCategoria=-1&IdGrupo=-1&IdJornada=0&IdEquipo=$teamId"
        )
        val rounds = roundResponse.optJSONArray("jornadas") ?: JSONArray()

        coroutineScope {
            val deferredCalendars = (0 until rounds.length()).mapNotNull { index ->
                val roundNumber = rounds.getJSONObject(index).optInt("NumeroJornada")
                if (roundNumber > 0) {
                    async(Dispatchers.IO) {
                        runCatching {
                            getJsonArray(
                                "/Calendarios/ObtenerCalendarioAgrupado" +
                                    "?IdDeporte=$sportId&IdCompeticion=$competitionId" +
                                    "&IdCategoria=-1&NumeroJornada=$roundNumber&IdEquipo=$teamId"
                            )
                        }.getOrNull()
                    }
                } else null
            }

            val calendars = deferredCalendars.awaitAll()
            val matches = mutableListOf<CalendarMatch>()
            for (calendar in calendars) {
                if (calendar != null) {
                    collectMatchesForTeam(calendar, teamId, matches)
                }
            }
            matches.sortedWith(compareBy({ it.roundNumber }, { it.rawDateTime ?: "" }))
        }
    }

    suspend fun teamStandings(groupId: Int, sportId: Int): List<TeamStanding> =
        withContext(Dispatchers.IO) {
            getText("/Clasificacion/ClasificacionTemporada")
            val response = getJsonObject(
                "/Clasificaciones/ObtenerClasificacionJornadaGrupo" +
                    "?IdGrupo=$groupId&IdJornada=0&IdDeporte=$sportId&IdEquipo=0"
            )
            parseStandings(response)
        }

    suspend fun groupResults(
        sportId: Int,
        competitionId: Int,
        groupId: Int
    ): List<CalendarMatch> = withContext(Dispatchers.IO) {
        val roundResponse = getJsonObject(
            "/Calendarios/ObtenerDatosJornadaGrupo" +
                "?IdDeporte=$sportId&IdCompeticion=$competitionId" +
                "&IdCategoria=-1&IdGrupo=$groupId&IdJornada=0&IdEquipo=0"
        )
        val rounds = roundResponse.optJSONArray("jornadas") ?: JSONArray()

        coroutineScope {
            val calendars = (0 until rounds.length()).mapNotNull { index ->
                val roundNumber = rounds.getJSONObject(index).optInt("NumeroJornada")
                if (roundNumber > 0) {
                    async(Dispatchers.IO) {
                        getJsonArray(
                            "/Calendarios/ObtenerCalendarioAgrupado" +
                                "?IdDeporte=$sportId&IdCompeticion=$competitionId" +
                                "&IdCategoria=-1&NumeroJornada=$roundNumber&IdEquipo=0"
                        )
                    }
                } else null
            }.awaitAll()

            val matches = mutableMapOf<Int, CalendarMatch>()
            calendars.forEach { calendar ->
                collectMatchesForGroup(calendar, groupId, matches)
            }
            matches.values.sortedWith(compareBy({ it.roundNumber }, { it.rawDateTime ?: "" }))
        }
    }

    private fun activeSeasonId(): Int {
        val page = getText("/Calendarios/CalendarioTemporada")
        val match = ACTIVE_SEASON.find(page)
            ?: throw IOException("No se pudo identificar la temporada activa en la web.")
        return match.groupValues[1].toIntOrNull()
            ?: throw IOException("La temporada activa publicada no tiene un identificador válido.")
    }

    private fun collectTeams(competitions: JSONArray, result: MutableMap<Int, Team>) {
        for (competitionIndex in 0 until competitions.length()) {
            val categories = competitions.getJSONObject(competitionIndex).optJSONArray("Categorias")
                ?: continue
            for (categoryIndex in 0 until categories.length()) {
                val groups = categories.getJSONObject(categoryIndex).optJSONArray("Grupos")
                    ?: continue
                for (groupIndex in 0 until groups.length()) {
                    val fixtures = groups.getJSONObject(groupIndex).optJSONArray("Enfrentamientos")
                        ?: continue
                    for (fixtureIndex in 0 until fixtures.length()) {
                        val fixture = fixtures.getJSONObject(fixtureIndex)
                        addTeam(fixture, "IdEquipoLocal", "EquipoLocal", "URLEscudoEquipoLocal", result)
                        addTeam(fixture, "IdEquipoVisitante", "EquipoVisitante", "URLEscudoEquipoVisitante", result)
                    }
                }
            }
        }
    }

    private fun addTeam(
        fixture: JSONObject,
        idField: String,
        nameField: String,
        shieldField: String,
        result: MutableMap<Int, Team>
    ) {
        val id = fixture.optInt(idField)
        val name = fixture.optString(nameField).trim()
        val shieldRaw = fixture.optString(shieldField).trim()
        val shieldUrl = normalizeShieldUrl(shieldRaw)
        if (id > 0 && name.isNotBlank() && name != "null") {
            val existing = result[id]
            if (existing == null) {
                result[id] = Team(id, name, shieldUrl)
            } else if (existing.shieldUrl.isNullOrBlank() && !shieldUrl.isNullOrBlank()) {
                result[id] = existing.copy(shieldUrl = shieldUrl)
            }
        }
    }

    private fun collectMatchesForTeam(
        competitions: JSONArray,
        teamId: Int,
        result: MutableList<CalendarMatch>
    ) {
        for (competitionIndex in 0 until competitions.length()) {
            val categories = competitions.getJSONObject(competitionIndex).optJSONArray("Categorias")
                ?: continue
            for (categoryIndex in 0 until categories.length()) {
                val groups = categories.getJSONObject(categoryIndex).optJSONArray("Grupos")
                    ?: continue
                for (groupIndex in 0 until groups.length()) {
                    val group = groups.getJSONObject(groupIndex)
                    val fixtures = group.optJSONArray("Enfrentamientos")
                        ?: continue
                    for (fixtureIndex in 0 until fixtures.length()) {
                        val fixture = fixtures.getJSONObject(fixtureIndex)
                        val idLocal = fixture.optInt("IdEquipoLocal")
                        val idVisitante = fixture.optInt("IdEquipoVisitante")
                        if (idLocal == teamId || idVisitante == teamId) {
                            val match = parseCalendarMatch(fixture)
                            result.add(
                                if (match.groupId > 0) match
                                else match.copy(groupId = group.optInt("Id"))
                            )
                        }
                    }
                }
            }
        }
    }

    private fun collectMatchesForGroup(
        competitions: JSONArray,
        groupId: Int,
        result: MutableMap<Int, CalendarMatch>
    ) {
        for (competitionIndex in 0 until competitions.length()) {
            val categories = competitions.getJSONObject(competitionIndex).optJSONArray("Categorias")
                ?: continue
            for (categoryIndex in 0 until categories.length()) {
                val groups = categories.getJSONObject(categoryIndex).optJSONArray("Grupos")
                    ?: continue
                for (groupIndex in 0 until groups.length()) {
                    val group = groups.getJSONObject(groupIndex)
                    if (group.optInt("IdGrupo", group.optInt("Id")) != groupId) continue
                    val fixtures = group.optJSONArray("Enfrentamientos") ?: continue
                    for (fixtureIndex in 0 until fixtures.length()) {
                        val fixture = fixtures.getJSONObject(fixtureIndex)
                        val match = parseCalendarMatch(fixture)
                        result[match.id] = if (match.groupId > 0) {
                            match
                        } else {
                            match.copy(groupId = groupId)
                        }
                    }
                }
            }
        }
    }

    internal fun parseCalendarMatch(fixture: JSONObject): CalendarMatch {
        val id = fixture.optInt("Id")
        val roundNumber = fixture.optInt("NumeroJornada")
        val rawDateTime = fixture.optString("FechaHora").takeUnless { it == "null" || it.isBlank() }
        val (formattedDate, formattedTime) = formatDateTime(rawDateTime)

        val idLocal = fixture.optInt("IdEquipoLocal")
        val nameLocal = fixture.optString("EquipoLocal").trim()
        val shieldLocal = normalizeShieldUrl(fixture.optString("URLEscudoEquipoLocal"))

        val idVisitante = fixture.optInt("IdEquipoVisitante")
        val nameVisitante = fixture.optString("EquipoVisitante").trim()
        val shieldVisitante = normalizeShieldUrl(fixture.optString("URLEscudoEquipoVisitante"))

        val isResting = (idLocal == 0 || idVisitante == 0)

        val statusId = fixture.optInt("IdEstadoPartido", 0)
        val statusName = fixture.optString("EstadoPartido").takeUnless { it == "null" || it.isBlank() }
        val isPostponed = (statusId == 1)
        val isDesprogramado = (statusId == 12)
        val status = when {
            isResting -> "Descansa"
            isDesprogramado -> "Desprogramado"
            isPostponed -> "Aplazado"
            !statusName.isNullOrBlank() -> statusName
            else -> "Pendiente"
        }

        val field = formatField(fixture)
        val lat = fixture.optDouble("Latitud", 0.0)
        val lon = fixture.optDouble("Longitud", 0.0)

        val isClosed = fixture.optBoolean("Cerrado", false)
        val scoreLocal = fixture.optInt("ResultadoEquipoLocal", -1)
        val scoreVisitante = fixture.optInt("ResultadoEquipoVisitante", -1)
        val isPlayed = statusName?.contains("jugad", ignoreCase = true) == true ||
            statusName?.contains("finaliz", ignoreCase = true) == true ||
            (isClosed && scoreLocal >= 0 && scoreVisitante >= 0) ||
            scoreLocal > 0 || scoreVisitante > 0
        val hasScore = isPlayed

        return CalendarMatch(
            id = id,
            roundNumber = roundNumber,
            groupId = fixture.optInt("IdGrupo"),
            roundName = "Jornada $roundNumber",
            date = formattedDate,
            time = formattedTime,
            rawDateTime = rawDateTime,
            field = field,
            latitude = lat,
            longitude = lon,
            status = status,
            localTeamId = idLocal,
            localTeamName = nameLocal,
            localShieldUrl = shieldLocal,
            visitorTeamId = idVisitante,
            visitorTeamName = nameVisitante,
            visitorShieldUrl = shieldVisitante,
            localScore = if (hasScore && scoreLocal >= 0) scoreLocal else null,
            visitorScore = if (hasScore && scoreVisitante >= 0) scoreVisitante else null,
            isResting = isResting,
            isPostponed = isPostponed,
            isModified = fixture.optBoolean("CambioAdministrador", false),
            isPlayed = !isResting && isPlayed
        )
    }

    internal fun parseStandings(response: JSONObject): List<TeamStanding> {
        val standings = response.optJSONArray("clasificacionJornada") ?: JSONArray()
        return (0 until standings.length()).map { index ->
            val row = standings.getJSONObject(index)
            TeamStanding(
                position = row.optInt("Posicion"),
                teamName = row.optString("Equipo").takeUnless { it == "null" }.orEmpty(),
                points = row.optInt("Puntos"),
                matchesPlayed = row.optInt("Disputados"),
                goalDifference = row.optInt("Diferencia")
            )
        }
    }

    internal fun formatDateTime(raw: String?): Pair<String, String> {
        if (raw.isNullOrBlank()) return Pair("Sin fecha", "--:--")
        return try {
            val ldt = LocalDateTime.parse(raw.take(19))
            val date = ldt.format(DATE_FORMATTER)
            val time = if (ldt.hour == 0 && ldt.minute == 0) {
                "Hora por confirmar"
            } else {
                ldt.format(TIME_FORMATTER)
            }
            Pair(date, time)
        } catch (_: Exception) {
            Pair(raw.take(10), "--:--")
        }
    }

    internal fun formatField(fixture: JSONObject): String {
        val direct = fixture.optString("Campo").trim()
        if (direct.isNotBlank() && direct != "null" && !direct.equals("sin campo asignado", ignoreCase = true)) {
            return direct
        }
        val campoJuego = fixture.optJSONObject("CampoJuego")
        if (campoJuego != null) {
            val direccion = campoJuego.optString("Direccion").trim()
            val cp = campoJuego.optString("CP").trim()
            val municipio = campoJuego.optString("Municipio").trim()
            val nombre = campoJuego.optString("Nombre").trim()
            val parts = mutableListOf<String>()
            if (direccion.isNotBlank()) parts.add(direccion)
            val loc = listOf(cp, municipio).filter { it.isNotBlank() }.joinToString(" - ")
            if (loc.isNotBlank()) parts.add("($loc)")
            if (nombre.isNotBlank()) parts.add(nombre)
            val combined = parts.joinToString(" ")
            if (combined.isNotBlank()) return combined
        }
        return "Sin campo asignado"
    }

    internal fun normalizeShieldUrl(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null" || raw.contains("NoEscudo", ignoreCase = true)) {
            return null
        }
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "$BASE_URL${if (trimmed.startsWith("/")) "" else "/"}$trimmed"
        }
    }

    private fun JSONArray.toObjects(): List<JSONObject> =
        (0 until length()).map { getJSONObject(it) }

    private fun getJsonObject(path: String): JSONObject =
        parseWrappedJson(getText(path)) as? JSONObject
            ?: throw IOException("La web devolvió un objeto JSON con un formato inesperado.")

    private fun getJsonArray(path: String): JSONArray =
        parseWrappedJson(getText(path)) as? JSONArray
            ?: throw IOException("La web devolvió una lista JSON con un formato inesperado.")

    private fun parseWrappedJson(raw: String): Any {
        val parsed = JSONTokener(raw).nextValue()
        return when (parsed) {
            is JSONObject -> parsed
            is JSONArray -> parsed
            is String -> parseWrappedJson(parsed)
            else -> throw IOException("La respuesta de la web tiene un formato inesperado.")
        }
    }

    private fun getText(path: String): String {
        var url = URL("$BASE_URL$path")
        for (redirectCount in 0..MAX_REDIRECTS) {
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                val cookieHeaders = synchronized(cookieManager) {
                    cookieManager.get(url.toURI(), emptyMap())
                }
                cookieHeaders.forEach { (name, values) ->
                    values.forEach { value -> connection.addRequestProperty(name, value) }
                }
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Accept", "application/json, text/html")
                connection.setRequestProperty("User-Agent", "ECMadridDeportes/1.0 (Android)")

                val statusCode = connection.responseCode
                synchronized(cookieManager) {
                    cookieManager.put(url.toURI(), connection.headerFields)
                }
                if (statusCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("La web devolvió una redirección sin destino.")
                    val redirectedUrl = URL(url, location)
                    if (redirectedUrl.protocol != "https" || redirectedUrl.host != BASE_HOST) {
                        throw IOException("La web intentó redirigir la consulta a un servidor no permitido.")
                    }
                    if (redirectCount == MAX_REDIRECTS) {
                        throw IOException("La web superó el máximo de redirecciones permitido.")
                    }
                    url = redirectedUrl
                    continue
                }

                val stream = if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (statusCode !in 200..299) {
                    throw IOException("La web respondió con el código HTTP $statusCode.")
                }
                if (body.isBlank()) throw IOException("La web devolvió una respuesta vacía.")
                return body
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("La web superó el máximo de redirecciones permitido.")
    }

    private companion object {
        const val BASE_URL = "https://juegosdeportivos.ecmadrid.org"
        const val BASE_HOST = "juegosdeportivos.ecmadrid.org"
        const val MAX_REDIRECTS = 5
        val ACTIVE_SEASON =
            Regex(""""Id":(\d+),"Nombre":"TEMPORADA [^"]+".*?"Activa":true""", RegexOption.DOT_MATCHES_ALL)
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
