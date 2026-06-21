package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    suffix: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusChanged: (FocusState) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged(onFocusChanged),
        interactionSource = interactionSource,
        enabled = true,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        ),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = isError,
                label = label,
                suffix = suffix,
                colors = OutlinedTextFieldDefaults.colors(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = OutlinedTextFieldDefaults.colors(),
                        shape = OutlinedTextFieldDefaults.shape,
                    )
                }
            )
        }
    )
}
