package com.example.quan_ly_ticket.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.ui.theme.*

/**
 * Premium Glass Card with subtle border and elevation
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    backgroundColor: Color = Color.White.copy(alpha = 0.88f),
    borderColor: Color = Color.White.copy(alpha = 0.9f),
    elevation: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color(0x1F243154),
                spotColor = Color(0x1F243154)
            )
            .border(width = 1.dp, color = borderColor, shape = shape),
        shape = shape,
        color = backgroundColor
    ) {
        Column(content = content)
    }
}

/**
 * Custom iOS/Design-system pill toggle switch
 */
@Composable
fun PremiumPillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color = PrimaryBlue,
    inactiveColor: Color = Color(0xFFC8CDD7),
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
            .width(46.dp)
            .height(26.dp)
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
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .shadow(elevation = 3.dp, shape = CircleShape)
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
 * Glass Bottom Navigation Bar matching preview
 */
@Composable
fun SecureBottomNav(
    currentTab: NavigationTab,
    onSelectTab: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ambientColor = Color(0x1F243154),
                spotColor = Color(0x1F243154)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.85f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xF8FAFCFF)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "Trang chủ",
                isSelected = currentTab == NavigationTab.HOME,
                onClick = { onSelectTab(NavigationTab.HOME) }
            )
            BottomNavItem(
                icon = Icons.Default.ConfirmationNumber,
                label = "Vé của tôi",
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
    val color = if (isSelected) PrimaryBlue else TextSubtle
    val fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold

    Column(
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = fontWeight,
            color = color
        )
    }
}
