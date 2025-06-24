/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wire.backup.verification.DifferentItemsTab
import com.wire.backup.verification.EqualItemsTab
import com.wire.backup.verification.MissingItemsTab

@Composable
fun ComparisonResultScreen(
    comparisonState: ComparisonScreenState?,
    onBackClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = "Back to file selection"
                )
            }

            Text(
                "Backup Comparison Results",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.size(16.dp))

        when (comparisonState) {
            is ComparisonScreenState.AnalyzingFiles -> {
                AnalyzingFilesScreen()
            }

            is ComparisonScreenState.ResultAvailable -> {
                // Comparison tabs
                val tabs = listOf("Equal Items", "Missing Items", "Different Items")
                var selectedTabIndex by remember { mutableStateOf(0) }

                // Display tabs
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index }
                        ) {
                            Text(title, modifier = Modifier.padding(16.dp))
                        }
                    }
                }

                // Display tab content
                SelectionContainer {
                    when (selectedTabIndex) {
                        0 -> EqualItemsTab(comparisonState.result)
                        1 -> MissingItemsTab(comparisonState.result)
                        2 -> DifferentItemsTab(comparisonState.result)
                    }
                }
            }

            else -> {
                // This shouldn't happen, but handle it gracefully
                Text("No comparison data available")

                Spacer(Modifier.size(16.dp))

                Button(onClick = onBackClick) {
                    Text("Back to file selection")
                }
            }
        }
    }
}

@Composable
private fun AnalyzingFilesScreen() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Comparing backup files...", fontSize = 18.sp)
            Spacer(Modifier.size(16.dp))
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        }
    }
}
