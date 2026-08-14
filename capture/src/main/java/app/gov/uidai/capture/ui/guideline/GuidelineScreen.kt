package app.gov.uidai.capture.ui.guideline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.gov.uidai.capture.BuildConfig
import app.gov.uidai.capture.R
import app.gov.uidai.capture.ui.theme.Colors
import app.gov.uidai.capture.ui.theme.Spacing
import app.gov.uidai.capture.ui.theme.Typography

// ── Static content — fixed copy from the mockup. Bold spans hardcoded
// as substrings since this isn't localized yet; move to
// AnnotatedString.fromHtml + string resources if localization is added.

private enum class RightsBullet { CHECK, DOT }

private data class RightsItem(
    val bullet: RightsBullet,
    val boldRanges: List<String>,
    val text: String
)

private val PURPOSE_RIGHTS_ITEMS = listOf(
    RightsItem(
        RightsBullet.CHECK,
        listOf("UIDAI SITAA programme"),
        "Contactless fingerprint images and anonymised demographic info (age group, gender) are collected for the UIDAI SITAA programme."
    ),
    RightsItem(
        RightsBullet.CHECK,
        listOf("only for SDK evaluation"),
        "Data is used only for SDK evaluation — not linked to your Aadhaar profile."
    ),
    RightsItem(
        RightsBullet.DOT,
        listOf("You have the right to decline"),
        "You have the right to decline at any point. Participation is fully voluntary."
    ),
)

private data class GuidelineRow(val emoji: String, val boldRange: String, val text: String)

private val CAPTURE_GUIDELINE_ROWS = listOf(
    GuidelineRow("\uD83D\uDC97", "clean and dry", "Finger must be clean and dry"),
    GuidelineRow(
        "\u2600\uFE0F",
        "bright, even lighting",
        "Use bright, even lighting — no glare or shadows"
    ),
    GuidelineRow("\uD83D\uDCF7", "Camera lens clean", "Camera lens clean before starting"),
    GuidelineRow(
        "\uD83D\uDCF8",
        "Do not use a photo or video",
        "Do not use a photo or video — live capture only"
    ),
    GuidelineRow(
        "\u270F\uFE0F",
        "ink, mehndi, or tattoos",
        "Avoid fingers with ink, mehndi, or tattoos"
    ),
)

private fun buildBoldText(full: String, boldParts: List<String>): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        boldParts.forEach { part ->
            val idx = full.indexOf(part, cursor)
            if (idx >= 0) {
                append(full.substring(cursor, idx))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
                cursor = idx + part.length
            }
        }
        append(full.substring(cursor))
    }

@Composable
fun GuidelineScreen(
    txnId: String,
    onBack: () -> Unit,
    onProceed: (txnId: String) -> Unit,
    onDebugSettings: () -> Unit = {},
    showDebugButton: Boolean = BuildConfig.DEBUG
) {
    var consentChecked by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        containerColor = Colors.colourBase,
        topBar = { GuidelineTopBar(onBack = onBack) },
        bottomBar = {
            GuidelineBottomBar(
                showDebugButton = showDebugButton,
                onDebugSettings = onDebugSettings,
                proceedEnabled = consentChecked && !isLoading,
                isLoading = isLoading,
                onProceedClick = {
                    isLoading = true
                    onProceed(txnId)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.SpacingL, vertical = Spacing.SpacingS),
            verticalArrangement = Arrangement.spacedBy(Spacing.SpacingS)
        ) {
            Spacer(modifier = Modifier.height(Spacing.SpacingS))
            PurposeRightsCard()
            SectionDivider(stringResource(R.string.section_capture_guidelines))
            CaptureGuidelinesCard()
            Spacer(modifier = Modifier.height(Spacing.SpacingXS))
            ConsentRow(
                checked = consentChecked,
                onCheckedChange = { consentChecked = it }
            )
        }
    }
}

@Composable
private fun GuidelineTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.colourBase)
            .padding(
                start = Spacing.SpacingS,
                top = Spacing.SpacingS,
                bottom = Spacing.Spacing2XS,
                end = Spacing.SpacingL
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.SpacingM)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.07f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_arrow_back_24),
                contentDescription = stringResource(R.string.desc_back_button),
                tint = Colors.colourSurfaceOnBase
            )
        }
        Text(
            text = stringResource(R.string.title_new_collection),
            style = Typography.heading3,
            color = Colors.colourSurfaceOnBase,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PurposeRightsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Colors.colourContainerBase)
    ) {
        Text(
            text = stringResource(R.string.section_purpose_rights),
            style = Typography.labels3,
            color = Colors.colourAccentOnBase,
            modifier = Modifier.padding(
                start = Spacing.SpacingM,
                top = Spacing.SpacingS,
                end = Spacing.SpacingM,
                bottom = Spacing.Spacing2XS
            )
        )

        HorizontalDivider(color = Colors.colourBorderBase, thickness = 1.dp)

        PURPOSE_RIGHTS_ITEMS.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = Colors.colourBorderBase, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.SpacingM, vertical = Spacing.SpacingS),
                horizontalArrangement = Arrangement.spacedBy(Spacing.SpacingS),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = if (item.bullet == RightsBullet.CHECK) "\u2713" else "\u25CF",
                    color = if (item.bullet == RightsBullet.CHECK) Colors.colourPrimary else Colors.colourStatusWarningOnContainer,
                    style = Typography.body2,
                    modifier = Modifier.padding(top = 1.dp)
                )
                Text(
                    text = buildBoldText(item.text, item.boldRanges),
                    style = Typography.body3,
                    color = Colors.colourSurfaceOnCard
                )
            }
        }
    }
}

@Composable
private fun SectionDivider(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.SpacingS)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Colors.colourBorderBase)
        Text(text = label, style = Typography.labels3, color = Colors.colourSurfaceMuted)
        HorizontalDivider(modifier = Modifier.weight(1f), color = Colors.colourBorderBase)
    }
}

@Composable
private fun CaptureGuidelinesCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Colors.colourContainerBase)
    ) {
        CAPTURE_GUIDELINE_ROWS.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = Colors.colourBorderBase, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.SpacingM, vertical = Spacing.SpacingS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.SpacingS)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Colors.colourBase),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = row.emoji, style = Typography.body2)
                }
                Text(
                    text = buildBoldText(row.text, listOf(row.boldRange)),
                    style = Typography.body3,
                    color = Colors.colourSurfaceOnCard
                )
            }
        }
    }
}

@Composable
private fun ConsentRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (checked) Colors.colourInfoContainer else Colors.colourContainerBase)
            .then(
                if (checked) Modifier.border(
                    1.dp,
                    Colors.colourInfoContainerBorder,
                    RoundedCornerShape(10.dp)
                )
                else Modifier
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Spacing.SpacingM, vertical = Spacing.SpacingM),
        horizontalArrangement = Arrangement.spacedBy(Spacing.SpacingM),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) Colors.colourPrimary else Color.White)
                .border(
                    2.dp,
                    if (checked) Colors.colourPrimary else Colors.colourBorderBase,
                    RoundedCornerShape(5.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.consent_text_updated),
            style = Typography.body3,
            color = if (checked) Colors.colourPrimary else Colors.colourConsentText,
            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun GuidelineBottomBar(
    showDebugButton: Boolean,
    onDebugSettings: () -> Unit,
    proceedEnabled: Boolean,
    isLoading: Boolean,
    onProceedClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.colourBase)
            .padding(horizontal = Spacing.SpacingL, vertical = Spacing.SpacingS),
        verticalArrangement = Arrangement.spacedBy(Spacing.SpacingS)
    ) {

        HorizontalDivider(color = Colors.colourBorderBase, thickness = 1.dp)

        if (showDebugButton) {
            OutlinedButton(onClick = onDebugSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_debug_settings), style = Typography.body1)
            }
        }

        Button(
            onClick = onProceedClick,
            enabled = proceedEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Colors.colourPrimary,
                disabledContainerColor = Colors.colourBorderBase,
                disabledContentColor = Colors.colourSurfaceMuted
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (isLoading) stringResource(R.string.btn_loading_camera) else stringResource(
                    R.string.btn_proceed
                ),
                style = Typography.body1,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GuidelineScreenPreview() {
    GuidelineScreen(txnId = "preview-txn-id", onBack = {}, onProceed = {}, showDebugButton = true)
}