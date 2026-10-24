//! Single-instance guard. The first agent binds a loopback control port; a second launch connects
//! to it, asks the first to show its QR window, and exits.

use crate::app::{Notify, UiEvent};
use std::io::Write;
use std::net::{Ipv4Addr, SocketAddr, SocketAddrV4, TcpListener, TcpStream};
use std::time::Duration;
use tokio::io::{AsyncBufReadExt, BufReader};

pub const CONTROL_PORT: u16 = 47801;

fn addr() -> SocketAddr {
    SocketAddr::V4(SocketAddrV4::new(Ipv4Addr::LOCALHOST, CONTROL_PORT))
}

pub enum Instance {
    /// We are the first instance and own the control port.
    First(TcpListener),
    /// Another instance is running and has been asked to show its window.
    Second,
}

pub fn acquire() -> Instance {
    match TcpListener::bind(addr()) {
        Ok(l) => Instance::First(l),
        Err(_) => {
            if let Ok(mut s) = TcpStream::connect_timeout(&addr(), Duration::from_secs(1)) {
                let _ = s.write_all(b"show\n");
                Instance::Second
            } else {
                // Port is taken by something else; run anyway rather than refuse to start.
                Instance::First(TcpListener::bind("127.0.0.1:0").expect("bind loopback"))
            }
        }
    }
}

pub async fn serve(listener: TcpListener, notify: Notify) {
    if listener.set_nonblocking(true).is_err() {
        return;
    }
    let Ok(listener) = tokio::net::TcpListener::from_std(listener) else {
        return;
    };
    loop {
        let Ok((stream, peer)) = listener.accept().await else {
            continue;
        };
        if !peer.ip().is_loopback() {
            continue;
        }
        let notify = notify.clone();
        tokio::spawn(async move {
            let mut line = String::new();
            let mut reader = BufReader::new(stream);
            if reader.read_line(&mut line).await.is_ok() && line.trim() == "show" {
                notify(UiEvent::Show);
            }
        });
    }
}
