<?php
require_once __DIR__ . '/../config/database.php';
setupApiHeaders();

$action = isset($_GET['action']) ? $_GET['action'] : '';
$db = Database::getConnection();

// Verificar estado de la base de datos
$userCount = $db->query("SELECT COUNT(*) as count FROM users")->fetch()['count'];
$deviceCount = $db->query("SELECT COUNT(*) as count FROM devices")->fetch()['count'];

if ($action === 'seed_demo_device') {
    // Crear un dispositivo de demostración con datos reales para poder interactuar en el panel web
    $childName = "Camilita 🌸";
    $token = "CAMI-DEMO777";

    // Verificar si ya existe
    $stmt = $db->prepare("SELECT id FROM devices WHERE pairing_token = ?");
    $stmt->execute([$token]);
    $dev = $stmt->fetch();

    if (!$dev) {
        $db->prepare("INSERT INTO devices (user_id, device_name, child_name, model, android_version, pairing_token, is_device_owner, battery_level, is_charging, is_screen_on, current_app, is_locked, status, last_seen) 
            VALUES (1, 'Teléfono de Camilita', ?, 'Samsung Galaxy A54', 'Android 14 (OneUI 6)', ?, 1, 88, 1, 1, 'com.google.android.apps.youtube.kids', 0, 'active', datetime('now'))")
           ->execute([$childName, $token]);
        $deviceId = $db->lastInsertId();

        // Insertar aplicaciones típicas
        $demoApps = [
            ['com.google.android.youtube', 'YouTube', 'video', 0, 60, 45],
            ['com.zhiliaoapp.musically', 'TikTok', 'social', 1, 30, 28],
            ['com.instagram.android', 'Instagram', 'social', 0, 40, 15],
            ['com.whatsapp', 'WhatsApp', 'social', 0, 0, 20],
            ['com.roblox.client', 'Roblox', 'games', 0, 45, 45],
            ['com.dts.freefireth', 'Free Fire', 'games', 1, 0, 0],
            ['com.spotify.music', 'Spotify', 'media', 0, 0, 35],
            ['com.duolingo', 'Duolingo', 'education', 0, 0, 18],
            ['com.android.chrome', 'Google Chrome', 'system', 0, 0, 12],
            ['com.brawlstars.supercell', 'Brawl Stars', 'games', 0, 30, 10]
        ];

        foreach ($demoApps as $app) {
            $db->prepare("INSERT INTO apps (device_id, package_name, app_name, category, is_blocked, daily_limit_minutes, used_today_minutes) 
                VALUES (?, ?, ?, ?, ?, ?, ?)")
               ->execute([$deviceId, $app[0], $app[1], $app[2], $app[3], $app[4], $app[5]]);
        }

        // Horarios
        $db->prepare("INSERT INTO schedules (device_id, name, days_of_week, start_time, end_time, rule_type, is_active) 
            VALUES (?, 'Hora de Dormir', '0,1,2,3,4,5,6', '22:00', '07:00', 'bedtime', 1)")->execute([$deviceId]);
        
        $db->prepare("INSERT INTO schedules (device_id, name, days_of_week, start_time, end_time, rule_type, is_active) 
            VALUES (?, 'Horario Escolar', '1,2,3,4,5', '08:00', '14:30', 'school', 1)")->execute([$deviceId]);

        // Categorías web
        $defaultCategories = [
            ['adult', 'Contenido para Adultos / Pornografía', 1],
            ['gambling', 'Apuestas y Casinos', 1],
            ['violence', 'Armas y Violencia', 1],
            ['games', 'Juegos Web Online', 0],
            ['social', 'Redes Sociales Web', 0]
        ];
        foreach ($defaultCategories as $cat) {
            $db->prepare("INSERT INTO web_categories (device_id, category_key, category_name, is_blocked) VALUES (?, ?, ?, ?)")
               ->execute([$deviceId, $cat[0], $cat[1], $cat[2]]);
        }

        // Dominios filtrados
        $db->prepare("INSERT INTO web_filters (device_id, domain, filter_type, is_active) VALUES (?, 'tiktok.com', 'blacklist', 1)")->execute([$deviceId]);
        $db->prepare("INSERT INTO web_filters (device_id, domain, filter_type, is_active) VALUES (?, 'wikipedia.org', 'whitelist', 1)")->execute([$deviceId]);

        // Eventos de seguridad
        $db->prepare("INSERT INTO security_events (device_id, event_type, description) VALUES (?, 'limit_reached', 'Límite diario de Roblox (45 min) alcanzado')")->execute([$deviceId]);
        $db->prepare("INSERT INTO security_events (device_id, event_type, description) VALUES (?, 'tamper_attempt', 'Intento bloqueado de desactivar permisos de accesibilidad')")->execute([$deviceId]);

        // Historial 7 días
        for ($i = 6; $i >= 0; $i--) {
            $date = date('Y-m-d', strtotime("-$i days"));
            $mins = rand(120, 240);
            $db->prepare("INSERT INTO usage_logs (device_id, package_name, app_name, date_recorded, minutes_used) VALUES (?, 'com.google.android.youtube', 'YouTube', ?, ?)")
               ->execute([$deviceId, $date, rand(40, 90)]);
            $db->prepare("INSERT INTO usage_logs (device_id, package_name, app_name, date_recorded, minutes_used) VALUES (?, 'com.roblox.client', 'Roblox', ?, ?)")
               ->execute([$deviceId, $date, rand(30, 60)]);
        }

        jsonResponse([
            'success' => true,
            'message' => 'Dispositivo de demostración creado con éxito',
            'device_id' => $deviceId,
            'child_name' => $childName
        ]);
    } else {
        jsonResponse([
            'success' => true,
            'message' => 'El dispositivo demo ya existe',
            'device_id' => $dev['id']
        ]);
    }
}

jsonResponse([
    'status' => 'ready',
    'system' => 'Cami Parental Control API (PHP)',
    'database' => 'OK',
    'users_count' => $userCount,
    'devices_count' => $deviceCount,
    'default_login' => [
        'username' => 'admin',
        'password' => 'admin123'
    ]
]);
