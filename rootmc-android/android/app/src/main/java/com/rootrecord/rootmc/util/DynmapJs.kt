package com.rootrecord.rootmc.util

import com.rootrecord.rootmc.data.local.entity.MinecraftDimension
import org.json.JSONArray
import org.json.JSONObject

object DynmapJs {

    fun dimensionToMapWorld(dimension: String): String = when (dimension.uppercase()) {
        MinecraftDimension.NETHER.name -> "world_nether"
        MinecraftDimension.END.name -> "world_the_end"
        else -> "world"
    }

    /** Pan the embedded Dynmap to block coordinates (best-effort across Dynmap versions). */
    fun flyToScript(x: Int, y: Int, z: Int, dimension: String): String {
        val world = dimensionToMapWorld(dimension)
        return """
            (function(){
              try {
                if (typeof dynmap === 'undefined') return 'loading';
                var map = null;
                try { map = dynmap.getMap('$world'); } catch (e) {}
                if (!map) { try { map = dynmap.getMap('world'); } catch (e2) {} }
                if (!map) { try { map = dynmap.getMap(); } catch (e3) {} }
                if (!map) return 'no_map';
                if (typeof map.panToLocation === 'function') {
                  map.panToLocation({world:'$world',x:$x,y:$y,z:$z});
                  return 'ok';
                }
                if (typeof dynmap.panTo === 'function') {
                  dynmap.panTo($x, $y, $z);
                  return 'ok';
                }
                return 'unsupported';
              } catch (err) { return String(err); }
            })();
        """.trimIndent()
    }

    /** Install or refresh RootMC waypoint markers on the Dynmap (when API is available). */
    fun installWaypointsScript(waypoints: List<WaypointJs>): String {
        val arr = JSONArray()
        waypoints.forEach { wp ->
            arr.put(
                JSONObject()
                    .put("id", wp.id)
                    .put("label", wp.label)
                    .put("x", wp.x)
                    .put("y", wp.y)
                    .put("z", wp.z)
                    .put("world", wp.world),
            )
        }
        val json = arr.toString()
        return """
            (function(){
              try {
                if (typeof dynmap === 'undefined') return 'loading';
                var wps = $json;
                var map = null;
                try { map = dynmap.getMap(); } catch (e) {}
                if (!map || !map.markersets) return 'no_markers';
                var label = 'RootMC';
                var set = map.markersets.get('rootmc_waypoints');
                if (!set && map.markersets.createMarkerSet) {
                  set = map.markersets.createMarkerSet('rootmc_waypoints', label, true);
                }
                if (!set) return 'no_set';
                if (set.clear) set.clear();
                for (var i = 0; i < wps.length; i++) {
                  var w = wps[i];
                  var icon = map.markersets.defaultIcon || null;
                  if (set.createMarker) {
                    set.createMarker(w.id, w.label, true, w.world, w.x, w.y, w.z, icon);
                  }
                }
                return 'ok';
              } catch (err) { return String(err); }
            })();
        """.trimIndent()
    }

    data class WaypointJs(
        val id: String,
        val label: String,
        val x: Int,
        val y: Int,
        val z: Int,
        val world: String,
    )
}
