package com.airpad.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airpad.app.ui.AirPadViewModel
import com.airpad.app.ui.screens.AirMouseScreen
import com.airpad.app.ui.screens.ConnectionScreen
import com.airpad.app.ui.screens.GamepadScreen
import com.airpad.app.ui.screens.SettingsScreen
import com.airpad.app.ui.theme.AccentTeal
import com.airpad.app.ui.theme.AirPadTheme
import com.airpad.app.ui.theme.BgDark
import com.airpad.app.ui.theme.BorderSubtle
import com.airpad.app.ui.theme.PrimaryBlue
import com.airpad.app.ui.theme.SurfaceDark
import com.airpad.app.ui.theme.TextMuted
import com.airpad.app.ui.theme.TextPrimary
import com.airpad.app.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private val viewModel: AirPadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AirPadTheme {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}

enum class NavigationTab(val title: String, val iconText: String) {
    CONNECT("Connect", "🔗"),
    AIR_MOUSE("Mouse", "🖱"),
    GAMEPAD("Gamepad", "🎮"),
    SETTINGS("Settings", "⚙")
}

@Composable
fun MainAppContainer(viewModel: AirPadViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = NavigationTab.values()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .border(1.dp, BorderSubtle)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Text(
                                text = tab.iconText,
                                fontSize = 18.sp
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) AccentTeal else TextMuted
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentTeal,
                            unselectedIconColor = TextMuted,
                            indicatorColor = PrimaryBlue.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (tabs[selectedTab]) {
                NavigationTab.CONNECT -> ConnectionScreen(viewModel = viewModel)
                NavigationTab.AIR_MOUSE -> AirMouseScreen(viewModel = viewModel)
                NavigationTab.GAMEPAD -> GamepadScreen(viewModel = viewModel)
                NavigationTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
