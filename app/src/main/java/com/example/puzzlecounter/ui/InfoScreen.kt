package com.example.puzzlecounter.ui

import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.example.puzzlecounter.R

/** The privacy policy of the app on the web (also given in the Play Console, which wants it inside the app as well). */
internal const val PRIVACY_POLICY_URL = "https://sites.google.com/view/piece-scanner/startseite"

/**
 * About the app. The top of the page is information about this app (version, privacy, developer); everything about
 * third-party software is in [LicensesSection] at the bottom, and the license texts themselves come last.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(uiState: PuzzleUiState, onBack: () -> Unit) {
    val context = LocalContext.current
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
    val versionName = if (packageInfo != null) {
        stringResource(R.string.info_version, packageInfo.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(packageInfo))
    } else {
        stringResource(R.string.info_version_unknown)
    }

    AppBackground(uiState = uiState) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.info_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent,
            modifier = Modifier.safeDrawingPadding()
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                // Header Card
                InfoCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = versionName,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Privacy Card: what the app does with photos, what the Google Play services runtime does on its own, the full policy
                InfoCard {
                    Column(modifier = Modifier.padding(24.dp)) {
                        CardTitle(R.string.info_privacy_title)
                        Spacer(Modifier.height(12.dp))
                        CardText(R.string.info_privacy_text)
                        Spacer(Modifier.height(12.dp))
                        CardText(R.string.info_privacy_services_text)
                        LinkRow(GOOGLE_PRIVACY_URL)
                        Spacer(Modifier.height(12.dp))
                        CardText(R.string.info_privacy_policy_text)
                        LinkRow(PRIVACY_POLICY_URL)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Developer Card
                InfoCard {
                    Column(modifier = Modifier.padding(24.dp)) {
                        CardTitle(R.string.info_developer_title)
                        Spacer(Modifier.height(12.dp))
                        CardText(R.string.info_developer_text)
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Everything about third-party software, the license texts at the very end
                LicensesSection()

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** The card the pages of the app use for a block of information. */
@Composable
internal fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(20.dp),
        content = content
    )
}

@Composable
internal fun CardTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
internal fun CardText(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
