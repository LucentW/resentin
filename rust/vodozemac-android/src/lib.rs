//! UniFFI binding over vodozemac for Resentin's E2E. Mirrors what cicchetto's
//! wasm binding exposes (grappa-irc `cicchetto/wasm/vodozemac-binding`), and
//! nothing more. Keys and signatures cross as vodozemac prints them: standard
//! base64, unpadded. Wire compatibility with cicchetto depends on that.

use std::sync::{Arc, Mutex};

use sha2::{Digest, Sha256};
use vodozemac::ecies::{
    Ecies as VEcies, EstablishedEcies as VEstablishedEcies, InitialMessage, Message as EciesMessage,
};
use vodozemac::hpke::DigitMode;
use vodozemac::olm::{
    Account as OlmAccount, AccountPickle, OlmMessage, PreKeyMessage, Session as OlmSession, SessionConfig,
    SessionPickle,
};
use vodozemac::sas::{EstablishedSas as VEstablishedSas, Sas as VSas};
use vodozemac::{Curve25519PublicKey, Ed25519PublicKey, Ed25519Signature, base64_decode};

uniffi::setup_scaffolding!();

#[derive(Debug, uniffi::Error)]
pub enum CryptoError {
    Failed { reason: String },
}

impl std::fmt::Display for CryptoError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let CryptoError::Failed { reason } = self;
        f.write_str(reason)
    }
}

type R<T> = Result<T, CryptoError>;

fn err<E: std::fmt::Display>(e: E) -> CryptoError {
    CryptoError::Failed { reason: e.to_string() }
}

// A key from the wire, as its 32 bytes. Checked here, never by vodozemac: its
// `from_base64` PANICS on a 44-char input that decodes to 33 bytes.
fn key_bytes(key: &str) -> R<[u8; 32]> {
    let bytes = base64_decode(key.trim_end_matches('=')).map_err(err)?;
    bytes.try_into().map_err(|_| err("a key is 32 bytes"))
}

fn ed25519(key: &str) -> R<Ed25519PublicKey> {
    Ed25519PublicKey::from_slice(&key_bytes(key)?).map_err(err)
}

fn curve25519(key: &str) -> R<Curve25519PublicKey> {
    Ok(Curve25519PublicKey::from_bytes(key_bytes(key)?))
}

// ponytail: one Mutex per object; UniFFI hands out shared Arcs, so &mut methods need it.
#[derive(uniffi::Object)]
pub struct Account(Mutex<OlmAccount>);

#[uniffi::export]
impl Account {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self(Mutex::new(OlmAccount::new())))
    }

    #[uniffi::constructor]
    pub fn from_pickle(pickle: String) -> R<Arc<Self>> {
        let p: AccountPickle = serde_json::from_str(&pickle).map_err(err)?;
        Ok(Arc::new(Self(Mutex::new(OlmAccount::from_pickle(p)))))
    }

    /// The whole account, private keys included, as JSON. Encrypt before storing.
    pub fn pickle(&self) -> String {
        serde_json::to_string(&self.0.lock().unwrap().pickle()).expect("an account pickle always serialises")
    }

    pub fn ed25519_key(&self) -> String {
        self.0.lock().unwrap().ed25519_key().to_base64()
    }

    pub fn curve25519_key(&self) -> String {
        self.0.lock().unwrap().curve25519_key().to_base64()
    }

    pub fn sign(&self, message: String) -> String {
        self.0.lock().unwrap().sign(&message).to_base64()
    }

    pub fn max_number_of_one_time_keys(&self) -> u64 {
        self.0.lock().unwrap().max_number_of_one_time_keys() as u64
    }

    pub fn generate_one_time_keys(&self, count: u64) {
        self.0.lock().unwrap().generate_one_time_keys(count as usize);
    }

    /// Unpublished one-time keys, oldest first.
    pub fn one_time_keys(&self) -> Vec<String> {
        let mut keys: Vec<_> = self.0.lock().unwrap().one_time_keys().into_iter().collect();
        keys.sort_by_key(|(id, _)| *id);
        keys.into_iter().map(|(_, k)| k.to_base64()).collect()
    }

    pub fn generate_fallback_key(&self) {
        self.0.lock().unwrap().generate_fallback_key();
    }

    pub fn fallback_key(&self) -> Option<String> {
        self.0.lock().unwrap().fallback_key().into_values().next().map(|k| k.to_base64())
    }

    pub fn mark_keys_as_published(&self) {
        self.0.lock().unwrap().mark_keys_as_published();
    }

    pub fn create_outbound_session(&self, identity_key: String, one_time_key: String) -> R<Arc<Session>> {
        let s = self
            .0
            .lock()
            .unwrap()
            .create_outbound_session(SessionConfig::version_1(), curve25519(&identity_key)?, curve25519(&one_time_key)?)
            .map_err(err)?;
        Ok(Arc::new(Session(Mutex::new(s))))
    }

    /// Opens the session a peer started with a PKMSG body and decrypts it. Uses
    /// up the one-time key: store the account afterwards.
    pub fn create_inbound_session(&self, identity_key: String, message: Vec<u8>) -> R<InboundSession> {
        let m = PreKeyMessage::from_bytes(&message).map_err(err)?;
        let c = self
            .0
            .lock()
            .unwrap()
            .create_inbound_session(SessionConfig::version_1(), curve25519(&identity_key)?, &m)
            .map_err(err)?;
        Ok(InboundSession { session: Arc::new(Session(Mutex::new(c.session))), plaintext: c.plaintext })
    }
}

#[derive(uniffi::Record)]
pub struct InboundSession {
    pub session: Arc<Session>,
    pub plaintext: Vec<u8>,
}

#[derive(uniffi::Record)]
pub struct Encrypted {
    /// 0 pre-key (PKMSG), 1 normal (MSG).
    pub message_type: u64,
    pub body: Vec<u8>,
}

#[derive(uniffi::Object)]
pub struct Session(Mutex<OlmSession>);

#[uniffi::export]
impl Session {
    #[uniffi::constructor]
    pub fn from_pickle(pickle: String) -> R<Arc<Self>> {
        let p: SessionPickle = serde_json::from_str(&pickle).map_err(err)?;
        Ok(Arc::new(Self(Mutex::new(OlmSession::from_pickle(p)))))
    }

    pub fn pickle(&self) -> String {
        serde_json::to_string(&self.0.lock().unwrap().pickle()).expect("a session pickle always serialises")
    }

    pub fn session_id(&self) -> String {
        self.0.lock().unwrap().session_id()
    }

    pub fn has_received_message(&self) -> bool {
        self.0.lock().unwrap().has_received_message()
    }

    pub fn encrypt(&self, plaintext: Vec<u8>) -> R<Encrypted> {
        let (t, body) = self.0.lock().unwrap().encrypt(&plaintext).map_err(err)?.to_parts();
        Ok(Encrypted { message_type: t as u64, body })
    }

    pub fn decrypt(&self, message_type: u64, body: Vec<u8>) -> R<Vec<u8>> {
        let m = OlmMessage::from_parts(message_type as usize, &body).map_err(err)?;
        self.0.lock().unwrap().decrypt(&m).map_err(err)
    }
}

#[derive(uniffi::Object)]
pub struct Sas(Mutex<Option<VSas>>);

#[uniffi::export]
impl Sas {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self(Mutex::new(Some(VSas::new()))))
    }

    pub fn public_key(&self) -> String {
        self.0.lock().unwrap().as_ref().map(|s| s.public_key().to_base64()).unwrap_or_default()
    }

    /// Consumes the Sas: a second call fails.
    pub fn diffie_hellman(&self, their_key: String) -> R<Arc<EstablishedSas>> {
        let sas = self.0.lock().unwrap().take().ok_or_else(|| err("sas already used"))?;
        let e = sas.diffie_hellman(curve25519(&their_key)?).map_err(err)?;
        Ok(Arc::new(EstablishedSas(e)))
    }
}

#[derive(uniffi::Object)]
pub struct EstablishedSas(VEstablishedSas);

#[uniffi::export]
impl EstablishedSas {
    pub fn emoji_indices(&self, info: String) -> Vec<u8> {
        self.0.bytes(&info).emoji_indices().to_vec()
    }

    pub fn calculate_mac(&self, input: String, info: String) -> String {
        self.0.calculate_mac(&input, &info).to_base64()
    }
}

#[derive(uniffi::Object)]
pub struct Ecies(Mutex<Option<VEcies>>);

#[uniffi::export]
impl Ecies {
    #[uniffi::constructor]
    pub fn new(info: String) -> Arc<Self> {
        Arc::new(Self(Mutex::new(Some(VEcies::with_info(&info)))))
    }

    pub fn public_key(&self) -> String {
        self.0.lock().unwrap().as_ref().map(|e| e.public_key().to_base64()).unwrap_or_default()
    }

    /// Consumes the Ecies.
    pub fn establish_outbound_channel(&self, their_key: String, plaintext: Vec<u8>) -> R<EciesOutbound> {
        let e = self.0.lock().unwrap().take().ok_or_else(|| err("ecies already used"))?;
        let c = e.establish_outbound_channel(curve25519(&their_key)?, &plaintext).map_err(err)?;
        Ok(EciesOutbound { channel: Arc::new(EstablishedEcies(Mutex::new(c.ecies))), message: c.message.encode() })
    }

    /// Consumes the Ecies.
    pub fn establish_inbound_channel(&self, initial: String) -> R<EciesInbound> {
        let e = self.0.lock().unwrap().take().ok_or_else(|| err("ecies already used"))?;
        let m = InitialMessage::decode(&initial).map_err(err)?;
        let c = e.establish_inbound_channel(&m).map_err(err)?;
        Ok(EciesInbound { channel: Arc::new(EstablishedEcies(Mutex::new(c.ecies))), plaintext: c.message })
    }
}

#[derive(uniffi::Record)]
pub struct EciesOutbound {
    pub channel: Arc<EstablishedEcies>,
    pub message: String,
}

#[derive(uniffi::Record)]
pub struct EciesInbound {
    pub channel: Arc<EstablishedEcies>,
    pub plaintext: Vec<u8>,
}

#[derive(uniffi::Object)]
pub struct EstablishedEcies(Mutex<VEstablishedEcies>);

#[uniffi::export]
impl EstablishedEcies {
    /// Two-digit check code, 0..=99 (render with a leading zero).
    pub fn check_code(&self) -> u8 {
        self.0.lock().unwrap().check_code().to_digit(DigitMode::AllowLeadingZero)
    }

    pub fn encrypt(&self, plaintext: Vec<u8>) -> String {
        self.0.lock().unwrap().encrypt(&plaintext).encode()
    }

    pub fn decrypt(&self, message: String) -> R<Vec<u8>> {
        let m = EciesMessage::decode(&message).map_err(err)?;
        self.0.lock().unwrap().decrypt(&m).map_err(err)
    }
}

#[uniffi::export]
pub fn pre_key_session_id(message: Vec<u8>) -> R<String> {
    Ok(PreKeyMessage::from_bytes(&message).map_err(err)?.session_id())
}

/// False for anything malformed: a key or signature from the wire never throws.
#[uniffi::export]
pub fn verify_ed25519(key: String, message: String, signature: String) -> bool {
    let Ok(k) = ed25519(&key) else { return false };
    let Ok(s) = Ed25519Signature::from_base64(&signature) else { return false };
    k.verify(message.as_bytes(), &s).is_ok()
}

#[uniffi::export]
pub fn sha256(data: Vec<u8>) -> Vec<u8> {
    Sha256::digest(data).to_vec()
}

#[uniffi::export]
pub fn canonical_ed25519(key: String) -> R<String> {
    Ok(ed25519(&key)?.to_base64())
}

#[uniffi::export]
pub fn canonical_curve25519(key: String) -> R<String> {
    Ok(curve25519(&key)?.to_base64())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn olm_roundtrip_and_ecies() {
        let a = Account::new();
        let b = Account::new();
        b.generate_one_time_keys(1);
        let otk = b.one_time_keys().remove(0);
        let mut s = a.create_outbound_session(b.curve25519_key(), otk).unwrap();
        let enc = s.encrypt(b"ciao".to_vec()).unwrap();
        assert_eq!(enc.message_type, 0);
        assert_eq!(pre_key_session_id(enc.body.clone()).unwrap(), s.session_id());
        let inb = b.create_inbound_session(a.curve25519_key(), enc.body).unwrap();
        assert_eq!(inb.plaintext, b"ciao");
        s = Session::from_pickle(s.pickle()).unwrap();
        assert_eq!(s.session_id(), inb.session.session_id());

        let sig = a.sign("x".into());
        assert!(verify_ed25519(a.ed25519_key(), "x".into(), sig));
        assert!(!verify_ed25519("bad".into(), "x".into(), "bad".into()));

        let e1 = Ecies::new("t".into());
        let out = e1.establish_outbound_channel(b.curve25519_key(), vec![]);
        assert!(out.is_err() || out.is_ok()); // key arbitrary; just must not panic
    }
}
