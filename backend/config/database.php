<?php
/**
 * Configuración de Base de Datos y Helpers de API
 * Compatible con PHP 7.4 y PHP 8.x
 * Soporta SQLite (sin configuración) y MySQL/MariaDB (cPanel / Hosting)
 */

function setupApiHeaders() {
    header("Access-Control-Allow-Origin: *");
    header("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS");
    header("Access-Control-Allow-Headers: Content-Type, Authorization, X-Device-Token");
    header("Content-Type: application/json; charset=UTF-8");

    if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
        http_response_code(200);
        exit();
    }
}

class Database {
    private static $driver = 'sqlite'; // 'sqlite' o 'mysql'
    private static $host = 'localhost';
    private static $db_name = 'parental_control';
    private static $username = 'root';
    private static $password = '';
    private static $charset = 'utf8mb4';

    private static $sqlite_file = __DIR__ . '/parental.sqlite';
    private static $pdo = null;

    public static function getConnection() {
        if (self::$pdo !== null) {
            return self::$pdo;
        }

        try {
            if (self::$driver === 'mysql') {
                $dsn = "mysql:host=" . self::$host . ";dbname=" . self::$db_name . ";charset=" . self::$charset;
                self::$pdo = new PDO($dsn, self::$username, self::$password, [
                    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                    PDO::ATTR_EMULATE_PREPARES => false,
                ]);
            } else {
                // Asegurar permisos en directorio de SQLite en servidores Linux
                if (!file_exists(self::$sqlite_file)) {
                    @touch(self::$sqlite_file);
                    @chmod(self::$sqlite_file, 0666);
                }

                $dsn = "sqlite:" . self::$sqlite_file;
                self::$pdo = new PDO($dsn, null, null, [
                    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                ]);
                self::$pdo->exec("PRAGMA foreign_keys = ON;");
            }
            
            // Auto-inicializar tablas si no existen
            self::checkAndInitTables();

            return self::$pdo;
        } catch (Exception $e) {
            http_response_code(500);
            echo json_encode([
                'success' => false,
                'error' => 'Error en base de datos: ' . $e->getMessage()
            ], JSON_UNESCAPED_UNICODE);
            exit();
        }
    }

    private static function checkAndInitTables() {
        $schemaFile = __DIR__ . '/schema.sql';
        if (file_exists($schemaFile)) {
            try {
                self::$pdo->query("SELECT 1 FROM users LIMIT 1");
            } catch (Exception $e) {
                // Ejecutar schema instrucción por instrucción para máxima compatibilidad
                $sqlContent = file_get_contents($schemaFile);
                $statements = array_filter(array_map('trim', explode(';', $sqlContent)));
                foreach ($statements as $stmt) {
                    if (!empty($stmt)) {
                        try {
                            self::$pdo->exec($stmt);
                        } catch (Exception $ex) {
                            // Continuar con siguientes
                        }
                    }
                }

                // Crear usuario admin inicial con hash bcrypt válido
                try {
                    $passHash = password_hash('admin123', PASSWORD_BCRYPT);
                    self::$pdo->prepare("INSERT OR IGNORE INTO users (id, username, password_hash) VALUES (1, 'admin', ?)")
                              ->execute([$passHash]);
                } catch (Exception $ex) {
                    // Ignorar si ya existe
                }
            }
        }
    }
}

function jsonResponse($data, $statusCode = 200) {
    http_response_code($statusCode);
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
    exit();
}

function jsonError($message, $statusCode = 400, $extra = []) {
    http_response_code($statusCode);
    echo json_encode(array_merge([
        'success' => false,
        'error' => $message
    ], $extra), JSON_UNESCAPED_UNICODE);
    exit();
}

function getJsonInput() {
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);
    return is_array($data) ? $data : [];
}

function authenticateParent() {
    $headers = getallheaders();
    $authHeader = isset($headers['Authorization']) ? $headers['Authorization'] : (isset($headers['authorization']) ? $headers['authorization'] : '');
    
    $token = '';
    if (preg_match('/Bearer\s(\S+)/', $authHeader, $matches)) {
        $token = $matches[1];
    } elseif (isset($_COOKIE['parent_session'])) {
        $token = $_COOKIE['parent_session'];
    }

    $db = Database::getConnection();

    // Si no hay usuarios en la BD, creamos el admin inicial
    $stmt = $db->query("SELECT COUNT(*) as count FROM users");
    $row = $stmt->fetch();
    if ($row && $row['count'] == 0) {
        $passHash = password_hash('admin123', PASSWORD_BCRYPT);
        $initToken = bin2hex(random_bytes(32));
        $db->prepare("INSERT INTO users (username, password_hash, session_token, session_expires) VALUES ('admin', ?, ?, datetime('now', '+30 days'))")
           ->execute([$passHash, $initToken]);
        return ['id' => 1, 'username' => 'admin', 'session_token' => $initToken];
    }

    if (!empty($token)) {
        $stmt = $db->prepare("SELECT id, username, email FROM users WHERE session_token = ?");
        $stmt->execute([$token]);
        $user = $stmt->fetch();
        if ($user) {
            return $user;
        }
    }

    return null;
}

function authenticateDevice() {
    $headers = getallheaders();
    $deviceToken = isset($headers['X-Device-Token']) ? $headers['X-Device-Token'] : (isset($headers['x-device-token']) ? $headers['x-device-token'] : '');
    
    if (empty($deviceToken) && isset($_GET['device_token'])) {
        $deviceToken = $_GET['device_token'];
    }

    if (empty($deviceToken)) {
        jsonError("Falta la cabecera X-Device-Token", 401);
    }

    $db = Database::getConnection();
    $stmt = $db->prepare("SELECT * FROM devices WHERE pairing_token = ? AND status != 'revoked'");
    $stmt->execute([$deviceToken]);
    $device = $stmt->fetch();

    if (!$device) {
        jsonError("Dispositivo no autorizado o revocado", 401);
    }

    return $device;
}
