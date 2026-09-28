// Plain JavaScript keeps the interface easy to understand and extend.
const $ = id => document.getElementById(id);
let visitsRequest = 0;
let latestPass = null;
let smsEnabled = false;
let communityFlats = [];
let communityResidents = [];
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

function message(text, error = false) {
    $('message').textContent = text;
    $('message').className = 'notice' + (error ? ' error' : '');
    // Restart the short notification animation when the message changes.
    void $('message').offsetWidth;
    $('message').classList.add('pop');
    if (error) $('message').scrollIntoView({block: 'nearest', behavior: reducedMotion.matches ? 'instant' : 'smooth'});
}
async function api(path, method = 'GET', body) {
    let response;
    try {
        response = await fetch('/api' + path, {
            method, headers: {'Content-Type': 'application/json'},
            body: body ? JSON.stringify(body) : undefined
        });
    } catch {
        throw new Error('Cannot reach the server. Check that the Java application is running, then try again.');
    }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || 'The request could not be completed. Please try again.');
    return data;
}
function values(form) { return Object.fromEntries(new FormData(form)); }
function option(select, value, label) {
    const item = document.createElement('option');
    item.value = value; item.textContent = label; select.append(item);
}
function count(id, value) {
    const element = $(id);
    element.textContent = value;
    if (!reducedMotion.matches) element.animate([
        {opacity: .3, transform: 'translateY(5px)'},
        {opacity: 1, transform: 'translateY(0)'}
    ], {duration: 350, easing: 'ease-out'});
}
async function loadSetup(selectedResident) {
    const [flats, residents] = await Promise.all([api('/flats'), api('/residents')]);
    communityFlats = flats; communityResidents = residents;
    const editingFlat = $('flatEdit').value;
    const editingResident = $('residentEdit').value;
    $('flatEdit').replaceChildren(); $('residentEdit').replaceChildren();
    option($('flatEdit'), '', 'Add a new flat');
    option($('residentEdit'), '', 'Add a new resident');
    flats.forEach(f => option($('flatEdit'), f.id, f.number + (f.address ? ' — ' + f.address : '')));
    residents.forEach(r => option($('residentEdit'), r.id, r.name + ' — ' + r.flat.number));
    $('flatEdit').value = editingFlat; $('residentEdit').value = editingResident;
    const previous = selectedResident || $('resident').value;
    const previousFlat = $('flat').value;
    $('flat').replaceChildren(); $('resident').replaceChildren();
    option($('flat'), '', 'Select a flat');
    option($('resident'), '', residents.length ? 'Select a resident' : 'Add a resident in Community setup');
    flats.forEach(f => option($('flat'), f.id, f.number));
    residents.forEach(r => option($('resident'), r.id, r.name + ' — ' + r.flat.number));
    $('resident').value = previous || (residents.length === 1 ? residents[0].id : '');
    $('flat').value = previousFlat || (flats.length === 1 ? flats[0].id : '');
    count('flatCount', flats.length); count('residentCount', residents.length);
    await loadVisits();
}
function date(value) {
    return value ? new Date(value).toLocaleString([], {month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'}) : '—';
}
function cell(row, text, className) {
    const td = document.createElement('td'); td.textContent = text;
    if (className) td.className = className;
    row.append(td); return td;
}
function emptyVisits(title, description) {
    $('visits').replaceChildren();
    const row = document.createElement('tr');
    const td = cell(row, '', 'empty-cell'); td.colSpan = 7;
    const icon = document.createElement('span'); icon.className = 'empty-icon'; icon.textContent = '◇'; icon.setAttribute('aria-hidden', 'true');
    const heading = document.createElement('strong'); heading.textContent = title;
    const detail = document.createElement('small'); detail.textContent = description;
    td.append(icon, heading, detail); $('visits').append(row);
}
async function loadVisits() {
    const request = ++visitsRequest;
    const resident = $('resident').value;
    $('visits').setAttribute('aria-busy', 'true');
    emptyVisits('Loading visits...', 'Getting the latest details.');
    let visits;
    try { visits = resident ? await api('/residents/' + resident + '/approvals') : []; }
    catch (error) {
        if (request === visitsRequest) {
            emptyVisits('Visits could not be loaded', 'Use Refresh to try again.');
            ['pendingCount', 'entryCount', 'visitCount'].forEach(id => count(id, '—'));
        }
        throw error;
    } finally { if (request === visitsRequest) $('visits').setAttribute('aria-busy', 'false'); }
    // Ignore old responses if the resident changed while the request was running.
    if (request !== visitsRequest) return;
    $('visits').replaceChildren();
    count('pendingCount', resident ? visits.filter(v => v.status === 'PENDING').length : '—');
    count('entryCount', resident ? visits.filter(v => v.status === 'ENTERED').length : '—');
    count('visitCount', visits.length);
    if (!visits.length) emptyVisits(resident ? 'Your next welcome starts here' : 'Choose a resident to get started',
        resident ? 'Create a visitor pass and it will appear here.' : 'Select a resident at the Resident desk to see their visits.');
    visits.forEach((v, index) => {
        const row = document.createElement('tr');
        row.style.animationDelay = Math.min(index * 35, 280) + 'ms';
        cell(row, '#' + v.id);
        const visitor = cell(row, v.visitorName);
        const detail = document.createElement('small'); detail.textContent = v.purpose; visitor.append(detail);
        const windowCell = cell(row, date(v.expectedAt));
        const end = document.createElement('small'); end.textContent = 'Until ' + date(v.expiresAt); windowCell.append(end);
        const badge = document.createElement('span'); badge.className = 'badge ' + v.status; badge.textContent = v.status;
        cell(row, '').append(badge);
        cell(row, v.otp || '—', 'otp-cell'); cell(row, date(v.entryTime));
        const action = cell(row, '');
        if (v.status === 'PENDING') {
            const button = document.createElement('button'); button.textContent = 'Revoke'; button.className = 'secondary';
            button.setAttribute('aria-label', 'Revoke visit for ' + v.visitorName);
            button.onclick = () => run(button, async () => {
                await api('/residents/' + resident + '/approvals/' + v.id + '/revoke', 'PUT');
                if (latestPass && latestPass.id === v.id) hidePass();
                message('Visit #' + v.id + ' has been revoked. Its pass can no longer be used.');
                await loadVisits();
            });
            action.append(button);
            const smsButton = document.createElement('button');
            smsButton.type = 'button'; smsButton.className = 'secondary'; smsButton.textContent = 'Send OTP';
            smsButton.setAttribute('aria-label', 'Send OTP by SMS to ' + v.visitorName);
            smsButton.onclick = () => run(smsButton, () => sendPassSms(v));
            action.append(document.createTextNode(' '), smsButton);
        } else action.textContent = '—';
        $('visits').append(row);
    });
    if (latestPass && !visits.some(v => v.id === latestPass.id && v.status === 'PENDING')) hidePass();
}
async function run(button, work) {
    button.disabled = true; button.classList.add('busy'); button.setAttribute('aria-busy', 'true');
    try { await work(); } catch (e) { message(e.message, true); }
    finally { button.disabled = false; button.classList.remove('busy'); button.removeAttribute('aria-busy'); }
}
function submit(id, work) {
    $(id).onsubmit = event => {
        event.preventDefault();
        const form = event.target;
        run(form.querySelector('button[type="submit"]'), () => work(form));
    };
}
function hidePass() { latestPass = null; $('createdPass').hidden = true; }
async function loadSmsStatus() {
    try {
        smsEnabled = (await api('/sms/status')).enabled;
        $('smsHint').textContent = smsEnabled
            ? 'An OTP SMS will be submitted to this visitor when you create a pass. Use a 10-digit Indian mobile number or +country code.'
            : 'SMS is not configured yet. Passes can still be created and copied. See the README SMS setup section.';
    } catch {
        smsEnabled = false;
        $('smsHint').textContent = 'SMS availability could not be checked. You can still create and copy a pass.';
    }
}
async function sendPassSms(visit) {
    if (!visit) return;
    if (latestPass && latestPass.id === visit.id) $('smsResult').textContent = 'Submitting OTP to the SMS provider...';
    try {
        const result = await api('/residents/' + visit.residentId + '/approvals/' + visit.id + '/sms', 'POST');
        if (latestPass && latestPass.id === visit.id) $('smsResult').textContent = result.message;
        message(result.message, result.status !== 'SUBMITTED');
    } catch (e) {
        if (latestPass && latestPass.id === visit.id) $('smsResult').textContent = e.message;
        message('Pass #' + visit.id + ' is saved. ' + e.message, true);
    }
}
$('sendSms').onclick = () => run($('sendSms'), () => sendPassSms(latestPass));
submit('flatForm', async form => {
    const id = $('flatEdit').value;
    const flat = await api('/flats' + (id ? '/' + id : ''), id ? 'PUT' : 'POST', values(form));
    form.reset(); $('flatSave').textContent = 'Add flat'; await loadSetup();
    if (!$('residentEdit').value) $('flat').value = flat.id;
    message(id ? 'Flat ' + flat.number + ' and its address have been saved to the database.'
        : 'Flat ' + flat.number + ' added. Now register a resident for their new home.');
});
submit('residentForm', async form => {
    const id = $('residentEdit').value;
    const body = values(form); body.flatId = Number(body.flatId);
    const resident = await api('/residents' + (id ? '/' + id : ''), id ? 'PUT' : 'POST', body);
    form.reset(); $('residentSave').textContent = 'Add resident'; hidePass(); await loadSetup(resident.id);
    message(id ? resident.name + "'s details and home flat have been saved to the database."
        : resident.name + ' is now part of your community. You can create their first visitor pass.');
    if (!id) $('visitor-desk').scrollIntoView({behavior: reducedMotion.matches ? 'instant' : 'smooth', block: 'start'});
});
$('flatEdit').onchange = () => {
    const flat = communityFlats.find(f => String(f.id) === $('flatEdit').value);
    $('flatForm').elements.number.value = flat ? flat.number : '';
    $('flatForm').elements.address.value = flat ? flat.address || '' : '';
    $('flatSave').textContent = flat ? 'Save flat and address' : 'Add flat';
};
$('residentEdit').onchange = () => {
    const resident = communityResidents.find(r => String(r.id) === $('residentEdit').value);
    $('residentForm').elements.name.value = resident ? resident.name : '';
    $('residentForm').elements.phone.value = resident ? resident.phone : '';
    $('flat').value = resident ? resident.flat.id : '';
    $('residentSave').textContent = resident ? 'Save resident changes' : 'Add resident';
};
submit('approvalForm', async form => {
    if (!$('resident').value) throw new Error('Choose an approving resident first. Add one in Community setup if the list is empty.');
    const visit = await api('/residents/' + $('resident').value + '/approvals', 'POST', values(form));
    form.reset(); await loadVisits();
    latestPass = visit;
    $('passVisitor').textContent = visit.visitorName;
    $('passId').textContent = '#' + visit.id;
    $('passOtp').textContent = visit.otp;
    $('createdPass').hidden = false;
    $('smsResult').textContent = 'SMS not submitted. You can copy these pass details.';
    message('Your visitor pass is ready. Share the approval ID and OTP shown below with ' + visit.visitorName + '.');
    $('createdPass').scrollIntoView({behavior: reducedMotion.matches ? 'instant' : 'smooth', block: 'nearest'});
    await loadSmsStatus();
    if (smsEnabled) await run($('sendSms'), () => sendPassSms(visit));
});
submit('entryForm', async form => {
    $('entryResult').textContent = '';
    const body = values(form); body.approvalId = Number(body.approvalId);
    const visit = await api('/security/entries', 'POST', body);
    $('entryResult').textContent = '✓ Welcome, ' + visit.visitorName + '. Entry recorded at ' + date(visit.entryTime) + '.';
    form.reset(); await loadVisits();
    message('Entry recorded successfully. This visitor pass has now been used.');
});
$('copyPass').onclick = () => run($('copyPass'), async () => {
    if (!latestPass) return;
    const text = 'Arrival visitor pass\nVisitor: ' + latestPass.visitorName + '\nApproval ID: ' + latestPass.id +
        '\nOTP: ' + latestPass.otp + '\nValid: ' + date(latestPass.expectedAt) + ' to ' + date(latestPass.expiresAt);
    try { await navigator.clipboard.writeText(text); message('Pass details copied. You can now share them with your visitor.'); }
    catch { throw new Error('Clipboard access is unavailable. Select and copy the approval ID and OTP from the pass.'); }
});
$('resident').onchange = () => { hidePass(); loadVisits().catch(e => message(e.message, true)); };
$('refresh').onclick = () => run($('refresh'), loadVisits);
$('today').textContent = new Date().toLocaleDateString([], {weekday: 'short', month: 'short', day: 'numeric', year: 'numeric'});
document.querySelectorAll('.nav-link').forEach(link => {
    link.onclick = () => {
        document.querySelectorAll('.nav-link').forEach(item => { item.classList.remove('active'); item.removeAttribute('aria-current'); });
        link.classList.add('active'); link.setAttribute('aria-current', 'location');
    };
});
loadSetup().then(() => {
    if ($('resident').value) message('Your community is ready. Create a visitor pass or check someone in at the gate.');
}).catch(e => message(e.message, true));
loadSmsStatus();
