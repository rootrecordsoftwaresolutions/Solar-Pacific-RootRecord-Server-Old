package com.rootrecord.kilauea.alerts.ui.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.rootrecord.kilauea.alerts.R

private const val LAST_STEP_INDEX = 3

@Composable
fun WelcomeTutorialOverlay(vm: WelcomeTutorialViewModel = hiltViewModel()) {
    val complete by vm.welcomeTutorialComplete.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(0) }

    if (complete) return

    val scroll = rememberScrollState()

    LaunchedEffect(step) {
        scroll.scrollTo(0)
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(
                        when (step) {
                            0 -> R.string.welcome_tutorial_step0_title
                            1 -> R.string.welcome_tutorial_step1_title
                            2 -> R.string.welcome_tutorial_step2_title
                            else -> R.string.welcome_tutorial_step3_title
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(
                        when (step) {
                            0 -> R.string.welcome_tutorial_step0_body
                            1 -> R.string.welcome_tutorial_step1_body
                            2 -> R.string.welcome_tutorial_step2_body
                            else -> R.string.welcome_tutorial_step3_body
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(scroll),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step > 0) {
                        TextButton(onClick = { step-- }) {
                            Text(stringResource(R.string.welcome_tutorial_back))
                        }
                        Spacer(Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            if (step < LAST_STEP_INDEX) step++ else vm.markWelcomeTutorialComplete()
                        },
                    ) {
                        Text(
                            stringResource(
                                if (step < LAST_STEP_INDEX) R.string.welcome_tutorial_next
                                else R.string.welcome_tutorial_begin,
                            ),
                        )
                    }
                }
                TextButton(
                    onClick = { vm.markWelcomeTutorialComplete() },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.welcome_tutorial_skip))
                }
            }
        }
    }
}
