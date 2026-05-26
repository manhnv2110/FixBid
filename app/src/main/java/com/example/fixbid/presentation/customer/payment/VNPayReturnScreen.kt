package com.example.fixbid.presentation.customer.payment

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.usecase.customer.ProcessVNPayReturnUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class VNPayReturnUiState(
    val isProcessing: Boolean = true,
    val isSuccess: Boolean = false,
    val message: String = "Đang xử lý kết quả thanh toán..."
)

@HiltViewModel
class VNPayReturnViewModel @Inject constructor(
    private val processVNPayReturnUseCase: ProcessVNPayReturnUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(VNPayReturnUiState())
    val uiState: StateFlow<VNPayReturnUiState> = _uiState.asStateFlow()

    fun processReturn(uriString: String) {
        val uri = Uri.parse(uriString)
        val params = mutableMapOf<String, String>()
        uri.queryParameterNames.forEach { key ->
            uri.getQueryParameter(key)?.let { value ->
                params[key] = value
            }
        }

        if (params.isEmpty()) {
            _uiState.value = VNPayReturnUiState(
                isProcessing = false,
                isSuccess = false,
                message = "Không có dữ liệu thanh toán"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = VNPayReturnUiState(isProcessing = true)
            when (val result = processVNPayReturnUseCase(params)) {
                is Resource.Success -> {
                    _uiState.value = VNPayReturnUiState(
                        isProcessing = false,
                        isSuccess = true,
                        message = "Thanh toán thành công!\nTiền đang được hệ thống giữ an toàn."
                    )
                }
                is Resource.Error -> {
                    _uiState.value = VNPayReturnUiState(
                        isProcessing = false,
                        isSuccess = false,
                        message = result.message
                    )
                }
                is Resource.Loading -> {}
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun VNPayReturnScreen(
    returnUri: String,
    onDone: () -> Unit,
    viewModel: VNPayReturnViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Process the return URI once
    LaunchedEffect(returnUri) {
        viewModel.processReturn(returnUri)
    }

    val isDark = isSystemInDarkTheme()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                when {
                    uiState.isProcessing -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Đang xử lý kết quả thanh toán...",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    uiState.isSuccess -> {
                        val successBg = if (isDark) Color(0xFF1A2B1A) else Color(0xFFE8F5E9)
                        val successColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)

                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(successBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = successColor,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = uiState.message,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = successColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = onDone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                "Về trang chủ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                    else -> {
                        val errorBg = if (isDark) Color(0xFF2D1010) else Color(0xFFFFEBEE)
                        val errorColor = if (isDark) Color(0xFFFF8A80) else Color(0xFFD32F2F)

                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(errorBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = errorColor,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Thanh toán thất bại",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = errorColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.message,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = onDone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                "Về trang chủ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}