use std::ffi::CStr;
use std::os::raw::c_char;
use std::ptr;

const MAX_URL_BYTES: usize = 4096;
const VALID_VIDEO_ID_BYTES: usize = 11;

/// Accept a supported HTTPS URL for one public-format YouTube video.
///
/// This validates input only: it performs no network requests and does not bypass login,
/// DRM, private-content restrictions, or source-service access controls.
pub fn normalize_youtube_url(input: &str) -> Option<&str> {
    let value = input.trim();
    if value.is_empty()
        || value.len() > MAX_URL_BYTES
        || value.chars().any(char::is_whitespace)
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
    if authority.is_empty() || authority.contains('@') {
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

    // Match Java's removeSuffix(".") semantics: accept at most one DNS trailing dot.
    let host = raw_host.strip_suffix('.').unwrap_or(raw_host).to_ascii_lowercase();
    let is_short_host = host == "youtu.be";
    if !is_short_host
        && host != "youtube.com"
        && host != "www.youtube.com"
        && host != "m.youtube.com"
        && host != "music.youtube.com"
    {
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

    let mut segments = path.split('/').filter(|part| !part.is_empty());
    let first_segment = segments.next();
    let second_segment = segments.next();

    let valid_video_id = if is_short_host {
        first_segment.is_some_and(is_valid_video_id)
    } else {
        match first_segment {
            Some("watch") => query
                .and_then(find_video_id_query_value)
                .is_some_and(|id| is_valid_video_id(&id)),
            Some("shorts" | "live" | "embed" | "v") => {
                second_segment.is_some_and(is_valid_video_id)
            }
            _ => false,
        }
    };

    if !valid_video_id {
        return None;
    }

    Some(value)
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
pub extern "C" fn ubahvo_normalize_youtube_url(
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

    let normalized = match normalize_youtube_url(input) {
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
    use super::normalize_youtube_url;

    #[test]
    fn accepts_supported_video_urls() {
        let urls = [
            "https://www.youtube.com/watch?v=abcdefghijk",
            "https://youtu.be/abcdefghijk",
            "https://youtube.com/shorts/abcdefghijk",
            "https://m.youtube.com/live/abcdefghijk",
            "https://music.youtube.com/embed/abcdefghijk",
            "https://youtube.com/v/abcdefghijk",
            "HTTPS://youtube.com:443/watch?v=abcdefghijk",
            " https://youtu.be/abcdefghijk ",
            "https://youtube.com/watch?list=PL123&v=abcdefghijk",
            "https://youtube.com/watch?v=%61bcdefghijk",
        ];

        for url in urls {
            assert!(
                normalize_youtube_url(url).is_some(),
                "expected accepted URL: {url}"
            );
        }
    }

    #[test]
    fn rejects_unsafe_or_unsupported_urls() {
        let urls = [
            "",
            "http://youtube.com/watch?v=abcdefghijk",
            "https://youtube.com.example.org/watch?v=abcdefghijk",
            "https://youtube.com../watch?v=abcdefghijk",
            "https://attacker@youtube.com/watch?v=abcdefghijk",
            "https://youtube.com:8443/watch?v=abcdefghijk",
            "https://youtube.com/playlist?list=PL123",
            "https://youtube.com/watch?v=short",
            "https://youtube.com/watch?v=abcdefghijk%GG",
            "https://youtube.com/watch?v=abcdefghijk extra",
            "https://youtube.com/watch?v=abcdefghijk#section with spaces",
            "file:///youtube.com/watch?v=abcdefghijk",
        ];

        for url in urls {
            assert!(
                normalize_youtube_url(url).is_none(),
                "expected rejected URL: {url}"
            );
        }
    }

    #[test]
    fn rejects_oversized_input() {
        let url = format!("https://youtube.com/watch?v=abcdefghijk&x={}", "a".repeat(5000));
        assert!(normalize_youtube_url(&url).is_none());
    }

    #[test]
    fn rejects_first_invalid_video_parameter_even_if_a_later_one_is_valid() {
        let url = "https://youtube.com/watch?v=short&v=abcdefghijk";
        assert!(normalize_youtube_url(url).is_none());
    }
}
