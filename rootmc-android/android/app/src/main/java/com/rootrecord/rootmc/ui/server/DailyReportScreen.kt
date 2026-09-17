package com.rootrecord.rootmc.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.data.repository.DailyReportCategory
import com.rootrecord.rootmc.data.repository.DailyReportDay
import com.rootrecord.rootmc.ui.components.MinecraftCard
import com.rootrecord.rootmc.util.EntryTimestampFromApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportScreen(
    onBack: () -> Unit,
    viewModel: DailyReportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.daily_report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.server_refresh))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.daily_report_lead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (uiState.loading && uiState.page == null) {
                item { CircularProgressIndicator() }
            }

            uiState.error?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error)
                }
            }

            val reports = uiState.page?.reports.orEmpty()
            if (!uiState.loading && reports.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.daily_report_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(reports, key = { it.dayKey }) { day ->
                DailyReportDayCard(day)
            }
        }
    }
}

@Composable
private fun DailyReportDayCard(day: DailyReportDay) {
    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
        Text(day.dayKey, style = MaterialTheme.typography.titleMedium)
        day.postedAt?.let {
            EntryTimestampFromApi(raw = it, modifier = Modifier.padding(top = 4.dp))
        }
        if (day.unchangedFromPrior) {
            Text(
                stringResource(R.string.daily_report_unchanged),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val headline = day.summary.ifBlank { stringResource(R.string.daily_report_summary_default) }
        Text(headline, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        if (day.reportText.isNotBlank()) {
            Text(
                day.reportText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        day.categories.forEach { category ->
            DailyReportCategoryBlock(category)
        }
    }
}

@Composable
private fun DailyReportCategoryBlock(category: DailyReportCategory) {
    Text(
        category.title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
    if (category.summary.isNotBlank()) {
        Text(category.summary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
    if (category.reportText.isNotBlank()) {
        Text(category.reportText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}
