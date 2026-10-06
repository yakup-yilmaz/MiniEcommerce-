# ==============================================================================
# E-COMMERCE COMPREHENSIVE ENDPOINT TEST SUITE
# ==============================================================================
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$baseUrl = "http://localhost:8080"

$script:passCount = 0
$script:failCount = 0
$script:results = [System.Collections.Generic.List[PSCustomObject]]::new()

function Test-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [int[]]$ExpectedStatus = @(200)
    )

    $jsonBody = $null
    if ($null -ne $Body) {
        $jsonBody = ($Body | ConvertTo-Json -Depth 10)
    }

    $allHeaders = @{ "Content-Type" = "application/json" }
    foreach ($k in $Headers.Keys) {
        $allHeaders[$k] = $Headers[$k]
    }

    $statusCode = 0
    $responseBody = ""
    $parsedJson = $null

    try {
        $params = @{
            Uri = $Url
            Method = $Method
            Headers = $allHeaders
            ErrorAction = "Stop"
        }
        if ($null -ne $jsonBody) {
            $params["Body"] = [System.Text.Encoding]::UTF8.GetBytes($jsonBody)
        }

        $response = Invoke-WebRequest @params
        $statusCode = [int]$response.StatusCode
        $responseBody = $response.Content
        if ($responseBody) {
            try { $parsedJson = $responseBody | ConvertFrom-Json } catch {}
        }
    }
    catch {
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $responseBody = $reader.ReadToEnd()
            if ($responseBody) {
                try { $parsedJson = $responseBody | ConvertFrom-Json } catch {}
            }
        }
        else {
            $statusCode = 599
            $responseBody = $_.Exception.Message
        }
    }

    $isPassed = $ExpectedStatus -contains $statusCode
    if ($isPassed) {
        $script:passCount++
        Write-Host "  [OK] ($statusCode) $Method $Url - $Name" -ForegroundColor Green
    }
    else {
        $script:failCount++
        Write-Host "  [FAIL] Expected $($ExpectedStatus -join ',') but got ${statusCode}: $Method $Url - $Name" -ForegroundColor Red
        if ($responseBody) {
            Write-Host "         Body: $responseBody" -ForegroundColor DarkGray
        }
    }

    $res = [PSCustomObject]@{
        Name     = $Name
        Method   = $Method
        Url      = $Url
        Expected = ($ExpectedStatus -join ",")
        Actual   = $statusCode
        Passed   = $isPassed
        Data     = $parsedJson
        Raw      = $responseBody
    }
    $script:results.Add($res)
    return ,$res
}

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "            E-COMMERCE API AUTOMATED ENDPOINT VERIFICATION" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

# ------------------------------------------------------------------------------
# 1. AUTHENTICATION & USERS
# ------------------------------------------------------------------------------
Write-Host "`n>>> [1/7] Testing Authentication (Register, Login, Refresh, Admin Role)..." -ForegroundColor Yellow

$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$customerEmail = "test_customer_$timestamp@ecommerce.com"

# Register Customer
$regResult = (Test-Api -Name "Register Customer User" `
    -Method "POST" -Url "$baseUrl/api/auth/register" `
    -Body @{ name = "Musteri Ahmet"; email = $customerEmail; password = "CustomerPassword123*" } `
    -ExpectedStatus @(200, 201))[0]

# Login Admin
$adminLoginResult = (Test-Api -Name "Login Admin User" `
    -Method "POST" -Url "$baseUrl/api/auth/login" `
    -Body @{ email = "admin@ecommerce.com"; password = "AdminPassword123*" } `
    -ExpectedStatus @(200))[0]

$adminToken = $adminLoginResult.Data.accessToken
$adminHeaders = @{ "Authorization" = "Bearer $adminToken" }

# Login Customer
$custLoginResult = (Test-Api -Name "Login Customer User" `
    -Method "POST" -Url "$baseUrl/api/auth/login" `
    -Body @{ email = $customerEmail; password = "CustomerPassword123*" } `
    -ExpectedStatus @(200))[0]

$customerToken = $custLoginResult.Data.accessToken
$customerRefreshToken = $custLoginResult.Data.refreshToken
$custHeaders = @{ "Authorization" = "Bearer $customerToken" }

# Refresh Token
$refreshResult = (Test-Api -Name "Refresh Token for Customer" `
    -Method "POST" -Url "$baseUrl/api/auth/refresh" `
    -Body @{ refreshToken = $customerRefreshToken } `
    -ExpectedStatus @(200))[0]

if ($refreshResult.Data -and $refreshResult.Data.accessToken) {
    $customerToken = $refreshResult.Data.accessToken
    $custHeaders = @{ "Authorization" = "Bearer $customerToken" }
}

# ------------------------------------------------------------------------------
# 2. CATEGORIES
# ------------------------------------------------------------------------------
Write-Host "`n>>> [2/7] Testing Category Endpoints..." -ForegroundColor Yellow

# Public GET Categories
$null = Test-Api -Name "Public GET /api/categories" `
    -Method "GET" -Url "$baseUrl/api/categories" `
    -ExpectedStatus @(200)

# Admin POST Categories
$cat1 = (Test-Api -Name "Admin POST /api/categories (Elektronik)" `
    -Method "POST" -Url "$baseUrl/api/categories" `
    -Headers $adminHeaders `
    -Body @{ name = "Elektronik-$timestamp"; description = "Elektronik Cihazlar" } `
    -ExpectedStatus @(201))[0]
$cat1Id = $cat1.Data.id

$catTemp = (Test-Api -Name "Admin POST /api/categories (Gecici Kategori)" `
    -Method "POST" -Url "$baseUrl/api/categories" `
    -Headers $adminHeaders `
    -Body @{ name = "Silinecek Kategori-$timestamp"; description = "Silme Testi" } `
    -ExpectedStatus @(201))[0]
$catTempId = $catTemp.Data.id

# Public GET Category by ID
$null = Test-Api -Name "Public GET /api/categories/{id}" `
    -Method "GET" -Url "$baseUrl/api/categories/$cat1Id" `
    -ExpectedStatus @(200)

# Admin PUT Category
$null = Test-Api -Name "Admin PUT /api/categories/{id}" `
    -Method "PUT" -Url "$baseUrl/api/categories/$cat1Id" `
    -Headers $adminHeaders `
    -Body @{ name = "Elektronik & Bilisim-$timestamp"; description = "Guncellenmis Kategori" } `
    -ExpectedStatus @(200)

# Admin DELETE Category
$null = Test-Api -Name "Admin DELETE /api/categories/{id}" `
    -Method "DELETE" -Url "$baseUrl/api/categories/$catTempId" `
    -Headers $adminHeaders `
    -ExpectedStatus @(200, 204)

# ------------------------------------------------------------------------------
# 3. PRODUCTS
# ------------------------------------------------------------------------------
Write-Host "`n>>> [3/7] Testing Product Endpoints..." -ForegroundColor Yellow

# Admin POST Product 1
$prod1 = (Test-Api -Name "Admin POST /api/products (MacBook Pro M3)" `
    -Method "POST" -Url "$baseUrl/api/products" `
    -Headers $adminHeaders `
    -Body @{
        name = "MacBook Pro M3-$timestamp"
        description = "Apple M3 Chip, 16GB, 512GB"
        price = 75000.00
        stock = 10
        categoryId = $cat1Id
    } `
    -ExpectedStatus @(201))[0]
$prod1Id = $prod1.Data.id

# Admin POST Product 2
$prod2 = (Test-Api -Name "Admin POST /api/products (Sony WH-1000XM5)" `
    -Method "POST" -Url "$baseUrl/api/products" `
    -Headers $adminHeaders `
    -Body @{
        name = "Sony WH-1000XM5-$timestamp"
        description = "Gürültü Önleyici Kulaklık"
        price = 12000.00
        stock = 25
        categoryId = $cat1Id
    } `
    -ExpectedStatus @(201))[0]
$prod2Id = $prod2.Data.id

# Admin POST Product Temp
$prodTemp = (Test-Api -Name "Admin POST /api/products (Gecici Urun)" `
    -Method "POST" -Url "$baseUrl/api/products" `
    -Headers $adminHeaders `
    -Body @{
        name = "Silinecek Urun-$timestamp"
        description = "Silme Testi"
        price = 500.00
        stock = 5
        categoryId = $cat1Id
    } `
    -ExpectedStatus @(201))[0]
$prodTempId = $prodTemp.Data.id

# Public GET Products (Pageable)
$null = Test-Api -Name "Public GET /api/products (Pageable)" `
    -Method "GET" -Url "$baseUrl/api/products" `
    -ExpectedStatus @(200)

# Public GET Products with Filters
$null = Test-Api -Name "Public GET /api/products (Filter: minPrice=10000)" `
    -Method "GET" -Url "$baseUrl/api/products?minPrice=10000&maxPrice=100000" `
    -ExpectedStatus @(200)

# Public GET Product by ID (1st time -> DB & caches in Redis)
$null = Test-Api -Name "Public GET /api/products/{id} (1st call: DB + Cache store)" `
    -Method "GET" -Url "$baseUrl/api/products/$prod1Id" `
    -ExpectedStatus @(200)

# Public GET Product by ID (2nd time -> Cache hit)
$null = Test-Api -Name "Public GET /api/products/{id} (2nd call: Redis Cache Hit)" `
    -Method "GET" -Url "$baseUrl/api/products/$prod1Id" `
    -ExpectedStatus @(200)

# Admin PUT Product
$null = Test-Api -Name "Admin PUT /api/products/{id} (Update Product)" `
    -Method "PUT" -Url "$baseUrl/api/products/$prod1Id" `
    -Headers $adminHeaders `
    -Body @{
        name = "MacBook Pro M3 Pro-$timestamp"
        description = "Apple M3 Pro Chip, 18GB, 512GB - Updated"
        price = 85000.00
        stock = 10
        categoryId = $cat1Id
    } `
    -ExpectedStatus @(200)

# Admin DELETE Product
$null = Test-Api -Name "Admin DELETE /api/products/{id}" `
    -Method "DELETE" -Url "$baseUrl/api/products/$prodTempId" `
    -Headers $adminHeaders `
    -ExpectedStatus @(200, 204)

# ------------------------------------------------------------------------------
# 4. CUSTOMER ORDERS & CONCURRENCY
# ------------------------------------------------------------------------------
Write-Host "`n>>> [4/7] Testing Customer Orders (Pessimistic Lock, Stock, Discount)..." -ForegroundColor Yellow

# Customer Place Order 1: 1x MacBook (85,000 TL) + 2x Sony Headphones (24,000 TL) = 109,000 TL
# Tiered / Standard discount applied
$order1Req = @{
    items = @(
        @{ productId = $prod1Id; quantity = 1 },
        @{ productId = $prod2Id; quantity = 2 }
    )
}
$order1 = (Test-Api -Name "Customer POST /api/orders (Place Order: 1x MacBook, 2x Sony)" `
    -Method "POST" -Url "$baseUrl/api/orders" `
    -Headers $custHeaders `
    -Body $order1Req `
    -ExpectedStatus @(201))[0]
$order1Id = $order1.Data.id

# Customer GET Orders
$null = Test-Api -Name "Customer GET /api/orders (List Customer Orders)" `
    -Method "GET" -Url "$baseUrl/api/orders" `
    -Headers $custHeaders `
    -ExpectedStatus @(200)

# Customer GET Order Details (Fetch Join items)
$null = Test-Api -Name "Customer GET /api/orders/{id} (Order Details with Items)" `
    -Method "GET" -Url "$baseUrl/api/orders/$order1Id" `
    -Headers $custHeaders `
    -ExpectedStatus @(200)

# Customer Place Order 2 (For Cancellation Test)
$order2Req = @{
    items = @(
        @{ productId = $prod2Id; quantity = 1 }
    )
}
$order2 = (Test-Api -Name "Customer POST /api/orders (Order 2 for Cancellation)" `
    -Method "POST" -Url "$baseUrl/api/orders" `
    -Headers $custHeaders `
    -Body $order2Req `
    -ExpectedStatus @(201))[0]
$order2Id = $order2.Data.id

# Customer Cancel Order 2 (Returns stock)
$null = Test-Api -Name "Customer PUT /api/orders/{id}/cancel (Cancel & Restock)" `
    -Method "PUT" -Url "$baseUrl/api/orders/$order2Id/cancel" `
    -Headers $custHeaders `
    -ExpectedStatus @(200)

# ------------------------------------------------------------------------------
# 5. ADMIN ORDERS
# ------------------------------------------------------------------------------
Write-Host "`n>>> [5/7] Testing Admin Order Management Endpoints..." -ForegroundColor Yellow

# Admin GET All Orders
$null = Test-Api -Name "Admin GET /api/admin/orders (Pageable)" `
    -Method "GET" -Url "$baseUrl/api/admin/orders" `
    -Headers $adminHeaders `
    -ExpectedStatus @(200)

# Admin GET Orders Filtered by Status
$null = Test-Api -Name "Admin GET /api/admin/orders?status=PENDING" `
    -Method "GET" -Url "$baseUrl/api/admin/orders?status=PENDING" `
    -Headers $adminHeaders `
    -ExpectedStatus @(200)

# Admin PUT Order Status: PENDING -> CONFIRMED
$null = Test-Api -Name "Admin PUT /api/admin/orders/{id}/status (PENDING -> CONFIRMED)" `
    -Method "PUT" -Url "$baseUrl/api/admin/orders/$order1Id/status" `
    -Headers $adminHeaders `
    -Body @{ status = "CONFIRMED" } `
    -ExpectedStatus @(200)

# ------------------------------------------------------------------------------
# 6. SECURITY & ROLE-BASED ACCESS CONTROL (RBAC)
# ------------------------------------------------------------------------------
Write-Host "`n>>> [6/7] Testing Security & RBAC Constraints..." -ForegroundColor Yellow

# Customer attempting Admin Category creation -> MUST RETURN 403 FORBIDDEN
$null = Test-Api -Name "RBAC: Customer accessing POST /api/categories (Expected: 403)" `
    -Method "POST" -Url "$baseUrl/api/categories" `
    -Headers $custHeaders `
    -Body @{ name = "Hacker Category"; description = "Unauthorized" } `
    -ExpectedStatus @(403)

# Customer attempting Admin Orders -> MUST RETURN 403 FORBIDDEN
$null = Test-Api -Name "RBAC: Customer accessing GET /api/admin/orders (Expected: 403)" `
    -Method "GET" -Url "$baseUrl/api/admin/orders" `
    -Headers $custHeaders `
    -ExpectedStatus @(403)

# Anonymous attempting Order creation -> MUST RETURN 401 UNAUTHORIZED
$null = Test-Api -Name "RBAC: Anonymous accessing POST /api/orders (Expected: 401)" `
    -Method "POST" -Url "$baseUrl/api/orders" `
    -Body @{ items = @(@{ productId = $prod1Id; quantity = 1 }) } `
    -ExpectedStatus @(401)

# ------------------------------------------------------------------------------
# 7. LOGOUT & REDIS BLACKLIST
# ------------------------------------------------------------------------------
Write-Host "`n>>> [7/7] Testing Logout & Redis Token Blacklist..." -ForegroundColor Yellow

# Customer Logout (Blacklists JWT in Redis with TTL)
$null = Test-Api -Name "Customer POST /api/auth/logout (Blacklists token in Redis)" `
    -Method "POST" -Url "$baseUrl/api/auth/logout" `
    -Headers $custHeaders `
    -ExpectedStatus @(200, 204)

# Customer re-using logged-out token -> MUST RETURN 401 UNAUTHORIZED
$null = Test-Api -Name "Security: Reusing Logged-Out Token (Expected: 401)" `
    -Method "GET" -Url "$baseUrl/api/orders" `
    -Headers $custHeaders `
    -ExpectedStatus @(401)

# ------------------------------------------------------------------------------
# SUMMARY & STATS
# ------------------------------------------------------------------------------
Write-Host "`n================================================================================" -ForegroundColor Cyan
Write-Host "                              TEST SUMMARY" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host " Total Endpoints Tested : $($script:passCount + $script:failCount)" -ForegroundColor White
Write-Host " Passed                 : $($script:passCount)" -ForegroundColor Green
Write-Host " Failed                 : $($script:failCount)" -ForegroundColor $(if ($script:failCount -gt 0) { "Red" } else { "Green" })
Write-Host " Success Rate           : $([Math]::Round(($script:passCount / ($script:passCount + $script:failCount)) * 100, 1))%" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

if ($script:failCount -eq 0) {
    Write-Host " >>> TÜM ENDPOINT'LER BAŞARIYLA DOĞRULANDI VE TESTLERİ GEÇTİ! <<<" -ForegroundColor Green
}
else {
    Write-Host " >>> BAZI TESTLER BAŞARISIZ OLDU. DETAYLARI İNCELEYİN. <<<" -ForegroundColor Red
}
