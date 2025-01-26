package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import helium314.keyboard.latin.R

@Composable
fun SliderDialog(
    onDismissRequest: () -> Unit,
    onDone: (Float) -> Unit,
    initialValue: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    showDefault: Boolean = false,
    onDefault: () -> Unit? = { },
    onValueChanged: (Float) -> Unit = { },
    title: (@Composable () -> Unit)? = null,
    intermediateSteps: Int? = null,
    positionString: (@Composable (Float) -> String) = { it.toString() },
    shape: Shape = MaterialTheme.shapes.medium,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(backgroundColor),
    properties: DialogProperties = DialogProperties()
) {
    var sliderPosition by remember { mutableFloatStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { // mis-use the confirm button and put everything in there
            Row {
                if (showDefault)
                    TextButton(
                        onClick = { onDismissRequest(); onDefault() }
                    ) { Text(stringResource(R.string.button_default)) }
                Spacer(modifier.weight(1f))
                TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) }
                TextButton(
                    onClick = { onDismissRequest(); onDone(sliderPosition) },
                ) { Text(stringResource(android.R.string.ok)) }
            }
        },
        modifier = modifier,
        title = title,
        text = {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge
            ) {
                Column {
                    if (intermediateSteps == null)
                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            onValueChangeFinished = { onValueChanged(sliderPosition) },
                            valueRange = range,
                        )
                    else
                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            onValueChangeFinished = { onValueChanged(sliderPosition) },
                            valueRange = range,
                            steps = intermediateSteps
                        )
                    Text(positionString(sliderPosition))
                }
            }
        },
        shape = shape,
        containerColor = backgroundColor,
        textContentColor = contentColor,
        properties = properties,
    )
}

@Preview
@Composable
private fun PreviewSliderDialog() {
    SliderDialog(
        onDismissRequest = { },
        onDone = { },
        initialValue = 100f,
        range = 0f..500f,
        title = { Text("move it") },
        showDefault = true
    )
}
