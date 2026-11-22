package com.example.quan_ly_ticket.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.graphicsLayer
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
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
 * Clean solid white card with hairline border, grounded elevation, and optional press-scale feedback.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color.White,
    borderColor: Color = BorderLight,
    elevation: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "glass_card_scale"
    )

    val cardModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color(0x120F172A),
            spotColor = Color(0x120F172A)
        )
        .border(width = 1.dp, color = borderColor, shape = shape)
        .let { base ->
            if (onClick != null) {
                base.clickable(interactionSource = interactionSource, indication = null) { onClick() }
            } else {
                base
            }
        }

    Surface(
        modifier = cardModifier,
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "ticket_stub_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isPast) 1.dp else 2.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color(0x140F172A),
                spotColor = Color(0x140F172A)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, BorderLight, RoundedCornerShape(18.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
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
                        color = if (isPast) Color(0xFFF1F5F9) else SuccessBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (!isPast) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = SuccessFg,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                            Text(
                                text = if (isPast) "Đã dùng" else "Hợp lệ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPast) TextMuted else SuccessFg
                            )
                        }
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
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
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
                    .shadow(elevation = 2.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

enum class NavigationTab {
    HOME,
    TICKETS,
    SETTINGS
}

data class NavTabItem(
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0
)

/**
 * Floating frosted-acrylic capsule bottom navigation shared by Customer and Organizer portals.
 * Uses multi-layer translucent acrylic + 1dp top-lit specular border + spring pill selection.
 */
@Composable
fun FrostedCapsuleBottomNav(
    items: List<NavTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color(0x1F0F172A),
                    spotColor = Color(0x1A0F172A)
                )
                .clip(RoundedCornerShape(32.dp))
                .background(FrostedAcrylicBrush)
                .border(1.dp, SpecularBorderBrush, RoundedCornerShape(32.dp))
                .padding(6.dp)
        ) {
            val tabWidth = if (items.isNotEmpty()) maxWidth / items.size else 0.dp
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * safeIndex,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "nav_pill_slide"
            )

            // Solid sliding active pill indicator (no stroke)
            if (items.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .height(44.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(PrimaryBlue)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == safeIndex
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.94f else 1f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                        label = "nav_item_press"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1f,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                        label = "nav_icon_scale"
                    )
                    val tint by animateColorAsState(
                        targetValue = if (isSelected) Color.White else TextMuted,
                        animationSpec = tween(durationMillis = 200),
                        label = "nav_tint"
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onSelect(index) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier
                                .size(19.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = item.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.badgeCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color.White else TextDark)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.badgeCount.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) PrimaryBlue else Color.White,
                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Customer bottom navigation wrapping FrostedCapsuleBottomNav.
 */
@Composable
fun SecureBottomNav(
    currentTab: NavigationTab,
    onSelectTab: (NavigationTab) -> Unit,
    badgeCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val tabs = NavigationTab.entries
    val items = remember(badgeCount) {
        listOf(
            NavTabItem("Khám phá", Icons.Default.Home),
            NavTabItem("Ví vé", Icons.Default.ConfirmationNumber, badgeCount = badgeCount),
            NavTabItem("Cài đặt", Icons.Default.Settings)
        )
    }
    FrostedCapsuleBottomNav(
        items = items,
        selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0),
        onSelect = { idx -> onSelectTab(tabs[idx]) },
        modifier = modifier
    )
}

/**
 * Frosted circular icon button with >= 44dp touch target, specular border, and press-scale feedback.
 */
@Composable
fun FrostedIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = TextDark,
    darkMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "frosted_icon_scale"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (darkMode) 0.dp else 6.dp,
                shape = CircleShape,
                ambientColor = Color(0x140F172A),
                spotColor = Color(0x140F172A)
            )
            .clip(CircleShape)
            .let {
                if (darkMode) it.background(FrostedDarkSurface)
                else it.background(FrostedAcrylicBrush)
            }
            .border(
                width = 1.dp,
                brush = if (darkMode) DarkFrostedBorderBrush else SpecularBorderBrush,
                shape = CircleShape
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * High-contrast dark obsidian terminal block for cryptographic proofs, JWTs, and ciphertexts.
 */
@Composable
fun CryptoTerminalBox(
    title: String,
    badgeText: String? = null,
    badgeColor: Color = TerminalTextEmerald,
    lines: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TerminalBg)
            .border(1.dp, TerminalBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalHeaderBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = TerminalTextCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TerminalTextMuted,
                    letterSpacing = 0.6.sp
                )
            }
            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.16f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = badgeColor
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            lines.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (label.isNotBlank()) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = TerminalTextCyan
                        )
                    }
                    Text(
                        text = value,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TerminalTextLight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

enum class AlertType {
    SUCCESS,
    WARNING,
    ERROR,
    INFO
}

/**
 * Vector-based semantic status banner replacing all inline emoji callouts.
 */
@Composable
fun SemanticAlertBanner(
    type: AlertType,
    message: String,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    val (bg, fg, border, icon) = when (type) {
        AlertType.SUCCESS -> Quadruple(SuccessBg, SuccessFg, SuccessBorder, Icons.Default.CheckCircle)
        AlertType.WARNING -> Quadruple(WarningBg, WarningFg, WarningBorder, Icons.Default.Warning)
        AlertType.ERROR -> Quadruple(DangerBg, DangerFg, DangerBorder, Icons.Default.Error)
        AlertType.INFO -> Quadruple(InfoBg, InfoFg, InfoBorder, Icons.Default.Info)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp).padding(top = 1.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
            }
            Text(
                text = message,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = fg
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
