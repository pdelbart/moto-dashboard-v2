package com.moto.dash

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val io = Executors.newCachedThreadPool()
    private val ui = Handler(Looper.getMainLooper())

    // ----- vues -----
    private lateinit var map: MapView
    private lateinit var destInput: EditText
    private lateinit var searchRow: View
    private lateinit var banner: View
    private lateinit var bannerArrow: TextView
    private lateinit var bannerDist: TextView
    private lateinit var bannerText: TextView
    private lateinit var etaBar: View
    private lateinit var etaMin: TextView
    private lateinit var etaSub: TextView
    private lateinit var tempView: TextView
    private lateinit var speedView: TextView
    private lateinit var gpsView: TextView
    private lateinit var batView: TextView
    private lateinit var btValue: TextView
    private lateinit var btLabel: TextView
    private lateinit var notifCard: View
    private lateinit var notifCount: TextView
    private lateinit var cover: ImageView
    private lateinit var songTitle: TextView
    private lateinit var songArtist: TextView
    private lateinit var songBar: ProgressBar
    private lateinit var songPos: TextView
    private lateinit var songDur: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var adapter: NotifAdapter

    // ----- carte et itinéraire -----
    private var meMarker: Marker? = null
    private var destMarker: Marker? = null
    private var routeLine: Polyline? = null
    private var lastLoc: Location? = null
    private var lastTouch = 0L
    private var route: RouteData? = null
    private var curIdx = 0
    private var destLat = 0.0
    private var destLon = 0.0
    private var lastReroute = 0L
    private var routing = false
    private var lastWeather = 0L

    // ----- musique -----
    private var msm: MediaSessionManager? = null
    private var mediaRegistered = false
    private var controller: MediaController? = null
    private var durMs = 0L

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pickController(list) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlayback()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMeta()
        }

        override fun onSessionDestroyed() {
            controller = null
            updateMeta()
            updatePlayback()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            tickProgress()
            ui.postDelayed(this, 500)
        }
    }

    // ----- localisation -----
    private val locListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onLocation(location)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }
    private var locationStarted = false

    // ----- bluetooth / batterie -----
    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) showBattery(intent)
            else ui.postDelayed({ refreshBt() }, 1500)
        }
    }

    // =====================================================================
    // Cycle de vie
    // =====================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }

        setContentView(R.layout.activity_main)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = WindowCompat.getInsetsController(window, window.decorView)
        wic.hide(WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        bindViews()
        setupMap()
        setupSearch()
        setupMusicButtons()
        setupNotifications()
        ensurePermissions()
        updateMeta()
        updatePlayback()
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, systemReceiver, f, ContextCompat.RECEIVER_EXPORTED)
        refreshBt()
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(systemReceiver)
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        updateNotifAccessUi()
        setupMedia()
        ui.removeCallbacks(ticker)
        ui.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        ui.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        super.onDestroy()
        NotifStore.onChange = null
        try {
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(locListener)
        } catch (e: Exception) {
        }
        try {
            controller?.unregisterCallback(controllerCallback)
        } catch (e: Exception) {
        }
        try {
            if (mediaRegistered) msm?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
        }
        io.shutdownNow()
    }

    private fun bindViews() {
        map = findViewById(R.id.map)
        destInput = findViewById(R.id.destInput)
        searchRow = findViewById(R.id.searchRow)
        banner = findViewById(R.id.banner)
        bannerArrow = findViewById(R.id.bannerArrow)
        bannerDist = findViewById(R.id.bannerDist)
        bannerText = findViewById(R.id.bannerText)
        etaBar = findViewById(R.id.etaBar)
        etaMin = findViewById(R.id.etaMin)
        etaSub = findViewById(R.id.etaSub)
        tempView = findViewById(R.id.tempView)
        speedView = findViewById(R.id.speedView)
        gpsView = findViewById(R.id.gpsView)
        batView = findViewById(R.id.batView)
        btValue = findViewById(R.id.btValue)
        btLabel = findViewById(R.id.btLabel)
        notifCard = findViewById(R.id.notifCard)
        notifCount = findViewById(R.id.notifCount)
        cover = findViewById(R.id.cover)
        songTitle = findViewById(R.id.songTitle)
        songArtist = findViewById(R.id.songArtist)
        songBar = findViewById(R.id.songBar)
        songPos = findViewById(R.id.songPos)
        songDur = findViewById(R.id.songDur)
        btnPlay = findViewById(R.id.btnPlay)
    }

    // =====================================================================
    // Permissions
    // =====================================================================
    private fun ensurePermissions() {
        val need = ArrayList<String>()
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            need.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (need.isEmpty()) {
            startLocation()
        } else {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 42)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) startLocation()
        else gpsView.text = "Position refusée"
        refreshBt()
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    // =====================================================================
    // Carte
    // =====================================================================
    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        map.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        map.controller.setZoom(16.0)
        map.controller.setCenter(GeoPoint(46.6, 2.5))

        val m = Marker(map)
        m.icon = ContextCompat.getDrawable(this, R.drawable.ic_nav)
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        m.setOnMarkerClickListener { _, _ -> true }
        meMarker = m

        // Appui long sur la carte = choisir la destination
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) startRoute(p.latitude, p.longitude)
                return true
            }
        }
        map.overlays.add(MapEventsOverlay(receiver))
        map.overlays.add(m)

        map.setOnTouchListener { _, _ ->
            lastTouch = SystemClock.elapsedRealtime()
            false
        }
        findViewById<View>(R.id.zoomIn).setOnClickListener { map.controller.zoomIn() }
        findViewById<View>(R.id.zoomOut).setOnClickListener { map.controller.zoomOut() }
        findViewById<View>(R.id.btnCancel).setOnClickListener { clearRoute() }
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (locationStarted) return
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener)
            locationStarted = true
            val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (last != null) onLocation(last)
        } catch (e: SecurityException) {
            gpsView.text = "Position refusée"
        } catch (e: Exception) {
            gpsView.text = "GPS indisponible"
        }
    }

    private fun onLocation(l: Location) {
        lastLoc = l
        val kmh = if (l.hasSpeed()) l.speed * 3.6f else 0f
        speedView.text = (if (kmh < 2f) 0 else Math.round(kmh)).toString()
        gpsView.text = "GPS ±" + Math.round(l.accuracy) + " m"

        val gp = GeoPoint(l.latitude, l.longitude)
        meMarker?.position = gp
        if (kmh > 5f && l.hasBearing()) map.mapOrientation = -l.bearing
        if (SystemClock.elapsedRealtime() - lastTouch > 8000) map.controller.setCenter(gp)
        map.invalidate()

        if (route != null) updateRouteProgress(l)
        fetchWeather(l)
    }

    private fun fetchWeather(l: Location) {
        val now = System.currentTimeMillis()
        if (now - lastWeather < 600_000L) return
        lastWeather = now
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m",
            l.latitude, l.longitude
        )
        io.execute {
            try {
                val t = JSONObject(Net.get(url)).getJSONObject("current").getDouble("temperature_2m")
                ui.post { tempView.text = Math.round(t).toString() + "°C" }
            } catch (e: Exception) {
                lastWeather = 0L
            }
        }
    }

    // =====================================================================
    // Recherche de destination et itinéraire
    // =====================================================================
    private fun setupSearch() {
        findViewById<View>(R.id.destGo).setOnClickListener { doSearch() }
        destInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else false
        }
    }

    private fun doSearch() {
        val q = destInput.text.toString().trim()
        if (q.isEmpty()) return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(destInput.windowToken, 0)
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=" +
                URLEncoder.encode(q, "UTF-8")
        io.execute {
            try {
                val arr = JSONArray(Net.get(url))
                val names = ArrayList<String>()
                val lats = ArrayList<Double>()
                val lons = ArrayList<Double>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    names.add(o.getString("display_name"))
                    lats.add(o.getString("lat").toDouble())
                    lons.add(o.getString("lon").toDouble())
                }
                ui.post {
                    if (names.isEmpty()) {
                        toast("Aucun résultat pour « $q »")
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Choisir la destination")
                            .setItems(names.toTypedArray()) { _, which ->
                                startRoute(lats[which], lons[which])
                            }
                            .show()
                    }
                }
            } catch (e: Exception) {
                ui.post { toast("Recherche impossible. Vérifie la connexion internet.") }
            }
        }
    }

    private fun startRoute(lat: Double, lon: Double) {
        val me = lastLoc
        if (me == null) {
            toast("Position GPS pas encore trouvée. Réessaie dans quelques secondes.")
            return
        }
        if (routing) return
        routing = true
        destLat = lat
        destLon = lon
        io.execute {
            try {
                val r = Router.fetch(me.latitude, me.longitude, lat, lon)
                ui.post {
                    routing = false
                    if (r == null) toast("Aucun itinéraire trouvé.") else setRoute(r)
                }
            } catch (e: Exception) {
                ui.post {
                    routing = false
                    toast("Calcul d'itinéraire impossible. Vérifie la connexion internet.")
                }
            }
        }
    }

    private fun setRoute(r: RouteData) {
        route = r
        curIdx = 0

        routeLine?.let { map.overlays.remove(it) }
        destMarker?.let { map.overlays.remove(it) }
        meMarker?.let { map.overlays.remove(it) }

        val pts = ArrayList<GeoPoint>(r.lat.size)
        for (i in r.lat.indices) pts.add(GeoPoint(r.lat[i], r.lon[i]))
        val pl = Polyline()
        pl.setPoints(pts)
        pl.outlinePaint.color = 0xFF3D9BFF.toInt()
        pl.outlinePaint.strokeWidth = 18f
        pl.outlinePaint.strokeCap = Paint.Cap.ROUND
        routeLine = pl
        map.overlays.add(pl)

        val dm = Marker(map)
        dm.position = GeoPoint(r.lat[r.lat.size - 1], r.lon[r.lon.size - 1])
        dm.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        dm.setOnMarkerClickListener { _, _ -> true }
        destMarker = dm
        map.overlays.add(dm)
        meMarker?.let { map.overlays.add(it) }

        searchRow.visibility = View.GONE
        banner.visibility = View.VISIBLE
        etaBar.visibility = View.VISIBLE
        lastLoc?.let { updateRouteProgress(it) }
        map.invalidate()
    }

    private fun clearRoute() {
        route = null
        routeLine?.let { map.overlays.remove(it) }
        destMarker?.let { map.overlays.remove(it) }
        routeLine = null
        destMarker = null
        banner.visibility = View.GONE
        etaBar.visibility = View.GONE
        searchRow.visibility = View.VISIBLE
        map.invalidate()
    }

    private fun updateRouteProgress(l: Location) {
        val r = route ?: return
        val n = r.lat.size

        var best = curIdx
        var bd = Double.MAX_VALUE
        val to = Math.min(n - 1, curIdx + 300)
        for (i in curIdx..to) {
            val d = Geo.dist(r.lat[i], r.lon[i], l.latitude, l.longitude)
            if (d < bd) {
                bd = d
                best = i
            }
        }
        if (bd > 80.0) {
            for (i in 0 until n) {
                val d = Geo.dist(r.lat[i], r.lon[i], l.latitude, l.longitude)
                if (d < bd) {
                    bd = d
                    best = i
                }
            }
        }
        if (bd > 80.0) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastReroute > 20000 && !routing) {
                lastReroute = now
                toast("Hors itinéraire, nouveau calcul...")
                startRoute(destLat, destLon)
            }
            return
        }
        curIdx = best

        val remain = r.total - r.cum[best]
        if (remain < 25.0) {
            toast("Vous êtes arrivé")
            clearRoute()
            return
        }

        // Prochaine manoeuvre
        var next: Step? = null
        for (s in r.steps) {
            if (s.type != "depart" && s.idx > best) {
                next = s
                break
            }
        }
        if (next != null) {
            bannerDist.text = fmtDist(r.cum[next.idx] - r.cum[best])
            bannerText.text = Words.instruction(next)
            bannerArrow.rotation = Words.arrowRotation(next.modifier)
        }

        // Temps et distance restants
        val secs = if (r.total > 0) r.duration * remain / r.total else 0.0
        val mins = Math.round(secs / 60.0).toInt()
        etaMin.text = if (mins >= 60) (mins / 60).toString() + " h " + (mins % 60) + " min" else "$mins min"
        val arrival = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(System.currentTimeMillis() + (secs * 1000).toLong()))
        etaSub.text = String.format(Locale.getDefault(), "%.1f km · %s", remain / 1000.0, arrival)
    }

    private fun fmtDist(m: Double): String =
        if (m >= 1000) String.format(Locale.getDefault(), "%.1f km", m / 1000.0)
        else (Math.round(m / 10.0) * 10).toString() + " m"

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // =====================================================================
    // Notifications
    // =====================================================================
    private fun setupNotifications() {
        adapter = NotifAdapter { n -> NotifStore.dismiss(n.key) }
        val rv = findViewById<RecyclerView>(R.id.notifList)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        NotifStore.onChange = { showNotifs() }
        showNotifs()
        findViewById<View>(R.id.notifGrant).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                toast("Ouvre Réglages > Notifications > Accès aux notifications.")
            }
        }
    }

    private fun showNotifs() {
        val items = NotifStore.items
        adapter.submit(items)
        notifCount.text = items.size.toString()
    }

    private fun hasNotifAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun updateNotifAccessUi() {
        notifCard.visibility = if (hasNotifAccess()) View.GONE else View.VISIBLE
    }

    // =====================================================================
    // Musique (Deezer, Spotify, YouTube Music...)
    // =====================================================================
    private fun setupMusicButtons() {
        findViewById<View>(R.id.btnPrev).setOnClickListener {
            controller?.transportControls?.skipToPrevious()
        }
        findViewById<View>(R.id.btnNext).setOnClickListener {
            controller?.transportControls?.skipToNext()
        }
        btnPlay.setOnClickListener {
            val c = controller
            if (c != null) {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
                else c.transportControls.play()
            }
        }
    }

    private fun setupMedia() {
        if (!hasNotifAccess()) {
            updateMeta()
            return
        }
        try {
            val mgr = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm = mgr
            val comp = ComponentName(this, NotifService::class.java)
            if (!mediaRegistered) {
                mgr.addOnActiveSessionsChangedListener(sessionsListener, comp)
                mediaRegistered = true
            }
            pickController(mgr.getActiveSessions(comp))
        } catch (e: SecurityException) {
            // accès pas encore accordé
        } catch (e: Exception) {
        }
    }

    private fun pickController(list: List<MediaController>?) {
        if (list == null || list.isEmpty()) {
            switchController(null)
            return
        }
        var pick: MediaController? = null
        for (c in list) if (c.packageName == "deezer.android.app") pick = c
        if (pick == null) {
            for (c in list) {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    pick = c
                    break
                }
            }
        }
        switchController(pick ?: list[0])
    }

    private fun switchController(c: MediaController?) {
        val old = controller
        if (old?.sessionToken == c?.sessionToken && old != null) {
            updateMeta()
            updatePlayback()
            return
        }
        try {
            old?.unregisterCallback(controllerCallback)
        } catch (e: Exception) {
        }
        controller = c
        try {
            c?.registerCallback(controllerCallback)
        } catch (e: Exception) {
        }
        updateMeta()
        updatePlayback()
    }

    private fun updateMeta() {
        val c = controller
        if (c == null) {
            songTitle.text = "Aucune musique"
            songArtist.text = if (hasNotifAccess()) "Lance Deezer" else "Autorise l'accès aux notifications"
            cover.setImageDrawable(null)
            durMs = 0L
            return
        }
        val m = c.metadata
        songTitle.text = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "—"
        songArtist.text = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val bmp = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bmp != null) cover.setImageBitmap(bmp) else cover.setImageDrawable(null)
        durMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    }

    private fun updatePlayback() {
        val playing = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
        btnPlay.setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        tickProgress()
    }

    private fun tickProgress() {
        val st = controller?.playbackState
        if (st == null) {
            songBar.progress = 0
            songPos.text = "0:00"
            songDur.text = "0:00"
            return
        }
        var pos = st.position
        if (pos < 0) pos = 0
        if (st.state == PlaybackState.STATE_PLAYING) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        if (durMs > 0) pos = Math.min(Math.max(pos, 0L), durMs)
        songBar.progress = if (durMs > 0) (pos * 1000 / durMs).toInt() else 0
        songPos.text = fmtMs(pos)
        songDur.text = fmtMs(durMs)
    }

    private fun fmtMs(ms: Long): String {
        val s = ms / 1000
        return (s / 60).toString() + ":" + (if (s % 60 < 10) "0" else "") + (s % 60)
    }

    // =====================================================================
    // Batterie et Bluetooth
    // =====================================================================
    private fun showBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) batView.text = (level * 100 / scale).toString() + "%"
    }

    private fun setBt(value: String, label: String) {
        ui.post {
            btValue.text = value
            btLabel.text = label
        }
    }

    private fun btBattery(d: BluetoothDevice): Int = try {
        d.javaClass.getMethod("getBatteryLevel").invoke(d) as Int
    } catch (t: Throwable) {
        -1
    }

    @SuppressLint("MissingPermission")
    private fun refreshBt() {
        val ad: BluetoothAdapter? = try {
            (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        } catch (e: Exception) {
            null
        }
        if (ad == null) {
            setBt("—", "Pas de Bluetooth")
            return
        }
        if (!ad.isEnabled) {
            setBt("OFF", "Bluetooth coupé")
            return
        }
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            setBt("?", "Autorisation requise")
            return
        }
        setBt("ON", "Aucun appareil")

        val found = LinkedHashMap<String, Int>()
        for (profile in intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            try {
                ad.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        try {
                            for (d in proxy.connectedDevices) {
                                found[d.name ?: d.address] = btBattery(d)
                            }
                        } catch (e: Exception) {
                        }
                        try {
                            ad.closeProfileProxy(p, proxy)
                        } catch (e: Exception) {
                        }
                        if (found.isNotEmpty()) {
                            val first = found.entries.first()
                            val v = if (first.value >= 0) first.value.toString() + "%" else "ON"
                            setBt(v, found.keys.joinToString(", "))
                        }
                    }

                    override fun onServiceDisconnected(p: Int) {}
                }, profile)
            } catch (e: Exception) {
            }
        }
    }
}
