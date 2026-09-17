package com.rootrecord.rootmc.ui.locations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.ui.chambers.ChambersScreen
import com.rootrecord.rootmc.ui.coords.CoordsScreen

@Composable
fun LocationsHubScreen(
    openAddWaypointOnLaunch: Boolean = false,
    onOpenWaypoint: (Long) -> Unit,
    onOpenArea: (Long) -> Unit,
    onOpenChamber: (Long) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.nav_waypoints)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.nav_areas)) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.nav_chambers)) },
                )
            }
            when (selectedTab) {
                0 -> CoordsScreen(
                    openAddDialogOnLaunch = openAddWaypointOnLaunch,
                    onOpenWaypoint = onOpenWaypoint,
                )
                1 -> AreasScreen(
                    onOpenArea = onOpenArea,
                )
                2 -> ChambersScreen(
                    onOpenChamber = onOpenChamber,
                )
            }
        }
    }
}
