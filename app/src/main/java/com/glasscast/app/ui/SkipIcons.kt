package com.glasscast.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.glasscast.app.R

/*
 * The back and forward icons for the skip setting (Settings.skipSeconds).
 * Material has 5, 10 and 30 second versions but no 15, so the 15s pair are
 * drawables built from Material's own shapes: the 10s icon's arrow and 1,
 * with the 5s icon's 5 in place of the 0. They match the 30s pair exactly.
 */

@Composable
fun skipBackIcon(seconds: Int): ImageVector =
    if (seconds == 15) ImageVector.vectorResource(R.drawable.ic_replay_15) else Icons.Filled.Replay30

@Composable
fun skipForwardIcon(seconds: Int): ImageVector =
    if (seconds == 15) ImageVector.vectorResource(R.drawable.ic_forward_15) else Icons.Filled.Forward30
