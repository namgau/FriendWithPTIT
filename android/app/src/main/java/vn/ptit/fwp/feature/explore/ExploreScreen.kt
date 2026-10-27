package vn.ptit.fwp.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.ptit.fwp.core.design.EmptyState
import vn.ptit.fwp.core.design.FwpChip
import vn.ptit.fwp.core.design.GpaBadge
import vn.ptit.fwp.core.design.ProfilePhoto
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.design.SearchField
import vn.ptit.fwp.core.design.VerifiedMark
import vn.ptit.fwp.core.model.ConnectionMode
import vn.ptit.fwp.core.model.RankedProfile
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.formatDistance
import vn.ptit.fwp.core.util.fwpViewModel

@Composable
fun ExploreScreen(
    onOpenProfile: (String) -> Unit,
    onOpenMatches: () -> Unit,
    onOpenNearby: () -> Unit
) {
    val container = appContainer()
    val viewModel = fwpViewModel { ExploreViewModel(container) }
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Khám phá", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${state.totalProfiles} hồ sơ đã xác thực trong cộng đồng FwP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.clickable(onClick = onOpenNearby)
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Map, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Bản đồ", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.clickable { viewModel.toggleSort() }
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Sort, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (state.filters.sortByDistance) "Gần nhất" else "Hợp nhất",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (state.incomingLikes > 0) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = PtitRedSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable(onClick = onOpenMatches)
            ) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PtitRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${state.incomingLikes} người đã thích bạn", style = MaterialTheme.typography.titleSmall, color = PtitRed)
                        Text("Mở Match để xem và kết nối lại.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Xem", style = MaterialTheme.typography.labelLarge, color = PtitRed)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            SearchField(
                value = state.filters.query,
                onValueChange = viewModel::setQuery,
                placeholder = "Tìm tên, khoa, sở thích…"
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                FwpChip(
                    text = "Tất cả",
                    selected = state.filters.mode == null,
                    onClick = { viewModel.setMode(null) }
                )
                ConnectionMode.entries.forEach { mode ->
                    FwpChip(
                        text = mode.label,
                        selected = state.filters.mode == mode,
                        onClick = { viewModel.setMode(if (state.filters.mode == mode) null else mode) }
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                state.campuses.forEach { campus ->
                    FwpChip(
                        text = campus.substringBefore(" – "),
                        selected = state.filters.campus == campus,
                        onClick = { viewModel.setCampus(if (state.filters.campus == campus) null else campus) }
                    )
                }
                if (state.filters.query.isNotBlank() || state.filters.mode != null || state.filters.campus != null) {
                    Text(
                        "Xóa bộ lọc",
                        style = MaterialTheme.typography.labelLarge,
                        color = PtitRed,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.reset() }
                            .padding(horizontal = 9.dp, vertical = 9.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (state.items.isEmpty() && !state.loading) {
            EmptyState(
                icon = Icons.Rounded.Verified,
                title = "Không tìm thấy hồ sơ phù hợp",
                subtitle = "Thử đổi từ khóa, bỏ lọc chế độ kết nối hoặc chọn campus khác.",
                actionLabel = "Đặt lại bộ lọc",
                onAction = viewModel::reset
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                items(state.items, key = { it.profile.id }) { ranked ->
                    ExploreCard(
                        ranked = ranked,
                        liked = ranked.profile.id in state.likedIds,
                        passed = ranked.profile.id in state.passedIds,
                        onClick = { onOpenProfile(ranked.profile.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreCard(
    ranked: RankedProfile,
    liked: Boolean,
    passed: Boolean,
    onClick: () -> Unit
) {
    val profile = ranked.profile
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(22.dp))
            .background(PtitRedSoft)
            .clickable(onClick = onClick)
    ) {
        ProfilePhoto(
            profile = profile,
            modifier = Modifier.fillMaxSize(),
            blurLevel = 0.9f,
            shape = RoundedCornerShape(22.dp),
            showAnonymousHint = false
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.10f),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f)
                    )
                )
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.36f),
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Text(
                "${ranked.score}% hợp",
                style = MaterialTheme.typography.labelSmall,
                color = PtitGold,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (liked) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(PtitRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        } else if (passed) {
            Text(
                "Đã bỏ qua",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(9.dp)
                    .background(Color.Black.copy(alpha = 0.36f), RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(3.dp))
                Text(profile.age.toString(), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.76f))
                if (profile.verified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Verified, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(14.dp))
                }
            }
            Text(
                "${profile.academicYear} · ${profile.major.ifBlank { profile.faculty }}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GpaBadge(profile.gpaTier, compact = true)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Rounded.LocationOn, null, tint = PtitGold, modifier = Modifier.size(11.dp))
                Text(
                    formatDistance(profile.distanceKm),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
