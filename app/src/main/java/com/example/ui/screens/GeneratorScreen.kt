package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.QrPayload
import com.example.model.QrType
import com.example.ui.QrViewModel
import com.example.ui.components.QrDisplayCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    viewModel: QrViewModel,
    onNavigateToCustomize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedType by viewModel.selectedQrType.collectAsStateWithLifecycle()
    val payload by viewModel.qrPayload.collectAsStateWithLifecycle()
    val generatedBitmap by viewModel.generatedBitmap.collectAsStateWithLifecycle()
    val readabilityResult by viewModel.readabilityResult.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("generator_screen"),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. Category Selector Chips
        item {
            Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                Text(
                    text = "Select QR Type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )

                val types = QrType.values()
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(types) { type ->
                        val isSelected = type == selectedType
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectQrType(type) },
                            label = { Text(type.displayName) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (type) {
                                        QrType.URL -> Icons.Default.Link
                                        QrType.TEXT -> Icons.Default.Notes
                                        QrType.PHONE -> Icons.Default.Phone
                                        QrType.EMAIL -> Icons.Default.Email
                                        QrType.SMS -> Icons.Default.Sms
                                        QrType.WIFI -> Icons.Default.Wifi
                                        QrType.CONTACT -> Icons.Default.Person
                                        QrType.LOCATION -> Icons.Default.LocationOn
                                        QrType.JSON_DATA -> Icons.Default.Code
                                        QrType.CUSTOM -> Icons.Default.DataObject
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("type_chip_${type.name}")
                        )
                    }
                }
            }
        }

        // 2. Input Form Based On Type
        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // QR Label / Custom Title
                    OutlinedTextField(
                        value = payload.title,
                        onValueChange = { viewModel.updatePayload(payload.copy(title = it)) },
                        label = { Text("QR Code Label / Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_qr_title")
                    )

                    // Specific input fields
                    when (selectedType) {
                        QrType.URL -> UrlInputForm(payload = payload, onUpdate = viewModel::updatePayload)
                        QrType.TEXT -> TextInputForm(payload = payload, onUpdate = viewModel::updatePayload)
                        QrType.PHONE -> PhoneInputForm(payload = payload, onUpdate = viewModel::updatePayload)
                        QrType.EMAIL -> EmailInputForm(payload = payload, onUpdate = viewModel::updatePayload)
                        QrType.SMS -> SmsInputForm(payload = payload, onUpdate = viewModel::updatePayload)
                        QrType.WIFI -> WifiInputForm(payload = payload, onUpdate = viewModel::updatePayload)
                        QrType.CONTACT -> ContactInputForm(payload = payload, onUpdate = viewModel::updatePayload)
                        QrType.LOCATION -> LocationInputForm(payload = payload, onUpdate = viewModel::updatePayload)
                        QrType.JSON_DATA -> JsonInputForm(payload = payload, onUpdate = viewModel::updatePayload)
                        QrType.CUSTOM -> CustomInputForm(payload = payload, onUpdate = viewModel::updatePayload)
                    }
                }
            }
        }

        // 3. Customize & Style Callout Button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Button(
                    onClick = onNavigateToCustomize,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("customize_style_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Customize Colors, Pattern & Logo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Live QR Preview & Save / Share Actions
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Live Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                QrDisplayCard(
                    bitmap = generatedBitmap,
                    readabilityResult = readabilityResult,
                    title = payload.title.ifEmpty { selectedType.displayName },
                    summary = payload.summary,
                    onCopyContent = { viewModel.copyCurrentContent(context) },
                    onShare = { viewModel.shareQrCode(context) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.saveQrToGallery(context) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_qr_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save PNG")
                    }

                    OutlinedButton(
                        onClick = { viewModel.saveGeneratedQrToHistory() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_to_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save History")
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlInputForm(
    payload: QrPayload,
    onUpdate: (QrPayload) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = payload.url,
            onValueChange = {
                onUpdate(payload.copy(url = it, rawContent = it, summary = it))
            },
            label = { Text("Website Address / URL") },
            placeholder = { Text("https://example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_url")
        )
    }
}

@Composable
private fun TextInputForm(
    payload: QrPayload,
    onUpdate: (QrPayload) -> Unit
) {
    OutlinedTextField(
        value = payload.text,
        onValueChange = {
            onUpdate(payload.copy(text = it, rawContent = it, summary = it))
        },
        label = { Text("Plain Text Content") },
        placeholder = { Text("Enter any note or message...") },
        minLines = 3,
        maxLines = 6,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("input_text")
    )
}

@Composable
private fun PhoneInputForm(
    payload: QrPayload,
    onUpdate: (QrPayload) -> Unit
) {
    OutlinedTextField(
        value = payload.phoneNumber,
        onValueChange = {
            onUpdate(payload.copy(phoneNumber = it, rawContent = "tel:$it", summary = it))
        },
        label = { Text("Phone Number") },
        placeholder = { Text("+1 (555) 000-1234") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("input_phone")
    )
}

@Composable
private fun EmailInputForm(
    payload: QrPayload,
    onUpdate: (QrPayload) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = payload.emailAddress,
            onValueChange = { onUpdate(payload.copy(emailAddress = it, summary = it)) },
            label = { Text("Email Recipient") },
            placeholder = { Text("alex@example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_email_address")
        )
        OutlinedTextField(
            value = payload.emailSubject,
            onValueChange = { onUpdate(payload.copy(emailSubject = it)) },
            label = { Text("Subject (Optional)") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_email_subject")
        )
        OutlinedTextField(
            value = payload.emailBody,
            onValueChange = { onUpdate(payload.copy(emailBody = it)) },
            label = { Text("Message Body (Optional)") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_email_body")
        )
    }
}

@Composable
private fun SmsInputForm(
    payload: QrPayload,
    onUpdate: (QrPayload) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = payload.smsPhone,
            onValueChange = { onUpdate(payload.copy(smsPhone = it, summary = "To: $it")) },
            label = { Text("Recipient Phone Number") },
            placeholder = { Text("+1 (555) 000-1234") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_sms_phone")
        )
        OutlinedTextField(
            value = payload.smsMessage,
            onValueChange = { onUpdate(payload.copy(smsMessage = it)) },
            label = { Text("Pre-filled Message") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_sms_message")
        )
    }
}

@Composable
private fun WifiInputForm(
    payload: QrPayload,
    onUpdate: (QrPayload) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = payload.wifiSsid,
            onValueChange = { onUpdate(payload.copy(wifiSsid = it, summary = "SSID: $it (${payload.wifiType})")) },
            label = { Text("Network Name (SSID)") },
            placeholder = { Text("Home_WiFi") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_wifi_ssid")
        )

        if (payload.wifiType != "nopass") {
            OutlinedTextField(
                value = payload.wifiPassword,
                onValueChange = { onUpdate(payload.copy(wifiPassword = it)) },
                label = { Text("Wi-Fi Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_wifi_password")
            )
        }

        // Security Type Selector
        Text(
            text = "Security Type",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )

        val securityOptions = listOf("WPA", "WEP", "nopass")
        val securityLabels = listOf("WPA/WPA2/3", "WEP", "Open (None)")

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            securityOptions.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = payload.wifiType == option,
                    onClick = { onUpdate(payload.copy(wifiType = option)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = securityOptions.size)
                ) {
                    Text(securityLabels[index], style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text("Hidden Network", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = payload.wifiHidden,
                onCheckedChange = { onUpdate(payload.copy(wifiHidden = it)) }
            )
        }
    }
}

@Composable
private fun ContactInputForm(
    payload: QrPayload,
    onUpdate: (QrPayload) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = payload.contactName,
            onValueChange = { onUpdate(payload.copy(contactName = it, summary = it)) },
            label = { Text("Full Name") },
            placeholder = { Text("Alex Morgan") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_contact_name")
        )
        OutlinedTextField(
            value = payload.contactPhone,
            onValueChange = { onUpdate(payload.copy(contactPhone = it)) },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_contact_phone")
        )
        OutlinedTextField(
            value = payload.contactEmail,
            onValueChange = { onUpdate(payload.copy(contactEmail = it)) },
            label = { Text("Email Address") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_contact_email")
        )
        OutlinedTextField(
            value = payload.contactOrg,
            onValueChange = { onUpdate(payload.copy(contactOrg = it)) },
            label = { Text("Company / Organization (Optional)") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_contact_org")
        )
        OutlinedTextField(
            value = payload.contactUrl,
            onValueChange = { onUpdate(payload.copy(contactUrl = it)) },
            label = { Text("Website URL (Optional)") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_contact_url")
        )
    }
}

@Composable
private fun LocationInputForm(
    payload: QrPayload,
    onUpdate: (QrPayload) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = payload.locationQuery,
            onValueChange = { onUpdate(payload.copy(locationQuery = it, summary = it)) },
            label = { Text("Location Address or Landmark") },
            placeholder = { Text("Golden Gate Bridge, San Francisco") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_location_query")
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = payload.locationLat,
                onValueChange = { onUpdate(payload.copy(locationLat = it)) },
                label = { Text("Latitude") },
                placeholder = { Text("37.7749") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = payload.locationLng,
                onValueChange = { onUpdate(payload.copy(locationLng = it)) },
                label = { Text("Longitude") },
                placeholder = { Text("-122.4194") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun JsonInputForm(
    payload: QrPayload,
    onUpdate: (QrPayload) -> Unit
) {
    OutlinedTextField(
        value = payload.jsonData,
        onValueChange = { onUpdate(payload.copy(jsonData = it, rawContent = it, summary = "JSON (${it.length} chars)")) },
        label = { Text("JSON Document") },
        placeholder = { Text("{\n  \"key\": \"value\"\n}") },
        minLines = 4,
        maxLines = 8,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("input_json")
    )
}

@Composable
private fun CustomInputForm(
    payload: QrPayload,
    onUpdate: (QrPayload) -> Unit
) {
    OutlinedTextField(
        value = payload.customData,
        onValueChange = { onUpdate(payload.copy(customData = it, rawContent = it, summary = it.take(50))) },
        label = { Text("Custom Payload / Raw Data") },
        placeholder = { Text("Enter raw formatted text or custom schema data...") },
        minLines = 3,
        maxLines = 6,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("input_custom_data")
    )
}
