package org.ecmadrid.deportes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.ecmadrid.deportes.data.CalendarRepository
import org.ecmadrid.deportes.data.FavoritesStore
import org.ecmadrid.deportes.model.CalendarMatch
import org.ecmadrid.deportes.model.Competition
import org.ecmadrid.deportes.model.Favorite
import org.ecmadrid.deportes.model.Sport
import org.ecmadrid.deportes.model.Team
import org.ecmadrid.deportes.model.TeamStanding
import org.ecmadrid.deportes.model.sports
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val EcMadridRed = Color(0xFFAF2600)
private val EcMadridDarkRed = Color(0xFF821C00)

private val AppColorScheme = lightColorScheme(
    primary = EcMadridRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD3),
    onPrimaryContainer = EcMadridDarkRed,
    secondary = EcMadridDarkRed,
    onSecondary = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF242424)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AppColorScheme) {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppScreen() {
    val context = LocalContext.current
    val repository = remember { CalendarRepository() }
    val favoritesStore = remember { FavoritesStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var favorites by remember { mutableStateOf(favoritesStore.load()) }
    var activeCalendarTeamKey by remember {
        mutableStateOf(favorites.firstOrNull()?.let { "${it.sportId}:${it.competitionId}:${it.teamId}" })
    }

    // Keep active team valid when favorites change
    LaunchedEffect(favorites) {
        if (favorites.none { "${it.sportId}:${it.competitionId}:${it.teamId}" == activeCalendarTeamKey }) {
            activeCalendarTeamKey = favorites.firstOrNull()?.let { "${it.sportId}:${it.competitionId}:${it.teamId}" }
        }
    }

    // Cache team calendars independently so they don't reload unnecessarily
    val teamCalendars = remember { mutableStateMapOf<String, List<CalendarMatch>>() }
    val calendarLoading = remember { mutableStateMapOf<String, Boolean>() }
    val calendarErrors = remember { mutableStateMapOf<String, String>() }
    val roundFilters = remember { mutableStateMapOf<String, Int>() }
    val teamStandings = remember { mutableStateMapOf<String, List<TeamStanding>>() }
    val standingsLoading = remember { mutableStateMapOf<String, Boolean>() }
    val standingsErrors = remember { mutableStateMapOf<String, String>() }
    val groupResults = remember { mutableStateMapOf<String, List<CalendarMatch>>() }
    val groupResultsLoading = remember { mutableStateMapOf<String, Boolean>() }
    val groupResultsErrors = remember { mutableStateMapOf<String, String>() }

    fun refreshCalendar(favorite: Favorite, force: Boolean = false) {
        val key = "${favorite.sportId}:${favorite.competitionId}:${favorite.teamId}"
        if (!force && teamCalendars.containsKey(key)) return
        calendarLoading[key] = true
        calendarErrors.remove(key)
        scope.launch {
            try {
                teamCalendars[key] = repository.teamCalendar(
                    favorite.sportId,
                    favorite.competitionId,
                    favorite.teamId
                )
            } catch (e: Exception) {
                calendarErrors[key] = e.message ?: "No se pudo cargar el calendario de este equipo."
            } finally {
                calendarLoading[key] = false
            }
        }
    }

    fun refreshStandings(favorite: Favorite, groupId: Int) {
        val key = "${favorite.sportId}:${favorite.competitionId}:${favorite.teamId}"
        if (groupId <= 0 || standingsLoading[key] == true || teamStandings.containsKey(key)) return
        standingsLoading[key] = true
        standingsErrors.remove(key)
        scope.launch {
            try {
                teamStandings[key] = repository.teamStandings(groupId, favorite.sportId)
            } catch (e: Exception) {
                standingsErrors[key] = e.message ?: "No se pudo cargar la clasificación de este equipo."
            } finally {
                standingsLoading[key] = false
            }
        }
    }

    fun refreshGroupResults(favorite: Favorite, groupId: Int) {
        val key = "${favorite.sportId}:${favorite.competitionId}:${favorite.teamId}"
        if (groupId <= 0 || groupResultsLoading[key] == true || groupResults.containsKey(key)) return
        groupResultsLoading[key] = true
        groupResultsErrors.remove(key)
        scope.launch {
            try {
                groupResults[key] = repository.groupResults(
                    favorite.sportId,
                    favorite.competitionId,
                    groupId
                )
            } catch (e: Exception) {
                groupResultsErrors[key] = e.message ?: "No se pudieron cargar los resultados de este grupo."
            } finally {
                groupResultsLoading[key] = false
            }
        }
    }

    fun addFavoriteWithAlias(team: Team, sport: Sport, competition: Competition, alias: String?) {
        val favorite = Favorite(
            sportId = sport.id,
            sportName = sport.name,
            competitionId = competition.id,
            competitionName = competition.name,
            teamId = team.id,
            teamName = team.name,
            shieldUrl = team.shieldUrl,
            alias = alias
        )
        val updated = favorites.filterNot { it.sameTeam(favorite) } + favorite
        try {
            favoritesStore.save(updated)
            favorites = updated
            activeCalendarTeamKey = "${favorite.sportId}:${favorite.competitionId}:${favorite.teamId}"
        } catch (_: Exception) {
        }
    }

    fun updateFavoriteAlias(favorite: Favorite, newAlias: String?) {
        val updated = favorites.map {
            if (it.sameTeam(favorite)) it.copy(alias = newAlias) else it
        }
        try {
            favoritesStore.save(updated)
            favorites = updated
        } catch (_: Exception) {
        }
    }

    fun removeFavorite(favorite: Favorite) {
        val updated = favorites.filterNot { it.sameTeam(favorite) }
        try {
            favoritesStore.save(updated)
            favorites = updated
            val key = "${favorite.sportId}:${favorite.competitionId}:${favorite.teamId}"
            teamCalendars.remove(key)
            calendarLoading.remove(key)
            calendarErrors.remove(key)
            roundFilters.remove(key)
            teamStandings.remove(key)
            standingsLoading.remove(key)
            standingsErrors.remove(key)
            groupResults.remove(key)
            groupResultsLoading.remove(key)
            groupResultsErrors.remove(key)
        } catch (_: Exception) {
        }
    }

    val activeFavorite = favorites.find { "${it.sportId}:${it.competitionId}:${it.teamId}" == activeCalendarTeamKey }
        ?: favorites.firstOrNull()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (selectedTabIndex == 1 && activeFavorite != null) {
                            // Top bar shows the team shield on the left of the alias!
                            val calendarTitle = activeFavorite.calendarDisplayName
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TeamShield(
                                    shieldUrl = activeFavorite.shieldUrl,
                                    teamName = activeFavorite.teamName,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = calendarTitle,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Text("EC Madrid Deportes", fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = EcMadridRed,
                        titleContentColor = Color.White
                    )
                )
                PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Equipos") },
                        icon = { Icon(Icons.Default.Groups, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Text(
                                if (favorites.isEmpty()) "Favoritos"
                                else "Favoritos (${favorites.size})"
                            )
                        },
                        icon = { Icon(Icons.Default.Star, contentDescription = null) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedTabIndex == 0) {
                TeamsSelectionTab(
                    repository = repository,
                    favorites = favorites,
                    onAddFavorite = ::addFavoriteWithAlias,
                    onUpdateAlias = ::updateFavoriteAlias,
                    onRemoveFavorite = ::removeFavorite,
                    onGoToCalendar = { fav ->
                        activeCalendarTeamKey = "${fav.sportId}:${fav.competitionId}:${fav.teamId}"
                        selectedTabIndex = 1
                    }
                )
            } else {
                CalendarsTab(
                    favorites = favorites,
                    activeKey = activeCalendarTeamKey,
                    teamCalendars = teamCalendars,
                    calendarLoading = calendarLoading,
                    calendarErrors = calendarErrors,
                    roundFilters = roundFilters,
                    teamStandings = teamStandings,
                    standingsLoading = standingsLoading,
                    standingsErrors = standingsErrors,
                    onRefreshStandings = ::refreshStandings,
                    groupResults = groupResults,
                    groupResultsLoading = groupResultsLoading,
                    groupResultsErrors = groupResultsErrors,
                    onRefreshGroupResults = ::refreshGroupResults,
                    onSelectTeam = { key ->
                        activeCalendarTeamKey = key
                        favorites.find { "${it.sportId}:${it.competitionId}:${it.teamId}" == key }?.let {
                            refreshCalendar(it, force = false)
                        }
                    },
                    onRefreshCalendar = { fav -> refreshCalendar(fav, force = true) },
                    onUpdateAlias = ::updateFavoriteAlias,
                    onRemoveFavorite = ::removeFavorite,
                    onGoToSelection = { selectedTabIndex = 0 }
                )
            }
        }
    }
}

@Composable
private fun TeamsSelectionTab(
    repository: CalendarRepository,
    favorites: List<Favorite>,
    onAddFavorite: (Team, Sport, Competition, String?) -> Unit,
    onUpdateAlias: (Favorite, String?) -> Unit,
    onRemoveFavorite: (Favorite) -> Unit,
    onGoToCalendar: (Favorite) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedSport by remember { mutableStateOf<Sport?>(null) }
    var selectedCompetition by remember { mutableStateOf<Competition?>(null) }
    var competitions by remember { mutableStateOf<List<Competition>>(emptyList()) }
    var teams by remember { mutableStateOf<List<Team>>(emptyList()) }
    var loadingCompetitions by remember { mutableStateOf(false) }
    var loadingTeams by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var currentPage by remember { mutableIntStateOf(1) }
    val pageSize = 8

    // State for alias dialog when adding a team
    var teamPendingAlias by remember { mutableStateOf<Triple<Team, Sport, Competition>?>(null) }
    var favoritePendingEditAlias by remember { mutableStateOf<Favorite?>(null) }

    val filteredTeams = remember(teams, searchQuery) {
        if (searchQuery.isBlank()) teams
        else teams.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    val totalPages = remember(filteredTeams, pageSize) {
        maxOf(1, (filteredTeams.size + pageSize - 1) / pageSize)
    }

    LaunchedEffect(searchQuery, selectedCompetition) {
        currentPage = 1
    }

    fun chooseSport(sport: Sport) {
        selectedSport = sport
        selectedCompetition = null
        competitions = emptyList()
        teams = emptyList()
        errorMessage = null
        loadingCompetitions = true
        scope.launch {
            try {
                competitions = repository.competitions(sport)
                if (competitions.isEmpty()) errorMessage = "No hay competiciones activas para este deporte."
            } catch (e: Exception) {
                errorMessage = e.message ?: "No se pudieron cargar las competiciones."
            } finally {
                loadingCompetitions = false
            }
        }
    }

    fun chooseCompetition(competition: Competition) {
        val sport = selectedSport ?: return
        selectedCompetition = competition
        teams = emptyList()
        errorMessage = null
        loadingTeams = true
        scope.launch {
            try {
                teams = repository.teams(sport, competition)
                if (teams.isEmpty()) errorMessage = "No hay equipos en las jornadas de esta competición."
            } catch (e: Exception) {
                errorMessage = e.message ?: "No se pudieron cargar los equipos."
            } finally {
                loadingTeams = false
            }
        }
    }

    val pagedTeams = remember(filteredTeams, currentPage, pageSize) {
        val start = (currentPage - 1) * pageSize
        filteredTeams.drop(start).take(pageSize)
    }

    // Dialog for setting alias when selecting a new team
    teamPendingAlias?.let { (team, sport, comp) ->
        AliasDialog(
            teamName = team.name,
            shieldUrl = team.shieldUrl,
            initialAlias = null,
            onDismiss = { teamPendingAlias = null },
            onConfirm = { alias ->
                onAddFavorite(team, sport, comp, alias)
                teamPendingAlias = null
            }
        )
    }

    // Dialog for editing existing alias
    favoritePendingEditAlias?.let { fav ->
        AliasDialog(
            teamName = fav.teamName,
            shieldUrl = fav.shieldUrl,
            initialAlias = fav.alias,
            onDismiss = { favoritePendingEditAlias = null },
            onConfirm = { newAlias ->
                onUpdateAlias(fav, newAlias)
                favoritePendingEditAlias = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "Selección de Equipos",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Elige deporte y competición para ver y seleccionar equipos. Al añadir un equipo podrás asignarle un alias personalizado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SelectionMenu(
                label = "Deporte",
                value = selectedSport?.name ?: "Seleccionar deporte",
                options = sports,
                optionLabel = { it.name },
                enabled = !loadingCompetitions && !loadingTeams,
                onSelected = ::chooseSport
            )
        }

        if (selectedSport != null) {
            item {
                SelectionMenu(
                    label = "Competición",
                    value = selectedCompetition?.name ?: "Seleccionar competición",
                    options = competitions,
                    optionLabel = { it.name },
                    enabled = !loadingCompetitions && !loadingTeams && competitions.isNotEmpty(),
                    onSelected = ::chooseCompetition
                )
            }
        }

        if (loadingCompetitions || loadingTeams) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            if (loadingTeams) "Capturando equipos y escudos de las jornadas…"
                            else "Cargando competiciones disponibles…"
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            item {
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (selectedCompetition != null && !loadingTeams && teams.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar equipo") },
                    placeholder = { Text("Ej. Corazonistas, Virgen...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                // Pagination controls
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (currentPage > 1) currentPage-- },
                            enabled = currentPage > 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Página anterior")
                        }

                        Text(
                            text = "Pág. $currentPage de $totalPages (${filteredTeams.size} equipos)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        IconButton(
                            onClick = { if (currentPage < totalPages) currentPage++ },
                            enabled = currentPage < totalPages
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Página siguiente")
                        }
                    }
                }
            }

            if (pagedTeams.isEmpty()) {
                item {
                    Text(
                        "No se encontró ningún equipo que coincida con la búsqueda.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(pagedTeams, key = { it.id }) { team ->
                    val existingFav = favorites.find {
                        it.sportId == selectedSport?.id &&
                            it.competitionId == selectedCompetition?.id &&
                            it.teamId == team.id
                    }
                    val isFavorite = existingFav != null

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFavorite)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Team shield image on the left of team name
                            TeamShield(
                                shieldUrl = team.shieldUrl,
                                teamName = team.name,
                                modifier = Modifier.size(44.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = team.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (existingFav != null && !existingFav.alias.isNullOrBlank()) {
                                    Text(
                                        text = existingFav.alias,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (isFavorite && existingFav != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        OutlinedButton(
                                            onClick = { favoritePendingEditAlias = existingFav }
                                        ) {
                                            Text("Alias")
                                        }
                                        OutlinedButton(
                                            onClick = { onRemoveFavorite(existingFav) }
                                        ) {
                                            Text("Quitar")
                                        }
                                    }
                                    Button(
                                        onClick = { onGoToCalendar(existingFav) },
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text("Ver calendario")
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        selectedSport?.let { sport ->
                                            selectedCompetition?.let { comp ->
                                                teamPendingAlias = Triple(team, sport, comp)
                                            }
                                        }
                                    }
                                ) {
                                    Text("Añadir")
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarsTab(
    favorites: List<Favorite>,
    activeKey: String?,
    teamCalendars: Map<String, List<CalendarMatch>>,
    calendarLoading: Map<String, Boolean>,
    calendarErrors: Map<String, String>,
    roundFilters: SnapshotStateMap<String, Int>,
    teamStandings: Map<String, List<TeamStanding>>,
    standingsLoading: Map<String, Boolean>,
    standingsErrors: Map<String, String>,
    onRefreshStandings: (Favorite, Int) -> Unit,
    groupResults: Map<String, List<CalendarMatch>>,
    groupResultsLoading: Map<String, Boolean>,
    groupResultsErrors: Map<String, String>,
    onRefreshGroupResults: (Favorite, Int) -> Unit,
    onSelectTeam: (String) -> Unit,
    onRefreshCalendar: (Favorite) -> Unit,
    onUpdateAlias: (Favorite, String?) -> Unit,
    onRemoveFavorite: (Favorite) -> Unit,
    onGoToSelection: () -> Unit
) {
    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ningún equipo seleccionado",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Para gestionar calendarios por separado, primero selecciona uno o más equipos en la pestaña 'Equipos'.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(onClick = onGoToSelection) {
                        Text("Buscar y seleccionar equipos")
                    }
                }
            }
        }
        return
    }

    val currentFavorite = favorites.find { "${it.sportId}:${it.competitionId}:${it.teamId}" == activeKey }
        ?: favorites.first()

    val currentKey = "${currentFavorite.sportId}:${currentFavorite.competitionId}:${currentFavorite.teamId}"

    var favoritePendingEditAlias by remember { mutableStateOf<Favorite?>(null) }

    // Load active team calendar when rendered if not already loaded
    LaunchedEffect(currentKey) {
        if (!teamCalendars.containsKey(currentKey)) {
            onRefreshCalendar(currentFavorite)
        }
    }

    // Dialog for editing alias from the calendar screen
    favoritePendingEditAlias?.let { fav ->
        AliasDialog(
            teamName = fav.teamName,
            shieldUrl = fav.shieldUrl,
            initialAlias = fav.alias,
            onDismiss = { favoritePendingEditAlias = null },
            onConfirm = { newAlias ->
                onUpdateAlias(fav, newAlias)
                favoritePendingEditAlias = null
            }
        )
    }

    val matches = teamCalendars[currentKey] ?: emptyList()
    val isLoading = calendarLoading[currentKey] == true
    val error = calendarErrors[currentKey]
    val selectedRound = roundFilters[currentKey]
    var selectedTeamPage by remember(currentKey) { mutableIntStateOf(0) }

    val availableRounds = remember(matches) {
        matches.map { it.roundNumber }.distinct().sorted()
    }

    val groupId = matches.firstOrNull { it.groupId > 0 }?.groupId ?: 0
    val groupMatches = groupResults[currentKey].orEmpty()
    val availableResultRounds = remember(groupMatches) {
        groupMatches.map { it.roundNumber }.distinct().sorted()
    }
    val displayedResultRounds = remember(groupMatches, selectedRound) {
        groupMatches
            .filter { selectedRound == null || it.roundNumber == selectedRound }
            .groupBy { it.roundNumber }
            .toSortedMap()
    }
    val isLoadingGroupResults = groupResultsLoading[currentKey] == true
    val groupResultsError = groupResultsErrors[currentKey]
    val standings = teamStandings[currentKey].orEmpty()
    val isLoadingStandings = standingsLoading[currentKey] == true
    val standingsError = standingsErrors[currentKey]

    LaunchedEffect(currentKey, selectedTeamPage, groupId) {
        if (selectedTeamPage == 2 && groupId > 0) {
            onRefreshStandings(currentFavorite, groupId)
        }
        if (selectedTeamPage == 1 && groupId > 0) {
            onRefreshGroupResults(currentFavorite, groupId)
        }
    }

    val displayedMatches = remember(matches, selectedRound) {
        if (selectedRound == null) matches
        else matches.filter { it.roundNumber == selectedRound }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // TabRow of selected teams for separate management - Shield on the left of the alias!
        ScrollableTabRow(
            selectedTabIndex = favorites.indexOf(currentFavorite).coerceAtLeast(0),
            edgePadding = 12.dp
        ) {
            favorites.forEach { fav ->
                val favKey = "${fav.sportId}:${fav.competitionId}:${fav.teamId}"
                val isSelected = favKey == currentKey
                val calendarTitle = fav.calendarDisplayName
                Tab(
                    selected = isSelected,
                    onClick = { onSelectTeam(favKey) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            // Team shield on the left of the alias in top bar tabs
                            TeamShield(
                                shieldUrl = fav.shieldUrl,
                                teamName = fav.teamName,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = calendarTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamShield(
                    shieldUrl = currentFavorite.shieldUrl,
                    teamName = currentFavorite.teamName,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentFavorite.teamName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!currentFavorite.alias.isNullOrBlank()) {
                            Text(
                                text = currentFavorite.alias,
                                style = MaterialTheme.typography.titleMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { favoritePendingEditAlias = currentFavorite },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar alias del calendario",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = "${currentFavorite.sportName} · ${currentFavorite.competitionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onRefreshCalendar(currentFavorite) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar calendario")
                }
                IconButton(onClick = { onRemoveFavorite(currentFavorite) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar de favoritos",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = selectedTeamPage) {
            Tab(
                selected = selectedTeamPage == 0,
                onClick = { selectedTeamPage = 0 },
                text = { Text("Calendario") },
                icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) }
            )
            Tab(
                selected = selectedTeamPage == 1,
                onClick = { selectedTeamPage = 1 },
                text = { Text("Resultados") },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
            )
            Tab(
                selected = selectedTeamPage == 2,
                onClick = { selectedTeamPage = 2 },
                text = { Text("Clasificación") },
                icon = { Icon(Icons.Default.Groups, contentDescription = null) }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Round selector chips
            if (
                (selectedTeamPage == 0 && availableRounds.size > 1 && !isLoading && error == null) ||
                    (selectedTeamPage == 1 && availableResultRounds.size > 1 && !isLoadingGroupResults)
            ) {
                val roundsForPage = if (selectedTeamPage == 0) availableRounds else availableResultRounds
                val itemCount = if (selectedTeamPage == 0) matches.size else availableResultRounds.size
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedRound == null,
                                onClick = { roundFilters.remove(currentKey) },
                                label = { Text("Todas ($itemCount)") }
                            )
                        }
                        items(roundsForPage) { roundNum ->
                            FilterChip(
                                selected = selectedRound == roundNum,
                                onClick = { roundFilters[currentKey] = roundNum },
                                label = { Text("Jornada $roundNum") }
                            )
                        }
                    }
                }
            }

            if (isLoading && selectedTeamPage != 2) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Cargando calendario del equipo...")
                    }
                }
            }

            if (error != null && selectedTeamPage != 2) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(onClick = { onRefreshCalendar(currentFavorite) }) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
            }

            if (selectedTeamPage == 0 && !isLoading && error == null) {
                if (displayedMatches.isEmpty()) {
                    item {
                        Text(
                            text = "No se encontraron partidos publicados para este equipo.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(displayedMatches, key = { it.id }) { match ->
                        MatchCard(match = match, activeFavorite = currentFavorite)
                    }
                }
            }

            if (selectedTeamPage == 1) {
                when {
                    isLoading && groupId == 0 -> item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Cargando datos del grupo...")
                        }
                    }
                    groupId == 0 && error != null -> item {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    groupId == 0 -> item {
                        Text(
                            text = "No se ha encontrado el grupo de este equipo.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    isLoadingGroupResults -> item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Cargando resultados del grupo...")
                        }
                    }
                    groupResultsError != null -> item {
                        Column(
                            modifier = Modifier.padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(groupResultsError, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { onRefreshGroupResults(currentFavorite, groupId) }) {
                                Text("Reintentar")
                            }
                        }
                    }
                    groupMatches.isEmpty() -> item {
                        Text(
                            text = "No hay partidos publicados para este grupo.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    else -> {
                        displayedResultRounds.forEach { (roundNumber, roundMatches) ->
                            item(key = "results-round-$roundNumber") {
                                GroupResultsCard(
                                    roundNumber = roundNumber,
                                    matches = roundMatches,
                                    activeFavorite = currentFavorite
                                )
                            }
                        }
                    }
                }
            }

            if (selectedTeamPage == 2) {
                when {
                    isLoading && groupId == 0 -> item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Cargando datos del equipo...")
                        }
                    }
                    groupId == 0 -> item {
                        Text(
                            text = error ?: "No se ha encontrado el grupo de este equipo.",
                            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    isLoadingStandings -> item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Cargando clasificación...")
                        }
                    }
                    standingsError != null -> item {
                        Column(
                            modifier = Modifier.padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(standingsError, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { onRefreshStandings(currentFavorite, groupId) }) {
                                Text("Reintentar")
                            }
                        }
                    }
                    standings.isEmpty() -> item {
                        Text(
                            text = "No hay clasificación publicada para este equipo.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    else -> {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                StandingTableHeader()
                                standings.forEach { standing ->
                                    StandingTableRow(standing)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StandingTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StandingColumn("Pos.", 0.6f, true, true)
        StandingColumn("Equipo", 1.3f, true)
        StandingColumn("Ptos.", 0.8f, true, true)
        StandingColumn("Jug.", 0.65f, true, true)
        StandingColumn("Dif. Goles", 1.05f, true, true)
    }
}

@Composable
private fun StandingTableRow(standing: TeamStanding) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StandingColumn(standing.position.toString(), 0.6f, centered = true)
        StandingColumn(standing.teamName, 1.3f)
        StandingColumn(standing.points.toString(), 0.8f, centered = true)
        StandingColumn(standing.matchesPlayed.toString(), 0.65f, centered = true)
        StandingColumn(standing.goalDifference.toString(), 1.05f, centered = true)
    }
}

@Composable
private fun GroupResultsCard(
    roundNumber: Int,
    matches: List<CalendarMatch>,
    activeFavorite: Favorite
) {
    val date = matches.firstOrNull()?.date.orEmpty()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "JORNADA $roundNumber${if (date.isNotBlank()) " · $date" else ""}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            matches.forEach { match ->
                val restingTeam = when {
                    match.localTeamId == 0 -> match.visitorTeamName
                    match.visitorTeamId == 0 -> match.localTeamName
                    else -> null
                }
                if (!restingTeam.isNullOrBlank()) {
                    Text(
                        text = "$restingTeam (D)",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (
                            match.localTeamId == activeFavorite.teamId ||
                            match.visitorTeamId == activeFavorite.teamId
                        ) FontWeight.Bold else FontWeight.Normal,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    GroupResultMatchRow(match)
                }
            }
        }
    }
}

@Composable
private fun GroupResultMatchRow(match: CalendarMatch) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = match.localTeamName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${match.localScore?.toString().orEmpty()} - ${match.visitorScore?.toString().orEmpty()}",
            modifier = Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = match.visitorTeamName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RowScope.StandingColumn(
    text: String,
    weight: Float,
    isHeader: Boolean = false,
    centered: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = if (isHeader) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        color = if (isHeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textAlign = if (centered) androidx.compose.ui.text.style.TextAlign.Center else null,
        maxLines = if (isHeader) 2 else 3,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun MatchCard(match: CalendarMatch, activeFavorite: Favorite) {
    val context = LocalContext.current
    val activeTeamId = activeFavorite.teamId

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Round + Date & Time + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = "JORNADA ${match.roundNumber}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${match.date.orEmpty()} ${match.time.orEmpty()}".trim(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusBadge(status = match.status.orEmpty(), isResting = match.isResting)
                }
            }

            // Body: Teams with images captured to the left of their names
            if (match.isResting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val shield = if (match.localTeamId == activeTeamId) match.localShieldUrl else match.visitorShieldUrl
                    val restingTeamName = if (match.localTeamId == activeTeamId) match.localTeamName else match.visitorTeamName

                    TeamShield(
                        shieldUrl = shield,
                        teamName = restingTeamName,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = restingTeamName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Descansa en esta jornada",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Local Team - always official team name
                    val isLocalCurrent = match.localTeamId == activeTeamId
                    TeamMatchRow(
                        name = match.localTeamName,
                        shieldUrl = match.localShieldUrl,
                        isCurrentTeam = isLocalCurrent,
                        score = match.localScore
                    )

                    // Visitor Team - always official team name
                    val isVisitorCurrent = match.visitorTeamId == activeTeamId
                    TeamMatchRow(
                        name = match.visitorTeamName,
                        shieldUrl = match.visitorShieldUrl,
                        isCurrentTeam = isVisitorCurrent,
                        score = match.visitorScore
                    )
                }
            }

            // Field / Location
            if (!match.field.isNullOrBlank() && !match.isResting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = match.field,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    if (match.field != "Sin campo asignado") {
                        IconButton(
                            onClick = {
                                openMap(context, match.field, match.latitude, match.longitude)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Map,
                                contentDescription = "Ver en Google Maps",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamMatchRow(
    name: String,
    shieldUrl: String?,
    isCurrentTeam: Boolean,
    score: Int?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shield image to the left of the team name / alias
        TeamShield(
            shieldUrl = shieldUrl,
            teamName = name,
            modifier = Modifier.size(36.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrentTeam) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrentTeam) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (score != null) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun AliasDialog(
    teamName: String,
    shieldUrl: String?,
    initialAlias: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var aliasText by remember { mutableStateOf(initialAlias.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alias para el calendario") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TeamShield(
                        shieldUrl = shieldUrl,
                        teamName = teamName,
                        modifier = Modifier.size(44.dp)
                    )
                    Column {
                        Text(
                            text = teamName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Equipo oficial",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = aliasText,
                    onValueChange = { aliasText = it },
                    label = { Text("Alias del calendario (opcional)") },
                    placeholder = { Text("Ej. Mi hijo, Infantil A, Mi equipo...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("El nombre oficial del equipo se mantiene inalterado. El alias solo se usa para organizar y gestionar los calendarios en las pestañas y barra superior.")
                    }
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialAlias == null) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text("Añadir sin alias")
                    }
                }
                Button(
                    onClick = {
                        onConfirm(aliasText.trim().takeIf { it.isNotBlank() })
                    }
                ) {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun StatusBadge(status: String, isResting: Boolean) {
    val bgColor: Color
    val textColor: Color

    when {
        isResting -> {
            bgColor = MaterialTheme.colorScheme.secondaryContainer
            textColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
        status.contains("Modificado", ignoreCase = true) -> {
            bgColor = Color(0xFFFFF3CD)
            textColor = Color(0xFF856404)
        }
        status.contains("Aplazado", ignoreCase = true) || status.contains("Desprogramado", ignoreCase = true) -> {
            bgColor = Color(0xFFF8D7DA)
            textColor = Color(0xFF721C24)
        }
        status.contains("Jugado", ignoreCase = true) || status.contains("Finalizado", ignoreCase = true) -> {
            bgColor = Color(0xFFD4EDDA)
            textColor = Color(0xFF155724)
        }
        else -> {
            bgColor = MaterialTheme.colorScheme.surfaceVariant
            textColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TeamShield(
    shieldUrl: String?,
    teamName: String,
    modifier: Modifier = Modifier.size(36.dp)
) {
    val context = LocalContext.current
    val fallbackUrl = "https://juegosdeportivos.ecmadrid.org/Content/images/NoEscudo.png"
    val imageUrl = remember(shieldUrl) {
        if (!shieldUrl.isNullOrBlank()) shieldUrl else fallbackUrl
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Escudo de $teamName",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            loading = {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            error = {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }
}

private fun openMap(context: android.content.Context, address: String, lat: Double, lon: Double) {
    try {
        val uri = if (lat != 0.0 && lon != 0.0) {
            Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(address)})")
        } else {
            Uri.parse("geo:0,0?q=${Uri.encode(address)}")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val webUri = if (lat != 0.0 && lon != 0.0) {
                Uri.parse("https://maps.google.com/?q=$lat,$lon")
            } else {
                Uri.parse("https://maps.google.com/?q=${URLEncoder.encode(address, StandardCharsets.UTF_8.name())}")
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun <T> SelectionMenu(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    enabled: Boolean = true,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

private fun Favorite.sameTeam(other: Favorite): Boolean =
    sportId == other.sportId && competitionId == other.competitionId && teamId == other.teamId
