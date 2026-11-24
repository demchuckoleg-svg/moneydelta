package com.moneydelta.app.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.moneydelta.app.R
import com.moneydelta.app.data.WidgetSummarySnapshot
import com.moneydelta.app.di.AppContainer
import com.moneydelta.app.domain.MoneyFormatter

class MoneyDeltaWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_SIZE, MEDIUM_SIZE, WIDE_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = AppContainer.get(context).ledgerRepository.getWidgetSnapshot()
        provideContent {
            GlanceTheme {
                WidgetContent(snapshot = snapshot)
            }
        }
    }

    companion object {
        private val COMPACT_SIZE = DpSize(110.dp, 90.dp)
        private val MEDIUM_SIZE = DpSize(180.dp, 110.dp)
        private val WIDE_SIZE = DpSize(250.dp, 110.dp)

        private val positiveColor = ColorProvider(
            day = Color(0xFF1B6E3A),
            night = Color(0xFF7AD896)
        )
        private val negativeColor = ColorProvider(
            day = Color(0xFFB3261E),
            night = Color(0xFFF28B82)
        )
    }

    @Composable
    private fun WidgetContent(snapshot: WidgetSummarySnapshot) {
        val context = LocalContext.current
        val size = LocalSize.current
        val isCompact = size.width < 170.dp

        val openAppAction = actionStartActivity(
            ComponentName(context.packageName, "com.moneydelta.app.MainActivity")
        )

        val deltaColor = when {
            snapshot.netDeltaCents > 0L -> positiveColor
            snapshot.netDeltaCents < 0L -> negativeColor
            else -> GlanceTheme.colors.onSurface
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(20.dp)
                .clickable(openAppAction)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = snapshot.periodLabel,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                Box(
                    modifier = GlanceModifier
                        .size(28.dp)
                        .cornerRadius(14.dp)
                        .background(GlanceTheme.colors.secondaryContainer)
                        .clickable(actionRunCallback<RefreshWidgetAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = context.getString(R.string.widget_refresh),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                        modifier = GlanceModifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            Text(
                text = MoneyFormatter.formatSigned(snapshot.netDeltaCents, snapshot.currencySymbol),
                style = TextStyle(
                    color = deltaColor,
                    fontSize = if (isCompact) 20.sp else 26.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (isCompact) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = "In +${MoneyFormatter.formatUnsigned(snapshot.incomeCents, snapshot.currencySymbol)}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = "Out -${MoneyFormatter.formatUnsigned(snapshot.expenseCents, snapshot.currencySymbol)}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "In +${MoneyFormatter.formatUnsigned(snapshot.incomeCents, snapshot.currencySymbol)}",
                        style = TextStyle(
                            color = positiveColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    Text(
                        text = "Out -${MoneyFormatter.formatUnsigned(snapshot.expenseCents, snapshot.currencySymbol)}",
                        style = TextStyle(
                            color = negativeColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        AppContainer.get(context).smsInboxScanner.scanLast12Months()
        MoneyDeltaWidget().updateAll(context)
    }
}
