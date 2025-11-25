package org.torproject.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun SettingsScreen(
    categories: List<SettingsCategory>,
    navController: NavController,
    onSettingChanged: (Setting) -> Unit
) {
    var toolbarTitle by remember { mutableStateOf("Settings") }
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    BackHandler(enabled = true) {
        if (selectedCategory != null) {
            toolbarTitle = "Settings"
            selectedCategory = null
        } else {
            navController.popBackStack()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(toolbarTitle) },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (selectedCategory != null) {
                            toolbarTitle = "Settings"
                            selectedCategory = null
                        } else {
                            navController.popBackStack()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        AnimatedContent(
            targetState = selectedCategory,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally(animationSpec = tween(300)) { it } +
                            fadeIn(animationSpec = tween(300)) with
                            slideOutHorizontally(animationSpec = tween(300)) { -it } +
                            fadeOut(animationSpec = tween(300))
                } else {
                    slideInHorizontally(animationSpec = tween(300)) { -it } +
                            fadeIn(animationSpec = tween(300)) with
                            slideOutHorizontally(animationSpec = tween(300)) { it } +
                            fadeOut(animationSpec = tween(300))
                }
            }
        ) { targetCategory ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (targetCategory == null) {
                    items(categories) { category ->
                        Text(
                            text = category.title,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .clickable {
                                    selectedCategory = category
                                    toolbarTitle = category.title
                                }
                        )
                    }
                } else {
                    targetCategory.settings.forEach { setting ->
                        item {
                            SettingItem(setting = setting, onSettingChanged = onSettingChanged)
                        }
                    }
                }
            }
        }
    }
}
