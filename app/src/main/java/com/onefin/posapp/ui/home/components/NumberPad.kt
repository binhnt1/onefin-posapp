package com.onefin.posapp.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onefin.posapp.R

@Composable
fun NumberPad(
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clearText = stringResource(R.string.numpad_clear)
    val backspaceText = stringResource(R.string.numpad_backspace)

    val numbers = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("0", "000", backspaceText)
    )

    val isP2 = remember {
        android.os.Build.MODEL.lowercase().contains("p2")
    }
    val spacing = if (isP2) 6.dp else 12.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        numbers.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                row.forEach { key ->
                    NumberButton(
                        text = key,
                        onClick = {
                            when (key) {
                                backspaceText -> onBackspaceClick()
                                else -> onNumberClick(key)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}