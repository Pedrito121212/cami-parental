-- Esquema de Base de Datos para Control Parental
-- Compatible con SQLite y MySQL / MariaDB

-- 1. Usuarios Administradores (Padres / Tutores)
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) DEFAULT NULL,
    password_hash VARCHAR(255) NOT NULL,
    session_token VARCHAR(255) DEFAULT NULL,
    session_expires DATETIME DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 2. Dispositivos de los Menores
CREATE TABLE IF NOT EXISTS devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER DEFAULT 1,
    device_name VARCHAR(150) NOT NULL,
    child_name VARCHAR(150) NOT NULL,
    model VARCHAR(150) DEFAULT 'Android Device',
    android_version VARCHAR(50) DEFAULT 'Android 13',
    pairing_token VARCHAR(64) NOT NULL UNIQUE,
    is_device_owner INTEGER DEFAULT 0, -- 1 si tiene permisos Device Owner / DPC
    battery_level INTEGER DEFAULT 100,
    is_charging INTEGER DEFAULT 0,
    is_screen_on INTEGER DEFAULT 1,
    current_app VARCHAR(255) DEFAULT NULL,
    is_locked INTEGER DEFAULT 0, -- 1 = Bloqueo total de emergencia activado
    lock_message VARCHAR(255) DEFAULT 'Dispositivo bloqueado por tus padres',
    safesearch_enabled INTEGER DEFAULT 1,
    vpn_filter_enabled INTEGER DEFAULT 1,
    bonus_minutes_remaining INTEGER DEFAULT 0,
    bonus_expires_at DATETIME DEFAULT NULL,
    status VARCHAR(50) DEFAULT 'active', -- 'active', 'pending_pairing', 'revoked'
    last_seen DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 3. Aplicaciones Instaladas en los Dispositivos
CREATE TABLE IF NOT EXISTS apps (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id INTEGER NOT NULL,
    package_name VARCHAR(255) NOT NULL,
    app_name VARCHAR(255) NOT NULL,
    category VARCHAR(100) DEFAULT 'other', -- 'social', 'games', 'video', 'education', 'system'
    is_system_app INTEGER DEFAULT 0,
    is_blocked INTEGER DEFAULT 0, -- 1 = Totalmente bloqueada
    daily_limit_minutes INTEGER DEFAULT 0, -- 0 = Sin límite de tiempo
    used_today_minutes INTEGER DEFAULT 0,
    last_used DATETIME DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(device_id, package_name),
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- 4. Horarios y Rutinas Semanales (Bedtime, Escuela, etc.)
CREATE TABLE IF NOT EXISTS schedules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id INTEGER NOT NULL,
    name VARCHAR(150) NOT NULL, -- ej: 'Hora de Dormir', 'Clases Escolares'
    days_of_week VARCHAR(50) NOT NULL, -- '1,2,3,4,5' (Lunes a Viernes) o '0,6' (Fin de semana)
    start_time VARCHAR(10) NOT NULL, -- '22:00'
    end_time VARCHAR(10) NOT NULL,   -- '07:00'
    rule_type VARCHAR(50) DEFAULT 'bedtime', -- 'bedtime', 'school', 'focus'
    is_active INTEGER DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- 5. Filtro de Páginas Web (Lista Negra y Blanca de Dominios)
CREATE TABLE IF NOT EXISTS web_filters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id INTEGER NOT NULL,
    domain VARCHAR(255) NOT NULL,
    filter_type VARCHAR(20) DEFAULT 'blacklist', -- 'blacklist', 'whitelist'
    is_active INTEGER DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- 6. Categorías de Filtro Web Predefinidas
CREATE TABLE IF NOT EXISTS web_categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id INTEGER NOT NULL,
    category_key VARCHAR(50) NOT NULL, -- 'adult', 'gambling', 'games', 'social', 'violence'
    category_name VARCHAR(100) NOT NULL,
    is_blocked INTEGER DEFAULT 1,
    UNIQUE(device_id, category_key),
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- 7. Cola de Comandos Remotos Instantáneos
CREATE TABLE IF NOT EXISTS commands (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id INTEGER NOT NULL,
    command_type VARCHAR(50) NOT NULL, -- 'lock_screen', 'unlock_screen', 'grant_bonus', 'sync_apps', 'ring_alarm'
    payload TEXT DEFAULT NULL, -- JSON con parámetros adicionales
    status VARCHAR(20) DEFAULT 'pending', -- 'pending', 'sent', 'executed', 'failed'
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    executed_at DATETIME DEFAULT NULL,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- 8. Reportes Históricos de Tiempo de Pantalla
CREATE TABLE IF NOT EXISTS usage_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id INTEGER NOT NULL,
    package_name VARCHAR(255) NOT NULL,
    app_name VARCHAR(255) NOT NULL,
    date_recorded DATE NOT NULL,
    minutes_used INTEGER DEFAULT 0,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- 9. Registro de Seguridad y Alertas de Evasión
CREATE TABLE IF NOT EXISTS security_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id INTEGER NOT NULL,
    event_type VARCHAR(50) NOT NULL, -- 'tamper_attempt', 'uninstall_attempt', 'vpn_disabled', 'limit_reached'
    description TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- La cuenta de administrador inicial se crea automáticamente al primer inicio de sesión con password_hash()
