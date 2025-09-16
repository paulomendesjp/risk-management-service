# Risk Management System - Architecture Diagrams

## System Overview

### Main Architecture - Layered View

```mermaid
graph TB
    subgraph "External Layer"
        CLIENT[🌐 Web Client<br/>Dashboard]
        ARCH[🏢 Architect.co API<br/>Trading Platform]
    end
    
    subgraph "Gateway Layer"
        GW[🚪 API Gateway<br/>Port 8080]
    end
    
    subgraph "Application Layer"
        subgraph "Core Services"
            USER[👥 User Service<br/>Port 8081]
            RISK[⚡ Risk Monitoring<br/>Port 8083]
            POS[💼 Position Service<br/>Port 8082]
            NOTIF[📢 Notification Service<br/>Port 8084]
        end
        
        subgraph "Integration"
            BRIDGE[🐍 Architect Bridge<br/>Python - Port 8090]
        end
    end
    
    subgraph "Messaging Layer"
        RMQ[🐰 RabbitMQ<br/>Port 5672]
    end
    
    subgraph "Data Layer"
        MONGO[(📊 MongoDB<br/>Port 27017)]
        REDIS[(💾 Redis<br/>Port 6379)]
    end
    
    %% Client connections
    CLIENT -->|HTTP/WebSocket| GW
    
    %% External API connection
    ARCH <-.->|WebSocket/REST| BRIDGE
    
    %% Gateway routing (simplified)
    GW --> USER
    GW --> RISK
    GW --> POS
    GW --> NOTIF
    
    %% Core service flow
    BRIDGE -->|Real-time Data| RISK
    RISK -->|Get Config| USER
    RISK -->|Close Positions| POS
    
    %% Event messaging
    USER -.->|Events| RMQ
    RISK -.->|Events| RMQ
    RMQ -.->|Events| NOTIF
    
    %% Data persistence
    USER --> MONGO
    RISK --> MONGO
    RISK --> REDIS
    POS --> MONGO
    NOTIF --> MONGO
    
    %% Styling
    classDef client fill:#e8f5e8,stroke:#2e7d32,stroke-width:3px
    classDef gateway fill:#e3f2fd,stroke:#1565c0,stroke-width:3px
    classDef service fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    classDef integration fill:#fff3e0,stroke:#ef6c00,stroke-width:2px
    classDef messaging fill:#fce4ec,stroke:#c2185b,stroke-width:2px
    classDef database fill:#e0f2f1,stroke:#00695c,stroke-width:2px
    
    class CLIENT,ARCH client
    class GW gateway
    class USER,RISK,POS,NOTIF service
    class BRIDGE integration
    class RMQ messaging
    class MONGO,REDIS database
```

### Simplified Data Flow

```mermaid
flowchart LR
    subgraph "External"
        A[🏢 Architect.co]
        C[🌐 Client]
    end
    
    subgraph "Processing"
        B[🐍 Bridge]
        R[⚡ Risk Engine]
        P[💼 Positions]
        N[📢 Notifications]
    end
    
    subgraph "Storage"
        D[(📊 Database)]
        Q[🐰 Queue]
    end
    
    A -->|Real-time| B
    B -->|Balance| R
    R -->|Violation| P
    R -->|Alert| N
    R --> D
    R --> Q
    Q --> N
    C <--> R
    
    style A fill:#ffcdd2
    style B fill:#fff3e0
    style R fill:#e8f5e8
    style P fill:#e3f2fd
    style N fill:#f3e5f5
    style D fill:#e0f2f1
    style Q fill:#fce4ec
```

## RabbitMQ Event Flow

```mermaid
graph LR
    %% Services
    USER[User Service]
    RISK[Risk Monitoring]
    POS[Position Service]
    NOTIF[Notification Service]
    
    %% RabbitMQ Exchanges and Queues
    subgraph RabbitMQ
        UE[user.exchange]
        RE[risk.exchange]
        NE[notification.exchange]
        PE[position.exchange]
        
        UQ[user.registrations<br/>queue]
        RQ[risk.violation<br/>queue]
        NQ[notification<br/>queue]
        PQ[position.closure<br/>queue]
    end
    
    %% Event Publishing
    USER -->|1. User Registration| UE
    RISK -->|3. Risk Violation| RE
    RISK -->|4. Notification Request| NE
    POS -->|5. Position Closed| PE
    
    %% Queue Routing
    UE --> UQ
    RE --> RQ
    NE --> NQ
    PE --> PQ
    
    %% Event Consumption
    UQ -->|2. Start Monitoring| RISK
    RQ -->|Risk Alert| NOTIF
    NQ -->|Send Notification| NOTIF
    PQ -->|Update Status| RISK
    
    %% Styling
    classDef service fill:#e1f5fe,stroke:#01579b,stroke-width:2px
    classDef exchange fill:#fff3e0,stroke:#e65100,stroke-width:2px
    classDef queue fill:#f3e5f5,stroke:#4a148c,stroke-width:2px
    
    class USER,RISK,POS,NOTIF service
    class UE,RE,NE,PE exchange
    class UQ,RQ,NQ,PQ queue
```

## User Registration Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as API Gateway
    participant US as User Service
    participant MQ as RabbitMQ
    participant RS as Risk Service
    participant AB as Architect Bridge
    participant DB as MongoDB
    
    C->>GW: POST /api/users
    GW->>US: Route request
    US->>DB: Save user config
    US->>MQ: Publish user.registration
    MQ->>RS: Consume registration event
    RS->>DB: Initialize monitoring
    RS->>US: Get user credentials
    US-->>RS: Return API keys
    RS->>AB: Start WebSocket monitoring
    AB-->>RS: Confirm connection
    RS-->>C: Registration complete
```

## Risk Violation Flow

```mermaid
sequenceDiagram
    participant AB as Architect Bridge
    participant RS as Risk Service
    participant PS as Position Service
    participant MQ as RabbitMQ
    participant NS as Notification Service
    participant DB as MongoDB
    
    AB->>RS: Real-time balance update
    RS->>RS: Calculate risk metrics
    alt Risk Violation Detected
        RS->>PS: Close all positions
        PS-->>RS: Positions closed
        RS->>DB: Update account status
        RS->>MQ: Publish violation event
        MQ->>NS: Route to notification
        NS->>NS: Send multi-channel alerts
        NS->>DB: Log notification history
    end
```

## Data Flow Architecture

```mermaid
graph TD
    %% Data Sources
    API[Architect.co API]
    
    %% Processing Layers
    subgraph "Data Ingestion"
        BRIDGE[Python Bridge<br/>• WebSocket Client<br/>• Data Polling<br/>• Format Translation]
    end
    
    subgraph "Risk Processing"
        RISK[Risk Engine<br/>• Balance Tracking<br/>• Risk Calculation<br/>• Violation Detection]
    end
    
    subgraph "Action Execution"
        POS[Position Manager<br/>• API Integration<br/>• Order Closure<br/>• Status Updates]
    end
    
    subgraph "Notification Layer"
        NOTIF[Notification Hub<br/>• Multi-channel<br/>• Audit Logging<br/>• Event History]
    end
    
    subgraph "Data Storage"
        MONGO[(MongoDB<br/>• User Configs<br/>• Monitoring Data<br/>• Audit Logs)]
        REDIS[(Redis<br/>• Real-time Cache<br/>• Session Data)]
    end
    
    %% Data Flow
    API -->|Real-time Data| BRIDGE
    BRIDGE -->|Processed Data| RISK
    RISK -->|Risk Events| POS
    RISK -->|Notifications| NOTIF
    
    %% Storage
    RISK --> MONGO
    RISK --> REDIS
    POS --> MONGO
    NOTIF --> MONGO
    
    %% Styling
    classDef processing fill:#e1f5fe,stroke:#01579b,stroke-width:2px
    classDef storage fill:#e8f5e8,stroke:#1b5e20,stroke-width:2px
    classDef external fill:#f3e5f5,stroke:#4a148c,stroke-width:2px
    
    class BRIDGE,RISK,POS,NOTIF processing
    class MONGO,REDIS storage
    class API external
```

## Technology Stack

```mermaid
graph TB
    subgraph "Frontend Layer"
        WEB[Web Dashboard<br/>HTML/CSS/JS]
        WS[WebSocket Client<br/>Real-time Updates]
    end
    
    subgraph "API Layer"
        GW[Spring Cloud Gateway<br/>Java 17]
    end
    
    subgraph "Service Layer"
        subgraph "Java Services"
            USER[User Service<br/>Spring Boot 3.1]
            RISK[Risk Monitoring<br/>Spring Boot 3.1]
            POS[Position Service<br/>Spring Boot 3.1]
            NOTIF[Notification Service<br/>Spring Boot 3.1]
        end
        
        subgraph "Python Services"
            BRIDGE[Architect Bridge<br/>FastAPI + Python 3.13]
        end
    end
    
    subgraph "Integration Layer"
        RMQ[RabbitMQ 3.12<br/>Message Broker]
        FEIGN[Feign Clients<br/>Service Communication]
    end
    
    subgraph "Data Layer"
        MONGO[MongoDB 7<br/>Document Store]
        REDIS[Redis 7<br/>Cache & Sessions]
    end
    
    subgraph "External APIs"
        ARCH[Architect.co API<br/>Trading Platform]
    end
    
    %% Connections
    WEB --> GW
    WS --> RISK
    GW --> USER
    GW --> RISK
    GW --> POS
    GW --> NOTIF
    
    USER <--> RMQ
    RISK <--> RMQ
    POS <--> RMQ
    NOTIF <--> RMQ
    
    USER --> MONGO
    RISK --> MONGO
    RISK --> REDIS
    POS --> MONGO
    NOTIF --> MONGO
    
    BRIDGE <--> ARCH
    BRIDGE <--> RISK
    
    %% Styling
    classDef frontend fill:#e8f5e8,stroke:#1b5e20,stroke-width:2px
    classDef java fill:#e1f5fe,stroke:#01579b,stroke-width:2px
    classDef python fill:#fff3e0,stroke:#e65100,stroke-width:2px
    classDef integration fill:#f3e5f5,stroke:#4a148c,stroke-width:2px
    classDef database fill:#fce4ec,stroke:#880e4f,stroke-width:2px
    classDef external fill:#f1f8e9,stroke:#33691e,stroke-width:2px
    
    class WEB,WS frontend
    class GW,USER,RISK,POS,NOTIF java
    class BRIDGE python
    class RMQ,FEIGN integration
    class MONGO,REDIS database
    class ARCH external
```

## Deployment Architecture

```mermaid
graph TB
    subgraph "Container Environment"
        subgraph "Application Containers"
            GW_C[api-gateway:8080]
            USER_C[user-service:8081]
            POS_C[position-service:8082]
            RISK_C[risk-monitoring:8083]
            NOTIF_C[notification-service:8084]
            BRIDGE_C[architect-bridge:8090]
        end
        
        subgraph "Infrastructure Containers"
            MONGO_C[mongodb:27017]
            REDIS_C[redis:6379]
            RMQ_C[rabbitmq:5672,15672]
        end
    end
    
    subgraph "Docker Network"
        NET[risk-management-network<br/>Bridge Network]
    end
    
    subgraph "Volume Mounts"
        MONGO_V[mongodb_data]
        REDIS_V[redis_data]
        RMQ_V[rabbitmq_data]
        LOGS_V[./logs]
    end
    
    %% Container connections
    GW_C -.-> NET
    USER_C -.-> NET
    POS_C -.-> NET
    RISK_C -.-> NET
    NOTIF_C -.-> NET
    BRIDGE_C -.-> NET
    MONGO_C -.-> NET
    REDIS_C -.-> NET
    RMQ_C -.-> NET
    
    %% Volume mounts
    MONGO_C --> MONGO_V
    REDIS_C --> REDIS_V
    RMQ_C --> RMQ_V
    RISK_C --> LOGS_V
    
    %% External access
    EXT[External Access<br/>localhost:8080-8090]
    EXT --> GW_C
    EXT --> BRIDGE_C
    EXT --> RMQ_C
    
    %% Styling
    classDef app fill:#e1f5fe,stroke:#01579b,stroke-width:2px
    classDef infra fill:#e8f5e8,stroke:#1b5e20,stroke-width:2px
    classDef volume fill:#fff3e0,stroke:#e65100,stroke-width:2px
    classDef network fill:#f3e5f5,stroke:#4a148c,stroke-width:2px
    
    class GW_C,USER_C,POS_C,RISK_C,NOTIF_C,BRIDGE_C app
    class MONGO_C,REDIS_C,RMQ_C infra
    class MONGO_V,REDIS_V,RMQ_V,LOGS_V volume
    class NET,EXT network
```

