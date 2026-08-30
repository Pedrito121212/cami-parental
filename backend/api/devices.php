<?php
require_once __DIR__ . '/../config/database.php';
setupApiHeaders();

$method = $_SERVER['REQUEST_METHOD'];
$action = isset($_GET['action']) ? $_GET['action'] : '';
$db = Database::getConnection();

// --- RUTAS DE DISPOSITIVO ANDROID (Con X-Device-Token) ---
if ($action === 'register_device' && $method === 'POST') {
    $input = getJsonInput();
    $token = isset($input['pairing_token']) ? trim($input['pairing_token']) : '';
    $model = isset($input['model']) ? trim($input['model']) : 'Android Phone';
    $androidVer = isset($input['android_version']) ? trim($input['android_version']) : 'Android';
    $isDpc = isset($input['is_device_owner']) ? (int)$input['is_device_owner'] : 0;

    if (empty($token)) {
        jsonError("Se requiere pairing_token", 400);
    }

    $stmt = $db->prepare("SELECT * FROM devices WHERE pairing_token = ?");
    $stmt->execute([$token]);
    $device = $stmt->fetch();

    if (!$device) {
        // Auto-crear dispositivo si se ingresa un código nuevo
        $childName = "Camilita 🌸";
        $devName = "Teléfono de Camilita (" . $model . ")";
        $stmt = $db->prepare("INSERT INTO devices (user_id, device_name, child_name, pairing_token, status) VALUES (1, ?, ?, ?, 'pending_pairing')");
        $stmt->execute([$devName, $childName, $token]);
        $deviceId = $db->lastInsertId();
        $device = [
            'id' => $deviceId,
            'pairing_token' => $token,
            'device_name' => $devName,
            'child_name' => $childName
        ];
    }

    // Activar dispositivo
    $stmt = $db->prepare("UPDATE devices SET 
        model = ?, 
        android_version = ?, 
        is_device_owner = ?, 
        status = 'active', 
        last_seen = datetime('now') 
        WHERE id = ?");
    $stmt->execute([$model, $androidVer, $isDpc, $device['id']]);

    // Insertar categorías web por defecto
    $defaultCategories = [
        ['adult', 'Contenido para Adultos / Pornografía', 1],
        ['gambling', 'Apuestas y Casinos', 1],
        ['violence', 'Armas y Violencia Extrema', 1],
        ['games', 'Juegos Web Online', 0],
        ['social', 'Redes Sociales Web', 0]
    ];

    foreach ($defaultCategories as $cat) {
        $db->prepare("INSERT OR IGNORE INTO web_categories (device_id, category_key, category_name, is_blocked) VALUES (?, ?, ?, ?)")
           ->execute([$device['id'], $cat[0], $cat[1], $cat[2]]);
    }

    // Insertar horario de dormir por defecto (22:00 a 07:00)
    $db->prepare("INSERT INTO schedules (device_id, name, days_of_week, start_time, end_time, rule_type, is_active) 
                  VALUES (?, 'Hora de Dormir (Bedtime)', '0,1,2,3,4,5,6', '22:00', '07:00', 'bedtime', 1)")
       ->execute([$device['id']]);

    jsonResponse([
        'success' => true,
        'message' => 'Dispositivo vinculado con éxito',
        'device_id' => $device['id'],
        'device_token' => $device['pairing_token'],
        'device_name' => $device['device_name'],
        'child_name' => $device['child_name']
    ]);
}

if ($action === 'heartbeat' && $method === 'POST') {
    $device = authenticateDevice();
    $input = getJsonInput();

    $battery = isset($input['battery_level']) ? (int)$input['battery_level'] : $device['battery_level'];
    $isCharging = isset($input['is_charging']) ? (int)$input['is_charging'] : $device['is_charging'];
    $isScreenOn = isset($input['is_screen_on']) ? (int)$input['is_screen_on'] : $device['is_screen_on'];
    $currentApp = isset($input['current_app']) ? trim($input['current_app']) : $device['current_app'];
    $isDpc = isset($input['is_device_owner']) ? (int)$input['is_device_owner'] : $device['is_device_owner'];

    // Actualizar telemetría del dispositivo
    $stmt = $db->prepare("UPDATE devices SET 
        battery_level = ?, 
        is_charging = ?, 
        is_screen_on = ?, 
        current_app = ?, 
        is_device_owner = ?, 
        last_seen = datetime('now') 
        WHERE id = ?");
    $stmt->execute([$battery, $isCharging, $isScreenOn, $currentApp, $isDpc, $device['id']]);

    // Verificar si hay comandos pendientes en cola para este dispositivo
    $cmdStmt = $db->prepare("SELECT id, command_type, payload FROM commands WHERE device_id = ? AND status = 'pending' ORDER BY id ASC");
    $cmdStmt->execute([$device['id']]);
    $pendingCommands = $cmdStmt->fetchAll();

    // Si hay comandos, marcarlos como 'sent'
    if (!empty($pendingCommands)) {
        $db->prepare("UPDATE commands SET status = 'sent' WHERE device_id = ? AND status = 'pending'")
           ->execute([$device['id']]);
    }

    // Obtener estado actual de bloqueo y configuración
    $freshDevice = $db->query("SELECT is_locked, lock_message, safesearch_enabled, vpn_filter_enabled, bonus_minutes_remaining, bonus_expires_at FROM devices WHERE id = " . (int)$device['id'])->fetch();

    jsonResponse([
        'success' => true,
        'is_locked' => (bool)$freshDevice['is_locked'],
        'lock_message' => $freshDevice['lock_message'],
        'safesearch_enabled' => (bool)$freshDevice['safesearch_enabled'],
        'vpn_filter_enabled' => (bool)$freshDevice['vpn_filter_enabled'],
        'bonus_minutes_remaining' => (int)$freshDevice['bonus_minutes_remaining'],
        'commands' => $pendingCommands
    ]);
}

// --- RUTAS DEL PANEL DE CONTROL DE PADRES (Requiere autenticación) ---
$user = authenticateParent();
if (!$user) {
    jsonError("No autorizado. Por favor inicia sesión.", 401);
}

if ($method === 'GET') {
    if ($action === 'list' || empty($action)) {
        // Listar dispositivos con indicador de en línea (visto en los últimos 2 minutos)
        $stmt = $db->query("SELECT 
            d.*,
            CASE 
                WHEN d.last_seen >= datetime('now', '-2 minutes') THEN 1 
                ELSE 0 
            END as is_online,
            (SELECT COUNT(*) FROM apps WHERE device_id = d.id) as total_apps_count,
            (SELECT COUNT(*) FROM apps WHERE device_id = d.id AND is_blocked = 1) as blocked_apps_count,
            (SELECT COUNT(*) FROM schedules WHERE device_id = d.id AND is_active = 1) as active_schedules_count
            FROM devices d 
            ORDER BY d.id DESC");
        $devices = $stmt->fetchAll();

        jsonResponse([
            'success' => true,
            'devices' => $devices
        ]);
    }

    if ($action === 'get') {
        $id = isset($_GET['id']) ? (int)$_GET['id'] : 0;
        $stmt = $db->prepare("SELECT d.*, 
            CASE WHEN d.last_seen >= datetime('now', '-2 minutes') THEN 1 ELSE 0 END as is_online 
            FROM devices d WHERE d.id = ?");
        $stmt->execute([$id]);
        $device = $stmt->fetch();

        if (!$device) {
            jsonError("Dispositivo no encontrado", 404);
        }

        // Obtener apps
        $appsStmt = $db->prepare("SELECT * FROM apps WHERE device_id = ? ORDER BY is_blocked DESC, used_today_minutes DESC, app_name ASC");
        $appsStmt->execute([$id]);
        $apps = $appsStmt->fetchAll();

        // Obtener horarios
        $schedStmt = $db->prepare("SELECT * FROM schedules WHERE device_id = ? ORDER BY start_time ASC");
        $schedStmt->execute([$id]);
        $schedules = $schedStmt->fetchAll();

        // Obtener filtros web
        $catStmt = $db->prepare("SELECT * FROM web_categories WHERE device_id = ?");
        $catStmt->execute([$id]);
        $categories = $catStmt->fetchAll();

        $domStmt = $db->prepare("SELECT * FROM web_filters WHERE device_id = ? ORDER BY id DESC");
        $domStmt->execute([$id]);
        $domains = $domStmt->fetchAll();

        jsonResponse([
            'success' => true,
            'device' => $device,
            'apps' => $apps,
            'schedules' => $schedules,
            'web_categories' => $categories,
            'web_domains' => $domains
        ]);
    }
}

if ($method === 'POST') {
    $input = getJsonInput();

    if ($action === 'create_pairing') {
        $childName = isset($input['child_name']) ? trim($input['child_name']) : 'Mi Hijo';
        $deviceName = isset($input['device_name']) ? trim($input['device_name']) : 'Teléfono de ' . $childName;

        $token = 'CAMI-' . strtoupper(substr(bin2hex(random_bytes(6)), 0, 8));

        $stmt = $db->prepare("INSERT INTO devices (user_id, device_name, child_name, pairing_token, status) VALUES (?, ?, ?, ?, 'pending_pairing')");
        $stmt->execute([$user['id'], $deviceName, $childName, $token]);
        $deviceId = $db->lastInsertId();

        // Armar URL base del servidor para generar el código QR
        $protocol = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? "https://" : "http://";
        $serverHost = $_SERVER['HTTP_HOST'];
        $scriptDir = trim(str_replace('\\', '/', dirname(dirname($_SERVER['SCRIPT_NAME']))), '/');
        $baseUrl = $protocol . $serverHost . ($scriptDir ? '/' . $scriptDir : '');

        $qrData = json_encode([
            'server_url' => $baseUrl . '/api',
            'pairing_token' => $token,
            'device_id' => $deviceId,
            'child_name' => $childName
        ]);

        jsonResponse([
            'success' => true,
            'device_id' => $deviceId,
            'pairing_token' => $token,
            'qr_payload' => $qrData,
            'server_url' => $baseUrl . '/api'
        ]);
    }

    if ($action === 'toggle_lock') {
        $deviceId = isset($input['device_id']) ? (int)$input['device_id'] : 0;
        $lock = isset($input['lock']) ? (int)$input['lock'] : 0;
        $message = isset($input['message']) ? trim($input['message']) : 'Dispositivo bloqueado por tus padres';

        $stmt = $db->prepare("UPDATE devices SET is_locked = ?, lock_message = ? WHERE id = ?");
        $stmt->execute([$lock, $message, $deviceId]);

        // Registrar comando en cola para notificación inmediata
        $cmdType = $lock ? 'lock_screen' : 'unlock_screen';
        $db->prepare("INSERT INTO commands (device_id, command_type, payload) VALUES (?, ?, ?)")
           ->execute([$deviceId, $cmdType, json_encode(['message' => $message])]);

        jsonResponse([
            'success' => true,
            'is_locked' => (bool)$lock,
            'message' => $lock ? 'Dispositivo bloqueado remotamente' : 'Dispositivo desbloqueado'
        ]);
    }

    if ($action === 'grant_bonus') {
        $deviceId = isset($input['device_id']) ? (int)$input['device_id'] : 0;
        $minutes = isset($input['minutes']) ? (int)$input['minutes'] : 15;

        // Añadir minutos y calcular tiempo de expiración
        $stmt = $db->prepare("UPDATE devices SET 
            is_locked = 0,
            bonus_minutes_remaining = bonus_minutes_remaining + ?, 
            bonus_expires_at = datetime('now', '+' . ? . ' minutes') 
            WHERE id = ?");
        $stmt->execute([$minutes, $minutes, $deviceId]);

        // Enviar comando
        $db->prepare("INSERT INTO commands (device_id, command_type, payload) VALUES (?, 'grant_bonus', ?)")
           ->execute([$deviceId, json_encode(['minutes' => $minutes])]);

        jsonResponse([
            'success' => true,
            'message' => "Se han concedido $minutes minutos adicionales",
            'minutes_granted' => $minutes
        ]);
    }

    if ($action === 'delete') {
        $deviceId = isset($input['device_id']) ? (int)$input['device_id'] : 0;
        $db->prepare("DELETE FROM devices WHERE id = ?")->execute([$deviceId]);
        jsonResponse(['success' => true, 'message' => 'Dispositivo eliminado']);
    }
}

jsonError("Acción no válida", 404);
