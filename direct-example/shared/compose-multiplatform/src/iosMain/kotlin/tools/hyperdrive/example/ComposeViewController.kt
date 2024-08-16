package tools.hyperdrive.example

import androidx.compose.ui.window.ComposeUIViewController
import org.brightify.hyperdrive.example.multiplatformx.ComposeView
import org.brightify.hyperdrive.example.multiplatformx.ExampleViewModel

fun ComposeViewController(viewModel: ExampleViewModel) = ComposeUIViewController {
    ComposeView(viewModel)
}
