package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.auth.AuthProvider
import com.example.ui.VaultViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.GoogleLogoIcon
import com.example.ui.screens.CreateExpiringScreen
import com.example.ui.screens.CreatePasswordScreen
import com.example.ui.screens.DesktopViewerSimulatorScreen
import com.example.ui.screens.UnlockViewerScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusRevoked

enum class ScreenTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    SELF_EXPIRING("Expiring", Icons.Default.Timer, Icons.Outlined.Timer, "tab_self_expiring"),
    PASSWORD_PROTECT("Password", Icons.Default.Lock, Icons.Outlined.Lock, "tab_password_protect"),
    VAULT("Vault", Icons.Default.Folder, Icons.Outlined.Folder, "tab_vault"),
    VIEWER("Viewer", Icons.Default.LockOpen, Icons.Outlined.LockOpen, "tab_viewer")
}

class MainActivity : ComponentActivity() {

    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle incoming intent if an .efile was opened
        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        try {
            val (bytes, _) = viewModel.repository.readUriBytes(uri)
            val name = viewModel.repository.getFileName(uri)
            viewModel.prepareUnlockForFile(bytes, name)
            Toast.makeText(this, "Loading $name in Compatible Viewer...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load efile: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: VaultViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTab by remember { mutableStateOf(ScreenTab.SELF_EXPIRING) }
    var viewingDesktopKeyId by remember { mutableStateOf<String?>(null) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var guestSessionAllowed by remember { mutableStateOf(false) }

    // If an efile is loaded via intent or action, automatically switch to VIEWER tab
    LaunchedEffect(uiState.inspectingMetadata) {
        if (uiState.inspectingMetadata != null) {
            guestSessionAllowed = true
            currentTab = ScreenTab.VIEWER
        }
    }

    // Snackbar notifications
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // If user is unauthenticated and hasn't chosen guest mode, show Firebase Login & Register directly
    if (uiState.currentUser == null && !guestSessionAllowed) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background
        ) {
            AuthScreen(
                viewModel = viewModel,
                onAuthSuccess = {
                    guestSessionAllowed = true
                },
                onSkip = {
                    guestSessionAllowed = true
                }
            )
        }
    } else if (viewingDesktopKeyId != null) {
        BackHandler { viewingDesktopKeyId = null }
        DesktopViewerSimulatorScreen(
            keyId = viewingDesktopKeyId!!,
            viewModel = viewModel,
            onBack = { viewingDesktopKeyId = null }
        )
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EFile Vault",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        val user = uiState.currentUser
                        if (user != null) {
                            Surface(
                                color = if (user.authProvider == AuthProvider.GOOGLE) CyanPrimary.copy(alpha = 0.2f) else EmeraldTertiary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .clickable { showAccountSheet = true }
                                    .testTag("button_user_profile")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (user.authProvider == AuthProvider.GOOGLE) {
                                        GoogleLogoIcon(modifier = Modifier.size(16.dp))
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = EmeraldTertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = user.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showAuthDialog = true },
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("button_open_auth")
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sign In / Register", fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("main_navigation_bar")
                ) {
                    ScreenTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        val itemColor = when (tab) {
                            ScreenTab.SELF_EXPIRING -> AmberSecondary
                            ScreenTab.PASSWORD_PROTECT -> EmeraldTertiary
                            ScreenTab.VAULT -> CyanPrimary
                            ScreenTab.VIEWER -> MaterialTheme.colorScheme.primary
                        }

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                if (tab == ScreenTab.VAULT && uiState.items.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = CyanPrimary) {
                                                Text(uiState.items.size.toString(), color = Color(0xFF003049))
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                }
                            },
                            label = { Text(tab.title, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = itemColor,
                                selectedTextColor = itemColor,
                                indicatorColor = itemColor.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    ScreenTab.SELF_EXPIRING -> {
                        CreateExpiringScreen(
                            viewModel = viewModel,
                            onNavigateToViewer = { bytes, fileName ->
                                viewModel.prepareUnlockForFile(bytes, fileName)
                                currentTab = ScreenTab.VIEWER
                            },
                            onNavigateToDesktopSim = { keyId ->
                                viewingDesktopKeyId = keyId
                            }
                        )
                    }

                    ScreenTab.PASSWORD_PROTECT -> {
                        CreatePasswordScreen(
                            viewModel = viewModel,
                            onNavigateToViewer = { bytes, fileName ->
                                viewModel.prepareUnlockForFile(bytes, fileName)
                                currentTab = ScreenTab.VIEWER
                            }
                        )
                    }

                    ScreenTab.VAULT -> {
                        VaultScreen(
                            viewModel = viewModel,
                            onNavigateToViewer = { bytes, fileName ->
                                viewModel.prepareUnlockForFile(bytes, fileName)
                                currentTab = ScreenTab.VIEWER
                            },
                            onNavigateToDesktopSim = { keyId ->
                                viewingDesktopKeyId = keyId
                            },
                            onNavigateToCreateExpiring = { currentTab = ScreenTab.SELF_EXPIRING },
                            onNavigateToCreatePassword = { currentTab = ScreenTab.PASSWORD_PROTECT }
                        )
                    }

                    ScreenTab.VIEWER -> {
                        UnlockViewerScreen(
                            viewModel = viewModel,
                            onCloseViewer = { currentTab = ScreenTab.VAULT }
                        )
                    }
                }
            }
        }
    }

    // Auth Dialog (Full screen dialog for Login & Registration)
    if (showAuthDialog) {
        Dialog(
            onDismissRequest = { showAuthDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AuthScreen(
                    viewModel = viewModel,
                    onAuthSuccess = { showAuthDialog = false },
                    onSkip = { showAuthDialog = false }
                )
            }
        }
    }

    // Account Details BottomSheet
    if (showAccountSheet && uiState.currentUser != null) {
        val user = uiState.currentUser!!
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (user.authProvider == AuthProvider.GOOGLE) CyanPrimary.copy(alpha = 0.2f) else EmeraldTertiary.copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (user.authProvider == AuthProvider.GOOGLE) {
                            GoogleLogoIcon(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = EmeraldTertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = user.displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = user.email,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Authentication Provider:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = if (user.authProvider == AuthProvider.GOOGLE) CyanPrimary.copy(alpha = 0.15f) else EmeraldTertiary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (user.authProvider == AuthProvider.GOOGLE) "Google Account" else "Manual Local PBKDF2",
                            color = if (user.authProvider == AuthProvider.GOOGLE) CyanPrimary else EmeraldTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "User ID: ${user.uid}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showAccountSheet = false
                            showAuthDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Switch Account", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.authManager.signOut()
                            guestSessionAllowed = false
                            showAccountSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusRevoked),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("button_sign_out")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sign Out", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
