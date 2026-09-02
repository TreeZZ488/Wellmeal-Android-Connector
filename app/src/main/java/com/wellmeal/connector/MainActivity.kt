package com.wellmeal.connector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                HealthConnectScreen()
            }
        }
    }
}

enum class Screen(val route: String, val title: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home),
    MedicalProfile("medical_profile", "Medical Profile", Icons.Default.Person),
    History("history", "History", Icons.Default.DateRange),
    Settings("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalPersonalHealthRecordApi::class)
@Composable
fun HealthConnectScreen() {

    val context = LocalContext.current

    // Check Health Connect availability.
    val sdkStatus = HealthConnectClient.getSdkStatus(context)

    if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text("Health Connect: Unavailable")
        }

        return
    }

    // Create the Health Connect client.
    val healthConnectClient = remember {
        HealthConnectClient.getOrCreate(context)
    }

    // Check Personal Health Record support.
    val personalHealthRecordAvailable = remember {
        healthConnectClient.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    // Define fitness permissions.
    val fitnessPermissions = remember {
        setOf(
            HealthPermission.getReadPermission(
                StepsRecord::class
            ),
            HealthPermission.getReadPermission(
                HeartRateRecord::class
            ),
            HealthPermission.getReadPermission(
                SleepSessionRecord::class
            ),
            HealthPermission.getReadPermission(
                ExerciseSessionRecord::class
            )
        )
    }

    // Define optional medical profile permissions.
    val medicalPermissions = remember {
        setOf(
            HealthPermission
                .PERMISSION_READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,

            HealthPermission
                .PERMISSION_READ_MEDICAL_DATA_MEDICATIONS
        )
    }

    // Check background health-data read support.
    val backgroundReadAvailable = remember {
        healthConnectClient.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    val backgroundPermissions = remember {
        setOf(
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        )
    }

    // Store all currently granted Health Connect permissions.
    var grantedPermissions by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    // Request fitness permissions.
    val fitnessPermissionLauncher =
        rememberLauncherForActivityResult(
            PermissionController
                .createRequestPermissionResultContract()
        ) { result ->

            grantedPermissions =
                grantedPermissions + result
        }

    // Request optional medical profile permissions.
    val medicalPermissionLauncher =
        rememberLauncherForActivityResult(
            PermissionController
                .createRequestPermissionResultContract()
        ) { result ->

            grantedPermissions =
                grantedPermissions + result
        }

    // Request background health read permission.
    val backgroundPermissionLauncher =
        rememberLauncherForActivityResult(
            PermissionController
                .createRequestPermissionResultContract()
        ) { result ->

            grantedPermissions =
                grantedPermissions + result
        }

    // Read the existing permission state when the app starts.
    LaunchedEffect(Unit) {

        grantedPermissions =
            healthConnectClient
                .permissionController
                .getGrantedPermissions()
    }

    val fitnessGrantedCount =
        fitnessPermissions.count {
            grantedPermissions.contains(it)
        }

    val fitnessAllGranted =
        grantedPermissions.containsAll(
            fitnessPermissions
        )

    val medicalGrantedCount =
        medicalPermissions.count {
            grantedPermissions.contains(it)
        }

    val medicalAllGranted =
        grantedPermissions.containsAll(
            medicalPermissions
        )

    val backgroundReadGranted =
        grantedPermissions.contains(
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        )

    // Create data services.
    val repository = remember {
        HealthConnectRepository(context)
    }

    val jsonExporter = remember {
        HealthJsonExporter(context)
    }

    val medicalRepository = remember {
        MedicalProfileRepository(context)
    }

    val medicalProfileParser = remember {
        MedicalProfileParser()
    }

    var medicalResult by remember {
        mutableStateOf<String?>(null)
    }

    var healthProfile by remember {
        mutableStateOf<HealthProfile?>(null)
    }

    val dietaryRestrictionStore = remember {
        DietaryRestrictionStore(context)
    }

    var dietaryRestrictions by remember {
        mutableStateOf(
            dietaryRestrictionStore.load()
        )
    }

    val profileJsonExporter = remember {
        HealthProfileJsonExporter(context)
    }

    val authManager = remember {
        MicrosoftAuthManager(context)
    }

    val oneDriveUploader = remember {
        OneDriveUploader()
    }

    val syncHistoryStore = remember {
        SyncHistoryStore(context)
    }

    val syncSettingsStore = remember {
        SyncSettingsStore(context)
    }

    var syncSettings by remember {
        mutableStateOf(syncSettingsStore.load())
    }

    val syncCoordinator = remember {
        SyncCoordinator(
            context = context,
            healthConnectRepository = repository,
            healthJsonExporter = jsonExporter,
            microsoftAuthManager = authManager,
            oneDriveUploader = oneDriveUploader,
            syncHistoryStore = syncHistoryStore
        )
    }

    var uploadResult by remember {
        mutableStateOf<String?>(null)
    }

    var isSyncing by remember {
        mutableStateOf(false)
    }

    var lastSyncResult by remember {
        mutableStateOf<SyncResult?>(null)
    }

    val scope = rememberCoroutineScope()

    // Store the currently loaded daily health snapshot.
    var snapshot by remember {
        mutableStateOf<DailyHealthSnapshot?>(null)
    }

    var dataLoaded by remember {
        mutableStateOf(false)
    }

    var loadError by remember {
        mutableStateOf<String?>(null)
    }

    var exportResult by remember {
        mutableStateOf<String?>(null)
    }

    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val screens = listOf(
                    Screen.Home,
                    Screen.MedicalProfile,
                    Screen.History,
                    Screen.Settings
                )

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    fitnessAllGranted = fitnessAllGranted,
                    authManager = authManager,
                    isSyncing = isSyncing,
                    lastSyncResult = lastSyncResult,
                    healthProfile = healthProfile,
                    dietaryRestrictions = dietaryRestrictions,
                    syncHistoryStore = syncHistoryStore,
                    syncSettings = syncSettings,
                    backgroundReadAvailable = backgroundReadAvailable,
                    backgroundReadGranted = backgroundReadGranted,
                    onSyncNow = {
                        scope.launch {
                            isSyncing = true
                            lastSyncResult = null
                            try {
                                val result = syncCoordinator.performSync()
                                lastSyncResult = result
                            } catch (e: Exception) {
                                lastSyncResult = SyncResult(
                                    date = java.time.LocalDate.now().minusDays(1),
                                    dailyUploaded = false,
                                    profileStatus = ProfileSyncStatus.SKIPPED,
                                    error = e.message ?: "Unknown sync error"
                                )
                            } finally {
                                isSyncing = false
                            }
                        }
                    }
                )
            }

            composable(Screen.MedicalProfile.route) {
                MedicalProfileScreen(
                    healthProfile = healthProfile,
                    dietaryRestrictions = dietaryRestrictions,
                    onAddDietaryRestriction = { newRestriction ->
                        val alreadyExists = dietaryRestrictions.any {
                            it.equals(newRestriction, ignoreCase = true)
                        }
                        if (!alreadyExists) {
                            val updated = (dietaryRestrictions + newRestriction).sortedBy { it.lowercase() }
                            dietaryRestrictions = updated
                            dietaryRestrictionStore.save(updated)
                            healthProfile = healthProfile?.copy(dietaryRestrictions = updated)
                        }
                    },
                    onRemoveDietaryRestriction = { restriction ->
                        val updated = dietaryRestrictions.filterNot { it == restriction }
                        dietaryRestrictions = updated
                        dietaryRestrictionStore.save(updated)
                        healthProfile = healthProfile?.copy(dietaryRestrictions = updated)
                    }
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    syncHistoryStore = syncHistoryStore
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    context = context,
                    syncSettings = syncSettings,
                    onSyncSettingsChanged = { updatedSettings ->
                        syncSettings = updatedSettings
                        syncSettingsStore.save(updatedSettings)
                    },
                    backgroundReadAvailable = backgroundReadAvailable,
                    backgroundReadGranted = backgroundReadGranted,
                    onLaunchBackgroundPermission = {
                        backgroundPermissionLauncher.launch(backgroundPermissions)
                    },
                    authManager = authManager,
                    oneDriveUploader = oneDriveUploader,
                    personalHealthRecordAvailable = personalHealthRecordAvailable,
                    fitnessAllGranted = fitnessAllGranted,
                    fitnessGrantedCount = fitnessGrantedCount,
                    fitnessPermissionsSize = fitnessPermissions.size,
                    onLaunchFitnessPermission = {
                        fitnessPermissionLauncher.launch(fitnessPermissions)
                    },
                    medicalAllGranted = medicalAllGranted,
                    medicalGrantedCount = medicalGrantedCount,
                    medicalPermissionsSize = medicalPermissions.size,
                    onLaunchMedicalPermission = {
                        medicalPermissionLauncher.launch(medicalPermissions)
                    },
                    medicalRepository = medicalRepository,
                    medicalProfileParser = medicalProfileParser,
                    healthProfile = healthProfile,
                    onHealthProfileUpdated = { healthProfile = it },
                    dietaryRestrictions = dietaryRestrictions,
                    profileJsonExporter = profileJsonExporter,
                    medicalResult = medicalResult,
                    onMedicalResultUpdated = { medicalResult = it },
                    uploadResult = uploadResult,
                    onUploadResultUpdated = { uploadResult = it },
                    repository = repository,
                    jsonExporter = jsonExporter,
                    snapshot = snapshot,
                    onSnapshotUpdated = { snapshot = it },
                    dataLoaded = dataLoaded,
                    onDataLoadedUpdated = { dataLoaded = it },
                    loadError = loadError,
                    onLoadErrorUpdated = { loadError = it },
                    exportResult = exportResult,
                    onExportResultUpdated = { exportResult = it },
                    scope = scope
                )
            }
        }
    }
}
