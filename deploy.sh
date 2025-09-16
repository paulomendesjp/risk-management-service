#!/bin/bash

# Deploy script for Risk Management System
# Server: 205.172.56.220
# User: root
# Password: Host@9090

set -e

echo "========================================="
echo "Risk Management System - Deployment Script"
echo "========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Server details
SERVER_IP="205.172.56.220"
SERVER_USER="root"
SERVER_PASSWORD="Host@9090"

echo -e "${YELLOW}Deploying to server: $SERVER_IP${NC}"

# Function to execute commands on remote server
remote_exec() {
    sshpass -p "$SERVER_PASSWORD" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" "$1"
}

# Function to copy files to remote server
remote_copy() {
    sshpass -p "$SERVER_PASSWORD" scp -o StrictHostKeyChecking=no -r "$1" "$SERVER_USER@$SERVER_IP:$2"
}

# Step 1: Check if sshpass is installed locally
if ! command -v sshpass &> /dev/null; then
    echo -e "${RED}sshpass is not installed. Installing...${NC}"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        brew install hudochenkov/sshpass/sshpass
    else
        sudo apt-get update && sudo apt-get install -y sshpass
    fi
fi

# Step 2: Test SSH connection
echo -e "${YELLOW}Testing SSH connection...${NC}"
if remote_exec "echo 'Connection successful'"; then
    echo -e "${GREEN}✓ SSH connection successful${NC}"
else
    echo -e "${RED}✗ Failed to connect to server${NC}"
    exit 1
fi

# Step 3: Check and install Docker on remote server
echo -e "${YELLOW}Checking Docker installation on server...${NC}"
if remote_exec "docker --version" &>/dev/null; then
    echo -e "${GREEN}✓ Docker is already installed${NC}"
else
    echo -e "${YELLOW}Installing Docker...${NC}"
    remote_exec "curl -fsSL https://get.docker.com | sh"
    remote_exec "systemctl start docker && systemctl enable docker"
    echo -e "${GREEN}✓ Docker installed successfully${NC}"
fi

# Step 4: Check and install Docker Compose on remote server
echo -e "${YELLOW}Checking Docker Compose installation...${NC}"
if remote_exec "docker-compose --version" &>/dev/null; then
    echo -e "${GREEN}✓ Docker Compose is already installed${NC}"
else
    echo -e "${YELLOW}Installing Docker Compose...${NC}"
    remote_exec "curl -L 'https://github.com/docker/compose/releases/download/v2.23.0/docker-compose-$(uname -s)-$(uname -m)' -o /usr/local/bin/docker-compose"
    remote_exec "chmod +x /usr/local/bin/docker-compose"
    echo -e "${GREEN}✓ Docker Compose installed successfully${NC}"
fi

# Step 5: Install Git on server if needed
echo -e "${YELLOW}Checking Git installation on server...${NC}"
if remote_exec "git --version" &>/dev/null; then
    echo -e "${GREEN}✓ Git is already installed${NC}"
else
    echo -e "${YELLOW}Installing Git...${NC}"
    remote_exec "apt-get update && apt-get install -y git"
    echo -e "${GREEN}✓ Git installed successfully${NC}"
fi

# Step 6: Clone or update repository
echo -e "${YELLOW}Cloning/updating repository on server...${NC}"
if remote_exec "[ -d /opt/risk-management/.git ]"; then
    echo -e "${YELLOW}Repository exists, pulling latest changes...${NC}"
    remote_exec "cd /opt/risk-management && git pull"
else
    echo -e "${YELLOW}Cloning repository...${NC}"
    remote_exec "rm -rf /opt/risk-management"
    remote_exec "cd /opt && git clone https://github.com/paulomendesjp/risk-management-service.git risk-management"
fi
echo -e "${GREEN}✓ Repository ready${NC}"

# Step 9: Stop any existing containers
echo -e "${YELLOW}Stopping any existing containers...${NC}"
remote_exec "cd /opt/risk-management && docker-compose down 2>/dev/null || true"

# Step 10: Build and start services
echo -e "${YELLOW}Building and starting services...${NC}"
echo -e "${YELLOW}This may take several minutes on first run...${NC}"
remote_exec "cd /opt/risk-management && docker-compose up --build -d"

# Step 11: Wait for services to be healthy
echo -e "${YELLOW}Waiting for services to start...${NC}"
sleep 30

# Step 12: Check service status
echo -e "${YELLOW}Checking service status...${NC}"
remote_exec "cd /opt/risk-management && docker-compose ps"

# Step 13: Show service URLs
echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}Deployment completed successfully!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "Service URLs:"
echo "  User Service:         http://$SERVER_IP:8081/swagger-ui.html"
echo "  Risk Monitoring:      http://$SERVER_IP:8082/swagger-ui.html"
echo "  Position Service:     http://$SERVER_IP:8083/swagger-ui.html"
echo "  Notification Service: http://$SERVER_IP:8084/swagger-ui.html"
echo "  RabbitMQ Management:  http://$SERVER_IP:15672 (admin/password123)"
echo "  MongoDB:              mongodb://admin:password123@$SERVER_IP:27017"
echo "  Redis:                redis://$SERVER_IP:6379"
echo ""
echo "To view logs:"
echo "  ssh root@$SERVER_IP 'cd /opt/risk-management && docker-compose logs -f'"
echo ""
echo "To restart services:"
echo "  ssh root@$SERVER_IP 'cd /opt/risk-management && docker-compose restart'"

