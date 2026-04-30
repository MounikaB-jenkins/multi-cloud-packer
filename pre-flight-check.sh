#!/bin/bash

# Pre-flight Checks for Jenkins Pipeline
# This script validates that all prerequisites are met before running the pipeline

set -e

echo "================================"
echo "Jenkins Pipeline Pre-Flight Check"
echo "================================"
echo ""

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check counter
CHECKS_PASSED=0
CHECKS_FAILED=0

# Function to check file existence
check_file() {
    if [ -f "$1" ]; then
        echo -e "${GREEN}✓${NC} File exists: $1"
        ((CHECKS_PASSED++))
    else
        echo -e "${RED}✗${NC} File missing: $1"
        ((CHECKS_FAILED++))
    fi
}

# Function to check command existence
check_command() {
    if command -v "$1" &> /dev/null; then
        version=$("$1" --version 2>&1 | head -n 1)
        echo -e "${GREEN}✓${NC} $1 installed: $version"
        ((CHECKS_PASSED++))
    else
        echo -e "${RED}✗${NC} $1 not found in PATH"
        ((CHECKS_FAILED++))
    fi
}

echo "[1/5] Checking Repository Files..."
check_file "Jenkinsfile"
check_file "aws-ubuntu.pkr.hcl"
check_file "dev.pkrvars.hcl"
check_file "install_nginx.sh"
echo ""

echo "[2/5] Checking Required Commands..."
check_command "git"
check_command "packer"
check_command "aws"
check_command "gcloud"
check_command "az"
echo ""

echo "[3/5] Validating Packer Templates..."
if packer validate aws-ubuntu.pkr.hcl > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} Packer template is valid"
    ((CHECKS_PASSED++))
else
    echo -e "${RED}✗${NC} Packer template validation failed"
    echo "  Run: packer validate aws-ubuntu.pkr.hcl"
    ((CHECKS_FAILED++))
fi
echo ""

echo "[4/5] Checking Git Configuration..."
if git config --get user.name > /dev/null 2>&1; then
    git_user=$(git config --get user.name)
    echo -e "${GREEN}✓${NC} Git user configured: $git_user"
    ((CHECKS_PASSED++))
else
    echo -e "${YELLOW}!${NC} Git user not configured"
    echo "  Run: git config --global user.name 'Your Name'"
    ((CHECKS_FAILED++))
fi

if [ -d ".git" ]; then
    echo -e "${GREEN}✓${NC} Git repository initialized"
    ((CHECKS_PASSED++))
    
    # Check remote
    if git remote get-url origin > /dev/null 2>&1; then
        remote=$(git remote get-url origin)
        echo -e "${GREEN}✓${NC} Git remote configured: $remote"
        ((CHECKS_PASSED++))
    else
        echo -e "${RED}✗${NC} Git remote not configured"
        ((CHECKS_FAILED++))
    fi
else
    echo -e "${RED}✗${NC} Not a git repository"
    ((CHECKS_FAILED++))
fi
echo ""

echo "[5/5] Checking Jenkins Credentials..."
echo -e "${YELLOW}!${NC} Manual verification required for Jenkins credentials:"
echo "  - github-token (GitHub Personal Access Token)"
echo "  - aws-creds (AWS Access Key ID + Secret Access Key)"
echo "  - gcp-key (GCP Service Account JSON)"
echo "  - azure-creds (Azure Client ID + Secret)"
echo "  - AZURE_SUBSCRIPTION_ID (Environment variable)"
echo "  - AZURE_TENANT_ID (Environment variable)"
echo ""

# Summary
echo "================================"
echo "Pre-Flight Check Summary"
echo "================================"
echo -e "Checks Passed: ${GREEN}$CHECKS_PASSED${NC}"
echo -e "Checks Failed: ${RED}$CHECKS_FAILED${NC}"
echo ""

if [ $CHECKS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All checks passed! Ready to run Jenkins pipeline.${NC}"
    exit 0
else
    echo -e "${RED}✗ Some checks failed. Please resolve issues above.${NC}"
    exit 1
fi
