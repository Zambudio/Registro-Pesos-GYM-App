package com.zambudio.registropesos.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zambudio.registropesos.BuildConfig
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.CardSpacing
import com.zambudio.registropesos.core.ui.GymCard
import com.zambudio.registropesos.core.ui.HeroCard
import com.zambudio.registropesos.core.ui.IconBadge
import com.zambudio.registropesos.core.ui.OnHero
import com.zambudio.registropesos.core.ui.ScreenPadding

private const val HeroBadgeAlpha = 0.2f
private const val HeroSecondaryAlpha = 0.85f

/** Pantalla estática: no necesita ViewModel. */
@Composable
fun AboutRoute(onBack: () -> Unit) {
    AboutScreen(versionName = BuildConfig.VERSION_NAME, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(versionName: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(CardSpacing),
        ) {
            HeroCard(modifier = Modifier.fillMaxWidth()) {
                IconBadge(
                    Icons.Filled.FitnessCenter,
                    size = 64.dp,
                    containerColor = OnHero.copy(alpha = HeroBadgeAlpha),
                    contentColor = OnHero,
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, color = OnHero)
                Text(
                    stringResource(R.string.settings_about_version, versionName),
                    style = MaterialTheme.typography.labelLarge,
                    color = OnHero.copy(alpha = HeroSecondaryAlpha),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnHero,
                )
            }
            GymCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Lock)
                    Text(
                        stringResource(R.string.settings_about_privacy_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                Text(
                    stringResource(R.string.settings_about_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    GymTheme { AboutScreen(versionName = "0.1.0", onBack = {}) }
}
