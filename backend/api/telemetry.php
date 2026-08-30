<?php
require_once __DIR__ . '/../config/database.php';
setupApiHeaders();

$method = $_SERVER['REQUEST_METHOD'];
$action = isset($_GET['action']) ? $_GET['action'] : '';
$db = Database::getConnection();

// --- RUTAS DE DISPOSITIVO ANDROID ---
if ($action === 'log_usage' && $method === 'POST') {
    $device = authenticateDevice();
    $input = getJsonInput();
    $usageList = isset($input['usage']) && is_array($input['usage']) ? $input['usage'] : [];
    $today = date('Y-m-d');

    $db->beginTransaction();
    foreach ($usageList as $item) {
        $pkg = isset($item['package_name']) ? trim($item['package_name']) : '';
        $appName = isset($item['app_name']) ? trim($item['app_name']) : $pkg;
        $minutes = isset($item['minutes']) ? (int)$item['minutes'] : 0;

        if (!empty($pkg)) {
            // Actualizar tabla de apps
            $db->prepare("UPDATE apps SET used_today_minutes = ?, last_used = datetime('now') WHERE device_id = ? AND package_name = ?")
               ->execute([$minutes, $device['id'], $pkg]);

            // Actualizar o insertar en historial usage_logs
            $checkStmt = $db->prepare("SELECT id FROM usage_logs WHERE device_id = ? AND package_name = ? AND date_recorded = ?");
            $checkStmt->execute([$device['id'], $pkg, $today]);
            $existing = $checkStmt->fetch();

            if ($existing) {
                $db->prepare("UPDATE usage_logs SET minutes_used = ? WHERE id = ?")->execute([$minutes, $existing['id']]);
            } else {
                $db->prepare("INSERT INTO usage_logs (device_id, package_name, app_name, date_recorded, minutes_used) VALUES (?, ?, ?, ?, ?)")
                   ->execute([$device['id'], $pkg, $appName, $today, $minutes]);
            }
        }
    }
    $db->commit();

    jsonResponse(['success' => true, 'message' => 'Uso registrado']);
}

if ($action === 'log_security_event' && $method === 'POST') {
    $device = authenticateDevice();
    $input = getJsonInput();

    $type = isset($input['event_type']) ? trim($input['event_type']) : 'general';
    $desc = isset($input['description']) ? trim($input['description']) : '';

    if (!empty($desc)) {
        $stmt = $db->prepare("INSERT INTO security_events (device_id, event_type, description) VALUES (?, ?, ?)");
        $stmt->execute([$device['id'], $type, $desc]);
    }

    jsonResponse(['success' => true]);
}

// --- RUTAS DEL PANEL DE CONTROL DE PADRES ---
$user = authenticateParent();
if (!$user) {
    jsonError("No autorizado", 401);
}

if ($method === 'GET') {
    $deviceId = isset($_GET['device_id']) ? (int)$_GET['device_id'] : 0;
    if (empty($deviceId)) jsonError("Falta device_id", 400);

    if ($action === 'get_stats') {
        // Total de minutos hoy
        $todayTotal = $db->query("SELECT SUM(used_today_minutes) as total FROM apps WHERE device_id = $deviceId")->fetch();

        // Top 5 apps más usadas hoy
        $topApps = $db->prepare("SELECT app_name, package_name, category, used_today_minutes, daily_limit_minutes, is_blocked 
            FROM apps WHERE device_id = ? AND used_today_minutes > 0 
            ORDER BY used_today_minutes DESC LIMIT 8");
        $topApps->execute([$deviceId]);

        // Resumen últimos 7 días
        $historyStmt = $db->prepare("SELECT date_recorded, SUM(minutes_used) as total_minutes 
            FROM usage_logs 
            WHERE device_id = ? AND date_recorded >= date('now', '-7 days') 
            GROUP BY date_recorded 
            ORDER BY date_recorded ASC");
        $historyStmt->execute([$deviceId]);

        jsonResponse([
            'success' => true,
            'today_total_minutes' => (int)($todayTotal['total'] ?? 0),
            'top_apps' => $topApps->fetchAll(),
            'history_7days' => $historyStmt->fetchAll()
        ]);
    }

    if ($action === 'get_events') {
        $eventsStmt = $db->prepare("SELECT * FROM security_events WHERE device_id = ? ORDER BY id DESC LIMIT 20");
        $eventsStmt->execute([$deviceId]);

        jsonResponse([
            'success' => true,
            'events' => $eventsStmt->fetchAll()
        ]);
    }
}

jsonError("Acción no válida", 404);
