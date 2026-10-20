use std::fs;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tauri::{AppHandle, Manager};

#[derive(Clone, Copy, PartialEq, Eq, Debug, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum BackendLifecycleState {
    Starting,
    Online,
    ShuttingDown,
    Offline,
    Recovering,
    Failed,
}

#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BackendStatusDto {
    pub state: BackendLifecycleState,
    pub port: u16,
    pub url: String,
    pub error: Option<String>,
}

#[derive(Clone)]
pub struct AppState {
    pub bound_port: Arc<Mutex<u16>>,
    pub backend_child: Arc<Mutex<Option<Child>>>,
    pub java_bin: PathBuf,
    pub backend_jar: PathBuf,
    pub working_dir: PathBuf,
    pub state: Arc<Mutex<BackendLifecycleState>>,
    pub last_error: Arc<Mutex<Option<String>>>,
    pub lifecycle_lock: Arc<Mutex<()>>,
}

impl AppState {
    pub fn do_get_status(&self) -> BackendStatusDto {
        let port = *self.bound_port.lock().unwrap();
        let mut current_state = *self.state.lock().unwrap();
        let err = self.last_error.lock().unwrap().clone();

        // If marked online, perform a quick 300ms verification
        if current_state == BackendLifecycleState::Online {
            if wait_for_health(port, Duration::from_millis(300)).is_err() {
                current_state = BackendLifecycleState::Offline;
                *self.state.lock().unwrap() = BackendLifecycleState::Offline;
            }
        }

        BackendStatusDto {
            state: current_state,
            port,
            url: format!("http://127.0.0.1:{port}"),
            error: err,
        }
    }

    pub fn do_start(&self) -> Result<BackendStatusDto, String> {
        let _guard = self.lifecycle_lock.lock().unwrap();
        let mut port = *self.bound_port.lock().unwrap();

        // 1. If already responding to health checks, confirm online
        if wait_for_health(port, Duration::from_millis(400)).is_ok() {
            *self.state.lock().unwrap() = BackendLifecycleState::Online;
            *self.last_error.lock().unwrap() = None;
            return Ok(BackendStatusDto {
                state: BackendLifecycleState::Online,
                port,
                url: format!("http://127.0.0.1:{port}"),
                error: None,
            });
        }

        // 2. Clean up any dead child process handle
        if let Ok(mut child_lock) = self.backend_child.lock() {
            if let Some(mut old_child) = child_lock.take() {
                let _ = old_child.try_wait();
            }
        }

        *self.state.lock().unwrap() = BackendLifecycleState::Starting;
        *self.last_error.lock().unwrap() = None;

        // 3. Ensure port is available or find a free ephemeral port
        if TcpListener::bind(format!("127.0.0.1:{port}")).is_err() {
            let next_port = find_available_port();
            println!("[TAURI] Port {port} is occupied; shifting backend to port {next_port}");
            port = next_port;
            *self.bound_port.lock().unwrap() = port;
        }

        // 4. Launch backend daemon
        println!(
            "[TAURI] Spawning backend daemon: {:?} with JAR {:?} on port {}",
            self.java_bin, self.backend_jar, port
        );

        let child = match spawn_java_backend(
            &self.java_bin,
            &self.backend_jar,
            &self.working_dir,
            port,
        ) {
            Ok(c) => c,
            Err(e) => {
                *self.state.lock().unwrap() = BackendLifecycleState::Failed;
                let err_msg = format!("Failed to spawn Java backend daemon: {e}");
                *self.last_error.lock().unwrap() = Some(err_msg.clone());
                eprintln!("[TAURI ERROR] {err_msg}");
                return Err(err_msg);
            }
        };

        *self.backend_child.lock().unwrap() = Some(child);

        // 5. Await health verification
        println!("[TAURI] Awaiting backend health verification on 127.0.0.1:{port}...");
        match wait_for_health(port, Duration::from_secs(25)) {
            Ok(()) => {
                *self.state.lock().unwrap() = BackendLifecycleState::Online;
                *self.last_error.lock().unwrap() = None;
                println!("[TAURI] Backend daemon is UP and verified healthy on port {port}");
                Ok(BackendStatusDto {
                    state: BackendLifecycleState::Online,
                    port,
                    url: format!("http://127.0.0.1:{port}"),
                    error: None,
                })
            }
            Err(e) => {
                *self.state.lock().unwrap() = BackendLifecycleState::Failed;
                let err_msg = format!("Backend failed to respond to health check: {e}");
                *self.last_error.lock().unwrap() = Some(err_msg.clone());
                eprintln!("[TAURI ERROR] {err_msg}");
                Err(err_msg)
            }
        }
    }

    pub fn do_shutdown(&self) -> Result<BackendStatusDto, String> {
        let _guard = self.lifecycle_lock.lock().unwrap();
        let port = *self.bound_port.lock().unwrap();

        *self.state.lock().unwrap() = BackendLifecycleState::ShuttingDown;
        println!("[TAURI] Initiating backend graceful termination on port {port}...");

        send_shutdown_signal(port);

        // Poll for up to 2.5 seconds for graceful exit
        let start = Instant::now();
        let mut terminated = false;
        while start.elapsed() < Duration::from_millis(2500) {
            if let Ok(mut child_lock) = self.backend_child.lock() {
                if let Some(ref mut child) = *child_lock {
                    if let Ok(Some(_)) = child.try_wait() {
                        terminated = true;
                        break;
                    }
                } else {
                    terminated = true;
                    break;
                }
            }
            std::thread::sleep(Duration::from_millis(100));
        }

        if !terminated {
            println!("[TAURI] Backend did not terminate within graceful window; terminating process...");
            if let Ok(mut child_lock) = self.backend_child.lock() {
                if let Some(mut child) = child_lock.take() {
                    let _ = child.kill();
                    let _ = child.wait();
                }
            }
        } else if let Ok(mut child_lock) = self.backend_child.lock() {
            let _ = child_lock.take();
        }

        *self.state.lock().unwrap() = BackendLifecycleState::Offline;
        *self.last_error.lock().unwrap() = None;
        println!("[TAURI] Backend daemon is now OFFLINE.");

        Ok(BackendStatusDto {
            state: BackendLifecycleState::Offline,
            port,
            url: format!("http://127.0.0.1:{port}"),
            error: None,
        })
    }

    pub fn do_restart(&self) -> Result<BackendStatusDto, String> {
        println!("[TAURI] Restarting backend daemon...");
        let _ = self.do_shutdown();
        std::thread::sleep(Duration::from_millis(300));
        *self.state.lock().unwrap() = BackendLifecycleState::Recovering;
        self.do_start()
    }
}

fn spawn_java_backend(
    java_bin: &Path,
    backend_jar: &Path,
    working_dir: &Path,
    port: u16,
) -> Result<Child, String> {
    let mut cmd = Command::new(java_bin);
    cmd.arg("--enable-native-access=ALL-UNNAMED")
        .arg("-Djava.awt.headless=true")
        .arg("-jar")
        .arg(backend_jar)
        .arg("--server")
        .arg(port.to_string())
        .current_dir(working_dir);

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x08000000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }

    let child = cmd
        .spawn()
        .map_err(|e| format!("Failed to spawn Java backend process: {e}"))?;

    #[cfg(windows)]
    win_job::setup_kill_on_close_job(&child);

    Ok(child)
}

#[tauri::command]
fn get_backend_url(state: tauri::State<AppState>) -> String {
    let port = *state.bound_port.lock().unwrap();
    format!("http://127.0.0.1:{port}")
}

#[tauri::command]
fn get_backend_status(state: tauri::State<AppState>) -> BackendStatusDto {
    state.do_get_status()
}

#[tauri::command]
fn start_backend(state: tauri::State<AppState>) -> Result<BackendStatusDto, String> {
    state.do_start()
}

#[tauri::command]
fn shutdown_backend(state: tauri::State<AppState>) -> Result<BackendStatusDto, String> {
    state.do_shutdown()
}

#[tauri::command]
fn restart_backend(state: tauri::State<AppState>) -> Result<BackendStatusDto, String> {
    state.do_restart()
}

fn find_available_port() -> u16 {
    // 1. Prefer 8080 if available
    if let Ok(listener) = TcpListener::bind("127.0.0.1:8080") {
        drop(listener);
        return 8080;
    }
    // 2. Bind ephemeral port on loopback
    if let Ok(listener) = TcpListener::bind("127.0.0.1:0") {
        if let Ok(addr) = listener.local_addr() {
            let p = addr.port();
            drop(listener);
            return p;
        }
    }
    8080
}

fn initialize_user_data_dir(app: &AppHandle) -> Result<PathBuf, String> {
    let base_dir = app
        .path()
        .app_data_dir()
        .map_err(|e| format!("Failed to get app_data_dir: {e}"))?;

    let config_dir = base_dir.join("config");
    let sample_dir = base_dir.join("sample-data");
    let output_dir = base_dir.join("output");

    fs::create_dir_all(&config_dir).map_err(|e| format!("Failed to create config dir: {e}"))?;
    fs::create_dir_all(&sample_dir).map_err(|e| format!("Failed to create sample dir: {e}"))?;
    fs::create_dir_all(&output_dir).map_err(|e| format!("Failed to create output dir: {e}"))?;

    // Copy bundled default config and sample datasets on first launch
    if let Ok(res_dir) = app.path().resource_dir() {
        let def_config = res_dir.join("resources").join("defaults").join("config");
        let def_sample = res_dir.join("resources").join("defaults").join("sample-data");

        if def_config.exists() {
            copy_dir_contents(&def_config, &config_dir);
        }
        if def_sample.exists() {
            copy_dir_contents(&def_sample, &sample_dir);
        }
    }

    Ok(base_dir)
}

fn copy_dir_contents(src: &Path, dst: &Path) {
    if let Ok(entries) = fs::read_dir(src) {
        for entry in entries.flatten() {
            let src_path = entry.path();
            let file_name = entry.file_name();
            let dst_path = dst.join(file_name);
            if src_path.is_file() && !dst_path.exists() {
                let _ = fs::copy(&src_path, &dst_path);
            }
        }
    }
}

fn locate_java_and_backend(app: &AppHandle) -> Result<(PathBuf, PathBuf), String> {
    // 1. Packaged installation resources
    if let Ok(res_dir) = app.path().resource_dir() {
        let bundled_java = res_dir
            .join("resources")
            .join("jre")
            .join("bin")
            .join("java.exe");
        let bundled_jar = res_dir
            .join("resources")
            .join("backend")
            .join("timetable-generator-1.0.0.jar");

        if bundled_java.exists() && bundled_jar.exists() {
            return Ok((bundled_java, bundled_jar));
        }
    }

    // 2. src-tauri local resources (staged during build/dev)
    let local_res = PathBuf::from("resources");
    let local_java = local_res.join("jre").join("bin").join("java.exe");
    let local_jar = local_res.join("backend").join("timetable-generator-1.0.0.jar");
    if local_java.exists() && local_jar.exists() {
        return Ok((local_java, local_jar));
    }

    // 3. Fallback to maven target jar
    let target_jar = PathBuf::from("../target/timetable-generator-1.0.0.jar");
    if target_jar.exists() {
        return Ok((PathBuf::from("java"), target_jar));
    }

    Err("Could not locate private Java runtime and backend JAR.".to_string())
}

#[cfg(windows)]
mod win_job {
    use std::ffi::c_void;
    use std::os::windows::io::AsRawHandle;
    use std::process::Child;

    const JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE: u32 = 0x2000;
    const JOB_OBJECT_EXTENDED_LIMIT_INFORMATION: u32 = 9;

    #[repr(C)]
    struct BasicLimitInformation {
        per_process_user_time_limit: i64,
        per_job_user_time_limit: i64,
        limit_flags: u32,
        minimum_working_set_size: usize,
        maximum_working_set_size: usize,
        active_process_limit: u32,
        affinity: usize,
        priority_class: u32,
        scheduling_class: u32,
    }

    #[repr(C)]
    struct IoCounters {
        read_operation_count: u64,
        write_operation_count: u64,
        other_operation_count: u64,
        read_transfer_count: u64,
        write_transfer_count: u64,
        other_transfer_count: u64,
    }

    #[repr(C)]
    struct ExtendedLimitInformation {
        basic_limit: BasicLimitInformation,
        io_info: IoCounters,
        process_memory_limit: usize,
        job_memory_limit: usize,
        peak_process_memory_limit: usize,
        peak_job_memory_limit: usize,
    }

    unsafe extern "system" {
        fn CreateJobObjectW(lp_job_attributes: *const c_void, lp_name: *const u16) -> *mut c_void;
        fn SetInformationJobObject(
            h_job: *mut c_void,
            job_object_info_class: u32,
            lp_job_object_info: *const c_void,
            cb_job_object_info_length: u32,
        ) -> i32;
        fn AssignProcessToJobObject(h_job: *mut c_void, h_process: *mut c_void) -> i32;
    }

    pub fn setup_kill_on_close_job(child: &Child) {
        unsafe {
            let job = CreateJobObjectW(std::ptr::null(), std::ptr::null());
            if !job.is_null() {
                let mut info: ExtendedLimitInformation = std::mem::zeroed();
                info.basic_limit.limit_flags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
                SetInformationJobObject(
                    job,
                    JOB_OBJECT_EXTENDED_LIMIT_INFORMATION,
                    &info as *const _ as *const c_void,
                    std::mem::size_of::<ExtendedLimitInformation>() as u32,
                );
                let proc_handle = child.as_raw_handle() as *mut c_void;
                AssignProcessToJobObject(job, proc_handle);
            }
        }
    }
}

fn wait_for_health(port: u16, max_duration: Duration) -> Result<(), String> {
    let start = Instant::now();
    let addr_str = format!("127.0.0.1:{port}");
    let addr = match addr_str.parse() {
        Ok(a) => a,
        Err(e) => return Err(format!("Invalid socket address: {e}")),
    };

    while start.elapsed() < max_duration {
        if let Ok(mut stream) = TcpStream::connect_timeout(&addr, Duration::from_millis(400)) {
            let request = format!(
                "GET /api/health HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n"
            );
            if stream.write_all(request.as_bytes()).is_ok() {
                let mut buf = [0u8; 512];
                if let Ok(n) = stream.read(&mut buf) {
                    let resp = String::from_utf8_lossy(&buf[..n]);
                    if resp.contains("200 OK") && resp.contains("\"status\"") {
                        return Ok(());
                    }
                }
            }
        }
        std::thread::sleep(Duration::from_millis(300));
    }

    Err(format!(
        "Backend daemon on port {port} failed to respond to health check within {:?}",
        max_duration
    ))
}

fn send_shutdown_signal(port: u16) {
    let addr_str = format!("127.0.0.1:{port}");
    if let Ok(addr) = addr_str.parse() {
        if let Ok(mut stream) = TcpStream::connect_timeout(&addr, Duration::from_millis(500)) {
            let request = format!(
                "POST /api/admin/shutdown HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            );
            let _ = stream.write_all(request.as_bytes());
            let mut buf = [0u8; 128];
            let _ = stream.read(&mut buf);
        }
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let app = tauri::Builder::default()
        .setup(|app| {
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }

            // 1. Determine Port
            let port = find_available_port();

            // 2. Prepare User Writable Data Directory
            let working_dir = match initialize_user_data_dir(app.handle()) {
                Ok(dir) => dir,
                Err(err) => {
                    eprintln!("[TAURI ERROR] Could not initialize user data directory: {err}");
                    std::env::current_dir().unwrap_or_else(|_| PathBuf::from("."))
                }
            };

            // 3. Locate Java and Backend JAR
            let (java_bin, backend_jar) = match locate_java_and_backend(app.handle()) {
                Ok(paths) => paths,
                Err(err) => {
                    eprintln!("[TAURI ERROR] {err}");
                    return Err(Box::new(std::io::Error::new(
                        std::io::ErrorKind::NotFound,
                        err,
                    )));
                }
            };

            // 4. Construct managed application state
            let app_state = AppState {
                bound_port: Arc::new(Mutex::new(port)),
                backend_child: Arc::new(Mutex::new(None)),
                java_bin,
                backend_jar,
                working_dir,
                state: Arc::new(Mutex::new(BackendLifecycleState::Starting)),
                last_error: Arc::new(Mutex::new(None)),
                lifecycle_lock: Arc::new(Mutex::new(())),
            };

            // 5. Initial start of the backend daemon
            if let Err(e) = app_state.do_start() {
                eprintln!("[TAURI WARNING] Initial backend launch failed (user can start via UI): {e}");
            }

            // 6. Manage state
            app.manage(app_state);

            // 7. Inject backend URL into main webview window
            if let Some(main_window) = app.get_webview_window("main") {
                let init_js = format!("window.__SIES_BACKEND_URL__ = 'http://127.0.0.1:{port}';");
                let _ = main_window.eval(&init_js);
            }

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_backend_url,
            get_backend_status,
            start_backend,
            shutdown_backend,
            restart_backend
        ])
        .build(tauri::generate_context!())
        .expect("error while building tauri application");

    app.run(move |app_handle, event| {
        if let tauri::RunEvent::ExitRequested { .. } = event {
            println!("[TAURI] Application exit requested. Initiating backend shutdown...");
            if let Some(state) = app_handle.try_state::<AppState>() {
                let _ = state.do_shutdown();
            }
        }
    });
}
