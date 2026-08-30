<?php
require_once __DIR__ . '/../config/database.php';
setupApiHeaders();

$method = $_SERVER['REQUEST_METHOD'];
$action = isset($_GET['action']) ? $_GET['action'] : '';
$db = Database::getConnection();

// --- RUTA ANDROID: Sincronizar aplicaciones instaladas ---
if ($action === 'sync_installed' && $method === 'POST') {
    $device = authenticateDevice();
    $input = getJsonInput();
    $apps = isset($input['apps']) && is_array($input['apps']) ? $input['apps'] : [];

    if (empty($apps)) {
        jsonError("Lista de apps vacía", 400);
    }

    $insertStmt = $db->prepare("INSERT INTO apps (device_id, package_name, app_name, is_system_app, category) 
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(device_id, package_name) DO UPDATE SET 
        app_name = excluded.app_name, 
        is_system_app = excluded.is_system_app,
        category = excluded.category");

    $db->beginTransaction();
    foreach ($apps as $app) {
        $pkg = isset($app['package_name']) ? trim($app['package_name']) : '';
        $name = isset($app['app_name']) ? trim($app['app_name']) : $pkg;
        $isSys = isset($app['is_system_app']) ? (int)$app['is_system_app'] : 0;
        $cat = isset($app['category']) ? trim($app['category']) : 'other';

        if (!empty($pkg)) {
            $insertStmt->execute([$device['id'], $pkg, $name, $isSys, $cat]);
        }
    }
    $db->commit();

    // Devolver las reglas actuales de apps para que el móvil las aplique
    $rulesStmt = $db->prepare("SELECT package_name, is_blocked, daily_limit_minutes, used_today_minutes FROM apps WHERE device_id = ?");
    $rulesStmt->execute([$device['id']]);
    $rules = $rulesStmt->fetchAll();

    jsonResponse([
        'success' => true,
        'message' => 'Aplicaciones sincronizadas',
        'rules' => $rules
    ]);
}

// --- RUTA ANDROID: Obtener reglas de apps ---
if ($action === 'get_rules' && $method === 'GET') {
    $device = authenticateDevice();
    $stmt = $db->prepare("SELECT package_name, is_blocked, daily_limit_minutes, used_today_minutes FROM apps WHERE device_id = ?");
    $stmt->execute([$device['id']]);
    $rules = $stmt->fetchAll();

    jsonResponse([
        'success' => true,
        'rules' => $rules
    ]);
}

// --- RUTAS DEL PANEL DE CONTROL DE PADRES ---
$user = authenticateParent();
if (!$user) {
    jsonError("No autorizado", 401);
}

if ($method === 'GET') {
    $deviceId = isset($_GET['device_id']) ? (int)$_GET['device_id'] : 0;
    if (empty($deviceId)) jsonError("Falta device_id", 400);

    $stmt = $db->prepare("SELECT * FROM apps WHERE device_id = ? ORDER BY is_blocked DESC, daily_limit_minutes DESC, used_today_minutes DESC, app_name ASC");
    $stmt->execute([$deviceId]);
    $apps = $stmt->fetchAll();

    jsonResponse([
        'success' => true,
        'apps' => $apps
    ]);
}

if ($method === 'POST') {
    $input = getJsonInput();

    if ($action === 'update_rule') {
        $appId = isset($input['app_id']) ? (int)$input['app_id'] : 0;
        $isBlocked = isset($input['is_blocked']) ? (int)$input['is_blocked'] : null;
        $dailyLimit = isset($input['daily_limit_minutes']) ? (int)$input['daily_limit_minutes'] : null;

        if (empty($appId)) jsonError("Falta app_id", 400);

        // Obtener app para saber el device_id
        $appStmt = $db->prepare("SELECT device_id, package_name, app_name FROM apps WHERE id = ?");
        $appStmt->execute([$appId]);
        $app = $appStmt->fetch();

        if (!$app) jsonError("Aplicación no encontrada", 404);

        if ($isBlocked !== null && $dailyLimit !== null) {
            $stmt = $db->prepare("UPDATE apps SET is_blocked = ?, daily_limit_minutes = ? WHERE id = ?");
            $stmt->execute([$isBlocked, $dailyLimit, $appId]);
        } elseif ($isBlocked !== null) {
            $stmt = $db->prepare("UPDATE apps SET is_blocked = ? WHERE id = ?");
            $stmt->execute([$isBlocked, $appId]);
        } elseif ($dailyLimit !== null) {
            $stmt = $db->prepare("UPDATE apps SET daily_limit_minutes = ? WHERE id = ?");
            $stmt->execute([$dailyLimit, $appId]);
        }

        // Encolar comando de actualización para el teléfono
        $db->prepare("INSERT INTO commands (device_id, command_type, payload) VALUES (?, 'sync_rules', ?)")
           ->execute([$app['device_id'], json_encode(['updated_pkg' => $app['package_name']])]);

        jsonResponse([
            'success' => true,
            'message' => 'Regla de aplicación actualizada con éxito'
        ]);
    }

    if ($action === 'bulk_toggle_category') {
        $deviceId = isset($input['device_id']) ? (int)$input['device_id'] : 0;
        $category = isset($input['category']) ? trim($input['category']) : '';
        $isBlocked = isset($input['is_blocked']) ? (int)$input['is_blocked'] : 0;

        if (empty($deviceId) || empty($category)) jsonError("Datos incompletos", 400);

        $stmt = $db->prepare("UPDATE apps SET is_blocked = ? WHERE device_id = ? AND category = ?");
        $stmt->execute([$isBlocked, $deviceId, $category]);

        // Encolar comando
        $db->prepare("INSERT INTO commands (device_id, command_type) VALUES (?, 'sync_rules')")
           ->execute([$deviceId]);

        jsonResponse([
            'success' => true,
            'message' => 'Categoría actualizada'
        ]);
    }
}

jsonError("Acción no válida", 404);
