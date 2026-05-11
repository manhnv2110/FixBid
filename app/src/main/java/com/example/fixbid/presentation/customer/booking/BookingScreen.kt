package com.example.fixbid.presentation.customer.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.ui.theme.BackgroundGray
import com.example.fixbid.ui.theme.PrimaryBlue
import com.example.fixbid.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingScreen(
    initialCategoryName: String?,
    onBackClick: () -> Unit,
    onSubmitSuccess: () -> Unit,
    viewModel: BookingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Handle side effects of UI State
    LaunchedEffect(uiState) {
        if (uiState is BookingUiState.Success) {
            viewModel.resetState()
            onSubmitSuccess()
        }
    }
    var expanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { 
        mutableStateOf(
            ServiceCategory.values().find { it.name == initialCategoryName } 
                ?: ServiceCategory.OTHER
        )
    }

    var detailedDescription by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    fun requiredLabel(label: String) = buildAnnotatedString {
        append(label)
        append(" ")
        withStyle(SpanStyle(color = Color.Red)) { append("*") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đặt lịch nhanh chóng", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundGray
                )
            )
        },
        containerColor = BackgroundGray
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nội dung công việc (Dropdown)
            Column {
                Text(
                    text = requiredLabel("Nội dung công việc"),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCategory.displayName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        ServiceCategory.values().forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.displayName, color = TextPrimary) },
                                onClick = {
                                    selectedCategory = category
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Mô tả chi tiết công việc
            Column {
                Text(
                    text = requiredLabel("Mô tả chi tiết công việc"),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = detailedDescription,
                    onValueChange = { detailedDescription = it },
                    placeholder = { Text("Nhập mô tả chi tiết công việc...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color.LightGray,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }

            // Địa chỉ
            Column {
                Text(
                    text = requiredLabel("Địa chỉ"),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    placeholder = { Text("Vui lòng nhập địa chỉ cụ thể") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color.LightGray
                    )
                )
            }

            // Số điện thoại
            Column {
                Text(
                    text = requiredLabel("Số điện thoại"),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                            .background(Color.White)
                            .border(1.dp, Color.LightGray, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        Text(text = "🇻🇳 +84", fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        placeholder = { Text("Số điện thoại của Quý Khách") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color.LightGray
                        )
                    )
                }
            }

            // Họ và tên
            Column {
                Text(
                    text = requiredLabel("Họ và tên"),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    placeholder = { Text("Vui lòng nhập họ và tên") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color.LightGray
                    )
                )
            }

            // Ghi chú
            Column {
                Text(
                    text = "Ghi chú",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Vui lòng nhập ghi chú nếu có\nGợi ý:\n- Nhập thêm địa chỉ, số căn hộ, tháp chung cư...\n- Nhập thêm tình trạng thiết bị cần sửa...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color.LightGray
                    ),
                    maxLines = 5
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState is BookingUiState.Error) {
                Text(
                    text = (uiState as BookingUiState.Error).message,
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    // validation could be added here
                    viewModel.createBooking(
                        category = selectedCategory,
                        description = detailedDescription,
                        address = address,
                        phoneNumber = phoneNumber,
                        fullName = fullName,
                        notes = notes
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp),
                enabled = uiState !is BookingUiState.Loading &&
                          detailedDescription.isNotBlank() &&
                          address.isNotBlank() &&
                          phoneNumber.isNotBlank() &&
                          fullName.isNotBlank()
            ) {
                if (uiState is BookingUiState.Loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Đặt lịch ngay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
