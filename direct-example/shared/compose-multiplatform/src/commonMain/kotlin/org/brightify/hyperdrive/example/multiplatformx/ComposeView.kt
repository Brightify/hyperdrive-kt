package org.brightify.hyperdrive.example.multiplatformx

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable

@Composable
fun ComposeView(viewModel: ExampleViewModel) {
    MaterialTheme {
        Column {
            Text("Being said: ${viewModel.helloWorld}", color = MaterialTheme.colors.onBackground)

            TextField(value = viewModel.helloWorld, onValueChange = viewModel::helloWorld::set)

            Button(onClick = {
                viewModel.reset()
            }) {
                Text("Reset")
            }
        }
    }
}
