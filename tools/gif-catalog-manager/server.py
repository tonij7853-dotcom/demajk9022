from __future__ import annotations

import hashlib
import html.parser
import ipaddress
import json
import os
import re
import socket
import tempfile
import threading
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import uuid
import warnings
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
from pathlib import Path
from typing import Any, Iterator

from PIL import Image, ImageSequence


ROOT = Path(__file__).resolve().parents[2]
GIFS_DIR = ROOT / "community-assets" / "gifs"
CATALOG_FILE = GIFS_DIR / "catalog.json"
WEB_DIR = Path(__file__).resolve().parent / "web"
HOST = "127.0.0.1"
PORT = 8765
MAX_SOURCE_BYTES = 40 * 1024 * 1024
MAX_OUTPUT_BYTES = 15 * 1024 * 1024
MAX_HTML_BYTES = 2 * 1024 * 1024
MAX_CATALOG_ITEMS = 300
MAX_FRAMES = 240
MAX_PIXELS = 20_000_000
STAGE_TTL_SECONDS = 60 * 60
RAW_ASSET_PREFIX = "https://raw.githubusercontent.com/tonij7853-dotcom/demajk9022/main/community-assets/gifs/"

Image.MAX_IMAGE_PIXELS = MAX_PIXELS


class StudioError(Exception):
    def __init__(self, message: str, status: int = 400):
        super().__init__(message)
        self.status = status


class MetaImageParser(html.parser.HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.images: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = {key.lower(): value for key, value in attrs if value}
        if tag.lower() == "meta":
            key = (values.get("property") or values.get("name") or "").lower()
            if key in {"og:image", "og:image:url", "twitter:image", "twitter:image:src"}:
                value = values.get("content")
                if value:
                    self.images.append(value.strip())


class PublicHttpsRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req: urllib.request.Request, fp: Any, code: int, msg: str,
                         headers: Any, newurl: str) -> urllib.request.Request | None:
        target = urllib.parse.urljoin(req.full_url, newurl)
        validate_public_https_url(target)
        return super().redirect_request(req, fp, code, msg, headers, target)


def validate_public_https_url(value: str) -> urllib.parse.SplitResult:
    try:
        parsed = urllib.parse.urlsplit(value)
        if parsed.scheme.lower() != "https" or not parsed.hostname or parsed.username or parsed.password:
            raise StudioError("Use a public HTTPS link.")
        if parsed.port not in (None, 443):
            raise StudioError("Links must use the standard HTTPS port.")

        host = parsed.hostname.rstrip(".")
        try:
            addresses = [ipaddress.ip_address(host)]
        except ValueError:
            addresses = [
                ipaddress.ip_address(item[4][0])
                for item in socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
            ]
        if not addresses or any(not address.is_global for address in addresses):
            raise StudioError("That link does not resolve to a public internet address.")
        return parsed
    except (ValueError, socket.gaierror, OSError) as error:
        if isinstance(error, StudioError):
            raise
        raise StudioError("That link could not be resolved. Check the URL and try again.") from error


def read_response(response: Any, limit: int) -> bytes:
    content_length = response.headers.get("Content-Length")
    if content_length and content_length.isdigit() and int(content_length) > limit:
        raise StudioError("The source file is too large to import.", 413)
    output = bytearray()
    while True:
        chunk = response.read(min(64 * 1024, limit + 1 - len(output)))
        if not chunk:
            break
        output.extend(chunk)
        if len(output) > limit:
            raise StudioError("The source file is too large to import.", 413)
    return bytes(output)


def fetch_url(value: str) -> tuple[bytes, str, str]:
    validate_public_https_url(value)
    request = urllib.request.Request(
        value,
        headers={
            "User-Agent": "Dismod-GIF-Studio/1.0",
            "Accept": "image/gif,image/webp,image/apng,image/png,image/jpeg,text/html;q=0.8,*/*;q=0.5",
        },
    )
    opener = urllib.request.build_opener(PublicHttpsRedirectHandler())
    try:
        with opener.open(request, timeout=20) as response:
            content_type = response.headers.get_content_type().lower()
            limit = MAX_HTML_BYTES if content_type in {"text/html", "application/xhtml+xml"} else MAX_SOURCE_BYTES
            return read_response(response, limit), content_type, response.geturl()
    except urllib.error.HTTPError as error:
        raise StudioError(f"The source site returned HTTP {error.code}. Check that the link still works.") from error
    except urllib.error.URLError as error:
        raise StudioError("Couldn't reach that link. It may have expired or block downloads.") from error
    except TimeoutError as error:
        raise StudioError("The source site took too long to respond. Try again.") from error


def resolve_image_sources(value: str) -> Iterator[tuple[bytes, str]]:
    body, content_type, final_url = fetch_url(value)
    looks_like_html = body.lstrip()[:128].lower().startswith((b"<!doctype html", b"<html", b"<!--"))
    if content_type not in {"text/html", "application/xhtml+xml"} and not looks_like_html:
        yield body, final_url
        return

    parser = MetaImageParser()
    try:
        parser.feed(body.decode("utf-8", errors="replace"))
    except Exception as error:
        raise StudioError("This page does not expose a GIF preview. Paste a direct image link instead.") from error

    candidates: list[str] = []
    for candidate in parser.images:
        image_url = urllib.parse.urljoin(final_url, candidate)
        if image_url not in candidates:
            candidates.append(image_url)
    if not candidates:
        raise StudioError("This page does not expose a GIF preview. Paste a direct image link instead.")

    yielded = False
    for candidate in candidates[:4]:
        try:
            data, _, media_url = fetch_url(candidate)
            yielded = True
            yield data, media_url
        except StudioError:
            continue
    if not yielded:
        raise StudioError("Couldn't download an image from that page. Try its direct GIF/media URL.")
    return results


def normalize_as_gif(data: bytes) -> tuple[bytes, dict[str, int]]:
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            source = Image.open(BytesIO(data))
            with source:
                if source.format not in {"GIF", "WEBP", "PNG", "JPEG", "BMP"}:
                    raise StudioError("That link isn't a supported image. Use a GIF, animated WebP, PNG, or JPEG.")
                if source.width * source.height > MAX_PIXELS:
                    raise StudioError("That image has too many pixels to safely convert.")
                frame_count = min(int(getattr(source, "n_frames", 1)), MAX_FRAMES + 1)
                if frame_count > MAX_FRAMES:
                    raise StudioError(f"That animation has more than {MAX_FRAMES} frames.")

                durations: list[int] = []
                frames: list[Image.Image] = []
                for frame in ImageSequence.Iterator(source):
                    if len(frames) >= MAX_FRAMES:
                        raise StudioError(f"That animation has more than {MAX_FRAMES} frames.")
                    duration = int(frame.info.get("duration", source.info.get("duration", 100)) or 100)
                    durations.append(min(max(duration, 20), 10_000))
                    rgba = frame.convert("RGBA")
                    if max(rgba.size) > 1024:
                        scale = 1024 / max(rgba.size)
                        size = (max(1, round(rgba.width * scale)), max(1, round(rgba.height * scale)))
                        rgba = rgba.resize(size, Image.Resampling.LANCZOS)
                    frames.append(rgba)

                if not frames:
                    raise StudioError("That link did not contain a readable image.")

                converted = BytesIO()
                frames[0].save(
                    converted,
                    format="GIF",
                    save_all=True,
                    append_images=frames[1:],
                    duration=durations,
                    loop=int(source.info.get("loop", 0)),
                    disposal=2,
                    optimize=True,
                )
                output = converted.getvalue()
                if len(output) > MAX_OUTPUT_BYTES:
                    raise StudioError("The converted GIF is over 15 MB. Use a shorter or smaller animation.", 413)

                with Image.open(BytesIO(output)) as check:
                    if check.format != "GIF" or int(getattr(check, "n_frames", 1)) < 1:
                        raise StudioError("This image could not be converted to a valid GIF.")
                    return output, {
                        "width": check.width,
                        "height": check.height,
                        "frames": int(getattr(check, "n_frames", 1)),
                    }
    except StudioError:
        raise
    except (Image.DecompressionBombError, Image.DecompressionBombWarning):
        raise StudioError("That image has too many pixels to safely convert.")
    except Exception as error:
        raise StudioError("That link didn't contain a readable GIF or image. Try a direct media URL.") from error


def json_catalog() -> dict[str, Any]:
    try:
        data = json.loads(CATALOG_FILE.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return {"gifs": []}
    except (OSError, json.JSONDecodeError) as error:
        raise StudioError("catalog.json could not be read. Fix the JSON file and reload the page.", 500) from error
    if not isinstance(data, dict) or not isinstance(data.get("gifs", []), list):
        raise StudioError("catalog.json must contain a gifs array.", 500)
    data.setdefault("gifs", [])
    return data


def write_catalog(data: dict[str, Any]) -> None:
    CATALOG_FILE.parent.mkdir(parents=True, exist_ok=True)
    temp_path = CATALOG_FILE.with_name(f"catalog.{uuid.uuid4().hex}.tmp")
    try:
        temp_path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        os.replace(temp_path, CATALOG_FILE)
    finally:
        temp_path.unlink(missing_ok=True)


def safe_slug(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii")
    slug = re.sub(r"[^a-zA-Z0-9]+", "-", normalized).strip("-").lower()
    return (slug or "gif")[:48].rstrip("-") or "gif"


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def local_preview_url(item: dict[str, Any]) -> str:
    media_url = str(item.get("mediaUrl", ""))
    if media_url.startswith(RAW_ASSET_PREFIX):
        filename = urllib.parse.unquote(media_url[len(RAW_ASSET_PREFIX):])
        if re.fullmatch(r"[A-Za-z0-9_-]{1,80}\.gif", filename) and (GIFS_DIR / filename).is_file():
            return f"/api/local-gifs/{urllib.parse.quote(filename)}"
    return str(item.get("previewUrl") or media_url)


staged_files: dict[str, dict[str, Any]] = {}
staged_lock = threading.Lock()
catalog_lock = threading.Lock()
stage_dir = Path(tempfile.mkdtemp(prefix="dismod-gif-studio-"))


def prune_stages() -> None:
    cutoff = time.time() - STAGE_TTL_SECONDS
    with staged_lock:
        expired = [key for key, item in staged_files.items() if item["created"] < cutoff]
        for key in expired:
            staged_files.pop(key)["path"].unlink(missing_ok=True)


class StudioHandler(BaseHTTPRequestHandler):
    server_version = "DismodGifStudio/1.0"

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"[gif-studio] {self.address_string()} {fmt % args}")

    def _security_headers(self, content_type: str) -> None:
        self.send_header("Content-Type", content_type)
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Cache-Control", "no-store")
        self.send_header(
            "Content-Security-Policy",
            "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'",
        )

    def _send_bytes(self, status: int, content_type: str, body: bytes) -> None:
        self.send_response(status)
        self._security_headers(content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_json(self, status: int, payload: dict[str, Any]) -> None:
        self._send_bytes(status, "application/json; charset=utf-8", json.dumps(payload, ensure_ascii=False).encode("utf-8"))

    def _check_host(self) -> bool:
        host = self.headers.get("Host", "").lower()
        allowed = {f"{HOST}:{PORT}", f"localhost:{PORT}"}
        if host not in allowed:
            self._send_json(403, {"error": "This local GIF manager only accepts requests from this computer."})
            return False
        return True

    def _check_origin(self) -> bool:
        origin = self.headers.get("Origin")
        if origin is not None and origin not in {f"http://{HOST}:{PORT}", f"http://localhost:{PORT}"}:
            self._send_json(403, {"error": "Cross-site requests are not allowed."})
            return False
        return True

    def _read_json(self) -> dict[str, Any]:
        content_type = self.headers.get("Content-Type", "").split(";", 1)[0].strip().lower()
        if content_type != "application/json":
            raise StudioError("Expected a JSON request.", 415)
        length = int(self.headers.get("Content-Length", "0"))
        if length < 1 or length > 64 * 1024:
            raise StudioError("The request is empty or too large.", 413)
        try:
            payload = json.loads(self.rfile.read(length))
        except (json.JSONDecodeError, UnicodeDecodeError) as error:
            raise StudioError("Couldn't read the request. Check the form values.") from error
        if not isinstance(payload, dict):
            raise StudioError("The request data must be an object.")
        return payload

    def do_GET(self) -> None:
        if not self._check_host():
            return
        path = urllib.parse.urlsplit(self.path).path
        static_files = {
            "/": (WEB_DIR / "index.html", "text/html; charset=utf-8"),
            "/styles.css": (WEB_DIR / "styles.css", "text/css; charset=utf-8"),
            "/app.js": (WEB_DIR / "app.js", "text/javascript; charset=utf-8"),
        }
        if path in static_files:
            file_path, content_type = static_files[path]
            try:
                self._send_bytes(200, content_type, file_path.read_bytes())
            except OSError:
                self._send_json(500, {"error": "The GIF manager files could not be read."})
            return
        if path == "/api/catalog":
            try:
                data = json_catalog()
                entries = []
                for item in data["gifs"]:
                    if isinstance(item, dict):
                        entries.append({**item, "localPreviewUrl": local_preview_url(item)})
                self._send_json(200, {"gifs": entries})
            except StudioError as error:
                self._send_json(error.status, {"error": str(error)})
            return
        if path.startswith("/api/preview/"):
            key = path.removeprefix("/api/preview/")
            with staged_lock:
                item = staged_files.get(key)
            if item is None or item["created"] < time.time() - STAGE_TTL_SECONDS:
                self._send_json(404, {"error": "That preview expired. Check the link again."})
                return
            try:
                self._send_bytes(200, "image/gif", item["path"].read_bytes())
            except OSError:
                self._send_json(404, {"error": "That preview expired. Check the link again."})
            return
        if path.startswith("/api/local-gifs/"):
            filename = urllib.parse.unquote(path.removeprefix("/api/local-gifs/"))
            if not re.fullmatch(r"[A-Za-z0-9_-]{1,80}\.gif", filename):
                self._send_json(404, {"error": "GIF not found."})
                return
            file_path = GIFS_DIR / filename
            try:
                self._send_bytes(200, "image/gif", file_path.read_bytes())
            except OSError:
                self._send_json(404, {"error": "GIF not found."})
            return
        self._send_json(404, {"error": "Not found."})

    def do_POST(self) -> None:
        if not self._check_host() or not self._check_origin():
            return
        path = urllib.parse.urlsplit(self.path).path
        try:
            payload = self._read_json()
            if path == "/api/preview":
                self._preview(payload)
            elif path == "/api/publish":
                with catalog_lock:
                    self._publish(payload)
            elif path == "/api/delete":
                with catalog_lock:
                    self._delete(payload)
            else:
                self._send_json(404, {"error": "Not found."})
        except StudioError as error:
            self._send_json(error.status, {"error": str(error)})
        except Exception as error:
            print(f"[gif-studio] request failed: {error}")
            self._send_json(500, {"error": "Something went wrong. Your existing GIF catalog was left unchanged."})

    def _preview(self, payload: dict[str, Any]) -> None:
        source_url = str(payload.get("url", "")).strip()
        if len(source_url) > 4096:
            raise StudioError("That URL is too long.")
        if not source_url:
            raise StudioError("Paste a Discord, Tenor, Giphy, or direct image link first.")

        prune_stages()
        failure: StudioError | None = None
        for data, resolved_url in resolve_image_sources(source_url):
            try:
                gif_data, dimensions = normalize_as_gif(data)
                key = uuid.uuid4().hex
                path = stage_dir / f"{key}.gif"
                path.write_bytes(gif_data)
                digest = hashlib.sha256(gif_data).hexdigest()
                with staged_lock:
                    staged_files[key] = {
                        "path": path,
                        "sha256": digest,
                        "resolvedUrl": resolved_url,
                        "created": time.time(),
                        **dimensions,
                    }
                self._send_json(200, {
                    "stageId": key,
                    "previewUrl": f"/api/preview/{key}",
                    "sha256": digest,
                    "bytes": len(gif_data),
                    "width": dimensions["width"],
                    "height": dimensions["height"],
                    "frames": dimensions["frames"],
                    "animated": dimensions["frames"] > 1,
                })
                return
            except StudioError as error:
                failure = error
        if failure is not None:
            raise failure
        raise StudioError("Couldn't find a usable image in that link.")

    def _publish(self, payload: dict[str, Any]) -> None:
        key = str(payload.get("stageId", ""))
        title = str(payload.get("title", "")).strip()
        category = str(payload.get("category", "Other")).strip() or "Other"
        category_emoji = str(payload.get("categoryEmoji", "🙂")).strip() or "🙂"
        tags_value = payload.get("tags", [])
        if not re.fullmatch(r"[a-f0-9]{32}", key):
            raise StudioError("Preview the link before adding it.")
        if not title or len(title) > 80:
            raise StudioError("Give the GIF a title up to 80 characters.")
        if len(category) > 32:
            raise StudioError("Keep the category under 32 characters.")
        if len(category_emoji) > 8:
            raise StudioError("Keep the group icon to one emoji.")
        if not isinstance(tags_value, list):
            raise StudioError("Tags must be a list.")
        tags = [str(tag).strip()[:32] for tag in tags_value if str(tag).strip()][:20]
        if len(tags_value) > 20:
            raise StudioError("Add no more than 20 tags.")
        if payload.get("rightsConfirmed") is not True:
            raise StudioError("Confirm you have permission to share this GIF.")

        with staged_lock:
            staged = staged_files.get(key)
        if staged is None or staged["created"] < time.time() - STAGE_TTL_SECONDS:
            raise StudioError("That preview expired. Paste the link and check it again.")

        catalog = json_catalog()
        if len(catalog["gifs"]) >= MAX_CATALOG_ITEMS:
            raise StudioError(f"The app catalog is limited to {MAX_CATALOG_ITEMS} GIFs.", 413)
        digest = staged["sha256"]
        for entry in catalog["gifs"]:
            if isinstance(entry, dict) and entry.get("sha256") == digest:
                raise StudioError(f"This GIF is already in the catalog as “{entry.get('title', 'Untitled')}”.", 409)
        for existing in GIFS_DIR.glob("*.gif"):
            try:
                if file_sha256(existing) == digest:
                    raise StudioError(f"This GIF file already exists as “{existing.name}”.", 409)
            except OSError:
                continue

        GIFS_DIR.mkdir(parents=True, exist_ok=True)
        stem = f"{safe_slug(title)}-{digest[:10]}"
        filename = f"{stem}.gif"
        file_path = GIFS_DIR / filename
        temp_asset = GIFS_DIR / f".{stem}.{uuid.uuid4().hex}.tmp"
        try:
            temp_asset.write_bytes(staged["path"].read_bytes())
            os.replace(temp_asset, file_path)
            media_url = RAW_ASSET_PREFIX + filename
            catalog["gifs"].append({
                "id": stem,
                "title": title,
                "category": category,
                "categoryEmoji": category_emoji,
                "tags": tags,
                "mediaUrl": media_url,
                "previewUrl": media_url,
                "sha256": digest,
            })
            write_catalog(catalog)
        except Exception:
            file_path.unlink(missing_ok=True)
            raise
        finally:
            temp_asset.unlink(missing_ok=True)

        with staged_lock:
            staged_files.pop(key, None)
        staged["path"].unlink(missing_ok=True)
        self._send_json(200, {"ok": True, "title": title, "filename": filename, "sha256": digest})

    def _delete(self, payload: dict[str, Any]) -> None:
        gif_id = str(payload.get("id", ""))
        catalog = json_catalog()
        matches = [item for item in catalog["gifs"] if isinstance(item, dict) and item.get("id") == gif_id]
        if not matches:
            raise StudioError("That GIF is no longer in the catalog.", 404)
        removed = matches[0]
        catalog["gifs"] = [item for item in catalog["gifs"] if not (isinstance(item, dict) and item.get("id") == gif_id)]
        filename: str | None = None
        media_url = str(removed.get("mediaUrl", ""))
        if media_url.startswith(RAW_ASSET_PREFIX):
            possible = urllib.parse.unquote(media_url[len(RAW_ASSET_PREFIX):])
            if re.fullmatch(r"[A-Za-z0-9_-]{1,80}\.gif", possible):
                filename = possible
        still_referenced = filename is not None and any(
            isinstance(item, dict) and str(item.get("mediaUrl", "")).endswith("/" + filename)
            for item in catalog["gifs"]
        )
        write_catalog(catalog)
        if filename is not None and not still_referenced:
            (GIFS_DIR / filename).unlink(missing_ok=True)
        self._send_json(200, {"ok": True, "title": removed.get("title", "GIF")})


def main() -> None:
    if not CATALOG_FILE.is_file():
        raise SystemExit(f"GIF catalog not found: {CATALOG_FILE}")
    try:
        server = ThreadingHTTPServer((HOST, PORT), StudioHandler)
    except OSError as error:
        raise SystemExit(f"Couldn't open http://{HOST}:{PORT}. Close another GIF Studio window and try again. ({error})")
    server.daemon_threads = True
    print("Dismod GIF Studio is running on this computer only.")
    print(f"Open http://{HOST}:{PORT} in your browser. Press Ctrl+C here to stop it.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nGIF Studio stopped.")
    finally:
        server.server_close()
        for item in list(staged_files.values()):
            item["path"].unlink(missing_ok=True)
        stage_dir.rmdir()


if __name__ == "__main__":
    main()
