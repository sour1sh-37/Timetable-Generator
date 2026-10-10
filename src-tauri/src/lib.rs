use std::fs;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tauri::{AppHandle, Manager};

pub struct AppState {
    pub port: u16,
    pub backend_child: Arc<Mutex<Option<Child>>>,
}

#[tauri::command]
fn get_backend_url(state: tauri::State<AppState>) -> String {
    format!("http://127.0.0.1:{}", state.port)
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
    let backend_child_holder: Arc<Mutex<Option<Child>>> = Arc::new(Mutex::new(None));
    let child_for_lifecycle = Arc::clone(&backend_child_holder);
    let bound_port_holder: Arc<Mutex<u16>> = Arc::new(Mutex::new(8080));
    let port_for_lifecycle = Arc::clone(&bound_port_holder);

    let app = tauri::Builder::default()
        .setup(move |app| {
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }

            // 1. Determine Port
            let port = find_available_port();
            *bound_port_holder.lock().unwrap() = port;

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

            // 4. Launch Java Backend Daemon (Headless, no visible console window)
            let mut cmd = Command::new(&java_bin);
            cmd.arg("--enable-native-access=ALL-UNNAMED")
                .arg("-Djava.awt.headless=true")
                .arg("-jar")
                .arg(&backend_jar)
                .arg("--server")
                .arg(port.to_string())
                .current_dir(&working_dir);

            #[cfg(windows)]
            {
                use std::os::windows::process::CommandExt;
                const CREATE_NO_WINDOW: u32 = 0x08000000;
                cmd.creation_flags(CREATE_NO_WINDOW);
            }

            println!(
                "[TAURI] Spawning backend daemon: {:?} with JAR {:?} on port {}",
                java_bin, backend_jar, port
            );

            let child = match cmd.spawn() {
                Ok(c) => c,
                Err(e) => {
                    eprintln!("[TAURI ERROR] Failed to spawn Java backend daemon: {e}");
                    return Err(Box::new(e));
                }
            };

            #[cfg(windows)]
            win_job::setup_kill_on_close_job(&child);

            *backend_child_holder.lock().unwrap() = Some(child);

            // 5. Wait for Backend Readiness (Handshake)
            println!("[TAURI] Awaiting backend health check on 127.0.0.1:{}...", port);
            if let Err(e) = wait_for_health(port, Duration::from_secs(25)) {
                eprintln!("[TAURI ERROR] Health check handshake failed: {e}");
                return Err(Box::new(std::io::Error::new(
                    std::io::ErrorKind::TimedOut,
                    e,
                )));
            }

            println!("[TAURI] Backend daemon is UP and verified healthy on port {}", port);

            // 6. Register App State
            app.manage(AppState {
                port,
                backend_child: Arc::clone(&backend_child_holder),
            });

            // 7. Inject backend URL into main webview window
            if let Some(main_window) = app.get_webview_window("main") {
                let init_js = format!("window.__SIES_BACKEND_URL__ = 'http://127.0.0.1:{port}';");
                let _ = main_window.eval(&init_js);
            }

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![get_backend_url])
        .build(tauri::generate_context!())
        .expect("error while building tauri application");

    app.run(move |_app_handle, event| {
        if let tauri::RunEvent::ExitRequested { .. } = event {
            let port = *port_for_lifecycle.lock().unwrap();
            println!("[TAURI] Application exit requested. Initiating backend shutdown on port {port}...");
            send_shutdown_signal(port);
            std::thread::sleep(Duration::from_millis(400));
            if let Ok(mut guard) = child_for_lifecycle.lock() {
                if let Some(mut child) = guard.take() {
                    let _ = child.kill();
                }
            }
        }
    });
}
