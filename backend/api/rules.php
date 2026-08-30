<?php
require_once __DIR__ . '/../config/database.php';
setupApiHeaders();

$method = $_SERVER['REQUEST_METHOD'];
$action = isset($_GET['action']) ? $_GET['action'] : '';
$db = Database::getConnection();

// --- RUTA ANDROID: Obtener horarios activos ---
if ($action === 'get_schedules' && $method === 'GET') {
    $device = authenticateDevice();
    $stmt = $db->prepare("SELECT id, name, days_of_week, start_time, end_time, rule_type, is_active FROM schedules WHERE device_id = ? AND is_active = 1");
    $stmt->execute([$device['id']]);
    $schedules = $stmt->fetchAll();

    jsonResponse([
        'success' => true,
        'schedules' => $schedules
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

    $stmt = $db->prepare("SELECT * FROM schedules WHERE device_id = ? ORDER BY start_time ASC");
    $stmt->execute([$deviceId]);
    $schedules = $stmt->fetchAll();

    jsonResponse([
        'success' => true,
        'schedules' => $schedules
    ]);
}

if ($method === 'POST') {
    $input = getJsonInput();

    if ($action === 'save_schedule') {
        $id = isset($input['id']) ? (int)$input['id'] : 0;
        $deviceId = isset($input['device_id']) ? (int)$input['device_id'] : 0;
        $name = isset($input['name']) ? trim($input['name']) : 'Horario';
        $daysOfWeek = isset($input['days_of_week']) ? trim($input['days_of_week']) : '0,1,2,3,4,5,6';
        $startTime = isset($input['start_time']) ? trim($input['start_time']) : '22:00';
        $endTime = isset($input['end_time']) ? trim($input['end_time']) : '07:00';
        $ruleType = isset($input['rule_type']) ? trim($input['rule_type']) : 'bedtime';
        $isActive = isset($input['is_active']) ? (int)$input['is_active'] : 1;

        if (empty($deviceId)) jsonError("Falta device_id", 400);

        if ($id > 0) {
            $stmt = $db->prepare("UPDATE schedules SET name = ?, days_of_week = ?, start_time = ?, end_time = ?, rule_type = ?, is_active = ? WHERE id = ? AND device_id = ?");
            $stmt->execute([$name, $daysOfWeek, $startTime, $endTime, $ruleType, $isActive, $id, $deviceId]);
        } else {
            $stmt = $db->prepare("INSERT INTO schedules (device_id, name, days_of_week, start_time, end_time, rule_type, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)");
            $stmt->execute([$deviceId, $name, $daysOfWeek, $startTime, $endTime, $ruleType, $isActive]);
            $id = $db->lastInsertId();
        }

        // Notificar al móvil
        $db->prepare("INSERT INTO commands (device_id, command_type) VALUES (?, 'sync_schedules')")
           ->execute([$deviceId]);

        jsonResponse([
            'success' => true,
            'schedule_id' => $id,
            'message' => 'Horario guardado correctamente'
        ]);
    }

    if ($action === 'toggle_schedule') {
        $id = isset($input['id']) ? (int)$input['id'] : 0;
        $isActive = isset($input['is_active']) ? (int)$input['is_active'] : 1;

        $stmt = $db->prepare("UPDATE schedules SET is_active = ? WHERE id = ?");
        $stmt->execute([$isActive, $id]);

        $s = $db->query("SELECT device_id FROM schedules WHERE id = $id")->fetch();
        if ($s) {
            $db->prepare("INSERT INTO commands (device_id, command_type) VALUES (?, 'sync_schedules')")->execute([$s['device_id']]);
        }

        jsonResponse(['success' => true, 'message' => 'Estado de horario actualizado']);
    }

    if ($action === 'delete_schedule') {
        $id = isset($input['id']) ? (int)$input['id'] : 0;
        $s = $db->query("SELECT device_id FROM schedules WHERE id = $id")->fetch();
        if ($s) {
            $db->prepare("DELETE FROM schedules WHERE id = ?")->execute([$id]);
            $db->prepare("INSERT INTO commands (device_id, command_type) VALUES (?, 'sync_schedules')")->execute([$s['device_id']]);
        }

        jsonResponse(['success' => true, 'message' => 'Horario eliminado']);
    }
}

jsonError("Acción no válida", 404);
