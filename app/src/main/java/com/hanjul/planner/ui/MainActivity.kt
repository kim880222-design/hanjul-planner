package com.hanjul.planner.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hanjul.planner.data.Item
import com.hanjul.planner.domain.ItemType
import com.hanjul.planner.notify.ReminderScheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        ReminderScheduler.createChannels(this)
        lifecycleScope.launch { ReminderScheduler.rescheduleAll(applicationContext) }
        setContent { PlannerTheme { PlannerApp(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshToday()
    }
}

private val tabs = listOf("현황", "오늘", "달력", "프로젝트")

@Composable
fun PlannerApp(vm: MainViewModel) {
    val items by vm.items.collectAsState()
    val steps by vm.steps.collectAsState()
    val today by vm.today.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var openProjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var statFilter by rememberSaveable { mutableStateOf<StatFilter?>(null) }
    var editing by remember { mutableStateOf<Item?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 알림 권한 (안드로이드 13 이상)
    val context = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val act = Actions(
        open = { if (it.type == ItemType.PROJECT) openProjectId = it.id else editing = it },
        toggle = { vm.toggle(it) },
        openProject = { openProjectId = it },
        toggleStep = { vm.toggleStep(it) },
    )

    BackHandler(enabled = openProjectId != null) { openProjectId = null }
    BackHandler(enabled = openProjectId == null && statFilter != null) { statFilter = null }

    Scaffold(
        containerColor = Pal.Black,
        topBar = {
            StatusHeader(today, when {
                openProjectId != null -> "프로젝트"
                statFilter != null -> "현황 > ${statFilter!!.label}"
                else -> tabs[tab]
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.imePadding()) {
                QuickAddBar(today) { parsed, type ->
                    vm.add(parsed, type)
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar("OK > ${type.label} 저장됨: ${parsed.title}")
                    }
                }
                FunctionKeyBar(tab, openProjectId == null && statFilter == null) { tab = it; openProjectId = null; statFilter = null }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val project = openProjectId?.let { id -> items.firstOrNull { it.id == id } }
            if (project != null) {
                ProjectDetailScreen(
                    project = project,
                    steps = steps.filter { it.projectId == project.id },
                    today = today,
                    vm = vm,
                    onEdit = { editing = project },
                    onBack = { openProjectId = null },
                )
            } else if (statFilter != null) {
                StatListScreen(statFilter!!, items, steps, today, act, onBack = { statFilter = null })
            } else when (tab) {
                0 -> DashboardScreen(items, steps, today, act,
                    onStat = { statFilter = it }, onProjects = { tab = 3 })
                1 -> TodayScreen(items, steps, today, act)
                2 -> CalendarScreen(items, steps, today, act)
                else -> ProjectsScreen(items, steps, today, act)
            }
        }
    }

    editing?.let { item ->
        EditItemDialog(
            item = item,
            today = today,
            onSave = { vm.update(it); editing = null },
            onDelete = {
                vm.delete(it); editing = null
                if (openProjectId == it.id) openProjectId = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** 위쪽 단말기 상태 표시줄 */
@Composable
private fun StatusHeader(today: java.time.LocalDate, screen: String) {
    var now by remember { mutableStateOf(java.time.LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = java.time.LocalTime.now(); kotlinx.coroutines.delay(1000) } }
    Column(Modifier.background(Pal.Black).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(Pal.Amber).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("HANJUL-OS", color = Pal.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(" // $screen", color = Pal.Black, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                "%02d.%02d %02d:%02d".format(today.monthValue, today.dayOfMonth, now.hour, now.minute),
                color = Pal.Black, style = MaterialTheme.typography.labelLarge,
            )
        }
        // 카세트 퓨처리즘 띠
        Row(Modifier.fillMaxWidth().height(3.dp)) {
            listOf(Pal.Orange, Pal.Rust, Pal.Red, Pal.Black, Pal.Cyan).forEachIndexed { i, c ->
                Box(Modifier.weight(if (i == 3) 6f else 1f).fillMaxHeight().background(c))
            }
        }
    }
}

/** 아래쪽 F1~F4 기능키 메뉴 */
@Composable
private fun FunctionKeyBar(selected: Int, active: Boolean, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Pal.Black).navigationBarsPadding().padding(4.dp)) {
        tabs.forEachIndexed { i, label ->
            val on = active && selected == i
            Row(
                Modifier
                    .weight(1f)
                    .padding(2.dp)
                    .then(if (on) Modifier.background(Pal.Amber) else Modifier.border(1.dp, Pal.Gray3))
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("F${i + 1}", color = if (on) Pal.Black else Pal.Cyan, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium)
                Text(" $label", color = if (on) Pal.Black else Pal.Amber, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}
