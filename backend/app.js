/**
 * Cami - Control Parental Web App (Vanilla JS)
 * Totalmente optimizado para iPhone (Safari) y navegadores de escritorio
 */

// Estado Global
const state = {
    token: localStorage.getItem('cami_token') || '',
    user: null,
    devices: [],
    activeDeviceId: null,
    activeDeviceData: null,
    currentTab: 'dashboard',
    pollInterval: null,
    selectedAppForLimit: null
};

// Rutas de API
const API_BASE = 'api';

// Inicialización al cargar la página
document.addEventListener('DOMContentLoaded', () => {
    initApp();
    setupEventListeners();
    checkIosPwaBanner();
});

// Inicialización de la App
async function initApp() {
    if (state.token) {
        const isValid = await checkSession();
        if (isValid) {
            showAppLayout();
            await loadDevices();
        } else {
            showLoginScreen();
        }
    } else {
        showLoginScreen();
    }
}

// Configuración de Event Listeners
function setupEventListeners() {
    // Formulario de Login
    const loginForm = document.getElementById('login-form');
    if (loginForm) {
        loginForm.addEventListener('submit', handleLogin);
    }

    // Botón de Logout
    const btnLogout = document.getElementById('btn-logout');
    if (btnLogout) {
        btnLogout.addEventListener('click', handleLogout);
    }

    // Selector de Dispositivo
    const deviceSelect = document.getElementById('device-select');
    if (deviceSelect) {
        deviceSelect.addEventListener('change', (e) => {
            state.activeDeviceId = parseInt(e.target.value);
            loadDeviceDetails(state.activeDeviceId);
        });
    }

    // Botón Refrescar
    const btnRefresh = document.getElementById('btn-refresh');
    if (btnRefresh) {
        btnRefresh.addEventListener('click', () => {
            if (state.activeDeviceId) loadDeviceDetails(state.activeDeviceId);
        });
    }

    // Botón de Bloqueo / Desbloqueo de Emergencia
    const btnToggleLock = document.getElementById('btn-toggle-lock');
    if (btnToggleLock) {
        btnToggleLock.addEventListener('click', handleToggleLock);
    }

    // Botones de Concesión de Tiempo Extra
    document.querySelectorAll('.btn-bonus').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const mins = parseInt(e.target.dataset.mins);
            handleGrantBonus(mins);
        });
    });

    // Botón Eliminar Dispositivo
    const btnDeleteDev = document.getElementById('btn-delete-device');
    if (btnDeleteDev) {
        btnDeleteDev.addEventListener('click', handleDeleteActiveDevice);
    }

    // Switches Rápidos en Dashboard
    const quickSafeSearch = document.getElementById('quick-safesearch');
    if (quickSafeSearch) {
        quickSafeSearch.addEventListener('change', (e) => {
            handleToggleSafeSearch(e.target.checked);
        });
    }

    // Modal de Vinculación
    const btnPairModal = document.getElementById('btn-pair-modal');
    if (btnPairModal) {
        btnPairModal.addEventListener('click', () => openModal('modal-pairing'));
    }

    const btnGenQR = document.getElementById('btn-generate-qr');
    if (btnGenQR) {
        btnGenQR.addEventListener('click', handleGeneratePairing);
    }

    // Buscador de Apps
    const appSearch = document.getElementById('app-search-input');
    if (appSearch) {
        appSearch.addEventListener('input', (e) => filterAppsList(e.target.value));
    }

    // Filtros de Categorías de Apps
    document.querySelectorAll('.category-chips .chip').forEach(chip => {
        chip.addEventListener('click', (e) => {
            document.querySelectorAll('.category-chips .chip').forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            filterAppsByCategory(chip.dataset.cat);
        });
    });

    // Guardar Límite de App
    const btnSaveLimit = document.getElementById('btn-save-app-limit');
    if (btnSaveLimit) {
        btnSaveLimit.addEventListener('click', handleSaveAppLimit);
    }

    // Modal de Horario
    const btnNewSchedule = document.getElementById('btn-new-schedule');
    if (btnNewSchedule) {
        btnNewSchedule.addEventListener('click', () => {
            document.getElementById('schedule-form').reset();
            document.getElementById('schedule-id').value = '0';
            document.getElementById('schedule-modal-title').textContent = 'Nuevo Horario de Bloqueo';
            openModal('modal-schedule');
        });
    }

    const scheduleForm = document.getElementById('schedule-form');
    if (scheduleForm) {
        scheduleForm.addEventListener('submit', handleSaveSchedule);
    }

    // Formulario de Dominios Web
    const domainForm = document.getElementById('add-domain-form');
    if (domainForm) {
        domainForm.addEventListener('submit', handleAddDomain);
    }
}

// Helpers de Petición HTTP Fetch
async function apiFetch(endpoint, options = {}) {
    const headers = {
        'Content-Type': 'application/json',
        ...(state.token ? { 'Authorization': `Bearer ${state.token}` } : {})
    };

    try {
        const response = await fetch(`${API_BASE}/${endpoint}`, {
            ...options,
            headers: { ...headers, ...options.headers }
        });

        const rawText = await response.text();
        let data;
        try {
            data = JSON.parse(rawText);
        } catch (jsonErr) {
            console.error('Respuesta no JSON del servidor:', rawText);
            return { 
                success: false, 
                error: rawText.length > 0 ? rawText.substring(0, 150) : `Error HTTP ${response.status}` 
            };
        }

        if (response.status === 401 && !endpoint.includes('action=login')) {
            handleLogout();
            return null;
        }
        return data;
    } catch (err) {
        console.error('API Error:', err);
        return { success: false, error: 'Error de conexión: ' + (err.message || 'Verifica tu conexión') };
    }
}

// Autenticación
async function handleLogin(e) {
    e.preventDefault();
    const userIn = document.getElementById('username').value.trim();
    const passIn = document.getElementById('password').value;
    const errBox = document.getElementById('login-error');
    const btnLogin = document.getElementById('btn-login');

    btnLogin.disabled = true;
    btnLogin.innerText = 'Verificando...';
    errBox.classList.add('hidden');

    const res = await apiFetch('auth.php?action=login', {
        method: 'POST',
        body: JSON.stringify({ username: userIn, password: passIn })
    });

    btnLogin.disabled = false;
    btnLogin.innerHTML = `<span>Iniciar Sesión</span><svg class="btn-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M12 5l7 7-7 7"/></svg>`;

    if (res && res.success) {
        state.token = res.token;
        state.user = res.user;
        localStorage.setItem('cami_token', res.token);
        showAppLayout();
        await loadDevices();
    } else {
        errBox.textContent = res ? res.error : 'Usuario o contraseña inválidos';
        errBox.classList.remove('hidden');
    }
}

async function checkSession() {
    const res = await apiFetch('auth.php?action=check_session');
    if (res && res.authenticated) {
        state.user = res.user;
        return true;
    }
    return false;
}

function handleLogout() {
    state.token = '';
    state.user = null;
    localStorage.removeItem('cami_token');
    clearInterval(state.pollInterval);
    showLoginScreen();
}

function showLoginScreen() {
    document.getElementById('login-screen').classList.remove('hidden');
    document.getElementById('app-container').classList.add('hidden');
}

function showAppLayout() {
    document.getElementById('login-screen').classList.add('hidden');
    document.getElementById('app-container').classList.remove('hidden');
}

// Carga de Dispositivos
async function loadDevices() {
    const res = await apiFetch('devices.php?action=list');
    const select = document.getElementById('device-select');
    select.innerHTML = '';

    if (res && res.success && res.devices.length > 0) {
        state.devices = res.devices;
        res.devices.forEach(dev => {
            const opt = document.createElement('option');
            opt.value = dev.id;
            opt.textContent = `📱 ${dev.child_name} (${dev.device_name})`;
            select.appendChild(opt);
        });

        if (!state.activeDeviceId || !res.devices.some(d => d.id === state.activeDeviceId)) {
            state.activeDeviceId = res.devices[0].id;
        }

        select.value = state.activeDeviceId;
        await loadDeviceDetails(state.activeDeviceId);
        startPolling();
    } else {
        // No hay dispositivos vinculados aún -> Sembrar demo automáticamente para pruebas
        await apiFetch('install.php?action=seed_demo_device');
        const retryRes = await apiFetch('devices.php?action=list');
        if (retryRes && retryRes.devices && retryRes.devices.length > 0) {
            state.devices = retryRes.devices;
            retryRes.devices.forEach(dev => {
                const opt = document.createElement('option');
                opt.value = dev.id;
                opt.textContent = `📱 ${dev.child_name} (${dev.device_name})`;
                select.appendChild(opt);
            });
            state.activeDeviceId = retryRes.devices[0].id;
            select.value = state.activeDeviceId;
            await loadDeviceDetails(state.activeDeviceId);
            startPolling();
        } else {
            select.innerHTML = '<option value="">Sin dispositivos</option>';
            openModal('modal-pairing');
        }
    }
}

// Cargar detalles completos del dispositivo activo
async function loadDeviceDetails(deviceId) {
    if (!deviceId) return;
    const res = await apiFetch(`devices.php?action=get&id=${deviceId}`);
    if (res && res.success) {
        state.activeDeviceData = res;
        renderDeviceHero(res.device);
        renderQuickStats(res);
        renderTopApps(res.apps);
        renderAllApps(res.apps);
        renderSchedules(res.schedules);
        renderWebFilter(res.web_categories, res.web_domains, res.device);
        if (state.currentTab === 'stats') {
            loadStats(deviceId);
        }
    }
}

// Render de la tarjeta Hero (Estado en vivo)
function renderDeviceHero(dev) {
    document.getElementById('child-name-display').textContent = dev.child_name;
    document.getElementById('device-model-display').textContent = `${dev.model} • ${dev.android_version}`;
    document.getElementById('child-avatar').textContent = dev.child_name.charAt(0).toUpperCase();

    // Estado en línea
    const onlineBadge = document.getElementById('online-badge');
    const onlineText = document.getElementById('online-text');
    if (parseInt(dev.is_online) === 1) {
        onlineBadge.className = 'badge online';
        onlineText.textContent = 'En Línea';
    } else {
        onlineBadge.className = 'badge';
        onlineText.textContent = 'Desconectado';
    }

    // Badge DPC (Device Owner)
    const dpcBadge = document.getElementById('dpc-badge');
    if (parseInt(dev.is_device_owner) === 1) {
        dpcBadge.classList.remove('hidden');
    } else {
        dpcBadge.classList.add('hidden');
    }

    // Telemetría
    document.getElementById('battery-val').textContent = `${dev.battery_level}%${dev.is_charging ? ' ⚡' : ''}`;
    document.getElementById('active-app-val').textContent = formatAppName(dev.current_app);

    // Botón de Bloqueo
    const btnLock = document.getElementById('btn-toggle-lock');
    const lockText = document.getElementById('lock-btn-text');
    if (parseInt(dev.is_locked) === 1) {
        btnLock.className = 'btn btn-lock is-locked btn-lg';
        lockText.textContent = 'Desbloquear Móvil';
    } else {
        btnLock.className = 'btn btn-lock is-unlocked btn-lg';
        lockText.textContent = 'Bloquear Dispositivo';
    }

    // SafeSearch Quick toggle
    const quickSafeSearch = document.getElementById('quick-safesearch');
    if (quickSafeSearch) quickSafeSearch.checked = parseInt(dev.safesearch_enabled) === 1;
}

function formatAppName(pkg) {
    if (!pkg) return 'Pantalla Bloqueada';
    const parts = pkg.split('.');
    const raw = parts[parts.length - 1];
    return raw.charAt(0).toUpperCase() + raw.slice(1);
}

// Render de resumen y Top Apps
function renderQuickStats(data) {
    let totalMins = 0;
    if (data.apps) {
        totalMins = data.apps.reduce((sum, app) => sum + (parseInt(app.used_today_minutes) || 0), 0);
    }
    const hours = Math.floor(totalMins / 60);
    const mins = totalMins % 60;
    document.getElementById('screen-time-val').textContent = `${hours}h ${mins}m`;

    // Mini lista de horarios
    const quickSched = document.getElementById('quick-schedules-list');
    if (data.schedules && data.schedules.length > 0) {
        quickSched.innerHTML = data.schedules.slice(0, 2).map(s => `
            <div class="toggle-row">
                <div>
                    <strong>${s.name}</strong>
                    <p class="text-xs text-muted">${s.start_time} a ${s.end_time}</p>
                </div>
                <span class="badge ${parseInt(s.is_active) ? 'online' : ''}">${parseInt(s.is_active) ? 'Activo' : 'Pausado'}</span>
            </div>
        `).join('');
    } else {
        quickSched.innerHTML = '<p class="text-muted text-sm">No hay horarios programados</p>';
    }
}

function renderTopApps(apps) {
    const list = document.getElementById('top-apps-list');
    if (!apps || apps.length === 0) {
        list.innerHTML = '<p class="text-muted text-sm">No hay datos de aplicaciones aún</p>';
        return;
    }

    const top = apps.slice(0, 4);
    list.innerHTML = top.map(app => `
        <div class="app-card">
            <div class="app-card-left">
                <div class="app-avatar">${getAppEmoji(app.category)}</div>
                <div class="app-meta">
                    <h4>${app.app_name}</h4>
                    <p>${app.used_today_minutes} min usados hoy ${app.daily_limit_minutes > 0 ? `(Límite: ${app.daily_limit_minutes}m)` : ''}</p>
                </div>
            </div>
            <div class="app-card-actions">
                <label class="switch">
                    <input type="checkbox" ${parseInt(app.is_blocked) ? '' : 'checked'} onchange="toggleAppBlock(${app.id}, this.checked)">
                    <span class="slider"></span>
                </label>
            </div>
        </div>
    `).join('');
}

// Render de Lista Completa de Apps
function renderAllApps(apps) {
    const list = document.getElementById('all-apps-list');
    if (!apps || apps.length === 0) {
        list.innerHTML = '<p class="text-muted text-sm">No hay aplicaciones registradas</p>';
        return;
    }

    list.innerHTML = apps.map(app => `
        <div class="app-card" data-category="${app.category}" data-name="${app.app_name.toLowerCase()}" data-blocked="${app.is_blocked}">
            <div class="app-card-left">
                <div class="app-avatar">${getAppEmoji(app.category)}</div>
                <div class="app-meta">
                    <h4>${app.app_name}</h4>
                    <p class="text-xs text-muted">${app.package_name}</p>
                </div>
            </div>
            <div class="app-card-actions">
                <button class="limit-badge ${app.daily_limit_minutes > 0 ? 'has-limit' : ''}" onclick="openAppLimitModal(${app.id}, '${app.app_name}', ${app.daily_limit_minutes})">
                    ⏱️ ${app.daily_limit_minutes > 0 ? app.daily_limit_minutes + ' min/día' : 'Sin Límite'}
                </button>
                <label class="switch" title="${parseInt(app.is_blocked) ? 'Bloqueada' : 'Permitida'}">
                    <input type="checkbox" ${parseInt(app.is_blocked) ? '' : 'checked'} onchange="toggleAppBlock(${app.id}, this.checked)">
                    <span class="slider"></span>
                </label>
            </div>
        </div>
    `).join('');
}

function getAppEmoji(category) {
    switch (category) {
        case 'social': return '💬';
        case 'games': return '🎮';
        case 'video': return '🎬';
        case 'education': return '📚';
        case 'system': return '⚙️';
        default: return '📱';
    }
}

// Filtros en la vista de Apps
function filterAppsList(term) {
    const q = term.toLowerCase().trim();
    document.querySelectorAll('#all-apps-list .app-card').forEach(card => {
        const name = card.dataset.name || '';
        card.style.display = name.includes(q) ? 'flex' : 'none';
    });
}

function filterAppsByCategory(cat) {
    document.querySelectorAll('#all-apps-list .app-card').forEach(card => {
        if (cat === 'all') {
            card.style.display = 'flex';
        } else if (cat === 'blocked') {
            card.style.display = card.dataset.blocked === '1' ? 'flex' : 'none';
        } else {
            card.style.display = card.dataset.category === cat ? 'flex' : 'none';
        }
    });
}

// Bloqueo y Límite de Aplicaciones
async function toggleAppBlock(appId, isAllowed) {
    const isBlocked = isAllowed ? 0 : 1;
    await apiFetch('apps.php?action=update_rule', {
        method: 'POST',
        body: JSON.stringify({ app_id: appId, is_blocked: isBlocked })
    });
    // Actualizar datos
    if (state.activeDeviceId) loadDeviceDetails(state.activeDeviceId);
}

function openAppLimitModal(appId, appName, currentLimit) {
    state.selectedAppForLimit = appId;
    document.getElementById('limit-modal-app-name').textContent = `Límite para ${appName}`;
    document.getElementById('custom-limit-minutes').value = currentLimit || 0;
    openModal('modal-app-limit');
}

function setLimitPreset(minutes) {
    document.getElementById('custom-limit-minutes').value = minutes;
}

async function handleSaveAppLimit() {
    if (!state.selectedAppForLimit) return;
    const mins = parseInt(document.getElementById('custom-limit-minutes').value) || 0;
    
    await apiFetch('apps.php?action=update_rule', {
        method: 'POST',
        body: JSON.stringify({ app_id: state.selectedAppForLimit, daily_limit_minutes: mins })
    });

    closeModal('modal-app-limit');
    if (state.activeDeviceId) loadDeviceDetails(state.activeDeviceId);
}

// Render de Horarios
function renderSchedules(schedules) {
    const list = document.getElementById('schedules-list');
    if (!schedules || schedules.length === 0) {
        list.innerHTML = '<p class="text-muted text-sm">No hay horarios creados</p>';
        return;
    }

    const dayLabels = ['D', 'L', 'M', 'X', 'J', 'V', 'S'];

    list.innerHTML = schedules.map(s => {
        const activeDays = (s.days_of_week || '').split(',').map(Number);
        return `
            <div class="schedule-card">
                <div class="schedule-card-top">
                    <div>
                        <h4>${s.name}</h4>
                        <div class="schedule-time">${s.start_time} - ${s.end_time}</div>
                    </div>
                    <label class="switch">
                        <input type="checkbox" ${parseInt(s.is_active) ? 'checked' : ''} onchange="toggleScheduleActive(${s.id}, this.checked)">
                        <span class="slider"></span>
                    </label>
                </div>
                <div class="days-tags">
                    ${dayLabels.map((label, idx) => `
                        <span class="day-pill ${activeDays.includes(idx) ? 'active' : ''}">${label}</span>
                    `).join('')}
                </div>
                <div class="mt-3 text-right">
                    <button class="btn btn-sm btn-secondary" onclick="deleteSchedule(${s.id})">Eliminar</button>
                </div>
            </div>
        `;
    }).join('');
}

async function handleSaveSchedule(e) {
    e.preventDefault();
    const id = parseInt(document.getElementById('schedule-id').value) || 0;
    const name = document.getElementById('schedule-name').value.trim();
    const start = document.getElementById('schedule-start').value;
    const end = document.getElementById('schedule-end').value;
    const type = document.getElementById('schedule-type').value;

    const days = [];
    document.querySelectorAll('input[name="day"]:checked').forEach(cb => days.push(cb.value));

    await apiFetch('rules.php?action=save_schedule', {
        method: 'POST',
        body: JSON.stringify({
            id: id,
            device_id: state.activeDeviceId,
            name: name,
            days_of_week: days.join(','),
            start_time: start,
            end_time: end,
            rule_type: type,
            is_active: 1
        })
    });

    closeModal('modal-schedule');
    if (state.activeDeviceId) loadDeviceDetails(state.activeDeviceId);
}

async function toggleScheduleActive(id, isActive) {
    await apiFetch('rules.php?action=toggle_schedule', {
        method: 'POST',
        body: JSON.stringify({ id: id, is_active: isActive ? 1 : 0 })
    });
}

async function deleteSchedule(id) {
    if (!confirm('¿Deseas eliminar este horario de bloqueo?')) return;
    await apiFetch('rules.php?action=delete_schedule', {
        method: 'POST',
        body: JSON.stringify({ id: id })
    });
    if (state.activeDeviceId) loadDeviceDetails(state.activeDeviceId);
}

// Render de Filtro Web
function renderWebFilter(categories, domains, device) {
    const catList = document.getElementById('web-categories-list');
    if (categories && categories.length > 0) {
        catList.innerHTML = categories.map(cat => `
            <div class="toggle-row">
                <div>
                    <strong>${cat.category_name}</strong>
                </div>
                <label class="switch">
                    <input type="checkbox" ${parseInt(cat.is_blocked) ? 'checked' : ''} onchange="toggleWebCategory('${cat.category_key}', this.checked)">
                    <span class="slider"></span>
                </label>
            </div>
        `).join('');
    }

    const domList = document.getElementById('domains-table');
    if (domains && domains.length > 0) {
        domList.innerHTML = domains.map(d => `
            <div class="domain-row">
                <div>
                    <strong>${d.domain}</strong>
                    <span class="badge text-xs ml-2">${d.filter_type === 'blacklist' ? '🚫 Bloqueado' : '✅ Permitido'}</span>
                </div>
                <button class="btn btn-sm btn-secondary" onclick="deleteDomain(${d.id})">✕</button>
            </div>
        `).join('');
    } else {
        domList.innerHTML = '<p class="text-muted text-sm">No hay dominios personalizados añadidos</p>';
    }
}

async function toggleWebCategory(key, isBlocked) {
    await apiFetch('webfilter.php?action=toggle_category', {
        method: 'POST',
        body: JSON.stringify({
            device_id: state.activeDeviceId,
            category_key: key,
            is_blocked: isBlocked ? 1 : 0
        })
    });
}

async function handleToggleSafeSearch(enabled) {
    await apiFetch('webfilter.php?action=toggle_safesearch', {
        method: 'POST',
        body: JSON.stringify({
            device_id: state.activeDeviceId,
            enabled: enabled ? 1 : 0
        })
    });
}

async function handleAddDomain(e) {
    e.preventDefault();
    const input = document.getElementById('domain-input');
    const type = document.getElementById('domain-type-select').value;
    const domain = input.value.trim();

    if (!domain) return;

    await apiFetch('webfilter.php?action=add_domain', {
        method: 'POST',
        body: JSON.stringify({
            device_id: state.activeDeviceId,
            domain: domain,
            filter_type: type
        })
    });

    input.value = '';
    if (state.activeDeviceId) loadDeviceDetails(state.activeDeviceId);
}

async function deleteDomain(id) {
    await apiFetch('webfilter.php?action=delete_domain', {
        method: 'POST',
        body: JSON.stringify({ id: id })
    });
    if (state.activeDeviceId) loadDeviceDetails(state.activeDeviceId);
}

// Bloqueo Remoto y Tiempo Extra
async function handleToggleLock() {
    if (!state.activeDeviceData) return;
    const isCurrentlyLocked = parseInt(state.activeDeviceData.device.is_locked) === 1;
    const newLockState = isCurrentlyLocked ? 0 : 1;

    await apiFetch('devices.php?action=toggle_lock', {
        method: 'POST',
        body: JSON.stringify({
            device_id: state.activeDeviceId,
            lock: newLockState,
            message: 'Dispositivo bloqueado por tus padres'
        })
    });

    if (state.activeDeviceId) loadDeviceDetails(state.activeDeviceId);
}

async function handleGrantBonus(minutes) {
    if (!state.activeDeviceId) return;
    await apiFetch('devices.php?action=grant_bonus', {
        method: 'POST',
        body: JSON.stringify({
            device_id: state.activeDeviceId,
            minutes: minutes
        })
    });

    alert(`Se concedieron ${minutes} minutos adicionales.`);
    if (state.activeDeviceId) loadDeviceDetails(state.activeDeviceId);
}

async function handleDeleteActiveDevice() {
    if (!state.activeDeviceId) return;
    if (!confirm('¿Deseas desvincular / eliminar este dispositivo del panel?')) return;

    await apiFetch('devices.php?action=delete', {
        method: 'POST',
        body: JSON.stringify({ device_id: state.activeDeviceId })
    });

    state.activeDeviceId = null;
    await loadDevices();
}

// Estadísticas y Canvas Chart
async function loadStats(deviceId) {
    const res = await apiFetch(`telemetry.php?action=get_stats&device_id=${deviceId}`);
    const eventsRes = await apiFetch(`telemetry.php?action=get_events&device_id=${deviceId}`);

    if (res && res.success) {
        renderChart(res.history_7days);
    }

    if (eventsRes && eventsRes.success) {
        renderEventsLog(eventsRes.events);
    }
}

function renderChart(history) {
    const canvas = document.getElementById('usage-chart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const width = canvas.parentElement.clientWidth || 300;
    canvas.width = width;
    canvas.height = 180;

    ctx.clearRect(0, 0, width, 180);

    const data = history && history.length > 0 ? history : [
        { date_recorded: 'L', total_minutes: 140 },
        { date_recorded: 'M', total_minutes: 180 },
        { date_recorded: 'X', total_minutes: 90 },
        { date_recorded: 'J', total_minutes: 210 },
        { date_recorded: 'V', total_minutes: 240 },
        { date_recorded: 'S', total_minutes: 310 },
        { date_recorded: 'D', total_minutes: 180 }
    ];

    const maxVal = Math.max(...data.map(d => parseInt(d.total_minutes) || 0), 100);
    const barWidth = Math.min(32, Math.floor(width / (data.length * 1.8)));
    const gap = Math.floor((width - (barWidth * data.length)) / (data.length + 1));

    data.forEach((d, idx) => {
        const val = parseInt(d.total_minutes) || 0;
        const barHeight = Math.floor((val / maxVal) * 120);
        const x = gap + (idx * (barWidth + gap));
        const y = 140 - barHeight;

        // Barra con degradado rosa y lavanda
        const grad = ctx.createLinearGradient(0, y, 0, 140);
        grad.addColorStop(0, '#f472b6');
        grad.addColorStop(1, '#a855f7');

        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.roundRect(x, y, barWidth, barHeight, [6, 6, 0, 0]);
        ctx.fill();

        // Texto Fecha
        ctx.fillStyle = '#9ca3af';
        ctx.font = '11px sans-serif';
        ctx.textAlign = 'center';
        const label = d.date_recorded.slice(-2);
        ctx.fillText(label, x + (barWidth / 2), 160);

        // Texto Minutos
        ctx.fillStyle = '#ffffff';
        ctx.font = '10px sans-serif';
        ctx.fillText(`${Math.round(val / 60)}h`, x + (barWidth / 2), y - 6);
    });
}

function renderEventsLog(events) {
    const list = document.getElementById('security-events-list');
    if (!events || events.length === 0) {
        list.innerHTML = '<p class="text-muted text-sm">Sin eventos sospechosos registrados</p>';
        return;
    }

    list.innerHTML = events.map(e => `
        <div class="toggle-row">
            <div>
                <strong>${getEventEmoji(e.event_type)} ${e.description}</strong>
                <p class="text-xs text-muted">${e.created_at}</p>
            </div>
        </div>
    `).join('');
}

function getEventEmoji(type) {
    switch (type) {
        case 'tamper_attempt': return '⚠️';
        case 'limit_reached': return '⏱️';
        case 'uninstall_attempt': return '🚫';
        default: return 'ℹ️';
    }
}

// Vinculación QR y Código
async function handleGeneratePairing() {
    const childName = document.getElementById('new-child-name').value.trim() || 'Camilita';
    const devName = document.getElementById('new-device-name').value.trim() || 'Teléfono de Camilita';

    const res = await apiFetch('devices.php?action=create_pairing', {
        method: 'POST',
        body: JSON.stringify({ child_name: childName, device_name: devName })
    });

    if (res && res.success) {
        document.getElementById('qr-result-area').classList.remove('hidden');
        document.getElementById('display-pairing-token').textContent = res.pairing_token;
        renderRealQrCode(res.qr_payload);

        // Configurar enlace de WhatsApp
        const wsspBtn = document.getElementById('btn-whatsapp-pairing');
        if (wsspBtn) {
            const wsspText = encodeURIComponent(`🌸 Hola! Descarga la app de Camilita desde aquí: https://cami.diazsistemas.com/descargar.html\n\nAl abrir la app, escribe este código para vincularla: *${res.pairing_token}*`);
            wsspBtn.href = `https://api.whatsapp.com/send?text=${wsspText}`;
        }
    }
}

function renderRealQrCode(text) {
    const qrBox = document.querySelector('.qr-box');
    if (!qrBox) return;

    // Generar imagen QR estándar de alta precisión escaneable por cualquier cámara
    const encodedData = encodeURIComponent(text);
    const qrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodedData}&margin=8`;

    qrBox.innerHTML = `
        <img src="${qrUrl}" alt="Código QR de Vinculación" style="width: 200px; height: 200px; border-radius: 12px; display: block; margin: 0 auto;" />
    `;
}

function drawQrCorner(ctx, startX, startY, cell) {
    ctx.fillRect(startX * cell, startY * cell, 7 * cell, 7 * cell);
    ctx.fillStyle = '#ffffff';
    ctx.fillRect((startX + 1) * cell, (startY + 1) * cell, 5 * cell, 5 * cell);
    ctx.fillStyle = '#000000';
    ctx.fillRect((startX + 2) * cell, (startY + 2) * cell, 3 * cell, 3 * cell);
}

function copyToken() {
    const token = document.getElementById('display-pairing-token').textContent;
    navigator.clipboard.writeText(token);
    alert('Código copiado al portapapeles: ' + token);
}

// Navegación por Tabs
function switchTab(tabName) {
    state.currentTab = tabName;
    document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));

    const tab = document.getElementById(`tab-${tabName}`);
    if (tab) tab.classList.add('active');

    // Activar icono en la barra inferior
    const navItems = document.querySelectorAll('.bottom-nav .nav-item');
    const tabMap = { 'dashboard': 0, 'apps': 1, 'schedules': 2, 'webfilter': 3, 'stats': 4 };
    if (navItems[tabMap[tabName]]) {
        navItems[tabMap[tabName]].classList.add('active');
    }

    if (tabName === 'stats' && state.activeDeviceId) {
        loadStats(state.activeDeviceId);
    }
}

// Manejo de Modales
function openModal(id) {
    const modal = document.getElementById(id);
    if (modal) modal.classList.remove('hidden');
}

function closeModal(id) {
    const modal = document.getElementById(id);
    if (modal) modal.classList.add('hidden');
}

// Polling en tiempo real cada 3 segundos
function startPolling() {
    if (state.pollInterval) clearInterval(state.pollInterval);
    state.pollInterval = setInterval(() => {
        if (state.activeDeviceId && !document.hidden && state.currentTab === 'dashboard') {
            loadDeviceDetails(state.activeDeviceId);
        }
    }, 3500);
}

// Detección de Safari en iPhone para sugerir instalación PWA
function checkIosPwaBanner() {
    const isIos = /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream;
    const isStandalone = window.navigator.standalone === true;
    if (isIos && !isStandalone) {
        const banner = document.getElementById('ios-pwa-banner');
        if (banner) banner.classList.remove('hidden');
    }
}
