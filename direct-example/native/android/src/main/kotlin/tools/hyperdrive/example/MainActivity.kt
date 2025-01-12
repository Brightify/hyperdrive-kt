package tools.hyperdrive.example

import AndroidViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel = ExampleViewModel()
        setContent {
            MaterialTheme {
                Column {
                    Text("Being said: ${viewModel.helloWorld}", color = MaterialTheme.colorScheme.onBackground)

                    TextField(value = viewModel.helloWorld, onValueChange = viewModel::helloWorld::set)

                    Button(onClick = {
                        viewModel.reset()
                    }) {
                        Text("Reset")
                    }
                }
            }
        }
    }
}
