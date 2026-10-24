//! Windows UI: a tray icon plus a small window that shows the pairing QR code and hides itself again.

use crate::app::{Notify, Shared, UiEvent};
use crate::config::{self, new_token};
use crate::{autostart, icon, logln, pairing, render, server, single};
use std::net::TcpListener;
use std::num::NonZeroU32;
use std::rc::Rc;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tao::dpi::LogicalSize;
use tao::event::{Event, StartCause, WindowEvent};
use tao::event_loop::{ControlFlow, EventLoopBuilder};
use tao::window::{Icon as WindowIcon, WindowBuilder};
use tray_icon::menu::{CheckMenuItem, Menu, MenuEvent, MenuId, MenuItem, PredefinedMenuItem};
use tray_icon::{Icon as TrayIcon, TrayIconBuilder, TrayIconEvent};

#[derive(Debug)]
enum Msg {
    Ui(UiEvent),
    Menu(MenuId),
    TrayDoubleClick,
}

fn build_qr(shared: &Shared) -> (Option<render::Qr>, String) {
    let cfg = shared.read().map(|c| c.clone()).unwrap_or_default();
    let text = pairing::pairing_string(&pairing::host_ip(), cfg.port, &cfg.token, &cfg.device_name);
    (pairing::qr(&text), cfg.device_name)
}

pub fn run(rt: tokio::runtime::Runtime, shared: Shared, control: TcpListener, show_at_start: bool) {
    let event_loop = EventLoopBuilder::<Msg>::with_user_event().build();
    let proxy = Mutex::new(event_loop.create_proxy());

    // Network side -> UI side.
    let notify_proxy = Arc::new(Mutex::new(event_loop.create_proxy()));
    let notify: Notify = {
        let p = notify_proxy.clone();
        Arc::new(move |e| {
            if let Ok(p) = p.lock() {
                let _ = p.send_event(Msg::Ui(e));
            }
        })
    };
    rt.spawn(server::run(shared.clone(), notify.clone()));
    rt.spawn(single::serve(control, notify));

    let window_icon = WindowIcon::from_rgba(icon::rgba(32), 32, 32).ok();
    let window = Rc::new(
        WindowBuilder::new()
            .with_title("PixelDeck - scan with the phone app")
            .with_inner_size(LogicalSize::new(380.0, 380.0))
            .with_resizable(false)
            .with_always_on_top(true)
            .with_visible(false)
            .with_window_icon(window_icon)
            .build(&event_loop)
            .expect("create window"),
    );
    let context = softbuffer::Context::new(window.clone()).expect("softbuffer context");
    let mut surface =
        softbuffer::Surface::new(&context, window.clone()).expect("softbuffer surface");

    // Tray icon and menu.
    let autostart_on = shared.read().map(|c| c.autostart).unwrap_or(true);
    let show_item = MenuItem::new("Show pairing QR code", true, None);
    let reset_item = MenuItem::new("Reset pairing (new secret)", true, None);
    let auto_item = CheckMenuItem::new("Start with Windows", true, autostart_on, None);
    let folder_item = MenuItem::new("Open settings folder", true, None);
    let quit_item = MenuItem::new("Quit PixelDeck Agent", true, None);
    let menu = Menu::new();
    let _ = menu.append_items(&[
        &show_item,
        &reset_item,
        &auto_item,
        &folder_item,
        &PredefinedMenuItem::separator(),
        &quit_item,
    ]);
    let tray_icon = TrayIcon::from_rgba(icon::rgba(32), 32, 32).expect("tray icon image");
    let mut tray = match TrayIconBuilder::new()
        .with_menu(Box::new(menu))
        .with_tooltip("PixelDeck Agent")
        .with_icon(tray_icon)
        .build()
    {
        Ok(t) => Some(t),
        Err(e) => {
            logln!("tray icon failed: {e}");
            None
        }
    };

    let menu_proxy = event_loop.create_proxy();
    let menu_proxy = Mutex::new(menu_proxy);
    MenuEvent::set_event_handler(Some(move |e: MenuEvent| {
        if let Ok(p) = menu_proxy.lock() {
            let _ = p.send_event(Msg::Menu(e.id));
        }
    }));
    TrayIconEvent::set_event_handler(Some(move |e: TrayIconEvent| {
        if matches!(e, TrayIconEvent::DoubleClick { .. }) {
            if let Ok(p) = proxy.lock() {
                let _ = p.send_event(Msg::TrayDoubleClick);
            }
        }
    }));

    let mut qr: Option<render::Qr> = None;
    let mut deadline: Option<Instant> = None;
    let mut pending_show = show_at_start;
    let show_ids = (
        show_item.id().clone(),
        reset_item.id().clone(),
        auto_item.id().clone(),
    );
    let folder_id = folder_item.id().clone();
    let quit_id = quit_item.id().clone();

    let _keep_runtime_alive = rt;
    event_loop.run(move |event, _, control_flow| {
        *control_flow = ControlFlow::Wait;

        let mut show = false;
        let mut hide = false;
        match event {
            Event::NewEvents(StartCause::Init) => {
                if pending_show {
                    pending_show = false;
                    show = true;
                }
            }
            Event::UserEvent(Msg::Ui(UiEvent::Show)) | Event::UserEvent(Msg::TrayDoubleClick) => {
                show = true
            }
            Event::UserEvent(Msg::Ui(UiEvent::Paired)) => hide = true,
            Event::UserEvent(Msg::Menu(id)) => {
                if id == show_ids.0 {
                    show = true;
                } else if id == show_ids.1 {
                    if let Ok(mut c) = shared.write() {
                        c.token = new_token();
                        c.paired_once = false;
                        if let Err(e) = config::save(&c, &config::config_path()) {
                            logln!("could not save config: {e}");
                        }
                    }
                    logln!("pairing reset from tray menu");
                    show = true;
                } else if id == show_ids.2 {
                    let enabled = auto_item.is_checked();
                    if let Ok(mut c) = shared.write() {
                        c.autostart = enabled;
                        let _ = config::save(&c, &config::config_path());
                    }
                    if let Err(e) = autostart::set(enabled) {
                        logln!("autostart change failed: {e}");
                    }
                } else if id == folder_id {
                    let _ = std::process::Command::new("explorer")
                        .arg(config::config_dir())
                        .spawn();
                } else if id == quit_id {
                    tray.take();
                    *control_flow = ControlFlow::Exit;
                    return;
                }
            }
            Event::WindowEvent {
                event: WindowEvent::CloseRequested,
                ..
            } => hide = true,
            Event::WindowEvent {
                event: WindowEvent::Resized(_),
                ..
            } => window.request_redraw(),
            Event::RedrawRequested(_) => {
                let size = window.inner_size();
                if let (Some(w), Some(h)) =
                    (NonZeroU32::new(size.width), NonZeroU32::new(size.height))
                {
                    if surface.resize(w, h).is_ok() {
                        if let Ok(mut buffer) = surface.buffer_mut() {
                            render::paint(
                                &mut buffer,
                                size.width as usize,
                                size.height as usize,
                                qr.as_ref(),
                            );
                            let _ = buffer.present();
                        }
                    }
                }
            }
            _ => {}
        }

        if show {
            let (new_qr, name) = build_qr(&shared);
            qr = new_qr;
            window.set_title(&format!("PixelDeck - scan with the phone app ({name})"));
            let seconds = shared.read().map(|c| c.qr_seconds).unwrap_or(90).max(10);
            deadline = Some(Instant::now() + Duration::from_secs(seconds));
            window.set_visible(true);
            window.set_minimized(false);
            window.set_focus();
            window.request_redraw();
        }
        if hide {
            window.set_visible(false);
            deadline = None;
        }
        if let Some(d) = deadline {
            if Instant::now() >= d {
                window.set_visible(false);
                deadline = None;
            } else {
                *control_flow = ControlFlow::WaitUntil(d);
            }
        }
    });
}
