import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/** 可行性验证用的最小窗口：只为确认 Compose Desktop 能在 aarch64 Linux 上编出并跑起来。 */
@Composable
fun Hello() {
    MaterialTheme {
        Column(Modifier.padding(24.dp)) {
            Text("JMComic_Next 桌面版", style = MaterialTheme.typography.headlineSmall)
            Text("构建链验证窗口")
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "JMComic_Next") { Hello() }
}
