package com.ahu.ahutong.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.R
import com.ahu.ahutong.data.model.License as LicenseItem
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.LicenseViewModel
import com.kyant.monet.n1
import com.kyant.monet.withNight

@Composable
fun License(
    licenseViewModel: LicenseViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedLicense by remember { mutableStateOf<LicenseItem?>(null) }

    fun openSource(license: LicenseItem) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(license.url)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(id = R.string.license),
            modifier = Modifier.padding(24.dp, 32.dp),
            style = MaterialTheme.typography.headlineLarge
        )
        Column(
            modifier = Modifier.clip(SmoothRoundedCornerShape(32.dp)),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            licenseViewModel.license.forEach {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SmoothRoundedCornerShape(4.dp))
                        .background(100.n1 withNight 20.n1)
                        .clickable {
                            if (it.licenseAsset != null || it.noticeAsset != null) {
                                selectedLicense = it
                            } else {
                                openSource(it)
                            }
                        }
                        .padding(24.dp, 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = it.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = it.author,
                        color = 30.n1 withNight 90.n1,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = it.url,
                        color = 50.n1 withNight 80.n1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = it.license,
                        color = 50.n1 withNight 80.n1,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    selectedLicense?.let { license ->
        val licenseText = remember(license) {
            listOfNotNull(license.noticeAsset, license.licenseAsset)
                .map { assetPath ->
                    runCatching {
                        context.assets.open(assetPath).bufferedReader().use { it.readText() }
                    }.getOrElse {
                        context.getString(R.string.license_load_failed, assetPath)
                    }
                }
                .joinToString("\n\n")
        }

        AlertDialog(
            onDismissRequest = { selectedLicense = null },
            title = {
                Text(text = license.name)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = license.author,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = license.license,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    SelectionContainer {
                        Text(
                            text = licenseText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { openSource(license) }) {
                    Text(text = stringResource(R.string.view_source))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedLicense = null }) {
                    Text(text = stringResource(R.string.close))
                }
            }
        )
    }
}
