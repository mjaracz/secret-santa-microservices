param(
    [string]$BaseUrl = "http://localhost:8090",
    [Parameter(Mandatory = $true)]
    [string]$Email,
    [Parameter(Mandatory = $true)]
    [string]$Password,
    [ValidateRange(1, 100000)]
    [int]$Requests = 100,
    [ValidateRange(0, 10000)]
    [int]$Warmup = 10
)

$ErrorActionPreference = "Stop"
$endpoint = "$($BaseUrl.TrimEnd('/'))/api/auth/sign-in"
$body = @{
    email = $Email
    password = $Password
} | ConvertTo-Json

function Invoke-SignIn {
    $response = Invoke-WebRequest `
        -Uri $endpoint `
        -Method Post `
        -ContentType "application/json" `
        -Body $body `
        -SkipHttpErrorCheck

    if ($response.StatusCode -ne 200) {
        throw "Sign-in failed with HTTP $($response.StatusCode): $($response.Content)"
    }
}

function Get-Percentile {
    param(
        [double[]]$SortedValues,
        [double]$Percentile
    )

    $index = [Math]::Ceiling($Percentile * $SortedValues.Count) - 1
    return $SortedValues[[Math]::Max(0, $index)]
}

Write-Host "Warming up with $Warmup requests..."
for ($index = 0; $index -lt $Warmup; $index++) {
    Invoke-SignIn
}

$durations = [System.Collections.Generic.List[double]]::new()
$total = [System.Diagnostics.Stopwatch]::StartNew()

for ($index = 0; $index -lt $Requests; $index++) {
    $request = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-SignIn
    $request.Stop()
    $durations.Add($request.Elapsed.TotalMilliseconds)
}

$total.Stop()
$sorted = [double[]]($durations | Sort-Object)
$average = ($durations | Measure-Object -Average).Average
$throughput = $Requests / $total.Elapsed.TotalSeconds

[PSCustomObject]@{
    Endpoint = $endpoint
    Requests = $Requests
    "Min ms/request" = [Math]::Round($sorted[0], 3)
    "Average ms/request" = [Math]::Round($average, 3)
    "P50 ms/request" = [Math]::Round((Get-Percentile $sorted 0.50), 3)
    "P95 ms/request" = [Math]::Round((Get-Percentile $sorted 0.95), 3)
    "Max ms/request" = [Math]::Round($sorted[-1], 3)
    "Requests/second" = [Math]::Round($throughput, 3)
} | Format-List
