package com.example.metacheck.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.metacheck.R

/** Версия приложения и ссылки на документы. Политика конфиденциальности должна быть доступна и внутри приложения. */
@Composable
fun AboutPanel() {
    val context = LocalContext.current
    var licensesOpen by rememberSaveable { mutableStateOf(false) }
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }
    val privacyUrl = stringResource(R.string.legal_privacy_url)
    val termsUrl = stringResource(R.string.legal_terms_url)

    fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.about_link_failed), Toast.LENGTH_SHORT).show()
        }
    }

    GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.secondary) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CyberEyebrow(stringResource(R.string.about_eyebrow), color = MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
            if (versionName.isNotBlank()) {
                Text(
                    stringResource(R.string.about_version, versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { openLink(privacyUrl) }) { Text(stringResource(R.string.about_privacy)) }
            TextButton(onClick = { openLink(termsUrl) }) { Text(stringResource(R.string.about_terms)) }
            TextButton(onClick = { openLink(LegalLinks.SUPPORT) }) { Text(stringResource(R.string.about_support)) }
            TextButton(onClick = { licensesOpen = true }) { Text(stringResource(R.string.about_licenses)) }
        }
    }

    if (licensesOpen) AlertDialog(
        onDismissRequest = { licensesOpen = false },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(26.dp),
        title = { Text(stringResource(R.string.about_licenses)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    LicenseNotices.text(
                        intro = stringResource(R.string.licenses_intro),
                        mapData = stringResource(R.string.licenses_map_data),
                        mapTiles = stringResource(R.string.licenses_map_tiles),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { licensesOpen = false }) { Text(stringResource(R.string.common_close)) } },
    )
}
