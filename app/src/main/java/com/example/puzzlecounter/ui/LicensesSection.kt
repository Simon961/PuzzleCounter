package com.example.puzzlecounter.ui

import android.widget.Toast
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.puzzlecounter.R

internal const val GOOGLE_PRIVACY_URL = "https://policies.google.com/privacy"
private const val ANDROID_SDK_LICENSE_URL = "https://developer.android.com/studio/terms"
private const val GOOGLE_APIS_TERMS_URL = "https://developers.google.com/terms"
private const val ULTRALYTICS_LICENSE_URL = "https://www.ultralytics.com/license"
private const val ULTRALYTICS_SOURCE_URL = "https://github.com/ultralytics/ultralytics"

/** A third-party component the app is built with: its name, and its license and owner in one line. */
private class Component(@param:StringRes val name: Int, @param:StringRes val details: Int)

/** What ships in the app (checked against the shrunk release build, not against the list of dependencies). */
private val components = listOf(
    Component(R.string.info_component_androidx_name, R.string.info_component_androidx_details),
    Component(R.string.info_component_libyuv_name, R.string.info_component_libyuv_details),
    Component(R.string.info_component_tensorflow_name, R.string.info_component_tensorflow_details),
    Component(R.string.info_component_play_name, R.string.info_component_play_details),
    Component(R.string.info_component_model_name, R.string.info_component_model_details),
)

/**
 * What the app is built with, and under which licenses: the notices first, then - at the very end of the page - the
 * license texts, each folded away until it is asked for (the longest, the AGPL, is 34 KB). The texts are the official
 * English ones in every language, because only the original wording of a license is binding.
 */
@Composable
internal fun LicensesSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(R.string.info_licenses_title, R.string.info_licenses_intro)

        InfoCard {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardTitle(R.string.info_components_title)
                for (component in components) ComponentRow(component)
            }
        }

        InfoCard {
            Column(modifier = Modifier.padding(24.dp)) {
                CardTitle(R.string.info_model_title)
                Spacer(Modifier.height(12.dp))
                CardText(R.string.info_model_text)
                LinkRow(ULTRALYTICS_LICENSE_URL)
                LinkRow(ULTRALYTICS_SOURCE_URL)
            }
        }

        Spacer(Modifier.height(16.dp))

        // The license texts: all of them here, at the very end
        SectionHeader(R.string.info_license_texts_title, R.string.info_license_texts_note)

        InfoCard {
            LicenseTextRow(R.string.info_license_apache_title, R.string.info_license_apache_subtitle) {
                LicenseTextBody(R.raw.license_apache_2_0)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LicenseTextRow(R.string.info_license_bsd_title, R.string.info_license_bsd_subtitle) {
                LicenseTextBody(R.raw.license_libyuv_bsd_3_clause)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LicenseTextRow(R.string.info_license_google_title, R.string.info_license_google_subtitle) {
                CardText(R.string.info_license_google_text)
                LinkRow(ANDROID_SDK_LICENSE_URL)
                LinkRow(GOOGLE_APIS_TERMS_URL)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LicenseTextRow(R.string.info_license_agpl_title, R.string.info_license_agpl_subtitle) {
                LicenseTextBody(R.raw.license_agpl_3_0)
            }
        }
    }
}

/** A heading of the page that stands on the background, set off from what is above it by a line. */
@Composable
private fun SectionHeader(@StringRes title: Int, @StringRes text: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ComponentRow(component: Component) {
    Column {
        Text(
            text = stringResource(component.name),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(component.details),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A row that unfolds its content (a license text) below itself and folds it away again. */
@Composable
private fun LicenseTextRow(
    @StringRes title: Int,
    @StringRes subtitle: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.info_license_state_expanded else R.string.info_license_state_collapsed)
    Column {
        ListItem(
            onClick = { expanded = !expanded },
            modifier = Modifier.semantics { stateDescription = state },
            supportingContent = { Text(stringResource(subtitle)) },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        ) {
            Text(stringResource(title), fontWeight = FontWeight.Bold)
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

/** The text of a license from the raw resources, its paragraphs the way [LicenseText] cuts them. */
@Composable
private fun LicenseTextBody(@RawRes resource: Int) {
    val resources = LocalResources.current
    val paragraphs = remember(resource) {
        LicenseText.paragraphs(resources.openRawResource(resource).bufferedReader().use { it.readText() })
    }
    for (paragraph in paragraphs) {
        when (paragraph.kind) {
            LicenseText.Kind.TITLE -> Text(
                text = paragraph.text,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            LicenseText.Kind.HEADING -> Text(
                text = paragraph.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            LicenseText.Kind.BODY -> Text(
                text = paragraph.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A web address that opens in the browser when touched. The app itself never goes online (it has no internet
 * permission): the browser does, on request. Where there is no browser, a message says so.
 */
@Composable
internal fun LinkRow(url: String) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    // lighter than the primary color, which is too dark for text on the dark cards
    val linkColor = lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface, 0.4f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(R.string.info_link_open), role = Role.Button) {
                try {
                    uriHandler.openUri(url)
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.info_link_failed, Toast.LENGTH_SHORT).show()
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = linkColor
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = url.removePrefix("https://"),
            style = MaterialTheme.typography.bodyMedium,
            color = linkColor,
            textDecoration = TextDecoration.Underline
        )
    }
}
