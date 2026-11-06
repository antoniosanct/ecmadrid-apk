package org.ecmadrid.deportes

import org.ecmadrid.deportes.data.CalendarRepository
import org.ecmadrid.deportes.model.CalendarMatch
import org.ecmadrid.deportes.model.Favorite
import org.ecmadrid.deportes.model.Team
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarRepositoryTest {

    private val repository = CalendarRepository()

    @Test
    fun `test normalizeShieldUrl with relative path`() {
        val raw = "/documentos/wallimages/imagepath/Emblema_0099.jpg"
        val normalized = repository.normalizeShieldUrl(raw)
        assertEquals("https://juegosdeportivos.ecmadrid.org/documentos/wallimages/imagepath/Emblema_0099.jpg", normalized)
    }

    @Test
    fun `test normalizeShieldUrl with null or empty or NoEscudo`() {
        assertNull(repository.normalizeShieldUrl(null))
        assertNull(repository.normalizeShieldUrl(""))
        assertNull(repository.normalizeShieldUrl("   "))
        assertNull(repository.normalizeShieldUrl("/Content/images/NoEscudo.png"))
    }

    @Test
    fun `test normalizeShieldUrl with full URL`() {
        val full = "https://example.com/logo.png"
        assertEquals(full, repository.normalizeShieldUrl(full))
    }

    @Test
    fun `test formatDateTime valid string`() {
        val raw = "2026-11-21T09:30:00"
        val (date, time) = repository.formatDateTime(raw)
        assertEquals("21/11/2026", date)
        assertEquals("09:30", time)
    }

    @Test
    fun `test formatDateTime with zero hours means unconfirmed time`() {
        val raw = "2026-11-14T00:00:00"
        val (date, time) = repository.formatDateTime(raw)
        assertEquals("14/11/2026", date)
        assertEquals("Hora por confirmar", time)
    }

    @Test
    fun `test parseCalendarMatch for played match with score`() {
        val json = JSONObject("""
            {
                "Id": 1234,
                "NumeroJornada": 3,
                "FechaHora": "2026-11-21T10:00:00",
                "IdEquipoLocal": 361,
                "EquipoLocal": "CORAZONISTAS",
                "URLEscudoEquipoLocal": "/documentos/wallimages/imagepath/Emblema_0099.jpg",
                "IdEquipoVisitante": 736,
                "EquipoVisitante": "VIRGEN DE MIRASIERRA",
                "URLEscudoEquipoVisitante": "/Documentos/Instituciones/55/Emblema_0055.PNG",
                "IdEstadoPartido": 0,
                "EstadoPartido": "Jugado",
                "Campo": "Pabellón Central",
                "ResultadoEquipoLocal": 54,
                "ResultadoEquipoVisitante": 48,
                "Cerrado": true
            }
        """.trimIndent())

        val match = repository.parseCalendarMatch(json)
        assertEquals(1234, match.id)
        assertEquals(3, match.roundNumber)
        assertEquals("21/11/2026", match.date)
        assertEquals("10:00", match.time)
        assertEquals("CORAZONISTAS", match.localTeamName)
        assertEquals("https://juegosdeportivos.ecmadrid.org/documentos/wallimages/imagepath/Emblema_0099.jpg", match.localShieldUrl)
        assertEquals("VIRGEN DE MIRASIERRA", match.visitorTeamName)
        assertEquals("https://juegosdeportivos.ecmadrid.org/Documentos/Instituciones/55/Emblema_0055.PNG", match.visitorShieldUrl)
        assertEquals(54, match.localScore)
        assertEquals(48, match.visitorScore)
        assertFalse(match.isResting)
    }

    @Test
    fun `test parseCalendarMatch for resting team`() {
        val json = JSONObject("""
            {
                "Id": 5678,
                "NumeroJornada": 1,
                "FechaHora": "2026-11-14T00:00:00",
                "IdEquipoLocal": 0,
                "EquipoLocal": "",
                "URLEscudoEquipoLocal": "",
                "IdEquipoVisitante": 361,
                "EquipoVisitante": "CORAZONISTAS",
                "URLEscudoEquipoVisitante": "/documentos/wallimages/imagepath/Emblema_0099.jpg",
                "IdEstadoPartido": 0,
                "EstadoPartido": "Pendiente de Jugar"
            }
        """.trimIndent())

        val match = repository.parseCalendarMatch(json)
        assertTrue(match.isResting)
        assertEquals("Descansa", match.status)
    }

    @Test
    fun `test pagination calculation`() {
        val totalTeams = 25
        val pageSize = 8
        val totalPages = maxOf(1, (totalTeams + pageSize - 1) / pageSize)
        assertEquals(4, totalPages)

        val page1 = (1..totalTeams).drop(0).take(pageSize).toList()
        assertEquals(8, page1.size)
        assertEquals(1, page1.first())
        assertEquals(8, page1.last())

        val page4 = (1..totalTeams).drop(24).take(pageSize).toList()
        assertEquals(1, page4.size)
        assertEquals(25, page4.first())
    }

    @Test
    fun `test team and favorite models`() {
        val team = Team(1, "Equipo A", "https://example.com/shield.png")
        val fav1 = Favorite(1, "Baloncesto", 100, "Comp A", 1, "Equipo A", team.shieldUrl)
        val fav2 = Favorite(1, "Baloncesto", 100, "Comp A", 1, "Equipo A")

        assertEquals("https://example.com/shield.png", fav1.shieldUrl)
        assertEquals(fav1.sportId, fav2.sportId)
        assertEquals(fav1.competitionId, fav2.competitionId)
        assertEquals(fav1.teamId, fav2.teamId)
    }
}
