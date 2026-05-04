package com.gramayatri.ui.screens.home

import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.gramayatri.R
import com.gramayatri.data.model.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.config.Configuration

@Composable
fun LiveMapContent(
    route: Route,
    liveLocation: LiveBusLocation?,
    activePing: BusPing?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Initialize OSMDroid config
    remember {
        Configuration.getInstance().load(context, android.preference.PreferenceManager.getDefaultSharedPreferences(context))
        true
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
        }
    }

    // Keep track of overlays to update them
    val routePolyline = remember { Polyline() }
    val busMarker = remember { 
        Marker(mapView).apply {
            title = "Live Bus"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
    }
    val stopMarkers = remember { mutableMapOf<String, Marker>() }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            // Update route polyline
            val points = route.stops.map { GeoPoint(it.lat, it.lng) }
            routePolyline.setPoints(points)
            if (!view.overlays.contains(routePolyline)) {
                view.overlays.add(routePolyline)
            }

            // Update stop markers
            route.stops.forEach { stop ->
                val marker = stopMarkers.getOrPut(stop.id) {
                    Marker(view).apply {
                        title = stop.name
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        view.overlays.add(this)
                    }
                }
                marker.position = GeoPoint(stop.lat, stop.lng)
            }

            // Update Bus Location (Hybrid logic)
            val busPoint = when {
                liveLocation != null -> GeoPoint(liveLocation.lat, liveLocation.lng)
                activePing != null && activePing.lat != 0.0 -> GeoPoint(activePing.lat, activePing.lng)
                else -> null
            }

            if (busPoint != null) {
                busMarker.position = busPoint
                busMarker.snippet = liveLocation?.reporterName?.let { "Reported by $it" } ?: "Estimated location"
                if (!view.overlays.contains(busMarker)) {
                    view.overlays.add(busMarker)
                }
                
                // Center map on bus if it's the first time or moving significantly
                // (Simplified for now: just follow)
                view.controller.animateTo(busPoint)
            } else {
                view.overlays.remove(busMarker)
            }

            // Center on first stop if no bus and first load
            if (busPoint == null && points.isNotEmpty() && view.zoomLevelDouble < 5.0) {
                view.controller.setCenter(points.first())
                view.controller.setZoom(13.0)
            }
            
            view.invalidate()
        }
    )
    
    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }
}
