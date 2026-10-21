import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Shield, LockKeyhole, Upload, LayoutDashboard, Files, History, LogOut, UserPlus, ScanSearch, AlertTriangle, ShieldCheck, Download, RefreshCw, Users, Activity, Bot } from 'lucide-react';
import './style.css';

const API = 'http://localhost:8080/api';

async function readResponse(response) {
  const raw = await response.text();
  let data = {};
  try { data = raw ? JSON.parse(raw) : {}; } catch { data = { message: raw || `Request failed (${response.status})` }; }
  if (!response.ok) throw new Error(data.message || data.error || `Request failed (${response.status})`);
  return data;
}

function App() {
  const [token, setToken] = useState(localStorage.getItem('pg_token'));
  const [user, setUser] = useState(localStorage.getItem('pg_user') || '');
  const [role, setRole] = useState(localStorage.getItem('pg_role') || '');
  const [page, setPage] = useState('dashboard');
  const [signup, setSignup] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [stats, setStats] = useState({ totalFiles: 0, highRisk: 0, protected: 0 });
  const [adminStats, setAdminStats] = useState({ users: 0, files: 0, highRisk: 0, auditEvents: 0 });
  const [files, setFiles] = useState([]);
  const [logs, setLogs] = useState([]);
  const [file, setFile] = useState(null);
  const [scan, setScan] = useState(null);

  const headers = useMemo(() => token ? { Authorization: `Bearer ${token}` } : {}, [token]);

  async function auth(path, body) {
    setError(''); setLoading(true);
    try {
      const d = await readResponse(await fetch(API + path, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
      }));
      localStorage.setItem('pg_token', d.token);
      localStorage.setItem('pg_user', d.username);
      localStorage.setItem('pg_role', d.role);
      setToken(d.token); setUser(d.username); setRole(d.role); setSignup(false);
    } catch (e) { setError(e.message); } finally { setLoading(false); }
  }

  function logout() {
    localStorage.removeItem('pg_token'); localStorage.removeItem('pg_user'); localStorage.removeItem('pg_role');
    setToken(null); setUser(''); setRole(''); setPage('dashboard');
  }

  async function load() {
    if (!token) return;
    try {
      const [dash, fileList, userLogs] = await Promise.all([
        fetch(API + '/dashboard', { headers }),
        fetch(API + '/files', { headers }),
        fetch(API + '/logs', { headers })
      ]);
      // A token saved by another project version may use a different JWT secret.
      // Validate the session against a normal authenticated endpoint and send the
      // user back to login instead of leaving the app in a misleading 403 state.
      if (dash.status === 401 || dash.status === 403) {
        localStorage.removeItem('pg_token');
        localStorage.removeItem('pg_user');
        localStorage.removeItem('pg_role');
        setToken(null); setUser(''); setRole('');
        setFiles([]); setLogs([]); setScan(null);
        setError('Your session expired or is invalid. Please log in again.');
        return;
      }
      if (dash.ok) setStats(await dash.json());
      if (fileList.ok) setFiles(await fileList.json());
      if (userLogs.ok) setLogs(await userLogs.json());
      if (role === 'ADMIN') {
        const [overview, adminLogs] = await Promise.all([
          fetch(API + '/admin/overview', { headers }), fetch(API + '/admin/logs', { headers })
        ]);
        if (overview.ok) setAdminStats(await overview.json());
        if (adminLogs.ok) setLogs(await adminLogs.json());
      }
    } catch (e) { setError('Unable to load security data: ' + e.message); }
  }

  useEffect(() => { load(); }, [token, user, role]);

  async function doScan() {
    if (!file) return;
    setLoading(true); setError(''); setScan(null);
    try {
      const fd = new FormData(); fd.append('file', file);
      const response = await fetch(API + '/files/scan', { method: 'POST', headers, body: fd });
      if (response.status === 401 || response.status === 403) {
        logout();
        throw new Error('Your session expired or is invalid. Please log in again.');
      }
      const d = await readResponse(response);
      const detectionList = Array.isArray(d.detections)
        ? d.detections
        : typeof d.detections === 'string'
          ? d.detections.split(',').map(x => x.trim()).filter(Boolean)
          : [];
      setScan({ ...d, detections: detectionList });
      await load();
    } catch (e) { setError(e.message); } finally { setLoading(false); }
  }

  async function protect(id, method = 'AUTO') {
    setError(''); setLoading(true);
    try {
      const response = await fetch(`${API}/files/${id}/protect?method=${encodeURIComponent(method)}`, { method: 'POST', headers });
      if (response.status === 401 || response.status === 403) {
        logout();
        throw new Error('Your session expired or is invalid. Please log in again.');
      }
      await readResponse(response);
      await load();
    }
    catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }

  if (!token) return <Auth signup={signup} setSignup={setSignup} onLogin={x => auth('/auth/login', x)} onSignup={x => auth('/auth/signup', x)} error={error} loading={loading} />;

  const title = { dashboard: 'Security Overview', scan: 'Privacy Scanner', files: 'Protected Files', recovery: 'Recovery Center', logs: 'Audit Trail', admin: 'Security Operations Center' }[page];
  return <div className="app">
    <aside>
      <div className="brand"><div className="logo"><Shield /></div><div><b>PRIVACY</b><strong>GUARDIAN</strong></div></div>
      <nav>{[['dashboard','Dashboard',LayoutDashboard],['scan','Privacy Scanner',ScanSearch],['files','My Files',Files],['recovery','Recovery Center',RefreshCw],['logs','Audit Logs',History], ...(role === 'ADMIN' ? [['admin','Admin SOC',Users]] : [])].map(([p,n,I]) => <button key={p} className={page === p ? 'active' : ''} onClick={() => setPage(p)}><I size={18}/>{n}</button>)}</nav>
      <div className="sidebottom"><div className="secure"><ShieldCheck size={18}/><span>System Secure</span></div><button onClick={logout}><LogOut size={17}/>Logout</button></div>
    </aside>
    <main>
      <header><div><span className="eyebrow">AGENTIC PRIVACY DEFENSE</span><h1>{title}</h1></div><div className="profile"><span>{user}</span><small>{role}</small></div></header>
      {error && <div className="error">{error}</div>}
      {page === 'dashboard' && <Dashboard stats={stats} files={files} setPage={setPage}/>} 
      {page === 'scan' && <Scanner file={file} setFile={setFile} scan={scan} doScan={doScan} loading={loading}/>} 
      {page === 'files' && <FileList files={files} protect={protect} token={token}/>} 
      {page === 'recovery' && <Recovery files={files} token={token}/>} 
      {page === 'logs' && <Logs logs={logs}/>} 
      {page === 'admin' && role === 'ADMIN' && <Admin stats={adminStats} logs={logs}/>} 
    </main>
  </div>;
}

function Auth({ signup, setSignup, onLogin, onSignup, error, loading }) {
  const [u, setU] = useState('user'), [p, setP] = useState('user123'), [e, setE] = useState('user@privacyguardian.local'), [c, setC] = useState('');
  function submit() {
    if (!u.trim() || !p) return;
    if (signup) {
      if (!e.includes('@')) return;
      if (p.length < 6) return;
      if (p !== c) return;
      onSignup({ username: u.trim(), email: e.trim(), password: p });
    } else onLogin({ username: u.trim(), password: p });
  }
  return <div className="auth"><div className="hero"><div className="orb"><Shield size={64}/></div><span className="eyebrow">AGENTIC PRIVACY DEFENSE</span><h1>Protect what<br/><em>matters most.</em></h1><p>Privacy Guardian detects sensitive information, protects exposed files and keeps an auditable recovery trail.</p><div className="pills"><span>Agentic AI decisions</span><span>Secure recovery</span><span>SHA-256 integrity</span></div></div><div className="card"><div className="brand"><div className="logo"><Shield/></div><div><b>PRIVACY</b><strong>GUARDIAN</strong></div></div><span className="eyebrow">SECURE ACCESS</span><h2>{signup ? 'Create account' : 'Welcome back'}</h2><p>{signup ? 'Create your protected workspace.' : 'Sign in to your privacy control center.'}</p><input placeholder="Username or Email" value={u} onChange={x=>setU(x.target.value)}/>{signup&&<input placeholder="Email" value={e} onChange={x=>setE(x.target.value)}/>}<input placeholder="Password" type="password" value={p} onChange={x=>setP(x.target.value)}/>{signup&&<input placeholder="Confirm password" type="password" value={c} onChange={x=>setC(x.target.value)}/>}<button className="primary" disabled={loading} onClick={submit}>{signup?<UserPlus/>:<LockKeyhole/>}{loading?'Please wait...':signup?'Create Account':'Enter Security Center'}</button>{error&&<div className="error">{error}</div>}<button className="link" onClick={()=>setSignup(!signup)}>{signup?'Already have an account? Login':'New here? Create an account'}</button>{!signup&&<div className="demo">Demo: <b>user / user123</b> &nbsp; <b>admin / admin123</b></div>}</div></div>;
}

const Card=({icon,title,value,sub})=><div className="stat"><div className="staticon">{icon}</div><small>{title}</small><strong>{value}</strong><span>{sub}</span></div>;

function Dashboard({stats,files,setPage}) { const latest=files[0]; return <><div className="grid4"><Card icon={<Files/>} title="FILES SCANNED" value={stats.totalFiles} sub="Your workspace"/><Card icon={<AlertTriangle/>} title="HIGH RISK" value={stats.highRisk} sub="Needs attention"/><Card icon={<ShieldCheck/>} title="PROTECTED" value={stats.protected} sub="Secured copies"/><Card icon={<Activity/>} title="SECURITY" value="98%" sub="Healthy"/></div><section className="panel"><div className="panelhead"><div><span className="eyebrow">QUICK ACTION</span><h2>Scan a new file</h2></div><button className="primary small" onClick={()=>setPage('scan')}><ScanSearch/>Start Scan</button></div><div className="scanbanner"><div><ShieldCheck size={42}/><div><b>Privacy risk analysis</b><p>Upload an image, PDF, DOCX, TXT or CSV and get a privacy risk score.</p></div></div><span className="status">● ONLINE</span></div></section><section className="panel agent-panel"><div className="panelhead"><div><span className="eyebrow">AUTONOMOUS AGENT</span><h2><Bot size={20}/> Privacy Guardian Agent</h2></div><span className="status">● ACTIVE</span></div><div className="agent-grid"><div><small>LAST DECISION</small><strong>{latest?.agentAction||'WAITING FOR SCAN'}</strong></div><div><small>RECOMMENDED PROTECTION</small><strong>{latest?.agentRecommendedMethod||'—'}</strong></div><div><small>CONFIDENCE</small><strong>{latest?.agentConfidence?Math.round(latest.agentConfidence*100)+'%':'—'}</strong></div></div>{latest?.agentReason&&<p className="agent-reason">{latest.agentReason}</p>}</section><section className="panel"><div className="panelhead"><h2>Recent files</h2><button className="link" onClick={()=>setPage('files')}>View all</button></div>{files.slice(0,5).map(f=><FileRow key={f.id} f={f}/>)}</section></> }

function Scanner({file,setFile,scan,doScan,loading}) { return <div className="scanner"><div className="upload"><Upload size={42}/><h2>Drop a file to scan</h2><p>Images, PDF, DOCX, TXT and CSV • OCR + PII + QR detection • Maximum 25 MB</p><input type="file" accept=".png,.jpg,.jpeg,.pdf,.docx,.txt,.csv" onChange={e=>setFile(e.target.files?.[0]||null)}/>{file&&<div className="selected">Selected: <b>{file.name}</b></div>}<button className="primary" disabled={!file||loading} onClick={doScan}><ScanSearch/>{loading?'Scanning...':'Analyze Privacy Risk'}</button></div>{scan&&<div className="result"><div className="score"><div className={'circle '+String(scan.riskLevel||'low').toLowerCase()}><b>{scan.riskScore}</b><small>/100</small></div><div><span className="eyebrow">RISK ASSESSMENT</span><h2>{scan.riskLevel}</h2><p>{scan.qrDetected?'QR code detected • ':''}{scan.detections.length} sensitive indicators found.</p></div></div><div className="agent-result"><div className="agent-title"><Bot size={20}/><div><span className="eyebrow">AGENTIC AI DECISION</span><strong>{scan.agentAction||'ANALYZING'}</strong></div></div><div className="agent-grid"><div><small>PROTECTION</small><strong>{scan.agentRecommendedMethod||'—'}</strong></div><div><small>CONFIDENCE</small><strong>{scan.agentConfidence?Math.round(scan.agentConfidence*100)+'%':'—'}</strong></div><div><small>EXECUTED</small><strong>{scan.agentExecuted?'YES':'NO'}</strong></div></div>{scan.agentReason&&<p className="agent-reason">{scan.agentReason}</p>}{scan.protectedFile&&<div className="secure"><ShieldCheck size={16}/> Agent automatically protected this file.</div>}</div><div className="detections">{scan.detections.map(x=><span key={x}>⚠ {x}</span>)}</div></div>}</div> }

function FileRow({f}) { return <div className="filerow"><div className="fileicon"><Files/></div><div className="fname"><b>{f.originalName}</b><span>{new Date(f.createdAt).toLocaleString()}</span></div><span className={'badge '+String(f.riskLevel||'').toLowerCase()}>{f.riskLevel}</span><span className="hash">SHA-256 {f.sha256?.slice(0,12)}…</span>{f.protectionMethod&&<span className="hash">Protected: {f.protectionMethod}</span>}</div> }

async function downloadProtected(id, token, originalName) {
  const r = await fetch(`${API}/files/${id}/protected`, { headers: { Authorization:`Bearer ${token}` } });
  const blob = await r.blob();
  if (!r.ok) throw new Error(await blob.text());
  const url=URL.createObjectURL(blob); const a=document.createElement('a'); a.href=url; a.download='PROTECTED_'+originalName; document.body.appendChild(a); a.click(); a.remove(); setTimeout(()=>URL.revokeObjectURL(url),1000);
}

function FileList({files,protect,token}) { const [method,setMethod]=useState('AUTO'); const [busy,setBusy]=useState(null); const [msg,setMsg]=useState(''); async function dl(f){try{setBusy(f.id);setMsg('');await downloadProtected(f.id,token,f.originalName)}catch(e){setMsg(e.message)}finally{setBusy(null)}} return <section className="panel"><div className="panelhead"><div><h2>My Files</h2><span>{files.length} files</span></div><label className="method">Protection <select value={method} onChange={e=>setMethod(e.target.value)}><option>AUTO</option><option>BLACKOUT</option><option>WHITEOUT</option><option>BLUR</option><option>PIXELATE</option><option>MOSAIC</option><option>MASK</option><option>REPLACE</option></select></label></div>{msg&&<div className="error">{msg}</div>}{files.map(f=><div key={f.id}><FileRow f={f}/><div className="actions">{!f.protectedFile&&<button disabled={busy===f.id} onClick={()=>protect(f.id,method)}><ShieldCheck/>Protect</button>}{f.protectedFile&&<button disabled={busy===f.id} onClick={()=>dl(f)}><Download/>Download Protected</button>}</div></div>)}</section> }

function Recovery({files,token}) {
  const [msg,setMsg]=useState('');
  const [recoveredIds,setRecoveredIds]=useState(new Set());
  const [busyId,setBusyId]=useState(null);

  async function recover(id){
    setMsg(''); setBusyId(id);
    try {
      const r=await fetch(`${API}/files/${id}/recover`,{method:'POST',headers:{Authorization:`Bearer ${token}`}});
      const d=await readResponse(r);
      setRecoveredIds(prev=>new Set(prev).add(id));
      setMsg(d.message || 'File recovered successfully.');
    } catch(e) { setMsg(e.message || 'Recovery failed.'); }
    finally { setBusyId(null); }
  }

  async function integrity(id){
    setMsg(''); setBusyId(id);
    try {
      const r=await fetch(`${API}/files/${id}/integrity`,{headers:{Authorization:`Bearer ${token}`}});
      const d=await readResponse(r);
      setMsg(d.integrityVerified?'Integrity verified ✓':'Integrity mismatch ⚠');
    } catch(e) { setMsg(e.message || 'Integrity verification failed.'); }
    finally { setBusyId(null); }
  }

  async function downloadRecovered(id){
    setMsg(''); setBusyId(id);
    try {
      const r=await fetch(`${API}/files/${id}/recover/download`,{headers:{Authorization:`Bearer ${token}`}});
      if(!r.ok){
        let message=`Download failed (${r.status})`;
        try { const d=await r.json(); message=d.message || d.error || message; } catch {}
        throw new Error(message);
      }
      const blob=await r.blob();
      const url=URL.createObjectURL(blob);
      const a=document.createElement('a');
      a.href=url;
      const disposition=r.headers.get('Content-Disposition') || '';
      const match=disposition.match(/filename="?([^";]+)"?/i);
      a.download=match?.[1] || `RECOVERED_${files.find(f=>f.id===id)?.originalName || 'file'}`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      setMsg('Recovered file downloaded successfully.');
    } catch(e) { setMsg(e.message || 'Recovered file download failed.'); }
    finally { setBusyId(null); }
  }

  return <section className="panel"><div className="panelhead"><h2>Recovery Center</h2><span>Automatic backups</span></div><p className="muted">Every uploaded file gets a backup before protection. SHA-256 is stored for integrity verification.</p>{msg&&<div className="secure">{msg}</div>}{files.map(f=><div className="recovery" key={f.id}><RefreshCw/><div><b>{f.originalName}</b><span>Backup available • Integrity hash {f.sha256?.slice(0,16)}…</span></div><button className="link" disabled={busyId===f.id} onClick={()=>integrity(f.id)}>Verify</button><button className="link" disabled={busyId===f.id} onClick={()=>recover(f.id)}>{busyId===f.id?'Working…':'Restore'}</button>{recoveredIds.has(f.id)&&<button className="link" disabled={busyId===f.id} onClick={()=>downloadRecovered(f.id)}><Download size={15}/> Download Recovered</button>}</div>)}</section>
}
function Logs({logs}) { return <section className="panel"><div className="panelhead"><h2>Audit Logs</h2><span>Latest security activity</span></div>{logs.map(l=><div className="log" key={l.id}><span>{new Date(l.createdAt).toLocaleString()}</span><b>{l.action}</b><span>{l.username}</span><em>{l.severity}</em><p>{l.details}</p></div>)}</section> }
function Admin({stats,logs}) { return <><div className="grid4"><Card icon={<Users/>} title="USERS" value={stats.users} sub="Registered users"/><Card icon={<Files/>} title="FILES" value={stats.files} sub="All accounts"/><Card icon={<AlertTriangle/>} title="HIGH RISKS" value={stats.highRisk} sub="All accounts"/><Card icon={<Activity/>} title="AUDIT" value={stats.auditEvents} sub="All events"/></div><section className="panel"><div className="panelhead"><h2>Security Operations Center</h2><span className="status">● MONITORING</span></div><p className="muted">Admin-only audit view for high-risk activity and account security.</p>{logs.slice(0,20).map(l=><div className="log" key={l.id}><span>{new Date(l.createdAt).toLocaleString()}</span><b>{l.action}</b><span>{l.username}</span><em>{l.severity}</em><p>{l.details}</p></div>)}</section></> }

createRoot(document.getElementById('root')).render(<App/>);
