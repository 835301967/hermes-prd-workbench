"""Synthetic HTTP echo service used only by the reverse-proxy transport test."""
import json
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.respond()

    def do_POST(self):
        self.respond()

    def respond(self):
        body = json.dumps({
            "host": self.headers.get("Host"),
            "origin": self.headers.get("Origin"),
            "method": self.command,
            "path": self.path,
        }).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_):
        pass


HTTPServer(("0.0.0.0", 18100), Handler).serve_forever()
