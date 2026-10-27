package vn.ptit.fwp.feature.nearby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import androidx.compose.ui.viewinterop.AndroidView
import vn.ptit.fwp.BuildConfig
import vn.ptit.fwp.core.design.FwpChip
import vn.ptit.fwp.core.design.FwpTopBar
import vn.ptit.fwp.core.design.ProfilePhoto
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.model.ConnectionMode
import vn.ptit.fwp.core.model.RankedProfile
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.formatDistance
import vn.ptit.fwp.core.util.fwpViewModel
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val PtitCenter = LatLng(20.9818, 105.7875)
private val radiusOptions = listOf(3, 5, 10, 20)

@Composable
fun NearbyScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val container = appContainer()
    val viewModel = fwpViewModel { NearbyViewModel(container) }
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize()) {
        FwpTopBar(
            title = "Quanh bạn",
            subtitle = "${state.items.size} hồ sơ trong bán kính ${state.radiusKm} km",
            onBack = onBack
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            radiusOptions.forEach { radius ->
                FwpChip(
                    text = "$radius km",
                    selected = state.radiusKm == radius,
                    onClick = { viewModel.setRadius(radius) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            FwpChip(
                text = "Tất cả",
                selected = state.mode == null,
                onClick = { viewModel.setMode(null) }
            )
            ConnectionMode.entries.forEach { mode ->
                FwpChip(
                    text = mode.label,
                    selected = state.mode == mode,
                    onClick = {
                        viewModel.setMode(if (state.mode == mode) null else mode)
                    }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        NearbyMap(
            items = state.items,
            radiusKm = state.radiusKm,
            onOpenProfile = onOpenProfile,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(24.dp))
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Gần bạn nhất",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(6.dp))
        if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Chưa có ai trong bán kính này. Thử mở rộng khoảng cách nhé.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.profile.id }) { ranked ->
                    NearbyRow(ranked = ranked, onClick = {
                        onOpenProfile(ranked.profile.id)
                    })
                }
            }
        }
    }
}

@Composable
private fun NearbyMap(
    items: List<RankedProfile>,
    radiusKm: Int,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasMapsKey = BuildConfig.MAPS_API_KEY.isNotBlank()
    Box(modifier.background(PtitRedSoft)) {
        if (hasMapsKey) {
            GoogleNearbyMap(
                items = items,
                radiusKm = radiusKm,
                onOpenProfile = onOpenProfile
            )
        } else {
            RadarNearbyMap(items = items, radiusKm = radiusKm)
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Map,
                    contentDescription = null,
                    tint = PtitRed,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (hasMapsKey) "Google Maps" else "Bản đồ mô phỏng",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (!hasMapsKey) {
            Text(
                "Thêm MAPS_API_KEY khi build để dùng Google Maps. Chạm thẻ bên dưới để mở hồ sơ.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun GoogleNearbyMap(
    items: List<RankedProfile>,
    radiusKm: Int,
    onOpenProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context) { MapView(context) }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        mapView.onCreate(null)
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map -> googleMap = map }
    }

    LaunchedEffect(googleMap, items, radiusKm, onOpenProfile) {
        val map = googleMap ?: return@LaunchedEffect
        map.clear()
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                PtitCenter,
                when {
                    radiusKm <= 3 -> 15f
                    radiusKm <= 5 -> 14f
                    radiusKm <= 10 -> 13f
                    else -> 12f
                }
            )
        )
        items.forEach { ranked ->
            val marker = map.addMarker(
                MarkerOptions()
                    .position(markerPosition(ranked.profile.avatarSeed, ranked.profile.distanceKm))
                    .title(ranked.profile.nickname)
                    .snippet(
                        "${ranked.profile.mode.label} • ${formatDistance(ranked.profile.distanceKm)}"
                    )
                    .icon(
                        BitmapDescriptorFactory.defaultMarker(
                            if (ranked.profile.likedYou) {
                                BitmapDescriptorFactory.HUE_ORANGE
                            } else {
                                BitmapDescriptorFactory.HUE_RED
                            }
                        )
                    )
            )
            marker?.tag = ranked.profile.id
        }
        map.setOnMarkerClickListener { marker ->
            (marker.tag as? String)?.let(onOpenProfile)
            true
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun RadarNearbyMap(items: List<RankedProfile>, radiusKm: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(PtitRed.copy(alpha = 0.08f), Color.Transparent)
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = min(size.width, size.height) / 2 - 24.dp.toPx()
            for (ring in 1..3) {
                drawCircle(
                    color = PtitRed.copy(alpha = 0.16f),
                    radius = maxRadius * ring / 3f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            drawCircle(PtitGold, radius = 6.dp.toPx(), center = center)
            items.forEach { ranked ->
                val angle = Math.toRadians(
                    ((ranked.profile.avatarSeed * 47) % 360).toDouble()
                )
                val fraction = (ranked.profile.distanceKm / radiusKm)
                    .coerceIn(0.08, 0.94)
                    .toFloat()
                val position = Offset(
                    x = center.x + cos(angle).toFloat() * maxRadius * fraction,
                    y = center.y + sin(angle).toFloat() * maxRadius * fraction
                )
                val color = when (ranked.profile.mode) {
                    ConnectionMode.DATING -> PtitRed
                    ConnectionMode.FRIENDS -> Color(0xFF7B1FA2)
                    ConnectionMode.STUDY -> Color(0xFF1565C0)
                }
                drawCircle(Color.White, radius = 8.dp.toPx(), center = position)
                drawCircle(color, radius = 5.5.dp.toPx(), center = position)
                if (ranked.profile.likedYou) {
                    drawCircle(
                        color = PtitGold,
                        radius = 9.dp.toPx(),
                        center = position,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
        Text(
            "PTIT Hà Đông",
            style = MaterialTheme.typography.labelSmall,
            color = PtitRed,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 16.dp)
        )
        Icon(
            Icons.Rounded.MyLocation,
            contentDescription = null,
            tint = PtitRed,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(18.dp)
        )
    }
}

private fun markerPosition(seed: Int, distanceKm: Double): LatLng {
    val angle = Math.toRadians(((seed * 47) % 360).toDouble())
    val latOffset = cos(angle) * distanceKm / 111.0
    val lngOffset = sin(angle) * distanceKm /
        (111.0 * cos(Math.toRadians(PtitCenter.latitude)))
    return LatLng(PtitCenter.latitude + latOffset, PtitCenter.longitude + lngOffset)
}

@Composable
private fun NearbyRow(ranked: RankedProfile, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfilePhoto(
                profile = ranked.profile,
                modifier = Modifier.size(56.dp),
                blurLevel = 0f,
                revealed = true,
                shape = RoundedCornerShape(16.dp),
                showAnonymousHint = false,
                showAnonBadge = false
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ranked.profile.nickname,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (ranked.profile.verified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.Verified,
                            contentDescription = "Đã xác thực",
                            tint = PtitRed,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    "${ranked.profile.faculty} • ${ranked.profile.mode.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatDistance(ranked.profile.distanceKm),
                    style = MaterialTheme.typography.labelSmall,
                    color = PtitRed
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${ranked.score}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PtitRed
                )
                Text(
                    "độ hợp",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
