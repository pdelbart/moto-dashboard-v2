package com.moto.dash

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Net {
    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "MotoDash/1.0 (usage personnel)")
        c.setRequestProperty("Accept-Language", "fr")
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

object Geo {
    /** Distance approchée en mètres (suffisante à l'échelle d'un trajet). */
    fun dist(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
        val x = Math.toRadians(lo2 - lo1) * Math.cos(Math.toRadians((la1 + la2) / 2))
        val y = Math.toRadians(la2 - la1)
        return Math.sqrt(x * x + y * y) * 6371000.0
    }
}

class Step(val idx: Int, val type: String, val modifier: String, val name: String)

class RouteData(
    val lat: DoubleArray,
    val lon: DoubleArray,
    val cum: DoubleArray,
    val steps: List<Step>,
    val duration: Double
) {
    val total: Double get() = cum[cum.size - 1]
}

object Router {
    /** Calcule un itinéraire voiture avec le serveur public OSRM. */
    fun fetch(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RouteData? {
        val url = String.format(
            java.util.Locale.US,
            "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true",
            fromLon, fromLat, toLon, toLat
        )
        return parse(Net.get(url))
    }

    fun parse(body: String): RouteData? {
        val j = JSONObject(body)
        if (j.optString("code") != "Ok") return null
        val r = j.getJSONArray("routes").getJSONObject(0)
        val coords: JSONArray = r.getJSONObject("geometry").getJSONArray("coordinates")
        val n = coords.length()
        if (n < 2) return null

        val lat = DoubleArray(n)
        val lon = DoubleArray(n)
        val cum = DoubleArray(n)
        for (i in 0 until n) {
            val c = coords.getJSONArray(i)
            lon[i] = c.getDouble(0)
            lat[i] = c.getDouble(1)
            if (i > 0) cum[i] = cum[i - 1] + Geo.dist(lat[i - 1], lon[i - 1], lat[i], lon[i])
        }

        val steps = ArrayList<Step>()
        val legs = r.getJSONArray("legs")
        var from = 0
        for (li in 0 until legs.length()) {
            val st = legs.getJSONObject(li).getJSONArray("steps")
            for (si in 0 until st.length()) {
                val s = st.getJSONObject(si)
                val m = s.getJSONObject("maneuver")
                val loc = m.getJSONArray("location")
                val mlon = loc.getDouble(0)
                val mlat = loc.getDouble(1)

                // Le point de manoeuvre fait partie de la géométrie : on le retrouve en avançant.
                var best = from
                var bd = Double.MAX_VALUE
                for (i in from until n) {
                    val d = Geo.dist(lat[i], lon[i], mlat, mlon)
                    if (d < bd) {
                        bd = d
                        best = i
                    }
                    if (d < 1.0) break
                }
                from = best
                steps.add(
                    Step(
                        best,
                        m.optString("type", ""),
                        m.optString("modifier", ""),
                        s.optString("name", "")
                    )
                )
            }
        }
        return RouteData(lat, lon, cum, steps, r.optDouble("duration", 0.0))
    }
}

object Words {
    fun arrowRotation(modifier: String): Float = when (modifier) {
        "left" -> -90f
        "right" -> 90f
        "slight left" -> -45f
        "slight right" -> 45f
        "sharp left" -> -135f
        "sharp right" -> 135f
        "uturn" -> 180f
        else -> 0f
    }

    fun instruction(s: Step): String {
        val on = if (s.name.isNotEmpty()) " sur ${s.name}" else ""
        val dir = when (s.modifier) {
            "left" -> "à gauche"
            "right" -> "à droite"
            "slight left" -> "légèrement à gauche"
            "slight right" -> "légèrement à droite"
            "sharp left" -> "franchement à gauche"
            "sharp right" -> "franchement à droite"
            "straight" -> "tout droit"
            else -> ""
        }
        return when (s.type) {
            "arrive" -> "Vous êtes arrivé"
            "roundabout", "rotary", "roundabout turn" -> "Au rond-point, prenez la sortie$on"
            "merge" -> "Insérez-vous$on"
            "on ramp", "off ramp" -> "Prenez la bretelle$on"
            "fork" -> if (dir.isEmpty()) "À la bifurcation$on" else "À la bifurcation, serrez $dir$on"
            else -> when {
                s.modifier == "uturn" -> "Faites demi-tour$on"
                dir.isEmpty() || s.modifier == "straight" -> "Continuez tout droit$on"
                else -> "Tournez $dir$on"
            }
        }
    }
}
