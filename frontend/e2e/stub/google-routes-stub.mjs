import { createServer } from 'node:http'

const port = 8090
let requestCount = 0
let elementCount = 0
let activeRequests = 0
let maxConcurrentRequests = 0
let delayMs = 120
let responseStatus = 200
let requests = []

function send(response, status, value) {
  const body = typeof value === 'string' ? value : JSON.stringify(value)
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8' })
  response.end(body)
}

async function readJson(request) {
  const chunks = []
  for await (const chunk of request) chunks.push(chunk)
  if (chunks.length === 0) return {}
  return JSON.parse(Buffer.concat(chunks).toString('utf8'))
}

function latLng(waypoint) {
  return waypoint?.waypoint?.location?.latLng ?? { latitude: 0, longitude: 0 }
}

function same(left, right) {
  return Number(left.latitude) === Number(right.latitude)
    && Number(left.longitude) === Number(right.longitude)
}

function leg(origin, destination, departureTime) {
  const latitudeMeters = (Number(origin.latitude) - Number(destination.latitude)) * 111_000
  const longitudeMeters = (Number(origin.longitude) - Number(destination.longitude)) * 91_000
  const direct = Math.hypot(latitudeMeters, longitudeMeters)
  const distanceMeters = Math.max(120, Math.round(direct * 1.18))
  const hour = departureTime ? new Date(departureTime).getUTCHours() : 0
  const directional = Math.abs(Math.round(Number(origin.latitude) * 1000
    + Number(destination.longitude) * 1000)) % 4
  const trafficSeconds = departureTime ? ((hour + directional) % 5) * 45 : 0
  const durationSeconds = Math.max(60, Math.round(distanceMeters / 8.5) + 90 + trafficSeconds)
  return { distanceMeters, duration: `${durationSeconds}s` }
}

function matrix(body) {
  const origins = Array.isArray(body.origins) ? body.origins : []
  const destinations = Array.isArray(body.destinations) ? body.destinations : []
  const result = []
  for (let originIndex = 0; originIndex < origins.length; originIndex += 1) {
    for (let destinationIndex = 0; destinationIndex < destinations.length; destinationIndex += 1) {
      const origin = latLng(origins[originIndex])
      const destination = latLng(destinations[destinationIndex])
      const value = {
        originIndex,
        destinationIndex,
        status: { code: 0 },
        condition: 'ROUTE_EXISTS',
      }
      if (!same(origin, destination)) Object.assign(value, leg(origin, destination, body.departureTime))
      result.push(value)
    }
  }
  return result
}

function state() {
  return {
    requestCount,
    elementCount,
    activeRequests,
    maxConcurrentRequests,
    delayMs,
    responseStatus,
    requests,
  }
}

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url ?? '/', 'http://localhost')
    if (request.method === 'GET' && url.pathname === '/health') {
      send(response, 200, { status: 'UP' })
      return
    }
    if (request.method === 'GET' && url.pathname === '/__admin/state') {
      send(response, 200, state())
      return
    }
    if (request.method === 'POST' && url.pathname === '/__admin/reset') {
      requestCount = 0
      elementCount = 0
      activeRequests = 0
      maxConcurrentRequests = 0
      requests = []
      delayMs = 120
      responseStatus = 200
      send(response, 200, state())
      return
    }
    if (request.method === 'POST' && url.pathname === '/__admin/config') {
      const config = await readJson(request)
      if (Number.isInteger(config.delayMs) && config.delayMs >= 0 && config.delayMs <= 5000) delayMs = config.delayMs
      if (Number.isInteger(config.responseStatus) && config.responseStatus >= 200 && config.responseStatus <= 599) responseStatus = config.responseStatus
      send(response, 200, state())
      return
    }
    if (request.method !== 'POST' || url.pathname !== '/distanceMatrix/v2:computeRouteMatrix') {
      send(response, 404, { error: 'not_found' })
      return
    }

    const body = await readJson(request)
    const origins = Array.isArray(body.origins) ? body.origins.length : 0
    const destinations = Array.isArray(body.destinations) ? body.destinations.length : 0
    const elements = origins * destinations
    requestCount += 1
    elementCount += elements
    activeRequests += 1
    maxConcurrentRequests = Math.max(maxConcurrentRequests, activeRequests)
    requests.push({
      travelMode: body.travelMode ?? null,
      routingPreference: body.routingPreference ?? null,
      departureTime: body.departureTime ?? null,
      elements,
    })
    await new Promise((resolvePromise) => setTimeout(resolvePromise, delayMs))
    activeRequests -= 1
    if (responseStatus !== 200) {
      send(response, responseStatus, { error: { status: 'UNAVAILABLE', message: 'synthetic V25 stub failure' } })
      return
    }
    send(response, 200, matrix(body))
  } catch (error) {
    activeRequests = Math.max(0, activeRequests - 1)
    send(response, 500, { error: error instanceof Error ? error.message : String(error) })
  }
})

server.listen(port, '0.0.0.0')
