package com.onefin.posapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.onefin.posapp.R

@Composable
fun DrawerHeader(onCloseClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 20.dp, start = 16.dp, end = 16.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_small),
            contentDescription = "Logo",
            modifier = Modifier
                .height(50.dp)
                .align(Alignment.Center)
        )

        IconButton(
            onClick = onCloseClick,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.Gray
            )
        }
    }
}