// MongoDB initialization script
// This script runs when MongoDB container starts for the first time

// Switch to the risk_management database
db = db.getSiblingDB('risk_management');

// Create collections with indexes
print('Creating collections and indexes...');

// Users collection
db.createCollection('users');
db.users.createIndex({ "email": 1 }, { unique: true });
db.users.createIndex({ "username": 1 }, { unique: true });
db.users.createIndex({ "status": 1 });
db.users.createIndex({ "createdAt": -1 });

// Positions collection
db.createCollection('positions');
db.positions.createIndex({ "userId": 1 });
db.positions.createIndex({ "symbol": 1 });
db.positions.createIndex({ "status": 1 });
db.positions.createIndex({ "openTime": -1 });
db.positions.createIndex({ "userId": 1, "status": 1 });

// Risk alerts collection
db.createCollection('risk_alerts');
db.risk_alerts.createIndex({ "userId": 1 });
db.risk_alerts.createIndex({ "alertType": 1 });
db.risk_alerts.createIndex({ "status": 1 });
db.risk_alerts.createIndex({ "createdAt": -1 });
db.risk_alerts.createIndex({ "userId": 1, "status": 1, "createdAt": -1 });

// Risk assessments collection
db.createCollection('risk_assessments');
db.risk_assessments.createIndex({ "userId": 1 });
db.risk_assessments.createIndex({ "timestamp": -1 });
db.risk_assessments.createIndex({ "riskLevel": 1 });

// Notification history collection
db.createCollection('notification_history');
db.notification_history.createIndex({ "userId": 1 });
db.notification_history.createIndex({ "type": 1 });
db.notification_history.createIndex({ "status": 1 });
db.notification_history.createIndex({ "sentAt": -1 });

// System events collection
db.createCollection('system_events');
db.system_events.createIndex({ "eventType": 1 });
db.system_events.createIndex({ "timestamp": -1 });
db.system_events.createIndex({ "severity": 1 });

// Create a default admin user (optional)
db.users.insertOne({
    "_id": ObjectId(),
    "username": "admin",
    "email": "admin@tradingsystem.com",
    "password": "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG", // password: password123
    "roles": ["ADMIN", "USER"],
    "status": "ACTIVE",
    "maxRiskLevel": 0.2,
    "dailyLossLimit": 10000,
    "createdAt": new Date(),
    "updatedAt": new Date(),
    "lastLogin": null,
    "preferences": {
        "notifications": {
            "email": true,
            "websocket": true,
            "slack": false
        },
        "riskAlerts": {
            "maxRisk": true,
            "stopLoss": true,
            "dailyLimit": true
        }
    }
});

// Create sample test user
db.users.insertOne({
    "_id": ObjectId(),
    "username": "trader1",
    "email": "trader1@example.com",
    "password": "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG", // password: password123
    "roles": ["USER"],
    "status": "ACTIVE",
    "maxRiskLevel": 0.15,
    "dailyLossLimit": 5000,
    "createdAt": new Date(),
    "updatedAt": new Date(),
    "lastLogin": null,
    "preferences": {
        "notifications": {
            "email": true,
            "websocket": true,
            "slack": false
        },
        "riskAlerts": {
            "maxRisk": true,
            "stopLoss": true,
            "dailyLimit": true
        }
    }
});

print('MongoDB initialization completed successfully!');

// Show created collections
print('\nCreated collections:');
db.getCollectionNames().forEach(function(name) {
    print('- ' + name);
});

// Show user count
print('\nInitial users created: ' + db.users.count());
