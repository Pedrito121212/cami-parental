<?php
require_once __DIR__ . '/../config/database.php';
setupApiHeaders();

$method = $_SERVER['REQUEST_METHOD'];
$action = isset($_GET['action']) ? $_GET['action'] : '';
$db = Database::getConnection();

// --- RUTA ANDROID: Obtener reglas de filtrado web DNS ---
if ($action === 'get_filters' && $method === 'GET') {
    $device = authenticateDevice();
    
    // Categorías bloqueadas
    $catStmt = $db->prepare("SELECT category_key, is_blocked FROM web_categories WHERE device_id = ? AND is_blocked = 1");
    $catStmt->execute([$device['id']]);
    $blockedCategories = $catStmt->fetchAll();

    // Dominios específicos
    $domStmt = $db->prepare("SELECT domain, filter_type FROM web_filters WHERE device_id = ? AND is_active = 1");
    $domStmt->execute([$device['id']]);
    $domains = $domStmt->fetchAll();

    $devInfo = $db->query("SELECT safesearch_enabled, vpn_filter_enabled FROM devices WHERE id = " . (int)$device['id'])->fetch();

    jsonResponse([
        'success' => true,
        'safesearch_enabled' => (bool)$devInfo['safesearch_enabled'],
        'vpn_filter_enabled' => (bool)$devInfo['vpn_filter_enabled'],
        'blocked_categories' => $blockedCategories,
        'domains' => $domains
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

    $categories = $db->prepare("SELECT * FROM web_categories WHERE device_id = ?");
    $categories->execute([$deviceId]);

    $domains = $db->prepare("SELECT * FROM web_filters WHERE device_id = ? ORDER BY id DESC");
    $domains->execute([$deviceId]);

    $devInfo = $db->prepare("SELECT safesearch_enabled, vpn_filter_enabled FROM devices WHERE id = ?");
    $devInfo->execute([$deviceId]);

    jsonResponse([
        'success' => true,
        'config' => $devInfo->fetch(),
        'categories' => $categories->fetchAll(),
        'domains' => $domains->fetchAll()
    ]);
}

if ($method === 'POST') {
    $input = getJsonInput();

    if ($action === 'toggle_safesearch') {
        $deviceId = isset($input['device_id']) ? (int)$input['device_id'] : 0;
        $enabled = isset($input['enabled']) ? (int)$input['enabled'] : 1;

        $db->prepare("UPDATE devices SET safesearch_enabled = ? WHERE id = ?")->execute([$enabled, $deviceId]);
        $db->prepare("INSERT INTO commands (device_id, command_type) VALUES (?, 'sync_webfilters')")->execute([$deviceId]);

        jsonResponse(['success' => true, 'safesearch_enabled' => (bool)$enabled]);
    }

    if ($action === 'toggle_category') {
        $deviceId = isset($input['device_id']) ? (int)$input['device_id'] : 0;
        $categoryKey = isset($input['category_key']) ? trim($input['category_key']) : '';
        $isBlocked = isset($input['is_blocked']) ? (int)$input['is_blocked'] : 0;

        $db->prepare("UPDATE web_categories SET is_blocked = ? WHERE device_id = ? AND category_key = ?")
           ->execute([$isBlocked, $deviceId, $categoryKey]);

        $db->prepare("INSERT INTO commands (device_id, command_type) VALUES (?, 'sync_webfilters')")->execute([$deviceId]);

        jsonResponse(['success' => true, 'message' => 'Categoría de filtro web actualizada']);
    }

    if ($action === 'add_domain') {
        $deviceId = isset($input['device_id']) ? (int)$input['device_id'] : 0;
        $domain = isset($input['domain']) ? strtolower(trim($input['domain'])) : '';
        $type = isset($input['filter_type']) ? trim($input['filter_type']) : 'blacklist';

        // Limpiar protocolo si lo pegan con https://
        $domain = preg_replace('#^https?://#i', '', $domain);
        $domain = preg_replace('#^www\.#i', '', $domain);
        $domain = explode('/', $domain)[0];

        if (empty($domain)) jsonError("Dominio inválido", 400);

        $stmt = $db->prepare("INSERT INTO web_filters (device_id, domain, filter_type, is_active) VALUES (?, ?, ?, 1)");
        $stmt->execute([$deviceId, $domain, $type]);
        $filterId = $db->lastInsertId();

        $db->prepare("INSERT INTO commands (device_id, command_type) VALUES (?, 'sync_webfilters')")->execute([$deviceId]);

        jsonResponse([
            'success' => true,
            'filter_id' => $filterId,
            'domain' => $domain,
            'filter_type' => $type
        ]);
    }

    if ($action === 'delete_domain') {
        $id = isset($input['id']) ? (int)$input['id'] : 0;
        $f = $db->query("SELECT device_id FROM web_filters WHERE id = $id")->fetch();
        if ($f) {
            $db->prepare("DELETE FROM web_filters WHERE id = ?")->execute([$id]);
            $db->prepare("INSERT INTO commands (device_id, command_type) VALUES (?, 'sync_webfilters')")->execute([$f['device_id']]);
        }

        jsonResponse(['success' => true, 'message' => 'Filtro eliminado']);
    }
}

jsonError("Acción no válida", 404);
