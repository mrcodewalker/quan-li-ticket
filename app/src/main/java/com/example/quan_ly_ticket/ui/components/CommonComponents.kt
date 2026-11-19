package com.example.quan_ly_ticket.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.model.Ticket
import com.example.quan_ly_ticket.ui.screens.formatEventTime
import com.example.quan_ly_ticket.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * In-memory LRU image cache for fast 60fps scrolling of real event cover photography.
 */
private object EventImageCache {
    private val cache = object : LruCache<String, Bitmap>(24) {}
    fun get(url: String): Bitmap? = cache.get(url)
    fun put(url: String, bmp: Bitmap) { cache.put(url, bmp) }
}

/**
 * Loads and caches real event cover photography with a shimmer skeleton while loading.
 */
@Composable
fun EventCoverImage(
    url: String,
    fallbackColor: Color = Color(0xFFE2E8F0),
    modifier: Modifier = Modifier
) {
    var bitmap by remember(url) { mutableStateOf(EventImageCache.get(url)) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    instanceFollowRedirects = true
                }
                conn.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                null
            }
        }
        if (loaded != null) {
            EventImageCache.put(url, loaded)
            bitmap = loaded
        } else {
            failed = true
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else if (failed) {
        Box(modifier = modifier.background(fallbackColor))
    } else {
        val brush = rememberShimmerBrush()
        Box(modifier = modifier.background(brush))
    }
}

/**
 * Clean solid white card with hairline border and grounded elevation.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color.White,
    borderColor: Color = BorderLight,
    elevation: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color(0x120F172A),
                spotColor = Color(0x120F172A)
            )
            .border(width = 1.dp, color = borderColor, shape = shape),
        shape = shape,
        color = backgroundColor
    ) {
        Column(content = content)
    }
}

/**
 * Smooth animated shimmer brush for loading skeletons.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer_loading")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFFECEEF2),
            Color(0xFFF8F9FB),
            Color(0xFFECEEF2)
        ),
        start = Offset(translateAnim - 240f, 0f),
        end = Offset(translateAnim, 180f)
    )
}

/**
 * Shimmer skeleton placeholder for an event row while loading.
 */
@Composable
fun EventCardSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    GlassCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(modifier = Modifier.width(68.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(brush))
                Box(modifier = Modifier.fillMaxWidth(0.88f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                Box(modifier = Modifier.fillMaxWidth(0.62f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(brush))
            }
            Box(modifier = Modifier.width(64.dp).height(34.dp).clip(RoundedCornerShape(10.dp)).background(brush))
        }
    }
}

/**
 * Shimmer skeleton placeholder for a ticket stub while loading wallet.
 */
@Composable
fun TicketCardSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    GlassCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(brush))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(modifier = Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(5.dp)).background(brush))
                    Box(modifier = Modifier.fillMaxWidth(0.55f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(brush))
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE2E8F0)))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(modifier = Modifier.width(100.dp).height(12.dp).clip(RoundedCornerShape(5.dp)).background(brush))
                Box(modifier = Modifier.width(72.dp).height(12.dp).clip(RoundedCornerShape(5.dp)).background(brush))
            }
        }
    }
}

/**
 * Solid white physical ticket card with real event photo thumbnail, perforated tear line, and crisp typography.
 */
@Composable
fun TicketStubCard(
    ticket: Ticket,
    isPast: Boolean = false,
    onClick: () -> Unit
) {
    val style = remember(ticket.eventTitle) { posterStyleForEvent(ticket.eventTitle) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isPast) 1.dp else 2.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color(0x140F172A),
                spotColor = Color(0x140F172A)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, BorderLight, RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        // Top Ticket Info with Real Event Thumbnail
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EventCoverImage(
                url = style.imageUrl,
                fallbackColor = Color(0xFFE2E8F0),
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = style.tag,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isPast) TextSubtle else style.accent,
                        letterSpacing = 0.6.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isPast) Color(0xFFF1F5F9) else Color(0xFFDCFCE7)
                    ) {
                        Text(
                            text = if (isPast) "Đã dùng" else "Hợp lệ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPast) TextMuted else Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = ticket.eventTitle ?: "Sự kiện SecureTicket",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPast) TextMuted else TextDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Schedule, null, tint = TextSubtle, modifier = Modifier.size(12.dp))
                    Text(
                        text = formatEventTime(ticket.eventStartTime) ?: "Đang cập nhật",
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Clean Perforated Tear Line with Side Cutouts
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(20f, cy),
                    end = Offset(size.width - 20f, cy),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
                drawCircle(color = AppShellBg, radius = size.height / 2f, center = Offset(0f, cy))
                drawCircle(color = AppShellBg, radius = size.height / 2f, center = Offset(size.width, cy))
            }
        }

        // Bottom Stub: Venue + Monospace Ticket Code
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = ticket.ticketCode.ifBlank { "TKT-PASS" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isPast) TextMuted else TextDark
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = TextSubtle, modifier = Modifier.size(12.dp))
                    Text(
                        text = ticket.eventLocation ?: "Việt Nam",
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isPast) "Chi tiết" else "Mở QR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPast) TextMuted else PrimaryBlue
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (isPast) TextMuted else PrimaryBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Custom crisp pill toggle switch
 */
@Composable
fun PremiumPillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color = PrimaryBlue,
    inactiveColor: Color = Color(0xFFCBD2DF),
    modifier: Modifier = Modifier
) {
    val targetBias = if (checked) 1f else -1f
    val alignmentBias by animateFloatAsState(
        targetValue = targetBias,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "switch_anim"
    )

    Box(
        modifier = modifier
            .width(44.dp)
            .height(25.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (checked) activeColor else inactiveColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            contentAlignment = BiasAlignment(alignmentBias, 0f)
        ) {
            Box(
                modifier = Modifier
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .shadow(elevation = 2.dp, shape = CircleShape)
            )
        }
    }
}

enum class NavigationTab {
    HOME,
    TICKETS,
    SETTINGS
}

/**
 * Clean docked bottom navigation bar with hairline border and pill indicator.
 */
@Composable
fun SecureBottomNav(
    currentTab: NavigationTab,
    onSelectTab: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().background(Color.White)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderLight))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "Khám phá",
                isSelected = currentTab == NavigationTab.HOME,
                onClick = { onSelectTab(NavigationTab.HOME) }
            )
            BottomNavItem(
                icon = Icons.Default.ConfirmationNumber,
                label = "Ví vé",
                isSelected = currentTab == NavigationTab.TICKETS,
                onClick = { onSelectTab(NavigationTab.TICKETS) }
            )
            BottomNavItem(
                icon = Icons.Default.Settings,
                label = "Cài đặt",
                isSelected = currentTab == NavigationTab.SETTINGS,
                onClick = { onSelectTab(NavigationTab.SETTINGS) }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (isSelected) PrimaryBlue else TextSubtle
    val pillBg = if (isSelected) Color(0xFFEFF6FF) else Color.Transparent

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(pillBg)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = tint
        )
    }
}
