package com.yike.ai.navigation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.yike.ai.core.designsystem.Confirm
import com.yike.ai.core.designsystem.LocalCardAnimations
import com.yike.ai.core.designsystem.LocalDefaultTemplate
import com.yike.ai.feature.*
import com.yike.ai.feature.card.DeckPreviewScreen
import com.yike.ai.feature.card.ManualCardScreen
import com.yike.ai.feature.deck.*
import com.yike.ai.feature.generation.*
import com.yike.ai.feature.home.*
import com.yike.ai.feature.settings.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YikeApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val snack = remember { SnackbarHostState() }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var exportKind by remember { mutableStateOf("JSON") }
    var exportDeck by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            uri?.let { vm.export(it, exportKind, exportDeck) }
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            restoreUri = uri
        }
    fun exportFile(kind: String, deck: String?) {
        exportKind = kind
        exportDeck = deck
        export.launch(
            "寄丢制卡-${System.currentTimeMillis()}.${if(kind=="APKG")"apkg" else if(kind=="CSV")"csv" else "json"}"
        )
    }
    LaunchedEffect(vm) { vm.message.collect { snack.showSnackbar(it) } }
    val tabs =
        listOf(
            Triple("home", "首页", Icons.Outlined.Home),
            Triple("decks", "牌组", Icons.Outlined.Folder),
            Triple("generation", "AI 制卡", Icons.Outlined.AutoAwesome),
            Triple("settings", "设置", Icons.Outlined.Settings),
        )
    val top = tabs.any { it.first == route }
    fun navigateTab(target: String) {
        if (nav.currentDestination?.route == target) return
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            // Home is the retained start destination. Restoring its saved stack can
            // restore the generation page that was popped from above it instead.
            restoreState = target != "home"
        }
    }
    CompositionLocalProvider(
        LocalDefaultTemplate provides prefs.defaultTemplate,
        LocalCardAnimations provides prefs.animations,
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (top) tabs.first { it.first == route }.second
                            else
                                when (route) {
                                    "deck/{id}" -> "牌组详情"
                                    "manual?deckId={deckId}" -> "手动制卡"
                                    "preview/{id}" -> "卡片预览"
                                    "progress/{id}" -> "生成进度"
                                    "audit/{id}" -> "卡片审核"
                                    "providers" -> "服务商与模型"
                                    else -> "寄丢制卡"
                                }
                        )
                    },
                    navigationIcon = {
                        if (!top)
                            IconButton({ nav.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                            }
                    },
                    actions = { if (top) Text("寄丢", style = MaterialTheme.typography.labelLarge) },
                )
            },
            bottomBar = {
                if (top)
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.first,
                                onClick = { navigateTab(tab.first) },
                                icon = { Icon(tab.third, tab.second) },
                                label = { Text(tab.second) },
                            )
                        }
                    }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                NavHost(nav, "home") {
                    composable("home") {
                        HomeScreen(
                            vm,
                            onManual = { nav.navigate("manual") },
                            onDeck = { nav.navigate("deck/$it") },
                            onAi = { navigateTab("generation") },
                        )
                    }
                    composable("decks") { DecksScreen(vm) { nav.navigate("deck/$it") } }
                    composable("deck/{id}") {
                        val id = it.arguments?.getString("id") ?: return@composable
                        DeckDetailScreen(
                            vm,
                            id,
                            onAdd = { nav.navigate("manual?deckId=$id") },
                            onPreview = { nav.navigate("preview/$id") },
                            onExport = { kind -> exportFile(kind, id) },
                            onShare = {
                                vm.share(id) { file ->
                                    val uri =
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.files",
                                            file,
                                        )
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "application/octet-stream"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                clipData =
                                                    android.content.ClipData.newRawUri("APKG", uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            },
                                            "分享 APKG",
                                        )
                                    )
                                }
                            },
                        )
                    }
                    composable("generation") {
                        GenerationScreen(
                            vm,
                            onModels = { nav.navigate("providers") },
                            onAudit = { nav.navigate("audit/$it") },
                            onProgress = { nav.navigate("progress/$it") },
                        )
                    }
                    composable("preview/{id}") { entry ->
                        DeckPreviewScreen(vm, entry.arguments?.getString("id") ?: return@composable)
                    }
                    composable("audit/{id}") {
                        val id = it.arguments?.getString("id") ?: return@composable
                        AuditScreen(vm, id)
                    }
                    composable("progress/{id}") { entry ->
                        TaskProgressScreen(
                            vm,
                            entry.arguments?.getString("id") ?: return@composable,
                            onAudit = { nav.navigate("audit/$it") },
                            onProgress = { nav.navigate("progress/$it") },
                        )
                    }
                    composable(
                        "manual?deckId={deckId}",
                        arguments =
                            listOf(
                                navArgument("deckId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            ),
                    ) {
                        ManualCardScreen(vm, it.arguments?.getString("deckId")) { deck ->
                            nav.popBackStack()
                            nav.navigate("deck/$deck") { launchSingleTop = true }
                        }
                    }
                    composable("settings") {
                        SettingsScreen(
                            vm,
                            onModels = { nav.navigate("providers") },
                            onExport = { exportFile(it, null) },
                            onRestore = {
                                restore.launch(
                                    arrayOf("application/json", "application/octet-stream")
                                )
                            },
                        )
                    }
                    composable("providers") { ProvidersScreen(vm) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
    restoreUri?.let { uri ->
        Confirm(
            "覆盖恢复制卡数据？",
            "将先校验备份，校验成功后事务覆盖牌组与卡片。损坏文件不会更改现有数据。模型与密钥不包含在备份中。",
            { restoreUri = null },
        ) {
            restoreUri = null
            vm.restore(uri)
        }
    }
}
