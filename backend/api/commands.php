<?php
require_once __DIR__ . '/../config/database.php';
setupApiHeaders();

$method = $_SERVER['REQUEST_METHOD'];
$action = isset($_GET['action']) ? $_GET['action'] : '';
$db = Database::getConnection();

// --- ACCIONES DE DISPOSITIVO ANDROID ---
if ($action === 'fetch_pending' && $method === 'GET') {
    $device = authenticateDevice();
    
    $stmt = $db->prepare("SELECT id, command_type, payload FROM commands WHERE device_id = ? AND status IN ('pending', 'sent') ORDER BY id ASC");
    $stmt->execute([$device['id']]);
    $commands = $stmt->fetchAll();

    jsonResponse([
        'success' => true,
        'commands' => $commands
    ]);
}

if ($action === 'confirm_executed' && $method === 'POST') {
    $device = authenticateDevice();
    $input = getJsonInput();
    $commandId = isset($input['command_id']) ? (int)$input['command_id'] : 0;

    $stmt = $db->prepare("UPDATE commands SET status = 'executed', executed_at = datetime('now') WHERE id = ? AND device_id = ?");
    $stmt->execute([$commandId, $device['id']]);

    jsonResponse([
        'success' => true,
        'message' => 'Comando confirmado como ejecutado'
    ]);
}

// --- ACCIONES DEL PADRE / TUTOR ---
$user = authenticateParent();
if (!$user) {
    jsonError("No autorizado", 401);
}

if ($method === 'POST') {
    $input = getJsonInput();
    $deviceId = isset($input['device_id']) ? (int)$input['device_id'] : 0;
    $commandType = isset($input['command_type']) ? trim($input['command_type']) : '';
    $payload = isset($input['payload']) ? (is_array($input['payload']) ? json_encode($input['payload']) : $input['payload']) : null;

    if (empty($deviceId) || empty($commandType)) {
        jsonError("Se requiere device_id y command_type", 400);
    }

    $stmt = $db->prepare("INSERT INTO commands (device_id, command_type, payload) VALUES (?, ?, ?)");
    $stmt->execute([$deviceId, $commandType, $payload]);
    $cmdId = $db->lastInsertId();

    jsonResponse([
        'success' => true,
        'command_id' => $cmdId,
        'message' => 'Comando enviado a la cola del dispositivo'
    ]);
}

jsonError("Acción no válida", 404);
