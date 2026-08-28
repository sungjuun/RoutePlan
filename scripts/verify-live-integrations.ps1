param([switch]$IncludeGoogle)
# Read-only public-location probes. -IncludeGoogle makes at most five billable API requests.
# Keys, raw provider payloads, and project identifiers are never printed or saved.
$ErrorActionPreference = 'Stop'
$routeRepository = Split-Path -Parent $PSScriptRoot
$routeReport = [System.Collections.Generic.List[object]]::new()

function Invoke-RouteProbe {
    param([string]$Name, [string]$Uri, [string]$Method = 'GET', [hashtable]$Headers = @{}, [object]$Body = $null)
    $clock = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $options = @{ Uri = $Uri; Method = $Method; Headers = $Headers; TimeoutSec = 20; SkipHttpErrorCheck = $true }
        if ($null -ne $Body) { $options.Body = $Body | ConvertTo-Json -Depth 15 -Compress; $options.ContentType = 'application/json; charset=utf-8' }
        $response = Invoke-WebRequest @options
        $payload = $response.Content | ConvertFrom-Json
        $status = [int]$response.StatusCode
        $reason = ''
        if ($status -ge 400) {
            $reason = [string]$payload.error.status
            foreach ($item in $payload.error.details) {
                if ($item.reason -match '^[A-Z_]+$') { $reason = [string]$item.reason; break }
            }
        }
        $routeReport.Add([ordered]@{ probe = $Name; httpStatus = $status; latencyMs = $clock.ElapsedMilliseconds; reason = $reason })
        if ($status -ge 200 -and $status -lt 300) { return $payload }
    } catch {
        $routeReport.Add([ordered]@{ probe = $Name; httpStatus = 0; latencyMs = $clock.ElapsedMilliseconds; reason = 'NETWORK_OR_INVALID_JSON' })
    }
    return $null
}

$forecast = Invoke-RouteProbe -Name 'Open-Meteo Seoul' -Uri 'https://api.open-meteo.com/v1/forecast?latitude=37.57&longitude=126.98&daily=weather_code,precipitation_probability_max&timezone=auto&forecast_days=16'
if ($forecast) { $routeReport.Add([ordered]@{ validation = 'weather'; timezone = $forecast.timezone; forecastDays = @($forecast.daily.time).Count }) }

if ($IncludeGoogle) {
    Push-Location $routeRepository
    try {
        $routeConfigText = & docker compose config --format json 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'config unavailable' }
        $routeConfig = $routeConfigText | ConvertFrom-Json
        $routeServerKey = [string]$routeConfig.services.backend.environment.GOOGLE_MAPS_API_KEY
        $routeReport.Add([ordered]@{ browserKeyConfigured = -not [string]::IsNullOrWhiteSpace($routeConfig.services.backend.environment.GOOGLE_MAPS_BROWSER_KEY); routeProvider = $routeConfig.services.backend.environment.ROUTEPLAN_ROUTE_PROVIDER })
        if ([string]::IsNullOrWhiteSpace($routeServerKey)) { throw 'key unavailable' }
        $headers = @{ 'X-Goog-Api-Key' = $routeServerKey; 'X-Goog-FieldMask' = 'places.id,places.displayName,places.formattedAddress,places.location,places.primaryType' }
        $search = Invoke-RouteProbe -Name 'Google Text Search Pro (1 request)' -Uri 'https://places.googleapis.com/v1/places:searchText' -Method POST -Headers $headers -Body @{ textQuery = 'Gyeongbokgung Palace Seoul'; languageCode = 'ko'; pageSize = 1 }
        if ($search -and @($search.places).Count -gt 0) {
            $headers['X-Goog-FieldMask'] = 'id,regularOpeningHours'
            $details = Invoke-RouteProbe -Name 'Google Place Details Enterprise (1 request)' -Uri ('https://places.googleapis.com/v1/places/' + $search.places[0].id + '?languageCode=ko') -Headers $headers
            if ($details) { $routeReport.Add([ordered]@{ validation = 'openingHours'; periods = @($details.regularOpeningHours.periods).Count; weekdayDescriptions = @($details.regularOpeningHours.weekdayDescriptions).Count }) }
        }
        $origin = @{ location = @{ latLng = @{ latitude = 35.681236; longitude = 139.767125 } } }
        $destination = @{ location = @{ latLng = @{ latitude = 35.6749; longitude = 139.7600 } } }
        $headers['X-Goog-FieldMask'] = 'originIndex,destinationIndex,status,condition,distanceMeters,duration'
        $matrix = Invoke-RouteProbe -Name 'Google Tokyo walking matrix (1 element)' -Uri 'https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix' -Method POST -Headers $headers -Body @{ origins = @(@{ waypoint = $origin }); destinations = @(@{ waypoint = $destination }); travelMode = 'WALK' }
        if ($matrix) { $routeReport.Add([ordered]@{ validation = 'walkingMatrix'; condition = $matrix[0].condition; distanceMeters = $matrix[0].distanceMeters; duration = $matrix[0].duration }) }
        $headers['X-Goog-FieldMask'] = 'routes.polyline.encodedPolyline'
        $geometry = Invoke-RouteProbe -Name 'Google Tokyo road geometry (1 request)' -Uri 'https://routes.googleapis.com/directions/v2:computeRoutes' -Method POST -Headers $headers -Body @{ origin = $origin; destination = $destination; travelMode = 'WALK'; languageCode = 'ko'; polylineQuality = 'OVERVIEW' }
        if ($geometry) { $routeReport.Add([ordered]@{ validation = 'roadGeometry'; encodedPolylinePresent = -not [string]::IsNullOrWhiteSpace($geometry.routes[0].polyline.encodedPolyline) }) }
        $headers['X-Goog-FieldMask'] = 'originIndex,destinationIndex,status,condition,distanceMeters,duration'
        $seoulOrigin = @{ location = @{ latLng = @{ latitude = 37.5547; longitude = 126.9707 } } }
        $seoulDestination = @{ location = @{ latLng = @{ latitude = 37.5796; longitude = 126.9770 } } }
        $transit = Invoke-RouteProbe -Name 'Google Seoul transit (1 element, tomorrow 09:00 KST)' -Uri 'https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix' -Method POST -Headers $headers -Body @{ origins = @(@{ waypoint = $seoulOrigin }); destinations = @(@{ waypoint = $seoulDestination }); travelMode = 'TRANSIT'; departureTime = [DateTimeOffset]::UtcNow.AddHours(9).Date.AddDays(1).ToString('yyyy-MM-dd') + 'T00:00:00Z' }
        if ($transit) { $routeReport.Add([ordered]@{ validation = 'datedTransit'; condition = $transit[0].condition; duration = $transit[0].duration; elementStatus = $transit[0].status.code }) }
    } catch { $routeReport.Add([ordered]@{ probe = 'Google configuration'; reason = 'CONFIGURATION_NOT_AVAILABLE' }) }
    finally { Pop-Location; $routeServerKey = $null; $routeConfig = $null; $routeConfigText = $null; $headers = $null }
}
$routeReport | ConvertTo-Json -Depth 4
