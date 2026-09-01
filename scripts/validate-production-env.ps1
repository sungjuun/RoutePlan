param(
    [string]$Path = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.production')
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw "Production environment file not found: $Path"
}

$values = @{}
foreach ($rawLine in Get-Content -LiteralPath $Path) {
    $line = $rawLine.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith('#')) { continue }
    $separator = $line.IndexOf('=')
    if ($separator -lt 1) { throw "Invalid environment line (expected NAME=value)." }
    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    $values[$name] = $value
}

function Require-Value([string]$Name) {
    if (-not $values.ContainsKey($Name) -or [string]::IsNullOrWhiteSpace($values[$Name])) {
        throw "$Name is required."
    }
    return [string]$values[$Name]
}

function Reject-Placeholder([string]$Name, [string]$Value) {
    if ($Value -match '(?i)example\.com|replace-me|replace-with|your-github|change-me|changeme') {
        throw "$Name still contains an example placeholder."
    }
}

$domain = Require-Value 'ROUTEPLAN_DOMAIN'
if ($domain -notmatch '^(?=.{4,253}$)([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$') {
    throw 'ROUTEPLAN_DOMAIN must be a hostname without a scheme, port, or path.'
}
Reject-Placeholder 'ROUTEPLAN_DOMAIN' $domain

$publicUrl = Require-Value 'ROUTEPLAN_PUBLIC_URL'
if ($publicUrl -ne "https://$domain") { throw 'ROUTEPLAN_PUBLIC_URL must exactly match https://ROUTEPLAN_DOMAIN.' }

$tlsEmail = Require-Value 'TLS_EMAIL'
if ($tlsEmail -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') { throw 'TLS_EMAIL must be a valid email address.' }
Reject-Placeholder 'TLS_EMAIL' $tlsEmail

$imagePrefix = Require-Value 'ROUTEPLAN_IMAGE_PREFIX'
if ($imagePrefix -notmatch '^ghcr\.io/[a-z0-9._-]+/[a-z0-9._/-]+$') {
    throw 'ROUTEPLAN_IMAGE_PREFIX must be a lowercase ghcr.io image prefix.'
}
Reject-Placeholder 'ROUTEPLAN_IMAGE_PREFIX' $imagePrefix

$databasePassword = Require-Value 'POSTGRES_PASSWORD'
if ($databasePassword.Length -lt 24) { throw 'POSTGRES_PASSWORD must contain at least 24 characters.' }
Reject-Placeholder 'POSTGRES_PASSWORD' $databasePassword

if ((Require-Value 'ROUTEPLAN_SESSION_COOKIE_SECURE').ToLowerInvariant() -ne 'true') {
    throw 'ROUTEPLAN_SESSION_COOKIE_SECURE must be true in production.'
}
if ((Require-Value 'ROUTEPLAN_AUTH_MAIL_MODE').ToUpperInvariant() -ne 'SMTP') {
    throw 'ROUTEPLAN_AUTH_MAIL_MODE must be SMTP in production.'
}
if ((Require-Value 'ROUTEPLAN_AUTH_TRUSTED_PROXIES') -ne '172.30.241.0/24') {
    throw 'ROUTEPLAN_AUTH_TRUSTED_PROXIES must match the isolated production app network.'
}

$smtpHost = Require-Value 'SMTP_HOST'
$smtpFrom = Require-Value 'SMTP_FROM'
Reject-Placeholder 'SMTP_HOST' $smtpHost
Reject-Placeholder 'SMTP_FROM' $smtpFrom
$smtpPort = 0
if (-not [int]::TryParse((Require-Value 'SMTP_PORT'), [ref]$smtpPort) -or $smtpPort -lt 1 -or $smtpPort -gt 65535) {
    throw 'SMTP_PORT must be between 1 and 65535.'
}
$smtpStartTls = (Require-Value 'SMTP_STARTTLS').ToLowerInvariant()
$smtpSsl = (Require-Value 'SMTP_SSL').ToLowerInvariant()
if ($smtpStartTls -notin @('true', 'false')) { throw 'SMTP_STARTTLS must be true or false.' }
if ($smtpSsl -notin @('true', 'false')) { throw 'SMTP_SSL must be true or false.' }
if ($smtpStartTls -ne 'true' -and $smtpSsl -ne 'true') { throw 'Production SMTP must enable STARTTLS or SSL.' }
if ((Require-Value 'SMTP_AUTH').ToLowerInvariant() -eq 'true') {
    foreach ($name in @('SMTP_USERNAME', 'SMTP_PASSWORD')) {
        $smtpSecret = Require-Value $name
        Reject-Placeholder $name $smtpSecret
    }
}

$grafanaUser = Require-Value 'GRAFANA_ADMIN_USER'
if ($grafanaUser -notmatch '^[A-Za-z0-9._-]{3,64}$') {
    throw 'GRAFANA_ADMIN_USER must contain 3-64 safe username characters.'
}
$grafanaPassword = Require-Value 'GRAFANA_ADMIN_PASSWORD'
if ($grafanaPassword.Length -lt 20) { throw 'GRAFANA_ADMIN_PASSWORD must contain at least 20 characters.' }
Reject-Placeholder 'GRAFANA_ADMIN_PASSWORD' $grafanaPassword
$alertEmail = Require-Value 'ROUTEPLAN_ALERT_EMAIL_TO'
if ($alertEmail -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') {
    throw 'ROUTEPLAN_ALERT_EMAIL_TO must be one valid email address.'
}
Reject-Placeholder 'ROUTEPLAN_ALERT_EMAIL_TO' $alertEmail

$placeProvider = (Require-Value 'ROUTEPLAN_PLACE_PROVIDER').ToUpperInvariant()
$routeProvider = (Require-Value 'ROUTEPLAN_ROUTE_PROVIDER').ToUpperInvariant()
$aiProvider = (Require-Value 'ROUTEPLAN_AI_PROVIDER').ToUpperInvariant()
if ($placeProvider -eq 'GOOGLE' -or $routeProvider -eq 'GOOGLE') {
    Reject-Placeholder 'GOOGLE_MAPS_API_KEY' (Require-Value 'GOOGLE_MAPS_API_KEY')
}
if ($routeProvider -eq 'GOOGLE') {
    Reject-Placeholder 'GOOGLE_MAPS_BROWSER_KEY' (Require-Value 'GOOGLE_MAPS_BROWSER_KEY')
}
if ($aiProvider -eq 'OPENAI') {
    Reject-Placeholder 'OPENAI_API_KEY' (Require-Value 'OPENAI_API_KEY')
}

Write-Output 'Production environment validation passed. Secret values were not printed.'
