@file:OptIn(ExperimentalMaterial3Api::class)

package com.skygoto.app.ui.screens.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.skygoto.app.domain.model.CelestialObject
import com.skygoto.app.domain.model.ObjectType
import com.skygoto.app.domain.repository.CatalogType
import com.skygoto.app.ui.components.ResultBanner
import com.skygoto.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel = hiltViewModel(),
@Suppress("UNUSED_PARAMETER")  // Deprecated: use gotoObject directly
    onGoto: (String, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary)
    ) {
        // 标题栏
        TopAppBar(
            title = { Text("天体目录", color = TextPrimary) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary)
        )
        
        // 搜索框
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = viewModel::search,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 目录选择器
        CatalogSelector(
            selected = uiState.selectedCatalog,
            onSelect = viewModel::selectCatalog
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 类型过滤器
        TypeFilter(
            selectedType = uiState.selectedType,
            onTypeSelected = viewModel::filterByType
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 天体数量
        Text(
            text = "${uiState.filteredObjects.size} 个天体",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 天体列表
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.filteredObjects) { obj ->
                    CelestialObjectItem(
                        obj = obj,
                        onClick = { viewModel.selectObject(obj) },
                        onGoto = { viewModel.gotoObject(obj) }
                    )
                }
            }
        }
    }
    
    // 详情弹窗
    uiState.selectedObject?.let { obj ->
        ObjectDetailSheet(
            obj = obj,
            onDismiss = { viewModel.selectObject(null) },
            onGoto = {
                viewModel.gotoObject(obj)
                viewModel.selectObject(null)
            }
        )
    }
    
    // GOTO 进度弹窗
    uiState.gotoProgressInfo?.let { progressInfo ->
        GotoProgressDialog(
            progressInfo = progressInfo,
            onConfirm = { viewModel.dismissGotoProgress() }
        )
    }
    
    // GOTO 结果 Banner（失败/成功提示，弹窗关闭后显示）
    uiState.gotoResult?.let { config ->
        ResultBanner(
            config = config,
            onDismiss = { viewModel.clearGotoResult() }
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("搜索梅西耶/NGC...", color = TextSecondary) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索", tint = TextSecondary) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "清除", tint = TextSecondary)
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = Secondary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Accent
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun CatalogSelector(
    selected: CatalogType,
    onSelect: (CatalogType) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(CatalogType.entries) { catalog ->
            FilterChip(
                selected = catalog == selected,
                onClick = { onSelect(catalog) },
                label = {
                    Text(
                        when (catalog) {
                            CatalogType.ALL -> "全部"
                            CatalogType.MESSIER -> "梅西耶"
                            CatalogType.NGC -> "NGC"
                            CatalogType.IC -> "IC"
                            CatalogType.SOLAR_SYSTEM -> "太阳系"
                            CatalogType.STAR -> "恒星"
                        }
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent,
                    selectedLabelColor = Primary
                )
            )
        }
    }
}

@Composable
private fun TypeFilter(
    selectedType: ObjectType?,
    onTypeSelected: (ObjectType?) -> Unit
) {
    val types = listOf(
        null to "全部",
        ObjectType.GALAXY to "星系",
        ObjectType.NEBULA to "星云",
        ObjectType.OPEN_CLUSTER to "疏散星团",
        ObjectType.GLOBULAR_CLUSTER to "球状星团",
        ObjectType.PLANETARY_NEBULA to "行星状星云"
    )
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(types) { (type, label) ->
            FilterChip(
                selected = type == selectedType,
                onClick = { onTypeSelected(type) },
                label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent2,
                    selectedLabelColor = Primary
                )
            )
        }
    }
}

@Composable
private fun CelestialObjectItem(
    obj: CelestialObject,
    onClick: () -> Unit,
    onGoto: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Secondary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = obj.id,
                        style = MaterialTheme.typography.titleMedium,
                        color = Accent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = obj.type.cnName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Text(
                    text = obj.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Text(
                    text = "${obj.constellation} | 星等 ${String.format("%.1f", obj.magnitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "RA: ${obj.ra}  Dec: ${obj.dec}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            IconButton(
                onClick = onGoto,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Accent)
            ) {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = "GOTO",
                    tint = Primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObjectDetailSheet(
    obj: CelestialObject,
    onDismiss: () -> Unit,
    onGoto: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Secondary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = obj.id,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Accent,
                    fontWeight = FontWeight.Bold
                )
                TypeBadge(type = obj.type.cnName)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = obj.name,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            
            if (obj.altNames.isNotEmpty()) {
                Text(
                    text = "别名: ${obj.altNames.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            DetailRow("星座", obj.constellation)
            DetailRow("类型", obj.type.cnName)
            DetailRow("星等", String.format("%.1f", obj.magnitude))
            DetailRow("赤经", obj.ra)
            DetailRow("赤纬", obj.dec)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onGoto,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, tint = Primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("GOTO ${obj.id}", color = Primary)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun TypeBadge(type: String) {
    Surface(
        color = Accent.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = type,
            style = MaterialTheme.typography.labelSmall,
            color = Accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
@Composable
private fun GotoProgressDialog(
    progressInfo: com.skygoto.app.ui.screens.catalog.GotoProgressInfo,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* 禁止点击遮罩关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = progressInfo.isFinished,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    disabledContainerColor = Accent.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "确定",
                    color = if (progressInfo.isFinished) Primary else TextSecondary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        title = {
            Text(
                text = "${progressInfo.objectId} GOTO执行中",
                style = MaterialTheme.typography.titleLarge,
                color = Accent,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "目标 RA：${progressInfo.targetRa}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "目标DEC：${progressInfo.targetDec}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = TextSecondary.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "当前 RA：${progressInfo.currentRa}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (progressInfo.isFinished) Accent else TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (progressInfo.isFinished) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "当前DEC：${progressInfo.currentDec}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (progressInfo.isFinished) Accent else TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (progressInfo.isFinished) FontWeight.Bold else FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!progressInfo.isFinished) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Accent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "赤道仪移动中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                } else {
                    Text(
                        text = "✓ 目标已锁定",
                        style = MaterialTheme.typography.bodySmall,
                        color = Accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        containerColor = Secondary,
        titleContentColor = Accent,
        textContentColor = TextPrimary
    )
}
