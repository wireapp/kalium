import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.wire.backup.verification.DifferentItemsTab
import com.wire.backup.verification.EqualItemsTab
import com.wire.backup.verification.MissingItemsTab
import com.wire.backup.verification.compareBackupFiles
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

fun main() = application {
    var screenState by mutableStateOf<ComparisonScreenState>(ComparisonScreenState.WaitingForFileSelection)
    var selectedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Window(onCloseRequest = ::exitApplication, title = "Wire Backup Verifier") {
        fun analyzeFiles(files: List<File>) {
            selectedFiles = files
            screenState = ComparisonScreenState.AnalyzingFiles(files.map { it.absolutePath })
            scope.launch(Dispatchers.IO) {
                val result = compareBackupFiles(files)
                screenState = ComparisonScreenState.ResultAvailable(result)
            }
        }

        fun selectFiles() {
            scope.launch(Dispatchers.IO) {
                val result = FileKit.pickFile(
                    type = PickerType.File(listOf("wbu")),
                    PickerMode.Multiple(null)
                )
                result?.let { files ->
                    if (files.isNotEmpty()) {
                        analyzeFiles(files.map { it.file })
                    }
                }
            }
        }

        Column {
            // Main content area
            when (val state = screenState) {
                ComparisonScreenState.WaitingForFileSelection -> {
                    Text(
                        "This tool compares Wire backup files (.wbu) to identify differences in messages between backups.",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(16.dp)
                    )

                    Button(onClick = { selectFiles() }) {
                        Text("Select .wbu files to compare")
                    }
                    Spacer(Modifier.size(8.dp))
                    if (selectedFiles.isEmpty()) {
                        Text("Please select two or more backup files to compare their contents.")
                    }
                    Spacer(Modifier.size(8.dp))
                }

                is ComparisonScreenState.AnalyzingFiles -> AnalyzingFilesScreen()
                is ComparisonScreenState.ResultAvailable -> ResultAvailableScreen(state, selectedFiles) {
                    selectFiles()
                }
            }
        }
    }
}

@Composable
private fun ResultAvailableScreen(state: ComparisonScreenState.ResultAvailable, selectedFiles: List<File>, onSelectFiles: () -> Unit) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Import Info", "Equal Items", "Missing Items", "Different Items")

    Column {
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
        SelectionContainer {
            when (selectedTabIndex) {
                0 -> ImportInfoTab(selectedFiles, onSelectFiles)
                1 -> EqualItemsTab(state.result)
                2 -> MissingItemsTab(state.result)
                3 -> DifferentItemsTab(state.result)
            }
        }
    }
}

@Composable
private fun ImportInfoTab(selectedFiles: List<File>, onSelectFiles: () -> Unit) {
    Column {
        Text("Selected files:", fontSize = 14.sp, color = Color.Gray)
        selectedFiles.forEach { file ->
            Text(file.absolutePath, fontSize = 14.sp)
        }
        Spacer(Modifier.size(8.dp))
        Button(onClick = {
            onSelectFiles()
        }) {
            Text("Select different files")
        }
    }
}

@Composable
private fun AnalyzingFilesScreen() {
    Column {
        Text("Analyzing files...", fontSize = 16.sp)
        Spacer(Modifier.size(8.dp))
        CircularProgressIndicator()
    }
}
