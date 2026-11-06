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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import org.ecmadrid.deportes.model.sports
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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

    fun toggleFavorite(team: Team, sport: Sport, competition: Competition) {
        val favorite = Favorite(
            sportId = sport.id,
            sportName = sport.name,
            competitionId = competition.id,
            competitionName = competition.name,
            teamId = team.id,
            teamName = team.name,
            shieldUrl = team.shieldUrl
        )
        val exists = favorites.any { it.sameTeam(favorite) }
        val updated = if (exists) {
            favorites.filterNot { it.sameTeam(favorite) }
        } else {
            favorites + favorite
        }
        try {
            favoritesStore.save(updated)
            favorites = updated
            if (!exists) {
                activeCalendarTeamKey = "${favorite.sportId}:${favorite.competitionId}:${favorite.teamId}"
            }
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
        } catch (_: Exception) {
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("EC Madrid Deportes", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                                if (favorites.isEmpty()) "Calendarios"
                                else "Calendarios (${favorites.size})"
                            )
                        },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) }
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
                    onToggleFavorite = ::toggleFavorite,
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
                    onSelectTeam = { key ->
                        activeCalendarTeamKey = key
                        favorites.find { "${it.sportId}:${it.competitionId}:${it.teamId}" == key }?.let {
                            refreshCalendar(it, force = false)
                        }
                    },
                    onRefreshCalendar = { fav -> refreshCalendar(fav, force = true) },
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
    onToggleFavorite: (Team, Sport, Competition) -> Unit,
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
                    text = "Elige deporte y competición para ver y seleccionar equipos. Las imágenes de los equipos se capturan automáticamente.",
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
                    val isFavorite = favorites.any {
                        it.sportId == selectedSport?.id &&
                            it.competitionId == selectedCompetition?.id &&
                            it.teamId == team.id
                    }
                    val existingFav = favorites.find {
                        it.sportId == selectedSport?.id &&
                            it.competitionId == selectedCompetition?.id &&
                            it.teamId == team.id
                    }

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

                            Text(
                                text = team.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            if (isFavorite) {
                                Column(horizontalAlignment = Alignment.End) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedSport?.let { sport ->
                                                selectedCompetition?.let { comp ->
                                                    onToggleFavorite(team, sport, comp)
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Quitar")
                                    }
                                    if (existingFav != null) {
                                        Button(
                                            onClick = { onGoToCalendar(existingFav) },
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Text("Ver calendario")
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        selectedSport?.let { sport ->
                                            selectedCompetition?.let { comp ->
                                                onToggleFavorite(team, sport, comp)
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

@Composable
private fun CalendarsTab(
    favorites: List<Favorite>,
    activeKey: String?,
    teamCalendars: Map<String, List<CalendarMatch>>,
    calendarLoading: Map<String, Boolean>,
    calendarErrors: Map<String, String>,
    roundFilters: SnapshotStateMap<String, Int>,
    onSelectTeam: (String) -> Unit,
    onRefreshCalendar: (Favorite) -> Unit,
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

    // Load active team calendar when rendered if not already loaded
    LaunchedEffect(currentKey) {
        if (!teamCalendars.containsKey(currentKey)) {
            onRefreshCalendar(currentFavorite)
        }
    }

    val matches = teamCalendars[currentKey] ?: emptyList()
    val isLoading = calendarLoading[currentKey] == true
    val error = calendarErrors[currentKey]
    val selectedRound = roundFilters[currentKey]

    val availableRounds = remember(matches) {
        matches.map { it.roundNumber }.distinct().sorted()
    }

    val displayedMatches = remember(matches, selectedRound) {
        if (selectedRound == null) matches
        else matches.filter { it.roundNumber == selectedRound }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // TabRow of selected teams for separate management
        ScrollableTabRow(
            selectedTabIndex = favorites.indexOf(currentFavorite).coerceAtLeast(0),
            edgePadding = 12.dp
        ) {
            favorites.forEach { fav ->
                val favKey = "${fav.sportId}:${fav.competitionId}:${fav.teamId}"
                val isSelected = favKey == currentKey
                Tab(
                    selected = isSelected,
                    onClick = { onSelectTeam(favKey) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            TeamShield(
                                shieldUrl = fav.shieldUrl,
                                teamName = fav.teamName,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = fav.teamName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Team Header
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
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
            }

            // Round selector chips
            if (availableRounds.size > 1 && !isLoading && error == null) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedRound == null,
                                onClick = { roundFilters.remove(currentKey) },
                                label = { Text("Todas (${matches.size})") }
                            )
                        }
                        items(availableRounds) { roundNum ->
                            FilterChip(
                                selected = selectedRound == roundNum,
                                onClick = { roundFilters[currentKey] = roundNum },
                                label = { Text("Jornada $roundNum") }
                            )
                        }
                    }
                }
            }

            if (isLoading) {
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

            if (error != null) {
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

            if (!isLoading && error == null) {
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
                        MatchCard(match = match, activeTeamId = currentFavorite.teamId)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MatchCard(match: CalendarMatch, activeTeamId: Int) {
    val context = LocalContext.current

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
                    val name = if (match.localTeamId == activeTeamId) match.localTeamName else match.visitorTeamName

                    TeamShield(
                        shieldUrl = shield,
                        teamName = name,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = name,
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
                    // Local Team
                    TeamMatchRow(
                        name = match.localTeamName,
                        shieldUrl = match.localShieldUrl,
                        isCurrentTeam = match.localTeamId == activeTeamId,
                        score = match.localScore
                    )

                    // Visitor Team
                    TeamMatchRow(
                        name = match.visitorTeamName,
                        shieldUrl = match.visitorShieldUrl,
                        isCurrentTeam = match.visitorTeamId == activeTeamId,
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
        // Shield image to the left of the team name
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
