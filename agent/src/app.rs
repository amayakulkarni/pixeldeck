use crate::config::Config;
use std::sync::{Arc, RwLock};

/// Events the network side sends to the UI side.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UiEvent {
    /// Show the pairing QR window (for example because the exe was launched a second time).
    Show,
    /// A phone authenticated successfully; the QR window can go away.
    Paired,
}

pub type Notify = Arc<dyn Fn(UiEvent) + Send + Sync>;

/// Config shared between the server, the single-instance listener and the UI.
pub type Shared = Arc<RwLock<Config>>;
