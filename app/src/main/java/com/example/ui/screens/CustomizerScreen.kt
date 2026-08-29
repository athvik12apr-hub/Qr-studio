package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.QrCustomization
import com.example.model.QrErrorCorrection
import com.example.model.QrEyeStyle
import com.example.model.QrGradientMode
import com.example.model.QrLogo
import com.example.model.QrPattern
import com.example.model.ReadabilityStatus
import com.example.ui.QrViewModel
import com.example.ui.components.ColorPaletteRow
import com.example.ui.components.QrDisplayCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizerScreen(
    viewModel: QrViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val custom by viewModel.customization.collectAsStateWithLifecycle()
    val payload by viewModel.qrPayload.collectAsStateWithLifecycle()
    val bitmap by viewModel.generatedBitmap.collectAsStateWithLifecycle()
    val readability by viewModel.readabilityResult.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Style & Color", "Shapes & Eyes", "Logo & Safety")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("customizer_screen"),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // 1. Live Interactive QR Preview
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                QrDisplayCard(
                    bitmap = bitmap,
                    readabilityResult = readability,
                    title = payload.title,
                    summary = payload.summary,
                    onCopyContent = { viewModel.copyCurrentContent(context) },
                    onShare = { viewModel.shareQrCode(context) }
                )
            }
        }

        // 2. Scannability / Contrast Warning & Guidance Box
        item {
            if (readability.warnings.isNotEmpty() || readability.suggestions.isNotEmpty()) {
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = when (readability.status) {
                            ReadabilityStatus.CRITICAL -> Color(0xFFFFF0F0)
                            ReadabilityStatus.WARNING -> Color(0xFFFFFBEB)
                            else -> Color(0xFFF0FDF4)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (readability.status == ReadabilityStatus.CRITICAL) Icons.Default.Warning else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (readability.status == ReadabilityStatus.CRITICAL) Color(0xFFDC2626) else Color(0xFFD97706),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (readability.status == ReadabilityStatus.CRITICAL) "Readability Risk" else "Contrast Guidance",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (readability.status == ReadabilityStatus.CRITICAL) Color(0xFF991B1B) else Color(0xFF92400E)
                            )
                        }

                        readability.warnings.forEach { warning ->
                            Text(
                                text = "• $warning",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF334155),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        readability.suggestions.forEach { suggestion ->
                            Text(
                                text = "💡 Tip: $suggestion",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Quick Design Presets
        item {
            Column(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                val presets = listOf("Classic Black", "Electric Indigo", "Emerald Minimal", "Cyber Sunset", "Dark Tech")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presets) { preset ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.applyPreset(preset) },
                            label = { Text(preset) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        // 4. Customization Sections Tab Header
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }

        // 5. Active Tab Contents
        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> ColorsTab(custom = custom, onUpdate = viewModel::updateCustomization)
                        1 -> ShapesTab(custom = custom, onUpdate = viewModel::updateCustomization)
                        2 -> LogoAndSafetyTab(custom = custom, onUpdate = viewModel::updateCustomization)
                    }
                }
            }
        }

        // 6. Final Save & Export Actions
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = { viewModel.saveQrToGallery(context) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("customizer_save_button")
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save PNG", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { viewModel.shareQrCode(context) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("customizer_share_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ColorsTab(
    custom: QrCustomization,
    onUpdate: ((QrCustomization) -> QrCustomization) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Foreground Color
        ColorPaletteRow(
            title = "Pattern / Foreground Color",
            selectedColor = custom.foregroundColor,
            onColorSelected = { color ->
                onUpdate { it.copy(foregroundColor = color) }
            }
        )

        // Background Color
        ColorPaletteRow(
            title = "Background Color",
            selectedColor = custom.backgroundColor,
            onColorSelected = { color ->
                onUpdate { it.copy(backgroundColor = color) }
            }
        )

        // Gradient Options
        Text(
            text = "Gradient Style",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val gradientModes = QrGradientMode.values()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(gradientModes) { mode ->
                FilterChip(
                    selected = custom.gradientMode == mode,
                    onClick = { onUpdate { it.copy(gradientMode = mode) } },
                    label = { Text(mode.displayName) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        if (custom.gradientMode != QrGradientMode.NONE) {
            ColorPaletteRow(
                title = "Gradient Secondary Color",
                selectedColor = custom.gradientColor2,
                onColorSelected = { color ->
                    onUpdate { it.copy(gradientColor2 = color) }
                }
            )
        }
    }
}

@Composable
private fun ShapesTab(
    custom: QrCustomization,
    onUpdate: ((QrCustomization) -> QrCustomization) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Pattern Module Shapes
        Text(
            text = "Pattern Shape",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val patterns = QrPattern.values()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(patterns) { pattern ->
                FilterChip(
                    selected = custom.pattern == pattern,
                    onClick = { onUpdate { it.copy(pattern = pattern) } },
                    label = { Text(pattern.displayName) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // Corner Eye Style
        Text(
            text = "Corner / Finder Eyes",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val eyes = QrEyeStyle.values()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(eyes) { eye ->
                FilterChip(
                    selected = custom.eyeStyle == eye,
                    onClick = { onUpdate { it.copy(eyeStyle = eye) } },
                    label = { Text(eye.displayName) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // Quiet-Zone / Margin
        Text(
            text = "Margin / Quiet Zone (${custom.quietZone} modules)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val margins = listOf(0, 1, 2, 4)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            margins.forEachIndexed { index, marginVal ->
                SegmentedButton(
                    selected = custom.quietZone == marginVal,
                    onClick = { onUpdate { it.copy(quietZone = marginVal) } },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = margins.size)
                ) {
                    Text(if (marginVal == 0) "None (0)" else "$marginVal Modules")
                }
            }
        }
    }
}

@Composable
private fun LogoAndSafetyTab(
    custom: QrCustomization,
    onUpdate: ((QrCustomization) -> QrCustomization) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Error Correction Level
        Text(
            text = "Error Correction Level",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val ecLevels = QrErrorCorrection.values()
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ecLevels.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = custom.errorCorrection == level,
                    onClick = { onUpdate { it.copy(errorCorrection = level) } },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ecLevels.size)
                ) {
                    Text(level.name)
                }
            }
        }

        Text(
            text = "Level ${custom.errorCorrection.name} can recover up to ${custom.errorCorrection.recoveryPercent} damaged or obscured area.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Center Logo / Badge
        Text(
            text = "Center Logo / Icon",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val logos = QrLogo.values()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(logos) { logo ->
                FilterChip(
                    selected = custom.logo == logo,
                    onClick = {
                        // Automatically suggest High Error Correction if logo is enabled
                        onUpdate {
                            it.copy(
                                logo = logo,
                                errorCorrection = if (logo != QrLogo.NONE && it.errorCorrection == QrErrorCorrection.L) QrErrorCorrection.H else it.errorCorrection
                            )
                        }
                    },
                    label = { Text(logo.displayName) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        if (custom.logo == QrLogo.CUSTOM_TEXT) {
            OutlinedTextField(
                value = custom.logoCustomText,
                onValueChange = { onUpdate { cur -> cur.copy(logoCustomText = it.take(3)) } },
                label = { Text("Center Monogram / Initial (Max 3 chars)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
