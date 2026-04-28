package com.phq.swl.pioms.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.phq.swl.pioms.R
import com.phq.swl.pioms.data.SettingsStore
import com.phq.swl.pioms.data.network.ApiEndpoints
import com.phq.swl.pioms.data.network.ApiHttpLogger
import com.phq.swl.pioms.domain.GeofenceCoordinate
import com.phq.swl.pioms.domain.GeofenceData
import com.phq.swl.pioms.domain.GeofenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

class LocationHistoryForegroundService : Service() {
    companion object {
        private const val ACTION_START = "com.phq.swl.pioms.service.LocationHistoryForegroundService.START"
        private const val ACTION_STOP = "com.phq.swl.pioms.service.LocationHistoryForegroundService.STOP"
        private const val CHANNEL_ID = "pioms_location_history"
        private const val NOTIFICATION_ID = 1005

        fun start(context: Context) {
            val intent = Intent(context, LocationHistoryForegroundService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationHistoryForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val settingsStore by lazy { SettingsStore(this) }

    private var lastSentAtMillis: Long = 0L
    private var loopStarted: Boolean = false
    private var cachedGeofenceData: GeofenceData? = null
    private var lastGeofenceCheckStatus: Boolean = true // true = inside, false = outside
    private var geofenceDataLoadingInProgress: Boolean = false

    private val callback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val now = System.currentTimeMillis()
                if (now - lastSentAtMillis < 5 * 60 * 1000L) return
                lastSentAtMillis = now
                serviceScope.launch { sendLocation(loc.latitude, loc.longitude) }
            }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startAsForeground()
                requestUpdates()
                startLoopIfNeeded()
                return START_STICKY
            }
            else -> return START_STICKY
        }
    }

    override fun onDestroy() {
        runCatching { fusedClient.removeLocationUpdates(callback) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = mgr.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "PIOMS Location",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.pioms_logo)
            .setContentTitle("PIOMS running")
            .setContentText("Location tracking is active")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun requestUpdates() {
        val request =
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5 * 60 * 1000L)
                .setMinUpdateIntervalMillis(5 * 60 * 1000L)
                .setWaitForAccurateLocation(false)
                .build()
        val fine = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            stopSelf()
            return
        }
        runCatching { fusedClient.requestLocationUpdates(request, callback, mainLooper) }
    }

    private fun startLoopIfNeeded() {
        if (loopStarted) return
        loopStarted = true
        serviceScope.launch {
            while (isActive) {
                val loc =
                    runCatching { fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() }
                        .getOrNull()
                if (loc != null) {
                    lastSentAtMillis = 0L
                    sendLocation(loc.latitude, loc.longitude)
                    lastSentAtMillis = System.currentTimeMillis()
                }
                delay(5 * 60 * 1000L)
            }
        }
    }

    private suspend fun sendLocation(
        latitude: Double,
        longitude: Double,
    ) {
        val token = settingsStore.get("auth_token")?.trim().orEmpty()
        val userId = settingsStore.get("logged_in_user_id")?.trim().orEmpty()
        val userAutoId = settingsStore.get("user_auto_id")?.trim().orEmpty()
        val userAutoIdInt = userAutoId.toIntOrNull() ?: run {
            println("DEBUG: Failed to parse userAutoId")
            return
        }
        if (token.isEmpty() || userId.isEmpty() || userAutoId.isEmpty()) {
            println("DEBUG: Missing credentials - token: ${token.isNotEmpty()}, userId: $userId, userAutoId: $userAutoId")
            return
        }

        val historyDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        val requestObj =
            JSONObject()
                .put("ComplainID", 0)
                .put("UserID", userId)
                .put("HistoryDate", historyDate)
                .put("Latitude", latitude)
                .put("Longitude", longitude)
                .put("UserAutoID", userAutoId)

        val body = JSONObject().put("RequestObj", requestObj)
        println("DEBUG: Sending location - Lat: $latitude, Lon: $longitude")
        httpPostJson(ApiEndpoints.Authenticate.insertAgentLocationHistory, token, body)

        // Check geofence status
        checkGeofence(latitude, longitude, token, userId, userAutoIdInt, historyDate)
    }

    private suspend fun checkGeofence(
        latitude: Double,
        longitude: Double,
        token: String,
        userId: String,
        userAutoIdInt: Int,
        notificationDate: String,
    ) {
        try {
            // Load geofence data if not cached (only once)
            if (cachedGeofenceData == null && !geofenceDataLoadingInProgress) {
                println("DEBUG: First time - Loading geofence data for agent $userAutoIdInt")
                geofenceDataLoadingInProgress = true
                loadGeofenceData(token, userAutoIdInt)
                geofenceDataLoadingInProgress = false
            } else if (cachedGeofenceData == null && geofenceDataLoadingInProgress) {
                println("DEBUG: Geofence data loading already in progress, waiting...")
                // Wait for current load to complete
                var waitCount = 0
                while (geofenceDataLoadingInProgress && waitCount < 30) {
                    delay(100)
                    waitCount++
                }
            }

            val geofenceData = cachedGeofenceData
            if (geofenceData == null) {
                println("DEBUG: No geofence data available after load attempt")
                return
            }

            println("DEBUG: Using cached geofence data - ID: ${geofenceData.geofenceID}, Name: ${geofenceData.geofenceName}")

            val isInside = GeofenceManager.isInsideGeofence(latitude, longitude, geofenceData)
            println("DEBUG: Geofence check - isInside: $isInside, lastStatus: $lastGeofenceCheckStatus")

            // Check server's last status to avoid duplicate notifications
            val lastServerStatus = getLastNotificationStatus(token, userId)
            println("DEBUG: Last server status: $lastServerStatus")

            // Send "Out" notification if user moved outside and was previously inside or never notified
            if (!isInside && (lastServerStatus == null || lastServerStatus == "In")) {
                println("DEBUG: User outside geofence! Sending Out notification")
                sendOutAreaNotification(token, userId, latitude, longitude, notificationDate, geofenceData.geofenceID)
            } else if (!isInside && lastServerStatus == "Out") {
                println("DEBUG: Already notified as Out, skipping duplicate notification")
            }

            // Send "In" notification if user came back inside and was previously outside
            if (isInside && lastServerStatus == "Out") {
                println("DEBUG: User back inside geofence! Sending In notification")
                sendInAreaNotification(token, userId, latitude, longitude, notificationDate, geofenceData.geofenceID)
            }

            lastGeofenceCheckStatus = isInside
        } catch (e: Exception) {
            println("DEBUG: Error in checkGeofence: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun getLastNotificationStatus(
        token: String,
        userId: String,
    ): String? {
        return try {
            val url = ApiEndpoints.NotificationArea.getLastInOutTypeByAgentID(userId)
            val response = httpGetJson(url, token)

            if (response.optInt("statusCode") == 1) {
                val status = response.optString("responseObj", "").trim()
                println("DEBUG: Got last status from server: $status")
                status.takeIf { it.isNotEmpty() }
            } else {
                println("DEBUG: Failed to get last status - statusCode: ${response.optInt("statusCode")}")
                null
            }
        } catch (e: Exception) {
            println("DEBUG: Error fetching last notification status: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private suspend fun sendInAreaNotification(
        token: String,
        userId: String,
        latitude: Double,
        longitude: Double,
        notificationDate: String,
        geofenceID: Int,
    ) {
        try {
            val requestObj =
                JSONObject()
                    .put("agentID", userId)
                    .put("notificationType", "In")
                    .put("notificationDate", notificationDate)
                    .put("assignedGeofenceID", geofenceID)
                    .put("locationLat", latitude)
                    .put("locationLong", longitude)

            val body = JSONObject().put("requestObj", requestObj)
            println("DEBUG: Sending In-area notification")
            httpPostJson(ApiEndpoints.NotificationArea.saveOutAreaNotification, token, body)
        } catch (e: Exception) {
            println("DEBUG: Error sending In notification: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun loadGeofenceData(
        token: String,
        agentId: Int,
    ) {
        try {
            val url = ApiEndpoints.Users.getAgentGeofenceCoOrdinateById(agentId)
            println("DEBUG: [API CALL] Fetching geofence data from: $url")
            val response = httpGetJson(url, token)

            if (response.optInt("statusCode") == 1) {
                val responseObj = response.optJSONObject("responseObj") ?: run {
                    println("DEBUG: No responseObj in geofence API response")
                    return
                }
                val geofenceID = responseObj.optInt("geofenceID", 0)
                val geofenceName = responseObj.optString("geofenceName", "")
                val radiusMeters = responseObj.optDouble("radiusMeters", 100.0)

                // Parse coordinates array
                val coordinatesArray = responseObj.optJSONArray("coordinates") ?: JSONArray()
                val coordinates = mutableListOf<GeofenceCoordinate>()

                for (i in 0 until coordinatesArray.length()) {
                    val coordObj = coordinatesArray.getJSONObject(i)
                    coordinates.add(
                        GeofenceCoordinate(
                            coordinateID = coordObj.optInt("coordinateID", 0),
                            latitude = coordObj.optDouble("latitude", 0.0),
                            longitude = coordObj.optDouble("longitude", 0.0),
                        ),
                    )
                }

                cachedGeofenceData = GeofenceData(
                    geofenceID = geofenceID,
                    geofenceName = geofenceName,
                    coordinates = coordinates,
                    radiusMeters = radiusMeters,
                )
                println("DEBUG: ✓ Geofence data loaded successfully - ID: $geofenceID, Name: $geofenceName, Coords: ${coordinates.size}")
            } else {
                println("DEBUG: Failed to load geofence - statusCode: ${response.optInt("statusCode")}, message: ${response.optString("message")}")
            }
        } catch (e: Exception) {
            println("DEBUG: Error loading geofence data: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun sendOutAreaNotification(
        token: String,
        userId: String,
        latitude: Double,
        longitude: Double,
        notificationDate: String,
        geofenceID: Int,
    ) {
        try {
            val requestObj =
                JSONObject()
                    .put("agentID", userId)
                    .put("notificationType", "Out")
                    .put("notificationDate", notificationDate)
                    .put("assignedGeofenceID", geofenceID)
                    .put("locationLat", latitude)
                    .put("locationLong", longitude)

            val body = JSONObject().put("requestObj", requestObj)
            httpPostJson(ApiEndpoints.NotificationArea.saveOutAreaNotification, token, body)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun httpPostJson(
        url: String,
        token: String,
        body: JSONObject,
    ): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.instanceFollowRedirects = false
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "PIOMS")
        conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val payload = body.toString()
        ApiHttpLogger.logRequest(
            method = "POST",
            url = url,
            headers =
                mapOf(
                    "Accept" to "application/json",
                    "Content-Type" to "application/json",
                    "Authorization" to conn.getRequestProperty("Authorization"),
                ),
            bodyText = payload,
        )
        conn.outputStream.use { it.write(payload.toByteArray()) }

        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.use { String(it.readBytes()) }.orEmpty()
        ApiHttpLogger.logResponse(
            method = "POST",
            url = url,
            statusCode = status,
            bodyText = responseText,
        )
        if (responseText.isBlank()) {
            return JSONObject().put("statusCode", status).put("message", "Empty response")
        }
        return runCatching { JSONObject(responseText) }
            .getOrElse { JSONObject().put("statusCode", status).put("message", responseText) }
    }

    private fun httpGetJson(
        url: String,
        token: String,
    ): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "PIOMS")
        conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        ApiHttpLogger.logRequest(
            method = "GET",
            url = url,
            headers =
                mapOf(
                    "Accept" to "application/json",
                    "Authorization" to conn.getRequestProperty("Authorization"),
                ),
            bodyText = "",
        )

        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.use { String(it.readBytes()) }.orEmpty()
        ApiHttpLogger.logResponse(
            method = "GET",
            url = url,
            statusCode = status,
            bodyText = responseText,
        )
        if (responseText.isBlank()) {
            return JSONObject().put("statusCode", status).put("message", "Empty response")
        }
        return runCatching { JSONObject(responseText) }
            .getOrElse { JSONObject().put("statusCode", status).put("message", responseText) }
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
    }
