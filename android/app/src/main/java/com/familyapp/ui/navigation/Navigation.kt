package com.familyapp.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Shopping : Screen("shopping", "Compras", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart)
    object Tasks : Screen("tasks", "Tareas", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle)
    object Calendar : Screen("calendar", "Calendario", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth)
    object Finance : Screen("finance", "Finanzas", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet)
}

val bottomNavItems = listOf(
    Screen.Shopping,
    Screen.Tasks,
    Screen.Calendar,
    Screen.Finance
)

/**
 * Bespoke Floating Dock Navigation for FamilyApp.
 * Replaces the generic flat bottom bar with an artisanal, tactile pill dock.
 */
@Composable
fun FamilyFloatingBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavItems.forEach { screen ->
                    val isSelected = currentRoute == screen.route

                    DockNavItem(
                        screen = screen,
                        isSelected = isSelected,
                        onClick = {
                            if (!isSelected) {
                                onNavigate(screen.route)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DockNavItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "navIconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "navTextColor"
    )

    val pillBackground by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent,
        label = "navPillBackground"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "navScale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(pillBackground)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                contentDescription = screen.title,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )

            if (isSelected) {
                Text(
                    text = screen.title,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}
