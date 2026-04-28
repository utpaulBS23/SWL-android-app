# Geofence Position Checking Implementation

## Overview
Your app now automatically checks the user's position inside the geofence every 5 minutes. If the user moves outside the geofence, the app calls the `SaveOutAreaNotification` API to report this event.

## Components Implemented

### 1. **GeofenceManager.kt** - Geofence Utility Class
Location: `app/src/main/java/com/phq/swl/pioms/domain/GeofenceManager.kt`

**Key Functions:**
- `calculateDistance()` - Calculates distance between two geographic points using Haversine formula
- `isPointInPolygon()` - Uses ray casting algorithm to check if a point is inside a geofence polygon
- `isInsideGeofence()` - Main function to determine if current location is inside the geofence
- `getGeofenceCentroid()` - Calculates the center point of the geofence polygon

**Data Models:**
```kotlin
data class GeofenceCoordinate(
    val coordinateID: Int,
    val latitude: Double,
    val longitude: Double
)

data class GeofenceData(
    val geofenceID: Int,
    val geofenceName: String,
    val coordinates: List<GeofenceCoordinate>,
    val radiusMeters: Double
)
```

### 2. **LocationHistoryForegroundService.kt** - Updated Service
Enhanced with geofence checking logic:

**New Properties:**
- `lastGeofenceNotificationAtMillis` - Throttles notifications (one per 2 minutes)
- `cachedGeofenceData` - Caches geofence data to avoid repeated API calls
- `lastGeofenceCheckStatus` - Tracks previous in/out status to detect state changes

**New Methods:**
- `checkGeofence()` - Checks if location is inside geofence and triggers notification if outside
- `loadGeofenceData()` - Fetches geofence coordinates from the API and caches them
- `sendOutAreaNotification()` - Sends "Out" notification to the backend
- `httpGetJson()` - HTTP GET helper for fetching geofence data

**Workflow:**
1. Location update received (every 5 minutes)
2. Current location sent to backend via `insertAgentLocationHistory`
3. Geofence data fetched (cached after first fetch)
4. Current position checked against geofence polygon
5. If outside and status changed, notification sent via `SaveOutAreaNotification`
6. Notifications throttled to prevent spam (max 1 per 2 minutes)

## API Endpoints Used

### Fetching Geofence Data
```
GET /api/Users/GetAgentGeofenceCoOrdinateById?id={agentId}
Authorization: Bearer {token}

Response:
{
  "statusCode": 1,
  "responseObj": {
    "geofenceID": 123,
    "geofenceName": "Work Area",
    "radiusMeters": 100.0,
    "coordinates": [
      { "coordinateID": 1, "latitude": 23.8103, "longitude": 90.4441 },
      { "coordinateID": 2, "latitude": 23.8105, "longitude": 90.4450 },
      ...
    ]
  }
}
```

### Sending Out-of-Area Notification
```
POST /api/NotificationArea/SaveOutAreaNotification
Authorization: Bearer {token}
Content-Type: application/json

{
  "requestObj": {
    "agentID": "USER123",
    "notificationType": "Out",
    "notificationDate": "2024-01-15T14:30:45",
    "assignedGeofenceID": 123,
    "locationLat": 23.8104,
    "locationLong": 90.4445
  }
}
```

## How It Works

### Every 5 Minutes:
1. **Location Capture**: Foreground service captures current GPS location
2. **History Log**: Location is logged to backend via `insertAgentLocationHistory`
3. **Geofence Check**: 
   - If first time, fetch geofence coordinates from backend
   - Use ray casting algorithm to check if point is inside polygon
4. **Status Comparison**: Compare current status with previous status
5. **Notification**: If changed from inside to outside, send notification API call

### Throttling:
- **Location History**: Sent every 5 minutes
- **Geofence Notifications**: Sent at most once every 2 minutes (prevents spam from continuous out-of-bounds state)
- **Geofence Cache**: Loaded once and reused until service restarts

## Algorithm Details

### Ray Casting Algorithm (Point in Polygon)
The algorithm determines if a point lies inside a polygon by:
1. Drawing an imaginary ray from the point to infinity
2. Counting how many polygon edges it crosses
3. If odd number of crossings → point is inside
4. If even number of crossings → point is outside

This is efficient and works for any polygon shape (convex or concave).

## Data Storage
- **Cached in Memory**: Geofence data is cached in service memory (`cachedGeofenceData`)
- **Shared Preferences**: Auth token, user ID fetched from SharedPreferences
- **Backend**: All notifications logged to server database

## Error Handling
- **Missing Permissions**: Service stops if location permissions not granted
- **API Failures**: Errors logged but don't crash the service
- **Empty Geofence Data**: Falls back to "inside" status if no coordinates available
- **Network Errors**: Caught and logged; next check will retry

## Testing Checklist

1. **Verify Service Starts**: Check notification appears when service starts
2. **Test Location Updates**: Move location and confirm every 5 minutes sends data
3. **Test Geofence Loading**: First location check should load geofence from API
4. **Test In-Boundary**: Stay inside geofence, verify no notifications sent
5. **Test Out-of-Boundary**: Move outside geofence, verify notification sent once
6. **Test Throttling**: Move in/out repeatedly, verify max 1 notification per 2 minutes
7. **Test Cache**: Restart service, verify geofence reloaded

## Configuration Options

To adjust behavior, modify these constants in the service:

```kotlin
// Location update interval
LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5 * 60 * 1000L)

// Geofence notification throttle
if (now - lastGeofenceNotificationAtMillis >= 2 * 60 * 1000) // Change 2 to desired minutes
```

## Important Notes

- Geofence coordinates are parsed from a polygon (not a simple circle)
- Service runs in foreground with persistent notification
- Location tracking requires `ACCESS_FINE_LOCATION` permission
- All network calls are made from a coroutine in `Dispatchers.IO` thread
- Service survives app background/death scenarios with `START_STICKY` flag
