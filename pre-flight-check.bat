@echo off
REM Pre-flight Checks for Jenkins Pipeline (Windows)
REM This script validates that all prerequisites are met before running the pipeline

setlocal enabledelayedexpansion

echo ================================
echo Jenkins Pipeline Pre-Flight Check
echo ================================
echo.

set CHECKS_PASSED=0
set CHECKS_FAILED=0

REM Function to check file existence
:check_file
if exist "%1" (
    echo [PASS] File exists: %1
    set /a CHECKS_PASSED+=1
) else (
    echo [FAIL] File missing: %1
    set /a CHECKS_FAILED+=1
)
exit /b

REM Function to check command existence
:check_command
where %1 >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo [PASS] %1 installed
    set /a CHECKS_PASSED+=1
) else (
    echo [FAIL] %1 not found
    set /a CHECKS_FAILED+=1
)
exit /b

REM Check 1: Repository Files
echo [1/5] Checking Repository Files...
if exist "Jenkinsfile" (
    echo [PASS] Jenkinsfile exists
    set /a CHECKS_PASSED+=1
) else (
    echo [FAIL] Jenkinsfile missing
    set /a CHECKS_FAILED+=1
)

if exist "aws-ubuntu.pkr.hcl" (
    echo [PASS] aws-ubuntu.pkr.hcl exists
    set /a CHECKS_PASSED+=1
) else (
    echo [FAIL] aws-ubuntu.pkr.hcl missing
    set /a CHECKS_FAILED+=1
)

if exist "dev.pkrvars.hcl" (
    echo [PASS] dev.pkrvars.hcl exists
    set /a CHECKS_PASSED+=1
) else (
    echo [FAIL] dev.pkrvars.hcl missing
    set /a CHECKS_FAILED+=1
)

if exist "install_nginx.sh" (
    echo [PASS] install_nginx.sh exists
    set /a CHECKS_PASSED+=1
) else (
    echo [FAIL] install_nginx.sh missing
    set /a CHECKS_FAILED+=1
)
echo.

REM Check 2: Required Commands
echo [2/5] Checking Required Commands...
where git >nul 2>&1 && (
    echo [PASS] git installed
    set /a CHECKS_PASSED+=1
) || (
    echo [FAIL] git not found
    set /a CHECKS_FAILED+=1
)

where packer >nul 2>&1 && (
    echo [PASS] packer installed
    set /a CHECKS_PASSED+=1
) || (
    echo [FAIL] packer not found
    set /a CHECKS_FAILED+=1
)

where aws >nul 2>&1 && (
    echo [PASS] aws CLI installed
    set /a CHECKS_PASSED+=1
) || (
    echo [FAIL] aws CLI not found
    set /a CHECKS_FAILED+=1
)

where gcloud >nul 2>&1 && (
    echo [PASS] gcloud installed
    set /a CHECKS_PASSED+=1
) || (
    echo [FAIL] gcloud not found
    set /a CHECKS_FAILED+=1
)

where az >nul 2>&1 && (
    echo [PASS] azure CLI installed
    set /a CHECKS_PASSED+=1
) || (
    echo [FAIL] azure CLI not found
    set /a CHECKS_FAILED+=1
)
echo.

REM Check 3: Validate Packer Templates
echo [3/5] Validating Packer Templates...
packer validate aws-ubuntu.pkr.hcl >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo [PASS] Packer template is valid
    set /a CHECKS_PASSED+=1
) else (
    echo [FAIL] Packer template validation failed
    echo   Run: packer validate aws-ubuntu.pkr.hcl
    set /a CHECKS_FAILED+=1
)
echo.

REM Check 4: Git Configuration
echo [4/5] Checking Git Configuration...
git config --get user.name >nul 2>&1
if %ERRORLEVEL% equ 0 (
    for /f "delims=" %%i in ('git config --get user.name') do set GIT_USER=%%i
    echo [PASS] Git user configured: !GIT_USER!
    set /a CHECKS_PASSED+=1
) else (
    echo [WARN] Git user not configured
    echo   Run: git config --global user.name "Your Name"
    set /a CHECKS_FAILED+=1
)

if exist ".git" (
    echo [PASS] Git repository initialized
    set /a CHECKS_PASSED+=1
    
    for /f "delims=" %%i in ('git remote get-url origin 2^>nul') do set GIT_REMOTE=%%i
    if defined GIT_REMOTE (
        echo [PASS] Git remote configured: !GIT_REMOTE!
        set /a CHECKS_PASSED+=1
    ) else (
        echo [FAIL] Git remote not configured
        set /a CHECKS_FAILED+=1
    )
) else (
    echo [FAIL] Not a git repository
    set /a CHECKS_FAILED+=1
)
echo.

REM Check 5: Jenkins Credentials
echo [5/5] Checking Jenkins Credentials...
echo [WARN] Manual verification required for Jenkins credentials:
echo   - github-token (GitHub Personal Access Token)
echo   - aws-creds (AWS Access Key ID + Secret Access Key)
echo   - gcp-key (GCP Service Account JSON)
echo   - azure-creds (Azure Client ID + Secret)
echo   - AZURE_SUBSCRIPTION_ID (Environment variable)
echo   - AZURE_TENANT_ID (Environment variable)
echo.

REM Summary
echo ================================
echo Pre-Flight Check Summary
echo ================================
echo Checks Passed: %CHECKS_PASSED%
echo Checks Failed: %CHECKS_FAILED%
echo.

if %CHECKS_FAILED% equ 0 (
    echo [SUCCESS] All checks passed! Ready to run Jenkins pipeline.
    exit /b 0
) else (
    echo [FAILURE] Some checks failed. Please resolve issues above.
    exit /b 1
)
