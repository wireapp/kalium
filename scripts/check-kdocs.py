#!/usr/bin/env python3
"""Generate API docs and check navigation, visibility and resolved signatures.

Run with: make doc/test-kdocs
This exercises the real documentation build; it does not deploy to GitHub Pages.
"""

from html.parser import HTMLParser
from pathlib import Path
import subprocess
from urllib.parse import unquote, urlsplit


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.paths = set()

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            for name, value in attrs:
                if name == "href" and value:
                    url = urlsplit(value)
                    if not url.scheme and not url.netloc:
                        self.paths.add(unquote(url.path))


class Symbols(HTMLParser):
    """Read signature text, excluding prose and HTML attributes."""

    VOID_TAGS = {"area", "base", "br", "col", "embed", "hr", "img", "input",
                 "link", "meta", "param", "source", "track", "wbr"}

    def __init__(self):
        super().__init__()
        self.depth = 0
        self.parts = []

    def handle_starttag(self, tag, attrs):
        if tag in self.VOID_TAGS:
            return
        if self.depth:
            self.depth += 1
        elif "symbol" in dict(attrs).get("class", "").split():
            self.depth = 1

    def handle_endtag(self, tag):
        if self.depth and tag not in self.VOID_TAGS:
            self.depth -= 1

    def handle_data(self, data):
        if self.depth:
            self.parts.append(data)


def signature_text(page: Path) -> str:
    assert page.is_file(), f"Missing signature page: {page}"
    symbols = Symbols()
    symbols.feed(page.read_text())
    return "".join(symbols.parts)


def check_publication(publication: Path) -> None:
    links = Links()
    for page in ("index.html", "navigation.html"):
        links.feed((publication / page).read_text())

    # The existing publication has nine modules, with flat module URLs.
    for module in (
        "backup", "calling", "cli", "cryptography", "logger", "logic",
        "network", "persistence", "protobuf",
    ):
        page = f"{module}/index.html"
        assert page in links.paths, f"Missing module link: {page}"
        assert (publication / page).is_file(), f"Broken module link: {page}"

    # Concrete public and internal declarations from the existing source API.
    for page in (
        "logger/com.wire.kalium.logger/-kalium-logger/index.html",
        "backup/com.wire.backup.filesystem/-in-memory-backup-page-storage/index.html",
    ):
        assert page in links.paths, f"Missing declaration link: {page}"
        assert (publication / page).is_file(), f"Broken declaration link: {page}"

    expected_signatures = {
        "logic/com.wire.kalium.logic.feature.message/-send-text-message-use-case/invoke.html":
            ("ConversationId", "MessageLinkPreview", "MessageMention"),
        "logic/com.wire.kalium.logic.util/-platform-view/index.html":
            ("UIView", "NSView"),
        "persistence/com.wire.kalium.persistence.dao.meeting/insert-generated-occurrences.html":
            ("MeetingsQueries",),
    }
    for relative_page, expected_types in expected_signatures.items():
        text = signature_text(publication / relative_page)
        for expected_type in expected_types:
            assert expected_type in text, f"Missing signature type {expected_type}: {relative_page}"

    invalid_pages = [
        page.relative_to(publication)
        for page in publication.rglob("*.html")
        if "ERROR CLASS" in page.read_text()
    ]
    assert not invalid_pages, (
        f"Unresolved signature types in {len(invalid_pages)} pages, including: "
        + ", ".join(str(page) for page in invalid_pages[:10])
    )
    print("API documentation navigation, public/internal visibility and signatures passed.")


def main():
    root = Path(__file__).resolve().parent.parent
    subprocess.run(["make", "doc/generate-kdocs"], cwd=root, check=True)
    check_publication(root / "build/dokka/html")


if __name__ == "__main__":
    main()
