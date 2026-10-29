use std::ffi::CStr;
use std::os::raw::c_char;
use std::ptr;

const MAX_URL_BYTES: usize = 4096;
const VALID_VIDEO_ID_BYTES: usize = 11;

/// Validates an HTTPS media URL for yt-dlp.
///
/// This is a syntactic preflight only. Whether a particular site currently works is decided
/// by yt-dlp's extractor and may change as the site changes.
pub fn normalize_media_url(input: &str) -> Option<&str> {
    let value = input.trim();
    if value.is_empty()
        || value.len() > MAX_URL_BYTES
        || value.chars().any(|ch| ch.is_whitespace() || ch.is_control())
        || !has_valid_percent_escapes(value)
    {
        return None;
    }

    let (scheme, remainder) = value.split_once("://")?;
    if !scheme.eq_ignore_ascii_case("https") {
        return None;
    }

    let authority_end = remainder
        .find(|ch| matches!(ch, '/' | '?' | '#'))
        .unwrap_or(remainder.len());
    let authority = &remainder[..authority_end];
    if authority.is_empty()
        || authority.contains('@')
        || authority.contains('[')
        || authority.contains(']')
        || authority.contains('\\')
        || authority.ends_with("..")
    {
        return None;
    }

    let (raw_host, port) = match authority.rsplit_once(':') {
        Some((host, port)) if !host.contains(':') => (host, Some(port)),
        Some(_) => return None,
        None => (authority, None),
    };
    if port.is_some_and(|port| port != "443") || raw_host.is_empty() {
        return None;
    }

    let host = raw_host
        .strip_suffix('.')
        .unwrap_or(raw_host)
        .to_ascii_lowercase();
    if !is_public_dns_host(&host) {
        return None;
    }

    let after_authority = &remainder[authority_end..];
    let path_end = after_authority
        .find(|ch| matches!(ch, '?' | '#'))
        .unwrap_or(after_authority.len());
    let path = &after_authority[..path_end];
    let suffix = &after_authority[path_end..];
    let query = suffix
        .strip_prefix('?')
        .map(|value| value.split('#').next().unwrap_or(""));

    // Preserve the existing single-video behavior for YouTube while allowing other public
    // media domains through to yt-dlp's site-specific/generic extractors.
    if is_youtube_host(&host) && !is_supported_single_youtube_url(&host, path, query) {
        return None;
    }

    Some(value)
}

fn is_public_dns_host(host: &str) -> bool {
    if host.len() > 253
        || !host.contains('.')
        || host.starts_with('.')
        || host.ends_with('.')
        || host == "localhost"
        || host.ends_with(".localhost")
        || host.ends_with(".local")
        || host.ends_with(".internal")
        || host.ends_with(".lan")
        || host.ends_with(".home.arpa")
        || host.ends_with(".test")
        || host.ends_with(".invalid")
    {
        return false;
    }

    let labels: Vec<&str> = host.split('.').collect();
    if labels.iter().any(|label| {
        label.is_empty()
            || label.len() > 63
            || !label.as_bytes()[0].is_ascii_alphanumeric()
            || !label.as_bytes()[label.len() - 1].is_ascii_alphanumeric()
            || !label
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-')
    }) {
        return false;
    }

    // DNS names only: reject IPv4 decimal and common hexadecimal integer spellings.
    let all_decimal = labels.iter().all(|label| label.bytes().all(|b| b.is_ascii_digit()));
    let all_numeric_or_hex = labels
        .iter()
        .all(|label| is_decimal_label(label) || is_hex_label(label));
    !(all_decimal || (labels.len() > 1 && all_numeric_or_hex))
}

fn is_decimal_label(value: &str) -> bool {
    !value.is_empty() && value.bytes().all(|byte| byte.is_ascii_digit())
}

fn is_hex_label(value: &str) -> bool {
    let digits = value
        .strip_prefix("0x")
        .or_else(|| value.strip_prefix("0X"));
    digits.is_some_and(|digits| {
        !digits.is_empty() && digits.bytes().all(|byte| byte.is_ascii_hexdigit())
    })
}

fn is_youtube_host(host: &str) -> bool {
    matches!(
        host,
        "youtube.com" | "www.youtube.com" | "m.youtube.com" | "music.youtube.com" | "youtu.be"
    )
}

fn is_supported_single_youtube_url(host: &str, path: &str, query: Option<&str>) -> bool {
    let mut segments = path.split('/').filter(|part| !part.is_empty());
    let first_segment = segments.next();
    let second_segment = segments.next();

    let video_id = if host == "youtu.be" {
        first_segment
    } else {
        match first_segment {
            Some("watch") => query.and_then(find_video_id_query_value).as_deref(),
            Some("shorts" | "live" | "embed" | "v") => second_segment,
            _ => None,
        }
    };
    video_id.is_some_and(is_valid_video_id)
}

fn is_valid_video_id(value: &str) -> bool {
    let bytes = value.as_bytes();
    bytes.len() == VALID_VIDEO_ID_BYTES
        && bytes
            .iter()
            .all(|byte| byte.is_ascii_alphanumeric() || *byte == b'_' || *byte == b'-')
}

fn find_video_id_query_value(query: &str) -> Option<String> {
    for item in query.split('&') {
        let (key, value) = item.split_once('=').unwrap_or((item, ""));
        if key == "v" {
            return decode_query_component(value);
        }
    }
    None
}

fn decode_query_component(value: &str) -> Option<String> {
    let bytes = value.as_bytes();
    let mut decoded = Vec::with_capacity(bytes.len());
    let mut index = 0;

    while index < bytes.len() {
        match bytes[index] {
            b'+' => {
                decoded.push(b' ');
                index += 1;
            }
            b'%' => {
                if index + 2 >= bytes.len() {
                    return None;
                }
                let high = hex_value(bytes[index + 1])?;
                let low = hex_value(bytes[index + 2])?;
                decoded.push((high << 4) | low);
                index += 3;
            }
            byte => {
                decoded.push(byte);
                index += 1;
            }
        }
    }

    String::from_utf8(decoded).ok()
}

fn hex_value(value: u8) -> Option<u8> {
    match value {
        b'0'..=b'9' => Some(value - b'0'),
        b'a'..=b'f' => Some(value - b'a' + 10),
        b'A'..=b'F' => Some(value - b'A' + 10),
        _ => None,
    }
}

fn has_valid_percent_escapes(value: &str) -> bool {
    let bytes = value.as_bytes();
    let mut index = 0;

    while index < bytes.len() {
        if bytes[index] == b'%' {
            if index + 2 >= bytes.len()
                || hex_value(bytes[index + 1]).is_none()
                || hex_value(bytes[index + 2]).is_none()
            {
                return false;
            }
            index += 3;
        } else {
            index += 1;
        }
    }

    true
}

/// C ABI entry point called by the C++ JNI adapter.
///
/// Return values: positive = number of output bytes; zero = invalid URL;
/// negative = invalid buffers/encoding or an undersized output buffer.
#[no_mangle]
pub extern "C" fn ubahvo_normalize_media_url(
    input: *const c_char,
    output: *mut c_char,
    output_capacity: usize,
) -> i32 {
    if input.is_null() || output.is_null() || output_capacity == 0 {
        return -1;
    }

    let c_input = unsafe { CStr::from_ptr(input) };
    let input = match c_input.to_str() {
        Ok(value) => value,
        Err(_) => return -1,
    };

    let normalized = match normalize_media_url(input) {
        Some(value) => value,
        None => return 0,
    };
    let bytes = normalized.as_bytes();

    if bytes.len() + 1 > output_capacity || bytes.len() > i32::MAX as usize {
        return -2;
    }

    unsafe {
        ptr::copy_nonoverlapping(bytes.as_ptr(), output.cast::<u8>(), bytes.len());
        output.add(bytes.len()).write(0);
    }

    bytes.len() as i32
}

#[cfg(test)]
mod tests {
    use super::normalize_media_url;

    #[test]
    fn accepts_common_public_media_urls() {
        let urls = [
            "https://www.youtube.com/watch?v=abcdefghijk",
            "https://youtu.be/abcdefghijk",
            "https://youtube.com/shorts/abcdefghijk",
            "https://vimeo.com/123456789",
            "https://www.tiktok.com/@creator/video/1234567890",
            "https://www.instagram.com/reel/C0ffee12345/",
            "https://soundcloud.com/artist/track-name",
            "https://x.com/creator/status/1234567890123456789",
            "https://files.example.org/media/demo.mp4",
            "HTTPS://youtube.com:443/watch?v=abcdefghijk",
            "https://youtube.com/watch?list=PL123&v=abcdefghijk",
        ];

        for url in urls {
            assert_eq!(normalize_media_url(url), Some(url), "URL: {url}");
        }
    }

    #[test]
    fn rejects_unsafe_or_unsupported_urls() {
        let urls = [
            "",
            "http://youtube.com/watch?v=abcdefghijk",
            "ftp://files.example.org/demo.mp4",
            "https://youtube.com.example.org../watch?v=abcdefghijk",
            "https://attacker@example.org/media.mp4",
            "https://example.org:8443/media.mp4",
            "https://example.org../media.mp4",
            "https://localhost/media.mp4",
            "https://nas.local/media.mp4",
            "https://127.0.0.1/media.mp4",
            "https://192.168.1.10/media.mp4",
            "https://0x7f.0.0.1/media.mp4",
            "https://example.org/%GG",
            "https://example.org/media file.mp4",
            "https://youtube.com/playlist?list=PL123456789",
            "https://youtube.com/watch?v=short",
        ];

        for url in urls {
            assert!(normalize_media_url(url).is_none(), "URL should be rejected: {url}");
        }
    }

    #[test]
    fn rejects_oversized_input() {
        let url = format!("https://files.example.org/media.mp4?x={}", "a".repeat(5000));
        assert!(normalize_media_url(&url).is_none());
    }
}
