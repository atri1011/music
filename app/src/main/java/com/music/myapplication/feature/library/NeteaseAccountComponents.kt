package com.music.myapplication.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.music.myapplication.domain.model.NeteaseAccountSession
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.glassSurface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NeteaseAccountHeaderCard(
    state: LibraryUiState,
    onClick: () -> Unit,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val account = state.account
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .glassSurface(shape = RoundedCornerShape(AppShapes.XLarge), pressScale = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccountAvatar(avatarUrl = account?.avatarUrl.orEmpty())
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = account?.nickname ?: "网易云音乐",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            !state.isNeteaseConfigured -> "先去“更多”里填增强版接口地址，再点我登录"
                            account == null -> "点击头像登录，支持密码、验证码、扫码"
                            else -> "UID ${account.userId} · ${formatSyncTime(account)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (account != null) {
                Box(
                    modifier = Modifier
                        .glassSurface(
                            shape = RoundedCornerShape(999.dp),
                            pressScale = !state.isSyncingNeteaseData
                        )
                        .clickable(enabled = !state.isSyncingNeteaseData, onClick = onSyncClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.isSyncingNeteaseData) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("同步", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        state.syncMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            InfoBadge(text = message, color = Color(0xFF1B8F4C))
        }
        state.syncError?.takeIf { it.isNotBlank() }?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            InfoBadge(text = error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeteaseLoginSheet(
    state: LibraryUiState,
    onDismiss: () -> Unit,
    onPasswordLogin: (phone: String, password: String) -> Unit,
    onSendCaptcha: (phone: String) -> Unit,
    onCaptchaLogin: (phone: String, captcha: String) -> Unit,
    onStartQrLogin: () -> Unit,
    onSyncClick: () -> Unit,
    onLogout: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var captcha by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (state.account == null) "登录网易云音乐" else "网易云账号",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!state.isNeteaseConfigured) {
                Text(
                    text = "当前还没配置增强版接口地址，先去“更多”页把 Base URL 填上。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                return@ModalBottomSheet
            }

            if (state.account != null) {
                LoggedInSheetContent(
                    account = state.account,
                    state = state,
                    onSyncClick = onSyncClick,
                    onLogout = onLogout
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("密码", "验证码", "扫码").forEachIndexed { index, title ->
                        FilterChip(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                if (index == 2 && state.qrPayload == null && !state.isPollingQr) {
                                    onStartQrLogin()
                                }
                            },
                            label = { Text(title) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> PasswordLoginContent(
                        phone = phone,
                        password = password,
                        isLoading = state.isAuthenticating,
                        onPhoneChange = { phone = it },
                        onPasswordChange = { password = it },
                        onLogin = { onPasswordLogin(phone, password) }
                    )
                    1 -> CaptchaLoginContent(
                        phone = phone,
                        captcha = captcha,
                        isSendingCaptcha = state.isSendingCaptcha,
                        isAuthenticating = state.isAuthenticating,
                        onPhoneChange = { phone = it },
                        onCaptchaChange = { captcha = it },
                        onSendCaptcha = { onSendCaptcha(phone) },
                        onLogin = { onCaptchaLogin(phone, captcha) }
                    )
                    else -> QrLoginContent(
                        state = state,
                        onRefresh = onStartQrLogin
                    )
                }

                state.authError?.takeIf { it.isNotBlank() }?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LoggedInSheetContent(
    account: NeteaseAccountSession,
    state: LibraryUiState,
    onSyncClick: () -> Unit,
    onLogout: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AccountAvatar(avatarUrl = account.avatarUrl, size = 72.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(account.nickname, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "UID ${account.userId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatSyncTime(account),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onSyncClick,
            enabled = !state.isSyncingNeteaseData,
            modifier = Modifier.weight(1f)
        ) {
            if (state.isSyncingNeteaseData) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
        Text("立即同步")
    }
    TextButton(onClick = onLogout, modifier = Modifier.weight(1f)) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("退出登录")
    }
}
}

@Composable
private fun PasswordLoginContent(
    phone: String,
    password: String,
    isLoading: Boolean,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    PhoneInput(phone = phone, onPhoneChange = onPhoneChange)
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("密码") }
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onLogin,
        enabled = phone.isNotBlank() && password.isNotBlank() && !isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text("登录")
    }
}

@Composable
private fun CaptchaLoginContent(
    phone: String,
    captcha: String,
    isSendingCaptcha: Boolean,
    isAuthenticating: Boolean,
    onPhoneChange: (String) -> Unit,
    onCaptchaChange: (String) -> Unit,
    onSendCaptcha: () -> Unit,
    onLogin: () -> Unit
) {
    PhoneInput(phone = phone, onPhoneChange = onPhoneChange)
    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            value = captcha,
            onValueChange = onCaptchaChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text("短信验证码") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.width(12.dp))
        TextButton(
            onClick = onSendCaptcha,
            enabled = phone.isNotBlank() && !isSendingCaptcha,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            if (isSendingCaptcha) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text("发验证码")
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onLogin,
        enabled = phone.isNotBlank() && captcha.isNotBlank() && !isAuthenticating,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isAuthenticating) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text("验证码登录")
    }
}

@Composable
private fun QrLoginContent(
    state: LibraryUiState,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                state.isAuthenticating && state.qrPayload == null -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("正在生成二维码…")
                }
                !state.qrPayload?.qrImageUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = state.qrPayload?.qrImageUrl,
                        contentDescription = "二维码",
                        modifier = Modifier.size(220.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.qrStatusMessage ?: "请使用网易云音乐 App 扫码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRefresh) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重新生成")
                    }
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRefresh) {
                        Text("生成二维码")
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneInput(
    phone: String,
    onPhoneChange: (String) -> Unit
) {
    OutlinedTextField(
        value = phone,
        onValueChange = onPhoneChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("手机号") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
    )
}

@Composable
private fun AccountAvatar(
    avatarUrl: String,
    size: androidx.compose.ui.unit.Dp = 64.dp
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), shape)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "头像",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(size),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun InfoBadge(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

private fun formatSyncTime(account: NeteaseAccountSession): String {
    if (account.lastSyncAt <= 0L) return "尚未同步"
    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
    return "上次同步 ${formatter.format(Instant.ofEpochMilli(account.lastSyncAt))}"
}
