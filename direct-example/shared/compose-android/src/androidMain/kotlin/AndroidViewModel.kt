import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AndroidViewModel {

    private val _helloWorld: MutableState<String> = mutableStateOf("")
    var helloWorld: String by _helloWorld


}
